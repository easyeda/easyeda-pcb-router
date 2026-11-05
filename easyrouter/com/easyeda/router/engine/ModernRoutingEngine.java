package com.easyeda.router.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.easyeda.router.Specctra;
import com.easyeda.utils.json.JObject;

/** Java 8 facade for a single isolated Java 25 routing worker. */
public final class ModernRoutingEngine implements RoutingEngine {

	private final File engineDirectory;
	private final String java;
	private final int threads;
	private final int passes;
	private final int heapMb;
	private final long timeout;
	private final long interval;
	private final long grace;
	private final AtomicReference<RoutingSnapshot> progress = new AtomicReference<RoutingSnapshot>();
	private volatile RoutingSnapshot latest;
	private volatile Exception error;
	private volatile boolean finished;
	private volatile boolean terminal;
	private volatile boolean stopping;
	private Process process;
	private Thread reader;
	private Path directory;

	public ModernRoutingEngine(File engineDirectory, String java, int threads, int passes,
			int heapMb, long timeout, long interval, long grace) {
		this.engineDirectory = engineDirectory;
		this.java = java;
		this.threads = threads;
		this.passes = passes > 0 ? passes : 3;
		this.heapMb = heapMb;
		this.timeout = timeout;
		this.interval = interval;
		this.grace = grace;
	}

	@Override
	public void load(byte[] input) throws IOException {
		if (directory != null || stopping) { throw new IllegalStateException("Engine jobs cannot be reused"); }
		if (!Specctra.parse(new String(input, StandardCharsets.UTF_8)).named("pcb")) {
			throw new EngineInputException("Expected a Specctra PCB design");
		}
		directory = Files.createTempDirectory("easyeda-route-");
		Files.write(directory.resolve("input.dsn"), input);
	}

	@Override
	public void start() throws IOException {
		if (directory == null || process != null) { throw new IllegalStateException("Worker job not ready"); }
		File worker = new File(engineDirectory, "easyeda-worker.jar");
		process = new ProcessBuilder(Arrays.asList(java, "-Xmx" + heapMb + "m",
				"-XX:ActiveProcessorCount=" + threads, "-Djava.awt.headless=true",
				"-Duser.home=" + directory.toAbsolutePath(),
				"-Djava.io.tmpdir=" + directory.toAbsolutePath(),
				"-jar", worker.getAbsolutePath(), directory.resolve("input.dsn").toString(),
				String.valueOf(threads), String.valueOf(passes), String.valueOf(interval), String.valueOf(timeout)))
				.directory(directory.toFile()).redirectError(directory.resolve("worker.log").toFile()).start();
		// Keep Java 8 linkage; tests and diagnostics can inspect the Process directly.
		reader = new Thread(() -> readEvents(), "freerouting-worker-events");
		reader.setDaemon(true);
		reader.start();
	}

	private void readEvents() {
		try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = input.readLine()) != null) {
				if (line.length() > 80000000) { throw new IOException("Oversized worker message"); }
				JObject event = JObject.parseSingle(line);
				if (event.get("protocol").asInt() != 1) { throw new IOException("Unsupported worker protocol"); }
				String type = event.get("type").asString();
				if ("error".equals(type)) {
					String message = event.get("message").asString();
					error = "input".equals(event.get("phase").asString())
							? new EngineInputException(message) : new IOException(message);
					terminal = true;
				} else if ("progress".equals(type) || "result".equals(type)) {
					RoutingSnapshot snapshot = new RoutingSnapshot(event.get("incomplete").asInt(),
							event.get("session").asString(), event.get("violations").asInt());
					latest = snapshot;
					progress.set(snapshot); // One-slot mailbox; never queue unbounded geometry.
					if ("result".equals(type)) { terminal = true; }
				} else if (!"ready".equals(type)) {
					throw new IOException("Unknown worker event: " + type);
				}
			}
			if (!terminal && !stopping) { throw new IOException("Worker exited without a terminal result"); }
		} catch (Exception e) {
			if (!stopping) { error = e; }
		} finally {
			finished = true;
		}
	}

	@Override public boolean isFinished() { return finished && error == null; }
	@Override public Exception failure() { return error; }
	@Override public RoutingSnapshot awaitProgress() { return progress.getAndSet(null); }

	@Override
	public RoutingSnapshot snapshot() throws IOException {
		if (latest == null) { throw new IOException("Worker produced no routing snapshot", error); }
		return latest;
	}

	@Override
	public void stop() {
		if (stopping) { return; }
		stopping = true;
		boolean interrupted = Thread.interrupted();
		try {
			if (process != null && process.isAlive()) {
				try (OutputStream control = process.getOutputStream()) {
					control.write("stop\n".getBytes(StandardCharsets.UTF_8));
					control.flush();
				} catch (IOException ignored) { }
				long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(grace);
				while (process.isAlive() && System.nanoTime() < deadline) {
					try { process.waitFor(100, TimeUnit.MILLISECONDS); }
					catch (InterruptedException e) { interrupted = true; }
				}
				if (process.isAlive()) {
					process.destroyForcibly();
					try { process.waitFor(2000, TimeUnit.MILLISECONDS); }
					catch (InterruptedException e) { interrupted = true; }
				}
			}
			if (reader != null) {
				try { reader.join(2000); }
				catch (InterruptedException e) { interrupted = true; }
			}
		} finally {
			if (directory != null) {
				try (Stream<Path> files = Files.walk(directory)) {
					files.sorted(Comparator.reverseOrder()).forEach(path -> {
						try { Files.deleteIfExists(path); }
						catch (IOException e) { System.err.println("Could not clean routing file: " + path); }
					});
				} catch (IOException e) { System.err.println("Could not clean routing directory: " + directory); }
			}
			if (interrupted) { Thread.currentThread().interrupt(); }
		}
	}
}
