# Living Villages — Acceptance Tests

These are gameplay acceptance scenarios for v1.0. They supplement unit tests and build checks.

## A1 — Village registration

### Setup
- Start a fresh world.
- Find or generate a normal vanilla village.
- Enter the village.

### Expected
- The village is registered exactly once.
- It receives a stable VillageId.
- Existing villagers become registered citizens.
- Reloading the world preserves the same village and citizen identities.
- No duplicate VillageData is created after re-entering the area.

## A2 — Persistent citizen identity

### Setup
- Inspect a registered villager.
- Note their name, role, XP, level and UUID.
- Save and reload the world.

### Expected
- Name remains unchanged.
- Role remains unchanged.
- XP and level remain unchanged.
- Citizen is still associated with the same village.
- Missing/unloaded entity references do not crash the game.

## A3 — Lumberjack vertical slice

### Setup
- Ensure village needs wood.
- Ensure an accessible vanilla tree exists in configured radius.
- Ensure a Lumberjack is available.

### Expected
- Village creates or activates wood-gathering work.
- Lumberjack walks to a reachable tree.
- Lumberjack breaks actual tree blocks.
- Drops are collected.
- Resources are delivered to village storage or a valid consumer.
- Lumberjack receives XP.
- Chronicle does not spam one entry per block.

## A4 — Lumberjack blocked by unreachable trees

### Setup
- Create a wood shortage.
- Prevent all known candidate trees from being reachable.

### Expected
- The gather task does not retry every tick forever.
- Retry/backoff occurs.
- Task becomes BLOCKED after configured thresholds.
- Blocker explains no reachable target/tree.
- Village Ledger and debug command show the reason.
- Parent plan reflects dependency blockage.

## A5 — Missing resources block construction

### Setup
- Force or naturally create housing demand.
- Allow the village to create a BuildHouse plan.
- Remove required wood from village storage.

### Expected
- BuildHouse does not create blocks for free.
- Plan enters WAITING or BLOCKED as appropriate.
- Diagnostics show required, reserved and available resource values.
- Dependency chain points to resource acquisition.

## A6 — Blocked dependency chain

### Setup
- Use A5.
- Also make the lumberjack unable to reach any tree.

### Expected
Diagnostics can explain:

```text
BuildHouse BLOCKED
└── Missing oak planks
    └── GatherWood BLOCKED
        └── No reachable tree
```

The same underlying diagnostic structure is usable by UI and commands.

## A7 — Recovery after blocker is removed

### Setup
- Start from A6.
- Restore a reachable tree or add legitimate physical wood resources.

### Expected
- Blocker is re-evaluated.
- Task/plan recovers without manual data editing.
- Resources are acquired.
- Construction can continue.

## A8 — Building site rejection

### Setup
Provide candidate terrain containing:
- deep water;
- steep slope;
- collision with existing building;
- inaccessible entrance;
- one valid site.

### Expected
- Invalid sites are rejected.
- A valid site is selected.
- Diagnostics retain rejection counts/reasons.
- No house is placed in mid-air, deep water or overlapping existing registered buildings.

## A9 — Progressive construction

### Setup
- Give a valid BuildHouse plan all required resources.
- Ensure a qualified Builder is available.

### Expected
- Building does not appear instantly.
- Builder obtains materials.
- Builder moves to site.
- Blueprint blocks are placed progressively.
- Material consumption corresponds to placed construction.
- Completion registers a BuildingData entry.
- Chronicle records building completion.

## A10 — Player obstructs construction

### Setup
- Start active construction.
- Place blocks that obstruct part of the blueprint or entrance.

### Expected
- System detects world mismatch.
- Builder does not loop infinitely.
- Task either adapts safely or becomes BLOCKED.
- Diagnostic reason identifies site obstruction/world change.

## A11 — Storage removed

### Setup
- Register/use village storage.
- Remove the chest/barrel used by the village.

### Expected
- Storage reference is invalidated.
- Cached resource counts are corrected.
- No item duplication occurs.
- Plans dependent on that storage become waiting/blocked or find alternate storage.
- No crash occurs.

## A12 — Save/reload mid-plan

### Setup
- Begin a plan such as BuildHouse.
- Save while the plan is in progress.
- Reload.

### Expected
- Plan identity is preserved.
- Dependencies remain valid or are safely recovered.
- Progress is preserved where safe.
- Builder/task can resume or report a clear recovery blocker.
- No duplicate building plan is created solely because of reload.

## A13 — Citizen dies during task

### Setup
- Assign a citizen to a task.
- Kill that citizen while the task is active.

### Expected
- Task does not retain an invalid active worker forever.
- Worker loss is diagnosed.
- Village may reassign if another qualified worker exists.
- Citizen death is recorded in history.
- Save remains valid.

## A14 — Profession progression

### Setup
- Allow one worker to repeatedly complete legitimate profession actions.

### Expected
- XP increases only for meaningful successful work.
- Level thresholds are deterministic.
- Level-up is persisted.
- Chronicle records significant level-up events.
- Reload does not reset progression.

## A15 — Miner capability gates

### Setup
Test miners at profession levels I–IV.

### Expected
- Level I targets only allowed low-tier materials such as stone/coal.
- Level II can target copper.
- Level III can target iron.
- Level IV can target gold/redstone/lapis.
- Miner does not magically create ore drops.
- Unsupported target selection produces a clear reason rather than silent behavior.

## A16 — Farmer loop

### Setup
- Provide mature supported crops and village demand for food.

### Expected
- Farmer walks to farmland.
- Mature crops are harvested.
- Crops are replanted where possible.
- Harvest is transported.
- XP increases.
- Player-modified or unavailable field state is revalidated.

## A17 — Animal population cap

### Setup
- Provide supported animals, breeding food and an Animal Keeper.

### Expected
- Breeding consumes valid resources.
- Population does not grow without bound.
- Configured village animal caps are respected.
- Missing food produces a diagnosable waiting/blocker state.

## A18 — Smith tool replacement

### Setup
- Cause a worker to require a replacement tool.
- Provide or withhold required materials.

### Expected with resources
- Smith creates the required legitimate tool.
- Ingredients are consumed.
- Tool becomes available to worker.
- Relevant XP/event is recorded.

### Expected without resources
- Tool-production plan waits/blocks.
- Missing materials are visible in diagnostics.

## A19 — Village Ledger

### Setup
- Hold a vanilla Book.
- Right-click a registered village bell.

### Expected
Ledger opens and can display:
- Overview
- Citizens
- Resources
- Buildings
- Plans
- Chronicle

Admin/debug users can inspect Diagnostics.

The client does not become authoritative over village state.

## A20 — Build and automated checks

Before release:

```bash
./gradlew test
./gradlew build
```

Expected:
- both succeed;
- deterministic unit tests cover core state/utility/resource logic;
- any known limitations are written in README rather than hidden.
