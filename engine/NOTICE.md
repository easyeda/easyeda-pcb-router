# Freerouting worker dependencies

The worker uses the unmodified official Freerouting 2.5.0 executable JAR.
Version, source commit, download URL, and SHA-256 are pinned in `upstream.json`.
Freerouting is copyright Alfons Wirtz, Andras Fuchs, and its contributors,
distributed under GPL-3.0-or-later. The repository's GPL text is packaged as
`LICENSE` alongside the worker. Corresponding pinned source is linked in the
manifest; distributors must meet the GPL source-distribution requirements.

The upstream executable retains its dependency license/notice resources.
`ant fetch-engine` verifies the artifact; packaging preserves the complete JAR,
including those resources. Worker code in this repository uses the same GPL
license as this project. No analytics client, GUI, REST server, or MCP server
is initialized by the worker.
