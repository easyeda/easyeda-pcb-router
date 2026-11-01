package com.easyeda.router.engine;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import com.easyeda.utils.Utils;
import com.easyeda.utils.json.JObject;

/** Runtime selection and validation; modern dependencies never enter the facade JVM. */
public final class EngineFactory {

	private EngineFactory() { }

	public static String selected(JObject router) {
		String choice = System.getProperty("com.easyeda.router.engine");
		if (choice == null || choice.isEmpty()) { choice = System.getenv("EASYEDA_ROUTER_ENGINE"); }
		if (choice == null || choice.isEmpty()) { choice = router.getOrDefault("engine", "modern").asString(); }
		if (!choice.equals("modern") && !choice.equals("legacy")) {
			throw new IllegalArgumentException("Unknown routing engine: " + choice);
		}
		return choice;
	}

	public static RoutingEngine create(JObject router, int passes, long timeout, long interval) {
		if (selected(router).equals("legacy")) {
			return new LegacyRoutingEngine(passes, router.get("max_route_retry").asInt());
		}
		return new ModernRoutingEngine(directory(), java(), positive(router, "worker_threads", 2, 64),
				passes, positive(router, "worker_heap_mb", 1024, 65536), timeout,
				Math.max(250, interval), positive(router, "worker_stop_grace_ms", 5000, 30000));
	}

	public static int positive(JObject router, String name, int fallback, int maximum) {
		int value = router.getOrDefault(name, fallback).asInt();
		if (value < 1 || value > maximum || router.getOrDefault(name, fallback).asDouble() != value) {
			throw new IllegalArgumentException(name + " must be between 1 and " + maximum);
		}
		return value;
	}

	public static File directory() {
		return new File(System.getProperty("com.easyeda.freerouting.directory", Utils.joinAppPath("engine")));
	}

	public static String java() {
		String home = System.getenv("FREEROUTING_JAVA_HOME");
		if (home == null || home.isEmpty()) { home = System.getProperty("java.home"); }
		return System.getProperty("com.easyeda.freerouting.java",
				new File(home, "bin/java" + (File.separatorChar == '\\' ? ".exe" : "")).getAbsolutePath());
	}

	public static void validate(JObject router) throws Exception {
		String choice = selected(router);
		positive(router, "max_active_jobs", 1, 64);
		positive(router, "max_queued_jobs", 4, 10000);
		positive(router, "keep_heartbeat", 5000, 60000);
		positive(router, "min_timeout", 300000, 86400000);
		positive(router, "min_progress_interval", 2000, 86400000);
		if (choice.equals("modern")) {
			positive(router, "worker_threads", 2, 64);
			if (positive(router, "worker_heap_mb", 1024, 65536) < 128) {
				throw new IllegalArgumentException("worker_heap_mb must be at least 128");
			}
			positive(router, "worker_stop_grace_ms", 5000, 30000);
			File worker = new File(directory(), "easyeda-worker.jar");
			File manifest = new File(directory(), "upstream.json");
			if (!worker.isFile() || !manifest.isFile()) {
				throw new IllegalStateException("Modern worker missing in " + directory() + "; run ant build-client");
			}
			JObject pinned = JObject.parseSingle(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
			File core = new File(directory(), pinned.get("artifact").asString());
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(core.toPath())) {
				byte[] buffer = new byte[65536];
				int count;
				while ((count = input.read(buffer)) >= 0) { sha.update(buffer, 0, count); }
			}
			StringBuilder digest = new StringBuilder();
			for (byte b : sha.digest()) { digest.append(String.format("%02x", b & 255)); }
			if (!digest.toString().equals(pinned.get("sha256").asString())) {
				throw new IllegalStateException("Modern Freerouting artifact checksum mismatch");
			}
			Process probe = new ProcessBuilder(java(), "-version").redirectErrorStream(true).start();
			try {
				if (!probe.waitFor(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Java version check timed out"); }
				byte[] text = Utils.readAll(probe.getInputStream());
				String version = new String(text, StandardCharsets.UTF_8);
				java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("version \\\"(\\d+)").matcher(version);
				if (probe.exitValue() != 0 || !matcher.find() || Integer.parseInt(matcher.group(1)) < 25) {
					throw new IllegalStateException("Modern routing requires Java 25+: set FREEROUTING_JAVA_HOME. Found: " + version.trim());
				}
			} finally { probe.destroyForcibly(); }
		}
		System.out.println("Routing engine: " + choice + (choice.equals("modern") ? " (Freerouting 2.5.0, isolated worker)" : " (historical)"));
	}
}
