# Changelog

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
