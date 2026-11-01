# Development workflow

## Repository remotes

Development takes place in [aweijnitz/easyeda-pcb-router](https://github.com/aweijnitz/easyeda-pcb-router).
Use `origin` for the fork and `upstream` for the original EasyEDA project:

```sh
git clone git@github.com:aweijnitz/easyeda-pcb-router.git
cd easyeda-pcb-router
git remote add upstream https://github.com/easyeda/easyeda-pcb-router.git
git config remote.pushDefault origin
git switch -c feature/modern-freerouting
```

For an existing checkout, inspect `git remote -v` first. If `origin` still
points to EasyEDA and no `upstream` remote exists, rename it to `upstream`,
add the fork as `origin`, fetch the fork, and set `main` to track `origin/main`.
No source dependency URLs need changing: Freerouting remains a separate project.

After reviewing and committing feature changes, publish the branch with:

```sh
git push -u origin feature/modern-freerouting
```

## Building and running

A complete build needs JDK 25 and Ant. The facade and historical engine still
compile with `--release 8` on a modern JDK; `worker/` compiles separately for Java 25. The modern
worker JAR and unmodified pinned Freerouting executable are packaged under
`engine/`, outside the facade classpath. The first build downloads the core;
`engine/.cache/` survives Ant clean. Each cached artifact is SHA-256 verified.

```sh
ant -Dbuild=.build/modern-client build-client
EASYEDA_ROUTER_DIR="$PWD/.build/modern-client/EasyEDA Router v0.8.11" \
  ./startRouter.sh
```

Alternatively, enter the distribution directory and use `sh mac64.sh`,
`sh lin64.sh`, or `win64.bat`. These launchers find Java via `JAVA_HOME`, then
an existing bundled runtime, then PATH. They run in the foreground. Set
Modern routing is the default. Set `EASYEDA_ROUTER_ENGINE=legacy` for a
temporary rollback to the historical engine.

For a Java 8 facade with a separate worker runtime, set `FREEROUTING_JAVA_HOME`
to a Java 25+ installation. It also selects the worker compiler for Ant; an
explicit `-Dworker.java.home=/path/to/jdk25` can select the build compiler.
Startup verifies the modern artifact and Java version before accepting requests.
There is no automatic fallback. `EASYEDA_ROUTER_ENGINE=legacy` selects rollback.

Selection priority: JVM property `com.easyeda.router.engine`, environment
variable `EASYEDA_ROUTER_ENGINE`, then `router.engine` in `main.json` (default
`modern`). Advanced JVM overrides `com.easyeda.freerouting.directory` and
`com.easyeda.freerouting.java` select the packaged engine directory and worker
Java executable. A standalone Eclipse project with JDK 25 is provided in
`worker/`; do not add its modern dependencies to the facade's Eclipse classpath.

Use `max_active_jobs`, `max_queued_jobs`, `worker_threads`, `worker_heap_mb`,
and `worker_stop_grace_ms` to budget resources. Defaults are one active job,
four queued jobs, two optimizer threads, a 1024 MB worker heap, and a 5-second
stop grace period. Modern autorouting uses one thread. `max_route_retry` applies
only to the historical engine; modern routing uses upstream defaults and the
job deadline. The modern SMD fanout pre-pass is disabled to match the
historical EasyEDA batch mode (see the validation report for the measured
impact). Both engines interpret `optimizeTime` as a pass count, defaulting
to three; upstream may stop optimization early at its improvement threshold.

The modern worker disables automatic neckdown and the optional narrow-width
retry. EasyEDA's captured round trip restored 0.75-unit tracks to the requested
1.0-unit width on matching centre lines. Reserving clearance for those narrower
tracks is therefore unsafe for this integration. Generated routes must retain
their declared net-class width; the actual-engine corpus tests enforce this.

## Automated validation

```sh
ant -Dbuild=.build/migration-check test
ant -Dbuild=.build/integration test-integration
ant -Dbuild=.build/quality -Dbenchmark.timeout.ms=20000 test-integration
python3 tests/websocket_integration.py '.build/modern-client/EasyEDA Router v0.8.11'
```

The first target runs dependency-free Java regression checks for conversion,
precision, executor lifecycle, and legacy import/export. Simulated failures
intentionally log errors. The integration target routes all four sample boards
with each engine, validates converted geometry, and checks actual worker input
errors, partial-stop results, crash handling, uncooperative-worker hard stops,
protected traces, and a four-layer
inner-copper fixture. It writes SES/JSON outputs
and a benchmark table under `<build>/migration-results/`. The benchmark uses
one CPU and 512 MB heaps per engine, one optimization pass, and an equal wall
clock budget including process startup. DRC counters are engine-reported and
may have different definitions; they do not replace editor DRC.

The Python standard-library suite tests packaged startup in a path containing
spaces, HTTP/WS, invalid requests, heartbeat/progress, partial timeout results,
replacement, bounded capacity, queued disconnect/removal, recovery, single final
responses, worker shutdown cleanup, and missing/wrong runtime/artifact startup failures. It copies the distribution and edits
only temporary configuration; it does not disturb the user's running router.
Its process inspection currently supports POSIX hosts.

All build targets clean their configured output directory. Use separate
`-Dbuild` directories; never run Ant targets concurrently in one output path.
The first target has a 30-second test process deadline, integration a 10-minute
ceiling. The build uses no hosted routing service. See
[migration validation](migration-validation.md) for measured results, current
limitations, and remaining release gates.

## External protocol corrections

The browser schema and action names are preserved. A result with remaining
connections is now code `0`, even when the engine stopped naturally. Code `1`
means zero remaining connections; it does not mean the board passed editor DRC.
Queue overflow is `-1`, invalid request/DSN is `-2`, and engine/conversion failure
is `-3`. `script.js` exposes `RESULT_CODE_ERROR_ROUTING`; clients should accept
partial geometry for code `0`. `progressInterval=0` disables progress messages
while heartbeats remain active. Heartbeats and progress have independent timers.
Times are integer seconds from 1 to 86400 (progress also accepts zero), converted
without overflow. Optimization passes are integers from 0 to 1000; zero selects
the default of three.

The SES converter uses structured expressions, original-DSN resolution/unit
metadata, and full-precision JSON numbers. Matching input/output resolution
preserves the historical coordinate division by 1000; this is an EasyEDA
integration convention rather than a generic physical-unit conversion for
arbitrary CAD systems. Output resolution/unit changes are normalized relative
to that source reference. Numeric layers and TopLayer/BottomLayer, F.Cu/B.Cu,
and InnerLayerN aliases are mapped explicitly; unknown named layers or malformed
wire geometry fail rather than silently dropping tracks. The existing browser
via schema carries coordinates only, so advanced via geometries still require
EasyEDA-specific acceptance testing.

## Capturing a routing regression

Set `EASYEDA_ROUTER_DIAGNOSTICS` to a local directory before starting the server:

```sh
EASYEDA_ROUTER_DIR="$PWD/.build/modern-diagnostics/EasyEDA Router v0.8.11" \
EASYEDA_ROUTER_ENGINE=modern \
EASYEDA_ROUTER_DIAGNOSTICS="$PWD/.build/routing-diagnostics" ./startRouter.sh
```

Build that distribution first with
`ant -Dbuild=.build/modern-diagnostics build-client`. Capture is disabled by
default and does not change routing settings. The server prints each unique
`route-*` directory as it accepts a request. It contains `input.dsn`, the exact
`request.json`, router `settings.json`, selected `engine.txt`, final `result.ses`,
and the exact result packet `result.json`. The latest progress packet/session
are stored separately and overwritten rather than accumulated.

These local files contain the PCB design. They are retained for comparison
until deleted, and are not uploaded. Select an ignored directory under `.build/`
to keep captures out of commits. A diagnostic write failure is logged and does
not prevent routing. Use the raw input/output and the editor's after-import
export to distinguish engine output errors from import transformations.

Run `python3 tests/websocket_integration.py '<distribution>' --diagnostics` to
verify capture along with the packaged HTTP/WebSocket lifecycle checks.
