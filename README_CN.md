# EasyEDA PCB Router（嘉立创EDA PCB 自动布线器）

基于开源 [Freerouting](https://github.com/freerouting/freerouting) 引擎的本地 PCB 自动布线 WebSocket 服务，供 [嘉立创EDA](https://easyeda.com)（EasyEDA）Web 编辑器调用。去除了 Freerouting 原有 GUI，封装为无头 WebSocket 服务。

## 功能特性

- 基于 WebSocket 的自动布线服务，与 EasyEDA PCB 编辑器无缝集成
- 采用成熟的 Freerouting 迷宫搜索算法
- 支持 90°、45° 和任意角度布线
- 布线过程中实时推送进度
- 布线后自动优化（走线拉直、减少过孔）
- 跨平台支持：Windows、Linux、macOS（均为 64 位）
- 可配置超时时间、进度间隔和优化次数

## 系统架构

```
EasyEDA Web 编辑器  ◄──WebSocket JSON──►  EasyEDA PCB Router
(浏览器 / script.js)                      │
                                          ├─ RouterServer (Jetty HTTP/WS 服务)
                                          ├─ WSHandler (WebSocket 消息处理)
                                          ├─ RouterExecutor (布线执行线程)
                                          └─ Freerouting Engine (无头布线引擎)
```

| 层 | 目录 | 职责 |
|----|------|------|
| Bootstrap | `bootstrap/` | 程序入口，委托给 RouterServer |
| EasyRouter | `easyrouter/` | WebSocket 服务、协议处理、格式转换 |
| Freerouter | `freerouter/` | 核心布线算法引擎（基于 Freerouting，去除 GUI） |

## 环境要求

- Java 8 (JRE 1.8) 64 位
- Apache Ant（从源码构建时需要）

## 快速开始

### 使用预编译版本

1. 下载发布包
2. 启动服务：
   - Windows：双击 `win64.bat`
   - Linux：`sh lin64.sh`
   - macOS：`sh mac64.sh`
3. 服务默认在 `127.0.0.1:3579` 启动
4. 打开 EasyEDA 编辑器，使用"自动布线"功能

### 从源码构建

```bash
ant build-client
```

构建产物位于 `.build/EasyEDA Router v0.8.11/` 目录。

#### 构建目标

| 目标 | 说明 |
|------|------|
| `ant clean` | 清理构建产物 |
| `ant build-freerouter` | 构建 Freerouting 引擎 JAR |
| `ant build-web` | 构建 WebSocket 服务（用于服务端部署） |
| `ant build-client` | 构建完整客户端分发包 |

## 配置说明

配置文件路径：`config/local/main.json`

```json
{
  "web": {
    "ip": "127.0.0.1",
    "port": 3579,
    "idle": 60000
  },
  "router": {
    "min_timeout": 300000,
    "min_progress_interval": 2000,
    "keep_heartbeat": 5000,
    "max_route_retry": 4
  }
}
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `web.ip` | string | `127.0.0.1` | 监听 IP 地址 |
| `web.port` | int | `3579` | 监听端口 |
| `web.idle` | int | `60000` | HTTP 连接空闲超时（毫秒） |
| `router.min_timeout` | int | `300000` | 最小布线超时（毫秒） |
| `router.min_progress_interval` | int | `2000` | 最小进度推送间隔（毫秒） |
| `router.keep_heartbeat` | int | `5000` | 心跳发送间隔（毫秒） |
| `router.max_route_retry` | int | `4` | 最大布线重试次数 |

运行环境通过 JVM 属性 `-Dcom.easyeda.env=local` 指定（默认为 `prod`）。

## WebSocket API

### 连接端点

```
ws://127.0.0.1:3579/router
```

### 健康检查

```
GET http://127.0.0.1:3579/api/whois
→ "EasyEDA Auto Router"
```

### 客户端 → 服务端

#### 启动布线

```json
{
  "a": "startRoute",
  "data": "<DSN 文件内容>",
  "timeout": 30,
  "progressInterval": 10,
  "optimizeTime": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `a` | string | 是 | 固定值 `"startRoute"` |
| `data` | string | 是 | Specctra DSN 格式的 PCB 设计数据 |
| `timeout` | int | 是 | 最大布线时间（秒） |
| `progressInterval` | int | 否 | 进度推送间隔（秒），默认 2 |
| `optimizeTime` | int | 否 | 布线后优化次数，默认 3 |

#### 心跳

```json
{"a": "heartbeat"}
```

### 服务端 → 客户端

#### 心跳

```json
{"a": "heartbeat"}
```

#### 布线进度

```json
{
  "a": "routingProgress",
  "inCompleteNetNum": 5,
  "data": {
    "1": {
      "net": "VCC",
      "wires": [{"layerid": 1, "width": 0.254, "points": [10, 20, 30, 40]}],
      "vias": [{"x": 10.5, "y": 20.3}]
    }
  }
}
```

#### 布线结果

```json
{
  "a": "routingResult",
  "complete": 1,
  "inCompleteNetNum": 0,
  "data": { ... }
}
```

| `complete` 值 | 含义 |
|---------------|------|
| `1` | 布线完成 |
| `0` | 超时未完成 |
| `-1` | 服务器忙（线程池满） |
| `-2` | 无法打开 DSN 文件 |

### 布线结果数据格式

```json
{
  "<序号>": {
    "net": "<网络名>",
    "wires": [
      {
        "layerid": "<层ID>",
        "width": "<线宽，EasyEDA单位>",
        "points": [x1, y1, x2, y2, ...]
      }
    ],
    "vias": [
      { "x": "<X坐标>", "y": "<Y坐标>" }
    ]
  }
}
```

坐标换算：Freerouting 内部坐标 ÷ 1000 = EasyEDA 坐标。

## JavaScript 客户端 API

`script.js` 提供 `easyeda.AutoRouter` 类，用于浏览器端连接布线服务：

```javascript
// 创建布线器实例
var router = new easyeda.AutoRouter("ws://127.0.0.1:3579/router");

// 设置回调
router.onResult = function(resultCode, netArr, inCompleteNetNum) {
    if (resultCode == easyeda.AutoRouter.RESULT_CODE_COMPLETE) {
        api('ripupAllNet');
        api('importSession', JSON.stringify(netArr));
    } else if (resultCode == easyeda.AutoRouter.RESULT_CODE_SERVER_BUSY) {
        alert("服务器忙，请稍候再试。");
    }
    router.close();
};

router.onProgress = function(netArr, inCompleteNetNum) {
    console.log("未完成连接数: " + inCompleteNetNum);
    if (netArr != null) {
        api('ripupAllNet');
        api('importSession', JSON.stringify(netArr));
    }
};

router.onError = function() {
    alert("布线服务连接错误");
    router.close();
};

// 导出 DSN 并启动布线
var dsnData = api('exportDSN', {'width': '8.1mil', 'clearance': '11mil'});
router.requestRoute(dsnData, 30, 10, 5);
```

### API 参考

#### `new easyeda.AutoRouter(serverURL)`

创建自动布线客户端实例。

| 参数 | 类型 | 说明 |
|------|------|------|
| `serverURL` | string | WebSocket 服务地址，如 `"ws://127.0.0.1:3579/router"` |

#### `router.requestRoute(dsnData, timeout, progressInterval, optimizeTime)`

发起布线请求。调用前需先设置回调函数。

| 参数 | 类型 | 说明 |
|------|------|------|
| `dsnData` | string | Specctra DSN 文件内容 |
| `timeout` | number | 最大布线时间（秒） |
| `progressInterval` | number | 进度推送间隔（秒），0 表示不推送 |
| `optimizeTime` | number | 优化次数（秒） |

#### `router.close()`

关闭 WebSocket 连接并取消当前布线任务。

#### 回调函数

| 回调 | 参数 | 说明 |
|------|------|------|
| `onResult` | `(resultCode, netArr, inCompleteNetNum)` | 布线最终结果 |
| `onProgress` | `(netArr, inCompleteNetNum)` | 中间进度（netArr 可能为 null） |
| `onError` | 无 | 连接错误或非主动断开 |

#### 结果码常量

| 常量 | 值 | 含义 |
|------|----|------|
| `RESULT_CODE_COMPLETE` | `1` | 布线全部完成 |
| `RESULT_CODE_NOT_COMPLETE` | `0` | 超时未完成 |
| `RESULT_CODE_SERVER_BUSY` | `-1` | 服务器忙 |
| `RESULT_CODE_ERROR_OPEN_FILE` | `-2` | 文件打开失败 |

## 常见问题

如果本地布线服务不可用：

1. **Chrome 浏览器**：请升级到最新版本。
2. **Firefox 浏览器**：在地址栏输入 `about:config`，将以下参数设为 `true`：
   - `network.websocket.allowInsecureFromHTTPS`
   - `security.mixed_content.block_active_content`

提高布线成功率的建议：

- 忽略 GND 网络，使用铺铜代替
- 使用较小的线宽和间隙（最小 6mil）
- 先手动布好关键走线，再执行自动布线
- 增加 PCB 层数（4 层或 6 层）
- 网络名避免使用特殊字符（如 `<> () # & @` 和空格）

## 开源协议

GPL-3.0

- Freerouting 原始代码：Copyright Alfons Wirtz / Freerouting
- EasyEDA 修改和新增代码：Copyright 嘉立创EDA & 嘉立创科技集团

## 致谢

- [freerouting.net](http://www.freerouting.net/)
- [Github.com/Freerouting](https://github.com/freerouting/freerouting)
- [Freerouting by mihosoft](https://freerouting.mihosoft.eu/)
- [freerouting.org](https://freerouting.org/)
- 所有 Freerouting 及相关项目的贡献者
