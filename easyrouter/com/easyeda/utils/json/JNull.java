package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;

public final class JNull extends JObject {

	private static final long serialVersionUID = -5340537924901265792L;

	public static final JNull NULL = new JNull();

	private JNull() {

	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		w.write("null");
	}

}
