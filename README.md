# Living Villages

A Java 21 / NeoForge mod that gives vanilla villagers persistent identities,
autonomous work, resource-dependent plans, progressive construction and a village
chronicle. Gameplay state belongs to the logical server, including single-player.

**Current artifact: `1.1.0-dev`, a development milestone.** v1.1 adds village
tiers I–V, a capability layer, physically researched technologies, wood palettes
and a responsive Ledger ([v1.1 specification](docs/V1_1_SPEC.md)). The automated
suite runs real workers and completes a house through both the construction
executor and the autonomous planner. A manual client playthrough and extended
survival-world soak test remain release acceptance work; this is not a claim that
every biome, terrain shape or mod combination has been validated.

The v1.0 hardening pass additionally exercises a two-process restart with real
world/entity files and a 48,000-tick simulation of three villages / 36 villagers,
including chunk unload/reload. These are controlled automated fixtures. Follow
the [manual acceptance protocol](docs/MANUAL_ACCEPTANCE.md) before treating this
as a stable release.

## Requirements and installation

- Minecraft **1.21.1**
- NeoForge **21.1.262** or a compatible newer **21.1.x**
- Java **21**
- Install `build/libs/livingvillages-1.1.0-dev.jar` in `mods` on both client and
  server. Existing 1.0.0-dev worlds are migrated in place (schema 1 → 2); back up
  valuable worlds first. A 1.1 world cannot be opened by 1.0 (it refuses the newer
  save instead of overwriting it). Do not install this artifact on other Minecraft minor versions.

The build pins ModDevGradle **2.0.148** and Gradle **9.2.1**, following the
[official 1.21.1 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle).
Minecraft 1.21.1 is the selected supported target, not the newest Minecraft release.

## First village

1. Visit a normal village containing a bell and villagers. Registration happens
   periodically while a player is nearby. Names and UUID membership persist.
2. Hold a vanilla **Book** and right-click the bell to open the **Village Ledger**.
3. Remain nearby while the loaded terrain index warms up. At default settings the
   initial surface survey takes about a minute; the wider vertical survey takes
   longer. Unloaded terrain is never force-loaded for work.
4. Observe needs, plans, assignments and blockers. Villagers use nearby real
   chests/barrels, carry actual items and walk between workplaces.
5. Housing demand comes from population and usable beds, with two spare beds as
   the target. A housing shortage creates a building plan. Existing spare beds
   may satisfy demand initially; growth also requires real food.

Plains villages near small trees, exposed stone and farms are the most
straightforward first test. Construction uses the local wood family (oak, spruce,
birch, jungle, acacia, dark oak, mangrove or cherry) chosen from nearby trees and
stock. An unsuitable environment can legitimately block development. You may supply
missing materials in a village chest; this uses the same physical economy.

**Chests and barrels inside the working boundary are village-accessible.** Keep
personal supplies outside that boundary if villagers should not consume them.
Where working boundaries overlap, a position belongs to the nearest registered
village center, with a stable UUID tie-break; the same chest is not shared stock.
The village can bootstrap a chest, crafting table and furnace using acquired
materials. Citizens can hand materials to another citizen at close range when
storage is unavailable.

## Work and progression

| Role | Implemented work |
|---|---|
| Lumberjack | Selects small soil-rooted trunks with leaves, walks, breaks logs, collects dropped items, delivers; replants if a matching sapling is carried and the position is valid. Can start bare-handed and obtain an axe. |
| Farmer | Harvests mature wheat, carrots, potatoes and beetroot; replants from actual seeds/produce; delivers harvest. Food provisioning supports vanilla breeding. |
| Miner | Mines reachable exposed stone/ore with real tools, loot and durability. Avoids fluids, falling-block adjacency and undermining its own feet. |
| Animal Keeper | Recognizes bounded fenced pens, feeds compatible pairs under a population cap, and shears white sheep with real shears. |
| Smith | Matches crafting recipes through Minecraft's recipe manager, consumes ingredients, produces tools/materials, and uses a real furnace for iron. |
| Builder | Chooses supported sites, obtains material batches, walks, places blocks progressively, validates construction and registers finished buildings. |

XP thresholds for levels I–V are **0, 64, 256, 768, 2048**. XP is stored per role
when citizens change jobs. Skills and lifetime statistics are separate persistent
records. Mining gates are I: stone/coal, II: copper, III: iron, IV:
gold/redstone/lapis, V: diamond — diamond additionally needs the village
DIAMOND_MINING capability and an iron pickaxe. Iron tool production requires
Smith III, village Tier II and an intact smithy. Missing qualifications are diagnosed.

## Village development (v1.1)

Tiers **I Settlement, II Village, III Town, IV City, V Capital** are derived on
the server from real facts: population, usable beds, completed buildings,
storage, practised profession levels, a physical production record (harvested
wood/food, mined stone, smelted iron), food reserve and technologies. No command
or payment changes a tier. The highest tier reached is historical and is never
lost to a temporary shortage, so tiers do not oscillate; each tier is recorded
once in the chronicle. Activities still need their physical infrastructure: a
former City without a smithy cannot make iron tools. Exact thresholds are in
[V1_1_SPEC.md](docs/V1_1_SPEC.md).

The planner asks **capabilities** (e.g. IRON_TOOLSMITHING, TECHNOLOGY_RESEARCH,
ADVANCED_MINING, DIAMOND_MINING), not tier numbers. **Technologies** are
researched by a qualified worker who carries the full material cost to a real
furnace or crafting table, works there and consumes it in the same tick the
unlock is recorded:

| Branch | Technology | Effect |
|---|---|---|
| Economy | Charcoal Burning (Tier II, Smith II) | Logs → charcoal; iron smelting uses charcoal when no coal is stocked |
| Infrastructure | Timber Framing (Tier III, Builder III) | Three-bed timber house `house_t2_01` |
| Expansion | Deep Prospecting (Tier IV, Miner IV) | Diamond mining for Miner V with an iron pickaxe |

Tier IV also extends the mining band deeper; Tier V grows the working boundary
by 16 blocks.

Villagers retain vanilla locomotion. A work activity temporarily suppresses
competing daytime work; rest, trading and nearby hostile threats release control.
Babies are born through vanilla breeding, never spawned by the simulation.

## Plans, buildings and history

The implemented chain is:

`Need -> scored Goal -> persistent Plan -> assigned Task -> physical Action -> Event`

Food, housing, wood, stone, metal, tools, storage and workforce needs are scored.
Plans expose priority, requirements, resource claims, dependencies, selected site,
progress, attempts and retry times. A blocked task releases its worker and retains
its diagnostic. Retries back off to at most one attempt per 6,000 ticks; resources
and world state are rechecked before acting.

Bundled templates (all palette-aware; the chosen palette is persisted with the
plan and the finished building):

- `house_t1_01`, `house_t1_02`, `house_t1_03`
- `house_t2_01` (requires ADVANCED_CONSTRUCTION)
- `storage_t1`
- `animal_pen_t1`
- `smithy_t1`

Two-part beds/doors consume one item each. Houses include real usable beds.
Construction checks reach, visibility, support, obstruction and previously placed
blocks. Player damage is reported rather than repaired for free. Finished
buildings are periodically inspected. Short road jobs flatten clear dirt/grass
with a real shovel, only on simple connections at the same elevation.

The chronicle records registration, role changes, levels, plans, equipment,
births, deaths and building milestones. History persists without silently
truncating old events. Domain data lives in dimension-specific
`data/livingvillages.dat`; carried items and equipment remain in vanilla entity
data. NBT stores the domain document as UTF-8 bytes, avoiding NBT's 65,535-byte
string limit. Unknown save versions are rejected instead of overwritten.

## Ledger and diagnostics

Tabs: **Overview, Citizens, Resources, Buildings, Plans, Progression, Chronicle**,
plus **Diagnostics** for operators with permission level 2. The layout is
computed from the actual screen size: tabs wrap (or become a compact `< Tab >`
selector on short windows), content is clipped to a scrollable viewport, and
controls never overlap. Progression shows the tier, every requirement as
`[✓]`/`[✗]`, capabilities and technologies per branch with text markers.

Click a row for details, use the mouse wheel to scroll, **Back** to return and
**Refresh** for a fresh server snapshot. Plans include a dependency tree and
structured blocker details. Citizens include skills, equipment, lifetime totals
and recent personal events. Resource rows distinguish storage and loaded cargo.

The view currently shows up to 200 citizens/recent chronicle entries, 150 plans
and 200 buildings. The complete history remains saved. Screenshots/GIFs have not
yet been captured.

Snapshots bound long fields and may shorten lists to fit the network budget;
the Overview tab reports such shortening. Personal events use the most recent
4,096 village events. Full records remain saved and a specific plan can be
inspected by UUID. Refresh preserves the selected tab and cannot reopen an
already closed Ledger. Both client and server must use the same build.

Commands:

```text
/livingvillages village list
/livingvillages village inspect
/livingvillages citizen inspect <entity selector or UUID>
/livingvillages plan inspect <plan UUID>
/livingvillages progression inspect
/livingvillages debug on
/livingvillages debug off
/livingvillages debug rename <name>
```

`progression inspect` is read-only (why the village is not the next tier,
capability and technology status). `debug rename` changes only the display name
and is recorded in history.

The debug toggle enables/disables an operator action-bar summary. Commands and
Ledger share the same diagnostic tree. No command creates free resources or
instantly completes a building.

Server configuration is generated at
`<world>/serverconfig/livingvillages-server.toml`. It controls planner interval,
working radius, scan budget, stuck timeout, retry threshold, active-plan limit,
animal cap, worker-step budget and verbose logging.

## Build and verification

With Java 21 on PATH:

```text
gradlew test
gradlew build
gradlew runGameTestServer
gradlew runClient
```

On this Windows workspace, `scripts/gradle.ps1` also discovers the locally
downloaded JDK in `.tools` and keeps Gradle caches inside the project:

```powershell
.\scripts\gradle.ps1 test
.\scripts\gradle.ps1 build
.\scripts\gradle.ps1 runGameTestServer
```

JDK/cache files are ignored and are not required in source control. GameTests use
the separate ignored `run-gametest` directory. `scripts/MakeTestTemplate.java`
reproducibly generates the empty NBT test structure.

See [acceptance results](docs/ACCEPTANCE_RESULTS.md) and
[v1.1 acceptance](docs/V1_1_ACCEPTANCE.md) for actual automated coverage and
manual checks still outstanding. The suite contains **36 JUnit tests** and **40
GameTests**, including the multi-village soak and natural Tier II progression.
A separate two-process restart test is run with:

```powershell
.\scripts\acceptance-restart.ps1
```

or on macOS/Linux:

```sh
./scripts/acceptance-restart.sh
```

It retains its world and both process logs in a unique ignored
`run-restart-<timestamp>` directory. It uses actual SavedData, region and entity
files, verifies cargo against the remaining building manifest, and resumes the
same construction. Test-only world setup and chunk tickets are confined to
GameTests and are disabled in production.

## Current limits

- No offline terrain simulation, chunk tickets, tunnels, ladders or temporary
  scaffolding. Forestry conservatively accepts vertical trunks up to five logs;
  inaccessible/taller trees remain unharvested. Saplings are not generated.
- The index covers the boundary and a vertical band of eight blocks above/below
  the bell. Mining is even more conservative about depth and exposure.
- Vanilla blocks do not retain a universal “placed by player” ownership flag.
  Soil/leaves heuristics, natural foundation checks, collision checks, block
  entity exclusion and registered-building protection reduce interference but
  cannot perfectly distinguish player-built natural-looking terrain.
- Templates are intentionally small, with flat roofs and strict flat-site
  requirements; palettes change materials, not shapes. No terrain carving, multilevel road routing, building
  repair automation or animal herding is implemented.
- Crafting supports the bundled production set, using live server recipes for
  validation. Arbitrary modded recipes and protection/claim-mod hooks have not
  been integrated. Furnaces currently handle the iron production chain.
- Chronicle/plan storage is retained in one SavedData document per dimension.
  Active work uses separate runtime indexes; completed records remain permanent
  and grow with meaningful activity. Multi-year archival storage, older-history
  pagination and larger-settlement profiling remain follow-up work.
- A graphical Ledger playthrough, full process restart with a real survival
  world, repeated chunk unload/reload and long-running balance tests still need
  manual validation. Automated two-JVM restart, NBT/cargo checks and an actual
  unload/reload soak are covered; they do not replace those manual sessions.

- Production before the v1.1 migration is not reconstructed; migrated villages
  re-earn tiers from new work. Diamond ore is mined only when exposed and
  reachable inside the indexed band, which is rare near villages.
- The responsive Ledger is verified by geometry tests only; the graphical
  procedure M4 is MANUAL_REQUIRED.

## Roadmap

[v1.1 specification](docs/V1_1_SPEC.md) ·
[Execution plan](docs/EXECUTION_PLAN.md) ·
[Architecture review](docs/ARCHITECTURE_REVIEW.md) ·
[v1.1 backlog](docs/V1_1_BACKLOG.md)

The next work prioritizes release verification, logistics, archival persistence
and terrain/navigation coverage before military or government systems.
