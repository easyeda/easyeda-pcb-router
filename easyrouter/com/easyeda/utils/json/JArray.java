package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedList;

import com.easyeda.utils.Utils;

public final class JArray extends JObject implements Iterable<JObject> {

	private static final long serialVersionUID = -4614380302414967309L;

	@SafeVarargs
	public static <T> JArray build(T... values) {
		JArray a = new JArray();
		for (Object o : values) {
			a.add(o);
		}
		return a;
	}

	private final LinkedList<JObject> inner_array;

	public JArray() {
		inner_array = new LinkedList<JObject>();
	}

	public <T> JArray(Iterable<T> a) {
		this();
		for (Object o : a) {
			add(o);
		}
	}

	public void add(JObject o) {
		inner_array.add(o);
	}

	@Override
	public void add(Object o) {
		add(auto(o));
	}

	public JObject get(int index) {
		return inner_array.get(index);
	}

	@Override
	public JArray asJArray() {
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (o.getClass() == JArray.class) {
			JArray a = (JArray) o;
			return this.inner_array.equals(a.inner_array);
		} else {
			return false;
		}
	}

	public boolean isEmpty() {
		return inner_array.isEmpty();
	}

	public int size() {
		return inner_array.size();
	}

	@Override
	public Iterator<JObject> iterator() {
		return inner_array.iterator();
	}

	@Override
	public int hashCode() {
		return inner_array.hashCode();
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		if (indent > 0) {
			w.append('[');
			String offset = "\r\n" + Utils.repeat(" ", (deep + 1) * indent);
			int cnt = inner_array.size();
			if (cnt > 0) {
				for (JObject o : inner_array) {
					cnt--;
					w.append(offset);
					o.toJson(w, deep + 1, indent);
					if (cnt > 0) {
						w.append(",");
					}
				}
				w.append("\r\n");
				w.append(Utils.repeat(" ", deep * indent));
				w.append(']');
			} else {
				w.append(']');
			}
		} else {
			w.append('[');
			int cnt = inner_array.size();
			if (cnt > 0) {
				for (JObject o : inner_array) {
					cnt--;
					o.toJson(w, deep + 1, indent);
					if (cnt > 0) {
						w.append(", ");
					}
				}
				w.append(']');
			} else {
				w.append(']');
			}
		}
	}

}
