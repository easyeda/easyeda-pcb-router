package com.easyeda.utils.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.easyeda.utils.Validation;

public class JsonDeserializer extends AbstractJsonParser {

	public static Object des(String src) throws InvalidJSON {
		Validation.NPE(src);
		try (Reader rd = new StringReader(src)) {
			return des(rd);
		} catch (IOException e) {
			return null;
		}
	}

	public static Object des(Reader rd) throws IOException, InvalidJSON {
		Validation.NPE(rd);
		JsonDeserializer des = new JsonDeserializer();
		for (;;) {
			int b = rd.read();
			if (b == -1) {
				break;
			} else {
				des._feed((char) b);
			}
		}
		return des.result();
	}

	private final List<Object> results = new LinkedList<>();
	private final Deque<State> stateStack = new LinkedList<State>();

	private abstract class State {
		public abstract Object end() throws InvalidJSON;
	}

	private class ArrayState extends State {
		private final List<Object> array;

		private ArrayState() {
			this.array = new LinkedList<>();
		}

		private void add(Object o) {
			array.add(o);
		}

		@Override
		public Object end() throws InvalidJSON {
			return array;
		}

	}

	private class MapState extends State {
		private final Map<Object, Object> map;

		private boolean isKey = true;
		private Object key;

		public MapState() {
			this.map = new LinkedHashMap<>();
		}

		private void add(Object o) {
			if (isKey) {
				key = o;
				isKey = false;
			} else {
				map.put(key, o);
				isKey = true;
			}
		}

		@Override
		public Object end() throws InvalidJSON {
			if (isKey) {
				return map;
			} else {
				throw new InvalidJSON("incomplete map items");
			}
		}
	}

	public JsonDeserializer() {

	}

	@Override
	public void onMapStart() throws InvalidJSON {
		stateStack.addLast(new MapState());
	}

	@Override
	public void onArrayStart() throws InvalidJSON {
		stateStack.addLast(new ArrayState());
	}

	@Override
	public void onMapEnd() throws InvalidJSON {
		State st = stateStack.pop();
		if (st == null || !(st instanceof MapState)) {
			throw new InvalidJSON("unexpected }");
		} else {
			auto(st.end());
		}
	}

	@Override
	public void onArrayEnd() throws InvalidJSON {
		State st = stateStack.pop();
		if (st == null || !(st instanceof ArrayState)) {
			throw new InvalidJSON("unexpected ]");
		} else {
			auto(st.end());
		}
	}

	@Override
	public void onJObject(JObject o) throws InvalidJSON {
		Object deserialized;
		if (o instanceof JString) {
			deserialized = o.asString();
		} else if (o instanceof JNumber) {
			deserialized = o.asDouble();
		} else if (o instanceof JBool) {
			deserialized = o.asBoolean();
		} else if (o instanceof JNull) {
			deserialized = null;
		} else {
			throw new InvalidJSON(String.format("unknow type %s to desrialize", o.getClass().getName()));
		}

		auto(deserialized);
	}

	private void auto(Object o) throws InvalidJSON {
		State current = stateStack.peekLast();
		if (current == null) {
			results.add(o);
		} else if (current instanceof MapState) {
			((MapState) current).add(o);
		} else if (current instanceof ArrayState) {
			((ArrayState) current).add(o);
		} else {
			throw new InvalidJSON("unknow state " + current.getClass().getName());
		}
	}

	private Object result() {
		int size = results.size();
		if (size == 0) {
			return null;
		} else if (size == 1) {
			return results.get(0);
		} else {
			return results;
		}
	}
}
