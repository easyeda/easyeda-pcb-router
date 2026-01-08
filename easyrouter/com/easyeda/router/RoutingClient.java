package com.easyeda.router;

/**
 * Created by hover on 15/8/25. 布线服务客户端
 */
public interface RoutingClient {
	/**
	 *
	 * @param resultCode
	 *            0超时未布线完成 1布线完成 -1服务器忙 -2无法打开输入文件
	 * @param inCompleteNetNum
	 * @param sesFileData
	 */
	public void sendResult(int resultCode, int inCompleteNetNum, String sesFileData);

	public void sendProgress(int inCompleteNetNum, String sesFileData);

	void sendRaw(String s);
}
