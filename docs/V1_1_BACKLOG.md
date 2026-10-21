# v1.1 backlog

## Status after 1.1.0-dev (2026-10-21)

| Item | Status | Notes |
|---|---|---|
| Village levels I–V from real facts | DONE (automated) | `V1_1_SPEC.md` §1; long survival balance of Tier IV/V is unmeasured (M3) |
| Capability policy | DONE (automated) | 13 capabilities; planner/executors no longer compare tier numbers |
| Technology framework + one technology per branch | DONE (automated) | Economy, Infrastructure, Expansion; one research plan at a time |
| Wood palettes and building variants | PARTIAL | 8 wood families, `house_t2_01`; no non-wood palettes (desert sandstone, badlands terracotta) or new shapes |
| Diamond mining | DONE (gated) | Practical only for exposed reachable ore; needs shafts/tunnels to matter |
| Ledger overlap fix | MANUAL_REQUIRED | Geometry unit-tested; graphical M4 not yet performed |
| Graphical Ledger, ordinary-world restart, survival soak | MANUAL_REQUIRED | M1–M4 unchanged in status |

New findings from this milestone:

- **Restart fixture race:** on a fresh world, villagers added to a just-forced
  chunk stayed invisible to entity queries until the chunk became entity-ticking.
  The two-JVM fixture now waits; production code never force-loads.
- **Chronicle noise:** v1.0 emits `RESOURCE_MILESTONE` for every craft. v1.1
  milestones use `PRODUCTION_MILESTONE`; consider renaming the per-craft event
  (`ITEM_CRAFTED`) through a schema migration so history types stay meaningful.
- **Production before migration** is unknown for v1.0 worlds; migrated villages
  re-earn tiers. A one-off survey (counting completed buildings' materials) could
  seed a conservative record if players find the restart too slow.
- **Research throughput:** one plan at a time and a single researcher; parallel
  branches, research buildings (library/workshop blueprint) and cancellation UX
  are follow-ups.
- **Palette edge cases:** a village whose only nearby wood is far outside the
  boundary falls back to stocked wood or oak; trading/importing wood is not modelled.
- **Cargo donors:** `fetch` now skips carriers whose current plan reserves the
  item; claims on carried items remain per strategic tick, not per transfer.

## Original v1.1 backlog

This backlog follows the implemented server-owned domain, UUID association,
physical inventory adapters, job executors, template construction and snapshot UI.
Do not begin the optional political/military branches before release validation.

| Area | Objective | Dependencies | Risk | Implementation direction |
|---|---|---|---|---|
| Critical fixes | Complete client Ledger, real-process restart, unload/reload and survival soak acceptance | Current build and reproducible world fixtures | High: automated serialization is not a complete lifecycle test | Maintain recorded world fixtures; verify A1–A19 across an actual restart and multiplayer session |
| Critical fixes | Test protection-mod interactions and natural-looking player construction | Physical work adapters and server event contracts | High: ownership cannot be inferred perfectly from vanilla blocks | Add explicit allowed work areas and opt-out storage; integrate claim checks before mutation |
| Critical fixes | Exercise bootstrap without prefilled building materials across village biomes | Planner, recipes, miners, forestry, livestock | High: resource/XP/workforce deadlocks can be environmental or algorithmic | Long-running deterministic scenarios with resource-flow traces; expose the distinction in blockers |
| Architecture improvements | Replace whole-document persistence for growing chronicles | Stable event IDs and existing data version | Medium | Versioned migration to append-only per-village archives plus compact live state; preserve all historical references |
| Architecture improvements | Stronger typed IDs, job dispatch and schema migration | Stable UUID semantics, current state codec | Medium | Introduce value wrappers through explicit migration; maintain historical plans and task phases |
| Architecture improvements | Ownership-aware cargo and workstation reservations | Real inventories, current resource claims | Medium | Persist claims on input/output batches and equipment; never serialize duplicate item copies in domain data |
| Village progression | ~~Derive village levels I–V from actual population, housing and production~~ — done in 1.1.0-dev | Need/goal scoring, buildings and profession levels | Medium | Capability policy evaluated against server snapshots, with visible unlock explanations |
| Village progression | ~~Technology choices with physical research/production costs~~ — framework + 3 technologies in 1.1.0-dev | Progression policy, dependencies, chronicle | Medium | Technology plans producing persistent facts; keep materials and worker activity real |
| Village progression | More biomes, ~~wood palettes~~ and building variants — wood palettes done; non-wood palettes/shapes open | Blueprint loader, recipe selection, site planner | Medium | Resolve local material palettes before manifest generation; persist the chosen palette for resume |
| Military | Soldier/archer training and skill-based accuracy | Skill map, equipment economy, new task executors | High | Independent combat roles; consume real ammunition and equipment; record casualties and training |
| Government | Persistent leaders and succession | Citizen identity, event history, village progression | Medium | Model offices as actor references with explicit succession policies and diagnostics |
| Diplomacy | Agreements, reputation and conflict decisions | Leaders, stable village IDs, goal evaluation | High | Server-owned relationship records and events; do not mix diplomacy into citizen navigation |
| Fortifications | Walls, gates and defensive site planning | Multi-template construction, terrain surveys, military needs | High | Segmented blueprints and dependency graphs; preserve roads and access during construction |
| Kingdoms | Multiple-village governance and colonies | Government, diplomacy, territorial indexes | High | Separate aggregate above villages; bounded cross-village planning and real logistics |
| Nether | Expedition preparation and dimension travel | Equipment tiers, territory, cross-dimension citizen lifecycle | High | Explicit expedition plans; no background terrain mutation or invented returned resources |
| Performance | Profile many villages and long histories | Repeatable large-world scenarios | Medium | Measure scan, pathfinding, save and Ledger costs; share server-wide budgets and archive old terminal plans |
| Performance | Incremental construction-prefix validation and bounded path budgets | Existing site/execution separation | Low–medium | Validate recent edits through dirty-region hints plus rotating audits; keep periodic full consistency checks |
| UX | Paginated history/plans, preserved selection and localization | Snapshot DTOs, request authorization | Low | Cursor-based server views; never send the entire mutable world to clients |
| UX | Clear plan cancellation/retry and worker-selection explanations | Validated state transitions and reservation release | Medium | Authorized commands/UI actions that release claims and preserve audit history; no free completion |
| UX | Record screenshots, short demos and a guided first-world scenario | Client playtest | Low | Show real forestry, blocked dependency recovery and progressive building |

Additional gameplay follow-ups: safe scaffolding for taller trees/buildings,
adaptive terrain support, field expansion, pen stocking, damaged-building repair,
broader furnace recipes, road elevation changes and longer-distance logistics.
Each needs physical actions and explicit failure states before being advertised.
