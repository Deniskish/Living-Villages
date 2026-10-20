package dev.livingvillages.gametest;

import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.network.*;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

@GameTestHolder("livingvillages")
@PrefixGameTestTemplate(false)
public final class HardeningGameTests {
    @GameTest(template="empty")
    public static void corruptExistingSaveCannotBeReplaced(GameTestHelper h) throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("livingvillages-corrupt-");
        var file=directory.resolve("livingvillages.dat");
        byte[] original={1,2,3,4};java.nio.file.Files.write(file,original);
        boolean rejected=false;
        try { VillageSavedData.createNew(file); } catch(IllegalStateException expected) { rejected=true; }
        h.assertTrue(rejected,"Fallback refuses an existing unreadable file");
        h.assertTrue(Arrays.equals(original,java.nio.file.Files.readAllBytes(file)),"Original bytes retained");
        java.nio.file.Files.delete(file);java.nio.file.Files.delete(directory);h.succeed();
    }
    @GameTest(template="empty")
    public static void overlappingVillagesNeverDoubleCountStorage(GameTestHelper h) {
        var data=VillageSavedData.get(h.getLevel());
        Village a=new Village(),b=new Village();
        a.center=of(h.absolutePos(new BlockPos(2,1,3)));b.center=of(h.absolutePos(new BlockPos(12,1,3)));
        a.name="A";b.name="B";a.radius=8;b.radius=8;
        data.world.villages.put(a.id,a);data.world.villages.put(b.id,b);
        Pos chest=of(h.absolutePos(new BlockPos(8,1,3)));
        h.setBlock(8,1,3,Blocks.CHEST);Inventories.container(h.getLevel(),chest).setItem(0,new ItemStack(Items.OAK_LOG,17));
        a.storage.add(chest);b.storage.add(chest);b.storage.add(chest);
        try {
            h.assertTrue(Inventories.stock(h.getLevel(),a).isEmpty(),"More distant village cannot claim shared chest");
            h.assertTrue(Inventories.stock(h.getLevel(),b).get("minecraft:oak_log")==17,"Nearest owner counts exactly once");
            h.assertTrue(Ownership.at(List.of(a,b),chest).orElseThrow().id.equals(b.id),"Bell/position ownership agrees");
        } finally { data.world.villages.remove(a.id);data.world.villages.remove(b.id);data.setDirty(); }
        h.succeed();
    }
    @GameTest(template="empty",batch="ledger",timeoutTicks=200)
    public static void ledgerBookBellAuthorizationAndMissingVillage(GameTestHelper h) {
        h.setBlock(8,0,8,Blocks.STONE);h.setBlock(8,1,8,Blocks.BELL);
        var player=h.makeMockServerPlayerInLevel();
        var pos=h.absolutePos(new BlockPos(8,1,7));player.teleportTo(pos.getX(),pos.getY(),pos.getZ());
        var data=VillageSavedData.get(h.getLevel());Village v=new Village();v.name="Ledger";v.center=of(h.absolutePos(new BlockPos(8,1,8)));
        data.world.villages.put(v.id,v);
        h.runAfterDelay(5,()->{
            try {
                h.assertTrue(LedgerNetwork.accessible(player,v.id)==null,"Requires ordinary Book");
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BOOK));
                h.assertTrue(LedgerNetwork.accessible(player,v.id)==v,"Book and real bell authorize correct village");
                h.assertTrue(LedgerNetwork.accessible(player,UUID.randomUUID())==null,"Unknown village rejected");
                h.setBlock(8,1,8,Blocks.AIR);
                h.assertTrue(LedgerNetwork.accessible(player,v.id)==null,"Removed bell invalidates refresh immediately");
                h.setBlock(8,1,8,Blocks.BELL);
                data.world.villages.remove(v.id);
                h.assertTrue(LedgerNetwork.accessible(player,v.id)==null,"Removed village is safe");
            } finally {
                data.world.villages.remove(v.id);data.setDirty();h.getLevel().getServer().getPlayerList().remove(player);
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void ledgerLargeAndEmptyDataRemainBounded(GameTestHelper h) {
        var empty=new LedgerView(UUID.randomUUID(),null,null);
        h.assertTrue(LedgerView.parse(empty.json()).tabs().size()==6,"Empty snapshots retain six safe tabs");
        List<LedgerView.Row> rows=new ArrayList<>();
        for(int i=0;i<2000;i++)rows.add(new LedgerView.Row("Long "+i+"x".repeat(3000),Collections.nCopies(100,"\\\"\n".repeat(2000))));
        var large=new LedgerView(UUID.randomUUID(),"Large".repeat(200),Map.of("Citizens",rows,"Chronicle",rows,"Plans",rows,"Diagnostics",rows));
        String json=large.json();
        h.assertTrue(json.length()<=240000,"Escaped JSON respects transport budget");
        var restored=LedgerView.parse(json);
        h.assertTrue(restored.tabs().values().stream().allMatch(list->list.size()<=201),"Row cap survives transport");
        h.assertTrue(restored.tabs().values().stream().flatMap(Collection::stream).allMatch(r->r.title().length()<=512&&r.detail().size()<=32),"Long fields bounded");
        h.succeed();
    }
    @GameTest(template="empty",batch="soak",timeoutTicks=50000,skyAccess=true)
    public static void threeVillagesThirtySixCitizensSoak(GameTestHelper h) {
        var level=h.getLevel();var data=VillageSavedData.get(level);
        level.setDayTime(1000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        List<Village> villages=new ArrayList<>();List<BlockPos> bases=new ArrayList<>();
        for(int n=0;n<3;n++) {
            BlockPos base=new BlockPos(8192+n*128,80,8192);bases.add(base);force(level,base,true);
            for(int x=0;x<32;x++)for(int z=0;z<16;z++) {
                // These remote coordinates belong exclusively to this fixture. Clear the
                // previous run's construction before setting up the next identical scenario.
                for(int y=1;y<=8;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(base.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                if(x==0||x==31||z==0||z==15)level.setBlock(base.offset(x,1,z),Blocks.OAK_FENCE.defaultBlockState(),3);
            }
            for(var oldEntity:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(base).expandTowards(32,10,16).inflate(2)))oldEntity.discard();
            Village v=new Village();v.center=of(base.offset(16,1,8));v.radius=20;v.name="Soak "+n;
            v.createdAt=level.getGameTime()-2000;villages.add(v);data.world.villages.put(v.id,v);
            Pos storage=of(base.offset(3,1,3));v.storage.add(storage);
            level.setBlock(block(storage),Blocks.CHEST.defaultBlockState(),3);
            level.setBlock(base.offset(5,1,3),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            var chest=Inventories.container(level,storage);
            new dev.livingvillages.building.Blueprints().get("house_t1_01").manifest()
                    .forEach((id,count)->Inventories.insert(chest,new ItemStack(Inventories.item(id),count*2)));
            for(var entry:Map.of(Items.OAK_LOG,64,Items.COBBLESTONE,192,Items.BREAD,64,Items.WHEAT_SEEDS,32,Items.WOODEN_PICKAXE,3).entrySet())
                Inventories.insert(chest,new ItemStack(entry.getKey(),entry.getValue()));
            for(int i=0;i<12;i++) {
                Villager worker=new Villager(EntityType.VILLAGER,level);
                worker.setPos(base.getX()+7.5+i%6,81,base.getZ()+3.5+i/6);worker.setPersistenceRequired();level.addFreshEntity(worker);
                Citizen citizen=new Citizen();citizen.id=worker.getUUID();citizen.name="Soak "+n+"/"+i;citizen.village=v.id;
                citizen.role=Role.values()[i%6];v.citizens.put(citizen.id,citizen);
            }
            for(int x=20;x<24;x++) {
                level.setBlock(base.offset(x,0,3),Blocks.FARMLAND.defaultBlockState(),3);
                level.setBlock(base.offset(x,1,3),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
            }
            level.setBlock(base.offset(24,1,8),Blocks.OAK_LOG.defaultBlockState(),3);
            level.setBlock(base.offset(24,2,8),Blocks.OAK_LOG.defaultBlockState(),3);
            level.setBlock(base.offset(24,3,8),Blocks.OAK_LOG.defaultBlockState(),3);
            level.setBlock(base.offset(24,4,8),Blocks.OAK_LEAVES.defaultBlockState(),3);
            level.setBlock(base.offset(25,3,8),Blocks.OAK_LEAVES.defaultBlockState(),3);
            for(int x=26;x<29;x++)level.setBlock(base.offset(x,1,11),Blocks.STONE.defaultBlockState(),3);
            // Real work demand even while the supplied food buffer is initially sufficient.
            for(String kind:List.of("FARM","GATHER_WOOD","MINE")) {
                Plan p=new Plan();p.kind=kind;p.village=v.id;p.createdAt=level.getGameTime();p.priority=1;
                Task t=new Task();t.plan=p.id;t.role=kind.equals("FARM")?Role.FARMER:kind.equals("MINE")?Role.MINER:Role.LUMBERJACK;
                t.lastProgressTick=level.getGameTime();p.tasks.add(t);v.plans.put(p.id,p);
            }
        }
        data.setDirty();boolean[] unloaded={false};
        h.runAtTickTime(8000,()->force(level,bases.get(2),false));
        h.runAtTickTime(11500,()->unloaded[0]=!level.hasChunkAt(block(villages.get(2).center)));
        h.runAtTickTime(12000,()->force(level,bases.get(2),true));
        h.runAtTickTime(48000,()->{
            try {
                h.assertTrue(unloaded[0],"Third village actually unloaded before reloading");
                Set<UUID> citizens=new HashSet<>(),plans=new HashSet<>();
                for(Village v:villages) {
                    h.assertTrue(!v.buildings.isEmpty(),"Autonomous construction completed in "+v.name+" "+
                            v.activePlans().stream().map(p->p.kind+" "+p.progress+" "+p.blocker).toList());
                    h.assertTrue(v.citizens.values().stream().anyMatch(c->c.experience.getOrDefault(Role.FARMER,0L)>0),"Farming executed in "+v.name);
                    h.assertTrue(v.citizens.values().stream().anyMatch(c->c.experience.getOrDefault(Role.LUMBERJACK,0L)>0),"Forestry executed in "+v.name);
                    h.assertTrue(v.activePlans().size()<=dev.livingvillages.config.VillageConfig.ACTIVE_PLANS.get(),"Active plan cap");
                    Set<String> kinds=new HashSet<>();
                    for(Plan p:v.activePlans())h.assertTrue(kinds.add(p.kind),"No duplicate active kind");
                    for(Citizen c:v.citizens.values())h.assertTrue(citizens.add(c.id)&&c.village.equals(v.id),"Unique citizen ownership");
                    for(Plan p:v.plans.values()) {
                        h.assertTrue(plans.add(p.id)&&p.village.equals(v.id),"Unique plan ownership");
                        for(Task t:p.tasks)if(t.worker!=null)h.assertTrue(v.citizens.containsKey(t.worker),"No foreign worker");
                        if(p.state()==State.BLOCKED)h.assertTrue(p.blocker!=null,"No unexplained blocker");
                    }
                    h.assertTrue(v.history.stream().allMatch(e->v.id.equals(e.village())&&(e.actor()==null||v.citizens.containsKey(e.actor()))),"History isolation");
                    h.assertTrue(v.storage.stream().allMatch(p->Ownership.owns(level,v,p)),"Storage isolation");
                    h.assertTrue(v.plans.size()<400&&v.history.size()<5000,"No uncontrolled idle plan/event growth");
                    h.assertTrue(v.scanSteps<=4801&&v.strategicSteps<=241,"Scan/strategy frequency bounded");
                    dev.livingvillages.LivingVillagesMod.LOG.info(
                            "event=acceptance_soak village={} ticks=48000 citizens={} plans={} active={} events={} buildings={} scans={} strategies={} work={} mean_work_us={} max_work_us={}",
                            v.name,v.citizens.size(),v.plans.size(),v.activePlans().size(),v.history.size(),v.buildings.size(),
                            v.scanSteps,v.strategicSteps,v.workerSteps,v.workerNanos/Math.max(1,v.workerSteps)/1000,v.maxWorkerNanos/1000);
                }
                h.assertTrue(villages.stream().mapToLong(v->v.workerSteps).sum()<=38408,"Dimension worker budget");
            } finally {
                for(Village v:villages) {
                    for(Citizen c:v.citizens.values())if(level.getEntity(c.id)!=null)level.getEntity(c.id).discard();
                    data.world.villages.remove(v.id);
                }
                for(BlockPos base:bases)force(level,base,false);
                data.setDirty();
            }
            h.succeed();
        });
    }
    private static void force(net.minecraft.server.level.ServerLevel level,BlockPos base,boolean forced) {
        for(int x=base.getX()>>4;x<=(base.getX()+31)>>4;x++) {
            level.setChunkForced(x,base.getZ()>>4,forced);
            if(forced)level.getChunk(x,base.getZ()>>4);
        }
    }
}
