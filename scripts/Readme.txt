# EasyEDA local Auto Router

Supported OS:

	Windows7(x64) or later 64bit Windows
	Ubuntu17.04(x64) or other 64bit Linux
	MacOS(x64)

Start local Auto Router:

	Double click win64.bat in Windows.
	Run "sh lin64.sh" on command prompt in Linux.
	Run "sh mac64.sh" on command prompt in MacOS.

If local auto router is unavailable:

	1) Chrome
	
		You need to upgrade Chrome to the newest version.	
	
	2) Firefox
	
		1. Type "about:config" into the address bar then press enter
		2. Search and double click the options as below (change the values to "true"):
			network.websocket.allowInsecureFromHTTPS
			security.mixed_content.block_active_content
		3. Re-open Firefox and try again.
		
Sometimes, if you can't get it done, try the tips below.

	1. Skip the GND nets, add copper area to GND net.
	2. Use small tracks and small clearance, but make sure the value is more than 6mil.
	3. Route some key tracks manually before auto routing.
	4. Add more layers, 4 layers or 6 layers
	5. Use local auto router rather than cloud server.
	6. Don't use the special characters for the net name, such as <> () # & and space.
	7. Tell the error detail to us and send your PCB file as EasyEDA Source file to support@easyeda.com.


-----------------------------------------------------------------------------------------------------------------------
# 立创EDA本地布线 

支持的操作系统：

	Win7 64位及以上
	Ubuntu 17.04 64位及其它64位Linux系统
	MacOS(x64)
	
运行本地布线：
	
	在Windows下双击win64.bat
	在Linux下控制台执行"sh lin64.sh"
	在MacOS下控制台执行"sh mac64.sh"

如果本地布线服务不可用：

	1) Chrome谷歌浏览器
	
		请检查是否已经使用了最新版的谷歌浏览器。		
	
	2) Firefox火狐浏览器
	
		1. 在地址栏输入"about:config"并按回车
		2. 搜索并双击如下参数（置为"true"）：
			network.websocket.allowInsecureFromHTTPS
			security.mixed_content.block_active_content
		3. 重新打开火狐浏览器进行本地自动布线。
		
如果自动布线失败，你可以尝试以下操作：

	1. 忽略GND网络，并铺铜和设置铺铜网络为GND。
	2. 使用小的线宽和间隙，但要确保线宽大于6mil。
	3. 先将重要的网络进行手动布线。
	4. 添加更多的层。
	5. 云端自动布线耗费服务器CPU资源，使用人数多时会容易失败，请耐心等待或者使用本地布线服务器。
	6. 不要在网络名使用特殊字符，如 # $ & <> @ 等。
	7. 其他的将详细故障信息告知我们，并将你的PCB文件以EasyEDA源码文件的格式发送至3001956291@qq.com。

---------------------------------------------------------------------------------------------------------------------------
change log v0.7

fix polygon self intersection bug

---------------------------------------------------------------------------------------------------------------------------
change log v0.6

add MacOS 64bit support
	
---------------------------------------------------------------------------------------------------------------------------
change log v0.4

improve multiple system language compatibility

---------------------------------------------------------------------------------------------------------------------------
change log v0.3

bug fix
add Linux 64bit support

---------------------------------------------------------------------------------------------------------------------------
change log v0.2

bug fix
