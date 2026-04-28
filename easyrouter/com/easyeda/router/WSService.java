package com.easyeda.router;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * WebSocket Servlet 配置，注册到 Jetty 的 {@code /router} 路径。
 * <p>
 * 配置 WebSocket 策略：30 秒空闲超时、20MB 最大消息大小，
 * 并注册 {@link WSHandler} 作为 WebSocket 消息处理器。
 * <p>
 * WebSocket servlet registered at {@code /router}. Configures idle timeout (30s),
 * max message size (20MB), and registers {@link WSHandler} as the handler.
 *
 * @see WSHandler
 * @see RouterServer
 */
public class WSService extends WebSocketServlet {

	private static final long serialVersionUID = -4298181493430182510L;

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.getPolicy().setIdleTimeout(30000);
		factory.getPolicy().setMaxTextMessageBufferSize(20480000);
		factory.getPolicy().setMaxTextMessageSize(20480000);
		factory.register(WSHandler.class);
	}
}
