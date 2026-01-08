package com.easyeda.router;

import java.io.IOException;
import java.net.BindException;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import com.easyeda.utils.Config;
import com.easyeda.utils.json.JObject;

public class RouterServer {

	public static void main(String[] args) throws IOException {
		try {
			JObject web = Config.get("main.json").get("web");

			Server server = new Server();

			HttpConfiguration http_config = new HttpConfiguration();

			ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http_config));
			connector.setHost(web.get("ip").asString());
			connector.setPort(web.get("port").asInt());
			connector.setIdleTimeout(web.get("idle").asInt());

			server.setConnectors(new Connector[] { connector });

			ServletHandler handler = new ServletHandler();
			handler.addServletWithMapping(WhoIs.class, "/api/whois");
			handler.addServletWithMapping(WSService.class, "/router");
			server.setHandler(handler);

			server.start();
			System.out.println(
					"Autorouter is running, please keep this window opening， and run the editor \"Auto Router\" function...");
			System.out.println("自动布线已经运行，请保持该窗口开启，并打开编辑器运行“自动布线”功能...");
			server.join();
		} catch (BindException e) {
			System.out.println("Auto Router is already running or address is already in use");
			System.out.println("已经有其他自动布线服务正在运行，或者网络端口已被占用");
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
		System.out.print("press any key to close...");
		System.out.print("按任意键退出...");
		System.in.read();
		System.exit(1);
	}

}
