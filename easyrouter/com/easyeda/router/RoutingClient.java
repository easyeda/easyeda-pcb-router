package com.easyeda.router;

/**
 * 布线结果回调接口，由 WebSocket 处理器实现，用于将布线进度和结果推送给客户端。
 * <p>
 * Routing result callback interface, implemented by the WebSocket handler
 * to push routing progress and results back to the client.
 *
 * @see WSHandler
 * @see RouterExecutor
 */
public interface RoutingClient {

	/**
	 * 发送布线最终结果。
	 * <p>
	 * Send the final routing result to the client.
	 *
	 * @param resultCode      结果码 / Result code:
	 *                        <ul>
	 *                          <li>{@code  1} — 布线全部完成 / Routing completed successfully</li>
	 *                          <li>{@code  0} — 超时未完成 / Timed out before completion</li>
	 *                          <li>{@code -1} — 服务器忙 / Server busy (thread pool full)</li>
	 *                          <li>{@code -2} — 无法打开输入文件 / Failed to open DSN input</li>
	 *                        </ul>
	 * @param inCompleteNetNum 未完成连接数 / Number of incomplete net connections (-1 if unknown)
	 * @param sesFileData      Specctra Session 文件文本，为 null 表示无数据 / Specctra SES file text, null if unavailable
	 */
	public void sendResult(int resultCode, int inCompleteNetNum, String sesFileData);

	/**
	 * 发送布线中间进度。
	 * <p>
	 * Send intermediate routing progress to the client.
	 *
	 * @param inCompleteNetNum 当前未完成连接数 / Current number of incomplete net connections
	 * @param sesFileData      当前阶段的 Specctra Session 文件文本 / Current Specctra SES file text snapshot
	 */
	public void sendProgress(int inCompleteNetNum, String sesFileData);

	/**
	 * 发送原始字符串消息（如心跳包）。
	 * <p>
	 * Send a raw string message (e.g., heartbeat).
	 *
	 * @param s 原始 JSON 字符串 / Raw JSON string to send
	 */
	void sendRaw(String s);
}
