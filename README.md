# EasyEDA PCB Router

[中文文档 (Chinese)](./README_CN.md)

A local PCB auto-routing WebSocket server for [EasyEDA](https://easyeda.com), based on the open-source [Freerouting](https://github.com/freerouting/freerouting) engine. The original Freerouting GUI has been removed and replaced with a headless WebSocket service that the EasyEDA web editor connects to for automated trace routing.

## Features

- WebSocket-based auto-routing service for EasyEDA PCB editor
- Based on the proven Freerouting maze-search algorithm
- Supports 90°, 45°, and any-angle routing
- Real-time progress reporting during routing
- Post-route optimization (trace straightening, via reduction)
- Cross-platform: Windows, Linux, macOS (all 64-bit)
- Configurable timeout, progress interval, and optimization passes

## Architecture

```
EasyEDA Web Editor  ◄──WebSocket JSON──►  EasyEDA PCB Router
(Browser / script.js)                     │
                                          ├─ RouterServer (Jetty HTTP/WS)
                                          ├─ WSHandler (WebSocket handler)
                                          ├─ RouterExecutor (routing thread)
                                          └─ Freerouting Engine (headless)
```

| Layer | Directory | Responsibility |
|-------|-----------|----------------|
| Bootstrap | `bootstrap/` | Entry point, delegates to RouterServer |
| EasyRouter | `easyrouter/` | WebSocket server, protocol handling, format conversion |
| Freerouter | `freerouter/` | Core routing algorithm engine (Freerouting, GUI removed) |

## Requirements

- Java 25 JDK and Apache Ant for a full source build (including the modern worker).
- The WebSocket facade targets Java 8. Modern routing runs in a separate Java 25+ JVM.
- The pinned upstream JAR is fetched and SHA-256 verified during the first build (about 65 MB).

## Quick Start

### Modern Engine

Modern Freerouting 2.5.0 is the default engine. The historical engine remains
available as an explicit rollback with `EASYEDA_ROUTER_ENGINE=legacy`. See
[migration validation](docs/migration-validation.md).

```sh
ant -Dbuild=.build/modern-client build-client
cd ".build/modern-client/EasyEDA Router v0.8.11"
sh mac64.sh  # Linux: sh lin64.sh
```

On Windows, run `win64.bat`; set `EASYEDA_ROUTER_ENGINE=legacy` only to roll
back to the historical engine.
Set `FREEROUTING_JAVA_HOME` to a Java 25+ installation if the facade runs on
an older Java runtime. Startup validates the runtime and artifact checksum;
it never silently falls back to the legacy engine.

Automatic track neckdown is disabled in the modern worker: the observed
EasyEDA round trip restores narrower tracks to their net-class width, which
can consume clearance. Routing retains the requested widths.

### Pre-built Release

1. Download the release package
2. Start the server:
   - Windows: double-click `win64.bat`
   - Linux: `sh lin64.sh`
   - macOS: `sh mac64.sh`
3. The server starts on `127.0.0.1:3579` by default
4. Open EasyEDA editor and use the "Auto Router" function

### Build from Source

```bash
ant build-client
```

The output is in `.build/EasyEDA Router v0.8.11/`.

For the fork workflow and migration regression checks, see [Development](docs/development.md).

#### Build Targets

| Target | Description |
|--------|-------------|
| `ant clean` | Clean build artifacts |
| `ant build-freerouter` | Build the historical engine JAR for rollback |
| `ant fetch-engine` | Fetch and verify the pinned modern engine |
| `ant build-worker` | Build the isolated Java 25 worker |
| `ant build-web` | Build the WebSocket server (for server deployment) |
| `ant build-client` | Build the full client distribution package |
| `ant -Dbuild=.build/migration-check test` | Build and run routing regression checks in an isolated directory |
| `ant -Dbuild=.build/integration test-integration` | Route the sample corpus with both engines and test worker failures/cancellation |

## Configuration

Configuration file: `config/local/main.json`

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

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `web.ip` | string | `127.0.0.1` | Listen IP address |
| `web.port` | int | `3579` | Listen port |
| `web.idle` | int | `60000` | HTTP idle timeout (ms) |
| `router.min_timeout` | int | `300000` | Minimum routing timeout (ms) |
| `router.min_progress_interval` | int | `2000` | Minimum nonzero progress push interval (ms) |
| `router.keep_heartbeat` | int | `5000` | Heartbeat interval (ms) |
| `router.max_route_retry` | int | `4` | Legacy-engine retry count |
| `router.engine` | string | `modern` | `modern` or `legacy`; JVM property `com.easyeda.router.engine`, then `EASYEDA_ROUTER_ENGINE`, override this value |
| `router.max_active_jobs` | int | `1` | Maximum simultaneous routing jobs |
| `router.max_queued_jobs` | int | `4` | Bounded waiting-job capacity |
| `router.worker_threads` | int | `2` | Optimizer threads per modern worker; autorouting uses one thread |
| `router.worker_heap_mb` | int | `1024` | Maximum heap per worker (MB) |
| `router.worker_stop_grace_ms` | int | `5000` | Grace period before forcibly stopping an unresponsive worker |

The environment is set via JVM property `-Dcom.easyeda.env=local` (defaults to `prod`).

## WebSocket API

### Endpoint

```
ws://127.0.0.1:3579/router
```

### Health Check

```
GET http://127.0.0.1:3579/api/whois
→ "EasyEDA Auto Router"
```

### Client → Server

#### Start Routing

```json
{
  "a": "startRoute",
  "data": "<DSN file content>",
  "timeout": 30,
  "progressInterval": 10,
  "optimizeTime": 5
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `a` | string | yes | Fixed: `"startRoute"` |
| `data` | string | yes | Specctra DSN format PCB design data |
| `timeout` | int | yes | Max routing time in seconds |
| `progressInterval` | int | no | Progress push interval in seconds (default: 2); 0 disables progress messages |
| `optimizeTime` | int | no | Post-route optimization passes (default: 3) |

#### Heartbeat

```json
{"a": "heartbeat"}
```

### Server → Client

#### Heartbeat

```json
{"a": "heartbeat"}
```

#### Routing Progress

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

#### Routing Result

```json
{
  "a": "routingResult",
  "complete": 1,
  "inCompleteNetNum": 0,
  "data": { ... }
}
```

| `complete` | Meaning |
|------------|---------|
| `1` | Routing completed successfully |
| `0` | Partial result with remaining connections, including timeout |
| `-1` | Server busy (thread pool full) |
| `-2` | Invalid request or failed DSN import |
| `-3` | Engine execution or SES conversion failed |

### Result Data Format

```json
{
  "<index>": {
    "net": "<net name>",
    "wires": [
      {
        "layerid": "<layer ID>",
        "width": "<trace width in EasyEDA units>",
        "points": [x1, y1, x2, y2, ...]
      }
    ],
    "vias": [
      { "x": "<X coordinate>", "y": "<Y coordinate>" }
    ]
  }
}
```

Coordinates retain the historical EasyEDA SES ÷ 1000 convention when input and
output resolutions match. Output unit/resolution changes are normalized against
the original DSN. Quoted names, decimal/signed coordinates, and numeric or standard
named copper layers are supported; unsupported geometry fails explicitly.

## JavaScript Client API

The `script.js` file provides `easyeda.AutoRouter`, a browser-side WebSocket client:

```javascript
// Create router instance
var router = new easyeda.AutoRouter("ws://127.0.0.1:3579/router");

// Set callbacks
router.onResult = function(resultCode, netArr, inCompleteNetNum) {
    if (resultCode == easyeda.AutoRouter.RESULT_CODE_COMPLETE ||
        resultCode == easyeda.AutoRouter.RESULT_CODE_NOT_COMPLETE) {
        api('ripupAllNet');
        api('importSession', JSON.stringify(netArr));
    } else if (resultCode == easyeda.AutoRouter.RESULT_CODE_SERVER_BUSY) {
        alert("Server busy, please try again later.");
    }
    router.close();
};

router.onProgress = function(netArr, inCompleteNetNum) {
    console.log("Incomplete nets: " + inCompleteNetNum);
    if (netArr != null) {
        api('ripupAllNet');
        api('importSession', JSON.stringify(netArr));
    }
};

router.onError = function() {
    alert("Router connection error.");
    router.close();
};

// Export DSN and start routing
var dsnData = api('exportDSN', {'width': '8.1mil', 'clearance': '11mil'});
router.requestRoute(dsnData, 30, 10, 5);
```

### API Reference

#### `new easyeda.AutoRouter(serverURL)`

Create a new auto-router client instance.

| Parameter | Type | Description |
|-----------|------|-------------|
| `serverURL` | string | WebSocket server URL, e.g. `"ws://127.0.0.1:3579/router"` |

#### `router.requestRoute(dsnData, timeout, progressInterval, optimizeTime)`

Start a routing request. Set callbacks before calling this method.

| Parameter | Type | Description |
|-----------|------|-------------|
| `dsnData` | string | Specctra DSN file content |
| `timeout` | number | Max routing time (seconds) |
| `progressInterval` | number | Progress interval (seconds), 0 to disable |
| `optimizeTime` | number | Maximum optimization passes (default: 3; the optimizer may stop earlier) |

#### `router.close()`

Close the WebSocket connection and cancel the current routing task.

#### Callbacks

| Callback | Parameters | Description |
|----------|------------|-------------|
| `onResult` | `(resultCode, netArr, inCompleteNetNum)` | Final routing result |
| `onProgress` | `(netArr, inCompleteNetNum)` | Intermediate progress (netArr may be null) |
| `onError` | none | Connection error or unexpected close |

#### Result Code Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `RESULT_CODE_COMPLETE` | `1` | Routing fully completed |
| `RESULT_CODE_NOT_COMPLETE` | `0` | Partial result / timeout |
| `RESULT_CODE_SERVER_BUSY` | `-1` | Server busy |
| `RESULT_CODE_ERROR_OPEN_FILE` | `-2` | Invalid request / DSN import error |
| `RESULT_CODE_ERROR_ROUTING` | `-3` | Engine / SES conversion failure |

## Troubleshooting

If the local auto-router is unavailable:

1. **Chrome**: Upgrade to the latest version.
2. **Firefox**: Go to `about:config` and set these to `true`:
   - `network.websocket.allowInsecureFromHTTPS`
   - `security.mixed_content.block_active_content`

Tips for better routing results:

- Skip GND nets and use copper areas for GND instead
- Use small track widths and clearances (minimum 6mil)
- Manually route critical tracks before auto-routing
- Add more layers (4 or 6 layers) for complex designs
- Avoid special characters in net names (`<> () # & @` and spaces)

## License

GPL-3.0

- Freerouting original code: Copyright Alfons Wirtz / Freerouting
- EasyEDA modifications and additions: Copyright EasyEDA & JLC Technology Group

## Acknowledgments

- [freerouting.net](http://www.freerouting.net/)
- [Github.com/Freerouting](https://github.com/freerouting/freerouting)
- [Freerouting by mihosoft](https://freerouting.mihosoft.eu/)
- [freerouting.org](https://freerouting.org/)
- All Freerouting and related project contributors
