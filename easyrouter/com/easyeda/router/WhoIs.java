package com.easyeda.router;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.easyeda.utils.Validation;

/**
 * HTTP 健康检查端点，注册在 {@code /api/whois} 路径。
 * <p>
 * 响应 GET 请求，返回 "EasyEDA Auto Router" 字符串，支持 CORS 跨域。
 * EasyEDA 编辑器通过此端点检测本地布线服务是否可用。
 * <p>
 * Health-check HTTP endpoint at {@code /api/whois}. Returns "EasyEDA Auto Router"
 * with CORS support. The EasyEDA editor uses this to detect whether the local
 * routing service is available.
 *
 * @see RouterServer
 */
public class WhoIs extends HttpServlet {

	private static final long serialVersionUID = -3828468974841030820L;

	public WhoIs() {

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String origin = req.getHeader("origin");
		if (!Validation.nullOrEmpty(origin)) {
			resp.setHeader("Access-Control-Allow-Origin", origin);
		}
		resp.getWriter().write("EasyEDA Auto Router");
	}

}
