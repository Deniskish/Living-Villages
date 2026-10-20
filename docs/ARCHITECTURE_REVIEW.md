# Implementation review — 2026-10-20

## Ownership and boundaries

- `Model`, `Rules`, `Diagnostics` and `StateCodec` contain the persistent domain
  and deterministic calculations. There are no vanilla entity references in the
  saved domain and no mutable static village maps.
- `VillageSavedData` owns each dimension's domain. `Simulation` owns runtime
  indexes and clears them at server stop. All world work happens on server ticks.
- Discovery, planning, resources, execution, sites, construction, history and
  Ledger transport have separate classes. Jobs share navigation/transfer
  primitives instead of duplicating world mutation logic.
- The client entrypoint is distribution-restricted. Common networking references
  a callback rather than loading client screen classes on a dedicated server.
  Server refresh requests have fixed-size IDs, throttling, proximity, bell and
  held-book checks. Diagnostics require operator permission.

## Resource audit

- Stock is read from actual loaded containers and entity inventories. Claims
  reserve stock without creating it; taking items checks other active claims.
- Transfers shrink source stacks only by the count accepted by the destination.
  Full destinations retain cargo, and alternative accepting storage is selected.
- Crafting queries the current recipe manager and consumes its specified input
  grid. Remainders are preserved. Smelting uses actual furnace slots and fuel.
- Mining passes the actual equipped tool to the vanilla loot calculation after
  successful block removal. The ordinary destruction helper otherwise supplies
  an empty tool, which loses tool-dependent stone/ore drops. A real mining and
  delivery GameTest covers the corrected behavior and durability.
- Construction consumes one item per placement, including paired beds/doors.
  World placement is checked before consumption; a failed partner placement
  restores the first block. Completed-prefix checks prevent replay after reload.
- Death removes dropped working inventory from the villager immediately.
  Equipped work tools are moved out of inventory, not copied, and are configured
  to drop on death.
- There are no gameplay calls to spawn villagers, teleport workers, force chunks,
  or generate ore/items without a block, recipe or existing inventory source.
  Test fixtures deliberately create entities/materials only in GameTests.

## Persistence and recovery

- Explicit schema version; UUIDs survive serialization. Missing/duplicate worker
  associations are repaired with diagnostics; orphan citizen assignments are
  rebuilt. Missing/cyclic dependencies block execution instead of recursion or
  silent continuation.
- Unknown versions and malformed blocked state fail loudly and do not replace
  the save with an empty world.
- Fixed during review: NBT `StringTag` is unsuitable for a growing JSON document
  because modified UTF is limited to 65,535 bytes. The document now uses an NBT
  byte array, with a reader for the earlier development string representation.
- Normal save/restore, vanilla cargo serialization and mid-construction resume
  have automated coverage. A power-loss transaction across Minecraft chunk,
  entity and SavedData files cannot be made atomic by this mod; mismatches are
  diagnosed. Real-process restart/unload soak testing remains on the release list.

## Retry and performance audit

- Retry delays are bounded and increase with repeated failure. Retrying grants
  one new movement window; it does not reset the timeout every execution step.
- Missing resources and routes release workers for other jobs. Unloaded workers
  retain identity and ownership without being treated as dead.
- Terminal plans do not restart. Unexpected executor exceptions log context,
  release claims and become FAILED; the planner does not recreate that failed
  kind indefinitely.
- Terrain indexes advance by a configured block budget. Strategies run
  periodically after the initial surface survey. Execution has a dimension-wide
  worker-step budget. Site candidates and path attempts are bounded.
- Building inspection rotates through completed buildings. The current small
  blueprints permit prefix validation each construction step; larger future
  structures should use dirty regions and rotating validation.

## Remaining practical risks

- Long histories and terminal plans still share one document. Archival storage
  and measured many-village profiling are deferred.
- Initial vanilla buildings do not have authoritative ownership metadata.
  Conservative terrain/tree heuristics cannot guarantee recognition of every
  player-created natural-looking structure.
- The template/recipe palette is narrow; natural bootstrap and balancing across
  every biome are not established by a controlled construction test.
- Client visuals and long survival sessions require manual acceptance. Automated
  dedicated-server success establishes server compatibility, not client UX polish.

## v1.0 hardening review

High-severity fixes:

- **Unreadable save fallback.** Mapped DimensionDataStorage catches exceptions
  from a SavedData deserializer and returns null. Throwing in our decoder alone
  was insufficient: computeIfAbsent could install a blank simulation. Its
  constructor now checks the dimension-specific existing file and refuses that
  replacement. The original file is preserved; an operator must repair/restore
  a corrupt save rather than silently lose history.
- **Shared stock ownership.** Overlapping observation squares previously allowed
  two villages to index the same chest. Ownership now uses boundary membership,
  nearest center and a UUID tie-break; stock also deduplicates repeated positions.
  Discovery, storage, harvesting, site selection and bell lookup use that rule.
- **Terminal mutation.** Work.progress/block check terminal state before touching
  timestamps, attempts or blockers. Complete is idempotent. Reload cancels live
  orphan tasks below terminal plans, clears their reservations and repairs
  duplicate citizen membership and assignment references.

Medium-severity fixes:

- Ledger JSON is bounded after escaping, rows and fields are bounded, and
  dependency expansion has node/depth budgets. Personal history is gathered in
  one bounded reverse scan. Completed plans are read in recent insertion order
  rather than sorting the full archive for every snapshot.
- Client refresh is distinguished from initial open: it updates an existing
  matching screen but cannot reopen a closed one. Wrapping is cached, titles
  clipped and tabs wrap at narrow widths. Actual visual usability is manual.
- Active/latest-kind indexes remove historical plans from frequent work and
  acquisition lookups. The domain remains append-only; these transient indexes
  rebuild after load. Histories are preserved, not artificially truncated.
- Unloaded/removed villages release terrain indexes; unloaded centers do not
  run strategic scans. Scan cursor arithmetic is long-lived. Identical worker
  reassignment after an unchanged blocker no longer emits another assignment
  event. Existing retry delays remain bounded.
- A reachable approach position could fall just outside the old container
  arrival radius. The three-block check now includes legal approaches while
  retaining visibility. Pen flood-fill validates adjacent chunk availability.
- The server tick entry checks its thread; no asynchronous world mutation path
  was introduced. Client screen classes remain distribution-restricted.

Evidence and limits:

- Two separate server JVMs preserved village/citizen/plan/building IDs, earned
  XP/skills, history, dependency/blocker metadata and exact remaining materials;
  a partially built second house completed once after loading real entity files.
- The 48,000-tick soak runs 36 villagers across three villages, verifies real
  unloading and isolation, and requires actual construction and forestry/farming.
  It measures job-call cost and work budgets, not complete server MSPT or client
  FPS. The fixture holds daylight fixed and supplies controlled materials.
- Permanent history and plans still grow with actual activity and are serialized
  as one document. This is retained world history rather than an orphan runtime
  cache, but very long saves still need archival design/profiling. That existing
  backlog is unchanged; no v1.1 gameplay was added.
- M1–M3 in MANUAL_ACCEPTANCE.md remain the visual, ordinary generated-world and
  longer survival gates. No manual pass or stable-release claim is made.
