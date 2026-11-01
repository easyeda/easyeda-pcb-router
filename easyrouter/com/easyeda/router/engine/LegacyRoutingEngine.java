package com.easyeda.router.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import board.BoardObserverAdaptor;
import board.ItemIdNoGenerator;
import board.TestLevel;
import designformats.specctra.DsnFile.ReadResult;
import interactive.AutorouteSettings;
import interactive.BoardHandling;
import interactive.RouterCache;

/** Historical engine adapter. All legacy engine types stay behind this boundary. */
public final class LegacyRoutingEngine implements RoutingEngine {

	private final BoardHandling board;

	public LegacyRoutingEngine(int optimizePasses, int maxRouteRetry) {
		AutorouteSettings.maxRouteRetry = maxRouteRetry;
		board = new BoardHandling(Locale.ENGLISH, optimizePasses > 0 ? optimizePasses : 3);
	}

	@Override
	public void load(byte[] dsnData) throws IOException {
		try (ByteArrayInputStream input = new ByteArrayInputStream(dsnData)) {
			ReadResult result = board.import_design(input, new BoardObserverAdaptor(),
					new ItemIdNoGenerator(), TestLevel.RELEASE_VERSION);
			if (result != ReadResult.OK) {
				throw new IOException("Failed to import DSN: " + result);
			}
		}
	}

	@Override
	public void start() {
		board.start_batch_autorouter();
	}

	@Override
	public boolean isFinished() {
		return board.isFinished;
	}

	@Override
	public Exception failure() {
		return board.exception;
	}

	@Override
	public RoutingSnapshot awaitProgress() throws InterruptedException {
		RouterCache cache = board.pollCurrentCache();
		return cache == null ? null : new RoutingSnapshot(cache.inCompletesNum, cache.data);
	}

	@Override
	public RoutingSnapshot snapshot() throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!board.export_specctra_session_file("tmp", output)) {
			throw new IOException("Failed to export routing session");
		}
		return new RoutingSnapshot(board.getInCompletesNum(),
				new String(output.toByteArray(), StandardCharsets.UTF_8),
				new interactive.ClearanceViolations(board.get_routing_board().get_items()).list.size() / 2);
	}

	@Override
	public void stop() throws InterruptedException {
		board.terminate();
	}
}
