package com.easyeda.utils.json;

import java.util.LinkedList;

import com.easyeda.utils.Validation;

public class JsonWatcherParser extends AbstractJsonParser {

	public static interface JsonWatcher {
		public void watcher(String jsonPath, JObject jo) throws Exception;
	}

	public abstract class State {
	}

	private class ArrayState extends State {
		long index = -1;
	}

	private class MapState extends State {
		boolean isKey = true;
		String currentKey = "";
	}

	private final JsonWatcher[] watchers;
	private final LinkedList<State> stateStack = new LinkedList<>();
	private State currentState = null;

	public JsonWatcherParser(JsonWatcher... watchers) {
		for (JsonWatcher w : Validation.NPE(watchers)) {
			Validation.NPE(w);
		}
		this.watchers = watchers;
	}

	public void feed(char c) throws InvalidJSON {
		_feed(c);
	}

	private String buildJsonPath() {
		StringBuilder sb = new StringBuilder();
		for (State s : stateStack) {
			if (s instanceof MapState) {
				if (sb.length() > 0) {
					sb.append(".");
				}
				sb.append(((MapState) s).currentKey);
			} else if (s instanceof ArrayState) {
				sb.append(String.format("[%d]", ((ArrayState) s).index));
			}
		}
		return sb.toString();
	}

	private void exec(JObject jo) throws InvalidJSON {
		for (JsonWatcher w : watchers) {
			try {
				w.watcher(buildJsonPath(), jo);
			} catch (Exception e) {
				throw new InvalidJSON(e);
			}
		}
	}

	@Override
	public void onMapStart() throws InvalidJSON {
		if (currentState instanceof MapState) {
			MapState ms = (MapState) currentState;
			if (ms.isKey) {
				throw new InvalidJSON("unexpected {");
			} else {
				ms.isKey = true;
			}
		}
		currentState = new MapState();
		stateStack.addLast(currentState);
		exec(null);
	}

	@Override
	public void onArrayStart() throws InvalidJSON {
		if (currentState instanceof MapState) {
			MapState ms = (MapState) currentState;
			if (ms.isKey) {
				throw new InvalidJSON("unexpected [");
			} else {
				ms.isKey = true;
			}
		}
		currentState = new ArrayState();
		stateStack.addLast(currentState);
		exec(null);
	}

	@Override
	public void onMapEnd() throws InvalidJSON {
		if (currentState instanceof MapState) {
			MapState ms = (MapState) currentState;
			if (!ms.isKey) {
				throw new InvalidJSON("unexpected }");
			}
			stateStack.removeLast();
			currentState = stateStack.peekLast();
		} else {
			throw new InvalidJSON("unexpected }");
		}
	}

	@Override
	public void onArrayEnd() throws InvalidJSON {
		if (currentState instanceof ArrayState) {
			stateStack.removeLast();
			currentState = stateStack.peekLast();
		} else {
			throw new InvalidJSON("unexpected ]");
		}
	}

	@Override
	public void onJObject(JObject o) throws InvalidJSON {
		if (currentState instanceof ArrayState) {
			((ArrayState) currentState).index++;
			exec(o);
		} else if (currentState instanceof MapState) {
			MapState ms = (MapState) currentState;
			if (ms.isKey) {
				ms.currentKey = o.asString();
				ms.isKey = false;
			} else {
				exec(o);
				ms.isKey = true;
			}
		}
	}

}
