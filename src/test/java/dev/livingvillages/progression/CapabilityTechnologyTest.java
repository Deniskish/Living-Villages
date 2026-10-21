package dev.livingvillages.progression;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityTechnologyTest {
    private static ProgressionPolicyTest.F facts() { return new ProgressionPolicyTest.F(); }
    @Test void capabilitiesCombineTierTechnologyAndPhysicalInfrastructure() {
        var f=facts();
        var smelting=CapabilityPolicy.status(VillageCapability.IRON_SMELTING,Tier.SETTLEMENT,f.build());
        assertTrue(smelting.unlocked());assertFalse(smelting.available());
        assertEquals(Reason.INFRASTRUCTURE_MISSING,smelting.reason());
        assertEquals("IRON_SMELTING unavailable: Furnace is missing",CapabilityPolicy.explain(smelting));
        f.furnace=true;
        assertTrue(CapabilityPolicy.status(VillageCapability.IRON_SMELTING,Tier.SETTLEMENT,f.build()).available());

        var tools=CapabilityPolicy.status(VillageCapability.IRON_TOOLSMITHING,Tier.SETTLEMENT,f.build());
        assertEquals(Reason.CAPABILITY_LOCKED,tools.reason());
        assertTrue(CapabilityPolicy.explain(tools).startsWith("IRON_TOOLSMITHING locked: Village tier II — Village required"),CapabilityPolicy.explain(tools));
        tools=CapabilityPolicy.status(VillageCapability.IRON_TOOLSMITHING,Tier.CITY,f.build());
        assertEquals("IRON_TOOLSMITHING unavailable: Smithy is missing",CapabilityPolicy.explain(tools),
                "A historical City still needs a physical smithy");
        f.buildings.put("SMITHY",1);
        assertTrue(CapabilityPolicy.status(VillageCapability.IRON_TOOLSMITHING,Tier.VILLAGE,f.build()).available());

        var mining=CapabilityPolicy.status(VillageCapability.ADVANCED_MINING,Tier.TOWN,f.build());
        assertTrue(CapabilityPolicy.explain(mining).startsWith("ADVANCED_MINING locked: Village tier IV — City required"),CapabilityPolicy.explain(mining));
        assertTrue(CapabilityPolicy.status(VillageCapability.ADVANCED_MINING,Tier.CITY,f.build()).available());
        var diamonds=CapabilityPolicy.status(VillageCapability.DIAMOND_MINING,Tier.CITY,f.build());
        assertEquals("DIAMOND_MINING locked: Technology Deep Prospecting required",CapabilityPolicy.explain(diamonds));
        f.technologies.add(Technologies.DEEP_PROSPECTING.id());
        assertFalse(CapabilityPolicy.status(VillageCapability.DIAMOND_MINING,Tier.TOWN,f.build()).unlocked(),"Technology alone is insufficient");
        assertTrue(CapabilityPolicy.status(VillageCapability.DIAMOND_MINING,Tier.CITY,f.build()).available());
        for(var basic:List.of(VillageCapability.BASIC_FORESTRY,VillageCapability.BASIC_FARMING,VillageCapability.BASIC_MINING,
                VillageCapability.BASIC_CONSTRUCTION,VillageCapability.ANIMAL_HUSBANDRY))
            assertTrue(CapabilityPolicy.status(basic,Tier.SETTLEMENT,facts().build()).available(),basic+" is a v1.0 baseline");
        assertEquals(VillageCapability.values().length,CapabilityPolicy.all(Tier.CAPITAL,f.build()).size());
    }
    @Test void snapshotSeparatesUnlockedAndLockedCapabilities() {
        var s=ProgressionPolicy.evaluate(facts().build(),Tier.VILLAGE,Set.of(),5);
        assertTrue(s.unlockedCapabilities().stream().anyMatch(c->c.capability()==VillageCapability.TECHNOLOGY_RESEARCH));
        assertTrue(s.lockedCapabilities().stream().anyMatch(c->c.capability()==VillageCapability.DIAMOND_MINING));
        assertEquals(VillageCapability.values().length,s.capabilities().size());
        assertTrue(s.capability(VillageCapability.IRON_TOOLSMITHING).line().startsWith("[!] Iron toolsmithing — unavailable"));
        assertTrue(s.capability(VillageCapability.EXPANDED_TERRITORY).line().startsWith("[✗] "));
    }
    @Test void technologyPrerequisitesTierProfessionAndOrder() {
        var f=facts();f.best.put(Role.MINER,4);f.table=true;
        var deep=TechnologyPolicy.status(Technologies.DEEP_PROSPECTING,Tier.CITY,f.build(),false);
        assertEquals(TechnologyStatus.Stage.LOCKED,deep.stage());
        assertTrue(deep.explanation().contains("Technology Charcoal Burning required"),deep.explanation());
        f.technologies.add(Technologies.CHARCOAL_BURNING.id());
        assertEquals(TechnologyStatus.Stage.AVAILABLE,TechnologyPolicy.status(Technologies.DEEP_PROSPECTING,Tier.CITY,f.build(),false).stage());
        var lowTier=TechnologyPolicy.status(Technologies.DEEP_PROSPECTING,Tier.TOWN,f.build(),false);
        assertEquals(TechnologyStatus.Stage.LOCKED,lowTier.stage());assertTrue(lowTier.explanation().contains("Village tier IV — City required"));
        f.best.put(Role.MINER,3);
        assertTrue(TechnologyPolicy.status(Technologies.DEEP_PROSPECTING,Tier.CITY,f.build(),false).explanation().contains("Miner IV (best Miner: III)"));
        var g=facts();g.best.put(Role.SMITH,2);
        var charcoal=TechnologyPolicy.status(Technologies.CHARCOAL_BURNING,Tier.VILLAGE,g.build(),false);
        assertEquals(TechnologyStatus.Stage.AVAILABLE,charcoal.stage(),"A missing furnace is placed by the plan, not a start gate");
        assertTrue(charcoal.explanation().contains("Furnace is missing"));
        assertEquals(Optional.of(Technologies.CHARCOAL_BURNING),TechnologyPolicy.next(Tier.VILLAGE,g.build(),Set.of()));
        assertEquals(Optional.empty(),TechnologyPolicy.next(Tier.VILLAGE,g.build(),Set.of("x")),"One research plan at a time");
        assertEquals(Optional.empty(),TechnologyPolicy.next(Tier.SETTLEMENT,g.build(),Set.of()));
        assertEquals(TechnologyStatus.Stage.RESEARCHING,TechnologyPolicy.status(Technologies.CHARCOAL_BURNING,Tier.VILLAGE,g.build(),true).stage());
        g.technologies.add(Technologies.CHARCOAL_BURNING.id());
        assertEquals(TechnologyStatus.Stage.UNLOCKED,TechnologyPolicy.status(Technologies.CHARCOAL_BURNING,Tier.VILLAGE,g.build(),false).stage());
        var none=facts();
        assertTrue(TechnologyPolicy.status(Technologies.TIMBER_FRAMING,Tier.TOWN,none.build(),false).explanation().contains("nobody has worked as Builder"));
    }
    @Test void everyTechnologyHasPhysicalCostAndRealEffectInEachBranch() {
        for(TechnologyBranch branch:TechnologyBranch.values())
            assertTrue(Technologies.ALL.stream().anyMatch(t->t.branch()==branch),"Branch "+branch+" has a technology");
        Set<String> ids=new HashSet<>();
        for(Technology t:Technologies.ALL) {
            assertTrue(ids.add(t.id()),"Stable unique id");
            assertFalse(t.unlocks().isEmpty(),t.id()+" unlocks a capability");
            assertFalse(t.costs().isEmpty());
            assertTrue(t.costs().size()<=3&&t.costs().values().stream().allMatch(n->n<=64),"Cost fits a villager inventory");
            assertTrue(t.tier().atLeast(VillageCapability.TECHNOLOGY_RESEARCH.tier()));
            for(String prerequisite:t.prerequisites())assertTrue(Technologies.get(prerequisite).isPresent());
            assertEquals(Optional.of(t),Technologies.ofPlanKind(t.planKind()));
        }
        var costs=TechnologyPolicy.costs(Technologies.TIMBER_FRAMING,"spruce",Map.of("minecraft:spruce_log",30,"minecraft:spruce_planks",4));
        assertEquals(List.of("[✓] minecraft:spruce_log 24 / 24","[✗] minecraft:spruce_planks 4 / 32"),costs.stream().map(Requirement::line).toList());
    }
    @Test void technologyStateAndResearchPlanSurviveSerialization() {
        World w=new World();Village v=new Village();v.center=new Pos(0,64,0);w.villages.put(v.id,v);
        UUID researcher=UUID.randomUUID();
        v.technologies.put(Technologies.CHARCOAL_BURNING.id(),new TechnologyRecord(Technologies.CHARCOAL_BURNING.id(),4800,UUID.randomUUID(),researcher));
        v.tier=Tier.TOWN;v.tierHistory.put("VILLAGE",100L);v.tierHistory.put("TOWN",900L);
        v.production.put("minecraft:iron_ingot",9L);
        Plan p=new Plan();p.kind=Technologies.TIMBER_FRAMING.planKind();p.technology=Technologies.TIMBER_FRAMING.id();p.palette="birch";
        p.requirements=new TreeMap<>(Palettes.resolve(Technologies.TIMBER_FRAMING.costs(),"birch"));p.village=v.id;
        Task t=new Task();t.role=Role.BUILDER;t.minimumLevel=3;t.progress=5;t.plan=p.id;p.tasks.add(t);p.progress=5;
        v.plans.put(p.id,p);
        Village copy=StateCodec.decode(StateCodec.encode(w)).villages.get(v.id);
        assertEquals(v.technologies,copy.technologies);
        assertEquals(Tier.TOWN,copy.tier);assertEquals(v.tierHistory,copy.tierHistory);assertEquals(9L,copy.production.get("minecraft:iron_ingot"));
        Plan restored=copy.plans.get(p.id);
        assertEquals(p.technology,restored.technology);assertEquals("birch",restored.palette);
        assertEquals(Map.of("minecraft:birch_log",24,"minecraft:birch_planks",32),restored.requirements);
        assertEquals(5,restored.tasks.getFirst().progress);assertEquals(3,restored.tasks.getFirst().minimumLevel);
    }
}
