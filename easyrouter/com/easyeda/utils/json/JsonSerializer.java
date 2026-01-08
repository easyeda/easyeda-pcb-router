package com.easyeda.utils.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class JsonSerializer {

	private final Object obj;

	public JsonSerializer(Object obj) {
		this.obj = obj;
	}

	public void go(Writer w) throws IOException {
		JObject.auto(obj).toJson(w);
	}

	public String go() {
		try (StringWriter sw = new StringWriter()) {
			go(sw);
			return sw.toString();
		} catch (IOException e) {
			return null;
		}
	}

}
