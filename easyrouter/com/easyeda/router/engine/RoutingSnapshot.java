package com.easyeda.router.engine;

/** Immutable engine-independent routing state and optional SES geometry. */
public final class RoutingSnapshot {

	public final int incompleteConnections;
	public final String sessionData;
	/** Engine-reported DRC count; -1 if unavailable. Compare definitions before benchmarking. */
	public final int clearanceViolations;

	public RoutingSnapshot(int incompleteConnections, String sessionData) {
		this(incompleteConnections, sessionData, -1);
	}

	public RoutingSnapshot(int incompleteConnections, String sessionData, int clearanceViolations) {
		this.incompleteConnections = incompleteConnections;
		this.sessionData = sessionData;
		this.clearanceViolations = clearanceViolations;
	}
}
