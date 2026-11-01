package com.easyeda.router.engine;

/**
 * One routing job, independent of a particular Freerouting API or runtime.
 * Methods are called by the owning executor thread; cancellation interrupts
 * that thread. Implementations must release their resources in stop().
 */
public interface RoutingEngine {

	void load(byte[] dsnData) throws Exception;

	void start() throws Exception;

	boolean isFinished();

	Exception failure();

	/** Wait briefly for consistent progress; return null if none is available. */
	RoutingSnapshot awaitProgress() throws InterruptedException;

	/** Export a consistent final/partial result after stop(). */
	RoutingSnapshot snapshot() throws Exception;

	/** Stop routing; retain the final snapshot. Must tolerate repeated calls. */
	void stop() throws InterruptedException;
}
