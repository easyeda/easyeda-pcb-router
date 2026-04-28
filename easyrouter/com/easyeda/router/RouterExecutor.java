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

/**
 * PCB 自动布线执行器，在独立线程中运行 Freerouting 引擎。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>导入 DSN 设计文件到 {@link BoardHandling}</li>
 *   <li>启动批量自动布线 {@link BoardHandling#start_batch_autorouter()}</li>
 *   <li>以 500ms 间隔轮询布线状态，期间发送心跳和进度</li>
 *   <li>布线完成或超时后，导出 Specctra Session 文件并通过 {@link RoutingClient} 回传结果</li>
 * </ol>
 * <p>
 * Core routing executor that runs the Freerouting engine in a separate thread.
 * Imports DSN data, starts batch autorouting, polls for completion at 500ms intervals,
 * and sends heartbeats/progress/results back via {@link RoutingClient}.
 *
 * @see WSHandler
 * @see RoutingClient
 * @see BoardHandling
 */
public class RouterExecutor implements Runnable, BoardObservers {

	/** 使用单调时钟避免系统时间调整的影响 / Monotonic clock to avoid system time adjustments */
	private static long currentTimeMillis() {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
	}

	private final RoutingClient client;
	private final BoardHandling bh;
	private final byte[] dsnFile;
	/** 布线超时时间（毫秒）/ Routing timeout in milliseconds */
	private final int timeout;
	/** 进度报告间隔（毫秒）/ Progress report interval in milliseconds */
	private final int progressInterval;
	/** 心跳发送间隔（毫秒）/ Heartbeat send interval in milliseconds */
	private final int heatbeatInterval;

	private volatile Thread myThread;
	private volatile long start;
	private volatile long deadline;
	private volatile long nextHeartbeat;
	private volatile long nextReportProgress;

	/**
	 * 创建布线执行器。
	 * <p>
	 * 从 {@code config/<env>/main.json} 的 {@code router} 节点读取最小超时、最小进度间隔、
	 * 心跳间隔和最大重试次数等配置。实际超时和进度间隔取客户端请求值与配置最小值中的较大者。
	 *
	 * @param client           结果回调接口 / Callback interface for sending results
	 * @param dsnFile          DSN 文件字节数据 / DSN file content as bytes
	 * @param timeout          客户端请求的超时时间（毫秒）/ Client-requested timeout in ms
	 * @param progressInterval 客户端请求的进度间隔（毫秒）/ Client-requested progress interval in ms
	 * @param optimizeTimes    布线后优化次数，0 表示使用默认值 3 / Post-route optimization passes, 0 defaults to 3
	 */
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

	/**
	 * 中断布线线程。
	 * <p>
	 * Interrupt the routing thread to cancel the operation.
	 */
	public void interrupt() {
		myThread.interrupt();
	}

	/**
	 * 布线主循环。
	 * <p>
	 * 步骤：导入 DSN → 启动自动布线 → 500ms 轮询（心跳/进度/完成/超时）→ 导出结果。
	 * <p>
	 * Main routing loop: import DSN, start autorouter, poll at 500ms intervals
	 * for heartbeat/progress/completion/timeout, then export results.
	 */
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
