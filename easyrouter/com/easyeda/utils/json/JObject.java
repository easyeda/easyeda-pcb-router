package com.easyeda.utils.json;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import com.easyeda.utils.logging.Logger;
import com.easyeda.utils.logging.ScreenLogger;

public abstract class JObject implements Serializable {

	private static final long serialVersionUID = -6546125952176887759L;

	public static JObject parseSingle(String content, Logger log) throws InvalidJSON {
		Parser h = new Parser(log);
		return h.parseSingle(content);
	}

	public static JObject parseSingle(String content) throws InvalidJSON {
		Parser h = new Parser(Logger.DUMMY);
		return h.parseSingle(content);
	}

	public static List<JObject> parseMultiple(String content, ScreenLogger log) throws InvalidJSON {
		Parser h = new Parser(log);
		return h.parseMultiple(content);
	}

	public static List<JObject> parseMultiple(String content) throws InvalidJSON {
		Parser h = new Parser(Logger.DUMMY);
		return h.parseMultiple(content);
	}

	public String toJson() {
		return toJson(0);
	}

	public String toJson(int indent) {
		try (Writer w = new CharArrayWriter()) {
			toJson(w, 0, indent);
			return w.toString();
		} catch (IOException e) {
			// should not happend except memory error
			throw new RuntimeException(e);
		}
	}

	public void toJson(Writer w) throws IOException {
		toJson(w, 0, 0);
	}

	public void toJson(Writer w, int indent) throws IOException {
		toJson(w, 0, indent);
	}

	private JObject get(Object[] keys, int offset, int length) {
		JObject cur = this;
		for (int i = offset; i < length && cur != null; i++) {
			if (JMap.class.isInstance(cur)) {
				cur = cur.asJMap().get(auto(keys[i]));
			} else if (JArray.class.isInstance(cur)) {
				cur = cur.asJArray().get((int) keys[i]);
			} else {
				return null;
			}
		}
		if (cur == null) {
			return null;
		} else {
			return cur;
		}
	}

	public JObject get(Object... keys) {
		if (keys.length < 1) {
			throw new IllegalArgumentException();
		}
		return get(keys, 0, keys.length);
	}

	public JObject getOrDefault(Object... keys) {
		if (keys.length < 2) {
			throw new IllegalArgumentException();
		}
		JObject ret = get(keys, 0, keys.length - 1);
		if (ret == null) {
			return auto(keys[keys.length - 1]);
		} else {
			return ret;
		}
	}

	abstract void toJson(Writer w, int deep, int indent) throws IOException;

	@Override
	public String toString() {
		return toJson();
	}

	public String asString() {
		throw new UnsupportedOperationException();
	}

	public int asInt() {
		throw new UnsupportedOperationException();
	}

	public double asDouble() {
		throw new UnsupportedOperationException();
	}

	public boolean asBoolean() {
		throw new UnsupportedOperationException();
	}

	public JMap asJMap() {
		throw new UnsupportedOperationException();
	}

	public JArray asJArray() {
		throw new UnsupportedOperationException();
	}

	public JObject put(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	public JObject remove(Object key) {
		throw new UnsupportedOperationException();
	}

	public void add(Object o) {
		throw new UnsupportedOperationException();
	}

	public static JObject auto(Object o) {
		if (o == null) {
			return JNull.NULL;
		} else if (o instanceof JObject) {
			return (JObject) o;
		} else {
			if (o instanceof Integer) {
				return new JNumber((int) o);
			} else if (o instanceof Long) {
				return new JNumber((long) o);
			} else if (o instanceof Float) {
				return new JNumber((float) o);
			} else if (o instanceof Double) {
				return new JNumber((double) o);
			} else if (o instanceof Boolean) {
				if ((boolean) o) {
					return JBool.TRUE;
				} else {
					return JBool.FALSE;
				}
			} else if (o instanceof Iterable) {
				return new JArray((Iterable<?>) o);
			} else if (o instanceof Map) {
				return new JMap((Map<?, ?>) o);
			} else {
				return new JString(o.toString());
			}
		}
	}

	public JObject deepCopy() {
		try {
			return JObject.parseSingle(this.toJson());
		} catch (InvalidJSON e) {
			// should not happened
			throw new RuntimeException(e);
		}
	}

	@Override
	public abstract int hashCode();
}
