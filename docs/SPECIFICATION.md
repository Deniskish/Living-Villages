# Living Villages — Product Specification

## 1. Vision

Living Villages transforms vanilla Minecraft villages into autonomous settlements.

Desired player experience:
1. discover an ordinary village;
2. villagers become persistent individuals;
3. they acquire useful roles;
4. they gather actual resources;
5. they create plans based on village needs;
6. construction visibly changes the settlement;
7. villagers become more skilled;
8. the population grows when conditions permit;
9. village history accumulates;
10. the player can inspect what the village is planning and why.

v1.0 focuses on peaceful autonomous development.

Future versions add civilizations, politics, armies, kings and warfare.

## 2. Village lifecycle

When the mod detects a valid vanilla village:
1. create stable `VillageId`;
2. determine center;
3. determine initial working boundary;
4. register relevant villagers;
5. discover relevant POIs/buildings/storage;
6. initialize village needs;
7. initialize chronicle;
8. begin low-frequency strategic planning.

A vanilla village should not be destroyed or replaced.

The mod augments it.

## 3. Village model

Each village conceptually contains:

```text
Village
├── Identity
├── Center / Boundary
├── Citizens
├── Population
├── Buildings
├── Storage
├── Resources
├── Needs
├── Goals
├── Plans
├── Tasks
├── Profession capability
├── Statistics
├── Chronicle
└── Diagnostics
```

## 4. Citizens

Every registered citizen has persistent data.

Required fields:

```text
CitizenData
├── UUID
├── display name
├── village ID
├── role
├── role level
├── role XP
├── skills
├── equipment snapshot/reference
├── home reference
├── current task
├── activity state
├── lifetime statistics
├── created/registered time
└── personal event history
```

Names should remain stable.

Do not rename citizens after reload.

## 5. v1.0 roles

Required autonomous roles:
- Lumberjack
- Miner
- Farmer
- Animal Keeper
- Builder
- Smith

Role architecture must allow future:
- Soldier
- Archer
- Scout
- Captain
- Commander
- Leader
- King

## 6. Role levels

All working professions support:
- I
- II
- III
- IV
- V

XP comes from meaningful successful actions.

Examples:

Lumberjack:
- logs harvested;
- saplings planted;
- deliveries completed.

Miner:
- blocks mined;
- useful ore obtained;
- mining trips completed.

Builder:
- valid blueprint blocks placed;
- buildings completed.

Farmer:
- crops harvested;
- fields maintained;
- food deliveries.

Smith:
- useful tools/items crafted.

Do not grant significant XP for repeatedly starting/cancelling tasks.

## 7. Skills

Role level and skills are separate concepts.

v1.0 should support a generic skill architecture.

Possible initial skills:
- MINING
- BUILDING
- FARMING
- WOODCUTTING
- SMITHING
- ENDURANCE

Future:
- MELEE
- DEFENSE
- DISCIPLINE
- ARCHERY
- ACCURACY
- DRAW_SPEED

Avoid dozens of meaningless stats.

## 8. Village needs

Village AI periodically evaluates needs.

Required v1.0 needs:
- FOOD
- HOUSING
- WOOD
- STONE
- METAL
- TOOLS
- STORAGE
- WORKFORCE

Each need produces a normalized score: `0.0 ... 1.0`.

Example:

```text
FOOD      0.31
HOUSING   0.88
WOOD      0.76
TOOLS     0.44
```

The planner chooses goals based on:
- urgency;
- feasibility;
- dependencies;
- current plans;
- available workforce;
- cooldowns.

Do not create duplicate identical plans indefinitely.

## 9. Goal/plan/task/action hierarchy

Required hierarchy:

```text
Need
 ↓
Goal
 ↓
Plan
 ↓
Tasks
 ↓
Citizen Actions
 ↓
Events
```

Example:

```text
Need:
HOUSING = 0.91

Goal:
IncreaseHousing

Plan:
BuildHouse #42

Tasks:
- obtain wood
- obtain stone
- reserve building site
- assign builder
- construct house

Actions:
- Erik cuts oak
- Erik carries logs
- Hans collects planks
- Hans places block
```

## 10. Plan model

Each plan should contain enough information to inspect and resume it.

Conceptual fields:

```text
VillagePlan
├── ID
├── village ID
├── type
├── state
├── priority
├── creation time
├── last progress time
├── retry metadata
├── dependencies
├── assigned citizen IDs
├── resource requirements
├── reservations
├── blockers
├── progress
└── diagnostic context
```

## 11. Plan states

Required:
- PLANNED
- WAITING
- ASSIGNED
- IN_PROGRESS
- BLOCKED
- COMPLETED
- FAILED
- CANCELLED

Valid transitions should be controlled by a state machine, not arbitrary field mutation.

## 12. Blockers

Required blocker categories include:
- MISSING_RESOURCE
- MISSING_TOOL
- NO_QUALIFIED_WORKER
- WORKER_UNAVAILABLE
- NO_VALID_SITE
- PATH_UNREACHABLE
- CHUNK_UNAVAILABLE
- BUILD_SITE_OBSTRUCTED
- DEPENDENCY_BLOCKED
- TARGET_MISSING
- STORAGE_FULL
- STORAGE_MISSING
- RETRY_LIMIT_REACHED
- WORLD_CHANGED
- UNKNOWN

Blocker includes, where applicable:
- type
- message
- createdAt
- lastCheckedAt
- relatedPlanId
- relatedTaskId
- relatedCitizenId
- coordinates
- requiredValue
- actualValue

## 13. Stuck detection

A task must update `lastProgressTick` when meaningful progress occurs.

Examples of progress:
- successfully reached destination;
- acquired required item;
- mined target block;
- delivered resource;
- placed required block.

If no progress occurs beyond configured thresholds:
1. diagnose;
2. retry with bounded backoff if reasonable;
3. set blocker;
4. escalate after repeated failure.

Never retry every tick forever.

## 14. Dependency graph

Plans/tasks may depend on one another.

Example:

```text
BuildHouse
├── NeedWood
│   └── GatherWood
├── NeedStone
│   └── MineStone
└── NeedBuilder
```

Diagnostics must be able to traverse dependency chains.

Example user-facing explanation:

```text
House construction is blocked
└── missing 31 oak planks
    └── wood gathering is blocked
        └── no reachable oak tree within search radius
```

## 15. Resources

v1.0 tracks actual physical resources.

Sources may include:
- citizen inventories;
- designated village containers;
- produced/harvested item entities where safe;
- known storage buildings.

The village can maintain a cached index for planning.

The cache must be periodically validated.

Do not treat cached resource counts as permanent truth.

## 16. Storage

Use vanilla containers.

Supported storage candidates:
- chest;
- barrel;
- suitable containers supported by current Minecraft version.

Village storage records contain:
- container position
- dimension
- capacity estimate
- last validation
- resource categories

If no storage exists, citizens may temporarily carry resources until the village can create/use storage.

Storage shortage itself can create a plan.

## 17. Lumberjack

Required vertical slice.

Behavior:
1. receive gather-wood task;
2. search bounded area for valid tree;
3. verify reachability;
4. travel;
5. harvest logs;
6. collect drops;
7. optionally replant sapling when appropriate;
8. deliver logs to village storage or dependent worker;
9. record progress;
10. gain XP.

Tree harvesting must not intentionally destroy arbitrary player buildings made of logs.

Use tree heuristics.

## 18. Miner

Behavior:
1. use allowed mining zone/search logic;
2. select resource allowed by profession level;
3. verify path/safety;
4. travel;
5. break actual blocks;
6. collect drops;
7. return resources.

Initial progression:

```text
Miner I:
stone, coal

Miner II:
copper

Miner III:
iron

Miner IV:
gold, redstone, lapis

Miner V:
architecture ready for diamond progression
```

v1.0 may gate dangerous/deep mining conservatively.

Do not create ore drops without breaking corresponding blocks.

## 19. Farmer

Behavior:
- locate village farmland;
- harvest mature vanilla crops;
- replant;
- transport food;
- respect existing player crops where practical;
- gain XP.

Supported initial crops may include:
- wheat;
- carrots;
- potatoes;
- beetroot.

## 20. Animal Keeper

Behavior:
- detect village-controlled animal areas;
- feed/breed animals when resources and population limits permit;
- avoid unbounded entity growth;
- maintain configured caps;
- move/feed animals only when practical.

Initial supported animals:
- cows;
- sheep;
- pigs;
- chickens.

v1.0 does not require cavalry.

## 21. Smith

Smith performs real vanilla crafting/smelting-oriented production where practical.

Initial responsibilities:
- wooden/stone/iron tools;
- replacement tools for workers;
- useful village equipment.

Production plan must account for:
- ingredients;
- workstation availability;
- worker skill;
- tool requirements.

No invisible free item creation.

## 22. Builder

Builder executes blueprint construction.

Required sequence:
1. plan exists;
2. blueprint selected;
3. site selected;
4. required resources calculated;
5. resources available/reserved;
6. builder assigned;
7. builder transports materials;
8. blocks placed progressively;
9. blueprint state checked;
10. completion event emitted.

Builder should not replace arbitrary protected/non-replaceable blocks without reason.

## 23. Building blueprints

Use structure/template data stored with the mod.

Initial required blueprints:
- `house_t1_01`
- `house_t1_02`
- `house_t1_03`
- `storage_t1`
- `animal_pen_t1`
- `smithy_t1`

Prefer multiple house variants so villages do not look identical.

Blueprint metadata includes:
- ID
- category
- size
- entrance position
- required village capability
- resource manifest
- placement constraints

## 24. Building site planner

The planner searches within bounded candidate regions around the village.

Each site receives a score.

Consider:
- flatness
- water coverage
- terrain support
- distance to village center
- distance to roads
- distance to existing buildings
- entrance accessibility
- obstruction
- slope
- height variance
- protected/important blocks

Hard reject obviously invalid sites.

Examples:
- deep water;
- mid-air;
- intersecting existing building;
- extreme slope;
- no navigable entrance;
- outside configured boundary.

Diagnostics must retain rejection statistics.

Example:

```text
Sites evaluated: 231
Rejected:
- slope: 89
- water: 42
- collision: 61
- unreachable: 31
- boundary: 8
```

## 25. Roads

v1.0 should support simple road connection after a building is completed.

Goal:
Connect building entrance to nearest reasonable existing path/network.

Use vanilla path blocks where appropriate.

Road building must also respect terrain and avoid destructive carving.

If road generation is unreliable, building functionality takes priority, but architecture must support road planning.

## 26. Housing

Housing demand derives from:
- population;
- usable beds;
- reserved beds;
- expected population pressure.

A house should contribute real usable beds.

Population growth must not simply spawn arbitrary villagers because `housingNeed < X`.

Use vanilla-compatible breeding conditions where practical.

## 27. Village Chronicle

Required.

Events are immutable historical facts.

Conceptual structure:

```text
VillageEvent
├── event ID
├── game time
├── type
├── severity
├── village ID
├── actor citizen ID
├── target ID
├── related plan/task
├── structured parameters
└── localized human-readable representation
```

Required event examples:
- VILLAGE_REGISTERED
- CITIZEN_REGISTERED
- ROLE_ASSIGNED
- ROLE_LEVEL_UP
- SKILL_LEVEL_UP
- TOOL_UPGRADED
- PLAN_CREATED
- PLAN_BLOCKED
- PLAN_COMPLETED
- BUILDING_STARTED
- BUILDING_COMPLETED
- RESOURCE_MILESTONE
- CITIZEN_BORN
- CITIZEN_DIED

Do not spam history with every placed dirt block.

History stores meaningful events.

## 28. Village Ledger UI

Avoid requiring a custom magical item.

Primary interaction:

> Right-click the village bell while holding a vanilla Book.

Open the **Village Ledger** UI for the village associated with that bell.

Tabs:
- Overview
- Citizens
- Resources
- Buildings
- Plans
- Chronicle

Admin/debug users additionally see:
- Diagnostics

## 29. Overview tab

Display at minimum:
- Village name
- Population
- Known citizens
- Housing
- Food status
- Resource summary
- Active plans
- Blocked plans
- Buildings

## 30. Citizens tab

Display:
- Name
- Role
- Role level
- XP
- Current task/state
- Home
- Selected important skills
- Lifetime summary

Selecting citizen shows details.

## 31. Plans tab

Example:

```text
Build House
IN_PROGRESS
68%
Builder: Hans

Upgrade Storage
BLOCKED
Missing: 19 oak planks

Create Animal Pen
BLOCKED
No suitable site found
```

Selecting a plan shows dependency tree.

## 32. Chronicle tab

Chronological list.

Example:

```text
Day 12 — Erik became Lumberjack II.
Day 16 — Construction of House #3 began.
Day 19 — House #3 was completed by Hans.
Day 25 — Anna was born.
```

## 33. Debug/admin commands

Implement a command namespace such as:

```text
/livingvillages
```

Required useful commands:
- `/livingvillages village inspect`
- `/livingvillages village list`
- `/livingvillages citizen inspect <target>`
- `/livingvillages plan inspect <id>`
- `/livingvillages debug on`
- `/livingvillages debug off`

Exact command ergonomics may adapt to NeoForge/Minecraft command APIs.

Debug output should expose IDs and internal scores.

## 34. Village debug diagnostics

Example:

```text
Village: Oakshire
ID: ...

Needs:
FOOD      0.24
HOUSING   0.89
WOOD      0.77
METAL     0.42

Selected goal:
IncreaseHousing

Reason:
Highest feasible utility.

Active plan:
BuildHouse #42

State:
BLOCKED

Reason:
MISSING_RESOURCE

oak_planks:
required = 64
reserved = 16
available = 19

Dependency:
GatherWood #44
BLOCKED

Reason:
NO_REACHABLE_TARGET
```

## 35. Player interference

Systems must react when the world changes.

Examples:

Player breaks planned block:
- detect construction mismatch;
- determine whether builder should repair.

Player removes storage chest:
- invalidate storage reference;
- re-evaluate resource plans.

Player blocks entrance:
- path failure eventually produces blocker.

Player steals resources:
- resource cache corrects itself;
- affected plans become waiting/blocked.

## 36. Configuration

Provide configuration for important performance/gameplay values.

Examples:
- planner interval
- resource scan radius
- tree search radius
- mining radius
- site search radius
- stuck timeout
- retry limits
- maximum active plans
- maximum animals per village
- history retention
- debug verbosity

Use conservative defaults.

## 37. v1.0 definition of "working"

A fresh test world should support this scenario:
1. player discovers normal village;
2. mod registers village;
3. citizens receive persistent identities;
4. village has visible needs;
5. village creates at least one useful plan;
6. workers gather resources;
7. workers gain XP;
8. resources are transported;
9. builder constructs at least one valid building progressively;
10. history records meaningful events;
11. plans can become BLOCKED;
12. player can inspect exact blocker reason;
13. save/reload preserves state;
14. full project builds successfully.
