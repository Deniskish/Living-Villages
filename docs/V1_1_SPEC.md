# Living Villages v1.1 — Progression, Capabilities, Technologies and Palettes

Status: implemented in `1.1.0-dev` (development milestone, not stable).
Scope: peaceful development only. Soldiers, archers, raids, war, diplomacy,
leaders/kings, kingdoms, colonies, walls and Nether expeditions are **not** part
of v1.1. All v1.0 game rules (`GAME_RULES.md`) remain in force.

Code map (pure logic is Minecraft-free and unit-tested):

| Concern | Pure logic | World adapter |
|---|---|---|
| Tiers, requirements, snapshot | `progression.ProgressionPolicy`, `Requirement`, `VillageFacts`, `VillageProgressionSnapshot` | `world.ProgressionService` |
| Capabilities | `progression.VillageCapability`, `CapabilityPolicy`, `CapabilityStatus` | `world.Capabilities` |
| Technologies | `progression.Technology`, `Technologies`, `TechnologyPolicy`, `TechnologyStatus` | `task.ResearchJob`, `ai.VillagePlanner` |
| Diagnostics | `progression.ProgressionDiagnostics` → `domain.Diagnostics.Node` | Ledger, commands, logs |
| Palettes | `domain.Palettes` | `building.Blueprints` |
| Production record | `progression.ProductionCategory` | `world.ProgressionService.recordProduction` |
| Ledger geometry | `client.LedgerLayout`, `client.LedgerRouting` | `client.LedgerScreen` |

## 1. Village tiers

| # | Identifier | Title |
|---|---|---|
| I | `SETTLEMENT` | Settlement |
| II | `VILLAGE` | Village |
| III | `TOWN` | Town |
| IV | `CITY` | City |
| V | `CAPITAL` | Capital |

A tier cannot be bought, granted by a command or set from the client. It is
derived on the server from measured facts.

### 1.1 Facts

`VillageFacts` is built on the strategic tick from validated state:

- **population** — living registered citizens;
- **beds** — usable bed heads (two air blocks above) found by the incremental
  terrain index inside the boundary;
- **buildings** — completed village buildings by category whose last inspection
  is not `DAMAGED` (`HOUSING`, `STORAGE`, `SMITHY`, `ANIMALS`);
- **storage containers** — distinct live chest/barrel references;
- **profession levels** — per role, the best level among living citizens who have
  actually practised that role (XP > 0); per citizen, their best practised level;
- **production record** — items physically produced by village work (§5);
- **food reserve** — stocked food units (bread = 4, carrot/potato/beetroot = 1;
  wheat is an ingredient);
- **technologies** — permanent unlock records;
- **workstations** — whether a furnace / crafting table is indexed.

### 1.2 Exact requirements

Requirements are **cumulative**: tier N includes every requirement of lower
tiers; for the same key the higher threshold applies. Thresholds never decrease
from one tier to the next (unit-tested), so qualification is monotonic.

| Tier | Requirements introduced or raised |
|---|---|
| I Settlement | Population ≥ 1; usable beds ≥ max(1, population) |
| II Village | Population ≥ 6; beds ≥ max(6, population); ≥ 1 completed house; ≥ 1 storage container; Lumberjack I practised; Farmer I practised; wood harvested ≥ 16; food harvested ≥ 16 |
| III Town | Population ≥ 10; beds ≥ max(10, population); ≥ 3 completed houses; ≥ 1 storage building; ≥ 1 smithy; Miner ≥ II; Smith ≥ II; wood ≥ 64; food ≥ 64; iron smelted ≥ 8; food reserve ≥ 2 units per citizen |
| IV City | Population ≥ 16; beds ≥ max(16, population); ≥ 5 houses; ≥ 4 completed building types; ≥ 3 citizens at level III+; Miner ≥ III; Smith ≥ III; iron ≥ 32; stone mined ≥ 256; food reserve ≥ 3 per citizen; ≥ 1 technology |
| V Capital | Population ≥ 24; beds ≥ max(24, population); ≥ 8 houses; ≥ 4 citizens at level IV+; Miner ≥ IV; Smith ≥ IV; Builder ≥ IV; iron ≥ 128; food reserve ≥ 4 per citizen; all 3 technologies |

Balance rationale (from the v1.0 economy): role XP thresholds are 0/64/256/768/2048
(miner 2 XP per block, smith 4 per craft/ingot, builder 1 per block + 16 per
building); iron needs a Miner III or supplied raw iron; houses add 1–3 beds.
Tier II is therefore reachable by an ordinary village that builds one house and
works its trees and fields; Tier V needs a long-running, multi-profession economy.

### 1.3 Requirement records

Each requirement is a machine-readable `Requirement(type, subject, required,
actual, fulfilled, message)`, e.g. `POPULATION "" 10 8 false "Population 8 / 10"`.
Types: `POPULATION, HOUSING, BUILDING, BUILDING_TYPES, STORAGE, PROFESSION,
SKILLED_WORKERS, PRODUCTION, FOOD_RESERVE, TECHNOLOGIES` (tiers) and `TIER,
TECHNOLOGY, WORKSTATION, INFRASTRUCTURE, RESOURCE` (capabilities/technologies).
The message is only the rendering of the structured values.

### 1.4 Snapshot

`VillageProgressionSnapshot` contains `currentTier` (qualified now, or null if
Tier I is unmet), `highestUnlockedTier`, `nextTier`, `requirements` (cumulative
for `nextTier`, so lower-tier regressions are visible), `fulfilledRequirements`,
`missingRequirements`, `unlockedCapabilities`, `lockedCapabilities`,
`technologies`, `explanation` and `evaluatedAt`.

### 1.5 Evaluation and level-up

- Runs inside the strategic planner tick (default every 200 game ticks), only
  when the village center is loaded and the terrain index is ready. It reads the
  stock the planner already gathered; no extra world scan.
- `highestUnlockedTier` is **historical**: it never decreases, so temporary
  shortages cannot produce `II → III → II` oscillation.
- Each newly reached tier writes `tierHistory[tier] = tick` once and emits one
  `VILLAGE_TIER_REACHED` Chronicle event with parameters `tier`, `previous`,
  `capabilities`. A tier already present in `tierHistory` never emits again
  (protects against restored or inconsistent saves). A village that qualifies
  for several tiers at once records each in order.
- Physical prerequisites are still enforced per activity (§2). A historical City
  whose smithy is destroyed keeps its tier, but iron toolsmithing is unavailable.

## 2. Capabilities

Planner and executors ask a capability, never a tier number. A capability is
**unlocked** by tier (+ technology) and **available** when its physical
infrastructure also exists now.

| Capability | Tier | Technology | Infrastructure | Consulted by |
|---|---|---|---|---|
| `BASIC_FORESTRY` | I | — | — | planner (GATHER_WOOD) |
| `BASIC_FARMING` | I | — | — | planner (FARM) |
| `BASIC_MINING` | I | — | — | planner (MINE) |
| `BASIC_CONSTRUCTION` | I | — | — | planner (houses, storage, smithy, pen) |
| `ANIMAL_HUSBANDRY` | I | — | — | planner (BREED_ANIMALS) |
| `IRON_SMELTING` | I | — | furnace (planned as dependency) | planner, `SmeltingJob` |
| `IRON_TOOLSMITHING` | II | — | intact smithy (planned as dependency) | `ProductionJob`, planner |
| `TECHNOLOGY_RESEARCH` | II | — | — | planner, `ResearchJob` |
| `CHARCOAL_BURNING` | II | `economy.charcoal_burning` | furnace | planner fuel choice, `SmeltingJob` |
| `ADVANCED_CONSTRUCTION` | III | `infrastructure.timber_framing` | — | planner (house variants) |
| `ADVANCED_MINING` | IV | — | — | terrain index depth, `HarvestJob` |
| `DIAMOND_MINING` | IV | `expansion.deep_prospecting` | — (worker needs Miner V + iron pickaxe) | planner, `HarvestJob` |
| `EXPANDED_TERRITORY` | V | — | — | applied once at Tier V: radius +16 (cap 96) |

All v1.0 behaviour maps to Tier I capabilities, except iron-tool crafting, which
now additionally requires Tier II and an intact smithy (still Smith III). Shears
are not iron tools for this rule (beds need wool).

`ADVANCED_MINING` extends the indexed band from 8 to 16 blocks below the bell
and the mining limit from 6 to 14 blocks below it. Exposure, safety, tool and
level rules are unchanged; there are still no tunnels or shafts.

Blockers: a locked capability blocks with `CAPABILITY_LOCKED`, missing
infrastructure with `INFRASTRUCTURE_MISSING`, e.g.
`ADVANCED_MINING locked: Village tier IV — City required (current III — Town)`,
`IRON_TOOLSMITHING unavailable: Smithy is missing`.

## 3. Technologies

A technology has a stable id, branch, prerequisites, required tier, required
role level, physical cost, workstation, work units (60 ticks each), persistent
state and an effect expressed as capabilities. Costs use palette tokens
(`WOOD_LOG`, `WOOD_PLANKS`) resolved once when the plan is created.

| Id | Branch | Tier | Prerequisites | Worker | Cost | Workstation | Work | Effect |
|---|---|---|---|---|---|---|---|---|
| `economy.charcoal_burning` | ECONOMY | II | — | Smith II | 16 `WOOD_LOG`, 8 cobblestone | furnace | 6 units | `CHARCOAL_BURNING`: charcoal from logs; iron smelting uses charcoal when no coal is stocked |
| `infrastructure.timber_framing` | INFRASTRUCTURE | III | — | Builder III | 24 `WOOD_LOG`, 32 `WOOD_PLANKS` | crafting table | 8 units | `ADVANCED_CONSTRUCTION`: three-bed `house_t2_01` alternates with tier-one houses |
| `expansion.deep_prospecting` | EXPANSION | IV | charcoal burning | Miner IV | 8 iron ingots, 64 cobblestone, 16 `WOOD_PLANKS` | crafting table | 10 units | `DIAMOND_MINING` |

Pipeline:

`DEVELOPMENT need → Goal → RESEARCH:<id> plan → reservation of real stock →
acquisition dependencies (GATHER_WOOD:<log>, MINE, CRAFT, PLACE:<workstation>)
→ qualified worker (task minimum level) → fetch full cost → walk to a real
workstation → timed work units → consume the full cost and record the unlock in
the same server tick → TECHNOLOGY_UNLOCKED event`.

- At most one research plan is active; the deterministic order is registry order.
- A missing workstation is not a start gate; the plan depends on placing one.
- The cost stays in the researcher's vanilla inventory during work. Restart keeps
  it there (entity data) and keeps the unit counter (plan state). Worker death
  drops it and blocks the task (`WORKER_UNAVAILABLE`); a new worker must fetch a
  full cost again. A removed chest or unloaded chunk blocks with the usual
  reasons. Nothing is unlocked unless the full cost is consumed.
- An already-unlocked technology is never consumed twice; a stray plan returns
  its carried materials to storage.
- Unlock records (`TechnologyRecord`: id, tick, plan, researcher) are permanent.

The `TechnologyBranch` enum and capability enum are the extension points for a
later MILITARY branch; no schema change is needed to add constants.

## 4. Building palettes and variants

Wood families (Minecraft 1.21.1): `oak, spruce, birch, jungle, acacia, dark_oak,
mangrove, cherry`. Pale oak (1.21.4) and nether stems are excluded.

Tokens: `WOOD_PLANKS, WOOD_LOG, WOOD_STAIRS, WOOD_SLAB, WOOD_DOOR, WOOD_FENCE,
WOOD_FENCE_GATE` → `minecraft:<wood>_<suffix>`.

Blueprint symbols (backward compatible with v1.0 files): `P` planks, `L` log,
`D` door, `|` fence, `G` fence gate, `S` bottom slab are palette tokens; `#`
cobblestone, `B` white bed, `C` chest, `T` crafting table, `F` furnace are fixed.
Optional JSON field `capability` gates a template. The entrance is derived from
the first door/gate cell. v1.0 templates resolve with the oak palette to exactly
their original blocks and cell order.

Templates: `house_t1_01`, `house_t1_02`, `house_t1_03`, `house_t2_01` (7×6,
three beds, log frame, slab roof, `ADVANCED_CONSTRUCTION`), `storage_t1`,
`smithy_t1`, `animal_pen_t1`. All use palettes.

Selection (`Palettes.select`, deterministic, once per strategic tick):
score = 2 × indexed log blocks of that family inside the boundary + stocked logs
+ 0.25 × stocked planks + 0.5 biome tie-break. The highest score wins; ties use
the family order above; with no wood at all the biome hint, otherwise oak. The
inputs are the existing bounded terrain index and stock — no extra block scan.
Scores are stored in `Village.paletteScores` for diagnostics.

Generic wooden recipes (sticks, chests, crafting table, bed, wooden tools) use the
most stocked planks, else the most stocked logs, else the village palette.
Family-specific items (planks, door, fence, gate, slab, stairs) use their own
family. A specific log requirement creates `GATHER_WOOD:<log>`, which only fells
that family.

## 5. Production record

`Village.production` (item id → amount) records only goods created by a world
action: final block loot from NeoForge `BlockDropsEvent` where the breaker is a
living registered citizen (lowest priority, cancelled events ignored), furnace
output collected by a worker, crafted output, collected sheared wool. Transfers,
deliveries and player deposits never count. Categories: FOOD (wheat, carrot,
potato, beetroot), WOOD (`*_log`), STONE (cobblestone, cobbled deepslate), IRON
(iron ingot), CHARCOAL, DIAMOND. Each category emits `PRODUCTION_MILESTONE` at
1, 64, 512 and 4,096.

## 6. Persistence and migration

Schema `StateCodec.VERSION = 2`. New persisted fields: `Village.tier`,
`tierHistory`, `production`, `technologies`, `palette`, `paletteScores`;
`Plan.palette`, `Plan.technology`; `Building.palette`; `Task.minimumLevel`.

Migration v1 → v2 is explicit (`StateCodec.migrateV1`): identities, XP, skills,
plans, tasks, dependencies, blockers, history, buildings and storage are kept;
in-flight construction, CRAFT plans and completed buildings receive `oak`
(every v1.0 template/recipe was oak, so a half-built house continues unchanged);
tier starts at SETTLEMENT with its milestone at the village creation tick; the
production record starts empty (v1.0 has no per-item record) and tiers are
re-derived from new production. Migration notes are logged as
`event=save_migrated`. The stored version is read from the raw document; a
version above 2 or below 1 is refused and the file is never overwritten.

Network: Ledger protocol version `3`; mismatched client/server builds are refused.

## 7. Diagnostics

One structure (`Requirement` → `Diagnostics.Node`) answers:

- *Why is the village not Tier IV?* — `ProgressionDiagnostics.tier(snapshot)`;
- *Why can't this job run?* — `ProgressionDiagnostics.capability(status)` and task
  blockers `CAPABILITY_LOCKED` / `INFRASTRUCTURE_MISSING`;
- *Why can't this technology start?* — `ProgressionDiagnostics.technology(status)`
  and research plan blockers (`REQUIREMENT_UNMET`, `MISSING_RESOURCE`, ...).

Available in the Ledger (Progression tab; Diagnostics tab first row for
operators), `/livingvillages progression inspect`, structured logs
(`event=progression_evaluated` with `debugLogging=true`; tier, technology,
milestone and territory events always) and tests.

## 8. Commands

Read-only: `/livingvillages progression inspect`; `village inspect` additionally
prints tier, technologies and palette. Operator display-only:
`/livingvillages debug rename <name>` (recorded as `VILLAGE_RENAMED`, used for
long-name Ledger acceptance). There is no command that sets a tier, unlocks a
technology or creates materials.

## 9. Ledger

Tabs: Overview, Citizens, Resources, Buildings, Plans, **Progression**,
Chronicle, Diagnostics (operators). Progression shows the tier, highest unlocked
and current qualification, next tier, every requirement as `[✓]`/`[✗]` text,
capabilities as `[✓]` available / `[!]` unlocked but infrastructure missing /
`[✗]` locked, and technologies per branch as `[✓]`/`[~]`/`[ ]`/`[✗]`. Colour only
reinforces the text. Rows open details like every other tab.

Layout (`LedgerLayout`, GUI-scaled pixels): header (title clipped with an
ellipsis, Refresh at the right), tabs wrapped onto rows using measured label
widths; when wrapping would leave fewer than three content rows a compact
`[<] Tab (n/N) [>]` selector is used; content viewport with whole rows,
scissor clipping, hover highlight and scrollbar; footer with Back, status and
Close. Clicks select rows only inside the viewport; the wheel scrolls only
there. Below 160×110 scaled pixels only a message and Close are shown. Refresh
keeps the tab and re-selects the same row by stable key; a refresh answer never
opens a closed Ledger.

## 10. Performance

Progression and palette work run on the existing strategic tick and reuse its
stock and bounded indexes. Capability checks are O(citizens + buildings) and
done once per executor tick. The drop listener returns immediately unless the
breaker is a villager. `ADVANCED_MINING` adds eight index layers per village at
the same per-step scan budget (a full survey takes longer; cost per tick is
unchanged).

## 11. Known limits

- Production before the v1.1 migration is not reconstructed.
- Diamond ore is mined only when exposed, reachable and inside the indexed band;
  at typical village heights that is rare. No tunnels/shafts exist yet.
- Tier IV/V thresholds are untested in long survival play (manual M3/M4).
- One research plan at a time; three technologies in total.
- A power loss between Minecraft's entity save and the SavedData save can still
  desynchronise carried materials (unchanged v1.0 limitation).
