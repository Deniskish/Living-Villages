# Living Villages v1.0 — Master Codex Task

You are implementing the first playable release of the Minecraft mod in this repository.

Do not treat this task as a prototype or architecture-only exercise.

Your goal is to leave the repository with a working, buildable, testable v1.0.

## Step 1 — Read project requirements

Before modifying code, read completely:
- `AGENTS.md`
- `docs/GAME_RULES.md`
- `docs/SPECIFICATION.md`
- `docs/ARCHITECTURE.md`
- `docs/V1_SCOPE.md`
- `docs/ACCEPTANCE_TESTS.md`

Treat them as the project specification.

Do not ask the user to restate information already contained there.

## Step 2 — Inspect current repository

Inspect:
- current Gradle configuration;
- Minecraft/NeoForge version;
- source tree;
- existing tests;
- resources;
- current compilation state.

If the repository is initially empty, create a proper NeoForge Java 21 project.

Use the current stable NeoForge setup compatible with the selected Minecraft target version.

Do not invent obsolete APIs when current project mappings/API show a different approach.

## Step 3 — Create implementation plan

Create:

`docs/EXECUTION_PLAN.md`

Break implementation into dependency-aware milestones.

The plan must result in working vertical slices rather than many empty abstractions.

Suggested sequence:
1. buildable mod foundation;
2. persistence and IDs;
3. village/citizen registration;
4. history/event system;
5. plan/task/blocker core;
6. diagnostics;
7. resource/storage model;
8. first complete Lumberjack vertical slice;
9. Village AI needs/utility;
10. remaining professions;
11. site planner;
12. builder/construction;
13. housing/population;
14. Village Ledger UI;
15. roads/minimal settlement expansion;
16. save/reload hardening;
17. integration tests;
18. documentation/release review.

Update the plan as implementation reveals constraints.

## Step 4 — Implement v1.0 fully

Implement every REQUIRED feature in `docs/V1_SCOPE.md`.

Do not stop after:
- defining interfaces;
- registering commands;
- creating empty GUI screens;
- creating placeholder blueprints;
- writing fake resource counters.

The core user scenario must actually function.

## Step 5 — Preserve vanilla-first design

Use vanilla Minecraft content wherever possible.

Do not solve complexity by adding:
- magic resource blocks;
- custom ore tiers;
- artificial worker tokens;
- invisible production machines;
- instant building items.

Villagers must interact with actual world state.

## Step 6 — Make failures inspectable

This is mandatory.

Every major plan/task must expose:
- current state;
- progress;
- dependencies;
- assigned citizen;
- missing requirements;
- blocker;
- last meaningful progress;
- retry information.

A user must be able to understand why a village is stuck.

Implement structured diagnostics rather than only log strings.

## Step 7 — Implement the Chronicle

Important village changes must produce persistent events.

Do not log every trivial action.

Prefer events such as:
- citizen registered;
- role assigned;
- level gained;
- tool upgraded;
- plan created;
- plan blocked;
- building started;
- building completed;
- citizen born;
- citizen died.

Chronicle entries must survive reload.

## Step 8 — Implement the Village Ledger

Primary interaction:

Right-click a registered village bell while holding a vanilla Book.

Provide practical tabs:
- Overview
- Citizens
- Resources
- Buildings
- Plans
- Chronicle

For authorized admin/debug users:
- Diagnostics

The UI may be visually simple in v1.0, but it must be functional.

## Step 9 — Implement real construction

Use blueprint/template-based structures.

Do not instantly spawn completed buildings as normal gameplay.

Required flow:
- need;
- plan;
- site;
- manifest;
- dependencies;
- materials;
- builder;
- progressive block placement;
- completion;
- building registration;
- Chronicle event.

Implement at least the required v1.0 blueprint set.

## Step 10 — Test bad situations intentionally

Do not test only the happy path.

Test scenarios including:
- no wood;
- no storage;
- destroyed storage;
- unreachable tree;
- builder dies;
- target building site becomes obstructed;
- player removes required resources;
- citizen unloads;
- chunk unloads;
- save/reload mid-plan;
- broken dependency;
- no qualified worker.

The system should fail safely and explain why.

## Step 11 — Build continuously

After major milestones:
- compile;
- run relevant tests;
- fix regressions immediately.

Before completion run:

`gradlew test`

and:

`gradlew build`

Both must succeed.

Do not declare success with failing build/tests.

## Step 12 — Review the implementation

When v1.0 functionality is implemented, perform an internal review.

Review specifically:

### Architecture
- giant classes;
- duplicated state;
- bad ownership;
- circular dependencies;
- client/server leaks.

### Correctness
- persistence;
- IDs;
- stale references;
- state transitions;
- resource duplication;
- resource loss;
- retry loops.

### Performance
- world scans;
- per-tick work;
- pathfinding frequency;
- unnecessary allocations;
- repeated container scanning.

### Gameplay
- village actually acts autonomously;
- citizens visibly work;
- progression is understandable;
- blockers are understandable;
- construction produces believable village growth.

Fix high-severity issues found during review.

## Step 13 — Produce v1.1 backlog

Create:

`docs/V1_1_BACKLOG.md`

Do not blindly copy the future-feature list.

Base the backlog on:
1. specification;
2. actual v1.0 architecture;
3. limitations discovered during implementation;
4. likely refactors;
5. gameplay gaps.

Group backlog into:
- Critical fixes
- Architecture improvements
- Village progression
- Military
- Government
- Diplomacy
- Fortifications
- Kingdoms
- Nether
- Performance
- UX

For each major item provide:
- objective;
- dependencies;
- risk;
- suggested implementation direction.

## Step 14 — Documentation

Update/create:
- `README.md`
- `CHANGELOG.md`
- `LICENSE`
- `.gitignore`

README must explain:
- what Living Villages is;
- current Minecraft/NeoForge requirement;
- installation;
- how villages work;
- professions;
- plans;
- Chronicle;
- Village Ledger;
- diagnostics;
- known limitations;
- roadmap.

Use MIT license unless dependency/license constraints require another compatible choice.

## Step 15 — Final report

At the end, provide a concise implementation report containing:

### Implemented
List major working systems.

### Tests
Show which tests/build commands passed.

### Known limitations
Only real remaining limitations.

### Architecture decisions
Mention significant decisions that affect v1.1.

### v1.1
Summarize the generated backlog.

Do not claim features that are only placeholders.

# Acceptance criteria

The implementation is accepted only if a player can reasonably experience this loop:

```text
discover ordinary village
        ↓
village becomes registered
        ↓
villagers become persistent individuals
        ↓
village evaluates needs
        ↓
village creates plans
        ↓
workers gather real resources
        ↓
workers gain experience
        ↓
resources enable construction
        ↓
builder physically builds
        ↓
population/housing can develop
        ↓
history records important events
        ↓
blocked plans explain exactly why they are blocked
        ↓
save/reload preserves the simulation
```

The most important quality requirement is:

> Never hide broken simulation behind silent magic.

If an NPC cannot do something, expose why.
