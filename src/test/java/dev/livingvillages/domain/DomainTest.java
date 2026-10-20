package dev.livingvillages.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.livingvillages.domain.Model.*;
import static org.junit.jupiter.api.Assertions.*;
class DomainTest {
    @Test void terminalOperationsAreAtomicAndCompletionIsIdempotent() {
        Plan p=new Plan();p.complete(42);
        String before=StateCodec.encode(worldWith(p));
        assertThrows(IllegalStateException.class,()->p.progress(500));
        assertThrows(IllegalStateException.class,()->p.block(Diagnostics.reason(Reason.UNKNOWN,"test",p,null,500,null,"",0,0),500,1));
        p.complete(600);
        assertEquals(before,StateCodec.encode(worldWith(p)));
    }
    private World worldWith(Plan p) {
        World w=new World();Village v=new Village();v.id=new UUID(0,1);v.plans.put(p.id,p);w.villages.put(v.id,v);return w;
    }
    @Test void terminalPlanReloadCannotRetainLiveWorkerOrReservations() {
        Plan p=new Plan();Task t=new Task();t.worker=UUID.randomUUID();p.tasks.add(t);
        p.reservations.put("oak",10);p.complete(5);
        Plan restored=StateCodec.decode(StateCodec.encode(worldWith(p))).villages.values().iterator().next().plans.get(p.id);
        assertEquals(State.CANCELLED,restored.tasks.getFirst().state());
        assertNull(restored.tasks.getFirst().worker);assertTrue(restored.reservations.isEmpty());
    }
    @Test void activeIndexExcludesLargeTerminalArchiveAndCompletedWork() {
        Village v=new Village();
        for(int i=0;i<10000;i++) { Plan p=new Plan();p.complete(i);v.plans.put(p.id,p); }
        Plan active=new Plan();v.plans.put(active.id,active);
        assertEquals(List.of(active),List.copyOf(v.activePlans()));
        active.complete(12000);assertTrue(v.activePlans().isEmpty());
        assertEquals(10001,v.plans.size(),"Archive is retained, not silently deleted");
    }
    @Test void experienceBoundariesAndMiningGates() {
        assertEquals(1,Rules.level(63));assertEquals(2,Rules.level(64));
        assertEquals(3,Rules.level(256));assertEquals(4,Rules.level(768));assertEquals(5,Rules.level(2048));
        assertEquals(1,Rules.miningLevel("minecraft:coal_ore"));
        assertEquals(2,Rules.miningLevel("minecraft:deepslate_copper_ore"));
        assertEquals(3,Rules.miningLevel("minecraft:iron_ore"));
        assertEquals(4,Rules.miningLevel("minecraft:redstone_ore"));
        assertEquals(Integer.MAX_VALUE,Rules.miningLevel("minecraft:diamond_ore"));
    }
    @Test void blockedRequiresExplanationAndTerminalWorkCannotRestart() {
        Plan p=new Plan();
        assertThrows(IllegalStateException.class,()->p.transition(State.BLOCKED));
        assertThrows(IllegalArgumentException.class,()->new Blocker(Reason.UNKNOWN,"",0,0,null,null,null,null,"",0,0,List.of()));
        p.complete(42);
        assertThrows(IllegalStateException.class,()->p.transition(State.IN_PROGRESS));
    }
    @Test void retriesEscalateButRemainRecoverableWithCappedDelay() {
        Plan p=new Plan();
        var blocker=Diagnostics.reason(Reason.MISSING_RESOURCE,"oak_planks required=64 available=19",p,null,100,null,"minecraft:oak_planks",64,19);
        p.block(blocker,100,3);assertEquals(State.WAITING,p.state());assertEquals(200,p.nextRetryTick);
        p.block(blocker,200,3);p.block(blocker,400,3);assertEquals(State.BLOCKED,p.state());
        assertTrue(Rules.backoff(30)<=6000);
        p.progress(1000);assertNull(p.blocker);assertEquals(0,p.attempts);assertEquals(State.IN_PROGRESS,p.state());
    }
    @Test void reservationsNeverOverbookAndStolenResourcesInvalidateClaims() {
        var stock=Map.of("oak",10);var demand=Map.of("oak",8);
        var first=Rules.reserve(stock,Map.of(),demand);
        var second=Rules.reserve(stock,first,demand);
        assertEquals(8,first.get("oak"));assertEquals(2,second.get("oak"));
        assertEquals(0,Rules.reserve(Map.of("oak",2),first,demand).get("oak"));
    }
    @Test void dependenciesExplainMissingAndCyclicReferences() {
        Plan parent=new Plan();parent.kind="BuildHouse";Plan child=new Plan();child.kind="GatherWood";
        parent.dependencies.add(child.id);child.dependencies.add(parent.id);
        var lines=Diagnostics.lines(Diagnostics.tree(parent,Map.of(parent.id,parent,child.id,child)));
        assertTrue(lines.stream().anyMatch(l->l.contains("Cyclic dependency")));
        lines=Diagnostics.lines(Diagnostics.tree(parent,Map.of(parent.id,parent)));
        assertTrue(lines.stream().anyMatch(l->l.contains("does not exist")));
    }
    @Test void utilityIsNormalizedAndFeasibilityMatters() {
        assertEquals(0,Rules.goal(Need.HOUSING,1,0,1).utility());
        assertEquals(0.4,Rules.goal(Need.WOOD,0.8,0.5,1).utility(),0.0001);
        assertEquals(1,Rules.shortage(-10,5));assertEquals(0,Rules.shortage(20,10));
    }
    @Test void sitesRejectWaterSlopeCollisionAndUnsupportedTerrain() {
        assertEquals("WATER",Rules.siteRejection(true,false,true,0,true,true));
        assertEquals("SLOPE",Rules.siteRejection(true,false,false,1,true,true));
        assertEquals("COLLISION",Rules.siteRejection(true,true,false,0,true,true));
        assertEquals("SUPPORT",Rules.siteRejection(true,false,false,0,false,true));
        assertTrue(Rules.siteScore(25,25)>Rules.siteScore(100,100));
    }
    @Test void roundTripPreservesIdentityProgressDiagnosticsAndHistory() {
        World world=new World();Village v=new Village();v.name="Oak";v.center=new Pos(1,64,2);world.villages.put(v.id,v);
        Citizen c=new Citizen();c.id=UUID.randomUUID();c.village=v.id;c.name="Anna";c.role=Role.BUILDER;
        c.experience.put(c.role,768L);v.citizens.put(c.id,c);
        Plan p=new Plan();p.kind="BUILD_HOUSE";p.village=v.id;p.site=new Pos(8,64,8);p.progress=19;v.plans.put(p.id,p);
        Task t=new Task();t.worker=c.id;t.plan=p.id;t.role=c.role;t.progress=19;p.tasks.add(t);c.currentTask=t.id;
        p.block(Diagnostics.reason(Reason.MISSING_RESOURCE,"Need planks",p,t,123,p.site,"minecraft:oak_planks",10,2),123,1);
        v.history.add(new Event(UUID.randomUUID(),120,"BUILDING_STARTED",v.id,c.id,p.id,"House started",Map.of()));
        World copy=StateCodec.decode(StateCodec.encode(world));
        Village restored=copy.villages.get(v.id);
        assertEquals(c.name,restored.citizens.get(c.id).name);assertEquals(4,restored.citizens.get(c.id).level());
        assertEquals(19,restored.plans.get(p.id).tasks.getFirst().progress);
        assertEquals(p.site,restored.plans.get(p.id).site);
        assertEquals(p.blocker,restored.plans.get(p.id).blocker);
        assertEquals(v.history,restored.history);
    }
    @Test void futureSaveVersionsAreNeverSilentlyReset() {
        assertThrows(IllegalArgumentException.class,()->StateCodec.decode("{\"version\":99,\"villages\":{}}"));
    }
    @Test void reloadRepairsDuplicateAssignmentsAndMissingWorkersWithoutDuplicatingCargo() {
        World world=new World();Village v=new Village();v.center=new Pos(0,64,0);world.villages.put(v.id,v);
        Citizen citizen=new Citizen();citizen.id=UUID.randomUUID();citizen.village=v.id;v.citizens.put(citizen.id,citizen);
        Plan plan=new Plan();plan.village=v.id;v.plans.put(plan.id,plan);
        Task first=new Task();first.worker=citizen.id;plan.tasks.add(first);
        Task duplicate=new Task();duplicate.worker=citizen.id;plan.tasks.add(duplicate);
        Task missing=new Task();missing.worker=UUID.randomUUID();plan.tasks.add(missing);
        v.storage.add(v.center);v.storage.add(v.center);
        World restored=StateCodec.decode(StateCodec.encode(world));
        Village copy=restored.villages.get(v.id);
        assertEquals(first.id,copy.citizens.get(citizen.id).currentTask);
        assertEquals(1,copy.storage.size());
        var tasks=copy.plans.get(plan.id).tasks;
        for(int i=1;i<3;i++) {
            assertNull(tasks.get(i).worker);assertEquals(State.BLOCKED,tasks.get(i).state());
            assertEquals(Reason.WORKER_UNAVAILABLE,tasks.get(i).blocker.type());
        }
        assertEquals(3,restored.recoveryNotes.size());
    }
}
