package com.easyeda.router;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.InvalidParameterException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.easyeda.utils.Config;
import com.easyeda.utils.json.JObject;

import board.BoardObservers;
import board.Component;
import board.Item;
import board.ItemIdNoGenerator;
import board.TestLevel;
import designformats.specctra.DsnFile.ReadResult;
import interactive.AutorouteSettings;
import interactive.BoardHandling;
import interactive.RouterCache;

public class RouterExecutor implements Runnable, BoardObservers {

	private static long currentTimeMillis() {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
	}

	private final RoutingClient client;
	private final BoardHandling bh;
	private final byte[] dsnFile;
	private final int timeout;
	private final int progressInterval;
	private final int heatbeatInterval;

	private volatile Thread myThread;
	private volatile long start;
	private volatile long deadline;
	private volatile long nextHeartbeat;
	private volatile long nextReportProgress;

	public RouterExecutor(RoutingClient client, byte[] dsnFile, int timeout, int progressInterval, int optimizeTimes) {
		JObject router = Config.get("main.json").get("router");
		this.heatbeatInterval = router.get("keep_heartbeat").asInt();
		int minTimeout = router.get("min_timeout").asInt();
		int minProgressInterval = router.get("min_progress_interval").asInt();
		AutorouteSettings.maxRouteRetry = router.get("max_route_retry").asInt();
		this.client = client;
		this.bh = new BoardHandling(Locale.ENGLISH, optimizeTimes > 0 ? optimizeTimes : 3);
		this.dsnFile = dsnFile;
		this.timeout = Math.max(timeout, minTimeout);
		this.progressInterval = Math.max(progressInterval, minProgressInterval);
	}

	public void interrupt() {
		myThread.interrupt();
	}

	@Override
	public void run() {
		myThread = Thread.currentThread();

		try (InputStream is = new ByteArrayInputStream(dsnFile)) {
			// send a heart beat first to make sure connection is alive
			// because this executor may pending when server is busy
			client.sendRaw("{\"a\":\"heartbeat\"}\n");

			ReadResult read_result = bh.import_design(is, this, new ItemIdNoGenerator(), TestLevel.RELEASE_VERSION);
			if (read_result != ReadResult.OK) {
				throw new InvalidParameterException();
			}
		} catch (Exception e) {
			e.printStackTrace();
			client.sendResult(-2, -1, null);
		}

		try {
			start = currentTimeMillis();
			deadline = start + timeout;
			nextHeartbeat = start + heatbeatInterval;
			nextReportProgress = start + progressInterval;
			bh.start_batch_autorouter();

			while (!Thread.interrupted()) {
				long current = currentTimeMillis();
				if (bh.isFinished) {
					bh.terminate();
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					bh.export_specctra_session_file("tmp", os);
					client.sendResult(1, bh.getInCompletesNum(), os.toString());
					break;
				} else if (bh.exception != null) {
					throw bh.exception;
				} else if (deadline > current) {
					if (current >= nextHeartbeat) {
						nextHeartbeat = current + heatbeatInterval;
						client.sendRaw("{\"a\":\"heartbeat\"}\n");
					} else if (current >= nextReportProgress) {
						nextReportProgress = current + progressInterval;
						try {
							RouterCache cache = bh.waitCurrentCache();
							client.sendProgress(cache.inCompletesNum, cache.data);
							nextHeartbeat = current + heatbeatInterval;
						} catch (InterruptedException ie) {
							// ignore
						}
					}
				} else {
					bh.terminate();
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					bh.export_specctra_session_file("tmp", os);
					client.sendResult(1, bh.getInCompletesNum(), os.toString());
					break;
				}

				Thread.sleep(500);
			}
		} catch (Exception e) {
			e.printStackTrace();
			client.sendResult(-1, -1, null);
		} finally {
			try {
				bh.terminate();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
	}

	@Override
	public void notify_deleted(Item p_object) {
	}

	@Override
	public void notify_changed(Item p_object) {
	}

	@Override
	public void notify_new(Item p_object) {
	}

	@Override
	public void activate() {
	}

	@Override
	public void deactivate() {
	}

	@Override
	public boolean is_active() {
		return false;
	}

	@Override
	public void notify_moved(Component p_component) {
	}
}
