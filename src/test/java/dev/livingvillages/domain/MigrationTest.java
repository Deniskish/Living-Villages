package dev.livingvillages.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.livingvillages.domain.Model.*;
import static org.junit.jupiter.api.Assertions.*;

/** A literal v1.0 document (Gson output of the v1.0 schema, nulls omitted) must migrate losslessly. */
class MigrationTest {
    static final String VILLAGE="0f0e0d0c-0000-4000-8000-000000000001", CITIZEN="0f0e0d0c-0000-4000-8000-000000000002",
            PLAN="0f0e0d0c-0000-4000-8000-000000000003", TASK="0f0e0d0c-0000-4000-8000-000000000004",
            BUILDING="0f0e0d0c-0000-4000-8000-000000000005", EVENT="0f0e0d0c-0000-4000-8000-000000000006",
            CRAFT="0f0e0d0c-0000-4000-8000-000000000007", GATHER="0f0e0d0c-0000-4000-8000-000000000008";
    static final String V1="""
        {"version":1,"villages":{"%1$s":{"id":"%1$s","name":"Village 12 / 34","center":{"x":12,"y":64,"z":34},"radius":48,
         "createdAt":1000,"lastPlanTick":52000,
         "citizens":{"%2$s":{"id":"%2$s","village":"%1$s","currentTask":"%4$s","name":"Hans 0f0e","role":"BUILDER",
           "experience":{"BUILDER":800,"MINER":70},"skills":{"BUILDING":800,"MINING":70},"statistics":{"successfulWork":870,"itemsDelivered":64},
           "history":["%6$s"],"home":{"x":10,"y":65,"z":30},"equipment":"1 stone_axe","activity":"PLACE","registeredAt":1000,"ageTicks":0,"dead":false}},
         "plans":{
          "%3$s":{"village":"%1$s","need":"HOUSING","kind":"BUILD_HOUSE","blueprint":"house_t1_02",
           "goal":{"need":"HOUSING","urgency":0.9,"feasibility":0.9,"cooldown":1.0,"utility":0.81,"explanation":"urgency × feasibility × cooldown"},
           "priority":0.81,"totalWork":104,"site":{"x":20,"y":64,"z":40},
           "tasks":[{"plan":"%3$s","worker":"%2$s","lastAssignedWorker":"%2$s","role":"BUILDER","phase":"SELECT","item":"","desiredResource":"",
             "scanCursor":3,"targets":[],"id":"%4$s","state":"IN_PROGRESS","createdAt":2000,"lastProgressTick":51000,"nextRetryTick":0,
             "attemptStartedAt":50000,"attempts":0,"progress":37,"dependencies":[]}],
           "requirements":{"minecraft:oak_planks":40},"reservations":{"minecraft:oak_planks":12},"siteRejections":{"SLOPE":3},
           "id":"%3$s","state":"IN_PROGRESS","createdAt":2000,"lastProgressTick":51000,"nextRetryTick":0,"attemptStartedAt":0,"attempts":0,"progress":37,"dependencies":[]},
          "%7$s":{"village":"%1$s","need":"TOOLS","kind":"CRAFT:minecraft:stick","priority":0.4,"totalWork":0,"tasks":[],
           "requirements":{"minecraft:oak_planks":2},"reservations":{},"siteRejections":{},
           "id":"%7$s","state":"WAITING","blocker":{"type":"MISSING_RESOURCE","message":"minecraft:oak_planks required=2 available=0 reservedByOthers=0",
             "createdAt":3000,"lastCheckedAt":3100,"planId":"%7$s","resource":"minecraft:oak_planks","required":2,"available":0,"dependencies":[]},
           "createdAt":3000,"lastProgressTick":3000,"nextRetryTick":3200,"attemptStartedAt":0,"attempts":1,"progress":0,"dependencies":[]},
          "%8$s":{"village":"%1$s","need":"WOOD","kind":"GATHER_WOOD","priority":0.5,"totalWork":8,"tasks":[],"requirements":{},"reservations":{},"siteRejections":{},
           "id":"%8$s","state":"COMPLETED","createdAt":100,"lastProgressTick":900,"nextRetryTick":0,"attemptStartedAt":0,"attempts":0,"progress":8,"dependencies":[]}},
         "buildings":{"%5$s":{"id":"%5$s","village":"%1$s","plan":"%8$s","blueprint":"house_t1_01","category":"HOUSING","condition":"COMPLETE",
           "origin":{"x":30,"y":64,"z":40},"entrance":{"x":32,"y":65,"z":39},"completedAt":40000}},
         "storage":[{"x":13,"y":64,"z":35}],
         "history":[{"id":"%6$s","tick":40000,"type":"BUILDING_COMPLETED","village":"%1$s","actor":"%2$s","target":"%5$s","message":"house_t1_01","parameters":{}}],
         "needs":{"FOOD":0.25,"HOUSING":0.9},"goals":[],"beds":4}}}
        """.formatted(VILLAGE,CITIZEN,PLAN,TASK,BUILDING,EVENT,CRAFT,GATHER);
    @Test void v1SaveMigratesWithoutLosingIdentityProgressOrHistory() {
        World world=StateCodec.decode(V1);
        assertEquals(StateCodec.VERSION,world.version);
        assertFalse(world.migrationNotes.isEmpty());
        Village v=world.villages.get(UUID.fromString(VILLAGE));
        assertEquals("Village 12 / 34",v.name);assertEquals(new Pos(12,64,34),v.center);
        Citizen hans=v.citizens.get(UUID.fromString(CITIZEN));
        assertEquals("Hans 0f0e",hans.name);assertEquals(800L,hans.experience.get(Role.BUILDER));assertEquals(4,hans.level());
        assertEquals(70L,hans.skills.get("MINING"));assertEquals(UUID.fromString(TASK),hans.currentTask);
        Plan house=v.plans.get(UUID.fromString(PLAN));
        assertEquals(State.IN_PROGRESS,house.state());assertEquals(37,house.tasks.getFirst().progress);
        assertEquals(new Pos(20,64,40),house.site);assertEquals(Map.of("minecraft:oak_planks",12),house.reservations);
        assertEquals("oak",house.palette,"v1.0 construction continues with the oak it started with");
        assertEquals("oak",v.plans.get(UUID.fromString(CRAFT)).palette,"v1.0 recipes were oak");
        assertEquals(Reason.MISSING_RESOURCE,v.plans.get(UUID.fromString(CRAFT)).blocker.type());
        assertNull(v.plans.get(UUID.fromString(GATHER)).palette,"Non-material plans need no palette");
        Building b=v.buildings.get(UUID.fromString(BUILDING));
        assertEquals("oak",b.palette);assertEquals("house_t1_01",b.blueprint);assertEquals(40000,b.completedAt);
        assertEquals(1,v.history.size());assertEquals(UUID.fromString(EVENT),v.history.getFirst().id());
        assertEquals(List.of(UUID.fromString(EVENT)),hans.history);
        assertEquals(Tier.SETTLEMENT,v.tier);assertEquals(Map.of("SETTLEMENT",1000L),v.tierHistory);
        assertTrue(v.production.isEmpty()&&v.technologies.isEmpty());
        assertEquals(4,v.beds);assertEquals(List.of(new Pos(13,64,35)),v.storage);
        // Re-encoding writes the new schema, which then loads without migration.
        World again=StateCodec.decode(StateCodec.encode(world));
        assertEquals(StateCodec.VERSION,again.version);assertTrue(again.migrationNotes.isEmpty());
        assertEquals(37,again.villages.get(v.id).plans.get(house.id).tasks.getFirst().progress);
        assertEquals("oak",again.villages.get(v.id).plans.get(house.id).palette);
    }
    @Test void unknownFutureVersionsAndMalformedDocumentsAreRejected() {
        var future=assertThrows(IllegalArgumentException.class,()->StateCodec.decode(V1.replace("\"version\":1","\"version\":"+(StateCodec.VERSION+1))));
        assertTrue(future.getMessage().contains("refusing to overwrite"));
        assertThrows(IllegalArgumentException.class,()->StateCodec.decode("{\"version\":0,\"villages\":{}}"));
        assertThrows(RuntimeException.class,()->StateCodec.decode("[]"));
        // A document without a version field predates the field default and is read as v1.0.
        assertEquals(StateCodec.VERSION,StateCodec.decode("{\"villages\":{}}").version);
    }
}
