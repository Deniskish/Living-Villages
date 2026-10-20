# v1.0 acceptance and hardening — 2026-10-20

Artifact remains **1.0.0-dev**, Minecraft 1.21.1 / NeoForge 21.1.262 / Java 21.
No graphical acceptance or ordinary generated-world playthrough is claimed.

## Reproduction and evidence

Run the wrapper using scripts/gradle.ps1 with arguments: **test runGameTestServer build**.
Run **scripts/acceptance-restart.ps1** for two separate server JVMs against the
same isolated world. It preserves logs/worlds in a new run-restart-TIMESTAMP
directory and never deletes earlier evidence.

- JUnit: **13 tests**, zero skipped/failures/errors; build/reports/tests/test/index.html.
- Main dedicated-server suite: **28 required GameTests**.
- External restart: **one test in each of two processes**, both passed.
  Evidence: run-restart-20261020-170707/Write.log and Read.log.
- Final regression log: .tools/hardening-final.log.
- Final regression result: **BUILD SUCCESSFUL**, all 28 required GameTests passed,
  no errors in the test/server log. JAR SHA-256:
  3042B81B98EAE6477C7A33B784AE246D686FC18AFB8CB1A7B4001851DB8FFA95.
- Earlier measured soak: .tools/hardening-check.log.
- Generated reports and test worlds are ignored by Git.

Test names below refer to VillageGameTests unless a different class is named.
PASS_GAMETEST establishes the stated controlled-world behavior.
PASS_AUTOMATED includes deterministic or cross-process verification.
PARTIAL means the entire scenario has not been accepted.
Procedures M1–M3 are in [MANUAL_ACCEPTANCE.md](MANUAL_ACCEPTANCE.md).

## A1–A20

| ID | Status | Concrete evidence | Remaining coverage / limitation |
|---|---|---|---|
| A1 | PARTIAL | bellVillageRegistersExactlyOnce; RestartGameTests.externalProcessRestart discovers a real bell/villager and reloads the same VillageId/membership in a new JVM. | M1/M2: naturally generated village with a real client. |
| A2 | PASS_AUTOMATED | DomainTest.roundTripPreservesIdentityProgressDiagnosticsAndHistory; savedDataAndVanillaCargoRoundTrip; external restart asserts name, role, level, XP, skills and membership. | Controlled fixture passed; ordinary-world lifecycle remains M2. |
| A3 | PASS_GAMETEST | lumberjackWalksHarvestsCollectsAndDelivers; HardeningGameTests.threeVillagesThirtySixCitizensSoak observes forestry XP in each village. | Small reachable trees; biome/long-term balance is M3. |
| A4 | PARTIAL | unreachableTreeBacksOffAndRecoversWhenOpened asserts spaced retries, BLOCKED and parent reason; ledgerSnapshotPreservesDiagnosticsAndPermissions checks diagnostic data. | Graphical display during this failure is M1. |
| A5 | PASS_GAMETEST | constructionCannotPlaceWithoutMaterials; reservationsProtectAnotherPlansPhysicalItems; autonomous house fixture produces missing planks from physical logs. | Simultaneous survival shortages are M3. |
| A6 | PARTIAL | DomainTest.dependenciesExplainMissingAndCyclicReferences; autonomous material dependencies and the shared diagnostic tree are exercised. | Full housing → missing planks → unreachable forestry chain in the client remains M1/M2. |
| A7 | PARTIAL | unreachableTreeBacksOffAndRecoversWhenOpened resumes delivery without editing plans; external restart resumes construction to completion. | These are separate fixtures; full A6-to-house recovery remains manual. |
| A8 | PARTIAL | siteValidationRejectsWaterAndPlayerStructures; DomainTest.sitesRejectWaterSlopeCollisionAndUnsupportedTerrain; autonomous real-site selection. | More steep/inaccessible-entrance world layouts need M3. |
| A9 | PASS_GAMETEST | builderCompletesRealHouseProgressively verifies manifest, movement, staged placement, completion and restoration; villageAutonomouslyPlansAssignsAndBuildsHouse also produces required planks. | Visual inspection of all six templates is M1/M3. |
| A10 | PARTIAL | playerObstructionDoesNotSpendMaterial preserves the diamond block and materials. | Obstruction precedes first placement; repeated mid-build entrance/roof edits remain M3. |
| A11 | PASS_GAMETEST | destroyedStorageInvalidatesPhysicalStock; transferConservesItemsWhenDestinationIsFull; HardeningGameTests.overlappingVillagesNeverDoubleCountStorage. | Concurrent multi-storage disruption remains M3. |
| A12 | PASS_AUTOMATED | builderCompletesRealHouseProgressively; largeChronicleSurvivesCompressedNbt; external restart loads real region/entity/SavedData files, compares state and reconciles cargo + chest with the remaining manifest. | Ordinary generated-world restart is M2 / MANUAL_REQUIRED. |
| A13 | PARTIAL | deathReleasesTaskAndDropsCargo checks death, released ownership, blocker, removed inventory cargo and history. | Replacement worker completing the same job in a longer session is M3. |
| A14 | PASS_AUTOMATED | DomainTest.experienceBoundariesAndMiningGates; physical jobs award XP; external restart checks earned role/skill XP and levels after a completed house. | Client observation of repeated level-ups is M1/M3. |
| A15 | PARTIAL | minerUsesActualToolLootAndDelivers checks real stone loot/tool durability; domain tests check tier gates and diamond exclusion. | Actual ore fixtures at every tier are not covered. |
| A16 | PARTIAL | farmerHarvestsReplantsAndDelivers plus farming in all three soak villages. | Wheat passed; other crops and player field changes need M3. |
| A17 | PASS_GAMETEST | boundedPenBreedingConsumesFood; animalCapDoesNotConsumeFood. | Full offspring/pen lifecycle and balance remain M3. |
| A18 | PARTIAL | smithUsesVanillaRecipeAndExactIngredients; furnaceConsumesRealFuelAndProducesIron; miner equipment/durability test. | Worn tool → replacement demand → crafting → re-equipment chain remains M3. |
| A19 | PARTIAL | ledgerSnapshotPreservesDiagnosticsAndPermissions; HardeningGameTests.ledgerBookBellAuthorizationAndMissingVillage checks Book/bell, removed bell, unknown/deleted village; ledgerLargeAndEmptyDataRemainBounded exercises null/empty/long/escaped/large data. | Actual click → packet → rendered screen, resize/close/reopen is M1 / MANUAL_REQUIRED. |
| A20 | PASS_AUTOMATED | Wrapper test, runGameTestServer, build: 13 JUnit tests, 28 main GameTests; both external restart phases separately pass. | Unperformed manual scenarios remain unaccepted. |

## External restart

RestartGameTests.externalProcessRestart uses an isolated flat GameTest world.
Process one discovers a bell/villager, supplies real physical materials, drives
movement/construction, completes one house and saves at 20 blocks of a second.
The checkpoint has three plans, a completed building, earned XP/skills,
dependencies, a structured blocked plan and history.

The process exits normally. A second JVM loads the same files; it does not call
Villager.load() or inject inventory copies. It checks identities/state, waits
for real entity storage, reconciles actual chest + cargo against the remaining
manifest, and completes exactly one more building. Final evidence:
**buildings=2 physical_remaining=0**, VillageId
fa65caa1-1ad3-4b35-955d-ad1b7e44af8b.

The fixture controls construction scheduling and freezes other planning.
GameTest world settings/clock initialization differ from normal server startup.
The blocked plan is a diagnostic fixture, not a forest blockage generated in
this test. This is real process/file persistence evidence, not a substitute for
the ordinary generated-world procedure M2.

## Multi-village soak

HardeningGameTests.threeVillagesThirtySixCitizensSoak executes **48,000 ticks**
with three independent villages and **36 actual villagers**. That represents
40 minutes at 20 TPS, accelerated by GameTest, not 40 wall-clock minutes.
Daylight is fixed and GameTest random ticking is disabled. Each village has
controlled flat terrain, physical supplies, mature wheat, a tree and exposed stone.

The third village loses its test-only chunk tickets at tick 8,000; its center
must actually be unloaded at 11,500 and is reloaded at 12,000. Production code
does not create these tickets.

Measured final successful run in .tools/hardening-final.log:

| Village | Citizens | Total / active plans | Events | Houses | Scan steps | Strategies | Work calls | Mean / max work call |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Soak 0 | 12 | 76 / 5 | 408 | 2 | 4,800 | 240 | 1,979 | 86 / 4,468 µs |
| Soak 1 | 12 | 52 / 4 | 280 | 2 | 4,800 | 240 | 1,433 | 89 / 1,480 µs |
| Soak 2 | 12 | 98 / 6 | 489 | 2 | 4,400 | 220 | 2,058 | 84 / 1,402 µs |

Assertions cover unique citizen/plan/task/storage/history ownership, unique
active kinds, plan cap, explained BLOCKED state, bounded plan/event growth,
scan/strategy frequency and aggregate worker budget. Each village must build
and gain forestry/farming XP. Timing measures job execution only, not whole
Minecraft ticks, frame rate or saving. Measurements vary between runs.

## Findings and fixes

- Duplicate storage references and overlapping boundaries could count a chest
  twice/across villages. Counts now deduplicate positions. Nearest-center
  ownership with UUID tie-break is shared by discovery, stock, harvesting,
  building sites and bell resolution.
- Minecraft catches SavedData deserializer errors and invokes a fallback
  constructor. That constructor now refuses to replace an existing unreadable
  save. corruptExistingSaveCannotBeReplaced verifies refusal and unchanged bytes;
  it tests the guard, not every possible disk fault.
- Terminal progress/block operations previously changed metadata before throwing.
  They now validate first; completion is idempotent. Restore clears terminal
  claims and cancels orphan active tasks.
- Ledger bounds rows, strings, diagnostic expansion and escaped JSON size;
  personal events use one bounded scan instead of full-history scans per citizen.
  Empty snapshots and secondary-bell refresh are handled.
- Client refresh cannot reopen a closed screen. Tabs survive refresh and wrap
  on narrow layouts; detail wrapping is cached; title/footer text is clipped.
  These changes compile, but visual acceptance remains manual.
- Active/latest-kind indexes remove terminal archives from frequent work.
  Unloaded/removed villages release indexes and stop strategic scans. Scan cursor
  uses long arithmetic. Same-worker retries no longer emit duplicate assignment
  events.
- Container arrival range covers legal adjacent approach positions while
  retaining line of sight and a three-block distance limit. This fixes a farmer
  delivery regression at a reachable chest.
- Animal pen searches check neighboring chunks before reading them. Simulation
  entry asserts the server thread. Pack metadata supports both 1.21.1 data and
  resource formats.

## Remaining release gates

| Gate | Status | Procedure |
|---|---|---|
| Graphical Ledger, real interaction and multiplayer UX | MANUAL_REQUIRED | M1 |
| Ordinary generated-world restart/unload/reload | MANUAL_REQUIRED | M2 |
| Two-hour normal-speed survival session with multiple villages | MANUAL_REQUIRED | M3 |

History and completed plans remain permanent records, with memory/disk usage
proportional to meaningful activity. They are excluded from frequent queues;
this pass adds no archival subsystem and deletes no history. Multi-year
archives, arbitrary-biome bootstrap, claim-mod integration and larger settlement
profiling remain known limits. No **1.0.0 stable** claim is made.
