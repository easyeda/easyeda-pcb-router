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

public class WSHandler implements WebSocketListener, RoutingClient {
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