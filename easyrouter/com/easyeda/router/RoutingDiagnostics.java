package com.easyeda.router;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.easyeda.utils.json.JObject;

/** Opt-in local request/output capture for reproducing browser routing regressions. */
final class RoutingDiagnostics {

	private final Path directory;

	private RoutingDiagnostics(Path directory) { this.directory = directory; }

	static RoutingDiagnostics capture(String request, String engine, JObject settings) {
		String destination = System.getenv("EASYEDA_ROUTER_DIAGNOSTICS");
		if (destination == null || destination.isEmpty()) { return null; }
		try {
			RoutingDiagnostics capture = create(Paths.get(destination), request, engine, settings);
			System.out.println("Routing diagnostics: " + capture.directory);
			return capture;
		} catch (Exception e) {
			System.err.println("Could not capture routing diagnostics: " + e.getMessage());
			return null;
		}
	}

	static RoutingDiagnostics create(Path destination, String request, String engine, JObject settings) throws Exception {
		Files.createDirectories(destination);
		RoutingDiagnostics capture = new RoutingDiagnostics(Files.createTempDirectory(destination.toAbsolutePath(), "route-"));
		capture.write("request.json", request);
		capture.write("input.dsn", JObject.parseSingle(request).get("data").asString());
		capture.write("settings.json", settings.toJson(2));
		capture.write("engine.txt", engine + "\n");
		return capture;
	}

	void record(String action, String packet, String ses) {
		String name = action.equals("routingResult") ? "result" : "progress";
		try {
			// Keep only the latest progress geometry; final output is recorded separately.
			if (ses != null) { write(name + ".ses", ses); }
			write(name + ".json", packet);
		} catch (IOException e) {
			System.err.println("Could not write routing diagnostics: " + e.getMessage());
		}
	}

	private void write(String name, String content) throws IOException {
		Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
	}
}
