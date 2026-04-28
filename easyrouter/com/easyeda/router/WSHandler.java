package com.easyeda.router;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.easyeda.utils.Utils;
import com.easyeda.utils.json.JMap;
import com.easyeda.utils.json.JObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

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
	/** 布线任务线程池，大小等于 CPU 核心数 / Thread pool sized to CPU core count */
	private static final ExecutorService threadPool = Executors.newFixedThreadPool(properWorks());

	private static int properWorks() {
		return Math.max(Utils.cpus(), 1);
	}

	private Session session;
	private RouterExecutor executor;

	@Override
	public void onWebSocketBinary(byte[] bytes, int i, int i1) {

	}

	@Override
	public void onWebSocketClose(int i, String s) {
		System.out.println("-------------Connection closed---------------");
		clearRemainExecutor();
	}

	@Override
	public void onWebSocketConnect(Session session) {
		System.out.println("-------------Connection established-----------");
		this.session = session;
	}

	@Override
	public void onWebSocketError(Throwable throwable) {
		clearRemainExecutor();
	}

	private void clearRemainExecutor() {
		if (executor != null) {
			executor.interrupt();
			executor = null;
		}
	}

	@Override
	public void onWebSocketText(String s) {
		try {
			JMap obj = JObject.parseSingle(s).asJMap();
			String a = obj.get("a").asString();
			if (a.equals("startRoute")) {
				clearRemainExecutor();
				System.err.println(s);
				System.out.println("Start routing...");
				int optimizeTime = obj.containsKey("optimizeTime") ? obj.get("optimizeTime").asInt() : 0;
				int progressInterval = obj.containsKey("progressInterval") ? obj.get("progressInterval").asInt() : 2;
				byte[] data = obj.get("data").asString().getBytes();
				int timeout = obj.get("timeout").asInt();
				this.executor = new RouterExecutor(this, data, timeout * 1000, progressInterval * 1000, optimizeTime);
				try {
					threadPool.submit(this.executor);
				} catch (RejectedExecutionException e) {
					sendResult(-2, -1, null);
				}
			} else {
				System.out.println("Routing");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public synchronized void sendRaw(String s) {
		try {
			session.getRemote().sendString(s);
		} catch (IOException e) {
			e.printStackTrace();
			clearRemainExecutor();
		}
	}

	@Override
	public void sendResult(int complete, int inCompleteNetNum, String sesFileData) {
		JMap object = new JMap();
		object.put("a", "routingResult");
		object.put("inCompleteNetNum", inCompleteNetNum);
		object.put("complete", complete);
		if (sesFileData == null)
			object.put("data", new JMap());
		else
			object.put("data", SessionFileUtil.sessionFileToEasyEDA(sesFileData));
		sendRaw(object.toString());
	}

	@Override
	public void sendProgress(int inCompleteNetNum, String sesFileData) {
		JMap object = new JMap();
		object.put("a", "routingProgress");
		object.put("inCompleteNetNum", inCompleteNetNum);
		if (sesFileData != null)
			object.put("data", SessionFileUtil.sessionFileToEasyEDA(sesFileData));
		sendRaw(object.toString());
	}
}