# Living Villages — Version 1.0 Scope

## 1. Objective

v1.0 is not a prototype shell.

It must be a playable first release demonstrating the core idea:

> A vanilla village can autonomously gather resources, organize work, develop individual villagers, construct buildings, grow, remember its history and explain failures.

## 2. Required systems

### Foundation
- NeoForge project
- Java 21
- mod initialization
- configuration
- server/client separation
- networking foundation
- persistence/versioning
- logging

Status required: COMPLETE.

### Village Registry

Required:
- detect/register vanilla village;
- stable village ID;
- village center;
- working boundary;
- village name;
- persistence;
- citizen membership;
- unload/reload safety.

Status required: COMPLETE.

### Citizen Registry

Required:
- stable UUID association;
- persistent name;
- village association;
- role;
- role level I–V;
- XP;
- generic skills;
- current task;
- history/stats.

Status required: COMPLETE.

### Village AI

Required:
- periodic needs calculation;
- utility-based goal selection;
- feasibility;
- plan creation;
- duplicate-plan suppression;
- worker assignment;
- configurable intervals.

Status required: COMPLETE.

### Planning system

Required:
- plans;
- tasks;
- dependencies;
- priorities;
- resource requirements;
- reservations;
- state machines;
- progress tracking;
- retry/backoff;
- blocker detection.

Status required: COMPLETE.

### Diagnostics

Required:
- structured blockers;
- human-readable explanations;
- dependency-tree explanations;
- stuck detection;
- admin commands;
- diagnostics tab/data model.

Status required: COMPLETE.

This is a core feature, not optional developer tooling.

### Chronicle

Required:
- village events;
- personal citizen events;
- significant milestones;
- persistence;
- chronological display.

Status required: COMPLETE.

### Village Ledger

Required interaction:

Right-click village bell while holding a vanilla Book.

Required tabs:
- Overview
- Citizens
- Resources
- Buildings
- Plans
- Chronicle

Authorized debug users:
- Diagnostics

Status required: COMPLETE enough for practical gameplay and debugging.

Visual polish may remain modest.

## 3. Required professions

### Lumberjack

Required:
- tree selection;
- pathing;
- harvesting;
- item collection;
- delivery;
- replanting where appropriate;
- XP;
- blockers.

### Farmer

Required:
- identify farm;
- harvest mature crops;
- replant;
- transport food;
- XP;
- blockers.

### Miner

Required:
- bounded mining logic;
- actual block breaking;
- resource-level restrictions;
- collection;
- delivery;
- XP;
- blockers.

Required initial capability model:

```text
I   stone / coal
II  copper
III iron
IV  gold / redstone / lapis
V   high-tier architecture prepared
```

Diamond mining may remain gated for future village progression if necessary.

### Animal Keeper

Required:
- detect supported animals;
- controlled breeding;
- food use;
- population caps;
- basic pen awareness;
- XP;
- blockers.

### Smith

Required:
- tool demand detection;
- ingredients;
- crafting/production;
- worker tool upgrades;
- actual resource consumption;
- XP;
- blockers.

### Builder

Required:
- retrieve materials;
- execute blueprint;
- progressive placement;
- detect changed/blocked site;
- complete/register building;
- XP;
- blockers.

## 4. Required buildings

Ship at least:
- 3 x small house variants
- 1 x village storage
- 1 x animal pen
- 1 x smithy

Use vanilla blocks.

Templates should demonstrate the system rather than architectural beauty.

## 5. Site planner

Required:
- bounded candidate generation;
- validation;
- scoring;
- collision checking;
- slope checking;
- water checking;
- support checking;
- entrance accessibility;
- boundary checking;
- diagnostics;
- selected-site persistence.

## 6. Resource economy

Required:
- physical resource tracking;
- inventory/storage discovery;
- reservation system;
- shortage detection;
- cache validation;
- resource plans.

No virtual infinite resource pool.

## 7. Housing/population

Required:
- determine usable housing/beds;
- generate housing demand;
- construct house when justified;
- allow vanilla-compatible population growth.

Do not implement artificial `spawnVillager()` as the normal growth mechanic.

## 8. Roads

Target for v1.0:
- simple connection from new building entrance to nearby village path/network.

If this feature threatens release stability:
- implement safe minimal version;
- document limitations.

Do not sacrifice core construction for sophisticated road generation.

## 9. Persistence

Required to survive:
- game save/load;
- server restart;
- chunk unload/load.

Test:
- village identity;
- citizen identity;
- XP;
- plans;
- buildings;
- chronicle;
- diagnostics metadata.

## 10. Required debug scenario

The following scenario must be reproducible:

1. Create/discover village.
2. Register citizens.
3. Force or naturally create housing need.
4. Village plans house.
5. Remove needed wood.
6. Plan becomes WAITING/BLOCKED.
7. Ledger shows missing wood.
8. Dependency points to wood gathering.
9. Block access to all reachable trees.
10. Lumberjack task eventually reports no reachable tree.
11. Dependency tree explains the entire chain.
12. Restore access/resources.
13. plan recovers.
14. builder completes house.
15. chronicle records completion.

## 11. Required tests

At minimum:
- role XP level calculation;
- plan transition validation;
- blocker creation;
- dependency blocker propagation;
- utility scoring;
- resource reservation;
- site scoring/rejection;
- data serialization where feasible.

## 12. Release requirements

Before v1.0 is considered complete:

`gradlew test`

passes.

`gradlew build`

passes.

README contains:
- project description;
- screenshots/GIF placeholders if no media available;
- installation instructions;
- gameplay explanation;
- debug instructions;
- current limitations;
- roadmap.

Repository also contains:
- `LICENSE`
- `CHANGELOG.md`
- `docs/EXECUTION_PLAN.md`
- `docs/V1_1_BACKLOG.md`

## 13. Explicitly NOT required for v1.0

Do not let these delay v1.0:
- king;
- player becoming king;
- royal guard;
- armies;
- archers;
- raids;
- wars;
- conquest;
- diplomacy;
- fortress walls;
- colonies;
- kingdom hierarchy;
- Nether expeditions;
- full technology tree;
- final village level I–V implementation.

However, architecture must be compatible with them.

## 14. v1.1 planned direction

After real gameplay testing of v1.0:
- Village progression I–V
- Technology branches
- Government
- Leader / King
- Royal Guard
- Soldiers
- Archers
- Military XP
- Accuracy progression
- Diplomacy
- Raids
- Wars
- Conquest
- Fortress planner
- Kingdoms
- Colonies
- Nether progression

Exact v1.1 scope must be based on findings from v1.0 rather than blindly implementing everything.
