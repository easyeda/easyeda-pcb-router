package com.easyeda.router;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.easyeda.router.engine.EngineFactory;
import com.easyeda.utils.Config;
import com.easyeda.utils.json.JMap;
import com.easyeda.utils.json.JObject;

/**
 * WebSocket 消息处理器，同时实现 {@link RoutingClient} 接口用于向客户端推送结果。
 * <p>
 * 每个 WebSocket 连接对应一个 WSHandler 实例。收到 {@code startRoute} 消息后，
 * 创建 {@link RouterExecutor} 并提交到线程池执行自动布线。布线完成或超时后，
 * 通过 WebSocket 将结果以 JSON 格式推送回客户端。
 * <p>
 * WebSocket message handler that also implements {@link RoutingClient} to push
 * results back to the client. Each WebSocket connection gets its own WSHandler instance.
 * On receiving a {@code startRoute} message, it creates a {@link RouterExecutor}
 * and submits it to a fixed thread pool for execution.
 *
 * <h3>WebSocket 协议 / Protocol</h3>
 * <p>客户端发送 / Client sends:</p>
 * <pre>{@code
 * {
 *   "a": "startRoute",
 *   "data": "<DSN file content>",
 *   "timeout": 30,
 *   "progressInterval": 10,
 *   "optimizeTime": 5
 * }
 * }</pre>
 * <p>服务端响应 / Server responds:</p>
 * <pre>{@code
 * {"a": "heartbeat"}
 * {"a": "routingProgress", "inCompleteNetNum": 5, "data": {...}}
 * {"a": "routingResult", "complete": 1, "inCompleteNetNum": 0, "data": {...}}
 * }</pre>
 *
 * @see RouterExecutor
 * @see RoutingClient
 * @see WSService
 */
public class WSHandler implements WebSocketListener, RoutingClient {
	private static final ThreadPoolExecutor threadPool = createPool();

	private static ThreadPoolExecutor createPool() {
		JObject router = Config.get("main.json").get("router");
		int active = EngineFactory.positive(router, "max_active_jobs", 1, 64);
		int queued = EngineFactory.positive(router, "max_queued_jobs", 4, 10000);
		ThreadPoolExecutor pool = new ThreadPoolExecutor(active, active, 0, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<Runnable>(queued));
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			pool.shutdownNow();
			try { pool.awaitTermination(35, TimeUnit.SECONDS); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}, "routing-pool-shutdown"));
		return pool;
	}

	private Session session;
	private RouterExecutor executor;
	private Future<?> task;
	private long generation;

	@Override
	public void onWebSocketBinary(byte[] bytes, int i, int i1) {

	}

	@Override
	public synchronized void onWebSocketClose(int i, String s) {
		System.out.println("-------------Connection closed---------------");
		clearRemainExecutor();
	}

	@Override
	public synchronized void onWebSocketConnect(Session session) {
		System.out.println("-------------Connection established-----------");
		this.session = session;
	}

	@Override
	public synchronized void onWebSocketError(Throwable throwable) {
		clearRemainExecutor();
	}

	private synchronized void clearRemainExecutor() {
		generation++;
		if (executor != null) { executor.interrupt(); executor = null; }
		if (task != null) {
			task.cancel(true);
			if (task instanceof Runnable) { threadPool.remove((Runnable) task); }
			task = null;
		}
	}

	@Override
	public synchronized void onWebSocketText(String text) {
		try {
			JObject obj = JObject.parseSingle(text);
			String action = obj.get("a").asString();
			if ("heartbeat".equals(action)) { return; }
			if (!"startRoute".equals(action)) { throw new IllegalArgumentException("Unknown action"); }
			clearRemainExecutor();
			String dsn = obj.get("data").asString();
			int timeout = seconds(obj.get("timeout"), false);
			int progress = obj.get("progressInterval") == null ? 2000 : seconds(obj.get("progressInterval"), true);
			int passes = obj.get("optimizeTime") == null ? 0 : obj.get("optimizeTime").asInt();
			if (passes < 0 || passes > 1000 || (obj.get("optimizeTime") != null
					&& obj.get("optimizeTime").asDouble() != passes)) { throw new IllegalArgumentException("Invalid optimization pass count"); }
			JObject settings = Config.get("main.json").get("router");
			RoutingDiagnostics diagnostics = RoutingDiagnostics.capture(text, EngineFactory.selected(settings), settings);
			RoutingClient client = new JobClient(generation, dsn, diagnostics);
			executor = new RouterExecutor(client, dsn.getBytes(StandardCharsets.UTF_8), timeout, progress, passes);
			try { task = threadPool.submit(executor); }
			catch (RejectedExecutionException e) {
				executor = null;
				client.sendResult(-1, -1, null);
			}
		} catch (Exception e) {
			clearRemainExecutor();
			sendResult(-2, -1, null);
		}
	}

	private static int seconds(JObject value, boolean zeroAllowed) {
		double seconds = value.asDouble();
		if (!Double.isFinite(seconds) || seconds < (zeroAllowed ? 0 : 1) || seconds > 86400
				|| seconds != Math.floor(seconds)) {
			throw new IllegalArgumentException("Time must be integer seconds from " + (zeroAllowed ? 0 : 1) + " to 86400");
		}
		return (int) (seconds * 1000);
	}

	/** Suppress callbacks from old or completed jobs, including replacement/cancel races. */
	private final class JobClient implements RoutingClient {
		private final long id;
		private final String dsn;
		private final RoutingDiagnostics diagnostics;
		private boolean complete;

		JobClient(long id, String dsn, RoutingDiagnostics diagnostics) {
			this.id = id;
			this.dsn = dsn;
			this.diagnostics = diagnostics;
		}

		private boolean active() {
			return id == generation && !complete && session != null && session.isOpen();
		}

		@Override public void sendRaw(String data) {
			synchronized (WSHandler.this) { if (active()) { WSHandler.this.sendRaw(data); } }
		}

		@Override public void sendProgress(int count, String ses) {
			synchronized (WSHandler.this) {
				if (active()) { sendPacket("routingProgress", 0, count, ses, dsn, diagnostics); }
			}
		}

		@Override public void sendResult(int code, int count, String ses) {
			synchronized (WSHandler.this) {
				if (active()) {
					complete = true;
					try { sendPacket("routingResult", code, count, ses, dsn, diagnostics); }
					finally { executor = null; task = null; }
				}
			}
		}
	}

	@Override
	public synchronized void sendRaw(String s) {
		try {
			if (session != null && session.isOpen()) { session.getRemote().sendString(s); }
		} catch (IOException e) {
			e.printStackTrace();
			clearRemainExecutor();
		}
	}

	@Override
	public synchronized void sendResult(int code, int count, String ses) {
		sendPacket("routingResult", code, count, ses, null, null);
	}

	@Override
	public synchronized void sendProgress(int count, String ses) {
		sendPacket("routingProgress", 0, count, ses, null, null);
	}

	private void sendPacket(String action, int code, int count, String ses, String dsn, RoutingDiagnostics diagnostics) {
		JMap packet = new JMap();
		packet.put("a", action);
		packet.put("inCompleteNetNum", count);
		if (action.equals("routingResult")) { packet.put("complete", code); }
		try {
			if (ses != null) { packet.put("data", SessionFileUtil.sessionFileToEasyEDA(ses, dsn)); }
			else if (action.equals("routingResult")) { packet.put("data", new JMap()); }
		} catch (IllegalArgumentException e) {
			// Never replace browser geometry with partially converted/invalid session data.
			if (action.equals("routingProgress")) { return; }
			packet.put("complete", -3);
			packet.put("data", new JMap());
			System.err.println("SES conversion failed: " + e.getMessage());
		}
		String serialized = packet.toString();
		if (diagnostics != null) { diagnostics.record(action, serialized, ses); }
		sendRaw(serialized);
	}
}
