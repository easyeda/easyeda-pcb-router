package com.easyeda.utils;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.easyeda.utils.json.InvalidJSON;
import com.easyeda.utils.json.JObject;

/**
 * 配置文件加载器，带线程安全缓存。
 * <p>
 * 根据 JVM 属性 {@code com.easyeda.env}（默认 "prod"）确定配置目录，
 * 从 {@code config/<env>/<fileName>} 加载 JSON 配置文件。
 * 首次加载后缓存在内存中，后续调用直接返回缓存。
 * <p>
 * Thread-safe configuration loader with caching. Reads JSON config files from
 * {@code config/<env>/<fileName>} where env is determined by the
 * {@code com.easyeda.env} JVM property (defaults to "prod").
 *
 * @see Utils#joinAppPath(String...)
 */
public class Config {

	/** 当前运行环境，由 -Dcom.easyeda.env 指定，默认 "prod" */
	public static final String env = Utils.NVL(System.getProperty("com.easyeda.env"), "prod");
	private static final ConcurrentHashMap<String, JObject> cache = new ConcurrentHashMap<>();

	/**
	 * 获取指定配置文件的 JSON 对象（带缓存）。
	 *
	 * @param fileName 配置文件名，如 "main.json"
	 * @return 解析后的 JSON 对象
	 * @throws RuntimeException 如果文件不存在或 JSON 格式错误
	 */
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
