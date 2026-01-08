package com.easyeda.utils.logging;

import com.easyeda.utils.Utils;

public abstract class Logger {

	public static final DummyLogger DUMMY = new DummyLogger();

	public static LOGLEVEL toLevel(String level) {
		return Enum.valueOf(LOGLEVEL.class, level.toUpperCase());
	}

	private LOGLEVEL level;

	public Logger(LOGLEVEL level) {
		this.level = level;
	}

	public Logger() {
		this.level = LOGLEVEL.ERROR;
	}

	public LOGLEVEL getLevel() {
		return level;
	}

	public boolean include(LOGLEVEL l) {
		return level.include(l);
	}

	public void debug(String format, Object... args) {
		if (include(LOGLEVEL.DEBUG)) {
			_debug(String.format(format, args));
		}
	}

	public void debug(String message) {
		if (include(LOGLEVEL.DEBUG)) {
			_debug(message);
		}
	}

	public void debug(Object o) {
		if (include(LOGLEVEL.DEBUG)) {
			_debug(o.toString());
		}
	}

	public void info(String format, Object... args) {
		if (include(LOGLEVEL.INFO)) {
			_info(String.format(format, args));
		}
	}

	public void info(String message) {
		if (include(LOGLEVEL.INFO)) {
			_info(message);
		}
	}

	public void info(Object o) {
		if (include(LOGLEVEL.INFO)) {
			_info(o.toString());
		}
	}

	public void warning(String format, Object... args) {
		if (include(LOGLEVEL.WARNING)) {
			_warning(String.format(format, args));
		}
	}

	public void warning(String message) {
		if (include(LOGLEVEL.WARNING)) {
			_warning(message);
		}
	}

	public void warning(Object o) {
		if (include(LOGLEVEL.WARNING)) {
			_warning(o.toString());
		}
	}

	public void error(String format, Object... args) {
		if (include(LOGLEVEL.ERROR)) {
			_error(String.format(format, args));
		}
	}

	public void error(String message) {
		if (include(LOGLEVEL.ERROR)) {
			_error(message);
		}
	}

	public void error(Object o) {
		if (include(LOGLEVEL.ERROR)) {
			_error(o.toString());
		}
	}

	public void error(Throwable o) {
		if (include(LOGLEVEL.ERROR)) {
			_error(Utils.getStackTrace(o));
		}
	}

	protected abstract void _debug(String message);

	protected abstract void _info(String message);

	protected abstract void _warning(String message);

	protected abstract void _error(String message);
}
