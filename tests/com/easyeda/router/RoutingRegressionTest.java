package com.easyeda.router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.easyeda.router.engine.LegacyRoutingEngine;
import com.easyeda.router.engine.RoutingEngine;
import com.easyeda.router.engine.RoutingSnapshot;
import com.easyeda.utils.json.JObject;
import com.easyeda.utils.json.JArray;

/** Dependency-free regression suite; assertions are always enabled. Run with ant test. */
public final class RoutingRegressionTest {

	private static int passed;

	public static void main(String[] args) throws Exception {
		checkConversionContract();
		checkStructuredConversion();
		checkQuotedSyntaxAndPrecision();
		checkReportedClearanceGeometry();
		checkDiagnosticsCapture();
		checkCompletion();
		checkImportFailure();
		checkRoutingFailure();
		checkTimeoutCompatibility();
		checkQueuedCancellation();
		checkRunningCancellation();
		checkProgress();
		checkHeartbeatAndDisabledProgress();
		checkLegacyCacheInterruption();
		checkLegacyImportExport();
		System.out.println("Passed " + passed + " routing regression checks.");
	}

	private static void checkConversionContract() {
		String ses = "(session tmp\n (routes\n (network_out\n"
				+ " (net VCC\n (wire\n (path 1 254\n 10000 20000\n 30000 40000\n )\n )\n"
				+ " (via via0 10500 20300\n )\n )\n )\n )\n)";
		JObject net = SessionFileUtil.sessionFileToEasyEDA(ses).get("1");
		require("VCC".equals(net.get("net").asString()), "net name");
		JObject wire = net.get("wires", 0);
		require(wire.get("layerid").asInt() == 1, "numeric layer ID");
		require(wire.get("width").asDouble() == 0.254, "trace width scaling");
		require(wire.get("points").asJArray().size() == 4, "all trace coordinates");
		require(wire.get("points", 0).asDouble() == 10.0, "trace coordinate scaling");
		require(net.get("vias", 0, "x").asDouble() == 10.5, "via coordinate scaling");
		passed++;
	}

	private static void checkCompletion() {
		FakeEngine engine = new FakeEngine();
		engine.finished = true;
		Client client = new Client();
		RouterExecutor executor = executor(client, engine, 1000, 1000);
		executor.run();
		require(engine.loaded && engine.started && engine.stopped, "completed engine lifecycle");
		require(client.heartbeats == 1, "initial heartbeat");
		require(client.codes.size() == 1 && client.codes.get(0) == 0, "incomplete result is not full completion");
		require(client.incomplete == 7 && "session".equals(client.session), "snapshot forwarded");
		// A late connection-close callback must never interrupt the old executor thread.
		executor.interrupt();
		require(!Thread.currentThread().isInterrupted(), "completed executor thread released");
		passed++;
	}

	private static void checkImportFailure() {
		FakeEngine engine = new FakeEngine();
		engine.invalidInput = true;
		Client client = new Client();
		executor(client, engine, 1000, 1000).run();
		require(client.codes.size() == 1 && client.codes.get(0) == -2, "single import error");
		require(!engine.started && engine.stopped, "invalid DSN never starts routing");
		passed++;
	}

	private static void checkRoutingFailure() {
		FakeEngine engine = new FakeEngine();
		engine.error = new IOException("Expected simulated routing failure");
		Client client = new Client();
		executor(client, engine, 1000, 1000).run();
		require(client.codes.size() == 1 && client.codes.get(0) == -3, "engine failure code");
		require(engine.stopped, "failed engine stopped");
		passed++;
	}

	private static void checkTimeoutCompatibility() {
		FakeEngine engine = new FakeEngine();
		Client client = new Client();
		executor(client, engine, 0, 1000).run();
		require(client.codes.size() == 1 && client.codes.get(0) == 0, "timeout reports incomplete result");
		require(client.incomplete == 7 && engine.stopped, "partial snapshot on timeout");
		passed++;
	}

	private static void checkStructuredConversion() {
		String ses = "(session test (routes (resolution mil 100) (network_out"
				+ " (net \"NET (A) \\\"B\\\"\" (wire (path \"TopLayer\" 2540 -100000 0 300000.5 400000))"
				+ " (via \"via (0)\" 0 -203000 (type normal))))))";
		JObject net = SessionFileUtil.sessionFileToEasyEDA(ses).get("1");
		require(net.get("net").asString().equals("NET (A) \"B\""), "escaped names preserved");
		require(net.get("wires", 0, "layerid").asInt() == 1, "named top layer");
		require(net.get("wires", 0, "width").asDouble() == 0.254, "resolution-normalized width");
		require(net.get("wires", 0, "points", 0).asDouble() == -10, "negative coordinate preserved");
		require(net.get("vias", 0, "x").asDouble() == 0, "zero via coordinate preserved");
		String metric = "(session test (routes (resolution mm 10000) (network_out"
				+ " (net GND (wire (path 21 6451.6 254000 0 508000 254000))))))";
		double x = SessionFileUtil.sessionFileToEasyEDA(metric).get("1", "wires", 0, "points", 0).asDouble();
		require(Math.abs(x - 10) < 1e-9, "millimetre output normalized");
		String dsn = "(pcb test (resolution mil 100))";
		double referenceX = SessionFileUtil.sessionFileToEasyEDA(ses, dsn).get("1", "wires", 0, "points", 0).asDouble();
		require(referenceX == -100, "original DSN resolution retains editor convention");
		for (String bad : new String[] { "(session broken", "(session x (routes (network_out (net n (wire (path 1 2 0))))) )",
				metric.replace("path 21", "path UnknownCopper"), metric.replace("254000 0", "NaN 0") }) {
			try {
				SessionFileUtil.sessionFileToEasyEDA(bad);
				throw new AssertionError("Malformed/unsupported session accepted");
			} catch (IllegalArgumentException expected) { }
		}
		passed++;
	}

	private static void checkQuotedSyntaxAndPrecision() throws Exception {
		Specctra.Node pcb = Specctra.parse("(pcb \"name )\" (parser (string_quote \"  )))");
		require(pcb.atom(1).equals("name )"), "closing parentheses in quoted identifiers");
		require(pcb.child("parser").child("string_quote").atom(1).equals("\""), "quote declaration");
		double coordinate = 123.123456789;
		JObject json = JObject.auto(coordinate);
		require(JObject.parseSingle(json.toJson()).asDouble() == coordinate, "geometry JSON preserves precision");
		passed++;
	}

	private static void checkReportedClearanceGeometry() {
		// Reduced from an EasyEDA report: preserve the offending geometry exactly,
		// rather than masking an existing violation through rounding or unit changes.
		String dsn = "(pcb test (resolution mil 1000))";
		String ses = "(session test (routes (resolution mil 1000) (network_out"
				+ " (net A (wire (path 2 1000 64772 -177011 62247 -179536)))"
				+ " (net B (via via0 64444 -180445)))))";
		JObject converted = SessionFileUtil.sessionFileToEasyEDA(ses, dsn);
		JObject wire = converted.get("1", "wires", 0);
		JObject via = converted.get("2", "vias", 0);
		JArray points = wire.get("points").asJArray();
		double ax = points.get(0).asDouble(), ay = points.get(1).asDouble();
		double dx = points.get(2).asDouble() - ax, dy = points.get(3).asDouble() - ay;
		double px = via.get("x").asDouble() - ax, py = via.get("y").asDouble() - ay;
		double position = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy)));
		double gapMm = (Math.hypot(px - position * dx, py - position * dy)
				- 2.4016 / 2 - wire.get("width").asDouble() / 2) * .254;
		require(Math.abs(gapMm - .12585031024081486) < 1e-10, "reported clearance geometry preserved");
		require(gapMm < .5984 * .254, "existing violation remains detectable after conversion");
		double neckedGapMm = gapMm + (.254 - .1905) / 2;
		require(neckedGapMm > .5984 * .254, "a legal neckdown gap can become illegal when its width is restored");
		passed++;
	}

	private static void checkDiagnosticsCapture() throws Exception {
		java.nio.file.Path root = Files.createTempDirectory("easyeda-diagnostics-check-");
		try {
			String dsn = "(pcb diagnostics (resolution mil 1000))";
			JObject request = JObject.parseSingle("{\"a\":\"startRoute\",\"data\":\"\",\"timeout\":30}");
			request.put("data", dsn);
			String raw = request.toJson();
			RoutingDiagnostics capture = RoutingDiagnostics.create(root, raw, "modern", JObject.parseSingle("{\"worker_threads\":2}"));
			String packet = "{\"a\":\"routingResult\",\"complete\":1,\"data\":{}}";
			capture.record("routingProgress", "old progress", "old SES");
			capture.record("routingProgress", "latest progress", "latest SES");
			capture.record("routingResult", packet, "final SES");
			java.nio.file.Path directory;
			try (java.util.stream.Stream<java.nio.file.Path> paths = Files.list(root)) {
				directory = paths.findFirst().get();
			}
			require(new String(Files.readAllBytes(directory.resolve("input.dsn")), java.nio.charset.StandardCharsets.UTF_8).equals(dsn), "exact routing input captured");
			require(new String(Files.readAllBytes(directory.resolve("request.json")), java.nio.charset.StandardCharsets.UTF_8).equals(raw), "exact request captured");
			require(new String(Files.readAllBytes(directory.resolve("result.json")), java.nio.charset.StandardCharsets.UTF_8).equals(packet), "exact browser packet captured");
			require(new String(Files.readAllBytes(directory.resolve("progress.ses")), java.nio.charset.StandardCharsets.UTF_8).equals("latest SES"), "progress capture is bounded");
			require(new String(Files.readAllBytes(directory.resolve("result.ses")), java.nio.charset.StandardCharsets.UTF_8).equals("final SES"), "raw final session captured");
		} finally {
			try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root)) {
				for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) {
					Files.delete(path);
				}
			}
		}
		passed++;
	}

	private static void checkQueuedCancellation() {
		FakeEngine engine = new FakeEngine();
		Client client = new Client();
		RouterExecutor executor = executor(client, engine, 1000, 1000);
		executor.interrupt();
		executor.run();
		require(!engine.loaded && !engine.started && client.heartbeats == 0,
				"cancelled queued job does no work");
		require(client.codes.isEmpty(), "queued cancellation sends no stale result");
		passed++;
	}

	private static void checkRunningCancellation() throws Exception {
		FakeEngine engine = new FakeEngine();
		engine.blockProgress = true;
		Client client = new Client();
		RouterExecutor executor = executor(client, engine, 5000, 1);
		Thread thread = new Thread(executor, "routing-regression-cancel");
		thread.setDaemon(true);
		thread.start();
		try {
			require(engine.progressEntered.await(2, TimeUnit.SECONDS), "worker reached progress wait");
			executor.interrupt();
			thread.join(2000);
			require(!thread.isAlive() && engine.stopped, "running cancellation stops engine");
			require(client.codes.isEmpty() && client.progress == 0, "no stale cancellation events");
		} finally {
			executor.interrupt();
			thread.join(2000);
		}
		passed++;
	}

	private static void checkProgress() {
		FakeEngine engine = new FakeEngine();
		Client client = new Client();
		executor(client, engine, 5000, 1).run();
		require(client.progress == 1 && client.progressIncomplete == 9, "progress count forwarded");
		require("progress".equals(client.progressSession), "progress geometry forwarded");
		require(client.codes.size() == 1, "progress followed by one final result");
		passed++;
	}

	private static void checkLegacyImportExport() throws Exception {
		LegacyRoutingEngine engine = new LegacyRoutingEngine(1, 4);
		try {
			engine.load(Files.readAllBytes(Paths.get("testcase/test.dsn")));
			engine.stop();
			RoutingSnapshot snapshot = engine.snapshot();
			require(snapshot.sessionData.contains("(session"), "actual legacy SES export");
			require(snapshot.incompleteConnections >= 0, "actual legacy incomplete count");
			SessionFileUtil.sessionFileToEasyEDA(snapshot.sessionData);
		} finally {
			engine.stop();
		}
		passed++;
	}

	private static void checkHeartbeatAndDisabledProgress() {
		FakeEngine engine = new FakeEngine();
		Client client = new Client();
		new RouterExecutor(client, new byte[0], 5000, 1, 1, engine).run();
		require(client.heartbeats >= 2 && client.progress == 1, "heartbeats do not starve progress");
		engine = new FakeEngine();
		engine.finishAfterChecks = 3;
		client = new Client();
		new RouterExecutor(client, new byte[0], 5000, 0, 1, engine).run();
		require(client.heartbeats >= 2 && client.progress == 0, "disabled progress retains heartbeat");
		passed++;
	}

	private static void checkLegacyCacheInterruption() throws Exception {
		LegacyRoutingEngine engine = new LegacyRoutingEngine(1, 4);
		java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
		CountDownLatch entered = new CountDownLatch(1);
		Thread waiter = new Thread(() -> {
			entered.countDown();
			try { engine.awaitProgress(); }
			catch (InterruptedException expected) { interrupted.set(true); }
		}, "legacy-cache-interrupt-test");
		waiter.setDaemon(true);
		waiter.start();
		try {
			require(entered.await(1, TimeUnit.SECONDS), "legacy cache waiter started");
			waiter.interrupt();
			waiter.join(1000);
			require(!waiter.isAlive() && interrupted.get(), "cache interruption is not a snapshot timeout");
		} finally { waiter.interrupt(); engine.stop(); }
		passed++;
	}

	private static RouterExecutor executor(Client client, RoutingEngine engine, int timeout, int progress) {
		return new RouterExecutor(client, new byte[0], timeout, progress, 10000, engine);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeEngine implements RoutingEngine {
		boolean loaded;
		boolean started;
		boolean stopped;
		boolean finished;
		boolean invalidInput;
		boolean blockProgress;
		int finishAfterChecks;
		Exception error;
		final CountDownLatch progressEntered = new CountDownLatch(1);

		@Override
		public void load(byte[] data) throws IOException {
			loaded = true;
			if (invalidInput) {
				throw new IOException("Expected simulated import failure");
			}
		}

		@Override
		public void start() { started = true; }

		@Override
		public boolean isFinished() {
			if (finishAfterChecks > 0 && --finishAfterChecks == 0) { finished = true; }
			return finished;
		}

		@Override
		public Exception failure() { return error; }

		@Override
		public RoutingSnapshot awaitProgress() throws InterruptedException {
			progressEntered.countDown();
			if (blockProgress) {
				new CountDownLatch(1).await();
			}
			finished = true;
			return new RoutingSnapshot(9, "progress");
		}

		@Override
		public RoutingSnapshot snapshot() {
			require(stopped, "stop before final snapshot");
			return new RoutingSnapshot(7, "session");
		}

		@Override
		public void stop() { stopped = true; }
	}

	private static final class Client implements RoutingClient {
		final List<Integer> codes = new ArrayList<Integer>();
		int heartbeats;
		int incomplete;
		String session;
		int progress;
		int progressIncomplete;
		String progressSession;

		@Override
		public void sendRaw(String data) {
			require(data.contains("heartbeat"), "heartbeat action");
			heartbeats++;
		}

		@Override
		public void sendResult(int code, int count, String data) {
			codes.add(code);
			incomplete = count;
			session = data;
		}

		@Override
		public void sendProgress(int count, String data) {
			progress++;
			progressIncomplete = count;
			progressSession = data;
		}
	}
}
