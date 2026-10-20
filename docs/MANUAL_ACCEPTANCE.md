# v1.0 manual acceptance protocol

All sections below currently have status **MANUAL_REQUIRED**. Do not change a
status to PASS_MANUAL without recording date, tester, artifact hash, world seed,
coordinates, screenshots/logs and observed results.

Use Minecraft 1.21.1, Java 21 and NeoForge 21.1.262 with this development JAR on
client and server. Preserve a copy of the test world. Do not use a valuable
existing world as the first acceptance fixture. No v1.1 feature is involved.

## M1 — Graphical Ledger and server authority

1. Start `scripts/gradle.ps1 runClient`, create an ordinary generated world with
   cheats enabled for inspection, and locate a village. `/locate structure
   minecraft:village_plains` is a convenient reproducible starting point.
   Record seed, bell coordinates and `/livingvillages village list`.
2. Wait for registration, give yourself a vanilla Book with
   `/give @s minecraft:book`, hold it in the main hand and right-click the bell.
   Confirm the village name/UUID belongs to that bell. Close with Escape; reopen
   ten times. Repeat with the book in the offhand.
3. Select Overview, Citizens, Resources, Buildings, Plans and Chronicle.
   Scroll to both ends; select the first/last row, use Back, Refresh and Close.
   Empty Buildings/Chronicle lists must remain usable. Long plan requirements
   must wrap in details; a long village/title must not cover Refresh.
4. Resize the window and test GUI Scale 1–4 and Auto. Tabs should wrap onto
   additional rows, remain clickable and leave a scrollable content area.
   Confirm bottom controls do not select a content row accidentally.
5. As an operator, open Diagnostics and compare the same plan using
   `/livingvillages plan inspect <plan UUID>`. As a non-operator on a dedicated
   server, reopen the book: Diagnostics must be absent. Regular plan blockers
   must still be readable.
6. Keep a plan selected, Refresh, then immediately close. A late refresh response
   must not reopen the screen. Refresh an open screen: the current tab should
   remain selected; stale row details may return to its list.
7. While the screen is open, remove the book, move more than 12 blocks from all
   its village bells, or have another player break the bell. Refresh must not
   reveal fresh remote data or crash; an unavailable message is expected.
   Close/reopen at a valid bell with a Book to recover.
8. Repeat on a dedicated server with two clients. Remove/add items in a village
   chest from client B, then Refresh from client A. Changes must reflect the
   server's physical inventory. Closing a screen must not stop the simulation.
9. For a secondary bell in the same working boundary, check initial open and
   Refresh resolve the same village. For overlapping village boundaries, check
   each bell uses the nearest registered center (UUID tie-break on equal distance).

Evidence: screenshots of each tab and a long detail, relevant server log,
regular/operator comparison, close/late-refresh observation. Large-data
transport is additionally tested by `ledgerLargeAndEmptyDataRemainBounded`;
that server test is not evidence of visual usability.

## M2 — Full ordinary-world process restart

1. Use the village from M1. Remain nearby until work, profession XP, history and
   a real completed building exist, then wait for another active construction.
   If necessary, put legitimate building materials in a village chest; record
   exactly which supplies were introduced. Do not use instant building commands.
2. Use `/tick freeze` to capture a consistent checkpoint. Record VillageId,
   citizen UUID/name/role/XP/skills, plan IDs/states/dependencies/blockers,
   building IDs, storage coordinates and construction block count from Ledger
   and inspect commands. Record carried items with `/data get entity <UUID>
   Inventory`, held tools with `/data get entity <UUID> HandItems`, and chest
   contents with `/data get block <x> <y> <z> Items`.
3. Include a blocked plan: temporarily remove a required item or obstruct the
   work route, then unfreeze long enough for a structured blocker and backoff.
   Freeze and record again. Keep the missing items separately; do not duplicate
   them when restoring access.
4. Dedicated server: run `/save-all flush`, then `stop` in the server console.
   Wait for “All dimensions are saved” and complete process termination.
   Single-player: Save and Quit, then Quit Game; verify the Java game process
   has ended. Copy the stopped world as the checkpoint backup.
5. Start a **new process** with the same JAR and world. Re-enter the village,
   freeze promptly for inspection and compare all checkpoint fields.
   Elapsed time, retry-check timestamps and valid later work may advance;
   identities, archived events, XP and completed work must not reset.
6. Reconcile resources: unchanged stock + carried cargo must match the saved
   checkpoint, adjusted only for actual newly built blocks/recipes. Check that
   a placed bed/door consumed one item, not two, and a resumed recipe did not
   issue its output twice. Check no duplicate village/citizen/plan IDs appeared.
7. Unfreeze. Restore the obstruction/materials and observe construction resume
   to exactly one completion event and one new building registration.
8. Save/restart once more while a worker is carrying material away from a chest.
   Also test a loaded missing chest and a killed assigned worker before saving:
   invalid references must diagnose/recover, not silently restore inventory.

Evidence: checkpoint inventory/ID table, before/after screenshots, both process
logs, saved world copy, completion record and material reconciliation.

The automated `scripts/acceptance-restart.ps1` uses two real JVMs and normal
region/entity/SavedData files in a controlled flat GameTest world. It explicitly
drives its construction fixture and freezes unrelated planning; it does not
replace this ordinary generated-world, client-connected procedure.

## M3 — Unload, multiple villages and longer survival soak

1. Register at least three ordinary villages with ten or more citizens in total.
   Record their centers, UUIDs, citizen UUID sets, storage and active plan IDs.
   Use distinct labelled supplies in each village's chests to track ownership.
2. Observe at least one gathering, farming and construction job, plus one
   intentionally unreachable target. Record last progress, attempts and retry
   tick; the unchanged unreachable target must not retry every game tick.
3. Confirm `/forceload query` has no tickets retaining the settlement. Travel
   at least 1,000 blocks away from it, with no other player nearby, and wait
   60 seconds. Save/stop/restart while still away. Return and compare identity,
   assignments, cargo, construction and history. Repeat three times.
4. Keep multiple villages loaded using normal players (or explicitly documented
   test-only `/forceload` tickets). Run at least two hours at normal speed,
   including day/night, hostile threats, trading, rest and births.
5. Every 15 minutes record citizens, entities, active/total plans, history size,
   container contents, blocked reasons and TPS/MSPT. Use vanilla `/debug start`
   and `/debug stop` for timed profiles. Enable Living Villages debug logging
   only for short samples; disable it afterwards.
6. Check IDs, workers, storage and actor history stay in their village. Ensure
   active identical kinds are unique. Total archived plans/events may grow with
   meaningful activity; unchanged idle failures should not create new plans or
   assignment events continuously.
7. Remove a warehouse, obstruct one site, kill one worker and reopen a previously
   blocked route. Confirm cargo conservation and eventual recovery/diagnosis.
   Verify no chunk is force-loaded by the production mod itself.

Evidence: time series, profiles, two-hour logs and observed exceptions. The
automated 48,000-tick soak runs controlled flat plots at accelerated GameTest
speed, with daylight/random crop growth conditions fixed by the harness. It
does not establish normal-client frame rate, hostile-world balance or unlimited
settlement scale.

## Recording results

For each procedure record `PASS_MANUAL`, `PARTIAL` or `FAIL`, the exact failing
step and reproduction. Preserve failed evidence. A changed JAR requires rerunning
affected steps. Keep `1.0.0-dev` while any required graphical or ordinary-world
restart acceptance remains unperformed.
