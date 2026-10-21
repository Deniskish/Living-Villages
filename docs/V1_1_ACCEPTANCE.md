# v1.1 acceptance — 1.1.0-dev, 2026-10-21

Minecraft 1.21.1 / NeoForge 21.1.262 / Java 21 (Temurin 21.0.12.1, macOS arm64).
Branch `feature/v1.1-progression`. Specification: [V1_1_SPEC.md](V1_1_SPEC.md).
No graphical, ordinary-world or survival-length acceptance is claimed.

## Evidence

Commands (macOS/Linux; Windows uses `scripts\gradle.ps1` / `acceptance-restart.ps1`):

```sh
./gradlew clean test runGameTestServer build
./scripts/acceptance-restart.sh
```

| Check | Result |
|---|---|
| JUnit (`gradlew test`) | **36 tests**, 0 failures, 0 errors, 0 skipped (13 v1.0 + 23 v1.1) |
| GameTests (`runGameTestServer`) | **All 40 required tests passed** (28 v1.0 + 12 v1.1) |
| Build (`gradlew build`) | **BUILD SUCCESSFUL**; `livingvillages-1.1.0-dev.jar` SHA-256 `60fb12a2de0c74d60e6ff6519bb4605cdb8aec8d93ed9b8960238b722b9c5abf` |
| Two-JVM restart | **Both phases passed**; evidence `run-restart-20261021-140332` (`progress=20 research_progress=2` written; `buildings=2 technologies=[economy.charcoal_burning] physical_remaining={minecraft:oak_log=4}` verified) |
| Stability after fixture fixes | 6 consecutive full GameTest passes; 4 consecutive restart passes (one exercised a transient path failure and recovered) |
| Logs | `.tools/v11-final.log`, `.tools/v11-final-restart.log` (ignored by Git) |

Baseline before any change (same machine, commit `75f9806`): 13 JUnit tests and
28 GameTests passed.

Status vocabulary as in [ACCEPTANCE_RESULTS.md](ACCEPTANCE_RESULTS.md):
PASS_GAMETEST (controlled dedicated-server world), PASS_AUTOMATED (deterministic
or cross-process), MANUAL_REQUIRED (not performed by a person).

## Required GameTests

All in `ProgressionGameTests` unless noted.

| # | Scenario | Test | Status | What the fixture provides / limits |
|---|---|---|---|---|
| 1 | Village naturally qualifies for Tier II → exactly one level-up event | `villageNaturallyReachesTierTwoExactlyOnce` | PASS_GAMETEST | Production `Simulation` (planner, scheduler, jobs) on a remote plot: 6 villagers, 7 beds, chest with one house manifest + bread/seeds/pickaxes, 6 trees, 20 mature wheat. The village builds the house, fells trees and farms by itself. Checks one event after five more evaluations and after a codec round trip. Daylight fixed; random ticks off. |
| 2 | Requirements incomplete → no level-up, correct blocker | `incompleteRequirementsDoNotLevelUp` | PASS_GAMETEST | Same simulation with 4 trees (12 logs max). After everything else is met: no event, `PRODUCTION:WOOD required=16 actual=12`, diagnostic tree `REQUIREMENT_UNMET`, Ledger row `[✗] Wood harvested 12 / 16`. |
| 3 | Village reaches Tier III through physical production | `villageReachesTierThreeThroughPhysicalIronProduction` | PASS_GAMETEST | Fixture represents a saved Tier II village: buildings placed block-by-block and registered, earlier wood/food record, Miner/Smith II XP. Only iron is missing; 8 real furnace cycles produce it; the 8th ingot gives one TOWN event. |
| 4 | Technology: resources exist → reserved → consumed → unlocked | `technologyReservesConsumesAndUnlocksOnce` | PASS_GAMETEST | Planner creates the research plan from a DEVELOPMENT goal and reserves real stock; assignment rule (Smith II) checked via `Simulation.qualified`; the fixture drives `ResearchJob`. Exactly the cost leaves storage; one unlock event; charcoal capability available. |
| 5 | Technology: resources missing → BLOCKED, no fake unlock | `technologyWithoutResourcesBlocksWithoutUnlock` | PASS_GAMETEST | Claims never exceed physical stock; acquisition dependencies `GATHER_WOOD:minecraft:oak_log`, `MINE`; BLOCKED `MISSING_RESOURCE` with ≤ 6 spaced attempts in 2,500 ticks; nothing consumed. |
| 6 | Restart during technology plan → continues without duplicated items | `technologyResumesAfterReloadWithoutDuplication`; `RestartGameTests.externalProcessRestart` | PASS_AUTOMATED | In-process SavedData round trip and a real two-JVM restart (entity inventory from region files). Same unit counter; chest + cargo unchanged by restart; exactly one cost consumed; one unlock. |
| 6b | Researcher dies mid-work | `researcherDeathBlocksWithoutFreeUnlock` | PASS_GAMETEST | Production death handler: cost drops as 16 real logs, task released, plan BLOCKED, work units kept, no unlock. |
| 7 | Building palette survives save/reload | `buildingPaletteSurvivesSaveReload`; restart spruce house | PASS_AUTOMATED | Spruce `house_t1_02` saved/loaded at 20 blocks, completed in spruce (log/planks/door asserted), inspected COMPLETE against spruce. Two-JVM run completes a spruce `house_t1_01`. |
| 8 | Two villages with different local materials → different palettes, no shared state | `twoVillagesChooseDifferentLocalPalettes` | PASS_GAMETEST | Real spruce and birch trees indexed by each village's own survey; same planner instance; distinct plans/palettes/scores. |
| 9 | Diamond mining unavailable before capability | `diamondMiningUnavailableBeforeCapability` | PASS_GAMETEST | Miner V with iron pickaxe, exposed ore. Tier III and Tier IV-without-technology both locked with explanations; planner creates no plan; diamond task blocks `CAPABILITY_LOCKED`; generic mining ignores the ore. Tier is set as persisted history. |
| 10 | Diamond mining only with all real requirements | `diamondMiningRequiresEveryRealRequirement` | PASS_GAMETEST | Tier IV + Deep Prospecting record (persisted history). Miner IV cannot target; Miner V with only a stone pickaxe blocks on `minecraft:iron_pickaxe`; with a real iron pickaxe from the chest the ore is broken, loot delivered, durability used, production and first-diamond milestone recorded. |
| 11 | Responsive Ledger data remains bounded | `ledgerProgressionDataRemainsBounded` | PASS_GAMETEST | 600 plans, 6,000 events, 40 citizens, 960-character name: JSON ≤ 240,000; Progression rows bounded, text markers, stable keys; layout areas disjoint at 320×240, 480×270, 960×540. Not a visual check. |
| 12 | Existing v1.0 GameTests still pass | 28 tests in `VillageGameTests`, `HardeningGameTests` | PASS_GAMETEST | Three assertions updated for intentional changes: seven templates (was six), seven player tabs (was six) — Progression added. The soak additionally asserts progression evaluation, unique tier milestones and recorded wood production. |

## Unit tests (deterministic)

| Area | Tests |
|---|---|
| Tier I requirements | `ProgressionPolicyTest.tierOneRequiresALivingHousedPopulation` |
| Tier II–V boundaries, each requirement matters | `everyTierBoundaryIsExactAndEachRequirementMatters`, `tierTwoBoundaryValues`, `tierThreeFourFiveBoundaryValues`, `cumulativeThresholdsNeverDecreaseSoQualificationIsMonotonic` |
| Missing requirement diagnostics | `missingRequirementsAreStructuredAndExplained` |
| No oscillation / unique milestones | `historicalTierNeverOscillatesAndMilestonesAreUnique`, `multiTierJumpRecordsEachTierOnceInOrder` |
| Capabilities | `CapabilityTechnologyTest.capabilitiesCombineTierTechnologyAndPhysicalInfrastructure`, `snapshotSeparatesUnlockedAndLockedCapabilities` |
| Technology prerequisites / registry | `technologyPrerequisitesTierProfessionAndOrder`, `everyTechnologyHasPhysicalCostAndRealEffectInEachBranch` |
| Technology persistence | `technologyStateAndResearchPlanSurviveSerialization` |
| Palette resolution / selection | `PaletteTest.everyVanillaWoodFamilyResolvesEveryToken`, `localEnvironmentSelectsPaletteDeterministically` |
| Palette persistence | `selectedPaletteIsPersistedWithPlansAndBuildings` |
| v1.0 → v1.1 migration | `MigrationTest.v1SaveMigratesWithoutLosingIdentityProgressOrHistory`, `unknownFutureVersionsAndMalformedDocumentsAreRejected` |
| Ledger layout / refresh | `LedgerLayoutTest` (GUI scale 1–4/Auto × 10 window sizes, 7,280 resized sizes × 2 tab sets, wrapping/compact/too-small, fitting, scrolling, routing) |

## Manual gates

| Gate | Status | Procedure |
|---|---|---|
| v1.0 Ledger overlap bug fixed visually; Progression tab readable | **MANUAL_REQUIRED** | [MANUAL_ACCEPTANCE.md M4](MANUAL_ACCEPTANCE.md) |
| Graphical Ledger / server authority (v1.0) | MANUAL_REQUIRED | M1 |
| Ordinary-world restart incl. a research plan | MANUAL_REQUIRED | M2 (+ verify a mid-research plan and a non-oak house) |
| Long survival soak, Tier III–V balance | MANUAL_REQUIRED | M3 |

The overlap bug is fixed in code and its geometry is tested, but it is **not**
recorded as PASS_MANUAL: no person has viewed the client during this milestone.
