package com.easyeda.utils;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.easyeda.utils.json.InvalidJSON;
import com.easyeda.utils.json.JObject;

public class Config {

	public static final String env = Utils.NVL(System.getProperty("com.easyeda.env"), "prod");
	private static final ConcurrentHashMap<String, JObject> cache = new ConcurrentHashMap<>();

	public static JObject get(String fileName) {
		return cache.computeIfAbsent(fileName, new Function<String, JObject>() {
			@Override
			public JObject apply(String name) {
				String path = Utils.joinAppPath("config", env, name);
				try {
					return JObject.parseSingle(Utils.readFile(path));
				} catch (InvalidJSON | IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
}
