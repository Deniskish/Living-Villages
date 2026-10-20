# Living Villages — Codex Instructions

## 1. Project

Living Villages is a Minecraft Java Edition mod that turns vanilla villages into autonomous evolving settlements.

Core fantasy:

> The player lives inside a world where villages develop on their own.

Villagers must gather resources, gain experience, perform professions, create plans, build structures block-by-block, reproduce, keep history, diagnose blocked actions, and gradually change their settlement.

The project must remain recognizably vanilla Minecraft.

Avoid unnecessary:
- custom ores;
- custom dimensions;
- custom tools;
- custom armor;
- decorative content;
- artificial currencies.

Prefer existing Minecraft blocks, items, entities, recipes, containers, structures, mechanics, and pathfinding.

## 2. Technology

Use:
- Java 21
- Minecraft Java Edition
- NeoForge
- Gradle
- SLF4J logging
- vanilla Minecraft systems wherever practical

Mod ID: `livingvillages`

Base package: `dev.livingvillages`

Do not introduce Kotlin, Scala, JavaScript, Python, native C++, or additional language runtimes.

## 3. Source of truth

Read these documents before making architectural decisions:

1. `AGENTS.md`
2. `docs/GAME_RULES.md`
3. `docs/SPECIFICATION.md`
4. `docs/ARCHITECTURE.md`
5. `docs/V1_SCOPE.md`
6. `docs/ACCEPTANCE_TESTS.md`

If code conflicts with these documents, the documents win unless implementation reality makes a requirement impossible.

If a requirement is impossible because of Minecraft/NeoForge behavior:
1. document the limitation;
2. implement the closest robust alternative;
3. do not silently fake functionality.

## 4. Development workflow

For large work:
1. inspect the repository;
2. create or update `docs/EXECUTION_PLAN.md`;
3. identify dependencies between subsystems;
4. implement foundation first;
5. implement vertical slices that can actually run;
6. compile frequently;
7. add tests for deterministic logic;
8. run tests;
9. run full Gradle build;
10. fix failures before moving on.

Do not stop after scaffolding.

Do not return a repository filled with interfaces, TODOs, empty methods, fake implementations, or placeholder logic.

A feature listed as required for v1.0 must either:
- work;
- or be explicitly documented as blocked by a concrete technical reason.

## 5. Architecture principles

### 5.1 Server authoritative

All gameplay state belongs to the logical server.

Clients may display state but must not be authoritative for:
- village resources;
- plans;
- skills;
- tasks;
- construction;
- citizen assignments;
- history;
- village progression.

Single-player must behave as an integrated server, not as a separate implementation.

### 5.2 Village simulation is separate from vanilla entities

Do not make the entire system depend directly on mutable fields added to vanilla `Villager`.

Use UUID-based associations.

Conceptually:

`Vanilla Villager Entity <-> UUID <-> CitizenData`

A citizen may temporarily be unloaded while `CitizenData` still exists.

A village may exist while some or all villagers are unloaded.

### 5.3 Separate strategic AI from citizen AI

The village decides **what should happen**.

Citizens decide **how to execute assigned work**.

Required hierarchy:

`Village Need -> Village Goal -> Plan -> Task -> Citizen Action -> Event`

Do not let every citizen independently decide global village strategy.

### 5.4 Explainability is mandatory

Every plan and task must have inspectable state.

Required states include where applicable:
- PLANNED
- WAITING
- ASSIGNED
- IN_PROGRESS
- BLOCKED
- COMPLETED
- FAILED
- CANCELLED

Every BLOCKED state must provide:
- machine-readable blocker type;
- human-readable explanation;
- timestamps;
- involved entity/resource/building IDs where applicable;
- dependency information where applicable.

Example:

`MISSING_RESOURCE: oak_planks required=64 available=19`

Not acceptable:

`Task failed.`

### 5.5 No invisible cheating

NPCs must obey the world.

They cannot:
- teleport to perform normal jobs;
- create resources from nothing;
- build without materials;
- use tools they do not possess;
- mine blocks they cannot reach;
- place blocks where Minecraft placement rules make no sense;
- instantly spawn completed buildings.

Administrative/debug commands may bypass rules only when clearly marked as debug functionality.

### 5.6 Physical execution

Where the world is loaded, important work must be visibly performed.

Examples:

Lumberjack:
`find tree -> path to tree -> break logs -> collect drops -> transport resources`

Builder:
`retrieve materials -> path to site -> place blocks progressively`

Miner:
`travel to mine -> break allowed blocks -> collect drops -> return resources`

Farmer:
`travel to field -> harvest -> replant -> transport harvest`

### 5.7 Unloaded chunks

v1.0 must not magically modify unloaded terrain.

When required terrain/entities are unloaded:
- strategic plans may continue to exist;
- timers/history metadata may continue where safe;
- physical tasks become WAITING/BLOCKED as appropriate;
- world mutation resumes only when necessary chunks are available.

Design interfaces so that a future background simulation layer can be added without rewriting the planner.

### 5.8 Performance

Never scan huge world regions every tick.

Use:
- cached indexes;
- bounded searches;
- configurable radii;
- low-frequency strategic ticks;
- incremental scans;
- task queues;
- pathfinding budgets.

Avoid O(all villages × all blocks) logic.

Heavy planning should run periodically, not every game tick.

## 6. Coding rules

Prefer:
- composition over deep inheritance;
- immutable records for value objects/events where appropriate;
- enums for stable state machines;
- explicit services/managers;
- dependency injection through constructors where practical;
- deterministic utility calculations;
- small focused classes;
- unit-testable pure logic.

Avoid:
- giant manager classes;
- global mutable static maps;
- magic numbers;
- reflection;
- duplicated state;
- silent exception swallowing;
- direct client/server state mixing;
- giant switch statements containing every gameplay system.

## 7. Persistence

Village state must survive:
- save/reload;
- server restart;
- villagers unloading/reloading;
- chunk unloading/reloading.

Persist at minimum:
- villages;
- citizens;
- roles;
- role levels;
- XP;
- skills;
- buildings;
- plans;
- tasks where safe;
- blockers;
- village history;
- citizen history;
- resource/storage references;
- configured village boundaries;
- planner metadata.

Use stable IDs.

Migration/version fields should exist from v1.0 so data format can evolve later.

## 8. Logging

Use structured logging.

Log important lifecycle events:
- village discovered;
- citizen registered;
- role changed;
- plan created;
- task assigned;
- plan blocked;
- building site selected;
- building completed;
- serialization failure;
- corrupted references;
- unrecoverable AI error.

Do not spam normal logs every tick.

Verbose information belongs behind debug configuration.

## 9. Testing

At minimum test deterministic logic for:
- utility scoring;
- profession XP thresholds;
- progression restrictions;
- task dependencies;
- blocker generation;
- site scoring;
- resource requirements;
- plan state transitions;
- serialization where feasible.

Use Minecraft/NeoForge game tests for world-dependent mechanics where practical.

Before considering v1.0 complete:

`gradlew test`

and

`gradlew build`

must succeed.

## 10. Git hygiene

Keep commits conceptually coherent.

Do not commit:
- build output;
- IDE caches;
- local run worlds;
- secrets;
- crash dumps.

Maintain:
- `README.md`
- `.gitignore`
- `LICENSE`
- `CHANGELOG.md`

Use MIT license unless an included dependency imposes incompatible requirements.

## 11. v1.1 compatibility

v1.0 architecture must leave extension points for future:
- village levels I–V;
- technology tree;
- leaders;
- kings;
- royal guards;
- soldiers;
- archers;
- military skill progression;
- diplomacy;
- raids;
- wars;
- conquest;
- fortress walls;
- colonies;
- Nether expeditions.

Do not fully implement these unless required by `V1_SCOPE.md`.

Do not hard-code assumptions that make these additions difficult later.
