package com.easyeda.utils.json;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import com.easyeda.utils.Validation;
import com.easyeda.utils.logging.LOGLEVEL;
import com.easyeda.utils.logging.Logger;

public class Parser extends AbstractJsonParser {

	private abstract class State {
		public abstract JObject end() throws InvalidJSON;
	}

	private class ArrayState extends State {
		private final JArray array;

		private ArrayState() {
			this.array = new JArray();
		}

		private void add(JObject o) {
			array.add(o);
		}

		@Override
		public JObject end() throws InvalidJSON {
			return array;
		}

	}

	private class MapState extends State {
		private final JMap map;

		private boolean isKey = true;
		private Object key;

		public MapState() {
			this.map = new JMap();
		}

		private void add(JObject o) {
			if (isKey) {
				key = o;
				isKey = false;
			} else {
				map.put(key, o);
				isKey = true;
			}
		}

		@Override
		public JObject end() throws InvalidJSON {
			if (isKey) {
				return map;
			} else {
				throw new InvalidJSON("incomplete map items");
			}
		}
	}

	private final Logger log;

	private final Deque<State> stateStack = new LinkedList<State>();

	private JObject result = null;
	private int line = 1;
	private int col = 0;

	public Parser(Logger log) {
		this.log = Validation.NPE(log);
	}

	public JObject parseSingle(String content) throws InvalidJSON {
		int len = content.length();
		for (int i = 0; i <= len && result == null; i++) {
			if (i == len) {
				finish();
			} else {
				char c = content.charAt(i);
				feed(c);
			}
		}
		if (result == null) {
			throw new InvalidJSON();
		} else {
			return result;
		}
	}

	public List<JObject> parseMultiple(String content) throws InvalidJSON {
		int len = content.length();
		LinkedList<JObject> results = new LinkedList<JObject>();
		for (int i = 0; i <= len; i++) {
			if (i == len) {
				finish();
			} else {
				char c = content.charAt(i);
				feed(c);
			}
			if (result != null) {
				results.add(result);
				result = null;
			}
		}

		if (results.isEmpty()) {
			throw new InvalidJSON();
		} else {
			return results;
		}
	}

	public void feed(char c) throws InvalidJSON {
		if (c == '\n') {
			line++;
			col = 0;
		} else {
			col++;
		}

		if (log.include(LOGLEVEL.DEBUG)) {
			log.debug("line %d col %d: char %s, state %s", line, col, c, getState().name());
		}

		_feed(c);
	}

	private void auto(JObject o) throws InvalidJSON {
		if (stateStack.isEmpty()) {
			result = o;
		} else {
			State st = stateStack.peek();
			if (st instanceof MapState) {
				((MapState) st).add(o);
			} else if (st instanceof ArrayState) {
				((ArrayState) st).add(o);
			} else {
				throw new InvalidJSON("unknow state " + st.getClass().getName());
			}
		}
	}

	@Override
	public void onMapStart() throws InvalidJSON {
		stateStack.push(new MapState());
	}

	@Override
	public void onArrayStart() throws InvalidJSON {
		stateStack.push(new ArrayState());
	}

	@Override
	public void onMapEnd() throws InvalidJSON {
		State st = stateStack.pop();
		if (st instanceof MapState) {
			auto(st.end());
		} else {
			throw new InvalidJSON("unexpected }");
		}
	}

	@Override
	public void onArrayEnd() throws InvalidJSON {
		State st = stateStack.pop();
		if (st instanceof ArrayState) {
			auto(st.end());
		} else {
			throw new InvalidJSON("unexpected ]");
		}
	}

	@Override
	public void onJObject(JObject o) throws InvalidJSON {
		auto(o);
	}

	public JObject getResult() {
		if (result != null) {
			JObject rst = result;
			result = null;
			return rst;
		} else {
			return null;
		}
	}
}
