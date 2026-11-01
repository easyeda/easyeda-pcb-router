# EasyEDA PCB Router（嘉立创EDA PCB 自动布线器）

[English Documentation](./README.md)

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

- 完整源码构建需要 Java 25 JDK 和 Apache Ant。
- WebSocket 服务保持 Java 8 兼容；现代引擎在独立 Java 25+ 进程中运行。
- 首次构建下载约 65 MB 的固定版本 JAR，并校验 SHA-256。

## 快速开始

### 现代引擎预览

Freerouting 2.5.0 已支持显式启用。浏览器/DRC 验收和样本回归尚待确认，
默认仍使用旧引擎。详见 [迁移验证（英文）](docs/migration-validation.md)。

```sh
ant -Dbuild=.build/modern-client build-client
cd ".build/modern-client/EasyEDA Router v0.8.11"
EASYEDA_ROUTER_ENGINE=modern sh mac64.sh  # Linux: sh lin64.sh
```

Windows 用户先设置 `EASYEDA_ROUTER_ENGINE=modern`，再运行 `win64.bat`。
若服务本身运行在旧版 Java 上，请通过 `FREEROUTING_JAVA_HOME` 指定 Java 25+。
启动时校验运行时及 JAR 摘要；现代引擎失败时不会自动回退到旧引擎。

现代工作进程禁用自动缩窄走线。已观察到 EasyEDA 往返导入/导出会把较窄走线
恢复为网络规则线宽，可能减少实际间距；因此引擎按请求的线宽预留间距。

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

Fork 开发流程及迁移回归检查请参阅 [开发文档（英文）](docs/development.md)。

#### 构建目标

| 目标 | 说明 |
|------|------|
| `ant clean` | 清理构建产物 |
| `ant build-freerouter` | 构建旧引擎 JAR（用于回退） |
| `ant fetch-engine` | 下载并校验现代引擎 |
| `ant build-worker` | 构建独立 Java 25 工作进程 |
| `ant build-web` | 构建 WebSocket 服务（用于服务端部署） |
| `ant build-client` | 构建完整客户端分发包 |
| `ant -Dbuild=.build/migration-check test` | 在独立目录构建并运行布线回归检查 |
| `ant -Dbuild=.build/integration test-integration` | 双引擎样本布线及工作进程生命周期检查 |

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
    "max_route_retry": 4,
		"engine": "modern",
    "max_active_jobs": 1,
    "max_queued_jobs": 4,
    "worker_threads": 2,
    "worker_heap_mb": 1024,
    "worker_stop_grace_ms": 5000
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
| `router.max_route_retry` | int | `4` | 旧引擎最大重试次数 |
| `router.engine` | string | `modern` | 引擎选择（默认现代引擎）；JVM 属性 `com.easyeda.router.engine`、环境变量 `EASYEDA_ROUTER_ENGINE` 依次优先于此项。设置为 `legacy` 可回退到历史引擎 |
| `router.max_active_jobs` | int | `1` | 最大并发任务数 |
| `router.max_queued_jobs` | int | `4` | 等待队列容量 |
| `router.worker_threads` | int | `2` | 现代优化器线程数；自动布线使用单线程 |
| `router.worker_heap_mb` | int | `1024` | 每个工作进程最大堆内存（MB） |
| `router.worker_stop_grace_ms` | int | `5000` | 强制终止前的宽限期（毫秒） |

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
| `0` | 部分结果，仍有未完成连接（含超时） |
| `-1` | 服务器忙（线程池满） |
| `-2` | 请求无效或 DSN 导入失败 |
| `-3` | 引擎执行或 SES 转换失败 |

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

输入输出分辨率相同时保留历史 SES 坐标 ÷ 1000 约定；单位或分辨率变化时，
以原始 DSN 为基准归一化。支持带引号的网络名、小数及负坐标；不支持的几何格式会明确报错。

## JavaScript 客户端 API

`script.js` 提供 `easyeda.AutoRouter` 类，用于浏览器端连接布线服务：

```javascript
// 创建布线器实例
var router = new easyeda.AutoRouter("ws://127.0.0.1:3579/router");

// 设置回调
router.onResult = function(resultCode, netArr, inCompleteNetNum) {
    if (resultCode == easyeda.AutoRouter.RESULT_CODE_COMPLETE ||
        resultCode == easyeda.AutoRouter.RESULT_CODE_NOT_COMPLETE) {
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
| `optimizeTime` | number | 最大优化次数（默认 3，可提前收敛） |

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
| `RESULT_CODE_NOT_COMPLETE` | `0` | 部分结果，仍有未完成连接（含超时） |
| `RESULT_CODE_SERVER_BUSY` | `-1` | 服务器忙 |
| `RESULT_CODE_ERROR_OPEN_FILE` | `-2` | 请求或 DSN 导入失败 |
| `RESULT_CODE_ERROR_ROUTING` | `-3` | 引擎或 SES 转换失败 |

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
