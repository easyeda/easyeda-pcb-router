package com.easyeda.router;

import java.util.concurrent.TimeUnit;

import com.easyeda.router.engine.EngineFactory;
import com.easyeda.router.engine.EngineInputException;
import com.easyeda.router.engine.RoutingEngine;
import com.easyeda.router.engine.RoutingSnapshot;
import com.easyeda.utils.Config;
import com.easyeda.utils.json.JObject;

/**
 * PCB 自动布线执行器，在独立线程中运行 Freerouting 引擎。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>导入 DSN 设计文件到 {@link RoutingEngine}</li>
 *   <li>启动批量自动布线 {@link RoutingEngine#start()}</li>
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
 * @see RoutingEngine
 */
public class RouterExecutor implements Runnable {

	/** 使用单调时钟避免系统时间调整的影响 / Monotonic clock to avoid system time adjustments */
	private static long currentTimeMillis() {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
	}

	private final RoutingClient client;
	private final RoutingEngine engine;
	private final byte[] dsnFile;
	/** 布线超时时间（毫秒）/ Routing timeout in milliseconds */
	private final int timeout;
	/** 进度报告间隔（毫秒）/ Progress report interval in milliseconds */
	private final int progressInterval;
	/** 心跳发送间隔（毫秒）/ Heartbeat send interval in milliseconds */
	private final int heatbeatInterval;

	private Thread myThread;
	private volatile boolean cancelled;
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
		this(client, dsnFile, timeout, progressInterval, optimizeTimes,
				Config.get("main.json").get("router"));
	}

	private RouterExecutor(RoutingClient client, byte[] dsnFile, int timeout, int progressInterval,
			int optimizeTimes, JObject router) {
		this(client, dsnFile, Math.max(timeout, router.get("min_timeout").asInt()),
				progressInterval == 0 ? 0 : Math.max(progressInterval, router.get("min_progress_interval").asInt()),
				router.get("keep_heartbeat").asInt(),
				EngineFactory.create(router, optimizeTimes, Math.max(timeout, router.get("min_timeout").asInt()),
						Math.max(progressInterval, router.get("min_progress_interval").asInt())));
	}

	// Engine injection also permits lifecycle regression checks without routing a board.
	RouterExecutor(RoutingClient client, byte[] dsnFile, int timeout, int progressInterval,
			int heartbeatInterval, RoutingEngine engine) {
		this.client = client;
		this.engine = engine;
		this.dsnFile = dsnFile;
		this.timeout = timeout;
		this.progressInterval = progressInterval;
		this.heatbeatInterval = heartbeatInterval;
	}

	/**
	 * 中断布线线程。
	 * <p>
	 * Interrupt the routing thread to cancel the operation.
	 */
	public synchronized void interrupt() {
		cancelled = true;
		if (myThread != null) {
			myThread.interrupt();
		}
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
		synchronized (this) {
			if (cancelled) {
				return;
			}
			myThread = Thread.currentThread();
		}

		try {
			client.sendRaw("{\"a\":\"heartbeat\"}\n");
			if (cancelled) {
				return;
			}
			try {
				engine.load(dsnFile);
			} catch (Exception e) {
				if (!cancelled) {
					e.printStackTrace();
					client.sendResult(-2, -1, null);
				}
				return;
			}
			if (cancelled) {
				return;
			}

			start = currentTimeMillis();
			deadline = start + timeout;
			nextHeartbeat = start + heatbeatInterval;
			nextReportProgress = start + progressInterval;
			engine.start();

			while (!cancelled && !Thread.currentThread().isInterrupted()) {
				long current = currentTimeMillis();
				if (engine.isFinished()) {
					sendFinalSnapshot();
					break;
				}
				Exception failure = engine.failure();
				if (failure != null) {
					throw failure;
				}
				if (current >= deadline) {
					sendFinalSnapshot();
					break;
				}
				if (current >= nextHeartbeat) {
					nextHeartbeat = current + heatbeatInterval;
					client.sendRaw("{\"a\":\"heartbeat\"}\n");
				}
				if (progressInterval > 0 && current >= nextReportProgress) {
					nextReportProgress = current + progressInterval;
					RoutingSnapshot snapshot = engine.awaitProgress();
					if (!cancelled && snapshot != null) {
						client.sendProgress(snapshot.incompleteConnections, snapshot.sessionData);
						nextHeartbeat = current + heatbeatInterval;
					}
				}
				if (!cancelled) {
					Thread.sleep(500);
				}
			}
		} catch (Exception e) {
			if (!cancelled) {
				e.printStackTrace();
				client.sendResult(e instanceof EngineInputException ? -2 : -3, -1, null);
			}
		} finally {
			try {
				engine.stop();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				synchronized (this) {
					myThread = null;
				}
			}
		}
	}

	private void sendFinalSnapshot() throws Exception {
		engine.stop();
		RoutingSnapshot snapshot = engine.snapshot();
		if (!cancelled) {
			client.sendResult(snapshot.incompleteConnections == 0 ? 1 : 0, snapshot.incompleteConnections, snapshot.sessionData);
		}
	}
}
