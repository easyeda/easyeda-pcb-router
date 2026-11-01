# Modern Freerouting migration plan

Status: engine/worker integration, SES hardening, lifecycle/resource handling,
packaging, tests, and evaluation are implemented on `feature/modern-freerouting`.
See [validation and measured results](migration-validation.md) and
[development notes](development.md). Modern is now the default after browser,
DRC, and regression acceptance; platform acceptance and eventual legacy removal
remain separate follow-up work. Legacy remains available for rollback.
Upstream checked on 2026-10-31.

## Findings and recommended approach

The latest stable upstream release checked is **v2.5.0**, published 2026-10-05.
Its build targets **Java 25**. Pin that release for the initial migration rather
than building a moving `master`. Upstream provides an executable JAR and native
CLI packages. Its optimizer supports a thread pool; this does not mean every
routing phase scales across cores. Measure benefits on our EasyEDA boards.

This is an API migration, not a drop-in JAR replacement. Our `RouterExecutor`
imports `board.*`, `interactive.*`, and `designformats.specctra.*`, constructs
`BoardHandling`, and relies on local extensions including `RouterCache`,
`waitCurrentCache()`, `isFinished`, and `exception`. Modern upstream uses
`app.freerouting.*` packages and a different job/lifecycle model. DSN and SES
remain useful interchange formats, but do not preserve Java API compatibility.

**Recommendation:** keep the existing EasyEDA HTTP/WebSocket facade and Ant
packaging, and run the modern engine in a local Java 25 worker process behind
a small adapter. Use one worker per active job initially. The facade can remain
Java 8-compatible during this migration. The worker is local; no hosted routing
service is required.

Start with the stock headless CLI as a compatibility experiment. For production,
build a small Java 25 worker wrapper around the pinned upstream job API to
provide structured progress, SES snapshots, cancellation, and partial results.
CLI completion alone is insufficient to replace our live routing contract.

Process separation also avoids placing upstream's Jetty 12 dependencies on the
existing Jetty 9 classpath. Do not copy an upstream executable JAR into `lib/`:
Ant currently copies every JAR there into the facade's dependency directory.

If a single JVM is a firm deployment requirement, use the alternative at the
end of this plan. Decide this before implementing the adapter.

## 1. Record the existing contract and baseline

- Preserve current local edits and build a legacy reference distribution into
  a separate output directory, for example
  `ant -Dbuild=.build/migration-baseline build-client`.
- Capture requests, progress, SES, converted JSON, remaining connections,
  routing time, wire length, via count, and peak memory for `testcase/*.dsn`.
  Add representative EasyEDA exports with multilayer boards and pre-routed nets.
- Record observable behavior in `WSHandler`, `RouterExecutor`, `RoutingClient`,
  and `script.js`, including reconnect, replacement requests, and disconnect.
- Explicitly distinguish documentation from existing behavior: timeout currently
  reports code `1`; progress interval zero is clamped; rejected submissions use
  `-2`; the fixed thread pool has an unbounded queue. `optimizeTime` is a pass
  count in this implementation, despite time-oriented documentation.

Deliverable: fixtures and a contract table stating what stays compatible and
which existing defects will be corrected explicitly during migration.

## 2. Pin and acquire upstream reproducibly

- Record release tag, resolved commit, artifact URL, SHA-256, runtime requirement,
  and license notices in a dependency manifest such as `engine/upstream.json`.
- Initial stock JAR: `freerouting-2.5.0.jar`. GitHub currently reports SHA-256
  `f6f51bb02245e8e717f9359bd260cc9c5c0b1bc0acc8b7cb2cd5b8ffeb5de3c7`.
  Verify downloaded bytes before use.
- Keep acquired artifacts outside `.build/` so Ant clean cannot remove the
  acquisition cache. Package them in a separate `engine/` distribution directory.
- If building from source, check out the pinned commit and use its Gradle
  wrapper with JDK 25. The executable-JAR task is `executableJar`; inspect its
  produced filename rather than assuming the release artifact name.
- Preserve GPL and dependency notices/source provenance in the distribution.

Deliverable: a repeatable fetch/build/verify operation with no implicit upgrade
to the next upstream release.

## 3. Prove headless DSN-to-SES compatibility independently

Before modifying the server, route a small board with the stock JAR:

```sh
"$FREEROUTING_JAVA_HOME/bin/java" -Djava.awt.headless=true \
  -jar /path/to/freerouting-2.5.0.jar \
  --gui.enabled=false --api_server.enabled=false -da \
  -de /absolute/path/to/input.dsn -do /absolute/path/to/output.ses -mt 2
```

Use a dedicated working/user-data directory and controlled settings for this
experiment; confirm other optional servers are disabled in effective settings.
`-da` disables upstream analytics. Validate the flags against the pinned binary.

- Route every baseline board and re-import results into EasyEDA.
- Inspect SES resolution, units, layer identifiers, via definitions, quoting,
  number formats, and handling of pre-existing tracks.
- Check actual completion metrics and output validity, not just exit status.
- Verify import failures and headless operation without an available display.

Gate: no adapter implementation until modern output can be converted correctly
and imported by EasyEDA. This experiment is not proof of progress/cancel support.

## 4. Harden SES conversion and add focused tests

`SessionFileUtil` currently splits on newlines and literal spaces, parses only
integers, strips quotes, drops points/vias with non-positive X, and always divides
coordinates by 1000. Standard-compliant modern output can expose these assumptions.

- Capture genuine SES fixtures from both engines with expected EasyEDA geometry.
- Implement a small structured Specctra reader or normalization layer independent
  of either engine's internal packages. Handle whitespace, nested expressions,
  quoted/escaped names, signed/decimal coordinates, and declared resolution/units.
- Map layers and vias using input/output metadata. Preserve the existing external
  JSON schema and EasyEDA coordinate convention, including numeric layer IDs.
- Add meaningful converter tests for legacy output, modern output, zero/negative
  coordinates, alternative units, malformed input, and multilayer vias.
- Add an Ant test target and a test-only dependency/source directory; do not
  rely on `freerouter/test/`, which is excluded from current compilation.

Gate: equivalent input geometry converts equivalently regardless of SES formatting.

## 5. Introduce an engine boundary with a legacy implementation

- Extract an engine-neutral interface, for example `RoutingEngine`, with request,
  progress/snapshot, result, and cancellation types owned by this repository.
- Move existing `BoardHandling` integration into `LegacyRoutingEngine`.
- Reduce `RouterExecutor` to request policy, engine invocation, heartbeat/progress
  scheduling, and mapping results to `RoutingClient`. Remove its direct board
  observer and engine-state responsibilities.
- Preserve `/router`, `/api/whois`, browser callback signatures, and JSON keys.
- Add explicit engine selection, defaulting to modern after validation while
  retaining legacy for rollback.

Gate: legacy behavior still passes the contract checks through the new boundary.

## 6. Implement the modern worker and lifecycle bridge

Create a separate Java 25 worker module, compiled against pinned upstream, and a
`ModernRoutingEngine` facade adapter that launches it with `ProcessBuilder`.
Use argument arrays, unique per-job directories, and explicit UTF-8.

Investigate these v2.5.0 integration points during a focused prototype:

| Existing dependency | Modern candidate / migration work |
| --- | --- |
| `BoardHandling.import_design()` | `RoutingJob` input and headless job setup; follow `CliRunner` and scheduler initialization. |
| `start_batch_autorouter()` | `RoutingJobScheduler`/routing pipeline; configure the current algorithm explicitly. |
| `RouterCache` / `waitCurrentCache()` | Job output-update events and SES snapshots; measure frequency and thread safety. |
| `isFinished` / `exception` | Terminal `RoutingJobState`, output availability, and structured errors. |
| `getInCompletesNum()` | Modern board statistics connection count; verify its meaning against EasyEDA. |
| `terminate()` | Cooperative stop/cancel with bounded grace period and worker termination fallback. |
| static retry and constructor optimization settings | Per-job modern settings with an explicit compatibility mapping. |

- Define a versioned private protocol for start, progress, snapshot-ready, result,
  error, and cancel. Keep log output separate from machine-readable events.
- Transfer immutable snapshots or atomically published SES files; never expose a
  board being mutated by optimizer threads. Rate-limit exports and apply backpressure.
- Follow upstream initialization for settings, sessions, and jobs; do not call
  its executable `main()` inside the facade JVM or assume it is a library facade.
- Prototype event delivery and snapshot consistency. If pinned APIs cannot meet
  them safely, use a small pinned upstream patch with a regression test rather
  than claiming the stock CLI provides live snapshots. `-im` creates binary
  checkpoints; those are not a live SES progress API.
- Keep independent heartbeats alive while workers route. Reject stale job events
  after replacement/disconnect and emit at most one terminal result.
- On timeout, request a cooperative stop, obtain a consistent partial result,
  then enforce a process deadline. On queued-job cancellation, avoid the current
  null-thread interrupt problem. Drain process streams and clean job files.

Gate: progress, partial results, timeout, cancellation, and worker crashes all
behave predictably without wedging the WebSocket service.

## 7. Define option mappings and resource limits

- Retain `timeout` and `progressInterval` in seconds on the browser boundary;
  validate ranges and convert without integer overflow. Document minimums.
- Map `optimizeTime` to a modern optimization pass budget where supported;
  keep it distinct from autorouter passes and wall-clock timeout. Verify the
  pinned optimizer's stopping criteria and document any non-equivalent mapping.
- Inventory local changes in `BoardHandling`, `AutorouteSettings`, routing passes,
  and cache handling. Port required behavior deliberately; do not carry old
  patches wholesale into upstream source.
- Set explicit maximum active jobs, bounded queue size, worker thread count,
  per-worker heap, and deadline/grace limits. Avoid multiplying a CPU-sized
  facade job pool by CPU-sized optimizer pools. Start with one active worker
  and a conservative optimizer thread allocation, then benchmark.
- Decide result-code corrections using the contract table. Keep existing numeric
  values; coordinate fixes with `script.js` and both READMEs. Do not label a
  timeout with remaining connections as fully routed or map a worker crash to
  a successful result. Distinguish errors internally even where old wire codes
  cannot express every category.

Deliverable: documented configuration defaults and option/result mappings.

## 8. Update Ant packaging and runtime discovery

- Keep facade compilation and modern worker compilation separate. Ant may invoke
  a pinned worker build; it should not recompile upstream with its old macro.
- Package modern artifacts/dependencies under `engine/`, outside the facade JAR
  manifest/classpath. Keep the legacy engine available during rollout.
- Add clear runtime discovery using a dedicated setting/environment variable,
  such as `FREEROUTING_JAVA_HOME`, and validate Java 25 before accepting work.
  `JAVA_HOME` can continue to select the facade runtime.
- Update `build-client`, `build-web`, Eclipse metadata as needed, distribution
  launchers, and the local launcher without overwriting unrelated local changes.
- Document whether each release supplies a Java 25 runtime or requires one;
  the current Ant distribution does not bundle its expected JRE automatically.
- Check paths containing spaces, executable permissions, offline startup, and
  missing/wrong-runtime messages on Windows, Linux, and macOS.

Gate: a clean checkout produces a runnable distribution with verified artifacts
and no Jetty dependency leakage between facade and worker.

## 9. Run regression, lifecycle, and quality comparisons

| Area | Required checks |
| --- | --- |
| Conversion | Both SES dialects; units, layers, names, vias, signed coordinates; no silent geometry loss. |
| Protocol | Start, heartbeat, progress with/without snapshot, final result, malformed request/input. |
| Lifecycle | Disconnect, replacement, cancel before start/during routing/during optimization, timeout with partial output, worker crash, shutdown. |
| Capacity | Concurrent clients, bounded-queue overflow, CPU/heap limits, repeated-job leaks and temporary-file cleanup. |
| Routing | Remaining connections, DRC, preserved fixed geometry/constraints, via count, wire length, runtime, peak memory. |
| Packaging | Source build and packaged launch on supported OS/architectures; missing runtime/dependency; no-display environment. |

Compare both engines on the same inputs, hardware, time limits, and total CPU
budget. Repeat representative boards when results vary. Evaluate aggregate
gains and individual regressions; newer code is not a guarantee of improvement
on every board. Validate DRC in EasyEDA as well as upstream because design rules
and format interpretation can differ. Do not compare SES byte-for-byte.

Gate: all contract/lifecycle/converter checks pass; no unexplained geometry or
DRC regressions; publish per-board quality/performance results before defaulting
to modern. End-to-end EasyEDA re-import is a release requirement.

## 10. Roll out, then retire the historical engine

- Ship modern with engine version visible in startup logs.
- Switch the default after the regression gate passes (completed). Retain explicit legacy
  selection for one transition release. Avoid silent retry on legacy after a
  modern failure, which would hide defects and extend timeout budgets.
- Update `README.md`, `README_CN.md`, `docs/PRD.md`, and `AGENTS.md` with actual
  runtime/build requirements, settings, behavior, and supported platforms.
- Once rollback is no longer needed, remove `freerouter/`, its Ant target/JAR,
  legacy adapter, and Eclipse source-root/classpath entries in a separate change.
- Establish a pinned-upstream upgrade routine that reruns this regression corpus.

Suggested review sequence: baseline/tests; SES conversion; engine extraction;
worker prototype; lifecycle/settings; packaging; measured rollout; legacy removal.
Each change should leave the legacy distribution buildable until retirement.

## Alternative: embed modern Freerouting in the facade JVM

If a single process is required, first move the entire backend build/runtime
to Java 25 and resolve the facade's old Jetty/Servlet dependencies alongside
upstream's Jetty 12/Jakarta dependencies. Use an upstream library artifact with
explicit transitive dependency management or a deliberately isolated core build;
do not place two Jetty versions on a flat classpath. Keep the same engine
interface, tests, migration gates, and rollback selection, but implement the
modern adapter in-process. Progress and cancellation still require API work.

This path offers simpler runtime process management but expands the initial
upgrade to the web container and runtime. Choose it deliberately rather than
treating that work as an incidental JAR replacement.

## Primary sources

- [v2.5.0 release and downloads](https://github.com/freerouting/freerouting/releases/tag/v2.5.0)
- [Release metadata and artifact digests](https://api.github.com/repos/freerouting/freerouting/releases/latest) — queried on 2026-10-31; this URL changes over time.
- [Pinned build: Java toolchain, dependencies, and packaging](https://github.com/freerouting/freerouting/blob/v2.5.0/build.gradle)
- [Pinned headless CLI documentation](https://github.com/freerouting/freerouting/blob/v2.5.0/docs/command_line_arguments.md)
- [RoutingJob and output events](https://github.com/freerouting/freerouting/blob/v2.5.0/src/main/java/app/freerouting/core/RoutingJob.java)
- [Headless CLI job setup](https://github.com/freerouting/freerouting/blob/v2.5.0/src/main/java/app/freerouting/cli/CliRunner.java)
- [Scheduler pipeline, output serialization, and timeout handling](https://github.com/freerouting/freerouting/blob/v2.5.0/src/main/java/app/freerouting/management/jobs/RoutingJobSchedulerActionThread.java)
- [Headless board manager](https://github.com/freerouting/freerouting/blob/v2.5.0/src/main/java/app/freerouting/management/HeadlessBoardManager.java)
- [Modern optimizer implementation](https://github.com/freerouting/freerouting/blob/v2.5.0/src/main/java/app/freerouting/autoroute/pipeline/BatchOptimizer.java)
