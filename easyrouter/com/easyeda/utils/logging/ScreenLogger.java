package com.easyeda.utils.logging;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenLogger extends Logger {

	private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public ScreenLogger(LOGLEVEL level) {
		super(level);
	}

	public ScreenLogger() {
		super();
	}

	private String currentTime() {
		return formatter.format(new Date());
	}

	@Override
	protected void _debug(String message) {
		System.out.println(currentTime() + " debug: " + message);
	}

	@Override
	protected void _info(String message) {
		System.out.println(currentTime() + " info: " + message);
	}

	@Override
	protected void _warning(String message) {
		System.out.println(currentTime() + " warning: " + message);
	}

	@Override
	protected void _error(String message) {
		System.err.println(currentTime() + " error: " + message);
	}
}
