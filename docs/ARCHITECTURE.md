# Living Villages — Technical Architecture

## 1. Package layout

Recommended starting structure:

```text
dev.livingvillages
│
├── LivingVillagesMod
├── api/
├── village/
│   ├── model/
│   ├── registry/
│   ├── persistence/
│   ├── boundary/
│   └── service/
├── citizen/
│   ├── model/
│   ├── role/
│   ├── skill/
│   ├── registry/
│   └── service/
├── ai/
│   ├── need/
│   ├── goal/
│   ├── utility/
│   ├── planner/
│   └── scheduler/
├── plan/
│   ├── model/
│   ├── dependency/
│   ├── blocker/
│   └── service/
├── task/
│   ├── model/
│   ├── execution/
│   ├── profession/
│   └── state/
├── resource/
│   ├── inventory/
│   ├── storage/
│   ├── reservation/
│   └── service/
├── building/
│   ├── blueprint/
│   ├── site/
│   ├── construction/
│   ├── road/
│   └── model/
├── history/
│   ├── event/
│   ├── chronicle/
│   └── service/
├── command/
├── network/
├── client/
│   ├── screen/
│   └── widget/
├── config/
└── util/
```

Do not create empty packages solely because this document lists them.

## 2. Important domain IDs

Use stable IDs.

Examples:
- VillageId
- PlanId
- TaskId
- BuildingId
- EventId

UUID wrappers or stable string/UUID representations are preferred.

Do not use list index as identity.

## 3. VillageData

Conceptual model:

```java
VillageData {
    VillageId id;
    String name;

    ResourceKey<Level> dimension;
    BlockPos center;

    VillageBoundary boundary;

    Set<UUID> citizens;
    Map<BuildingId, BuildingData> buildings;

    VillageNeeds needs;

    Set<PlanId> activePlans;
    Set<PlanId> blockedPlans;

    VillageStatistics statistics;

    int dataVersion;
}
```

The exact serializable form may differ.

Keep runtime caches separate from persisted domain state when appropriate.

## 4. CitizenData

Conceptual:

```java
CitizenData {
    UUID entityId;
    VillageId villageId;

    String name;

    CitizenRole role;
    int roleLevel;
    long roleXp;

    Map<SkillType, SkillProgress> skills;

    TaskId currentTaskId;

    CitizenStatistics statistics;

    int dataVersion;
}
```

## 5. Village services

Avoid one `VillageManager` doing everything.

Separate responsibilities.

Suggested services:
- VillageRegistry
- CitizenRegistry
- VillagePlanner
- VillageScheduler
- PlanRepository
- TaskRepository
- ResourceService
- StorageService
- BuildingService
- SitePlanner
- ChronicleService
- DiagnosticService

## 6. Strategic tick

Village strategic planning should run significantly less often than 20 TPS.

Example architecture:

```text
Game tick
   ↓
Scheduler
   ↓ when due
Village strategic tick
   ├── refresh lightweight observations
   ├── calculate needs
   ├── evaluate existing plans
   ├── diagnose stale work
   ├── create bounded new plans
   └── assign available tasks
```

Exact intervals should be configurable.

## 7. Citizen execution tick

Citizen task execution may need more frequent updates but should rely on Minecraft AI/navigation rather than expensive global scans.

Task execution should be state-machine based.

Example builder task:

```text
WAIT_ASSIGNMENT
↓
FETCH_MATERIALS
↓
MOVE_TO_SITE
↓
SELECT_NEXT_BLOCK
↓
PLACE_BLOCK
↓
CHECK_PROGRESS
├── more blocks -> SELECT_NEXT_BLOCK
└── complete -> FINISH
```

## 8. Task executor interface

Conceptually:

```java
interface TaskExecutor<T extends VillageTask> {
    ExecutionResult tick(
        ServerLevel level,
        CitizenContext citizen,
        T task
    );
}
```

Result should communicate explicit outcomes:
- PROGRESS
- WAITING
- BLOCKED
- COMPLETED
- FAILED

Include structured diagnostic information.

## 9. Planner interface

Conceptually:

```java
interface GoalEvaluator {
    GoalEvaluation evaluate(VillageSnapshot snapshot);
}
```

Result:
- goal type
- utility score
- feasibility score
- reason breakdown

The breakdown is important for debug UI.

## 10. Utility scoring

Do not hide planning inside opaque random conditions.

Example:

```text
HousingUtility =
    housingNeed
    * urgencyWeight
    * feasibility
    * cooldownFactor
```

Retain component scores for diagnostics.

## 11. Snapshot pattern

Strategic planning should work from a stable snapshot where practical.

Example:

```text
VillageSnapshot
├── population
├── housing
├── resource summary
├── available workers
├── active plans
├── buildings
├── nearby known opportunities
└── current needs
```

This makes planners easier to test.

## 12. Resource reservations

Prevent two plans from spending the same items.

Resource model:
- available
- reserved
- committed

A reservation is released when:
- plan cancelled;
- task failed permanently;
- resources consumed;
- reservation expires safely.

Reservations do not create physical resources.

Before consumption, physical availability must be validated.

## 13. Building manifests

Blueprint loader should derive or load resource requirements.

Example:

```text
House T1:
oak_log        24
oak_planks     64
cobblestone    48
glass_pane     12
bed             2
door            1
```

Builder should take materials in manageable batches rather than requiring full manifest in personal inventory.

## 14. Site planning

Separate:
- SiteCandidateGenerator
- SiteValidator
- SiteScorer
- SiteSelection

This allows debug reports for rejection reasons.

Potential validator pipeline:
- BOUNDARY
- COLLISION
- WATER
- SUPPORT
- SLOPE
- HEIGHT
- ENTRANCE
- PATH
- PROTECTED_BLOCKS

## 15. Building registration

Completed blueprint becomes `BuildingData`.

Building data includes:
- BuildingId
- BlueprintId
- category
- bounding box
- entrance
- owner village
- construction state
- condition
- created time

Future systems can query buildings without scanning all blocks repeatedly.

## 16. Chronicle architecture

Use event publishing.

Example:

```text
Domain action
    ↓
VillageEventBus
    ├── ChronicleService
    ├── StatisticsService
    ├── Debug logger
    └── future reputation/achievement systems
```

Avoid coupling every feature directly to the UI.

## 17. Diagnostics architecture

A diagnostic object should be structured.

Conceptually:

```text
DiagnosticNode
├── status
├── title
├── reason code
├── human text
├── metadata
└── children
```

This allows recursive explanation:

```text
BuildHouse BLOCKED
└── Resources BLOCKED
    └── Oak Planks missing
        └── GatherWood BLOCKED
            └── No reachable tree
```

The same model can power:
- commands;
- Ledger UI;
- logs;
- tests.

## 18. Client networking

Do not send entire server state continuously.

When Ledger opens:
1. client requests permitted village view;
2. server prepares DTO/snapshot;
3. client renders it.

Use incremental refresh if needed.

Debug data should only be sent to authorized users.

## 19. Village Ledger

Client UI must operate on server-provided view models.

Suggested DTOs:
- VillageOverviewDto
- CitizenSummaryDto
- PlanSummaryDto
- PlanDetailsDto
- BuildingSummaryDto
- ChronicleEntryDto
- DiagnosticTreeDto

Do not expose mutable domain objects to the client.

## 20. Persistence design

Include explicit data version.

Example:

`CURRENT_DATA_VERSION = 1`

Future loading path:

```text
load
↓
read version
↓
migrate if necessary
↓
validate references
↓
construct runtime indexes
```

Never assume persisted UUID references are always still valid.

## 21. Recovery

On load:
- missing citizen entity should not crash world;
- destroyed building should be detected;
- missing storage should invalidate reference;
- orphaned task should be recovered or failed;
- invalid plan dependency should generate diagnostics.

Favor recoverable degraded state over save corruption.

## 22. Concurrency

Minecraft world/entity mutation must remain on appropriate server thread.

Do not mutate world state from arbitrary async workers.

If expensive pure computation is later moved async:
- operate on immutable snapshots;
- return result;
- validate against current world before applying.

v1.0 does not require complex async planning.

## 23. Extension points for v1.1

Reserve concepts/interfaces for:
- VillageProgressionService
- TechnologyService
- DiplomacyService
- MilitaryService
- GovernmentService
- TerritoryService
- KingdomService

Do not implement fake empty systems just to claim support.

Instead avoid architectural decisions that prevent them.

Future hierarchy:

```text
Village
↓
Settlement level
↓
Government
↓
Kingdom
↓
Multiple villages
```

## 24. Future military model

Do not implement in v1.0, but citizen skills should support future:

Soldier:
- MELEE
- DEFENSE
- DISCIPLINE

Archer:
- ARCHERY
- ACCURACY
- DRAW_SPEED

Future projectile accuracy should be based on skill rather than raw damage inflation.

## 25. Future government

Architecture should permit:

```text
Village leader
↓
King
↓
Royal Guard
↓
Succession
↓
Player usurpation
```

The ruler must eventually be a persistent actor/reference, not an arbitrary boolean flag.

## 26. Definition of architectural success

v1.0 architecture is successful when adding a future feature such as:

> "Train an archer because the village chose a military technology"

does not require rewriting:
- persistence;
- citizen identity;
- task model;
- plan model;
- chronicle;
- diagnostics;
- UI transport layer.

## 27. v1.1 implementation (progression milestone)

The v1.1 code follows §23 ("VillageProgressionService, TechnologyService") without
adding a parallel simulation. Details and balance values: `V1_1_SPEC.md`.

```text
Strategic tick (VillagePlanner, every plannerInterval, center loaded, index ready)
 ├── stock + citizen cargo (existing)
 ├── Palettes.select(indexed logs, stock, biome)      -> Village.palette (+scores)
 ├── ProgressionService.evaluate                      -> VillageFacts
 │     ├── ProgressionPolicy.qualifiedTier / advance  -> tier, tierHistory, event
 │     └── ProgressionPolicy.evaluate                 -> VillageProgressionSnapshot
 │           ├── CapabilityPolicy.all                 (tier + technology + infrastructure)
 │           └── TechnologyPolicy.all                 (tier, prerequisites, profession, workstation)
 ├── needs/goals (+ DEVELOPMENT)                      -> capability-gated ensure()
 ├── RESEARCH:<id> plan, workstation/material dependencies
 └── reservations (existing priority claims)
Executors (WorkContext.capability / blockCapability)
 ├── HarvestJob   ADVANCED_MINING depth, DIAMOND_MINING (dedicated plan only)
 ├── ProductionJob IRON_TOOLSMITHING, palette recipes, produced()
 ├── SmeltingJob  IRON_SMELTING / CHARCOAL_BURNING, produced()
 └── ResearchJob  fetch cost -> workstation -> units -> consume + unlock (one tick)
BlockDropsEvent (lowest priority) -> Simulation.drops -> production record
```

Boundaries:

- `dev.livingvillages.progression` and `domain.Palettes` are Minecraft-free and
  unit-tested; they operate on `VillageFacts` and domain records only.
- `world.ProgressionService` / `world.Capabilities` adapt server state to facts.
  The latest snapshot is a transient field on `Village` (rebuilt each strategic
  tick, never persisted); Ledger/commands fall back to a read-only evaluation.
- Persisted additions are plain fields on existing domain records, so the
  single-document codec, IDs and recovery path are unchanged (schema 2 + migration).
- Diagnostics reuse `Requirement` → `Diagnostics.Node`; no second blocker model.
- Client geometry is `client.LedgerLayout` (pure); `LedgerScreen` only renders it.

Extension points: add `TechnologyBranch` / `VillageCapability` constants and
`Technologies` entries (e.g. MILITARY training grounds), add tier specs, or add
a blueprint with a `capability` field. Government, military and diplomacy
services can consume `VillageProgressionSnapshot` and capabilities without
reading tier numbers.
