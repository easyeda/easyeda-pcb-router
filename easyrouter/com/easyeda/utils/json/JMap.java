package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.easyeda.utils.Utils;

import java.util.Set;

public final class JMap extends JObject {

	private static final long serialVersionUID = 8945823021410448498L;

	public static JMap build(Object... kvs) {
		if (kvs.length % 2 == 0) {
			JMap m = new JMap();
			for (int i = 0; i < kvs.length; i += 2) {
				m.put(kvs[i], kvs[i + 1]);
			}
			return m;
		} else {
			throw new IllegalArgumentException("key-value pair is not completed");
		}
	}

	private final LinkedHashMap<JObject, JObject> inner_map;

	public JMap() {
		inner_map = new LinkedHashMap<>();
	}

	public <K, V> JMap(Map<K, V> m) {
		this();
		for (Entry<K, V> e : m.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	@Override
	public JMap asJMap() {
		return this;
	}

	public int size() {
		return inner_map.size();
	}

	public boolean isEmpty() {
		return inner_map.isEmpty();
	}

	public boolean containsKey(Object key) {
		return inner_map.containsKey(auto(key));
	}

	public boolean containsValue(Object value) {
		return inner_map.containsValue(auto(value));
	}

	public JObject get(JObject key) {
		return inner_map.get(key);
	}

	public JObject put(JObject key, JObject value) {
		return inner_map.put(key, value);
	}

	@Override
	public JObject put(Object key, Object value) {
		return inner_map.put(auto(key), auto(value));
	}

	@Override
	public JObject remove(Object key) {
		return inner_map.remove(auto(key));
	}

	public void clear() {
		inner_map.clear();
	}

	public Set<JObject> keySet() {
		return inner_map.keySet();
	}

	public Collection<JObject> values() {
		return inner_map.values();
	}

	public Set<Entry<JObject, JObject>> entrySet() {
		return inner_map.entrySet();
	}

	@Override
	public int hashCode() {
		return inner_map.hashCode();
	}

	@Override
	void toJson(Writer w, int deep, int indent) throws IOException {
		if (indent > 0) {
			String offset = "\r\n" + Utils.repeat(" ", (deep + 1) * indent);
			w.append('{');
			int cnt = inner_map.size();
			if (cnt > 0) {
				for (Entry<JObject, JObject> i : entrySet()) {
					cnt--;
					w.append(offset);
					i.getKey().toJson(w, deep + 1, indent);
					w.append(": ");
					i.getValue().toJson(w, deep + 1, indent);
					if (cnt > 0) {
						w.append(',');
					}
				}
				w.append("\r\n" + Utils.repeat(" ", deep * indent));
				w.append('}');
			} else {
				w.append('}');
			}
		} else {
			w.append('{');
			int cnt = inner_map.size();
			if (cnt > 0) {
				for (Entry<JObject, JObject> i : entrySet()) {
					cnt--;
					i.getKey().toJson(w, deep + 1, indent);
					w.append(": ");
					i.getValue().toJson(w, deep + 1, indent);
					if (cnt > 0) {
						w.append(", ");
					}
				}
				w.append('}');
			} else {
				w.append('}');
			}
		}
	}
}
