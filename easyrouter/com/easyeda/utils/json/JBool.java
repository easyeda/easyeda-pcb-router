package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;

public final class JBool extends JObject {

	private static final long serialVersionUID = 3120889392267058495L;

	public static final JBool TRUE = new JBool(true);
	public static final JBool FALSE = new JBool(false);

	private final boolean inner_boolean;
	private final String display;

	private JBool(boolean b) {
		inner_boolean = b;
		display = inner_boolean ? "true" : "false";
	}

	@Override
	public boolean asBoolean() {
		return inner_boolean;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public int hashCode() {
		return Boolean.hashCode(inner_boolean);
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		w.write(display);
	}

}
