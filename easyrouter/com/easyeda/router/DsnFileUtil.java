package com.easyeda.router;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * DSN 临时文件读写工具。
 * <p>
 * 提供将 DSN 文件数据写入系统临时目录和删除临时文件的功能。
 * <p>
 * Utility for writing DSN file data to the system temp directory
 * and cleaning up temporary files.
 */
public class DsnFileUtil {

	/**
	 * 将 DSN 文件数据写入系统临时文件。
	 *
	 * @param dsnFileData DSN 文件的文本内容
	 * @return 临时文件的绝对路径，写入失败时返回 null
	 */
	public static String writeTmpFile(String dsnFileData) {
		try {
			File f = File.createTempFile("dsn_", "");
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(dsnFileData.getBytes());
			fos.flush();
			fos.close();
			return f.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 删除指定路径的临时文件。
	 *
	 * @param filename 要删除的文件绝对路径
	 */
	public static void deleteTmpFile(String filename) {
		File f = new File(filename);
		if (f.exists())
			f.delete();
	}
}
