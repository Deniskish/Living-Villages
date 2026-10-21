# Changelog

## 1.1.0-dev — 2026-10-21

Development milestone (branch `feature/v1.1-progression`). Not a stable release;
graphical Ledger acceptance (M4) has not been performed by a person.

### Village Ledger

- Fixed overlapping Ledger controls: geometry now comes from a pure `LedgerLayout`
  model (header, wrapped tabs or compact selector, content viewport, scrollbar,
  footer) computed from the real screen size and measured labels.
- The panel is drawn in `renderBackground`; previously Minecraft 1.21's blurred
  menu background was painted over the Ledger text after it was drawn.
- Rows are clipped to the viewport; clicks and the wheel act only inside it;
  long titles/names end with an ellipsis; details wrap to the viewport width.
- Refresh keeps the tab and re-selects the same row by a stable key; a refresh
  answer cannot reopen a closed Ledger (pure `LedgerRouting`).
- New Progression tab; tier row in Overview; palettes in Buildings/Plans;
  production record in Resources; progression diagnostics for operators.
  Network protocol version 3.

### Progression

- Village tiers I–V (Settlement, Village, Town, City, Capital) derived from
  population, usable beds, completed buildings, storage, practised profession
  levels, a physical production record, food reserve and technologies. Historical
  tier never decreases; each tier is recorded once (`VILLAGE_TIER_REACHED`).
- Capability layer (13 capabilities) with tier/technology unlocks and physical
  infrastructure; `CAPABILITY_LOCKED` / `INFRASTRUCTURE_MISSING` blockers.
  Iron-tool crafting now also requires Tier II and an intact smithy.
- Technologies with physical research plans: Charcoal Burning (Economy), Timber
  Framing (Infrastructure), Deep Prospecting (Expansion).
- Diamond mining: Tier IV + Deep Prospecting + Miner V + iron pickaxe + exposed,
  reachable ore. Advanced mining (Tier IV) indexes and mines deeper.
- Tier V expands the working boundary by 16 blocks (cap 96).
- Production milestones (`PRODUCTION_MILESTONE`) at 1/64/512/4096 per category.

### Palettes and buildings

- Eight 1.21.1 wood palettes; blueprint wood symbols are palette tokens. v1.0
  templates resolve to identical oak blocks. Local palette chosen from indexed
  trees, stock and a biome tie-break; persisted per plan and building.
- Palette-aware recipes and family-specific wood gathering (`GATHER_WOOD:<log>`).
- New three-bed `house_t2_01` behind `ADVANCED_CONSTRUCTION`.

### Persistence, commands, tooling

- Schema version 2 with explicit v1→v2 migration; v1.0 worlds load unchanged.
- `/livingvillages progression inspect` (read-only); `village inspect` shows tier,
  technologies and palette; operator display-only `debug rename`.
- `scripts/acceptance-restart.sh` for macOS/Linux. The two-JVM restart now also
  carries a mid-research technology plan and a spruce house across processes.
- Restart fixture waits for its forced chunks to become entity-ticking before
  spawning villagers (fresh worlds load entity storage asynchronously) and drives
  executors with the scheduler's retry semantics (a transient path failure right
  after loading had stalled the resumed house).
- 23 new unit tests (36 total) and 12 new GameTests (40 total).

## 1.0.0-dev — 2026-10-20

### Acceptance and hardening pass

- Added two-JVM world restart verification, inventory reconciliation and resumed
  construction using real region/entity/SavedData files.
- Added a 48,000-tick, three-village / 36-citizen soak with actual chunk unloading,
  ownership assertions and bounded work/scan/planning measurements.
- Fixed overlapping-village storage ownership and duplicate position counts.
- Prevented Minecraft's SavedData fallback from replacing an unreadable existing
  save; made terminal work operations atomic and completion idempotent.
- Bounded Ledger snapshots and diagnostic expansion, reduced history lookup
  costs, validated Book/bell refresh access and supported secondary bells.
- Preserved the selected Ledger tab on refresh, prevented late refresh from
  reopening a closed screen, cached wrapping and adapted tabs to narrow screens.
- Removed completed-plan archives from frequent scheduling/lookup paths;
  released unloaded village indexes and suppressed duplicate retry assignment
  events. Preserved all permanent history.
- Fixed reachable-container arrival range, loaded-chunk checks in pen searches
  and resource/data pack format metadata.
- Added explicit A1–A20 statuses and manual Ledger/restart/survival procedures.
  The version remains a development build; visual acceptance is unperformed.

### Initial implementation

- Added the pinned Minecraft 1.21.1 / NeoForge / Java 21 build and Gradle wrapper.
- Added UUID village/citizen registration, versioned server persistence, per-role
  XP, generic skills, history and recovery diagnostics.
- Added utility goals, bounded scheduling, resource acquisition dependencies,
  physical reservations, worker assignment and retry backoff.
- Added physical forestry, farming, exposed mining, controlled animal breeding,
  shearing, recipe-validated production, furnace smelting and food provisioning.
- Added six templates, site validation/scoring, progressive construction,
  building inspection and minimal road work.
- Added the book-and-bell Ledger, server snapshots and inspect/debug commands.
- Added deterministic tests and dedicated-server GameTests, including autonomous
  house construction and save/restore during construction.
- Fixed NBT string-size limits by storing the persistent document as bytes.
- Fixed worker movement conflicts with vanilla Brain, item collection reach,
  building visibility checks and resource transfer into full containers.
- Fixed tool-dependent mining loot, missing/duplicate saved worker associations
  and paired-block checks at unloaded chunk boundaries.

This is a development build. Outstanding manual release checks and concrete
limitations are listed in README and the acceptance report.
