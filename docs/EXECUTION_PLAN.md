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
