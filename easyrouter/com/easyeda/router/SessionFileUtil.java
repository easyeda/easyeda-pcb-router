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
		JMap netArr = new JMap();
		if (fileData == null)
			return netArr;
		String[] nets = fileData.split("\\(net ");
		for (int i = 1; i < nets.length; i++) {
			String[] netNodes = nets[i].split("\n");
			String netName = netNodes[0].trim();
			JMap item = new JMap();
			JArray wires = new JArray();
			JArray vias = new JArray();
			item.put("wires", wires);
			item.put("vias", vias);
			item.put("net", netName.replace("\"", ""));

			String[] maybeWireArr = nets[i].split("\\(path");
			for (int j = 1; j < maybeWireArr.length; j++) {
				String[] lines = maybeWireArr[j].split("\n");
				String[] wireInfo = lines[0].trim().split(" ");
				int layerId = parseInt(wireInfo[0].trim());
				int width = parseInt(wireInfo[1].trim());
				JMap lineObj = new JMap();
				JArray linePoints = new JArray();
				lineObj.put("layerid", layerId);
				lineObj.put("width", toEasyEDASize(width));
				lineObj.put("points", linePoints);

				for (int k = 1; k < lines.length; k++) {
					String[] points = lines[k].trim().split(" ");
					int ptx = parseInt(points[0].trim());
					if (points.length == 2 && ptx > 0) {
						int pty = parseInt(points[1].trim());
						linePoints.add(toEasyEDASize(ptx));
						linePoints.add(toEasyEDASize(pty));
					}
				}
				wires.add(lineObj);
			}

			// net中的过孔
			String[] maybeVia = nets[i].split("\\(via");
			for (int j = 1; j < maybeVia.length; j++) {
				String t = maybeVia[j].trim();
				t = t.substring(0, t.indexOf("\n")).trim();
				String[] ts = t.split(" ");
				if (ts.length != 3)
					continue;
				int x = parseInt(ts[1]);
				int y = parseInt(ts[2]);
				if (x > 0) {
					JMap viaObj = new JMap();
					viaObj.put("x", toEasyEDASize(x));
					viaObj.put("y", toEasyEDASize(y));
					vias.add(viaObj);
				}
			}

			netArr.put("" + i, item);
		}
		return netArr;
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
		}
		return 0;
	}

	/**
	 * 将 Freerouting 内部坐标转换为 EasyEDA 坐标（除以 1000）。
	 *
	 * @param s Freerouting 坐标值
	 * @return EasyEDA 坐标值
	 */
	private static double toEasyEDASize(double s) {
		return s / 1000;
	}
}
