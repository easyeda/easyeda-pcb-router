package com.easyeda.router;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by hover on 15/9/1.
 */
public class DsnFileUtil {
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

	public static void deleteTmpFile(String filename) {
		File f = new File(filename);
		if (f.exists())
			f.delete();
	}
}
