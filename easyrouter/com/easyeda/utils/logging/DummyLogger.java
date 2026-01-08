package com.easyeda.utils.logging;

public class DummyLogger extends Logger {

	DummyLogger() {
		super(LOGLEVEL.DUMMY);
	}

	@Override
	protected void _debug(String message) {
	}

	@Override
	protected void _info(String message) {
	}

	@Override
	protected void _warning(String message) {
	}

	@Override
	protected void _error(String message) {
	}

}
