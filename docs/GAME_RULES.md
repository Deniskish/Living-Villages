# Living Villages — Fundamental Game Rules

These rules define the identity of the mod.

They should not be broken merely to simplify implementation.

## Rule 1 — Nothing important appears from nowhere

Resources must have a source.

Examples:

Wood:
- tree;
- harvested logs;
- citizen inventory;
- village storage.

Iron:
- mined ore;
- loot;
- trade;
- existing stored iron.

A plan may reserve resources but reservation does not create them.

## Rule 2 — Citizens are physical entities

When loaded, a citizen must normally:
- walk;
- navigate;
- carry items;
- interact with blocks;
- perform work over time.

Do not reduce loaded-world gameplay to invisible counters.

## Rule 3 — Buildings are constructed

A building is not purchased and instantly spawned.

Required process:
1. village identifies need;
2. building plan is created;
3. suitable blueprint is selected;
4. site is evaluated;
5. materials are calculated;
6. dependencies are created;
7. builder receives materials;
8. blocks are placed progressively;
9. construction is validated;
10. building becomes operational.

## Rule 4 — Every decision should have a reason

The system must be able to answer questions such as:
- Why are we building a house?
- Why is the house not being built?
- Why was Hans selected as builder?
- Why does the village need more food?
- Why is a task waiting?
- Why was this site rejected?
- Which dependency blocks this plan?

Debug information is part of the product.

## Rule 5 — Failure is gameplay

A village is allowed to fail.

Examples:
- insufficient food;
- insufficient wood;
- no qualified worker;
- worker death;
- blocked route;
- unsuitable terrain;
- missing tool;
- construction damaged;
- resource shortage.

Do not silently fix every problem behind the scenes.

Failures should produce understandable consequences.

## Rule 6 — Citizens develop individually

Citizens are persistent individuals.

A citizen may have:
- name;
- UUID;
- age;
- home;
- family relationships;
- role;
- role XP;
- role level;
- skills;
- equipment;
- current task;
- lifetime statistics;
- personal history.

A skilled citizen dying should matter.

## Rule 7 — Profession progression matters

Roles have five levels.

Level should influence real capabilities rather than only numerical bonuses.

Example miner progression:

Level I:
- stone;
- coal.

Level II:
- copper;
- safer/deeper mining.

Level III:
- iron.

Level IV:
- gold;
- redstone;
- lapis.

Level V:
- diamond access when village progression later permits it.

Exact balancing may change, but capability progression must remain.

## Rule 8 — Village development emerges from systems

Do not treat village level as a button that magically unlocks everything.

Development must depend on real conditions such as:
- population;
- housing;
- food;
- qualified professions;
- buildings;
- production;
- security;
- technology.

Future village levels should represent actual development.

## Rule 9 — History is permanent world memory

Important actions create events.

Examples:
- citizen joined village;
- profession acquired;
- profession level increased;
- tool upgraded;
- building started;
- building completed;
- citizen born;
- citizen died;
- task failed;
- major resource milestone;
- future political/military events.

History should reference concrete actors whenever possible.

## Rule 10 — Plans are visible

The player must be able to distinguish:
- completed history;
- current plans;
- citizen tasks;
- blocked work.

These are not the same system.

## Rule 11 — Vanilla first

Prefer vanilla:
- blocks;
- tools;
- armor;
- mobs;
- animals;
- crops;
- recipes;
- containers;
- villagers;
- structures.

Add custom content only when interaction/diagnostics require it.

For v1.0, avoid adding new resource tiers or major item sets.

## Rule 12 — The player is not required

A village should not require constant player orders.

The player can observe.

The village should autonomously decide routine development.

Future versions may let a ruler influence strategic decisions.

## Rule 13 — Player modifications matter

If a player:
- destroys a road;
- steals resources;
- blocks a doorway;
- changes terrain;
- destroys part of a building;

the system should detect changed reality rather than assume its cached model is still true.

## Rule 14 — Safety before stupidity

Citizens should avoid obviously invalid actions.

Examples:
- building in deep water;
- building in mid-air;
- mining directly into known lava when a safe alternative exists;
- selecting an unreachable building site;
- repeatedly attempting the same impossible action forever.

Repeated failures must eventually become BLOCKED with diagnostics.

## Rule 15 — No infinite retry loops

Every task tracks:
- attempts;
- last progress time;
- failure reason;
- next retry time.

Repeated failure must use backoff.

After a threshold, escalate to BLOCKED or FAILED.

## Rule 16 — Simulation should be deterministic where useful

Planning/scoring should be reproducible enough to debug.

Randomness may provide variety, but important choices should expose:
- candidate scores;
- selected candidate;
- random seed/tie-break where relevant.

## Rule 17 — Debug mode is a first-class feature

Developer/admin tools are required.

Debug mode should expose:
- IDs;
- states;
- priorities;
- utility scores;
- dependencies;
- blockers;
- coordinates;
- worker assignment;
- required resources;
- available resources;
- last progress time.

Do not rely only on raw server logs.

## Rule 18 — The world must tell stories

Systems should combine into emergent events.

The objective is not merely efficient automation.

The objective is that a player can return to a village and understand:

> what happened here while I was living in this world?
