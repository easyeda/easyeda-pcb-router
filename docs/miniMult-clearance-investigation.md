# miniMult clearance investigation

Input: local `Autorouter_PCB_miniMult_2026-10-31.dsn`, SHA-256
`04f299bf7925c92d3d44a209e7fc5f9b6403dbf7ce03d3ec44043a5e2f729882`.
The original export was left unchanged. Diagnostic variants and outputs are
under `.build/drc-investigation/`; they are not replacements for the user's PCB.

## Confirmed geometry

The reported `gge8708 - gge8975` gap is reproduced in the exported geometry:

- Layer: bottom copper (`2`).
- Foreign trace net: `R3_2`, width `1.0` in the preserved EasyEDA convention.
- Pad/via alias `8975`: net `DP2_2`, centre `(64.444, -180.445)`, circular
  padstack `p8651`, diameter `2.4016`.
- Nearest offending trace segment: `(64.772, -177.011)` to `(62.247, -179.536)`.
- Exact circle-to-segment copper gap, including half the trace width:
  `0.12585031024081486 mm`, matching EasyEDA's rounded `0.126 mm`.
- The DSN net-class clearance is `0.5984`, corresponding to `0.1519936 mm`,
  matching EasyEDA's rounded `0.152 mm` requirement.

An independent upstream DRC check on the input, without routing it, also detects
this foreign-net trace/pin violation. Its discretized measurements are about
`0.125903 mm` actual and `0.153416 mm` required. The small measurement difference
does not account for the approximately `0.026 mm` shortfall. It is not a JSON
precision or display-rounding artifact.

The trace is already present in the file with `(type protect)`. Freerouting
therefore treats it as user-fixed when loading this export. This file reproduces
the violation's geometry, but cannot establish which pre-routing input produced
it or whether via sizing changed during browser re-import. Those require the
actual DSN exported before the problematic routing run.

## Checks performed

- Loaded the original export and inspected its clearance violation geometry.
- Verified the gap directly from the raw DSN coordinates independently of the
  engine's DRC implementation.
- Added a reduced regression check proving SES conversion preserves these
  coordinates and the detectable clearance violation at resolution `mil 1000`.
- Ran a diagnostic variant with all exported wiring removed, retaining its pad
  and via-alias placement. Modern routing produced zero engine-reported
  clearance violations and zero remaining connections on that variant. This is
  a different routing problem, not a reproduction of the user's original run;
  it discards the exported fixed tracks and must not be applied as a repair.
- Tried rerouting an unprotected diagnostic copy. That does not reliably repair
  the existing violation, even with strict checking of newly routed items.

No production routing rules or binaries were changed on this evidence alone.
The current result code `1` denotes zero incomplete connections, independently
of DRC acceptance. Modern default promotion remains gated on editor DRC.

## Next evidence needed

Historical investigation step; the captured rerun and width fix below supersede
this request for a manually exported pre-routing file.

Establish whether this export was made before or after the reported run. If it
is an after-routing export, save a separate pre-routing DSN. Compare its pad/
via sizes, allowed via types, clearances, and fixed tracks with the worker's raw
SES and the after-import export. Reproduce with the same optimization-pass
count and worker settings before selecting a routing or conversion fix.

## Comparison with the old-router export

The user supplied `Autorouter_PCB_miniMult_2026-10-31-OLD-ROUTER.dsn` for the
same board, with no other edits, and reports that it passes EasyEDA DRC.

- Resolution, outline, global rules, layer definitions, and `via0`/`via1`
  padstack sizes are identical between exports.
- All 122 original pads match exactly. Each export additionally contains 15
  via-alias pads at positions produced by that routing result.
- Per-net class widths, clearances, and allowed vias match by class name;
  differences in scope ordering are not rule-value changes.
- The old export has 113 wire records, the modern export 141. Via locations and
  trace paths differ. The old export has no foreign-net clearance collision
  under the independent engine check; the modern export has the reported
  `R3_2` to `DP2_2` collision.
- The engine also flags same-net via/pin overlaps caused by the exported alias
  representation (30 checks on the old file). These are distinct from the
  genuine foreign-net clearance failure and are not evidence that the editor's
  clean result is incorrect. The modern file reports that physical foreign-net
  collision twice, against the via and its colocated pin alias.

A common unrouted diagnostic board was reconstructed by removing both sets of
generated via aliases and their network references, and clearing wiring in
test copies. The remaining layout, connectivity, and rules match between the
two exports, allowing for scope ordering. Fresh modern-worker routing of that
common input completed without engine DRC violations. Rebuilding its serialized
SES as DSN and checking again also found no violations. That experiment has not
reproduced the original offending run.

The investigation therefore confirms a regression in the resulting geometry,
but has not isolated its creation stage. Routing settings and the user's two
exports remain unchanged. An opt-in diagnostic build now records the actual
request, raw SES and exact browser JSON for one more live run. Instructions are
in [development notes](development.md#capturing-a-routing-regression); capture
is local, disabled by default, and does not change routing rules.

## Captured clean rerun and width compatibility fix

The user completed the capture-enabled rerun and reports no EasyEDA DRC issues.
Capture: `.build/routing-diagnostics/route-9713144598188720112/`. The actual
selected engine is `modern`, overriding the stored `engine: legacy` setting.
The request asks for 300 seconds, progress every 2 seconds, and 3 optimization
passes; the configured minimum raises the worker budget to 1600 seconds.
The worker uses 2 optimizer threads. Its input is unrouted, and has SHA-256
`517309cd40b91009197dae62b8014906ace9c01f58441b6fd7a442b4465c6511`.

The clean run returned 21 nets, 149 wires, 16 vias, and zero incomplete
connections. The raw SES and browser JSON agree exactly on trace segments,
widths, and via centres. The separately supplied
`Autorouter_PCB_miniMult_2026-10-31-MODERN-ROUTER-RERUN.dsn` also has those 16 via
centres and no foreign-net clearance collision in the independent check.

However, the successful round trip exposes a width incompatibility:

- The request declares net-class width `1.0` (`0.254 mm`).
- Automatic neckdown emits some tracks at `0.75` (`0.1905 mm`). Those widths
  survive SES-to-JSON conversion exactly.
- On 26 identical centre-line segments, the after-import DSN reports width
  `1.0` instead. These exports alone do not distinguish import-side widening
  from export-side width normalization, but demonstrate that the narrower width
  is not preserved by the observed round trip.
- A change from 0.75 to 1.0 consumes `0.03175 mm` of edge clearance per side.
  This is sufficient to turn a legal `0.15760 mm` gap into the previously
  observed `0.12585 mm` gap. The original failing run's raw SES was not captured,
  so that specific earlier trace's pre-import width remains unproven.

The worker now explicitly disables automatic neckdown and the optional
neck-width retry. This keeps generated widths consistent with the request and
does not loosen the board's clearances. Replaying the captured input with the
same thread/pass settings produces 123 wires and 14 vias, zero incomplete
connections, zero engine-reported clearance violations, and exclusively
1.0-unit tracks. Rebuilding the serialized result as DSN and checking with zero
DRC tolerance also gives zero clearance violations.

The actual-engine regression suite now asserts declared-width compliance on
all four sample boards. Before the fix their minimum widths were 0.606 vs
0.81 requested, 1.2 vs 1.6, and 47.24 vs 62.99 on the two remaining boards.
The fixed worker passes these checks and still routes all four to zero
incomplete connections in the 20-second comparison. Packaged WebSocket and
capture/lifecycle tests also pass. The diagnostic distribution has been rebuilt
in `.build/modern-diagnostics/`; existing captures and user exports are retained.
