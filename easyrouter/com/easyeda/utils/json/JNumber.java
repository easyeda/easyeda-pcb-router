package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

public final class JNumber extends JObject {

	private static final long serialVersionUID = -6424062415921792849L;

	private final double inner_number;

	public JNumber(double n) {
		inner_number = n;
	}

	@Override
	public int asInt() {
		return (int) inner_number;
	}

	@Override
	public double asDouble() {
		return inner_number;
	}

	@Override
	public boolean equals(Object o) {
		if (o.getClass() == JNumber.class) {
			return inner_number == ((JNumber) o).inner_number;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Double.hashCode(inner_number);
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		double num;
		if (inner_number == Double.POSITIVE_INFINITY) {
			num = Double.MAX_VALUE;
		} else if (inner_number == Double.NEGATIVE_INFINITY) {
			num = -Double.MAX_VALUE;
		} else if (Double.isNaN(inner_number)) {
			num = 0;
		} else {
			num = inner_number;
		}
		if (num % 1 == 0) {
			w.write(String.format(Locale.ENGLISH, "%d", (long) num));
		} else {
			w.write(String.format(Locale.ENGLISH, "%g", num));
		}
	}
}
