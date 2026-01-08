package com.easyeda.utils.logging;

import com.easyeda.utils.Validation;

public class WrappingLogger extends Logger {

	public interface LogWrapper {
		public String wrap(String message);
	}

	private final Logger inner;
	private final LogWrapper wrapper;

	public WrappingLogger(Logger inner, LogWrapper wrapper) {
		super(inner.getLevel());
		this.inner = Validation.NPE(inner);
		this.wrapper = Validation.NPE(wrapper);
	}

	@Override
	protected void _debug(String message) {
		inner._debug(wrapper.wrap(message));
	}

	@Override
	protected void _info(String message) {
		inner._info(wrapper.wrap(message));
	}

	@Override
	protected void _warning(String message) {
		inner._warning(wrapper.wrap(message));
	}

	@Override
	protected void _error(String message) {
		inner._error(wrapper.wrap(message));
	}

}
