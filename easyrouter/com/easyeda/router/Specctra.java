package com.easyeda.router;

import java.util.ArrayList;
import java.util.List;

/** Small, engine-independent reader for Specctra S-expressions. */
public final class Specctra {

	public static final class Node {
		public final List<Object> values = new ArrayList<Object>();

		public String atom(int index) {
			if (index >= values.size() || !(values.get(index) instanceof String)) {
				throw new IllegalArgumentException("Missing Specctra atom " + index);
			}
			return (String) values.get(index);
		}

		public Node child(String name) {
			for (Object value : values) {
				if (value instanceof Node && ((Node) value).named(name)) {
					return (Node) value;
				}
			}
			return null;
		}

		public boolean named(String name) {
			return !values.isEmpty() && values.get(0) instanceof String
					&& name.equalsIgnoreCase((String) values.get(0));
		}
	}

	private final String text;
	private int offset;

	private Specctra(String text) { this.text = text; }

	public static Node parse(String text) {
		if (text == null || text.length() > 20480000) {
			throw new IllegalArgumentException("Missing or oversized Specctra data");
		}
		Specctra reader = new Specctra(text);
		reader.space();
		Node root = reader.node(0);
		reader.space();
		if (reader.offset != text.length()) {
			throw new IllegalArgumentException("Trailing Specctra data");
		}
		return root;
	}

	private Node node(int depth) {
		if (depth > 128 || offset >= text.length() || text.charAt(offset++) != '(') {
			throw new IllegalArgumentException("Invalid Specctra scope");
		}
		Node node = new Node();
		while (true) {
			space();
			if (offset >= text.length()) {
				throw new IllegalArgumentException("Unclosed Specctra scope");
			}
			char c = text.charAt(offset);
			if (c == ')') {
				offset++;
				return node;
			}
			if (c == '"' && node.named("string_quote") && node.values.size() == 1) {
				int end = offset + 1;
				while (end < text.length() && Character.isWhitespace(text.charAt(end))) { end++; }
				if (end < text.length() && text.charAt(end) == ')') {
					offset++;
					node.values.add("\"");
					continue;
				}
			}
			node.values.add(c == '(' ? node(depth + 1) : atom());
		}
	}

	private String atom() {
		if (text.charAt(offset) == '"') {
			offset++;
			StringBuilder atom = new StringBuilder();
			while (offset < text.length()) {
				char c = text.charAt(offset++);
				if (c == '"') {
					return atom.toString();
				}
				if (c == '\\' && offset < text.length()
						&& (text.charAt(offset) == '\\' || text.charAt(offset) == '"')) {
					c = text.charAt(offset++);
				}
				atom.append(c);
			}
			throw new IllegalArgumentException("Unclosed Specctra string");
		}
		int begin = offset;
		while (offset < text.length() && !Character.isWhitespace(text.charAt(offset))
				&& text.charAt(offset) != '(' && text.charAt(offset) != ')') {
			offset++;
		}
		return text.substring(begin, offset);
	}

	private void space() {
		while (offset < text.length()) {
			char c = text.charAt(offset);
			if (Character.isWhitespace(c) || c == '\uFEFF') {
				offset++;
			} else if (c == ';') {
				while (offset < text.length() && text.charAt(offset) != '\n') { offset++; }
			} else {
				return;
			}
		}
	}

	public static double number(String text) {
		String[] fraction = text.split("/", -1);
		double result = Double.parseDouble(fraction[0]);
		if (fraction.length == 2) {
			result /= Double.parseDouble(fraction[1]);
		} else if (fraction.length != 1) {
			throw new IllegalArgumentException("Invalid Specctra number: " + text);
		}
		if (!Double.isFinite(result)) {
			throw new IllegalArgumentException("Non-finite Specctra number");
		}
		return result;
	}
}
