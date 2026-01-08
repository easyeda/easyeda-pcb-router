package com.easyeda.router;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.easyeda.utils.Validation;

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
