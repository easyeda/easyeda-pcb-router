# EasyEDA PCB Router — 产品需求文档 (PRD)

## 1. 项目概述

EasyEDA PCB Router 是嘉立创EDA（EasyEDA）的本地 PCB 自动布线服务。基于开源 Freerouting 引擎，去除原有 GUI，封装为 WebSocket 服务，供 EasyEDA Web 编辑器调用，实现 PCB 设计的自动走线。

| 属性 | 值 |
|------|-----|
| 项目名称 | easyeda-pcb-router |
| 当前版本 | 0.8.11 |
| 开发语言 | Java 8 (JavaSE-1.8) |
| 构建工具 | Apache Ant + Ivy |
| 开源协议 | GPL-3.0 |
| 核心依赖 | Freerouting Engine, Jetty 9.2.22 (WebSocket) |

## 2. 目标用户

- EasyEDA Web 编辑器用户（通过浏览器使用自动布线功能）
- 需要本地高性能布线的 PCB 设计师（避免云端排队）
- 二次开发者（基于 WebSocket 协议集成自动布线能力）

## 3. 系统架构

```
┌─────────────────────┐     WebSocket (JSON)     ┌──────────────────────────┐
│  EasyEDA Web Editor │ ◄──────────────────────► │   EasyEDA PCB Router     │
│  (Browser / JS)     │                          │                          │
│                     │   GET /api/whois         │  ┌────────────────────┐  │
│  script.js          │ ────────────────────────►│  │   RouterServer     │  │
│  AutoRouter Client  │                          │  │   (Jetty HTTP/WS)  │  │
└─────────────────────┘                          │  └────────┬───────────┘  │
                                                 │           │              │
                                                 │  ┌────────▼───────────┐  │
                                                 │  │    WSHandler       │  │
                                                 │  │  (WebSocket 处理)   │  │
                                                 │  └────────┬───────────┘  │
                                                 │           │              │
                                                 │  ┌────────▼───────────┐  │
                                                 │  │  RouterExecutor    │  │
                                                 │  │  (布线执行线程)     │  │
                                                 │  └────────┬───────────┘  │
                                                 │           │              │
                                                 │  ┌────────▼───────────┐  │
                                                 │  │  Freerouting       │  │
                                                 │  │  Engine (无头模式)  │  │
                                                 │  └────────────────────┘  │
                                                 └──────────────────────────┘
```

### 3.1 分层结构

| 层 | 目录 | 职责 |
|----|------|------|
| Bootstrap | `bootstrap/` | 程序入口，委托给 RouterServer |
| EasyRouter | `easyrouter/` | WebSocket 服务、协议处理、格式转换 |
| Freerouter | `freerouter/` | 核心布线算法引擎（基于 Freerouting，去除 GUI） |

### 3.2 关键类

| 类 | 职责 |
|----|------|
| `RouterServer` | Jetty 服务器启动，注册 HTTP/WS 端点 |
| `WSService` | WebSocket Servlet 配置（超时、消息大小） |
| `WSHandler` | WebSocket 消息处理，实现 `RoutingClient` 接口 |
| `RouterExecutor` | 布线执行器，在线程池中运行 Freerouting 引擎 |
| `RoutingClient` | 结果回调接口（sendResult / sendProgress / sendRaw） |
| `SessionFileUtil` | Specctra SES → EasyEDA JSON 格式转换 |
| `BoardHandling` | Freerouting 核心控制器（导入/导出/布线/缓存） |
| `BatchAutorouterThread` | 批量自动布线线程（扇出→布线→优化） |
| `AutorouteSettings` | 布线算法参数（过孔代价、层偏好、重试次数等） |
| `RouterCache` | 布线进度缓存值对象 |

## 4. 功能需求

### 4.1 核心功能：自动布线服务

**FR-001: WebSocket 布线服务**
- 在本地启动 WebSocket 服务器（默认 `127.0.0.1:3579`）
- 接收 Specctra DSN 格式的 PCB 设计数据
- 执行自动布线算法
- 返回布线结果（EasyEDA JSON 格式）

**FR-002: 布线超时控制**
- 客户端可指定最大布线时间（秒）
- 服务端有最小超时保护（默认 300 秒）
- 超时后终止布线，返回当前最优结果

**FR-003: 中间进度推送**
- 按客户端指定间隔推送布线中间结果
- 包含当前未完成连接数和已布线数据快照
- 客户端可实时展示布线进度

**FR-004: 心跳保活**
- 服务端定期发送心跳包（默认 5 秒）
- 客户端每 15 秒发送心跳
- 防止 WebSocket 空闲断开

**FR-005: 布线后优化**
- 支持布线完成后的走线优化
- 客户端可指定优化时间（秒）
- 优化包括走线拉直、减少过孔等

**FR-006: 健康检查**
- HTTP GET `/api/whois` 端点
- 返回 "EasyEDA Auto Router"，支持 CORS
- 用于编辑器检测本地服务可用性

### 4.2 并发控制

**FR-007: 线程池管理**
- 线程池大小等于 CPU 核心数
- 线程池满时返回 "服务器忙" 错误码
- 每个 WebSocket 连接同时只允许一个布线任务

**FR-008: 连接生命周期**
- WebSocket 连接关闭时自动中断正在执行的布线任务
- 新的 `startRoute` 请求会取消当前连接上的旧任务

## 5. WebSocket 通信协议

### 5.1 客户端 → 服务端

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
| `timeout` | int | 是 | 最大布线时间（秒），实际取 max(timeout*1000, min_timeout) |
| `progressInterval` | int | 否 | 进度推送间隔（秒），默认 2，实际取 max(val*1000, min_progress_interval) |
| `optimizeTime` | int | 否 | 优化次数，默认 0（使用内部默认值 3） |

#### 心跳
```json
{"a": "heartbeat"}
```

### 5.2 服务端 → 客户端

#### 心跳响应
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
      "net": "NetName",
      "wires": [{"layerid": 1, "width": 0.254, "points": [x1, y1, x2, y2]}],
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
| `1` | 布线完成（或超时后返回当前结果） |
| `0` | 超时未完成 |
| `-1` | 服务器忙（线程池满） |
| `-2` | 无法打开 DSN 文件 |

### 5.3 数据格式：布线结果 `data`

```json
{
  "<序号>": {
    "net": "<网络名>",
    "wires": [
      {
        "layerid": <层ID>,
        "width": <线宽，EasyEDA单位>,
        "points": [x1, y1, x2, y2, ...]
      }
    ],
    "vias": [
      { "x": <X坐标>, "y": <Y坐标> }
    ]
  }
}
```

坐标单位：Freerouting 内部坐标 ÷ 1000 = EasyEDA 坐标。

## 6. 配置规格

配置文件路径：`config/<env>/main.json`，环境由 JVM 属性 `-Dcom.easyeda.env` 指定（默认 `local`）。

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

## 7. 布线算法概述

基于 Freerouting 开源引擎，核心算法流程：

1. **扇出（Fanout）**：可选步骤，将元件引脚向外扩展短走线
2. **迷宫搜索布线（Maze Search）**：基于扩展房间（Expansion Room）的 A* 搜索算法
3. **批量布线（Batch Autoroute）**：按网络优先级逐个布线，支持拆线重布（Rip-up & Reroute）
4. **走线优化（Post-route Optimization）**：拉直走线、减少过孔、优化角度（支持 90°/45°/任意角度）

支持的走线角度：
- 正交（90°）
- 45°
- 任意角度

## 8. 支持平台

| 平台 | 要求 |
|------|------|
| Windows | Windows 7 x64 及以上 |
| Linux | Ubuntu 17.04 x64 及其他 64 位 Linux |
| macOS | macOS x64 |

运行要求：Java 8 (JRE 1.8) 64 位。

## 9. 客户端集成（JavaScript API）

EasyEDA 编辑器通过 `script.js` 中的 `easyeda.AutoRouter` 类集成：

```javascript
var router = new easyeda.AutoRouter("ws://127.0.0.1:3579/router");

router.onResult = function(resultCode, netArr, inCompleteNetNum) {
    if (resultCode == easyeda.AutoRouter.RESULT_CODE_COMPLETE) {
        api('importSession', JSON.stringify(netArr));
    }
};

router.onProgress = function(netArr, inCompleteNetNum) {
    if (netArr != null) {
        api('ripupAllNet');
        api('importSession', JSON.stringify(netArr));
    }
};

router.onError = function() {
    alert("Router connection error");
    router.close();
};

var dsnData = api('exportDSN', {'width': '8.1mil', 'clearance': '11mil'});
router.requestRoute(dsnData, 30, 10, 5);
```

### 结果码常量

| 常量 | 值 | 含义 |
|------|----|------|
| `RESULT_CODE_COMPLETE` | `1` | 布线全部完成 |
| `RESULT_CODE_NOT_COMPLETE` | `0` | 超时未完成 |
| `RESULT_CODE_SERVER_BUSY` | `-1` | 服务器忙 |
| `RESULT_CODE_ERROR_OPEN_FILE` | `-2` | 文件打开失败 |

## 10. 构建与部署

### 构建命令
```bash
ant build-client
```

### 构建产物
```
.build/EasyEDA Router v0.8.11/
├── bin/
│   ├── bootstrap.jar          # 启动入口
│   ├── easyrouter-0.8.11.jar  # WebSocket 服务层
│   ├── freerouter-0.8.11.jar  # 布线引擎
│   ├── jetty-*.jar            # Jetty 依赖
│   └── websocket-*.jar        # WebSocket 依赖
├── config/local/main.json     # 配置文件
├── log/                       # 日志目录
├── win64.bat                  # Windows 启动脚本
├── lin64.sh                   # Linux 启动脚本
└── mac64.sh                   # macOS 启动脚本
```

### 启动方式
- Windows: 双击 `win64.bat`
- Linux: `sh lin64.sh`
- macOS: `sh mac64.sh`

## 11. 已知限制

- 不支持交互式手动布线（仅批量自动布线）
- 单个 WebSocket 连接同时只能执行一个布线任务
- WebSocket 最大消息大小 20MB（DSN 文件大小限制）
- 网络名不支持特殊字符（如 `<> () # &` 和空格）
- 最小线宽建议大于 6mil

## 12. 版权声明

- Freerouting 原始代码：Copyright Alfons Wirtz / Freerouting
- EasyEDA 修改和新增代码：Copyright EasyEDA & JLC Technology Group
- 开源协议：GPL-3.0
