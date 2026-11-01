package com.easyeda.router;

import com.easyeda.utils.json.JArray;
import com.easyeda.utils.json.JMap;
import com.easyeda.utils.json.JObject;

/**
 * Specctra Session (SES) 文件到 EasyEDA JSON 格式的转换工具。
 * <p>
 * Freerouting 引擎输出标准的 Specctra Session 文本，本工具将其解析为 EasyEDA 编辑器
 * 可直接导入的 JSON 结构。转换过程中坐标除以 1000 以适配 EasyEDA 的单位体系。
 * <p>
 * Converts Specctra Session (SES) file text output from the Freerouting engine
 * into EasyEDA's JSON format. Coordinates are divided by 1000 to match EasyEDA's unit system.
 *
 * <h3>输出 JSON 结构 / Output JSON Structure</h3>
 * <pre>{@code
 * {
 *   "1": {
 *     "net": "NetName",
 *     "wires": [
 *       { "layerid": 1, "width": 0.254, "points": [x1, y1, x2, y2, ...] }
 *     ],
 *     "vias": [
 *       { "x": 10.5, "y": 20.3 }
 *     ]
 *   },
 *   "2": { ... }
 * }
 * }</pre>
 *
 * @see RouterExecutor
 */
public class SessionFileUtil {

	/**
	 * 将 Specctra Session 文件文本转换为 EasyEDA JSON 格式。
	 * <p>
	 * 解析 SES 文本中的 {@code (net ...)} 块，提取每个网络的走线路径 {@code (path ...)}
	 * 和过孔 {@code (via ...)}，转换坐标单位后组装为 JSON 对象。
	 *
	 * @param fileData Specctra Session 文件的完整文本内容，为 null 时返回空 JMap
	 * @return 包含所有网络布线数据的 JSON 对象，key 为网络序号（从 "1" 开始）
	 */
	public static JObject sessionFileToEasyEDA(String fileData) {
		return sessionFileToEasyEDA(fileData, null);
	}

	/**
	 * Convert using the original EasyEDA DSN's resolution as the reference.
	 * This retains the historical /1000 editor convention when both match,
	 * while handling upstream SES output expressed in different units/resolution.
	 */
	public static JObject sessionFileToEasyEDA(String fileData, String sourceDsn) {
		JMap nets = new JMap();
		if (fileData == null) {
			return nets;
		}
		Specctra.Node session = Specctra.parse(fileData);
		if (!session.named("session")) {
			throw new IllegalArgumentException("Expected a Specctra session");
		}
		Specctra.Node routes = session.child("routes");
		if (routes == null || routes.child("network_out") == null) {
			throw new IllegalArgumentException("Missing SES routes/network_out");
		}
		Specctra.Node source = sourceDsn == null ? null : Specctra.parse(sourceDsn);
		Specctra.Node inputResolution = source == null ? null : source.child("resolution");
		Specctra.Node outputResolution = routes.child("resolution");
		double scale = 0.001;
		if (outputResolution != null) {
			double inputRes = inputResolution == null ? 10 : positive(inputResolution.atom(2));
			double inputUnit = inputResolution == null ? 1 : mils(inputResolution.atom(1));
			scale *= inputRes / positive(outputResolution.atom(2)) * mils(outputResolution.atom(1)) / inputUnit;
		}
		int index = 0;
		for (Object value : routes.child("network_out").values) {
			if (!(value instanceof Specctra.Node) || !((Specctra.Node) value).named("net")) { continue; }
			Specctra.Node net = (Specctra.Node) value;
			JMap item = new JMap();
			JArray wires = new JArray();
			JArray vias = new JArray();
			item.put("net", net.atom(1));
			item.put("wires", wires);
			item.put("vias", vias);
			for (Object child : net.values) {
				if (!(child instanceof Specctra.Node)) { continue; }
				Specctra.Node node = (Specctra.Node) child;
				if (node.named("wire")) {
					Specctra.Node path = node.child("path");
					if (path == null || path.values.size() < 7 || (path.values.size() - 3) % 2 != 0) {
						throw new IllegalArgumentException("Unsupported or malformed SES wire path");
					}
					JMap wire = new JMap();
					wire.put("layerid", layer(path.atom(1)));
					wire.put("width", positive(path.atom(2)) * scale);
					JArray points = new JArray();
					for (int i = 3; i < path.values.size(); i++) {
						points.add(Specctra.number(path.atom(i)) * scale);
					}
					wire.put("points", points);
					wires.add(wire);
				} else if (node.named("via")) {
					JMap via = new JMap();
					via.put("x", Specctra.number(node.atom(2)) * scale);
					via.put("y", Specctra.number(node.atom(3)) * scale);
					vias.add(via);
				}
			}
			nets.put(String.valueOf(++index), item);
		}
		return nets;
	}

	private static int layer(String name) {
		try {
			int id = Integer.parseInt(name);
			if (id > 0) { return id; }
		} catch (NumberFormatException e) { /* Named copper layers below. */ }
		if (name.equalsIgnoreCase("TopLayer") || name.equalsIgnoreCase("F.Cu")) { return 1; }
		if (name.equalsIgnoreCase("BottomLayer") || name.equalsIgnoreCase("B.Cu")) { return 2; }
		if (name.matches("(?i)InnerLayer[1-9][0-9]*")) {
			return 20 + Integer.parseInt(name.substring(10));
		}
		throw new IllegalArgumentException("Unknown EasyEDA copper layer: " + name);
	}

	private static double positive(String value) {
		double number = Specctra.number(value);
		if (number <= 0) { throw new IllegalArgumentException("Expected positive Specctra value"); }
		return number;
	}

	private static double mils(String unit) {
		if (unit.equalsIgnoreCase("mil")) { return 1; }
		if (unit.equalsIgnoreCase("inch")) { return 1000; }
		if (unit.equalsIgnoreCase("mm")) { return 1000.0 / 25.4; }
		if (unit.equalsIgnoreCase("um")) { return 1.0 / 25.4; }
		throw new IllegalArgumentException("Unsupported Specctra unit: " + unit);
	}
}
