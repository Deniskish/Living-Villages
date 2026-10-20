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
