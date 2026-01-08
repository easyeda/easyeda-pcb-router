package com.easyeda.utils.json;

import java.util.ArrayDeque;

public abstract class AbstractJsonParser {
	enum STAT {
		UNKNOW,
		INSTRING,
		INSTRING_ESCAPE,
		INSTRING_ESCAPE_UNICODE,
		INNUMBER,
		T,
		TR,
		TRU,
		F,
		FA,
		FAL,
		FALS,
		N,
		NU,
		NUL
	}

	private STAT state = STAT.UNKNOW;
	private final ArrayDeque<StringBuilder> buffer_stack = new ArrayDeque<StringBuilder>();

	protected void _feed(char c) throws InvalidJSON {
		switch (state) {
		case UNKNOW: {
			switch (c) {
			case '"': {
				state = STAT.INSTRING;
				startNewBuffer();
				break;
			}
			case '{': {
				onMapStart();
				break;
			}
			case '}': {
				onMapEnd();
				break;
			}
			case '[': {
				onArrayStart();
				break;
			}
			case ']': {
				onArrayEnd();
				break;
			}
			case 'T':
			case 't': {
				state = STAT.T;
				break;
			}
			case 'F':
			case 'f': {
				state = STAT.F;
				break;
			}
			case 'N':
			case 'n': {
				state = STAT.N;
				break;
			}
			case ' ':
			case ':':
			case ',':
			case '\t':
			case '\r':
			case '\n':
			case '\0':
				// ignore
				break;
			default:
				if ('0' <= c && c <= '9' || c == '-') {
					state = STAT.INNUMBER;
					startNewBuffer();
					buffer_stack.peek().append(c);
				} else {
					throw new InvalidJSON();
				}
			}
			break;
		}
		case INSTRING: {
			switch (c) {
			case '"': {
				state = STAT.UNKNOW;
				onJObject(new JString(getReleaseBuffer()));
				break;
			}
			case '\\': {
				state = STAT.INSTRING_ESCAPE;
				break;
			}
			default:
				buffer_stack.peek().append(c);
			}
			break;
		}
		case INSTRING_ESCAPE: {
			switch (c) {
			case 'b': {
				state = STAT.INSTRING;
				buffer_stack.peek().append('\b');
				break;
			}
			case 'f': {
				state = STAT.INSTRING;
				buffer_stack.peek().append('\f');
				break;
			}
			case 'r': {
				state = STAT.INSTRING;
				buffer_stack.peek().append('\r');
				break;
			}
			case 'n': {
				state = STAT.INSTRING;
				buffer_stack.peek().append('\n');
				break;
			}
			case 't': {
				state = STAT.INSTRING;
				buffer_stack.peek().append('\t');
				break;
			}
			case 'u': {
				state = STAT.INSTRING_ESCAPE_UNICODE;
				startNewBuffer();
				break;
			}
			default:
				state = STAT.INSTRING;
				buffer_stack.peek().append(c);
			}
			break;
		}
		case INSTRING_ESCAPE_UNICODE: {
			if (isHexDigit(c)) {
				buffer_stack.peek().append(c);
				if (buffer_stack.peek().length() == 4) {
					String hex_number = getReleaseBuffer();
					buffer_stack.peek().append((char) Integer.parseInt(hex_number, 16));
					state = STAT.INSTRING;
				} else {
					// ignore
				}
			} else {
				throw new InvalidJSON("invalid unicode escape charactor");
			}
			break;
		}
		case INNUMBER: {
			if (isNumber(c)) {
				buffer_stack.peek().append(c);
			} else {
				double number;
				try {
					number = Double.parseDouble(getReleaseBuffer());
				} catch (NumberFormatException e) {
					throw new InvalidJSON("invalid number format");
				}
				onJObject(new JNumber(number));
				state = STAT.UNKNOW;
				_feed(c);
			}
			break;
		}
		case T:
			if (c == 'r' || c == 'R') {
				state = STAT.TR;
			} else {
				throw new InvalidJSON();
			}
			break;
		case TR:
			if (c == 'u' || c == 'U') {
				state = STAT.TRU;
			} else {
				throw new InvalidJSON();
			}
			break;
		case TRU: {
			if (c == 'e' || c == 'E') {
				state = STAT.UNKNOW;
				onJObject(JBool.TRUE);
			} else {
				throw new InvalidJSON();
			}
			break;
		}
		case F:
			if (c == 'a' || c == 'A') {
				state = STAT.FA;
			} else {
				throw new InvalidJSON();
			}
			break;
		case FA:
			if (c == 'l' || c == 'L') {
				state = STAT.FAL;
			} else {
				throw new InvalidJSON();
			}
			break;
		case FAL:
			if (c == 's' || c == 'S') {
				state = STAT.FALS;
			} else {
				throw new InvalidJSON();
			}
			break;
		case FALS: {
			if (c == 'e' || c == 'E') {
				state = STAT.UNKNOW;
				onJObject(JBool.FALSE);
			} else {
				throw new InvalidJSON();
			}
			break;
		}
		case N:
			if (c == 'u' || c == 'U') {
				state = STAT.NU;
			} else {
				throw new InvalidJSON();
			}
			break;
		case NU:
			if (c == 'l' || c == 'L') {
				state = STAT.NUL;
			} else {
				throw new InvalidJSON();
			}
			break;
		case NUL: {
			if (c == 'l' || c == 'L') {
				state = STAT.UNKNOW;
				onJObject(JNull.NULL);
			} else {
				throw new InvalidJSON();
			}
			break;
		}
		default:
			throw new InvalidJSON("failed to parse JSON with bug.");
		}
	}

	private void startNewBuffer() {
		buffer_stack.push(new StringBuilder());
	}

	private String getReleaseBuffer() {
		return buffer_stack.pop().toString();
	}

	private boolean isHexDigit(char c) {
		return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
	}

	private boolean isNumber(char c) {
		return '0' <= c && c <= '9' || c == '.' || c == '+' || c == '-' || c == 'e' || c == 'E';
	}

	public STAT getState() {
		return state;
	}

	public void finish() throws InvalidJSON {
		_feed('\0');
	}

	public abstract void onMapStart() throws InvalidJSON;

	public abstract void onArrayStart() throws InvalidJSON;

	public abstract void onMapEnd() throws InvalidJSON;

	public abstract void onArrayEnd() throws InvalidJSON;

	// JNumber, JNull, JBool, JString
	public abstract void onJObject(JObject o) throws InvalidJSON;
}
