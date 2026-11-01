# Repository guide

## Purpose and architecture

EasyEDA PCB Router is a Java 8-compatible WebSocket facade with two headless
Freerouting adapters: the historical in-process engine and a Java 25 worker
pinned to Freerouting 2.5.0. The modern worker is the default; set
`EASYEDA_ROUTER_ENGINE=legacy` for temporary rollback.
The EasyEDA browser editor sends Specctra DSN designs over WebSocket; the
service routes them and returns progress and EasyEDA-compatible JSON results.
Read `README.md` (English), `README_CN.md` (Chinese), and `docs/PRD.md` for
background. Check implementation details against the source: some protocol
descriptions in the documentation differ from current behavior.

Request flow:
`AutoRouter` → `RouterServer` → `WSService` / `WSHandler` → `RouterExecutor`
→ `RoutingEngine` → either `LegacyRoutingEngine` / `interactive.BoardHandling`
or `ModernRoutingEngine` / isolated `ModernWorker` / upstream pipeline. See `docs/development.md` for the fork workflow:
`origin` is the aweijnitz fork and `upstream` is the original EasyEDA repository.

## Layout

| Path | Contents |
| --- | --- |
| `bootstrap/com/easyeda/bootstrap/` | `AutoRouter`, the distribution entry point delegating to `RouterServer`. |
| `easyrouter/com/easyeda/router/` | Jetty server, WebSocket protocol, routing executor and callbacks, DSN/SES conversion utilities, browser client `script.js`, and sample `easyeda.json`. |
| `easyrouter/com/easyeda/utils/` | Configuration, custom JSON model/parser, logging, and shared utilities. |
| `freerouter/autoroute/` | Maze search, batch routing, fanout, and optimization. |
| `freerouter/board/`, `rules/`, `library/` | Board model, routing constraints, and component/padstack libraries; all three are under `freerouter/`. |
| `freerouter/geometry/`, `datastructures/` | Geometry and supporting data structures; both are under `freerouter/`. |
| `freerouter/designformats/specctra/` | DSN/SES parsing and writing, including a JFlex scanner and its `.flex` source. |
| `freerouter/interactive/`, `boardgraphics/` | Engine orchestration, settings, progress cache, and retained interactive/graphics support; both are under `freerouter/`. |
| `freerouter/test/` | Legacy board validation helpers, not a runnable unit-test suite. |
| `testcase/` | Sample `.dsn` boards and `bug01.json` for manual regression checks. |
| `tests/` | Java regressions, actual-engine benchmark/lifecycle checks, and a standard-library Python packaged WebSocket suite. |
| `worker/` | Separate Java 25 worker source and Eclipse metadata; never add modern dependencies to the facade classpath. |
| `engine/` | Pinned upstream manifest and notices; ignored `.cache/` stores the verified executable JAR. |
| `easyrouter/com/easyeda/router/engine/` | Engine boundary, immutable snapshots, selection/validation, and legacy/modern adapters. |
| `config/local/main.json` | Local server and router configuration. |
| `lib/` | Checked-in runtime/build dependency JARs (Jetty 9.2.22 and Servlet API 3.1). |
| `scripts/`, `debug/` | Distribution launchers and Windows debugging scripts. |
| `wwwroot/` | Static web assets (currently `robots.txt`). |
| `build.xml`, `ivy.xml` | Ant compilation/packaging and optional Ivy dependency retrieval. |
| `.classpath`, `.project`, `.settings/` | Eclipse project metadata, including Java 8 compiler settings. |
| `.build/` | Generated build output, ignored by Git. |

The Java 8 source roots are `bootstrap`, `easyrouter`, and `freerouter`; the
separate Java 25 source root is `worker`. Tests live in `tests`.
Engine packages are named `board`, `autoroute`, `geometry.planar`, etc.; they
do **not** have a `freerouter.` prefix. Service packages use `com.easyeda.*`.

## Building

Use a 64-bit Java 25 **JDK** and Apache Ant for a complete build. The facade
and historical engine target Java 8; the isolated worker targets Java 25.
`FREEROUTING_JAVA_HOME` (or Ant `worker.java.home`) selects the worker compiler.
Run commands from the repository root:

```sh
ant build-client
ant build-web
ant build-freerouter
ant clean
```

Choose the target for the task; these commands are alternatives, not a required
sequence. No default target is declared, so specify one explicitly.

- `build-client`: builds the engine, server, and bootstrap; creates
  `.build/EasyEDA Router v0.8.11/` with `bin/`, configuration, launchers, and `log/`.
- `build-web`: builds the engine/server and dependencies in `.build/bin/`,
  copies configuration to `.build/config/`, and copies static files to
  `.build/wwroot/` (the spelling is from `build.xml`).
- `build-freerouter`: creates `.build/bin/freerouter-0.8.11.jar`.
- `fetch-engine`: fetches and SHA-256 verifies the pinned modern JAR into
  `engine/.cache/`, outside the clean output directory.
- `build-worker`: compiles the Java 25 wrapper and packages the isolated core,
  manifest, and notices under `${build}/engine/`.
- `test-integration`: runs both engines on the sample corpus, checks worker
  failure/stop handling, and writes `${build}/migration-results/`.
- `clean`: deletes the configured build directory.

Compilation/build targets depend on `pre`, which depends on `clean`; rebuilding
deletes prior output. To preserve an existing build, use a separate directory,
for example `ant -Dbuild=.build/agent-check build-client`. Do not run separate
build targets concurrently against the same output directory.

Ant compiles UTF-8 Java sources into `${build}/.class/`, packages `.properties`
resources, and sets JAR manifest classpaths. `build-client` moves `${build}/bin`
into the distribution. Facade/legacy compilation uses `--release 8` on modern JDKs, with Java 8
source/target fallback for an Ant JVM running Java 8. Worker compilation
uses `--release 25`. Modern libraries must stay out of `${build}/bin/`.

Facade dependencies come from `lib/`; full builds also fetch the pinned core
if absent. Neither path depends on `resolve`. Run `ant resolve` only
when dependency retrieval is needed; that target requires the Ivy Ant task
to be installed. There is no Maven, Gradle, or npm build.

## Running and checking changes

Run a built client with an installed Java runtime:

```sh
cd ".build/EasyEDA Router v0.8.11"
java -XX:+UseG1GC -Dcom.easyeda.env=local -jar bin/bootstrap.jar
```

Alternatively, after `build-web`, run from `.build/`:

```sh
java -Dcom.easyeda.env=local -jar bin/easyrouter-0.8.11.jar
```

The packaged launchers find Java via `JAVA_HOME`, an existing bundled runtime,
then PATH. Ant does not bundle a JRE. Modern routing requires Java 25+ via
`FREEROUTING_JAVA_HOME` when the facade uses an older runtime. Enable it with
`EASYEDA_ROUTER_ENGINE=modern` or `router.engine`; the JVM property
`com.easyeda.router.engine` takes highest priority. Startup checks runtime and
artifact checksum. `EASYEDA_ROUTER_ENGINE=legacy` explicitly selects rollback.
The root `startRouter.sh` supports an `EASYEDA_ROUTER_DIR` distribution override.

Configuration is loaded from `config/<env>/main.json`; the JVM property
`com.easyeda.env` defaults to `prod`, but only `local` is supplied. Use
`-Dcom.easyeda.env=local`. Source configuration is copied at build time;
edits to it require rebuilding or updating the built copy, and configuration
is cached in the running process. The documented default address is
`127.0.0.1:3579`; check the active configuration for local overrides.

Run `ant -Dbuild=.build/migration-check test` for automated adapter, executor,
and conversion regression checks. Use `test-integration` for actual-engine
checks and corpus measurements, and `python3 tests/websocket_integration.py
'<distribution directory>'` for packaged HTTP/WS lifecycle checks. Tests live in `tests/`; historical engine
`test/**` helpers are excluded from explicit Ant source selection, but javac
can still compile referenced validation helpers implicitly.
There is no external test framework, formatter, or linter configured. For
server/routing changes, also start the service and check:

```sh
curl --fail http://127.0.0.1:3579/api/whois
```

Expected health response: `EasyEDA Auto Router`. Exercise
`ws://127.0.0.1:3579/router` with a sample board from `testcase/` via the
EasyEDA editor or a WebSocket client. Check heartbeats, progress, final JSON,
and remaining incomplete nets; for lifecycle changes also check disconnect
and replacement-request cancellation. Report what was actually verified.

## Coding conventions and compatibility

These conventions are observed in the source, rather than enforced by tooling:

- Keep Java 8 compatibility in facade/legacy code and Java 25 code confined
  to `worker/`. Preserve the existing source-root/package layout.
- Java generally uses tabs, opening braces on the declaration/control-flow
  line, and PascalCase class names. Follow the surrounding file's formatting
  and import grouping; avoid unrelated reformatting.
- EasyEDA code generally uses camelCase methods/fields. Inherited engine code
  uses snake_case methods/fields and `p_` parameter prefixes. Preserve each
  area's naming style and existing public interfaces.
- Preserve Javadoc and copyright/license notices. Service documentation often
  includes Chinese and English; retain both when updating existing comments.
- Use the existing `com.easyeda.utils.json` types (`JObject`, `JMap`, etc.) and
  `Config` loader where appropriate rather than introducing parallel libraries.
- The browser client uses a closure, `var`, functions, and prototype methods;
  match that style and preserve the `easyeda.AutoRouter` API.
- `SpecctraFileScanner.java` and `LogfileScanner.java` are generated by JFlex.
  Their sources are `SpecctraFileDescription.flex` and `LogfileDescription.flex`.
  Prefer changing the grammar and regenerating for scanner changes; Ant does
  not currently provide a scanner-generation target.
- Keep WebSocket action names, JSON keys, result codes, layer mapping, and
  coordinate conversions compatible with the browser client. Session output
  coordinates retain division by 1000 when source/output resolution matches;
  normalize output unit/resolution changes relative to the original DSN.
  Protocol times arrive in seconds
  and are converted to milliseconds; `optimizeTime` is used as a pass count.
- Protocol result codes are `1` for zero remaining connections, `0` for partial
  results, `-1` for queue overflow, `-2` for invalid request/DSN, and `-3` for
  engine/conversion failure. `progressInterval=0` disables progress. Validate
  integer time inputs before converting seconds to milliseconds. Keep these
  semantics synchronized with `script.js` and both READMEs.
- Keep engine-specific APIs inside adapters under `router/engine/` so the
  WebSocket executor and modern worker share an engine-neutral contract.
- Keep automatic neckdown and narrow-width retry disabled in the modern worker.
  Captured EasyEDA round trips restore narrower tracks to the net-class width,
  consuming clearance the router had reserved for the smaller width. Run the
  integration width checks when changing routing/optimizer settings.
- Keep active/queued work bounded, worker streams separate from logging,
  snapshots immutable, and old-generation callbacks suppressed. Cancellation,
  shutdown, and failures must clean up processes and temporary files. Do not
  silently retry a modern failure on the legacy engine.
- `EASYEDA_ROUTER_DIAGNOSTICS` enables local request/SES/browser-packet capture.
  It is off by default. Keep captures under an ignored directory and do not
  include the user's complete PCB exports in commits without explicit intent.
- Retain the old engine until the documented browser/DRC, corpus, and platform
  gates pass. Do not change defaults based on a single successful sample.
- Preserve existing synchronization, volatile state, cancellation, and cleanup
  semantics when changing routing threads or WebSocket lifecycle handling.
- Keep generated outputs out of commits and preserve unrelated local changes.
  Runtime behavior changes should be reflected in both README languages when
  relevant. The project is GPL-3.0; retain upstream attribution.
