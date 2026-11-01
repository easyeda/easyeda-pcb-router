# Modern Freerouting validation and rollout

Validated on 2026-10-31 on macOS ARM64 with Oracle JDK 25.0.2.
Implementation is on `feature/modern-freerouting`; modern routing is available
through `EASYEDA_ROUTER_ENGINE=modern`; it is now the default. Set
`EASYEDA_ROUTER_ENGINE=legacy` for rollback.

## Implemented

- Verified, pinned Freerouting 2.5.0 executable, separate Java 25 worker, and
  engine-neutral Java 8 facade. No upstream fork/patch is required.
- Structured SES conversion with source-relative units/resolution, quoted and
  escaped names, signed/decimal/rational coordinates, explicit layer aliases,
  and full-precision JSON serialization. Unsupported geometry fails explicitly.
- Live SES snapshots at upstream pipeline update boundaries, one-slot progress
  delivery, independent heartbeat scheduling, consistent partial stop results,
  cooperative cancellation, bounded grace/forced termination, and temporary
  file cleanup. Each job has its own process and directory.
- Bounded active/queued jobs, canceled-queue removal, generation-guarded browser
  callbacks, one terminal response per job, validated time/pass inputs, and
  distinct busy/input/engine error codes.
- Ant acquisition/build/packaging, facade/worker runtime discovery, checksum and
  Java validation before server startup, launcher support for source-built
  distributions, and separate Eclipse worker metadata.
- Regression tests, actual-engine corpus checks, failure/lifecycle checks, and
  a standard-library Python test for packaged HTTP/WebSocket behavior.

## Results

The current [20-second run with requested-width enforcement](benchmarks/2026-10-31-width-safe-20s.json)
retains the raw benchmark rows. The earlier [5-second run](benchmarks/2026-10-31-5s.json)
and [20-second run](benchmarks/2026-10-31-20s.json) predate the neckdown correction.
Each engine had one CPU, a 512 MB heap budget, one optimization pass, and the
same per-board wall-clock limit including worker startup. No hosted services
were used. The historical engine stopped early on the first three boards
under its existing retry policy. The modern engine could use the remaining
budget to finish them. Completion and clearance counters measure different
properties: zero incomplete connections is not a DRC certificate.

| Board | Legacy incomplete, 20s | Modern incomplete, 20s | Legacy / modern reported clearance violations |
| --- | ---: | ---: | ---: |
| `test.dsn` | 2 | 0 | 0 / 0 |
| `test1.dsn` | 2 | 0 | 4 / 4 |
| `simple.dsn` | 4 | 0 | 0 / 0 |
| `MAIN.dsn` | 28 | 0 | 0 / 0 |

Modern used 9, 10, 24, and 84 vias respectively; legacy used 4, 10, 3, and 26.
Wire-length values are in the preserved EasyEDA coordinate convention, so
compare within a board rather than across boards with different source units.
Longer routes or more vias on a more completely routed board are not directly
comparable to a partial design. Legacy clearance counts use its per-item pair
checks; modern counts come from upstream statistics. Definitions may differ.

These are small canary comparisons, not a claim of universal speed or quality
improvement. The final mapping disables the SMD fanout pre-pass, matching the
historical EasyEDA backend. Initial runs with upstream's default fanout regressed
on `MAIN.dsn`: [5s](benchmarks/2026-10-31-upstream-fanout-5s.json) and
[20s](benchmarks/2026-10-31-upstream-fanout-20s.json). A
[stock CLI diagnostic](benchmarks/2026-10-31-stock-cli-MAIN.json) reproduced the
behavior, spending almost its whole 20-second budget in fanout before routing.
Disabling that pre-pass fixed the observed regression without changing board
clearance/width rules. Final 5-second runs left 48 connections on both engines;
with 20 seconds modern reached zero while legacy left 28. Two additional
[modern MAIN repeats](benchmarks/2026-10-31-MAIN-repeats.json) also reached zero
connections and zero reported clearance violations with 86 vias each. Modern
was still optimizing at the deadline on MAIN; zero incomplete connections does
not imply the optimizer exhausted every possible improvement.

Packaged WebSocket tests passed for startup with spaces in paths, health,
invalid inputs, progress/heartbeat, routing, partial timeout, job replacement,
single terminal responses, bounded-queue busy handling, queued disconnect,
recovery, an actual worker crash during routing, worker concurrency, and worker
cleanup at facade shutdown. Missing/wrong runtime, a corrupt core artifact, and
a missing worker all failed startup explicitly without fallback.
Actual-worker tests also cover malformed DSN, cooperative partial stop, and
unexpected worker exit. An injected uncooperative worker also verifies forced
termination within the grace bound, retained partial geometry, and deletion of
its job directory. A synthetic four-layer fixture also verifies protected
trace preservation, a non-ASCII net name, and routing on inner copper layer 21
on both engines.
Fifteen regression checks cover signed/quoted geometry, resolution/unit
changes, precision, malformed output, executor lifecycle, the reported miniMult
clearance geometry, and exact request/output diagnostic capture.

The user verified EasyEDA browser connectivity and routing with the legacy
adapter before this stage, and subsequently reported successful modern routing
with one clearance violation on miniMult. The exact gap is confirmed in the
provided export; see [the investigation](miniMult-clearance-investigation.md).
That failing case remains documented below alongside the subsequent clean rerun
and width correction. Windows/Linux launchers are supplied, but those
OS builds and a real Java 8 facade runtime have not been executed here. Java 8
API compatibility is checked separately with `javac --release 8`.
The worker heap has a hard JVM limit; peak RSS/leak measurements across large
production workloads remain to be collected. Ordinary disconnect, replacement,
shutdown, and crash cleanup are covered; sudden OS termination cannot guarantee
deletion of job directories.

The captured miniMult rerun subsequently passed editor DRC and verified the
actual modern engine selection. It also revealed 0.75-unit neckdown tracks
reported as 1.0-unit tracks on identical centre lines in the editor's export.
The worker now disables automatic neckdown and narrow-width retry. Replaying
that exact captured request retains requested widths, completes with no
engine-reported clearance violations, and passes a serialized-geometry DRC
check. The integration corpus checks the requested-width invariant. This fixes
a demonstrated width compatibility issue; the failing run's raw output was not
captured, so its particular creation path is not proven. The original exports
and captures remain available. See the investigation for the evidence.

## Gates before changing the default

1. Expand the confirmed canary results to realistic boards/budgets, recording remaining
   connections, DRC, vias, length, time, CPU, and peak memory. Do not weaken design
   rules simply to improve a completion score.
2. Re-import modern results in EasyEDA, verify source-relative scaling and layer
   mapping, and run editor DRC. Include multilayer designs, fixed/pre-routed
   tracks, non-ASCII/special net names, and via types used by real projects.
3. Validate Windows/Linux distributions, Java 8 facade plus Java 25 worker,
   missing/wrong runtime, and repeated-job resource usage on those hosts.
4. Promote modern after the regression gate passes. Keep explicit legacy
   selection for a transition release; remove `freerouter/` and the legacy
   target/adapter in a later dedicated change.

The modern implementation is now the default. Keeping the old engine for one
transition release is the planned rollback policy.
