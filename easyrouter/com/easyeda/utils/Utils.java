package com.easyeda.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

public class Utils {

	public static final String APP_PATH;

	static {
		File parent = new File(getClassPath(Utils.class)).getParentFile();
		if (parent.getName().equals("bin")) {
			APP_PATH = parent.getParent();
		} else {
			APP_PATH = parent.getAbsolutePath();
		}
	}

	private static final Pattern r_abs_url = Pattern.compile("^([a-z0-9]+)://([^/]+)(/.*)?$", Pattern.CASE_INSENSITIVE);

	public static String getClassPath(Class<?> cls) {
		try {
			return cls.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static String join(String sperator, Iterator<?> iter) {
		return join(sperator, null, iter);
	}

	public static String join(String sperator, String initialPath, Iterator<?> iter) {
		StringBuilder sb = new StringBuilder();
		if (initialPath != null) {
			sb.append(initialPath);
			sb.append(sperator);
		}
		if (iter.hasNext()) {
			sb.append(iter.next().toString());
			while (iter.hasNext()) {
				sb.append(sperator);
				sb.append(iter.next().toString());
			}
			return sb.toString();
		} else {
			return "";
		}
	}

	public static <T> String join(String sperator, List<T> l) {
		return join(sperator, l.iterator());
	}

	@SafeVarargs
	public static <T> String join(String sperator, T... args) {
		return join(sperator, Arrays.asList(args).iterator());
	}

	public static String joinPath(String... args) {
		return join(File.separator, args);
	}

	public static String joinAppPath(String... args) {
		return join(File.separator, APP_PATH, Arrays.asList(args).iterator());
	}

	public static String repeat(String base, int times) {
		if (times <= 0) {
			return "";
		} else {
			StringBuilder sb = new StringBuilder(base.length() * times);
			for (int i = 0; i < times; i++) {
				sb.append(base);
			}
			return sb.toString();
		}
	}

	public static String repeat(char c, int times) {
		return repeat(Character.valueOf(c).toString(), times);
	}

	public static byte[] readAll(InputStream is) throws IOException {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			streamTunnel(is, os);
			return os.toByteArray();
		}
	}

	public static String readFile(String path) throws IOException {
		char[] buffer = new char[8192];
		StringBuilder sb = new StringBuilder();
		FileReader fr;
		fr = new FileReader(path);
		try {
			while (fr.ready()) {
				int nread = fr.read(buffer);
				sb.append(buffer, 0, nread);
			}
		} finally {
			fr.close();
		}
		return sb.toString();
	}

	public static String MD5(byte[] data) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			// i think it should never happened
			return null;
		}
		byte[] hash = md.digest(data);
		StringBuilder sb = new StringBuilder(hash.length * 2);
		for (int i = 0; i < hash.length; i++) {
			sb.append(String.format("%02X", hash[i]));
		}
		return sb.toString();
	}

	public static void singleton(Singleton st) throws Exception {
		if (!st.isInitialized()) {
			synchronized (st.getLocker()) {
				if (!st.isInitialized()) {
					st.initialize();
				}
			}
		}
	}

	public static String UUID() {
		return UUID.randomUUID().toString();
	}

	public static void loadJarFile(File path) throws Exception {
		URL url = path.toURI().toURL();
		URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
		method.setAccessible(true);
		method.invoke(classLoader, url);
	}

	public static String getStackTrace(Throwable e) {
		return getStackTrace("", e);
	}

	private static String getStackTrace(String prefix, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(prefix + getStackTraceHead(e) + "\r\n");
		for (StackTraceElement ele : e.getStackTrace()) {
			sb.append(prefix + "    " + ele.toString() + "\r\n");
		}
		if (e.getCause() != null) {
			sb.append(prefix + " caused by:\r\n");
			sb.append(getStackTrace(prefix + "    ", e.getCause()) + "\r\n");
		}
		return sb.toString();
	}

	public static String getStackTraceHead(Throwable e) {
		String cls = e.getClass().getName();
		String msg = e.getMessage();
		if (msg == null) {
			return cls;
		} else {
			return String.format("%s: %s", cls, msg);
		}
	}

	public static String resolveUrl(String base, String target) {
		if (r_abs_url.matcher(target).matches()) {
			return target;
		} else {
			Matcher m = r_abs_url.matcher(base);
			if (m.matches()) {
				String protocol = m.group(1);
				String domain = m.group(2);
				String uri = m.group(3);
				if (target.startsWith("/")) {
					return String.format("%s://%s%s", protocol, domain, target);
				} else if (target.startsWith("?") || target.startsWith("#")) {
					return base + target;
				} else {
					if (uri == null) {
						return String.format("%s://%s/%s", protocol, domain, target);
					} else {
						int slashIdx = uri.lastIndexOf('/');
						if (slashIdx == -1) {
							return String.format("%s://%s%s/%s", protocol, domain, uri, target);
						} else {
							return String.format("%s://%s%s/%s", protocol, domain, uri.substring(0, slashIdx), target);
						}
					}
				}
			} else {
				throw new UnsupportedOperationException("Invalid base url " + base);
			}
		}
	}

	public static String getExt(char sperator, String path) {
		// firstly we remove the # or ? part of an url
		int firstSpecialPart = Math.max(path.indexOf('#'), path.indexOf('?'));
		if (firstSpecialPart != -1) {
			path = path.substring(0, firstSpecialPart);
		} else {
			// ignore
		}

		// secondly we get the real file name
		int lastSperatorIdx = path.lastIndexOf(sperator);
		if (lastSperatorIdx != -1) {
			path = path.substring(lastSperatorIdx);
		} else {
			// ignore
		}

		// finally we find the extension name
		int lastDotIdx = path.lastIndexOf('.');
		if (lastDotIdx != -1 && lastDotIdx + 1 < path.length()) {
			return path.substring(lastDotIdx + 1);
		} else {
			return "";
		}
	}

	public static String getExt(String path) {
		return getExt('/', path);
	}

	public static byte[] base64Decode(String data) {
		return Base64.getDecoder().decode(data);
	}

	public static String base64Encode(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}

	public static String capitalize(String s) {
		return capitalize(s, 0);
	}

	public static String capitalize(String s, int startIndex) {
		char first = s.charAt(startIndex);
		if ('a' <= first && first <= 'z') {
			char[] data = s.toCharArray();
			data[startIndex] = (char) (first - 32);
			return String.valueOf(data);
		} else {
			return s;
		}
	}

	public static String xmlEncode(String s) {
		int len = s.length();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&':
				sb.append("&amp;");
				break;
			case '"':
				sb.append("&quot;");
				break;
			case '\'':
				sb.append("&apos;");
				break;
			case '<':
				sb.append("&lt;");
				break;
			case '>':
				sb.append("&gt;");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static String xmlDecode(String s) {
		int len = s.length();
		StringBuilder sb = new StringBuilder(len);
		int quoteStart = -1;
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			if (quoteStart >= 0) {
				if (c == ';') {
					String quote = s.substring(quoteStart + 1, i);
					switch (quote) {
					case "lt":
						sb.append('<');
						break;
					case "gt":
						sb.append('>');
						break;
					case "quot":
						sb.append('"');
						break;
					case "apos":
						sb.append('\'');
						break;
					case "amp":
						sb.append('&');
						break;
					default:
						sb.append("&" + quote + ";");
					}
					quoteStart = -1;
				} else {
					// ignore
				}
			} else {
				if (c == '&') {
					quoteStart = i;
				} else {
					sb.append(c);
				}
			}
		}
		if (quoteStart >= 0) {
			sb.append(s.substring(quoteStart));
		}

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] toArray(List<T> o, Class<T> cls) {
		if (o == null) {
			return null;
		} else {
			T[] a = (T[]) java.lang.reflect.Array.newInstance(cls, o.size());

			int index = 0;
			for (T i : o) {
				a[index++] = i;
			}

			return a;
		}
	}

	public static <T> void addAll(List<T> l, T[] array) {
		for (T i : array) {
			l.add(i);
		}
	}

	public static long fileCopy(String src, String dst) throws FileNotFoundException, IOException {
		try (FileInputStream is = new FileInputStream(src)) {
			try (FileOutputStream os = new FileOutputStream(dst)) {
				return streamTunnel(is, os);
			}
		}
	}

	public static String subStr(String src, int beginIndex, int endIndex) {
		int len = src.length();

		if (beginIndex < 0) {
			beginIndex = len + beginIndex;
		}

		if (endIndex < 0) {
			endIndex = len + endIndex + 1;
		}

		return src.substring(beginIndex, endIndex);
	}

	public static <T> T NVL(T src, T replacement) {
		return src == null ? replacement : src;
	}

	public static long streamTunnel(InputStream is, OutputStream os) throws IOException {
		long bytesCount = 0;
		byte[] buffer = new byte[8192];
		for (;;) {
			int nread = is.read(buffer);
			if (nread > 0) {
				os.write(buffer, 0, nread);
				bytesCount += nread;
			} else {
				break;
			}
		}
		return bytesCount;
	}

	public static long writeFile(File f, InputStream is) throws FileNotFoundException, IOException {
		try (OutputStream os = new FileOutputStream(f)) {
			return streamTunnel(is, os);
		}
	}

	public static long writeFile(File f, byte[] content) throws FileNotFoundException, IOException {
		try (ByteArrayInputStream is = new ByteArrayInputStream(content)) {
			try (OutputStream os = new FileOutputStream(f)) {
				return streamTunnel(is, os);
			}
		}
	}

	public static String lpad(String src, char c, int length) {
		int offset = length - src.length();
		if (offset > 0) {
			return Utils.repeat(c, offset) + src;
		} else {
			return src;
		}
	}

	public static String rpad(String src, char c, int length) {
		int offset = length - src.length();
		if (offset > 0) {
			return src + Utils.repeat(c, offset);
		} else {
			return src;
		}
	}

	public static String replace(String src, String pattern, String replacement) {
		int i = src.indexOf(pattern);
		if (i >= 0) {
			int ptnLen = pattern.length();
			StringBuilder sb = new StringBuilder(src.length());
			sb.append(src, 0, i).append(replacement);
			i += ptnLen;
			for (;;) {
				int j = src.indexOf(pattern, i);
				if (j >= 0) {
					sb.append(src, i, j).append(replacement);
					i = j + ptnLen;
				} else {
					return sb.append(src, i, src.length()).toString();
				}
			}
		} else {
			return src;
		}
	}

	public static int cpus() {
		return Runtime.getRuntime().availableProcessors();
	}
}
