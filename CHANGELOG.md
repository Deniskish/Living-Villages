# Changelog

## 1.0.0-dev — 2026-10-20

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
