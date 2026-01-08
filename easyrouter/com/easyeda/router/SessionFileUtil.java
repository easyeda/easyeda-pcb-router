package com.easyeda.router;

import com.easyeda.utils.json.JArray;
import com.easyeda.utils.json.JMap;
import com.easyeda.utils.json.JObject;

/**
 * Created by hover on 15/9/1.
 */
public class SessionFileUtil {
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

	private static double toEasyEDASize(double s) {
		return s / 1000;
	}
}
