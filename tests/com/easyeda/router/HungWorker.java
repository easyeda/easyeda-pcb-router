package com.easyeda.router;

/** Failure-injection worker for testing the hard stop deadline; never packaged for production. */
public final class HungWorker {

	public static void main(String[] args) throws Exception {
		System.out.println("{\"protocol\":1,\"type\":\"progress\",\"incomplete\":1,\"violations\":0,"
				+ "\"session\":\"(session t (routes (network_out)))\"}");
		Thread.sleep(Long.MAX_VALUE);
	}
}
