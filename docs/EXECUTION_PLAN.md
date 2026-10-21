# Living Villages v1.0 execution plan

Requirements read in full: AGENTS, MASTER_PROMPT, GAME_RULES, SPECIFICATION,
ARCHITECTURE, V1_SCOPE, ACCEPTANCE_TESTS. Initial repository contains only these
documents; no implementation, wrapper, Java installation on PATH, or tests.

## Target and evidence

Minecraft 1.21.1, Java 21, NeoForge 21.1 stable branch, ModDevGradle 2.
Use the current official 1.21.1 MDK versions, pinned rather than dynamic:
https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle.
This is a deliberate supported Minecraft target, not a claim that 1.21.1 is
the newest Minecraft release. Verify against downloaded mappings by compiling.
Install a workspace-local JDK if no existing Java 21 installation is available.

## Dependency-ordered implementation

1. **Build foundation**: wrapper, Java 21 toolchain, NeoForge entrypoint,
   server config, metadata, MIT license, ignore local toolchains/caches/worlds.
   Gate: compile and load mod on a dedicated GameTest server.
2. **Persistent domain** (depends on 1): UUID villages/citizens/plans/tasks,
   versioned SavedData, immutable events, profession XP/skills, bounded retries,
   validated state machine, structured blockers and dependency diagnostics.
   Gate: deterministic tests including reload and invalid references.
3. **Discovery and observations** (2): loaded vanilla bell/POI discovery,
   citizen enrollment, bounded incremental block indexes, real containers,
   beds and workstations. Death differs from unloading. Gate: A1/A2/A11.
4. **Economy and work execution** (3): physical cargo, movement and reach
   checks, reservations, collect item entities, actual block drops, delivery,
   equipment and resource-consuming crafting. No authoritative resource counters.
   Gate: resource conservation tests and GameTests.
5. **First vertical slice** (4): needs -> wood goal -> plan -> assigned task
   -> navigation/harvest/collection/delivery -> XP/events. Backoff and recovery.
   Gate: A3/A4/A7/A14, no arbitrary log-building harvesting.
6. **Strategic planning and remaining jobs** (5): utility breakdowns for all
   required needs, duplicate suppression, dynamic workforce, farmer replanting,
   conservative exposed mining with capability/tool gates, animal breeding with
   pen/cap checks, smith recipes and replacement demand. Gate: A15-A18.
7. **Construction** (4,6): six resource blueprints; bounded site candidates,
   support/water/collision/entrance checks and rejection counts; manifests,
   dependency acquisition, reservations and staged material transport; progressive
   placement, changed-world validation, completion and real beds. Gate: A5-A10.
8. **Population and roads** (7): real bed/food observations, vanilla breeding,
   safe short surface path connections without destructive carving.
9. **Ledger and commands** (2,3,6,7): book+bell opens server snapshot with
   Overview/Citizens/Resources/Buildings/Plans/Chronicle; authorized Diagnostics,
   inspect commands sharing dependency tree. No client authority. Gate: A19.
10. **Recovery and review** (all): mid-plan restart/unload, missing storage,
    worker death, stolen resources, damaged construction, dependency validation,
    resource duplication audit, retries and performance budgets. Gate: A11-A13.
11. **Release validation**: deterministic tests, GameTests where practical,
    `gradlew test`, `gradlew build`; record actual coverage of A1-A20 and manual
    checks still needed. Update README, CHANGELOG and V1_1_BACKLOG with concrete
    findings. Compilation alone does not establish gameplay acceptance.

## Ownership and safety constraints

Server owns SavedData and all world mutations. Runtime observations may be
rebuilt; persistent plans retain IDs and selected sites. Workers use vanilla
navigation and bounded reach. Unloaded terrain is never modified or force-loaded.
Physical item transfers validate the container at transfer time; reservations
are claims, never stock. Significant events, not block-by-block history spam.
Domain logic remains testable without Minecraft; adapters use mapped APIs.

## Progress

- [x] Requirements and initial repository inspection.
- [x] Official build tooling selection.
- [x] Build foundation and toolchain.
- [x] Persistent model, tests and diagnostics.
- [x] Discovery, physical work and autonomous planning.
- [x] Blueprints, construction and population.
- [x] Ledger, commands and recovery implementation.
- [x] Automated acceptance checks, build, architecture review and release documentation.
- [ ] Manual graphical Ledger, real-process restart/unload and survival soak acceptance.

## Implemented validation and findings

The repository now contains a complete compiling mod implementation, six
resource templates, a server-owned simulation, dedicated-client Ledger,
deterministic tests and real-world GameTests. See `ACCEPTANCE_RESULTS.md` for
the A1–A20 evidence matrix and remaining manual coverage.

Fixes made through execution and review:

- Vanilla Brain movement conflicts required a dedicated work activity while
  retaining the vanilla core navigation behavior.
- Interaction requires physical range and line of sight, including block-face
  visibility for roof work. Containers and item pickup use physical movement.
- Paired-block validation checks both chunks before reading terrain.
- Tool-dependent mining loot must use the equipped tool explicitly; vanilla
  `Level.destroyBlock(..., true)` passes an empty tool to loot tables.
- Construction material consumption counts paired beds/doors only once and
  validates the already-built prefix after restoration.
- Retry attempt time is separate from the timestamp of meaningful progress.
- Growing persistence uses NBT bytes because NBT strings cannot hold a large
  chronicle. Missing/duplicate saved worker associations are repaired explicitly.
- Full storage retains cargo and searches accepting alternatives. Reserved
  resources cannot be silently consumed by another plan.

The selected toolchain is pinned to Minecraft 1.21.1, NeoForge 21.1.262,
ModDevGradle 2.0.148, Gradle 9.2.1 and Java 21. The packaged version is
`1.0.0-dev`; it is not labelled a fully manually accepted release.

Final automated run on 2026-10-20: `test build runGameTestServer` completed with
**BUILD SUCCESSFUL**, **10 JUnit tests** (zero skipped/failures/errors), and
**23 required GameTests passed**. The autonomous house fixture also passed
without prefilled planks, producing them from physical logs through the planner.
Architecture/resource/retry/client-separation review is recorded in
`ARCHITECTURE_REVIEW.md`; remaining acceptance work is recorded explicitly in
`ACCEPTANCE_RESULTS.md` and `V1_1_BACKLOG.md`.

## v1.0 acceptance and hardening pass

Scope is frozen: no v1.1 gameplay. Dependency order:

1. Review all requirements and current source; identify lifecycle, ownership,
   persistence, resource and hot-path defects.
2. Harden ownership/state/persistence invariants, then test regressions.
3. Exercise Ledger authorization, bounded snapshots and empty/long data; prepare
   explicit graphical manual procedure without claiming a visual pass.
4. Run a two-process persisted-world fixture if the available server runner
   permits it. Distinguish this controlled fixture from an ordinary-world manual
   restart and record any remaining gap.
5. Run multiple-village soak with real workers, bounded counters and isolation
   assertions; report duration and workload rather than claiming unlimited scale.
6. Run `test`, `runGameTestServer`, `build`; update A1–A20 with explicit statuses,
   evidence and exact manual procedures. Keep the artifact `1.0.0-dev`.

Implemented in this pass: deterministic boundary ownership; duplicate-stock
protection; unreadable-save fallback guard; terminal-state atomicity; active-plan
indexes; bounded Ledger data/diagnostics; safer refresh handling; loaded-index
cleanup; retry event deduplication; server-thread assertion and measured soak
counters. No v1.1 gameplay or archival subsystem was introduced.

The external restart test passed in two JVMs, preserving the same real world
files and completing the resumed house with zero remaining/duplicated material.
The main suite now has 13 JUnit tests and 28 GameTests, including 48,000 ticks
with three villages and 36 villagers. The soak fixture clears its own remote
plots before reuse so repeated runs do not inherit the previous test's houses.
See ACCEPTANCE_RESULTS.md for exact evidence/statuses and MANUAL_ACCEPTANCE.md
for the unperformed manual gates.

Final hardening regression: **BUILD SUCCESSFUL**, 13 JUnit tests and all 28
required GameTests passed. The external two-process test separately passed both
phases. Final evidence is in .tools/hardening-final.log and
run-restart-20261020-170707. README, CHANGELOG, architecture review and acceptance
matrix are updated. The existing v1.1 backlog is unchanged: no newly discovered
future gameplay task justified expanding it during this pass.

## v1.1 progression milestone (branch `feature/v1.1-progression`)

Baseline: commit `75f9806` on `main`. Verified on this workspace (macOS, Temurin
21) before changes: `gradlew test build` **BUILD SUCCESSFUL**, 13 JUnit tests.
Requirements re-read: AGENTS, README, GAME_RULES, SPECIFICATION, ARCHITECTURE,
V1_SCOPE, V1_1_BACKLOG, ACCEPTANCE_RESULTS, MANUAL_ACCEPTANCE,
ARCHITECTURE_REVIEW and this plan. Final specification: `V1_1_SPEC.md`.

Out of scope for this milestone: soldiers, archers, raids, war, diplomacy,
leaders/kings, kingdoms, colonies, walls and Nether expeditions. Technology
branches and capabilities are open enumerations so a military branch can be
added later without changing persistence or the planner contract.

Dependency-ordered work:

1. **Ledger layout fix** (independent): pure `LedgerLayout` geometry (header,
   refresh, wrapped or compact tabs, content viewport, scrollbar, footer);
   screen uses it for widgets, clipping, row hit-testing and scrolling. Pure
   refresh routing (open / update / ignore). Unit tests over GUI scale 1–4/Auto
   for common window sizes. Visual acceptance stays MANUAL_REQUIRED.
2. **Schema v2 + migration** (foundation for 3–6): tier, tier milestones,
   production record, technologies, palettes on plans/buildings, task minimum
   level. Explicit v1→v2 migration (v1 construction and buildings are oak);
   unknown future versions are still rejected. Migration unit tests.
3. **Pure progression domain** (2): requirement records, village facts,
   cumulative tier requirements I–V, capability policy, technology registry and
   policy, palette tokens/selector, production categories. Unit tests for every
   boundary, diagnostics and monotonic (non-oscillating) unlocks.
4. **Physical production record** (2): record items at the physical source
   (loot from broken blocks, furnace output, crafted output, collected wool).
   No counters change without a world action.
5. **Palettes and variants** (2,3): semantic blueprint tokens resolved by palette;
   persisted per plan/building; palette-aware recipes; t2 house unlocked by
   ADVANCED_CONSTRUCTION. Old v1 templates resolve identically with oak.
6. **Planner integration** (3,4,5): low-frequency progression evaluation and
   level-up events; capability checks instead of tier numbers; research plans
   with reservations, workstation dependencies and a physical research executor;
   charcoal fuel; deeper mining band; diamond mining plan and executor gates.
7. **Ledger Progression tab, commands, diagnostics** (3,6): read-only
   `/livingvillages progression inspect`; shared diagnostic tree.
8. **GameTests** for tiers, technology, restart, palettes, diamonds and Ledger
   bounds; v1.0 suite retained.
9. **Verification**: `test`, `runGameTestServer`, `build`, two-JVM restart;
   architecture review; documentation; version `1.1.0-dev`.
