package com.easyeda.router;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

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
