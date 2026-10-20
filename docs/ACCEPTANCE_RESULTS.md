# Acceptance evidence — 2026-10-20

Artifact: `livingvillages-1.0.0-dev.jar`. Minecraft 1.21.1, NeoForge 21.1.262,
Java 21, Gradle 9.2.1. This report distinguishes executable evidence from
unperformed manual release acceptance.

## Reproduction

```powershell
.\scripts\gradle.ps1 test build runGameTestServer
```

The script invokes the checked-in Gradle wrapper with the workspace JDK when
available. With a configured Java 21 installation, the equivalent commands are
`gradlew test`, `gradlew build`, and `gradlew runGameTestServer`.

JUnit report: `build/reports/tests/test/index.html`.
GameTest output: `run-gametest/logs/latest.log`.
Final combined verification log in this workspace: `.tools/verification.log`.
These generated files and test worlds are deliberately ignored by Git.

## Scenario coverage

“Automated” below means the described fixture passed, not that every possible
survival-world variation of the acceptance scenario has been exercised.

| Scenario | Automated evidence | Remaining scope |
|---|---|---|
| A1 Registration | A real bell/POI and villagers register once; repeated discovery after domain reload preserves the village ID. | Discovery in a naturally generated village during a client playthrough. |
| A2 Citizen identity | Domain identity, name, role, XP, history and assignments round-trip; vanilla entity UUID and actual carried items round-trip through NBT. Duplicate/missing saved workers are diagnosed and repaired. | A real server process restart and repeated entity unload/reload. |
| A3 Lumberjack | A real villager walks to a tree, breaks its blocks, collects item entities, delivers logs and earns XP. | Long-term forestry, sapling supply and biome variation. |
| A4 Unreachable trees | A tree enclosed by walls causes structured `PATH_UNREACHABLE`, parent-plan diagnostics, spaced retries and escalation to `BLOCKED`. | Viewing that live failure through the graphical Ledger. |
| A5 Missing materials | A builder with no materials places no block and reports the shortage; resource claims protect another plan's actual items. | Full survival-session shortage balancing. |
| A6 Dependency chain | Domain tests validate missing/cyclic references; plan inspection and Ledger use the same tree. Autonomous construction requires a material-production plan. | The complete multi-stage debug scenario observed through the client UI. |
| A7 Recovery | Opening the enclosed tree's access resumes gathering and delivery without changing saved plan data; successful progress clears the blocker. | Recovery from multiple simultaneous shortages in a survival village. |
| A8 Site rejection | Actual supported flat terrain is accepted, water and an existing chest are rejected; pure rules cover slopes, unsupported ground and scoring. Autonomous planner selects a buildable site. | More irregular terrain and inaccessible-entrance layouts. |
| A9 Progressive building | A moving builder places an entire real house over time, consumes its exact manifest, registers it and emits completion history. A separate autonomous planner scenario assigns work and builds a house. | Visually observing the other five templates in a client. |
| A10 Player obstruction | An obstructing diamond block remains untouched; construction reports obstruction without spending material. Construction also revalidates the completed prefix. | Repeated edits to entrances and partially built roofs. |
| A11 Storage removal | Destroyed storage disappears from stock and references; full-container transfers conserve every item and retain untransferred cargo. | Multiple storage removals while several workers carry cargo. |
| A12 Mid-plan reload | A house build serializes and restores SavedData after at least 20 placements, then resumes to completion with exactly one registered building. A 1,000-entry chronicle passes real compressed NBT round-trip. | Full process restart, chunk-file/entity-file integration and unload/reload soak. |
| A13 Worker death | Killing an assigned worker marks death, clears ownership, diagnoses worker loss, removes dropped cargo from inventory and writes history. | Replacement assignment in a longer multiplayer session. |
| A14 Progression | XP boundaries I–V are deterministic and persist; real forestry/farming/mining/building gain XP only through successful actions. | Watching repeated level-ups in a long client session. |
| A15 Mining | A miner uses an actual wooden pickaxe, breaks real stone, delivers precisely one cobblestone and spends durability. Unit tests cover coal/copper/iron/high-tier gates and diamond exclusion. | Real-world ore fixtures at every tier and larger exposed mining sites. |
| A16 Farming | Mature wheat is physically harvested, replanted using real seeds and delivered; farmer XP increases. | All supported crop types under changing player field layouts. |
| A17 Animals | Real animals in a bounded pen consume two food items and enter vanilla breeding. At the configured cap, no food is consumed. | Full offspring lifecycle, pen expansion and long-term balancing. |
| A18 Smith | A wooden pickaxe uses an actual crafting-table recipe and exact ingredients; a real furnace consumes raw iron/fuel and produces iron. | A worn-out tool's entire replacement chain in a client village. |
| A19 Ledger | Server snapshot round-trip preserves IDs and blocker details; six player tabs and permission-controlled Diagnostics are verified. Dedicated-server load verifies common code does not load client screen classes. | Actual book/bell interaction, graphical navigation, resizing and multiplayer refresh. |
| A20 Build | Unit tests, full build and dedicated-server GameTests are executed through the Gradle wrapper. | See final run totals below. |

## Fixture boundaries

GameTests create controlled terrain, villagers and material supplies as test
setup. Production gameplay contains no corresponding free-resource command.
The autonomous house test starts with physical logs and other supplies in a
chest, but no building planks: the planner must craft them. It does not prove a
complete self-sufficient economy starting in every naturally generated biome.

The full house executor test resumes a serialized simulation midway while the
GameTest server remains running. The separate entity test restores actual
vanilla inventory NBT. These are useful persistence checks, but do not establish
atomic saves across independent Minecraft files or replace process-restart tests.

The site/autonomy tests use open sky. Minecraft's default GameTest enclosure has
an invisible roof that affects surface heightmaps; leaving that roof in these
fixtures would test the enclosure instead of the intended terrain.

## Manual release gate

Before promoting this development artifact to a fully accepted v1.0:

1. Run the A1–A19 playthrough in a naturally generated plains village, including
   the full blocked housing → blocked forestry → restored access → completed
   house chain and visible personal level-up.
2. Save and stop the process during active construction and carrying. Restart,
   compare IDs/materials/progress, then unload/reload the settlement repeatedly.
3. Inspect all Ledger tabs using a regular player and an operator; test refresh,
   screen resizing and a dedicated client/server connection.
4. Observe multiple villages and several in-game days; record tick/save costs,
   stalled plans, food/tool balance and unintended interference with buildings.

No graphical screenshots or manual-session results are claimed by this report.

## Final automated run

`test build runGameTestServer` completed with **BUILD SUCCESSFUL**:
**10 JUnit tests**, zero skipped/failures/errors, and **23 required GameTests
passed**. The final run includes autonomous production of the missing house
planks and full construction.

The development artifact remains labelled `1.0.0-dev` until the manual gate
above is completed.
