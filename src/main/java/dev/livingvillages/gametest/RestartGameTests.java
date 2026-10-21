package dev.livingvillages.gametest;

import dev.livingvillages.building.*;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Model.Tier;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.progression.Technologies;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.task.*;
import dev.livingvillages.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

/**
 * Opt-in two-JVM fixture. Uses real region/entity/SavedData files, never entity.load().
 * v1.1: the checkpoint also holds a spruce-palette house and a technology plan whose
 * researcher carries the full material cost mid-research.
 */
@GameTestHolder("livingvillages_restart")
@PrefixGameTestTemplate(false)
public final class RestartGameTests {
    private static final BlockPos BASE=new BlockPos(4096,80,4096);
    private static final String SECOND_PALETTE="spruce";
    private static final int EXTRA_LOGS=4;
    @GameTest(template="empty",timeoutTicks=30000,skyAccess=true)
    public static void externalProcessRestart(GameTestHelper h) throws Exception {
        String phase=System.getProperty("livingvillages.acceptance.phase","");
        h.assertTrue(phase.equals("write")||phase.equals("read"),"Explicit restart phase required");
        var level=h.getLevel();
        var file=level.getServer().getWorldPath(LevelResource.ROOT).resolve("livingvillages-acceptance.json");
        for(int x=256;x<=257;x++) { level.setChunkForced(x,256,true);level.getChunk(x,256); }
        level.setDayTime(1000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        var data=VillageSavedData.get(level);
        var blueprints=new Blueprints();Job job=new ConstructionJob(blueprints),research=new ResearchJob();
        Pos furnace=of(BASE.offset(4,1,2));
        var cost=Palettes.resolve(Technologies.CHARCOAL_BURNING.costs(),Palettes.DEFAULT);
        if(phase.equals("write")) {
            h.assertTrue(!Files.exists(file),"Use a fresh acceptance directory");
            // A fresh world loads entity storage asynchronously: entities added before the forced
            // chunks are entity-ticking stay invisible to queries. Set up only once they tick.
            // GameTest sequences cannot be registered from inside a running sequence: one driver.
            Runnable[] driver={null};
            h.onEachTick(()->{
                if(driver[0]!=null) { driver[0].run();return; }
                if(level.isPositionEntityTicking(BASE)&&level.isPositionEntityTicking(BASE.offset(31,0,15)))
                    driver[0]=write(h,level,data,file,blueprints,job,research,furnace,cost);
            });
            return;
        }
        read(h,level,data,file,blueprints,job,research,furnace,cost);
    }
    /** Places the fixture and returns the per-tick driver (discovery after ten ticks, then work). */
    private static Runnable write(GameTestHelper h,net.minecraft.server.level.ServerLevel level,VillageSavedData data,Path file,Blueprints blueprints,
                                  Job job,Job research,Pos furnace,Map<String,Integer> cost) {
        {
            for(int x=0;x<32;x++)for(int z=0;z<16;z++)level.setBlock(BASE.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            var chestPos=of(BASE.offset(2,1,3));level.setBlock(block(chestPos),Blocks.CHEST.defaultBlockState(),3);
            level.setBlock(block(furnace),Blocks.FURNACE.defaultBlockState(),3);
            var bell=BASE.offset(12,1,8);level.setBlock(bell,Blocks.BELL.defaultBlockState(),3);
            Villager worker=new Villager(EntityType.VILLAGER,level);
            worker.setPos(BASE.getX()+3.5,BASE.getY()+1,BASE.getZ()+3.5);worker.setPersistenceRequired();level.addFreshEntity(worker);
            Villager smithEntity=new Villager(EntityType.VILLAGER,level);
            smithEntity.setPos(BASE.getX()+5.5,BASE.getY()+1,BASE.getZ()+4.5);smithEntity.setPersistenceRequired();level.addFreshEntity(smithEntity);
            var player=h.makeMockServerPlayerInLevel();player.teleportTo(bell.getX(),bell.getY(),bell.getZ());
            WorkContext[] current={null},researching={null};boolean[] second={false},saved={false},paused={false};
            long discoverAt=h.getTick()+10;long[] buildRetry={0},studyRetry={0};
            Runnable discover=()->{
                new Discovery(new Chronicle()).discover(level,data.world);
                Village v=data.world.villages.values().stream().filter(candidate->candidate.citizens.containsKey(worker.getUUID())).findFirst().orElseThrow();
                level.getServer().getPlayerList().remove(player);
                v.lastPlanTick=Long.MAX_VALUE;v.storage.add(chestPos);
                // Persisted history of an earlier Tier II village; research needs that tier.
                v.tier=Tier.VILLAGE;v.tierHistory.put(Tier.VILLAGE.name(),level.getGameTime());
                Citizen citizen=v.citizens.get(worker.getUUID());citizen.role=Role.BUILDER;
                Citizen smith=v.citizens.get(smithEntity.getUUID());smith.role=Role.SMITH;smith.experience.put(Role.SMITH,64L);
                var chest=Inventories.container(level,chestPos);
                blueprints.get("house_t1_01").manifest().forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n)));
                blueprints.get("house_t1_01",SECOND_PALETTE).manifest().forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n)));
                cost.forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n)));
                Inventories.insert(chest,new ItemStack(Inventories.item("minecraft:oak_log"),EXTRA_LOGS));
                current[0]=construction(level,v,citizen,worker,of(BASE.offset(6,1,6)),Palettes.DEFAULT);
                researching[0]=research(level,v,smith,smithEntity,furnace);
            };
            return ()->{
                    if(current[0]==null&&h.getTick()>=discoverAt)discover.run();
                    if(current[0]==null||saved[0]||h.getTick()%10!=0)return;
                    Village v=current[0].village();Citizen citizen=current[0].citizen();
                    drive(job,current[0],buildRetry);
                    if(!paused[0]) {
                        drive(research,researching[0],studyRetry);
                        paused[0]=researching[0].task().progress>=2; // Keep research mid-way for the checkpoint.
                    }
                    if(current[0].plan().state()==State.COMPLETED&&!second[0]) {
                        UUID completed=current[0].plan().id;
                        current[0]=construction(level,v,citizen,worker,of(BASE.offset(16,1,6)),SECOND_PALETTE);
                        current[0].plan().dependencies.add(completed);second[0]=true;buildRetry[0]=0;
                    }
                    if(second[0]&&paused[0]&&current[0].task().progress>=20) {
                        Plan blocked=new Plan();blocked.kind="GATHER_WOOD";blocked.village=v.id;
                        blocked.block(Diagnostics.reason(Reason.PATH_UNREACHABLE,"Restart fixture: no accessible tree",blocked,null,
                                level.getGameTime(),null,"minecraft:oak_log",1,0),level.getGameTime(),1);
                        v.plans.put(blocked.id,blocked);
                        current[0].task().nextRetryTick=Long.MAX_VALUE;
                        h.assertTrue(cargo(smithEntity).equals(cost),"Researcher carries exactly the cost: "+cargo(smithEntity));
                        data.setDirty();
                        try {
                            World expected=new World();expected.villages.put(v.id,v);
                            Files.writeString(file,StateCodec.encode(expected));
                            level.save(null,true,false);
                        } catch(Exception error) { throw new RuntimeException(error); }
                        saved[0]=true;
                        dev.livingvillages.LivingVillagesMod.LOG.info("event=restart_checkpoint_written village={} citizen={} progress={} research_progress={} plans={} buildings={}",
                                v.id,citizen.id,current[0].task().progress,researching[0].task().progress,v.plans.size(),v.buildings.size());
                        h.succeed();
                    }
            };
        }
    }
    private static void read(GameTestHelper h,net.minecraft.server.level.ServerLevel level,VillageSavedData data,Path file,Blueprints blueprints,
                             Job job,Job research,Pos furnace,Map<String,Integer> cost) throws Exception {
        {
            h.assertTrue(Files.exists(file),"First JVM checkpoint must exist");
            World expected=StateCodec.decode(Files.readString(file));
            Village before=expected.villages.values().iterator().next();
            Village restored=data.world.villages.get(before.id);
            h.assertTrue(restored!=null&&data.world.villages.size()==1,"Same village, no duplicates");
            h.assertTrue(restored.plans.keySet().equals(before.plans.keySet()),"Same plans, no duplicates");
            h.assertTrue(restored.citizens.keySet().equals(before.citizens.keySet()),"Same citizen membership");
            h.assertTrue(restored.history.equals(before.history),"Chronicle survives process restart");
            h.assertTrue(restored.storage.equals(before.storage),"Storage references preserved");
            h.assertTrue(restored.buildings.keySet().equals(before.buildings.keySet()),"Completed building identity");
            h.assertTrue(restored.tier==Tier.VILLAGE&&restored.tierHistory.equals(before.tierHistory),"Historical tier preserved");
            var active=restored.plans.values().stream().filter(p->p.blueprint!=null&&!p.state().terminal()).findFirst().orElseThrow();
            var old=before.plans.get(active.id);
            h.assertTrue(active.progress==old.progress&&active.dependencies.equals(old.dependencies),"Progress/dependencies preserved");
            h.assertTrue(SECOND_PALETTE.equals(active.palette),"Construction palette survives a real restart");
            var study=restored.plans.values().stream().filter(p->p.technology!=null).findFirst().orElseThrow();
            var studyBefore=before.plans.get(study.id);
            h.assertTrue(study.tasks.getFirst().progress==studyBefore.tasks.getFirst().progress&&study.tasks.getFirst().progress>=2
                    &&study.tasks.getFirst().minimumLevel==studyBefore.tasks.getFirst().minimumLevel,"Research progress preserved");
            h.assertTrue(restored.technologies.isEmpty(),"No unlock was granted by the restart");
            h.assertTrue(restored.plans.values().stream().anyMatch(p->p.state()==State.BLOCKED&&p.blocker!=null),"Blocker survived");
            var citizen=restored.citizens.values().stream().filter(c->c.role==Role.BUILDER).findFirst().orElseThrow();
            var prior=before.citizens.get(citizen.id);
            h.assertTrue(citizen.name.equals(prior.name)&&citizen.role==prior.role&&citizen.experience.equals(prior.experience)
                    &&citizen.skills.equals(prior.skills)&&citizen.level()==prior.level(),"Citizen identity, role, XP and skills");
            var smith=restored.citizens.values().stream().filter(c->c.role==Role.SMITH).findFirst().orElseThrow();
            h.assertTrue(smith.experience.get(Role.SMITH)>=64,"Researcher identity and XP preserved");
            Observations observed=new Observations();observed.furnaces.add(furnace);
            boolean[] resumed={false};WorkContext[] studying={null};long[] buildRetry={0},studyRetry={0};
            h.onEachTick(()->{
                if(h.getTick()%10!=0)return;
                // Actual entity storage loads asynchronously.
                if(!(level.getEntity(citizen.id) instanceof Villager worker)||!(level.getEntity(smith.id) instanceof Villager researcher))return;
                if(!resumed[0]) {
                    Map<String,Integer> remaining=new TreeMap<>();
                    var bp=blueprints.get(active.blueprint,active.palette);
                    for(int i=active.tasks.getFirst().progress;i<bp.cells().size();i++)remaining.merge(bp.cells().get(i).item(),1,Integer::sum);
                    remaining.merge("minecraft:oak_log",EXTRA_LOGS,Integer::sum);
                    Map<String,Integer> physical=new TreeMap<>(Inventories.stock(level,restored));Inventories.addStock(worker.getInventory(),physical);
                    h.assertTrue(physical.equals(remaining),"Chest + builder cargo exactly equal unbuilt manifest after restart: physical="+physical+" remaining="+remaining);
                    h.assertTrue(cargo(researcher).equals(cost),"Researcher's vanilla inventory still holds exactly the cost: "+cargo(researcher));
                    studying[0]=new WorkContext(level,restored,study,study.tasks.getFirst(),smith,researcher,observed,new Chronicle());
                    resumed[0]=true;
                }
                WorkContext c=new WorkContext(level,restored,active,active.tasks.getFirst(),citizen,worker,new Observations(),new Chronicle());
                if(h.getTick()>=29000)h.fail("Resume stalled: house "+active.state()+" "+active.progress+" "+active.blocker
                        +" | research "+study.state()+" "+study.tasks.getFirst().progress+" "+study.blocker);
                if(active.state()!=State.COMPLETED)drive(job,c,buildRetry);
                if(study.state()!=State.COMPLETED)drive(research,studying[0],studyRetry);
                if(active.state()==State.COMPLETED&&study.state()==State.COMPLETED) {
                    h.assertTrue(restored.buildings.size()==2,"Second house completes once after restart");
                    var building=restored.buildings.values().stream().filter(b->b.plan.equals(active.id)).findFirst().orElseThrow();
                    h.assertTrue(SECOND_PALETTE.equals(building.palette),"Completed building keeps its palette");
                    h.assertTrue(level.getBlockState(block(building.origin.offset(0,1,0))).is(Blocks.SPRUCE_PLANKS),"Spruce blocks placed after restart");
                    h.assertTrue(restored.technologies.containsKey(Technologies.CHARCOAL_BURNING.id())
                            &&restored.history.stream().filter(e->e.type().equals("TECHNOLOGY_UNLOCKED")).count()==1,"Technology unlocked exactly once after restart");
                    h.assertTrue(Inventories.stock(level,restored).equals(Map.of("minecraft:oak_log",EXTRA_LOGS))&&worker.getInventory().isEmpty()
                            &&cargo(researcher).isEmpty(),"No duplicated or lost materials: "+Inventories.stock(level,restored));
                    dev.livingvillages.LivingVillagesMod.LOG.info("event=restart_checkpoint_verified village={} buildings={} technologies={} physical_remaining={}",
                            restored.id,restored.buildings.size(),restored.technologies.keySet(),Inventories.stock(level,restored));
                    h.succeed();
                }
            });
        }
    }
    /**
     * Drives one executor with the production scheduler's retry semantics: wait for the
     * backoff after a blocker, then open a new attempt window. The task itself stays parked
     * (nextRetryTick = MAX) so the live Simulation never runs it concurrently.
     */
    private static void drive(Job job,WorkContext c,long[] retryAt) {
        long now=c.now();
        if(now<retryAt[0])return;
        if(retryAt[0]>0) { c.task().attemptStartedAt=now;retryAt[0]=0; }
        c.task().nextRetryTick=0;
        job.tick(c);
        if(c.task().nextRetryTick>now)retryAt[0]=c.task().nextRetryTick;
        c.task().nextRetryTick=Long.MAX_VALUE;
    }
    private static Map<String,Integer> cargo(Villager v) { Map<String,Integer> m=new TreeMap<>();Inventories.addStock(v.getInventory(),m);return m; }
    private static WorkContext construction(net.minecraft.server.level.ServerLevel level,Village v,Citizen citizen,Villager worker,Pos site,String palette) {
        Plan p=new Plan();p.kind="BUILD_HOUSE";p.village=v.id;p.blueprint="house_t1_01";p.palette=palette;p.site=site;
        Task t=new Task();t.plan=p.id;t.role=Role.BUILDER;t.worker=citizen.id;t.nextRetryTick=Long.MAX_VALUE;
        t.lastProgressTick=level.getGameTime();t.attemptStartedAt=t.lastProgressTick;
        p.tasks.add(t);v.plans.put(p.id,p);citizen.currentTask=t.id;
        return new WorkContext(level,v,p,t,citizen,worker,new Observations(),new Chronicle());
    }
    private static WorkContext research(net.minecraft.server.level.ServerLevel level,Village v,Citizen smith,Villager worker,Pos furnace) {
        var tech=Technologies.CHARCOAL_BURNING;
        Plan p=new Plan();p.kind=tech.planKind();p.village=v.id;p.technology=tech.id();p.palette=Palettes.DEFAULT;p.need=Need.DEVELOPMENT;
        p.requirements=new TreeMap<>(Palettes.resolve(tech.costs(),p.palette));p.totalWork=tech.workUnits();
        Task t=new Task();t.plan=p.id;t.role=tech.role();t.worker=smith.id;t.minimumLevel=tech.roleLevel();t.nextRetryTick=Long.MAX_VALUE;
        t.lastProgressTick=level.getGameTime();t.attemptStartedAt=t.lastProgressTick;
        p.tasks.add(t);v.plans.put(p.id,p);smith.currentTask=t.id;
        Observations observed=new Observations();observed.furnaces.add(furnace);
        return new WorkContext(level,v,p,t,smith,worker,observed,new Chronicle());
    }
}
