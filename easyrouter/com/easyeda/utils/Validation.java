package com.easyeda.utils;

public class Validation {

	private static void exceptionOrPass(boolean cmp, String fmt, Object... args) {
		if (cmp) {
			return;
		} else if (args == null || args.length == 0) {
			throw new IllegalArgumentException(fmt);
		} else {
			throw new IllegalArgumentException(String.format(fmt, args));
		}
	}

	public static <T> T validate(T o, boolean cmp, String fmt, Object... args) {
		exceptionOrPass(cmp, fmt, args);
		return o;
	}

	public static <T> T NPE(T o) {
		return validate(o, o != null, "null value is not permitted");
	}

	public static <T> T[] NPEA(T[] c) {
		for (T t : NPE(c)) {
			NPE(t);
		}
		return c;
	}

	public static int validRGB(int o) {
		return validate(o, 0 <= o && 0 <= 255, "RGB should not nagative and not over 255(0xFF)");
	}

	public static double validPositive(double o) {
		return validate(o, 0 <= o, "negative value is not permitted");
	}

	public static String validStringNotEmpty(String s) {
		return validate(s, !nullOrEmpty(s), "null or empty is not permitted");
	}

	public static int validPositive(int o) {
		return validate(o, 0 <= o, "negative value is not permitted");
	}

	public static boolean nullOrEmpty(String s) {
		return s == null || s.length() == 0;
	}

	public static double validNotNaN(double o) {
		if (Double.isNaN(o)) {
			throw new IllegalArgumentException("NaN is not permitted");
		} else {
			return o;
		}
	}

}
