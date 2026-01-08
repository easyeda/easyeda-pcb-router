package com.easyeda.utils;

public interface Singleton {
	public Object getLocker();

	public boolean isInitialized();

	public void initialize() throws Exception;
}
