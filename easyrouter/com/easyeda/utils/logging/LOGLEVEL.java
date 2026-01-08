package com.easyeda.utils.logging;

public enum LOGLEVEL {
	DEBUG(0),
	INFO(1),
	WARNING(2),
	ERROR(3),
	DUMMY(4);

	private final int priority;

	private LOGLEVEL(int priority) {
		this.priority = priority;
	}

	public int getPriority() {
		return priority;
	}

	public boolean include(LOGLEVEL l) {
		return priority <= l.priority;
	}
}
