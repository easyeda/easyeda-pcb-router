package com.easyeda.worker;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.autoroute.pipeline.RoutingPipeline;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;

/** One local routing job. stdout is a versioned JSON-lines protocol; logs use stderr. */
public final class ModernWorker {

	private final PrintStream protocol = System.out;
	private final AtomicBoolean stopping = new AtomicBoolean();
	private final RoutingJob job = new RoutingJob();
	private long nextSnapshot;
	private long interval;
	private volatile boolean pipelineStarted;

	public static void main(String[] args) {
		ModernWorker worker = new ModernWorker();
		System.setOut(System.err);
		int status = worker.run(args);
		// Optimizer/deferred infrastructure must never outlive this one-job process.
		System.exit(status);
	}

	private int run(String[] args) {
		String phase = "input";
		try {
			if (args.length != 5) { throw new IllegalArgumentException("Expected input, threads, passes, interval, timeout"); }
			FRLogger.disableLogging();
			GlobalSettings settings = new GlobalSettings();
			GlobalSettings.setCurrent(settings);
			FRAnalytics.setGlobalSettings(settings);
			FRAnalytics.setEnabled(false);
			byte[] input = Files.readAllBytes(Path.of(args[0]));
			job.name = "easyeda";
			job.setInput(input);
			job.routerSettings = new SettingsMerger(new DefaultSettings(),
					new DsnFileSettings(new ByteArrayInputStream(input), "input.dsn")).merge();
			job.routerSettings.setMaxThreads(Integer.parseInt(args[1]));
			// Route sequentially; reserve the configured thread pool for optimization.
			job.routerSettings.autorouter.maxThreads = 1;
			job.routerSettings.optimizer.maxThreads = Integer.parseInt(args[1]);
			job.routerSettings.optimizer.maxPasses = Integer.parseInt(args[2]);
			job.routerSettings.setRunOptimizer(true);
			// Match EasyEDA's historical batch mode: direct routing, no SMD fanout
			// pre-pass. Upstream's default fanout can consume the entire job budget.
			job.routerSettings.setFanoutEnabled(false);
			// EasyEDA's round trip can restore tracks to the net-class width. A
			// narrower neck would then have reserved too little clearance.
			job.routerSettings.setAutomaticNeckdown(false);
			job.routerSettings.neckWidthUm = 0.0;
			job.routerSettings.autorouter.algorithm = "freerouting-router";
			interval = Math.max(250, Long.parseLong(args[3]));
			long timeout = Long.parseLong(args[4]);
			HeadlessBoardManager manager = new HeadlessBoardManager(job);
			BoardReadResult result = manager.loadFromSpecctraDsn(new ByteArrayInputStream(input), null,
					new ItemIdGenerator());
			if (!(result instanceof BoardReadResult.Success)) {
				throw new IllegalArgumentException("Invalid DSN board or missing outline: " + result.getClass().getSimpleName());
			}
			job.board = manager.getRoutingBoard();
			job.board.awaitPostLoad();
			job.routerSettings.applyBoardSpecificOptimizationsIfNeeded(job.board);
			job.thread = new StoppableThread() {
				@Override protected void threadAction() { }
			};
			Thread control = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
					// A stop command or parent EOF requests a cooperative stop.
					reader.readLine();
				} catch (Exception ignored) { }
				stopping.set(true);
				job.thread.requestStop();
			}, "worker-control");
			control.setDaemon(true);
			control.start();
			Thread watchdog = new Thread(() -> {
				try { Thread.sleep(timeout); } catch (InterruptedException ignored) { return; }
				stopping.set(true);
				job.thread.requestStop();
			}, "worker-deadline");
			watchdog.setDaemon(true);
			watchdog.start();
			phase = "routing";
			emit("ready", null, false);
			emit("progress", job.board, false);
			if (!stopping.get()) {
				RoutingPipeline pipeline = RoutingPipeline.createForHeadless(job);
				// Upstream emits these on the coordinating pipeline thread, between
				// mutations. Candidate optimizer threads work on separate board copies.
				pipeline.addBoardUpdatedEventListener(event -> {
					if (System.nanoTime() >= nextSnapshot) {
						try { emit("progress", event.getBoard(), false); }
						catch (Exception e) { throw new IllegalStateException("SES snapshot failed", e); }
					}
				});
				pipelineStarted = true;
				pipeline.run();
			}
			watchdog.interrupt();
			emit("result", job.board, stopping.get());
			return 0;
		} catch (Throwable e) {
			JsonObject event = event("error");
			event.addProperty("phase", pipelineStarted ? "routing" : phase);
			event.addProperty("message", e.getClass().getSimpleName() + ": " + e.getMessage());
			protocol.println(event);
			e.printStackTrace(System.err);
			return 1;
		}
	}

	private void emit(String type, RoutingBoard board, boolean stopped) throws Exception {
		JsonObject event = event(type);
		if (board != null) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			SesWriter.write(board, output, "easyeda");
			BoardStatistics stats = new BoardStatistics(board);
			event.addProperty("session", output.toString(StandardCharsets.UTF_8));
			event.addProperty("incomplete", stats.connections.incompleteCount);
			event.addProperty("vias", stats.items.viaCount);
			event.addProperty("wireLengthMm", stats.traces.totalLengthMm);
			event.addProperty("violations", stats.clearanceViolations.totalCount);
			event.addProperty("stopped", stopped);
			event.addProperty("observedHeapBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
			nextSnapshot = System.nanoTime() + interval * 1000000L;
		}
		protocol.println(event);
		if (protocol.checkError()) { throw new IllegalStateException("Parent protocol stream closed"); }
	}

	private JsonObject event(String type) {
		JsonObject event = new JsonObject();
		event.addProperty("protocol", 1);
		event.addProperty("engine", "freerouting-2.5.0");
		event.addProperty("type", type);
		return event;
	}
}
