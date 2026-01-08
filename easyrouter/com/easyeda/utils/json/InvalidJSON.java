package com.easyeda.utils.json;

public class InvalidJSON extends Exception {

	private static final long serialVersionUID = -5725071800401538144L;

	public InvalidJSON() {
		super();
	}

	public InvalidJSON(String message) {
		super(message);
	}

	public InvalidJSON(Exception e) {
		super(e);
	}
}
