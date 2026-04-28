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

/**
 * EasyEDA PCB 自动布线 WebSocket 服务器入口。
 * <p>
 * 基于 Jetty 启动 HTTP/WebSocket 服务，提供两个端点：
 * <ul>
 *   <li>{@code /api/whois} — HTTP GET 健康检查，返回 "EasyEDA Auto Router"</li>
 *   <li>{@code /router}   — WebSocket 端点，接收 DSN 数据并执行自动布线</li>
 * </ul>
 * 服务器配置从 {@code config/<env>/main.json} 的 {@code web} 节点读取（IP、端口、空闲超时）。
 * <p>
 * Main entry point for the EasyEDA PCB auto-routing WebSocket server.
 * Starts a Jetty HTTP/WebSocket server with two endpoints:
 * health-check ({@code /api/whois}) and routing ({@code /router}).
 * Configuration is loaded from {@code config/<env>/main.json}.
 *
 * @see WSService
 * @see WSHandler
 * @see WhoIs
 */
public class RouterServer {

	/**
	 * 启动自动布线服务器。
	 * <p>
	 * 从配置文件读取 IP/端口/空闲超时，注册 Servlet 端点并启动 Jetty 服务。
	 * 如果端口已被占用，输出提示信息并等待用户按键退出。
	 *
	 * @param args 命令行参数（未使用）
	 * @throws IOException 如果等待用户输入时发生 I/O 错误
	 */
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
