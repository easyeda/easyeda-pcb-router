package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;

public final class JString extends JObject {

	private static final long serialVersionUID = 7880039119286718546L;

	private final String inner_string;

	public JString(String s) {
		inner_string = s;
	}

	private static void encode(Writer w, String s) throws IOException {
		int len = s.length();
		w.append('"');
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			switch (c) {
			case '\r':
				w.append("\\r");
				break;
			case '\n':
				w.append("\\n");
				break;
			case '\t':
				w.append("\\t");
				break;
			case '\b':
				w.append("\\b");
				break;
			case '\f':
				w.append("\\f");
				break;
			case '\\':
				w.append("\\\\");
				break;
			case '"':
				w.append("\\\"");
				break;
			default:
				if (0x20 <= c && c <= 0x7e) {
					w.append(c);
				} else {
					w.append(String.format("\\u%04x", (int) c));
				}
			}
		}
		w.append('"');
	}

	@Override
	public String asString() {
		return inner_string;
	}

	@Override
	public boolean equals(Object o) {
		if (o.getClass() == JString.class) {
			return inner_string.equals(((JString) o).inner_string);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return inner_string.hashCode();
	}

	@Override
	public String toString() {
		return inner_string;
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		encode(w, inner_string);
	}
}
