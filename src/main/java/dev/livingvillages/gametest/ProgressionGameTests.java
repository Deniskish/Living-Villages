package dev.livingvillages.gametest;

import dev.livingvillages.ai.VillagePlanner;
import dev.livingvillages.building.*;
import dev.livingvillages.client.LedgerLayout;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Model.Tier;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.network.LedgerView;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.progression.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.task.*;
import dev.livingvillages.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

/**
 * v1.1 progression, technology, palette and diamond scenarios. Fixtures create the physical
 * world (villagers, beds, chests, trees, crops, ore) and, where stated, persisted history
 * that a saved village would already have. Tier changes, unlocks and production are only
 * produced by the real simulation, jobs and policies under test.
 */
@GameTestHolder("livingvillages")
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private static final int REMOTE=12288, REMOTE_Y=80;

    // ---------------------------------------------------------------- natural tier II

    /** 1. Real autonomous work: houses, wood, food and professions raise the village to Tier II once. */
    @GameTest(template="empty",batch="progression_natural",timeoutTicks=40000,skyAccess=true)
    public static void villageNaturallyReachesTierTwoExactlyOnce(GameTestHelper h) {
        var level=h.getLevel();var data=VillageSavedData.get(level);
        BlockPos base=reserve(level,0,40,24);Natural[] built={null};
        long[] reachedAt={-1};
        h.onEachTick(()->{
            if(built[0]==null) {
                if(!ticking(level,base,40,24))return;
                // Seven beds: below population + 2, so the village still has to build a house itself,
                // with headroom for a vanilla birth (babies register as citizens).
                built[0]=natural(level,base,0,6,6,7);
                h.assertTrue(built[0].village.tier==Tier.SETTLEMENT,"Starts as a settlement");return;
            }
            Natural fixture=built[0];Village v=fixture.village;
            if(reachedAt[0]<0&&h.getTick()>=38000) { fixture.close(level,data);h.fail("Tier II not reached: "+state(v)); }
            if(reachedAt[0]<0&&v.tier==Tier.VILLAGE)reachedAt[0]=h.getTick();
            if(reachedAt[0]<0||h.getTick()<reachedAt[0]+1000)return; // Five more strategic evaluations.
            try {
                var events=tierEvents(v);
                h.assertTrue(events.size()==1&&events.getFirst().parameters().get("tier").equals("VILLAGE"),"Exactly one Tier II event: "+events);
                h.assertTrue(v.tierHistory.containsKey("VILLAGE")&&v.tier==Tier.VILLAGE,"Milestone persisted");
                h.assertTrue(v.buildings.values().stream().anyMatch(b->b.category.equals("HOUSING")),"A house was built by the village");
                h.assertTrue(ProductionCategory.WOOD.total(v.production)>=16&&ProductionCategory.FOOD.total(v.production)>=16,"Real production: "+v.production);
                h.assertTrue(v.progression!=null&&v.progression.capability(VillageCapability.TECHNOLOGY_RESEARCH).available(),"Tier II capability unlocked");
                // Reload: the historical tier and its single milestone survive.
                Village copy=StateCodec.decode(StateCodec.encode(worldOf(v))).villages.get(v.id);
                h.assertTrue(copy.tier==Tier.VILLAGE&&tierEvents(copy).size()==1,"Tier survives save/load without duplicate");
            } finally { fixture.close(level,data); }
            h.succeed();
        });
    }
    /** 2. Everything except wood production is met: no level-up and a structured, explained blocker. */
    @GameTest(template="empty",batch="progression_natural",timeoutTicks=40000,skyAccess=true)
    public static void incompleteRequirementsDoNotLevelUp(GameTestHelper h) {
        var level=h.getLevel();var data=VillageSavedData.get(level);
        BlockPos base=reserve(level,1,40,24);Natural[] built={null};long[] stableAt={-1};
        h.onEachTick(()->{
            if(built[0]==null) {
                if(ticking(level,base,40,24))built[0]=natural(level,base,1,6,4,6); // Four trees: at most 12 logs.
                return;
            }
            Natural fixture=built[0];Village v=fixture.village;
            var s=v.progression;
            boolean settled=s!=null&&v.buildings.values().stream().anyMatch(b->b.category.equals("HOUSING"))
                    &&ProductionCategory.WOOD.total(v.production)==12&&ProductionCategory.FOOD.total(v.production)>=16;
            if(stableAt[0]<0&&settled)stableAt[0]=h.getTick();
            if(stableAt[0]<0&&h.getTick()>=38000) { fixture.close(level,data);h.fail("Fixture did not settle: "+state(v)); }
            if(stableAt[0]<0||h.getTick()<stableAt[0]+1000)return;
            try {
                h.assertTrue(tierEvents(v).isEmpty()&&v.tier==Tier.SETTLEMENT,"No level-up without wood production");
                var wood=v.progression.missingRequirements().stream().filter(r->r.key().equals("PRODUCTION:WOOD")).findFirst();
                h.assertTrue(wood.isPresent()&&wood.get().required()==16&&wood.get().actual()==12&&!wood.get().fulfilled(),
                        "Machine-readable blocker: "+v.progression.missingRequirements());
                h.assertTrue(v.progression.requirements().stream().filter(r->r.type()==RequirementType.POPULATION||r.type()==RequirementType.STORAGE
                        ||r.key().equals("BUILDING:HOUSING")||r.key().equals("PRODUCTION:FOOD")).allMatch(Requirement::fulfilled),"Other facts are met");
                var lines=Diagnostics.lines(ProgressionDiagnostics.tier(v.progression));
                h.assertTrue(lines.stream().anyMatch(l->l.contains("REQUIREMENT_UNMET")&&l.contains("Wood harvested 12 / 16")),"Why not Tier II: "+lines);
                var ledger=LedgerView.create(level,v,false).tabs().get("Progression");
                h.assertTrue(ledger.stream().anyMatch(r->r.title().contains("[✗] Wood harvested 12 / 16")),"Ledger shows the missing requirement");
                h.assertTrue(v.plans.values().stream().anyMatch(p->p.kind.startsWith("GATHER_WOOD")&&p.blocker!=null),"Wood work explains its own blocker");
            } finally { fixture.close(level,data); }
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- tier III by production

    /** 3. A historical Tier II village smelts real iron; the eighth ingot raises it to Tier III once. */
    @GameTest(template="empty",timeoutTicks=12000,skyAccess=true)
    public static void villageReachesTierThreeThroughPhysicalIronProduction(GameTestHelper h) {
        var level=h.getLevel();BlockPos base=reserve(level,2,48,20);
        Runnable[] driver={null};
        h.onEachTick(()->{
            if(driver[0]!=null) { driver[0].run();return; }
            if(ticking(level,base,48,20))driver[0]=townFixture(h,level,base);
        });
    }
    /** Builds the Tier III fixture once its chunks tick and returns the per-tick smelting driver. */
    private static Runnable townFixture(GameTestHelper h,ServerLevel level,BlockPos base) {
        prepare(level,base,48,20);
        Village v=village(level,base.offset(24,1,10),24);v.tier=Tier.VILLAGE;v.tierHistory.put("VILLAGE",level.getGameTime()-24000);
        // Persisted record of earlier wood/food production (the village already reached Tier II).
        v.production.put("minecraft:oak_log",64L);v.production.put("minecraft:wheat",64L);
        var blueprints=new Blueprints();
        build(level,v,blueprints,"house_t1_01","oak",base.offset(2,1,2));
        build(level,v,blueprints,"house_t1_02","oak",base.offset(10,1,2));
        build(level,v,blueprints,"house_t1_03","oak",base.offset(18,1,2));
        build(level,v,blueprints,"storage_t1","oak",base.offset(27,1,2));
        build(level,v,blueprints,"smithy_t1","oak",base.offset(35,1,2));
        for(int i=0;i<6;i++)bed(level,base.offset(4+i*2,1,15));
        // North of the buildings: the incremental index meets this furnace before the smithy's.
        BlockPos chest=base.offset(20,1,1),furnace=base.offset(23,1,1);
        level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);level.setBlock(furnace,Blocks.FURNACE.defaultBlockState(),3);
        var container=Inventories.container(level,of(chest));
        container.setItem(0,new ItemStack(Items.RAW_IRON,8));container.setItem(1,new ItemStack(Items.COAL,8));
        container.setItem(2,new ItemStack(Items.BREAD,5));
        List<Villager> workers=new ArrayList<>();
        Role[] roles={Role.SMITH,Role.MINER,Role.LUMBERJACK,Role.FARMER,Role.BUILDER,Role.ANIMAL_KEEPER,Role.FARMER,Role.LUMBERJACK,Role.BUILDER,Role.MINER};
        for(int i=0;i<10;i++)workers.add(villager(level,v,base.offset(20+i%5,1,13+i/5),roles[i],"Town "+i,roles[i]==Role.SMITH||roles[i]==Role.MINER?64:8));
        Observations observed=survey(level,v);
        var chronicle=new Chronicle();var progression=new ProgressionService(chronicle);
        var initial=progression.evaluate(level,v,observed,stockWithCargo(level,v));
        h.assertTrue(v.tier==Tier.VILLAGE,"Not yet a town");
        h.assertTrue(initial.missingRequirements().stream().map(Requirement::key).toList().equals(List.of("PRODUCTION:IRON")),
                "Only iron production is missing: "+initial.missingRequirements());
        Citizen smith=v.citizens.get(workers.getFirst().getUUID());
        WorkContext[] current={smelt(level,v,smith,workers.getFirst(),observed,chronicle)};
        int[] cycles={0};
        return ()->{
            if(h.getTick()%10!=0)return;
            step(current[0],new SmeltingJob());
            if(current[0].plan().state()!=State.COMPLETED)return;
            cycles[0]++;
            var snapshot=progression.evaluate(level,v,observed,stockWithCargo(level,v));
            if(cycles[0]<8) {
                h.assertTrue(v.tier==Tier.VILLAGE&&ProductionCategory.IRON.total(v.production)==cycles[0],"Ingot "+cycles[0]+" counted once");
                current[0]=smelt(level,v,smith,workers.getFirst(),observed,chronicle);return;
            }
            try {
                h.assertTrue(v.tier==Tier.TOWN&&snapshot.highestUnlockedTier()==Tier.TOWN,"Eighth ingot reaches Tier III");
                var stock=stockWithCargo(level,v);
                h.assertTrue(stock.getOrDefault("minecraft:iron_ingot",0)==8&&!stock.containsKey("minecraft:raw_iron")
                        &&!stock.containsKey("minecraft:coal"),"Physical inputs consumed, outputs delivered: "+stock);
                progression.evaluate(level,v,observed,stockWithCargo(level,v));
                var events=tierEvents(v);
                h.assertTrue(events.size()==1&&events.getFirst().parameters().get("tier").equals("TOWN")
                        &&events.getFirst().parameters().get("previous").equals("VILLAGE"),"One Town event: "+events);
                h.assertTrue(snapshot.nextTier()==Tier.CITY&&!snapshot.missingRequirements().isEmpty(),"Next tier explained");
            } finally { close(level,base,48,20,v); }
            h.succeed();
        };
    }

    // ---------------------------------------------------------------- technology

    /** 4. Real stock -> research plan -> reservation -> qualified worker -> work -> consumption -> unlock. */
    @GameTest(template="empty",timeoutTicks=4000)
    public static void technologyReservesConsumesAndUnlocksOnce(GameTestHelper h) {
        Research r=research(h,20,8);
        var plan=r.plan;
        h.assertTrue(plan.requirements.equals(Map.of("minecraft:oak_log",16,"minecraft:cobblestone",8)),"Cost resolved with the local oak palette: "+plan.requirements);
        h.assertTrue(plan.reservations.equals(plan.requirements),"Real stock reserved for research: "+plan.reservations);
        Task task=plan.tasks.getFirst();
        Citizen apprentice=new Citizen();apprentice.experience.put(Role.SMITH,10L);
        h.assertTrue(!Simulation.qualified(apprentice,task)&&Simulation.qualified(r.smith,task),"Assignment requires Smith II");
        var c=assign(h,r);
        h.onEachTick(()->{if(h.getTick()%10==0)step(c,new ResearchJob());});
        h.runAtTickTime(200,()->h.assertTrue(!r.village.technologies.containsKey(Technologies.CHARCOAL_BURNING.id()),"Research takes time"));
        h.succeedWhen(()->{
            h.assertTrue(plan.state()==State.COMPLETED,"Research completed: "+describe(c));
            h.assertTrue(r.village.technologies.containsKey(Technologies.CHARCOAL_BURNING.id()),"Technology unlocked");
            h.assertTrue(Inventories.count(r.chest(),"minecraft:oak_log")==4&&Inventories.count(r.chest(),"minecraft:cobblestone")==0,"Exactly the cost left storage");
            h.assertTrue(Inventories.count(c.worker().getInventory(),"minecraft:oak_log")==0&&Inventories.count(c.worker().getInventory(),"minecraft:cobblestone")==0,"Cost consumed, not carried");
            h.assertTrue(unlocks(r.village).size()==1,"One unlock event");
            h.assertTrue(plan.progress==Technologies.CHARCOAL_BURNING.workUnits()&&plan.reservations.isEmpty(),"Progress complete, claims released");
            h.assertTrue(Capabilities.available(r.village,VillageCapability.CHARCOAL_BURNING,r.observed),"Unlock effect: charcoal burning available");
            var smelt=new Plan();smelt.kind="SMELT_CHARCOAL";smelt.palette="oak";Task t=new Task();t.item="minecraft:oak_log";smelt.tasks.add(t);
            h.assertTrue(SmeltingJob.recipe(smelt,t).output().equals("minecraft:charcoal"),"Charcoal production uses real logs");
        });
    }
    /** 5. Missing materials: plan blocks with explanation and dependencies; nothing is unlocked or consumed. */
    @GameTest(template="empty",timeoutTicks=3000)
    public static void technologyWithoutResourcesBlocksWithoutUnlock(GameTestHelper h) {
        Research r=research(h,4,0);
        var plan=r.plan;
        // Research depends on cobblestone; that raises MINE (and its pickaxe/plank chain) above research,
        // so a log may be claimed by that chain first. Claims never exceed physical stock.
        int logClaims=r.village.activePlans().stream().mapToInt(p->p.reservations.getOrDefault("minecraft:oak_log",0)).sum();
        h.assertTrue(logClaims==4&&plan.reservations.getOrDefault("minecraft:oak_log",0)>0
                &&r.village.activePlans().stream().allMatch(p->p.reservations.getOrDefault("minecraft:cobblestone",0)==0),
                "Only real stock can be reserved: research="+plan.reservations+" total log claims="+logClaims);
        h.assertTrue(plan.dependencies.stream().map(r.village.plans::get).filter(Objects::nonNull).map(p->p.kind).toList()
                .containsAll(List.of("GATHER_WOOD:minecraft:oak_log","MINE")),"Acquisition dependencies planned");
        var c=assign(h,r);
        h.onEachTick(()->{if(h.getTick()%10==0)step(c,new ResearchJob());});
        h.runAtTickTime(2500,()->{
            h.assertTrue(plan.state()==State.BLOCKED&&plan.blocker!=null&&plan.blocker.type()==Reason.MISSING_RESOURCE,"Blocked: "+plan.blocker);
            // Backoff 100,200,400,800,1600 ticks: five spaced attempts in 2,500 ticks, not one per step.
            h.assertTrue(plan.attempts<=6&&plan.nextRetryTick-h.getLevel().getGameTime()>=0,"Bounded retries with backoff: "+plan.attempts);
            h.assertTrue(r.village.technologies.isEmpty()&&unlocks(r.village).isEmpty(),"No fake unlock");
            h.assertTrue(Inventories.count(r.chest(),"minecraft:oak_log")==4,"Nothing consumed");
            var lines=Diagnostics.lines(Diagnostics.tree(plan,r.village.plans));
            h.assertTrue(lines.getFirst().contains("MISSING_RESOURCE")&&lines.stream().anyMatch(l->l.contains("GATHER_WOOD:minecraft:oak_log")),"Dependency tree: "+lines);
            var status=TechnologyPolicy.status(Technologies.CHARCOAL_BURNING,r.village.tier,Capabilities.facts(r.village,r.observed),true);
            h.assertTrue(status.stage()==TechnologyStatus.Stage.RESEARCHING,"Status reflects the active plan");
            h.succeed();
        });
    }
    /** 6. Save/load mid-research continues from the same unit without duplicating or losing items. */
    @GameTest(template="empty",timeoutTicks=4000)
    public static void technologyResumesAfterReloadWithoutDuplication(GameTestHelper h) {
        Research r=research(h,20,8);
        var current=new java.util.concurrent.atomic.AtomicReference<>(assign(h,r));
        boolean[] reloaded={false};
        h.onEachTick(()->{
            if(h.getTick()%10!=0)return;
            WorkContext c=current.get();
            step(c,new ResearchJob());
            if(reloaded[0]||c.task().progress<2)return;
            Map<String,Integer> before=physical(c);
            var saved=new VillageSavedData();saved.world.villages.put(r.village.id,r.village);
            var loaded=VillageSavedData.load(saved.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            Village v=loaded.world.villages.get(r.village.id);Plan p=v.plans.get(r.plan.id);
            h.assertTrue(p.technology.equals(r.plan.technology)&&p.palette.equals("oak")&&p.tasks.getFirst().progress==c.task().progress
                    &&p.tasks.getFirst().minimumLevel==2,"Research state restored");
            var restored=new WorkContext(h.getLevel(),v,p,p.tasks.getFirst(),v.citizens.get(r.smith.id),c.worker(),r.observed,new Chronicle());
            h.assertTrue(physical(restored).equals(before)&&before.get("minecraft:oak_log")==20&&before.get("minecraft:cobblestone")==8,
                    "Chest + cargo unchanged by reload: "+before);
            current.set(restored);reloaded[0]=true;
        });
        h.succeedWhen(()->{
            WorkContext c=current.get();
            h.assertTrue(reloaded[0]&&c.plan().state()==State.COMPLETED,"Completed after reload: "+describe(c));
            h.assertTrue(c.village().technologies.size()==1&&unlocks(c.village()).size()==1,"Unlocked exactly once");
            Map<String,Integer> after=physical(c);
            h.assertTrue(after.getOrDefault("minecraft:oak_log",0)==4&&after.getOrDefault("minecraft:cobblestone",0)==0,"Exactly one cost consumed: "+after);
        });
    }

    // ---------------------------------------------------------------- palettes

    /** 7. A spruce house keeps its palette through save/load, completes in spruce and inspects intact. */
    @GameTest(template="empty",timeoutTicks=15000)
    public static void buildingPaletteSurvivesSaveReload(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=standalone(h,Role.BUILDER,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        var blueprints=new Blueprints();var bp=blueprints.get("house_t1_02","spruce");
        h.assertTrue(bp.manifest().containsKey("minecraft:spruce_log")&&bp.manifest().containsKey("minecraft:spruce_door")
                &&!bp.manifest().containsKey("minecraft:oak_planks"),"Tokens resolved to spruce: "+bp.manifest());
        c.plan().blueprint=bp.id();c.plan().palette="spruce";c.plan().site=of(h.absolutePos(new BlockPos(6,1,6)));
        var chest=Inventories.container(h.getLevel(),c.village().storage.getFirst());
        bp.manifest().forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n)));
        Job job=new ConstructionJob(blueprints);
        var current=new java.util.concurrent.atomic.AtomicReference<>(c);boolean[] reloaded={false};
        h.onEachTick(()->{
            if(h.getTick()%10==0)step(current.get(),job);
            if(!reloaded[0]&&current.get().task().progress>=20) {
                var saved=new VillageSavedData();saved.world.villages.put(c.village().id,current.get().village());
                var loaded=VillageSavedData.load(saved.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
                Village v=loaded.world.villages.get(c.village().id);Plan p=v.plans.get(c.plan().id);
                h.assertTrue("spruce".equals(p.palette),"Palette persisted with the plan");
                current.set(new WorkContext(h.getLevel(),v,p,p.tasks.getFirst(),v.citizens.get(c.citizen().id),c.worker(),c.observations(),new Chronicle()));
                reloaded[0]=true;
            }
        });
        h.succeedWhen(()->{
            WorkContext r=current.get();
            h.assertTrue(reloaded[0]&&r.plan().state()==State.COMPLETED,"House completed after reload: "+describe(r));
            Building b=r.village().buildings.values().iterator().next();
            h.assertTrue("spruce".equals(b.palette),"Building records its palette");
            h.assertBlockPresent(Blocks.SPRUCE_LOG,new BlockPos(6,2,6));
            h.assertBlockPresent(Blocks.SPRUCE_PLANKS,new BlockPos(7,2,6));
            h.assertBlockPresent(Blocks.SPRUCE_DOOR,new BlockPos(8,2,6));
            new BuildingInspection(blueprints,new Chronicle()).inspect(h.getLevel(),r.village());
            h.assertTrue(b.condition.equals("COMPLETE"),"Inspection compares against spruce, not oak: "+b.condition);
            h.assertTrue(Inventories.stock(h.getLevel(),r.village()).isEmpty(),"Spruce manifest consumed exactly");
        });
    }
    /** 8. Two villages with different local trees choose different palettes; no state is shared. */
    @GameTest(template="empty",timeoutTicks=200,skyAccess=true)
    public static void twoVillagesChooseDifferentLocalPalettes(GameTestHelper h) {
        floor(h);
        tree(h.getLevel(),h.absolutePos(new BlockPos(1,1,1)),Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES);
        tree(h.getLevel(),h.absolutePos(new BlockPos(4,1,1)),Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES);
        tree(h.getLevel(),h.absolutePos(new BlockPos(14,1,14)),Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES);
        tree(h.getLevel(),h.absolutePos(new BlockPos(11,1,14)),Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES);
        var level=h.getLevel();var planner=new VillagePlanner(new Chronicle(),new Blueprints(),new ProgressionService(new Chronicle()));
        Village a=village(level,h.absolutePos(new BlockPos(3,1,3)),5),b=village(level,h.absolutePos(new BlockPos(12,1,12)),5);
        for(Village v:List.of(a,b)) {
            v.createdAt=level.getGameTime()-2000;
            villager(level,v,block(v.center).offset(0,0,1),Role.BUILDER,"Builder "+v.name,8);
        }
        h.runAfterDelay(2,()->{
            planner.tick(level,a,survey(level,a));planner.tick(level,b,survey(level,b));
            Plan houseA=a.active("BUILD_HOUSE").orElseThrow(),houseB=b.active("BUILD_HOUSE").orElseThrow();
            h.assertTrue(a.palette.equals("spruce")&&b.palette.equals("birch"),"Local palettes: "+a.paletteScores+" / "+b.paletteScores);
            h.assertTrue("spruce".equals(houseA.palette)&&"birch".equals(houseB.palette),"Plans persist their own palette");
            h.assertTrue(houseA.requirements.keySet().stream().anyMatch(k->k.startsWith("minecraft:spruce_"))
                    &&houseB.requirements.keySet().stream().anyMatch(k->k.startsWith("minecraft:birch_"))
                    &&houseA.requirements.keySet().stream().noneMatch(k->k.startsWith("minecraft:birch_")),"Manifests use local wood");
            h.assertTrue(!a.paletteScores.containsKey("birch")&&!b.paletteScores.containsKey("spruce"),"Scores come from each village's own index");
            h.assertTrue(Collections.disjoint(a.plans.keySet(),b.plans.keySet())&&a.plans.values().stream().allMatch(p->p.village.equals(a.id))
                    &&b.plans.values().stream().allMatch(p->p.village.equals(b.id)),"No shared plan state");
            houseA.palette="spruce";b.palette="oak";
            h.assertTrue("birch".equals(houseB.palette),"Changing one village does not affect the other's plan");
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- diamonds

    /** 9. Before DIAMOND_MINING, a fully equipped Miner V never targets exposed diamond ore. */
    @GameTest(template="empty",timeoutTicks=1500)
    public static void diamondMiningUnavailableBeforeCapability(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(9,1,8,Blocks.DIAMOND_ORE);h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=standalone(h,Role.MINER,new BlockPos(3,1,3));Village v=c.village();
        v.storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        v.tier=Tier.TOWN;c.citizen().experience.put(Role.MINER,2048L);
        c.worker().setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        Pos ore=of(h.absolutePos(new BlockPos(9,1,8)));c.observations().stone.add(ore);c.observations().diamonds.add(ore);
        var status=Capabilities.status(v,VillageCapability.DIAMOND_MINING,c.observations());
        h.assertTrue(CapabilityPolicy.explain(status).startsWith("DIAMOND_MINING locked: Village tier IV — City required"),CapabilityPolicy.explain(status));
        v.tier=Tier.CITY;
        h.assertTrue(CapabilityPolicy.explain(Capabilities.status(v,VillageCapability.DIAMOND_MINING,c.observations()))
                .equals("DIAMOND_MINING locked: Technology Deep Prospecting required"),"City alone is insufficient");
        var planner=new VillagePlanner(new Chronicle(),new Blueprints(),new ProgressionService(new Chronicle()));
        planner.tick(h.getLevel(),v,c.observations());
        h.assertTrue(v.active("MINE:minecraft:diamond").isEmpty(),"Planner creates no diamond plan while locked");
        c.task().desiredResource=HarvestJob.DIAMOND;
        new HarvestJob().tick(c);
        h.assertTrue(c.task().blocker!=null&&c.task().blocker.type()==Reason.CAPABILITY_LOCKED
                &&c.task().blocker.message().contains("Deep Prospecting"),"Diamond task explains the lock: "+c.task().blocker);
        // An ordinary stone task never picks the ore either.
        WorkContext stone=standalone(h,Role.MINER,new BlockPos(4,1,4));stone.citizen().experience.put(Role.MINER,2048L);
        stone.village().tier=Tier.CITY;stone.observations().stone.add(ore);
        stone.worker().setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        new HarvestJob().tick(stone);
        h.assertTrue(stone.task().target==null&&stone.task().blocker!=null,"Generic mining ignores diamonds");
        h.runAfterDelay(200,()->{ h.assertBlockPresent(Blocks.DIAMOND_ORE,new BlockPos(9,1,8));h.succeed(); });
    }
    /** 10. Tier IV + technology + Miner V + real iron pickaxe + reachable ore: then, and only then, a diamond is mined. */
    @GameTest(template="empty",timeoutTicks=6000)
    public static void diamondMiningRequiresEveryRealRequirement(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(9,1,8,Blocks.DIAMOND_ORE);h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=standalone(h,Role.MINER,new BlockPos(3,1,3));Village v=c.village();
        v.radius=12; // Registered: keep the boundary inside this test's own structure.
        var data=VillageSavedData.get(h.getLevel());
        v.lastPlanTick=Long.MAX_VALUE; // Registered only so real block drops are attributed; the fixture drives the job.
        v.plans.remove(c.plan().id);data.world.villages.put(v.id,v);
        v.storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        v.tier=Tier.CITY;v.technologies.put(Technologies.DEEP_PROSPECTING.id(),new TechnologyRecord(Technologies.DEEP_PROSPECTING.id(),0,null,null));
        Pos ore=of(h.absolutePos(new BlockPos(9,1,8)));c.observations().stone.add(ore);c.observations().diamonds.add(ore);
        c.task().desiredResource=HarvestJob.DIAMOND;c.task().minimumLevel=5;
        c.citizen().experience.put(Role.MINER,768L);
        h.assertTrue(!Simulation.qualified(c.citizen(),c.task()),"Miner IV is not assigned");
        new HarvestJob().tick(c);
        h.assertTrue(c.task().target==null&&c.task().blocker!=null&&c.task().blocker.type()==Reason.PATH_UNREACHABLE,"Miner IV cannot target diamond");
        c.citizen().experience.put(Role.MINER,2048L);
        h.assertTrue(Simulation.qualified(c.citizen(),c.task()),"Miner V qualifies");
        c.worker().getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        c.task().blocker=null;c.task().nextRetryTick=0;c.task().worker=c.citizen().id;c.citizen().currentTask=c.task().id;
        boolean[] provided={false};
        h.onEachTick(()->{
            if(h.getTick()%10==0)step(c,new HarvestJob());
            if(!provided[0]&&c.task().blocker!=null&&c.task().blocker.type()==Reason.MISSING_RESOURCE) {
                h.assertTrue(c.task().blocker.resource().equals("minecraft:iron_pickaxe"),"Stone pickaxe is not enough: "+c.task().blocker);
                h.assertBlockPresent(Blocks.DIAMOND_ORE,new BlockPos(9,1,8));
                Inventories.insert(Inventories.container(h.getLevel(),v.storage.getFirst()),new ItemStack(Items.IRON_PICKAXE));
                provided[0]=true;
            }
        });
        // succeedWhen polls every tick, so cleanup must not run on a failed poll.
        h.runAtTickTime(5999,()->{data.world.villages.remove(v.id);data.setDirty();});
        h.succeedWhen(()->{
            h.assertTrue(provided[0]&&c.plan().state()==State.COMPLETED,"Diamond mined and delivered: "+describe(c));
            h.assertBlockPresent(Blocks.AIR,new BlockPos(9,1,8));
            h.assertTrue(Inventories.count(Inventories.container(h.getLevel(),v.storage.getFirst()),"minecraft:diamond")==1,"Real loot delivered");
            h.assertTrue(v.production.getOrDefault("minecraft:diamond",0L)==1,"Physical production recorded: "+v.production);
            h.assertTrue(c.worker().getMainHandItem().is(Items.IRON_PICKAXE)&&c.worker().getMainHandItem().getDamageValue()==1,"Real tool used");
            h.assertTrue(v.history.stream().anyMatch(e->e.type().equals("PRODUCTION_MILESTONE")&&e.parameters().get("category").equals("DIAMOND")),"First diamond in history");
            data.world.villages.remove(v.id);data.setDirty();
        });
    }

    // ---------------------------------------------------------------- ledger

    /** 11. Progression data in the Ledger stays bounded and readable without colour; layout areas never overlap. */
    @GameTest(template="empty",timeoutTicks=200)
    public static void ledgerProgressionDataRemainsBounded(GameTestHelper h) {
        Village v=village(h.getLevel(),h.absolutePos(new BlockPos(8,1,8)),16);
        v.name="Exceedingly ".repeat(80)+"Long Village";v.tier=Tier.TOWN;v.tierHistory.put("TOWN",48000L);
        for(int i=0;i<40;i++)villager(h.getLevel(),v,h.absolutePos(new BlockPos(2+i%12,1,2+i/12)),Role.values()[i%6],"Citizen "+"x".repeat(300)+i,i*50);
        for(int i=0;i<600;i++) {
            Plan p=new Plan();p.kind="CRAFT:minecraft:stick "+"y".repeat(400);p.village=v.id;
            p.block(Diagnostics.reason(Reason.MISSING_RESOURCE,"m".repeat(1000),p,null,i,null,"minecraft:oak_planks",64,i),i,1);
            v.plans.put(p.id,p);
        }
        for(int i=0;i<6000;i++)v.history.add(new Event(UUID.randomUUID(),i,"PLAN_CREATED",v.id,null,null,"z".repeat(600),Map.of()));
        var view=LedgerView.create(h.getLevel(),v,true);
        String json=view.json();
        h.assertTrue(json.length()<=240000,"Payload budget: "+json.length());
        var parsed=LedgerView.parse(json);
        var rows=parsed.tabs().get("Progression");
        h.assertTrue(rows!=null&&!rows.isEmpty()&&rows.size()<=LedgerView.MAX_ROWS+1,"Progression tab bounded");
        h.assertTrue(rows.getFirst().title().equals("Village Tier: III — Town"),"Tier row: "+rows.getFirst().title());
        h.assertTrue(rows.stream().anyMatch(r->r.title().startsWith("Next: IV — City")),"Next tier row");
        h.assertTrue(rows.stream().anyMatch(r->r.title().contains("[✗] Population")||r.title().contains("[✓] Population")),"Requirement rows use text markers");
        h.assertTrue(rows.stream().anyMatch(r->r.title().contains("Iron toolsmithing")),"Capability rows");
        for(TechnologyBranch branch:TechnologyBranch.values())
            h.assertTrue(rows.stream().anyMatch(r->r.title().equals(branch.title())),"Branch "+branch);
        h.assertTrue(rows.stream().allMatch(r->r.title().length()<=LedgerView.MAX_TEXT&&r.detail().size()<=LedgerView.MAX_DETAIL),"Rows bounded");
        h.assertTrue(rows.stream().filter(r->r.key().startsWith("technology:")).count()==Technologies.ALL.size(),"Stable keys survive transport");
        h.assertTrue(parsed.tabs().get("Citizens").stream().allMatch(r->r.key().length()==36),"Citizen rows keep UUID keys");
        h.assertTrue(parsed.tabs().get("Diagnostics").getFirst().detail().stream().anyMatch(l->l.contains("REQUIREMENT_UNMET")),"Diagnostics tab explains next tier");
        // Layout of the eight tabs at the smallest Auto GUI size and a large one.
        for(int[] size:new int[][]{{320,240},{480,270},{960,540}}) {
            var layout=LedgerLayout.compute(size[0],size[1],LedgerView.TABS.stream().map(t->t.length()*6).toList());
            var areas=layout.areas();
            for(int i=0;i<areas.size();i++)for(int j=i+1;j<areas.size();j++)
                h.assertTrue(!areas.get(i).intersects(areas.get(j)),"No overlap at "+size[0]+"x"+size[1]);
            h.assertTrue(layout.visibleRows()>=LedgerLayout.MIN_CONTENT_ROWS,"Readable viewport");
        }
        h.succeed();
    }

    // ---------------------------------------------------------------- fixtures

    private record Natural(Village village,BlockPos base) {
        void close(ServerLevel level,VillageSavedData data) { ProgressionGameTests.close(level,base,40,24,village);data.setDirty(); }
    }
    /** Remote plot driven only by the production Simulation (planner, scheduler, jobs). */
    private static Natural natural(ServerLevel level,BlockPos base,int slot,int citizens,int trees,int beds) {
        level.setDayTime(1000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        prepare(level,base,40,24);
        Village v=village(level,base.offset(20,1,12),20);v.name="Natural "+slot;v.createdAt=level.getGameTime()-2000;
        BlockPos chest=base.offset(3,1,3);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);v.storage.add(of(chest));
        level.setBlock(base.offset(5,1,3),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        var container=Inventories.container(level,of(chest));
        new Blueprints().get("house_t1_01").manifest().forEach((id,n)->Inventories.insert(container,new ItemStack(Inventories.item(id),n)));
        for(var e:Map.of(Items.OAK_PLANKS,16,Items.BREAD,16,Items.WHEAT_SEEDS,16,Items.WOODEN_PICKAXE,2).entrySet())
            Inventories.insert(container,new ItemStack(e.getKey(),e.getValue()));
        for(int i=0;i<beds;i++)bed(level,base.offset(4+i*2,1,19));
        for(int i=0;i<trees;i++)tree(level,base.offset(27+(i%3)*4,1,3+(i/3)*4),Blocks.OAK_LOG,Blocks.OAK_LEAVES);
        for(int x=26;x<31;x++)for(int z=16;z<20;z++) {
            level.setBlock(base.offset(x,0,z),Blocks.FARMLAND.defaultBlockState(),3);
            level.setBlock(base.offset(x,1,z),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
        }
        for(int i=0;i<citizens;i++)villager(level,v,base.offset(8+i%3,1,8+i/3),Role.values()[i%Role.values().length],"Natural "+slot+"/"+i,0);
        // A timed-out earlier run never reaches its cleanup; its registered village would own
        // this plot (nearest center, UUID tie-break). GameTest has no teardown on timeout.
        var villages=VillageSavedData.get(level).world.villages;
        villages.values().removeIf(old->old.center.x()>=base.getX()&&old.center.x()<base.getX()+40
                &&old.center.z()>=base.getZ()&&old.center.z()<base.getZ()+24);
        villages.put(v.id,v);VillageSavedData.get(level).setDirty();
        return new Natural(v,base);
    }
    private record Research(Village village,Plan plan,Citizen smith,Villager worker,Observations observed,Pos chestPos,GameTestHelper h) {
        net.minecraft.world.Container chest() { return Inventories.container(h.getLevel(),chestPos); }
    }
    /** Historical Tier II village with a Smith II, a real furnace/table and the given physical stock. */
    private static Research research(GameTestHelper h,int logs,int cobblestone) {
        floor(h);h.setDayTime(1000);
        h.setBlock(2,1,3,Blocks.CHEST);h.setBlock(5,1,3,Blocks.FURNACE);h.setBlock(7,1,3,Blocks.CRAFTING_TABLE);
        var level=h.getLevel();
        Village v=village(level,h.absolutePos(new BlockPos(8,1,8)),12);v.tier=Tier.VILLAGE;v.tierHistory.put("VILLAGE",0L);
        Pos chest=of(h.absolutePos(new BlockPos(2,1,3)));v.storage.add(chest);
        var container=Inventories.container(level,chest);
        if(logs>0)container.setItem(0,new ItemStack(Items.OAK_LOG,logs));
        if(cobblestone>0)container.setItem(1,new ItemStack(Items.COBBLESTONE,cobblestone));
        Villager worker=h.spawn(EntityType.VILLAGER,new BlockPos(4,1,5));worker.setAge(0);
        Citizen smith=new Citizen();smith.id=worker.getUUID();smith.village=v.id;smith.name="Ada Smith";smith.role=Role.SMITH;
        smith.experience.put(Role.SMITH,64L);v.citizens.put(smith.id,smith);
        Observations observed=new Observations();
        observed.furnaces.add(of(h.absolutePos(new BlockPos(5,1,3))));observed.tables.add(of(h.absolutePos(new BlockPos(7,1,3))));
        new VillagePlanner(new Chronicle(),new Blueprints(),new ProgressionService(new Chronicle())).tick(level,v,observed);
        Plan plan=v.active(Technologies.CHARCOAL_BURNING.planKind()).orElseThrow(()->new GameTestAssertException("Planner created no research plan: "
                +v.activePlans().stream().map(p->p.kind).toList()+" goals="+v.goals));
        h.assertTrue(plan.need==Need.DEVELOPMENT&&plan.goal!=null&&plan.goal.need()==Need.DEVELOPMENT,"Village goal -> technology plan");
        return new Research(v,plan,smith,worker,observed,chest,h);
    }
    private static WorkContext assign(GameTestHelper h,Research r) {
        Task task=r.plan.tasks.getFirst();task.worker=r.smith.id;r.smith.currentTask=task.id;
        task.lastProgressTick=h.getLevel().getGameTime();task.attemptStartedAt=task.lastProgressTick;
        return new WorkContext(h.getLevel(),r.village,r.plan,task,r.smith,r.worker,r.observed,new Chronicle());
    }
    private static Map<String,Integer> physical(WorkContext c) {
        Map<String,Integer> result=new TreeMap<>(Inventories.stock(c.level(),c.village()));
        Inventories.addStock(c.worker().getInventory(),result);return result;
    }
    private static WorkContext smelt(ServerLevel level,Village v,Citizen smith,Villager worker,Observations observed,Chronicle chronicle) {
        Plan p=new Plan();p.kind="SMELT_IRON";p.village=v.id;p.lastProgressTick=level.getGameTime();
        Task t=new Task();t.role=Role.SMITH;t.plan=p.id;t.worker=smith.id;t.lastProgressTick=p.lastProgressTick;t.attemptStartedAt=p.lastProgressTick;
        p.tasks.add(t);v.plans.put(p.id,p);smith.currentTask=t.id;
        return new WorkContext(level,v,p,t,smith,worker,observed,chronicle);
    }
    private static WorkContext standalone(GameTestHelper h,Role role,BlockPos spawn) {
        var worker=h.spawn(EntityType.VILLAGER,spawn);worker.setAge(0);
        Village v=village(h.getLevel(),h.absolutePos(new BlockPos(3,1,3)),48);v.name="Fixture "+role;
        Citizen citizen=new Citizen();citizen.id=worker.getUUID();citizen.village=v.id;citizen.role=role;citizen.name="Worker "+role;
        v.citizens.put(citizen.id,citizen);
        Plan plan=new Plan();plan.kind=role.toString();plan.village=v.id;plan.lastProgressTick=h.getLevel().getGameTime();v.plans.put(plan.id,plan);
        Task task=new Task();task.role=role;task.plan=plan.id;task.worker=citizen.id;task.lastProgressTick=plan.lastProgressTick;
        plan.tasks.add(task);citizen.currentTask=task.id;
        return new WorkContext(h.getLevel(),v,plan,task,citizen,worker,new Observations(),new Chronicle());
    }
    private static Village village(ServerLevel level,BlockPos center,int radius) {
        Village v=new Village();v.center=of(center);v.radius=radius;v.name="Progression";v.createdAt=level.getGameTime();
        v.tierHistory.put(Tier.SETTLEMENT.name(),v.createdAt);
        return v;
    }
    private static Villager villager(ServerLevel level,Village v,BlockPos at,Role role,String name,long xp) {
        Villager worker=new Villager(EntityType.VILLAGER,level);
        worker.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);worker.setPersistenceRequired();level.addFreshEntity(worker);
        Citizen c=new Citizen();c.id=worker.getUUID();c.village=v.id;c.name=name;c.role=role;
        if(xp>0)c.experience.put(role,xp);
        v.citizens.put(c.id,c);return worker;
    }
    private static void build(ServerLevel level,Village v,Blueprints blueprints,String id,String palette,BlockPos origin) {
        var bp=blueprints.get(id,palette);
        for(var cell:bp.cells()) {
            level.setBlock(origin.offset(cell.offset().x(),cell.offset().y(),cell.offset().z()),cell.state(),2);
            if(cell.partner()!=null)level.setBlock(origin.offset(cell.partnerOffset().x(),cell.partnerOffset().y(),cell.partnerOffset().z()),cell.partner(),2);
        }
        Building b=new Building();b.id=UUID.randomUUID();b.village=v.id;b.blueprint=id;b.category=bp.category();b.palette=bp.palette();
        b.origin=of(origin);b.entrance=b.origin.offset(bp.entrance().x(),bp.entrance().y(),bp.entrance().z());b.completedAt=level.getGameTime();
        v.buildings.put(b.id,b);
    }
    private static void bed(ServerLevel level,BlockPos foot) {
        var state=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        level.setBlock(foot,state.setValue(BedBlock.PART,BedPart.FOOT),3);
        level.setBlock(foot.south(),state.setValue(BedBlock.PART,BedPart.HEAD),3);
    }
    private static void tree(ServerLevel level,BlockPos root,Block log,Block leaves) {
        for(int y=0;y<3;y++)level.setBlock(root.above(y),log.defaultBlockState(),3);
        level.setBlock(root.above(3),leaves.defaultBlockState(),3);level.setBlock(root.above(2).east(),leaves.defaultBlockState(),3);
    }
    /** Complete bounded survey of the village area (test-only; production scans incrementally). */
    private static Observations survey(ServerLevel level,Village v) {
        Observations o=new Observations();
        long total=(long)(2*v.radius+1)*(2*v.radius+1)*17;
        for(long n=0;n<total;n+=dev.livingvillages.config.VillageConfig.SCAN_BUDGET.get())o.scan(level,v);
        return o;
    }
    private static Map<String,Integer> stockWithCargo(ServerLevel level,Village v) {
        Map<String,Integer> stock=new TreeMap<>(Inventories.stock(level,v));
        for(Citizen c:v.citizens.values())if(level.getEntity(c.id) instanceof Villager e)Inventories.addStock(e.getInventory(),stock);
        return stock;
    }
    private static String state(Village v) {
        return "tier="+v.tier+" pop="+v.population()+" beds="+v.beds+" production="+v.production+" missing="
                +(v.progression==null?"not evaluated":v.progression.missingRequirements().stream().map(Requirement::message).toList())
                +" plans="+v.activePlans().stream().map(p->p.kind+":"+p.state()+(p.blocker==null?"":"/"+p.blocker.message())).toList();
    }
    private static List<Event> tierEvents(Village v) { return v.history.stream().filter(e->e.type().equals("VILLAGE_TIER_REACHED")).toList(); }
    private static List<Event> unlocks(Village v) { return v.history.stream().filter(e->e.type().equals("TECHNOLOGY_UNLOCKED")).toList(); }
    private static World worldOf(Village v) { World w=new World();w.villages.put(v.id,v);return w; }
    /** Remote plot reserved for one test slot; force-loads it. Build only once {@link #ticking}. */
    private static BlockPos reserve(ServerLevel level,int slot,int width,int depth) {
        BlockPos base=new BlockPos(REMOTE+slot*128,REMOTE_Y,REMOTE);
        force(level,base,width,depth,true);
        return base;
    }
    /** Entity storage loads asynchronously; earlier runs' entities are only visible once chunks tick. */
    private static boolean ticking(ServerLevel level,BlockPos base,int width,int depth) {
        return level.isPositionEntityTicking(base)&&level.isPositionEntityTicking(base.offset(width,0,0))
                &&level.isPositionEntityTicking(base.offset(0,0,depth))&&level.isPositionEntityTicking(base.offset(width,0,depth));
    }
    /**
     * Clears a previous run's plot. Blocks first: removing an old chest drops its contents,
     * so the entity sweep (villagers, dropped bread) must come after it.
     */
    private static void prepare(ServerLevel level,BlockPos base,int width,int depth) {
        for(int x=0;x<width;x++)for(int z=0;z<depth;z++) {
            for(int y=1;y<=10;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(base.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            if(x==0||x==width-1||z==0||z==depth-1)level.setBlock(base.offset(x,1,z),Blocks.OAK_FENCE.defaultBlockState(),3);
        }
        for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(base).expandTowards(width,12,depth).inflate(2)))old.discard();
    }
    private static void close(ServerLevel level,BlockPos base,int width,int depth,Village v) {
        for(Citizen c:v.citizens.values())if(level.getEntity(c.id)!=null)level.getEntity(c.id).discard();
        VillageSavedData.get(level).world.villages.remove(v.id);VillageSavedData.get(level).setDirty();
        force(level,base,width,depth,false);
    }
    private static void force(ServerLevel level,BlockPos base,int width,int depth,boolean forced) {
        for(int x=base.getX()>>4;x<=(base.getX()+width)>>4;x++)for(int z=base.getZ()>>4;z<=(base.getZ()+depth)>>4;z++) {
            level.setChunkForced(x,z,forced);
            if(forced)level.getChunk(x,z);
        }
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(x,0,z,Blocks.GRASS_BLOCK); }
    private static void step(WorkContext c,Job job) {
        if(c.plan().state().terminal()||c.now()<c.task().nextRetryTick)return;
        if(c.task().nextRetryTick>0) { c.task().attemptStartedAt=c.now();c.task().nextRetryTick=0; }
        if(c.task().worker==null) { c.task().worker=c.citizen().id;c.citizen().currentTask=c.task().id; }
        job.tick(c);
    }
    private static String describe(WorkContext c) {
        return "state="+c.plan().state()+" progress="+c.task().progress+" phase="+c.task().phase+" position="+c.worker().position()
                +" inventory="+c.worker().getInventory()+" blocker="+c.task().blocker;
    }
}
