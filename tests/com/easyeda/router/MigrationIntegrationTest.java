package com.easyeda.router;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.easyeda.router.engine.EngineFactory;
import com.easyeda.router.engine.EngineInputException;
import com.easyeda.router.engine.LegacyRoutingEngine;
import com.easyeda.router.engine.ModernRoutingEngine;
import com.easyeda.router.engine.RoutingEngine;
import com.easyeda.router.engine.RoutingSnapshot;
import com.easyeda.utils.json.JArray;
import com.easyeda.utils.json.JMap;
import com.easyeda.utils.json.JObject;

/** Actual-engine compatibility checks and a small repeatable benchmark corpus. */
public final class MigrationIntegrationTest {

	private static File engineDirectory;
	private static Path reports;
	private static long budget;

	public static void main(String[] args) throws Exception {
		engineDirectory = new File(args[0]).getAbsoluteFile();
		reports = Paths.get(args[1]);
		budget = Long.parseLong(args[2]);
		Files.createDirectories(reports);
		System.setProperty("com.easyeda.freerouting.directory", engineDirectory.toString());
		System.setProperty("com.easyeda.router.engine", "modern");
		EngineFactory.validate(JObject.parseSingle("{}"));
		JArray results = new JArray();
		if (args.length < 4 || !args[3].equals("--checks-only")) {
			for (String name : new String[] { "test", "test1", "simple", "MAIN" }) {
				for (String engine : new String[] { "legacy", "modern" }) {
					results.add(route(name, engine));
				}
			}
		}
		checkInvalidInput();
		checkFixedMultilayer();
		checkEarlyStop();
		checkCrash();
		checkHardStop();
		Files.write(reports.resolve("results.json"), results.toJson(2).getBytes(StandardCharsets.UTF_8));
		System.out.println("Actual-engine checks passed; results: " + reports.resolve("results.json"));
	}

	private static RoutingEngine modern(long timeout) {
		return new ModernRoutingEngine(engineDirectory, EngineFactory.java(), 1, 1, 512, timeout, 250, 3000);
	}

	private static JObject route(String name, String choice) throws Exception {
		String dsn = new String(Files.readAllBytes(Paths.get("testcase", name + ".dsn")), StandardCharsets.UTF_8);
		RoutingEngine engine = choice.equals("modern") ? modern(budget) : new LegacyRoutingEngine(1, 4);
		long start = System.nanoTime();
		boolean finished = false;
		int snapshots = 0;
		RoutingSnapshot last = null;
		try {
			engine.load(dsn.getBytes(StandardCharsets.UTF_8));
			engine.start();
			long deadline = start + TimeUnit.MILLISECONDS.toNanos(budget);
			while (!(finished = engine.isFinished()) && System.nanoTime() < deadline) {
				if (engine.failure() != null) { throw engine.failure(); }
				// Legacy awaitProgress waits up to one second; benchmark avoids skewing
				// the deadlines by reading only nonblocking modern progress here.
				if (choice.equals("modern")) {
					RoutingSnapshot snapshot = engine.awaitProgress();
					if (snapshot != null) {
						validateGeometry(SessionFileUtil.sessionFileToEasyEDA(snapshot.sessionData, dsn));
						snapshots++;
					}
				}
				Thread.sleep(20);
			}
			engine.stop();
			if (engine.failure() != null) { throw engine.failure(); }
			last = engine.snapshot();
			JObject geometry = SessionFileUtil.sessionFileToEasyEDA(last.sessionData, dsn);
			validateGeometry(geometry);
			if (choice.equals("modern")) { validateRequestedWidths(geometry, dsn); }
			Files.write(reports.resolve(choice + "-" + name + ".ses"), last.sessionData.getBytes(StandardCharsets.UTF_8));
			Files.write(reports.resolve(choice + "-" + name + ".json"), geometry.toJson(2).getBytes(StandardCharsets.UTF_8));
			JMap row = new JMap();
			row.put("board", name);
			row.put("engine", choice);
			row.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
			row.put("finishedBeforeDeadline", finished);
			row.put("incomplete", last.incompleteConnections);
			row.put("reportedClearanceViolations", last.clearanceViolations);
			row.put("progressSnapshotsChecked", snapshots);
			int vias = 0;
			double length = 0;
			for (JObject net : geometry.asJMap().values()) {
				vias += net.get("vias").asJArray().size();
				for (JObject wire : net.get("wires").asJArray()) {
					JArray points = wire.get("points").asJArray();
					for (int i = 2; i < points.size(); i += 2) {
						length += Math.hypot(points.get(i).asDouble() - points.get(i - 2).asDouble(),
								points.get(i + 1).asDouble() - points.get(i - 1).asDouble());
					}
				}
			}
			row.put("vias", vias);
			row.put("wireLengthEditorUnits", length);
			System.out.println(row.toJson());
			return row;
		} finally { engine.stop(); }
	}

	private static void validateGeometry(JObject geometry) {
		for (JObject net : geometry.asJMap().values()) {
			for (JObject wire : net.get("wires").asJArray()) {
				if (wire.get("layerid").asInt() <= 0 || wire.get("width").asDouble() <= 0) {
					throw new AssertionError("Invalid layer/width");
				}
				JArray points = wire.get("points").asJArray();
				if (points.size() < 4 || points.size() % 2 != 0) { throw new AssertionError("Invalid path"); }
				for (JObject point : points) {
					if (!Double.isFinite(point.asDouble())) { throw new AssertionError("Non-finite geometry"); }
				}
			}
		}
	}

	/** EasyEDA may widen imported neckdowns; reserve clearance for the declared width. */
	private static void validateRequestedWidths(JObject geometry, String dsn) {
		Specctra.Node design = Specctra.parse(dsn);
		double resolution = Specctra.number(design.child("resolution").atom(2));
		double defaultWidth = declaredWidth(design.child("structure"), 0);
		java.util.Map<String, Double> widths = new java.util.HashMap<String, Double>();
		Specctra.Node network = design.child("network");
		for (Object value : network.values) {
			if (!(value instanceof Specctra.Node) || !((Specctra.Node) value).named("class")) { continue; }
			Specctra.Node netClass = (Specctra.Node) value;
			double width = declaredWidth(netClass, defaultWidth);
			for (int i = 2; i < netClass.values.size() && netClass.values.get(i) instanceof String; i++) {
				widths.put(netClass.atom(i), width);
			}
		}
		for (JObject net : geometry.asJMap().values()) {
			double width = widths.getOrDefault(net.get("net").asString(), defaultWidth) * resolution / 1000;
			if (width <= 0) { throw new AssertionError("Fixture has no declared trace width"); }
			for (JObject wire : net.get("wires").asJArray()) {
				if (wire.get("width").asDouble() + 1e-8 < width) {
					throw new AssertionError("Modern output necked down below the import width for " + net.get("net"));
				}
			}
		}
	}

	private static double declaredWidth(Specctra.Node scope, double fallback) {
		for (Object value : scope.values) {
			if (value instanceof Specctra.Node && ((Specctra.Node) value).named("rule")) {
				Specctra.Node width = ((Specctra.Node) value).child("width");
				if (width != null) { fallback = Specctra.number(width.atom(1)); }
			}
		}
		return fallback;
	}

	private static void checkInvalidInput() throws Exception {
		RoutingEngine engine = modern(5000);
		try {
			engine.load("(pcb invalid)".getBytes(StandardCharsets.UTF_8));
			engine.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (engine.failure() == null && System.nanoTime() < deadline) { Thread.sleep(20); }
			if (!(engine.failure() instanceof EngineInputException)) { throw new AssertionError("Missing input failure"); }
		} finally { engine.stop(); }
	}

	private static void checkFixedMultilayer() throws Exception {
		String dsn = new String(Files.readAllBytes(Paths.get("tests/fixtures/fixed-multilayer.dsn")), StandardCharsets.UTF_8);
		for (RoutingEngine engine : new RoutingEngine[] { new LegacyRoutingEngine(1, 4), modern(5000) }) {
			try {
				engine.load(dsn.getBytes(StandardCharsets.UTF_8));
				engine.start();
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (!engine.isFinished() && engine.failure() == null && System.nanoTime() < deadline) { Thread.sleep(20); }
				engine.stop();
				if (engine.failure() != null) { throw engine.failure(); }
				RoutingSnapshot snapshot = engine.snapshot();
				if (snapshot.incompleteConnections != 0) { throw new AssertionError("Multilayer fixture did not complete"); }
				boolean fixed = false;
				boolean inner = false;
				for (JObject net : SessionFileUtil.sessionFileToEasyEDA(snapshot.sessionData, dsn).asJMap().values()) {
					for (JObject wire : net.get("wires").asJArray()) {
						JArray points = wire.get("points").asJArray();
						if (net.get("net").asString().equals("FIXED")) {
							fixed = wire.get("layerid").asInt() == 1 && wire.get("width").asDouble() == .2
									&& points.size() == 4 && points.get(0).asDouble() == 1 && points.get(1).asDouble() == 1
									&& points.get(2).asDouble() == 9 && points.get(3).asDouble() == 1;
						}
						if (net.get("net").asString().equals("ROUTE_测试") && wire.get("layerid").asInt() == 21) { inner = true; }
					}
				}
				if (!fixed || !inner) { throw new AssertionError("Protected trace or inner copper layer lost"); }
			} finally { engine.stop(); }
		}
		System.out.println("Protected trace and four-layer routing fixture passed on both engines.");
	}

	private static void checkEarlyStop() throws Exception {
		RoutingEngine engine = modern(10000);
		try {
			engine.load(Files.readAllBytes(Paths.get("testcase/MAIN.dsn")));
			engine.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (engine.awaitProgress() == null && System.nanoTime() < deadline) { Thread.sleep(20); }
			engine.stop();
			RoutingSnapshot partial = engine.snapshot();
			if (partial.incompleteConnections < 0) { throw new AssertionError("Missing partial result"); }
			engine.stop();
		} finally { engine.stop(); }
	}

	private static void checkCrash() throws Exception {
		// A JVM that starts but cannot launch the worker must produce an error, not success.
		File empty = Files.createTempDirectory("easyeda-missing-worker-").toFile();
		RoutingEngine engine = new ModernRoutingEngine(empty, EngineFactory.java(), 1, 1, 128, 5000, 250, 1000);
		try {
			engine.load(Files.readAllBytes(Paths.get("testcase/test.dsn")));
			engine.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (engine.failure() == null && System.nanoTime() < deadline) { Thread.sleep(20); }
			if (engine.failure() == null || engine.isFinished()) { throw new AssertionError("Missing worker crash error"); }
		} finally { engine.stop(); Files.delete(empty.toPath()); }
	}

	private static void checkHardStop() throws Exception {
		Path fake = Files.createTempDirectory("easyeda-stubborn-worker-");
		java.util.jar.Manifest manifest = new java.util.jar.Manifest();
		manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		manifest.getMainAttributes().putValue("Main-Class", HungWorker.class.getName());
		String resource = HungWorker.class.getName().replace('.', '/') + ".class";
		try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(
				Files.newOutputStream(fake.resolve("easyeda-worker.jar")), manifest);
				java.io.InputStream code = HungWorker.class.getClassLoader().getResourceAsStream(resource)) {
			jar.putNextEntry(new java.util.jar.JarEntry(resource));
			byte[] bytes = new byte[8192];
			int count;
			while ((count = code.read(bytes)) >= 0) { jar.write(bytes, 0, count); }
			jar.closeEntry();
		}
		ModernRoutingEngine engine = new ModernRoutingEngine(fake.toFile(), EngineFactory.java(), 1, 1, 128, 10000, 250, 200);
		try {
			engine.load(Files.readAllBytes(Paths.get("testcase/test.dsn")));
			engine.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (engine.awaitProgress() == null && System.nanoTime() < deadline) { Thread.sleep(20); }
			if (engine.snapshot().incompleteConnections != 1) { throw new AssertionError("Missing injected partial result"); }
			// Inspect resources only to prove the fault-injection child and its directory
			// are gone; no extra production API is introduced solely for tests.
			java.lang.reflect.Field child = ModernRoutingEngine.class.getDeclaredField("process");
			java.lang.reflect.Field folder = ModernRoutingEngine.class.getDeclaredField("directory");
			child.setAccessible(true);
			folder.setAccessible(true);
			Process process = (Process) child.get(engine);
			Path directory = (Path) folder.get(engine);
			long start = System.nanoTime();
			engine.stop();
			if (process.isAlive() || Files.exists(directory)
					|| System.nanoTime() - start > TimeUnit.SECONDS.toNanos(3)) {
				throw new AssertionError("Uncooperative worker not killed/cleaned within the stop bound");
			}
			if (engine.snapshot().incompleteConnections != 1) { throw new AssertionError("Partial snapshot lost on hard stop"); }
			System.out.println("Uncooperative worker hard-stop deadline and file cleanup passed.");
		} finally {
			engine.stop();
			Files.deleteIfExists(fake.resolve("easyeda-worker.jar"));
			Files.deleteIfExists(fake);
		}
	}
}
