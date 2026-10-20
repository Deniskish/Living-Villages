package dev.livingvillages.gametest;
import dev.livingvillages.building.*;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.task.*;
import dev.livingvillages.world.Observations;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

@GameTestHolder("livingvillages")
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    @GameTest(template="empty")
    public static void transferConservesItemsWhenDestinationIsFull(GameTestHelper h) {
        SimpleContainer from=new SimpleContainer(2),to=new SimpleContainer(1);
        from.setItem(0,new ItemStack(Items.OAK_LOG,32));to.setItem(0,new ItemStack(Items.OAK_LOG,60));
        h.assertTrue(Inventories.transfer(from,to,"minecraft:oak_log",32)==4,"Only four items fit");
        h.assertTrue(Inventories.count(from,"minecraft:oak_log")==28,"Source retains remainder");
        h.assertTrue(Inventories.count(to,"minecraft:oak_log")==64,"Destination is exactly full");
        h.assertTrue(Inventories.transfer(from,to,"minecraft:oak_log",32)==0,"Retry cannot duplicate");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void destroyedStorageInvalidatesPhysicalStock(GameTestHelper h) {
        h.setBlock(2,1,2,Blocks.CHEST);
        Village v=new Village();v.center=of(h.absolutePos(new BlockPos(2,1,2)));v.storage.add(v.center);
        var chest=Inventories.container(h.getLevel(),v.center);chest.setItem(0,new ItemStack(Items.OAK_LOG,12));
        h.assertTrue(Inventories.stock(h.getLevel(),v).get("minecraft:oak_log")==12,"Counts physical chest");
        h.setBlock(2,1,2,Blocks.AIR);
        h.assertTrue(Inventories.stock(h.getLevel(),v).isEmpty(),"Removed stock disappears");
        h.assertTrue(v.storage.isEmpty(),"Stale storage reference removed");h.succeed();
    }
    @GameTest(template="empty")
    public static void manifestsCountBedAndDoorOnce(GameTestHelper h) {
        var blueprints=new Blueprints();
        h.assertTrue(blueprints.all().size()==6,"Six templates");
        var house=blueprints.get("house_t1_01");
        h.assertTrue(house.manifest().get("minecraft:white_bed")==1,"One item per two-part bed");
        h.assertTrue(house.manifest().get("minecraft:oak_door")==1,"One item per two-part door");
        Set<Pos> occupied=new HashSet<>();
        for(var cell:house.cells()) {
            h.assertTrue(occupied.add(cell.offset()),"No cell overlap");
            if(cell.partnerOffset()!=null)h.assertTrue(occupied.add(cell.partnerOffset()),"No partner overlap");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void savedDataAndVanillaCargoRoundTrip(GameTestHelper h) {
        WorkContext c=context(h,Role.BUILDER,new BlockPos(3,1,3));
        c.plan().site=of(h.absolutePos(new BlockPos(6,1,6)));c.task().progress=12;c.plan().progress=12;
        c.worker().getInventory().addItem(new ItemStack(Items.OAK_PLANKS,17));
        c.citizen().experience.put(Role.BUILDER,256L);
        var data=new dev.livingvillages.persistence.VillageSavedData();data.world.villages.put(c.village().id,c.village());
        var tag=data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var loaded=dev.livingvillages.persistence.VillageSavedData.load(tag,h.getLevel().registryAccess());
        h.assertTrue(loaded.world.villages.get(c.village().id).plans.get(c.plan().id).tasks.getFirst().progress==12,"Plan and task progress restored");
        var entityTag=c.worker().saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var restored=new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER,h.getLevel());
        restored.load(entityTag);
        h.assertTrue(restored.getUUID().equals(c.worker().getUUID()),"Vanilla UUID preserved");
        h.assertTrue(Inventories.count(restored.getInventory(),"minecraft:oak_planks")==17,"Vanilla saves working cargo");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void largeChronicleSurvivesCompressedNbt(GameTestHelper h) throws Exception {
        var data=new dev.livingvillages.persistence.VillageSavedData();
        Village v=new Village();v.name="Archive";v.center=new Pos(0,64,0);data.world.villages.put(v.id,v);
        for(int i=0;i<1000;i++)v.history.add(new Event(UUID.randomUUID(),i,"PLAN_COMPLETED",v.id,null,null,
                "Persistent chronicle entry with enough text to exceed the NBT string size limit. "+i,Map.of()));
        var tag=data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var bytes=new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.writeCompressed(tag,bytes);
        var decoded=net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()),
                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var restored=dev.livingvillages.persistence.VillageSavedData.load(decoded,h.getLevel().registryAccess());
        h.assertTrue(restored.world.villages.get(v.id).history.size()==1000,"Large history survives real NBT serialization");h.succeed();
    }
    @GameTest(template="empty")
    public static void reservationsProtectAnotherPlansPhysicalItems(GameTestHelper h) {
        floor(h);h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.SMITH,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        var chest=Inventories.container(h.getLevel(),c.village().storage.getFirst());
        chest.setItem(0,new ItemStack(Items.OAK_PLANKS,4));
        Plan other=new Plan();other.kind="Other";other.reservations.put("minecraft:oak_planks",3);c.village().plans.put(other.id,other);
        h.assertTrue(!c.fetch("minecraft:oak_planks",2),"Cannot take another plan's reservation");
        h.assertTrue(Inventories.count(chest,"minecraft:oak_planks")==4,"No reserved items moved");
        other.transition(State.CANCELLED);other.reservations.clear();
        h.assertTrue(c.fetch("minecraft:oak_planks",2),"Released reservation makes stock available");
        h.assertTrue(Inventories.count(chest,"minecraft:oak_planks")==2,"Only committed items moved");h.succeed();
    }
    @GameTest(template="empty")
    public static void playerObstructionDoesNotSpendMaterial(GameTestHelper h) {
        floor(h);
        WorkContext c=context(h,Role.BUILDER,new BlockPos(3,1,3));
        c.plan().site=of(h.absolutePos(new BlockPos(4,1,4)));c.plan().blueprint="house_t1_01";
        c.worker().getInventory().addItem(new ItemStack(Items.COBBLESTONE,16));
        h.setBlock(4,1,4,Blocks.DIAMOND_BLOCK);
        new ConstructionJob(new Blueprints()).tick(c);
        h.assertTrue(c.task().blocker.type()==Reason.BUILD_SITE_OBSTRUCTED,"Player obstruction is explicit");
        h.assertTrue(Inventories.count(c.worker().getInventory(),"minecraft:cobblestone")==16,"Material remains");
        h.assertBlockPresent(Blocks.DIAMOND_BLOCK,new BlockPos(4,1,4));h.succeed();
    }
    @GameTest(template="empty")
    public static void deathReleasesTaskAndDropsCargo(GameTestHelper h) {
        floor(h);WorkContext c=context(h,Role.MINER,new BlockPos(3,1,3));
        var data=dev.livingvillages.persistence.VillageSavedData.get(h.getLevel());
        data.world.villages.put(c.village().id,c.village());
        c.worker().getInventory().addItem(new ItemStack(Items.COAL,7));
        c.worker().hurt(c.worker().damageSources().genericKill(),1000);
        h.assertTrue(c.citizen().dead,"Citizen marked dead");
        h.assertTrue(c.task().worker==null,"Task releases dead worker");
        h.assertTrue(c.task().blocker.type()==Reason.WORKER_UNAVAILABLE,"Worker loss diagnosed");
        h.assertTrue(c.worker().getInventory().isEmpty(),"Dropped cargo removed from entity inventory");
        h.assertTrue(c.village().history.stream().anyMatch(e->e.type().equals("CITIZEN_DIED")),"Death chronicle");
        data.world.villages.remove(c.village().id);data.setDirty();h.succeed();
    }
    @GameTest(template="empty")
    public static void animalCapDoesNotConsumeFood(GameTestHelper h) {
        floor(h);WorkContext c=context(h,Role.ANIMAL_KEEPER,new BlockPos(3,1,3));
        for(int i=0;i<dev.livingvillages.config.VillageConfig.ANIMAL_CAP.get();i++)
            h.spawn(EntityType.COW,new BlockPos(5+i%4,1,5+i/4));
        c.worker().getInventory().addItem(new ItemStack(Items.WHEAT,2));
        new AnimalJob().tick(c);
        h.assertTrue(c.plan().state()==State.COMPLETED,"Cap prevents breeding action");
        h.assertTrue(Inventories.count(c.worker().getInventory(),"minecraft:wheat")==2,"No food spent at cap");h.succeed();
    }
    @GameTest(template="empty",batch="registration",timeoutTicks=200)
    public static void bellVillageRegistersExactlyOnce(GameTestHelper h) {
        floor(h);h.setBlock(8,1,8,Blocks.BELL);
        h.spawn(EntityType.VILLAGER,new BlockPos(7,1,8));h.spawn(EntityType.VILLAGER,new BlockPos(9,1,8));
        var player=h.makeMockServerPlayerInLevel();
        var pos=h.absolutePos(new BlockPos(8,1,7));player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var world=new World();var discovery=new dev.livingvillages.world.Discovery(new Chronicle());
        h.runAfterDelay(5,()->{
            discovery.discover(h.getLevel(),world);discovery.discover(h.getLevel(),world);
            h.assertTrue(world.villages.size()==1,"One village for the same bell");
            Village v=world.villages.values().iterator().next();
            h.assertTrue(v.citizens.size()>=2,"Real villagers enrolled");
            UUID id=v.id;
            World restored=StateCodec.decode(StateCodec.encode(world));
            discovery.discover(h.getLevel(),restored);
            h.assertTrue(restored.villages.size()==1&&restored.villages.containsKey(id),"No duplicate after reload");
            h.getLevel().getServer().getPlayerList().remove(player);
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void furnaceConsumesRealFuelAndProducesIron(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(2,1,3,Blocks.CHEST);h.setBlock(4,1,3,Blocks.FURNACE);
        WorkContext c=context(h,Role.SMITH,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().furnaces.add(of(h.absolutePos(new BlockPos(4,1,3))));
        var chest=Inventories.container(h.getLevel(),c.village().storage.getFirst());
        chest.setItem(0,new ItemStack(Items.RAW_IRON));chest.setItem(1,new ItemStack(Items.COAL));
        Job job=new SmeltingJob();h.onEachTick(()->{if(h.getTick()%10==0)step(c,job);});
        h.succeedWhen(()->{
            h.assertTrue(Inventories.count(chest,"minecraft:iron_ingot")==1,"Furnace produced iron");
            h.assertTrue(Inventories.count(chest,"minecraft:raw_iron")==0&&Inventories.count(chest,"minecraft:coal")==0,"Raw material and fuel consumed");
        });
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void boundedPenBreedingConsumesFood(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        for(int i=5;i<=9;i++) { h.setBlock(i,1,5,Blocks.OAK_FENCE);h.setBlock(i,1,9,Blocks.OAK_FENCE);
            h.setBlock(5,1,i,Blocks.OAK_FENCE);h.setBlock(9,1,i,Blocks.OAK_FENCE); }
        WorkContext c=context(h,Role.ANIMAL_KEEPER,new BlockPos(7,1,7));
        var a=h.spawn(EntityType.COW,new BlockPos(6,1,7));var b=h.spawn(EntityType.COW,new BlockPos(8,1,7));
        c.worker().getInventory().addItem(new ItemStack(Items.WHEAT,2));
        Job job=new AnimalJob();h.onEachTick(()->{if(h.getTick()%10==0)step(c,job);});
        h.succeedWhen(()->{
            h.assertTrue(c.plan().state()==State.COMPLETED,"Breeding action completed");
            h.assertTrue(Inventories.count(c.worker().getInventory(),"minecraft:wheat")==0,"Two food items consumed");
            h.assertTrue(a.isInLove()||a.getAge()>0,"Vanilla breeding started");
            h.assertTrue(b.isInLove()||b.getAge()>0,"Both parents fed");
        });
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void farmerHarvestsReplantsAndDelivers(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        h.setBlock(8,0,8,Blocks.FARMLAND);h.setBlock(8,1,8,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.FARMER,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().crops.add(of(h.absolutePos(new BlockPos(8,1,8))));
        c.worker().getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS,1));
        Job job=new HarvestJob();h.onEachTick(()->{if(h.getTick()%10==0)step(c,job);});
        h.succeedWhen(()->{
            h.assertTrue(Inventories.stock(h.getLevel(),c.village()).getOrDefault("minecraft:wheat",0)>0,"Harvest delivered: "+describe(c));
            h.assertTrue(h.getBlockState(new BlockPos(8,1,8)).is(Blocks.WHEAT),"Crop replanted");
            h.assertTrue(c.citizen().experience.getOrDefault(Role.FARMER,0L)>0,"Farmer XP");
        });
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void smithUsesVanillaRecipeAndExactIngredients(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(2,1,3,Blocks.CHEST);h.setBlock(4,1,3,Blocks.CRAFTING_TABLE);
        WorkContext c=context(h,Role.SMITH,new BlockPos(3,1,3));c.task().item="minecraft:wooden_pickaxe";
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().tables.add(of(h.absolutePos(new BlockPos(4,1,3))));
        var chest=Inventories.container(h.getLevel(),c.village().storage.getFirst());
        chest.setItem(0,new ItemStack(Items.OAK_PLANKS,3));chest.setItem(1,new ItemStack(Items.STICK,2));
        Job job=new ProductionJob();h.onEachTick(()->{if(h.getTick()%10==0)step(c,job);});
        h.succeedWhen(()->{
            h.assertTrue(Inventories.count(chest,"minecraft:wooden_pickaxe")==1,"Crafted real tool: "+describe(c));
            h.assertTrue(Inventories.count(chest,"minecraft:oak_planks")==0&&Inventories.count(chest,"minecraft:stick")==0,"Ingredients consumed");
        });
    }
    @GameTest(template="empty",batch="autonomy",timeoutTicks=40000,skyAccess=true)
    public static void villageAutonomouslyPlansAssignsAndBuildsHouse(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,h.getLevel().getServer());
        h.setBlock(2,1,3,Blocks.CHEST);h.setBlock(4,1,3,Blocks.CRAFTING_TABLE);
        WorkContext setup=context(h,Role.BUILDER,new BlockPos(3,1,3));
        Village village=setup.village();village.plans.clear();setup.citizen().currentTask=null;
        village.center=of(h.absolutePos(new BlockPos(8,1,8)));village.radius=16;
        village.createdAt=h.getLevel().getGameTime()-2000;
        village.storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        var chest=Inventories.container(h.getLevel(),village.storage.getFirst());
        Map<String,Integer> supplies=new TreeMap<>(new Blueprints().get("house_t1_01").manifest());
        supplies.remove("minecraft:oak_planks"); // The planner must obtain these through real crafting.
        supplies.put("minecraft:oak_log",32);supplies.put("minecraft:cobblestone",192);
        supplies.put("minecraft:bread",64);supplies.put("minecraft:wooden_pickaxe",3);
        supplies.forEach((item,n)->Inventories.insert(chest,new ItemStack(Inventories.item(item),n)));
        var data=dev.livingvillages.persistence.VillageSavedData.get(h.getLevel());
        data.world.villages.put(village.id,village);data.setDirty();
        h.runAtTickTime(39999,()->{data.world.villages.remove(village.id);data.setDirty();});
        h.succeedWhen(()->{
            h.assertTrue(village.buildings.values().stream().anyMatch(b->b.category.equals("HOUSING")),
                    "Autonomous housing: worker="+setup.worker().position()+" "+
                            village.plans.values().stream().map(p->p.kind+" "+p.state()+" "+p.progress+" "+p.blocker).toList());
            h.assertTrue(village.history.stream().anyMatch(e->e.type().equals("PLAN_CREATED")),"Planner emitted goal plan");
            h.assertTrue(village.history.stream().anyMatch(e->e.type().equals("TASK_ASSIGNED")),"Scheduler assigned a citizen");
            h.assertTrue(village.plans.values().stream().anyMatch(p->p.kind.equals("CRAFT:minecraft:oak_planks")&&p.state()==State.COMPLETED),
                    "Housing dependencies caused real material production");
            data.world.villages.remove(village.id);data.setDirty();
        });
    }
    @GameTest(template="empty")
    public static void constructionCannotPlaceWithoutMaterials(GameTestHelper h) {
        floor(h);
        WorkContext c=context(h,Role.BUILDER,new BlockPos(4,1,3));
        c.plan().blueprint="house_t1_01";c.plan().site=of(h.absolutePos(new BlockPos(4,1,4)));
        new ConstructionJob(new Blueprints()).tick(c);
        h.assertTrue(h.getBlockState(new BlockPos(4,1,4)).isAir(),"No free floor block");
        h.assertTrue(c.task().blocker!=null,"Missing resources diagnosed");h.succeed();
    }
    @GameTest(template="empty")
    public static void unloadedTargetDoesNotForceLoadTerrain(GameTestHelper h) {
        WorkContext c=context(h,Role.LUMBERJACK,new BlockPos(3,1,3));
        Pos far=c.village().center.offset(100000,0,100000);
        h.assertTrue(!h.getLevel().hasChunkAt(block(far)),"Fixture chunk starts unloaded");
        c.task().target=far;
        h.assertTrue(!c.loaded(far),"Physical work waits");
        h.assertTrue(c.task().blocker.type()==Reason.CHUNK_UNAVAILABLE,"Chunk blocker is structured");
        h.assertTrue(!h.getLevel().hasChunkAt(block(far)),"No terrain was force loaded");h.succeed();
    }
    @GameTest(template="empty",skyAccess=true)
    public static void siteValidationRejectsWaterAndPlayerStructures(GameTestHelper h) {
        floor(h);WorkContext c=context(h,Role.BUILDER,new BlockPos(3,1,3));
        var bp=new Blueprints().get("house_t1_01");var sites=new SitePlanner();
        Pos site=of(h.absolutePos(new BlockPos(6,1,6)));
        h.assertTrue(sites.validate(c,bp,site).isEmpty(),"Flat supported site accepted");
        h.setBlock(6,0,6,Blocks.WATER);
        h.assertTrue(sites.validate(c,bp,site).equals("WATER"),"Water rejected");
        h.setBlock(6,0,6,Blocks.GRASS_BLOCK);h.setBlock(6,1,6,Blocks.CHEST);
        h.assertTrue(!sites.validate(c,bp,site).isEmpty(),"Existing container/building rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void lumberjackWalksHarvestsCollectsAndDelivers(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        h.setBlock(9,1,8,Blocks.OAK_LOG);h.setBlock(9,2,8,Blocks.OAK_LOG);h.setBlock(9,3,8,Blocks.OAK_LOG);
        h.setBlock(9,4,8,Blocks.OAK_LEAVES);h.setBlock(10,3,8,Blocks.OAK_LEAVES);
        h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.LUMBERJACK,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().logs.add(of(h.absolutePos(new BlockPos(9,1,8))));
        Job job=new HarvestJob();
        h.onEachTick(()->{
            if(h.getTick()%10==0)step(c,job);
        });
        h.succeedWhen(()->{
            h.assertTrue(h.getBlockState(new BlockPos(9,1,8)).isAir(),"Real tree block harvested");
            h.assertTrue(Inventories.stock(h.getLevel(),c.village()).getOrDefault("minecraft:oak_log",0)>0,"Real log delivered: "+describe(c));
            h.assertTrue(c.citizen().experience.getOrDefault(Role.LUMBERJACK,0L)>0,"Legitimate XP gained");
        });
    }
    @GameTest(template="empty",timeoutTicks=15000)
    public static void builderCompletesRealHouseProgressively(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.BUILDER,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        var blueprints=new Blueprints();var bp=blueprints.get("house_t1_01");
        c.plan().blueprint=bp.id();c.plan().site=of(h.absolutePos(new BlockPos(6,1,6)));
        var chest=Inventories.container(h.getLevel(),c.village().storage.getFirst());
        bp.manifest().forEach((id,n)->Inventories.insert(chest,new ItemStack(Inventories.item(id),n)));
        Job job=new ConstructionJob(blueprints);
        var current=new java.util.concurrent.atomic.AtomicReference<>(c);
        boolean[] reloaded={false};
        h.onEachTick(()->{
            if(h.getTick()%10==0)step(current.get(),job);
            if(!reloaded[0]&&current.get().task().progress>=20) {
                var saved=new dev.livingvillages.persistence.VillageSavedData();saved.world.villages.put(c.village().id,current.get().village());
                var loaded=dev.livingvillages.persistence.VillageSavedData.load(
                        saved.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
                Village v=loaded.world.villages.get(c.village().id);Plan p=v.plans.get(c.plan().id);Task t=p.tasks.getFirst();
                current.set(new WorkContext(h.getLevel(),v,p,t,v.citizens.get(c.citizen().id),c.worker(),c.observations(),new Chronicle()));
                reloaded[0]=true;
            }
        });
        h.runAtTickTime(50,()->h.assertTrue(c.task().progress<bp.cells().size(),"House does not appear instantly"));
        h.succeedWhen(()->{
            WorkContext restored=current.get();
            h.assertTrue(restored.plan().state()==State.COMPLETED,"House completed: "+describe(restored));
            h.assertTrue(reloaded[0],"Mid-construction state restored");
            h.assertTrue(restored.village().buildings.size()==1,"Completed building registered");
            h.assertTrue(Inventories.stock(h.getLevel(),restored.village()).isEmpty(),"Manifest consumed exactly");
            h.assertTrue(restored.village().history.stream().anyMatch(e->e.type().equals("BUILDING_COMPLETED")),"Chronicle records completion");
        });
    }
    private static void floor(GameTestHelper h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(x,0,z,Blocks.GRASS_BLOCK);
    }
    @GameTest(template="empty",timeoutTicks=3000)
    public static void minerUsesActualToolLootAndDelivers(GameTestHelper h) {
        floor(h);h.setDayTime(1000);h.setBlock(9,1,8,Blocks.STONE);h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.MINER,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().stone.add(of(h.absolutePos(new BlockPos(9,1,8))));
        c.worker().getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        Job job=new HarvestJob();h.onEachTick(()->{if(h.getTick()%10==0)step(c,job);});
        h.succeedWhen(()->{
            h.assertTrue(h.getBlockState(new BlockPos(9,1,8)).isAir(),"Actual stone removed");
            h.assertTrue(Inventories.stock(h.getLevel(),c.village()).getOrDefault("minecraft:cobblestone",0)==1,
                    "Tool-dependent loot delivered: "+describe(c));
            h.assertTrue(c.worker().getMainHandItem().getDamageValue()==1,"Real pickaxe durability consumed");
            h.assertTrue(c.citizen().experience.getOrDefault(Role.MINER,0L)>0,"Meaningful mining grants XP");
        });
    }
    @GameTest(template="empty")
    public static void ledgerSnapshotPreservesDiagnosticsAndPermissions(GameTestHelper h) {
        WorkContext c=context(h,Role.BUILDER,new BlockPos(3,1,3));
        c.block(Reason.MISSING_RESOURCE,"minecraft:oak_planks required=64 available=0",null,"minecraft:oak_planks",64,0);
        var regular=dev.livingvillages.network.LedgerView.create(h.getLevel(),c.village(),false);
        h.assertTrue(regular.tabs().keySet().equals(Set.of("Overview","Citizens","Resources","Buildings","Plans","Chronicle")),
                "All six player tabs without admin diagnostics");
        var admin=dev.livingvillages.network.LedgerView.parse(
                dev.livingvillages.network.LedgerView.create(h.getLevel(),c.village(),true).json());
        h.assertTrue(admin.village().equals(c.village().id),"Snapshot identity survives network serialization");
        h.assertTrue(admin.tabs().containsKey("Diagnostics"),"Admin diagnostics included");
        h.assertTrue(admin.tabs().get("Plans").getFirst().detail().stream().anyMatch(s->s.contains("MISSING_RESOURCE")),
                "Plan blocker is visible to players");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=6000)
    public static void unreachableTreeBacksOffAndRecoversWhenOpened(GameTestHelper h) {
        floor(h);h.setDayTime(1000);
        h.setBlock(8,1,8,Blocks.OAK_LOG);h.setBlock(8,2,8,Blocks.OAK_LOG);h.setBlock(8,3,8,Blocks.OAK_LOG);
        h.setBlock(8,4,8,Blocks.OAK_LEAVES);h.setBlock(9,3,8,Blocks.OAK_LEAVES);
        for(int y=1;y<=6;y++)for(int i=5;i<=11;i++) {
            h.setBlock(i,y,5,Blocks.STONE);h.setBlock(i,y,11,Blocks.STONE);
            h.setBlock(5,y,i,Blocks.STONE);h.setBlock(11,y,i,Blocks.STONE);
        }
        h.setBlock(2,1,3,Blocks.CHEST);
        WorkContext c=context(h,Role.LUMBERJACK,new BlockPos(3,1,3));
        c.village().storage.add(of(h.absolutePos(new BlockPos(2,1,3))));
        c.observations().logs.add(of(h.absolutePos(new BlockPos(8,1,8))));
        Job job=new HarvestJob();boolean[] blocked={false};
        h.onEachTick(()->{
            if(h.getTick()%10==0)step(c,job);
            if(c.task().state()==State.BLOCKED)blocked[0]=true;
        });
        h.runAtTickTime(650,()->{
            h.assertTrue(blocked[0],"Unreachable target escalates to BLOCKED");
            h.assertTrue(c.task().attempts<=4,"Retries are spaced rather than every tick");
            h.assertTrue(c.task().blocker.type()==Reason.PATH_UNREACHABLE,"Route failure is explained");
            h.assertTrue(c.plan().blocker!=null,"Parent plan exposes blocker");
            for(int y=1;y<=6;y++)for(int i=5;i<=11;i++) {
                h.setBlock(i,y,5,Blocks.AIR);h.setBlock(i,y,11,Blocks.AIR);
                h.setBlock(5,y,i,Blocks.AIR);h.setBlock(11,y,i,Blocks.AIR);
            }
        });
        h.succeedWhen(()->{
            h.assertTrue(blocked[0],"Previously blocked");
            h.assertTrue(Inventories.stock(h.getLevel(),c.village()).getOrDefault("minecraft:oak_log",0)>0,
                    "Recovered without editing plan data: "+describe(c));
            h.assertTrue(c.task().blocker==null,"Progress clears obsolete blocker");
        });
    }
    private static void step(WorkContext c,Job job) {
        if(c.plan().state().terminal()||c.now()<c.task().nextRetryTick)return;
        if(c.task().nextRetryTick>0) { c.task().attemptStartedAt=c.now();c.task().nextRetryTick=0; }
        job.tick(c);
    }
    private static String describe(WorkContext c) {
        return "progress="+c.task().progress+" phase="+c.task().phase+" statistics="+c.citizen().statistics+" position="+c.worker().position()+
                " inventory="+c.worker().getInventory()+" target="+c.task().target+" blocker="+c.task().blocker;
    }
    private static WorkContext context(GameTestHelper h,Role role,BlockPos spawn) {
        var worker=h.spawn(EntityType.VILLAGER,spawn);
        worker.setAge(0);
        Village v=new Village();v.center=of(h.absolutePos(new BlockPos(3,1,3)));v.name="Test";v.radius=48;
        Citizen citizen=new Citizen();citizen.id=worker.getUUID();citizen.village=v.id;citizen.role=role;citizen.name="Test worker";
        v.citizens.put(citizen.id,citizen);
        Plan plan=new Plan();plan.kind=role.toString();plan.village=v.id;plan.lastProgressTick=h.getLevel().getGameTime();v.plans.put(plan.id,plan);
        Task task=new Task();task.role=role;task.plan=plan.id;task.worker=citizen.id;task.lastProgressTick=plan.lastProgressTick;
        plan.tasks.add(task);citizen.currentTask=task.id;
        return new WorkContext(h.getLevel(),v,plan,task,citizen,worker,new Observations(),new Chronicle());
    }
}
