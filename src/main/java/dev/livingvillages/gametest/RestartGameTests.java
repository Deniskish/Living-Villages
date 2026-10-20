package dev.livingvillages.gametest;

import dev.livingvillages.building.*;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.persistence.VillageSavedData;
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

/** Opt-in two-JVM fixture. Uses real region/entity/SavedData files, never entity.load(). */
@GameTestHolder("livingvillages_restart")
@PrefixGameTestTemplate(false)
public final class RestartGameTests {
    private static final BlockPos BASE=new BlockPos(4096,80,4096);
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
        var blueprints=new Blueprints();Job job=new ConstructionJob(blueprints);
        if(phase.equals("write")) {
            h.assertTrue(!Files.exists(file),"Use a fresh acceptance directory");
            for(int x=0;x<32;x++)for(int z=0;z<16;z++)level.setBlock(BASE.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            var chestPos=of(BASE.offset(2,1,3));level.setBlock(block(chestPos),Blocks.CHEST.defaultBlockState(),3);
            var bell=BASE.offset(12,1,8);level.setBlock(bell,Blocks.BELL.defaultBlockState(),3);
            Villager worker=new Villager(EntityType.VILLAGER,level);
            worker.setPos(BASE.getX()+3.5,BASE.getY()+1,BASE.getZ()+3.5);worker.setPersistenceRequired();level.addFreshEntity(worker);
            var player=h.makeMockServerPlayerInLevel();player.teleportTo(bell.getX(),bell.getY(),bell.getZ());
            WorkContext[] current={null};boolean[] second={false},saved={false};
            h.runAfterDelay(10,()->{
                new Discovery(new Chronicle()).discover(level,data.world);
                Village v=data.world.villages.values().stream().filter(candidate->candidate.citizens.containsKey(worker.getUUID())).findFirst().orElseThrow();
                level.getServer().getPlayerList().remove(player);
                v.lastPlanTick=Long.MAX_VALUE;v.storage.add(chestPos);
                Citizen citizen=v.citizens.get(worker.getUUID());citizen.role=Role.BUILDER;
                var chest=Inventories.container(level,chestPos);
                blueprints.get("house_t1_01").manifest().forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n*2)));
                current[0]=construction(level,v,citizen,worker,of(BASE.offset(6,1,6)));
            });
                h.onEachTick(()->{
                    if(current[0]==null||saved[0]||h.getTick()%10!=0)return;
                    Village v=current[0].village();Citizen citizen=current[0].citizen();
                    job.tick(current[0]);
                    current[0].task().nextRetryTick=Long.MAX_VALUE; // Only this fixture drives this executor.
                    if(current[0].plan().state()==State.COMPLETED&&!second[0]) {
                        UUID completed=current[0].plan().id;
                        current[0]=construction(level,v,citizen,worker,of(BASE.offset(16,1,6)));
                        current[0].plan().dependencies.add(completed);second[0]=true;
                    }
                    if(second[0]&&current[0].task().progress>=20) {
                        Plan blocked=new Plan();blocked.kind="GATHER_WOOD";blocked.village=v.id;
                        blocked.block(Diagnostics.reason(Reason.PATH_UNREACHABLE,"Restart fixture: no accessible tree",blocked,null,
                                level.getGameTime(),null,"minecraft:oak_log",1,0),level.getGameTime(),1);
                        v.plans.put(blocked.id,blocked);
                        current[0].task().nextRetryTick=Long.MAX_VALUE;
                        data.setDirty();
                        try {
                            World expected=new World();expected.villages.put(v.id,v);
                            Files.writeString(file,StateCodec.encode(expected));
                            level.save(null,true,false);
                        } catch(Exception error) { throw new RuntimeException(error); }
                        saved[0]=true;
                        dev.livingvillages.LivingVillagesMod.LOG.info("event=restart_checkpoint_written village={} citizen={} progress={} plans={} buildings={}",
                                v.id,citizen.id,current[0].task().progress,v.plans.size(),v.buildings.size());
                        h.succeed();
                    }
                });
        } else {
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
            var active=restored.plans.values().stream().filter(p->p.blueprint!=null&&!p.state().terminal()).findFirst().orElseThrow();
            var old=before.plans.get(active.id);
            h.assertTrue(active.progress==old.progress&&active.dependencies.equals(old.dependencies),"Progress/dependencies preserved");
            h.assertTrue(restored.plans.values().stream().anyMatch(p->p.state()==State.BLOCKED&&p.blocker!=null),"Blocker survived");
            var citizen=restored.citizens.values().iterator().next();var prior=before.citizens.get(citizen.id);
            h.assertTrue(citizen.name.equals(prior.name)&&citizen.role==prior.role&&citizen.experience.equals(prior.experience)
                    &&citizen.skills.equals(prior.skills)&&citizen.level()==prior.level(),"Citizen identity, role, XP and skills");
            boolean[] resumed={false};
            h.onEachTick(()->{
                if(h.getTick()%10!=0)return;
                if(!(level.getEntity(citizen.id) instanceof Villager worker))return; // Actual entity storage loads asynchronously.
                if(!resumed[0]) {
                    Map<String,Integer> remaining=new TreeMap<>();
                    for(int i=active.tasks.getFirst().progress;i<blueprints.get(active.blueprint).cells().size();i++)
                        remaining.merge(blueprints.get(active.blueprint).cells().get(i).item(),1,Integer::sum);
                    Map<String,Integer> physical=new TreeMap<>(Inventories.stock(level,restored));Inventories.addStock(worker.getInventory(),physical);
                    h.assertTrue(physical.equals(remaining),"Chest + entity cargo exactly equal unbuilt manifest after restart: physical="+physical+" remaining="+remaining);
                    resumed[0]=true;
                }
                WorkContext c=new WorkContext(level,restored,active,active.tasks.getFirst(),citizen,worker,new Observations(),new Chronicle());
                job.tick(c);
                c.task().nextRetryTick=Long.MAX_VALUE;
                if(active.state()==State.COMPLETED) {
                    h.assertTrue(restored.buildings.size()==2,"Second house completes once after restart");
                    h.assertTrue(Inventories.stock(level,restored).isEmpty()&&worker.getInventory().isEmpty(),"No duplicated or lost materials");
                    dev.livingvillages.LivingVillagesMod.LOG.info("event=restart_checkpoint_verified village={} buildings={} physical_remaining=0",restored.id,restored.buildings.size());
                    h.succeed();
                }
            });
        }
    }
    private static WorkContext construction(net.minecraft.server.level.ServerLevel level,Village v,Citizen citizen,Villager worker,Pos site) {
        Plan p=new Plan();p.kind="BUILD_HOUSE";p.village=v.id;p.blueprint="house_t1_01";p.site=site;
        Task t=new Task();t.plan=p.id;t.role=Role.BUILDER;t.worker=citizen.id;t.nextRetryTick=Long.MAX_VALUE;
        t.lastProgressTick=level.getGameTime();t.attemptStartedAt=t.lastProgressTick;
        p.tasks.add(t);v.plans.put(p.id,p);citizen.currentTask=t.id;
        return new WorkContext(level,v,p,t,citizen,worker,new Observations(),new Chronicle());
    }
}
