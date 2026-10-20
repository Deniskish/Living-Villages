package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Rules;
import dev.livingvillages.resource.Inventories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

/** A loaded-world gather loop; every drop comes from the block loot table. */
public final class HarvestJob implements Job {
    @Override public void tick(WorkContext c) {
        Task t=c.task();
        if(t.phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        if(t.phase.equals("COLLECT")) {
            Pos pickup=t.role==Role.LUMBERJACK&&t.workOrigin!=null?t.workOrigin:t.target;
            if(c.now()-t.actionStarted<40||!c.loaded(pickup)||!c.move(pickup))return;
            var drops=c.level().getEntitiesOfClass(ItemEntity.class,new AABB(block(pickup)).inflate(2,5,2));
            for(ItemEntity drop:drops) {
                if(drop.getY()>pickup.y()+1.5&&t.targets.size()>1)continue;
                if(c.worker().distanceToSqr(drop)>2.25) {
                    var path=c.worker().getNavigation().createPath(drop.blockPosition(),0);
                    if(path!=null&&path.canReach())WorkerNavigation.start(c.level(),c.worker(),new WorkerNavigation.Approach(drop.blockPosition(),path));
                    else c.block(Reason.PATH_UNREACHABLE,"Cannot reach harvested item",of(drop.blockPosition()),Inventories.id(drop.getItem()),1,0);
                    return;
                }
                ItemStack stack=drop.getItem();
                if(Inventories.insert(c.worker().getInventory(),stack)>0) {
                    if(stack.isEmpty())drop.discard();else drop.setItem(stack);
                    c.progress();
                }
                if(!stack.isEmpty()) { c.block(Reason.STORAGE_FULL,"Worker inventory cannot accept harvested items");return; }
            }
            replant(c);
            t.progress++;
            c.plan().progress=t.progress;c.plan().totalWork=8;
            t.target=null;t.actionStarted=0;
            t.phase=t.progress>=8?"DELIVER":"SELECT";
            return;
        }
        if(t.target==null) {
            if(t.role==Role.LUMBERJACK&&!t.targets.isEmpty()) {
                t.targets.removeIf(p->!c.level().hasChunkAt(block(p))||!c.level().getBlockState(block(p)).is(BlockTags.LOGS));
                t.target=t.targets.stream().max(Comparator.comparingInt(Pos::y)).orElse(null);
            }
            Set<Pos> candidates=switch(t.role) {
                case LUMBERJACK -> c.observations().logs;
                case MINER -> c.observations().stone;
                default -> c.observations().crops;
            };
            if(t.target==null)t.target=candidates.stream().filter(p->valid(c,p)).sorted(Comparator.comparingLong(p->p.distanceSquared(of(c.worker().blockPosition()))))
                    .limit(6).filter(p-> {
                        return WorkerNavigation.approach(c.level(),c.worker(),p)!=null;
                    }).findFirst().orElse(null);
            if(t.target==null) {
                if(t.progress>0) { t.phase="DELIVER";return; }
                c.block(Reason.PATH_UNREACHABLE,"No reachable "+t.role+" target in currently indexed loaded area");return;
            }
            if(t.role==Role.LUMBERJACK&&t.targets.isEmpty()) {
                BlockPos root=block(t.target);
                while(c.level().getBlockState(root.below()).is(BlockTags.LOGS))root=root.below();
                t.workOrigin=of(root);
                for(int y=0;y<5&&c.level().getBlockState(root.above(y)).is(BlockTags.LOGS);y++)t.targets.add(of(root.above(y)));
                t.target=t.targets.getLast();
            }
            t.phase="HARVEST";c.progress();
        }
        if(!c.loaded(t.target))return;
        if(!valid(c,t.target)) { t.target=null;c.block(Reason.TARGET_MISSING,"Harvest target changed");return; }
        if(!tool(c))return;
        if(!c.interactAt(t.target,t.role==Role.LUMBERJACK&&t.workOrigin!=null?t.workOrigin.y():t.target.y()))return;
        if(t.actionStarted==0) { t.actionStarted=c.now();c.progress();return; }
        c.worker().swing(InteractionHand.MAIN_HAND);
        int duration=Math.max(20,80-c.citizen().level()*10);
        if(t.role==Role.LUMBERJACK&&c.worker().getMainHandItem().getItem() instanceof AxeItem)duration=Math.max(20,duration/2);
        if(c.now()-t.actionStarted<duration)return;
        BlockPos pos=block(t.target);
        var state=c.level().getBlockState(pos);
        if(state.requiresCorrectToolForDrops()&&!c.worker().getMainHandItem().isCorrectToolForDrops(state)) {
            c.block(Reason.MISSING_TOOL,"Tool cannot harvest "+BuiltInRegistries.BLOCK.getKey(state.getBlock()));return;
        }
        var blockEntity=c.level().getBlockEntity(pos);
        if(c.level().destroyBlock(pos,false,c.worker())) {
            // Level.destroyBlock(..., true) supplies ItemStack.EMPTY to loot tables.
            // Use the real equipped tool so mined stone/ore yields legitimate drops.
            Block.dropResources(state,c.level(),pos,blockEntity,c.worker(),c.worker().getMainHandItem());
            if(!c.worker().getMainHandItem().isEmpty())
                c.worker().getMainHandItem().hurtAndBreak(1,c.worker(),EquipmentSlot.MAINHAND);
            // Retain exact crop identity for replanting after collecting the actual drops.
            t.item=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            t.phase="COLLECT";t.actionStarted=c.now();c.progress();c.xp(2);
        } else c.block(Reason.WORLD_CHANGED,"World rejected harvest");
    }
    private boolean valid(WorkContext c,Pos p) {
        if(!c.village().contains(p)||!c.level().hasChunkAt(block(p)))return false;
        var s=c.level().getBlockState(block(p));
        if(c.village().buildings.values().stream().anyMatch(b->b.origin.distanceSquared(p)<100))return false;
        if(c.task().role==Role.FARMER)return s.getBlock() instanceof CropBlock crop&&crop.isMaxAge(s);
        if(c.task().role==Role.MINER) {
            if(c.task().desiredResource.equals("minecraft:raw_iron")&&!s.is(BlockTags.IRON_ORES))return false;
            if(c.task().desiredResource.equals("minecraft:coal")&&!s.is(BlockTags.COAL_ORES))return false;
            if(Rules.miningLevel(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString())>c.citizen().level())return false;
            if(p.y()<c.village().center.y()-6||p.y()>=c.worker().blockPosition().getY()+2)return false;
            // Only exposed faces; no tunnels, undermining, fluid/gravity adjacency or block entities.
            boolean exposed=false;
            for(Direction d:Direction.values()) {
                BlockPos n=block(p).relative(d);
                if(!c.level().hasChunkAt(n))return false;
                var adjacent=c.level().getBlockState(n);
                if(!adjacent.getFluidState().isEmpty()||adjacent.getBlock() instanceof FallingBlock)return false;
                if(d.getAxis().isHorizontal()&&adjacent.isAir())exposed=true;
            }
            return exposed&&c.level().getBlockEntity(block(p))==null
                    &&!block(p).equals(c.worker().blockPosition().below());
        }
        if(!s.is(BlockTags.LOGS))return false;
        if(c.task().targets.contains(p))return true;
        // Conservative natural-tree evidence: a soil-rooted vertical trunk plus nearby leaves.
        BlockPos root=block(p);
        for(int i=0;i<8&&c.level().getBlockState(root.below()).is(BlockTags.LOGS);i++)root=root.below();
        if(!c.level().getBlockState(root.below()).is(BlockTags.DIRT))return false;
        if(c.level().getBlockState(root.above(5)).is(BlockTags.LOGS))return false;
        for(int y=0;y<8;y++)for(Direction d:Direction.Plane.HORIZONTAL) {
            BlockPos leaf=root.above(y).relative(d);
            if(c.level().hasChunkAt(leaf)&&c.level().getBlockState(leaf).is(BlockTags.LEAVES))return true;
        }
        return false;
    }
    private boolean tool(WorkContext c) {
        if(c.task().role==Role.LUMBERJACK) {
            if(c.worker().getMainHandItem().getItem() instanceof AxeItem)return true;
            for(String id:List.of("minecraft:iron_axe","minecraft:stone_axe","minecraft:wooden_axe")) {
                if(Inventories.count(c.worker().getInventory(),id)==0&&Inventories.stock(c.level(),c.village()).getOrDefault(id,0)>0)
                    if(!c.fetch(id,1))return false;
                for(int i=0;i<c.worker().getInventory().getContainerSize();i++)if(c.worker().getInventory().getItem(i).getItem() instanceof AxeItem) {
                    if(!c.worker().getMainHandItem().isEmpty())Inventories.giveOrDrop(c.worker(),c.worker().getMainHandItem().copy());
                    c.worker().setItemSlot(EquipmentSlot.MAINHAND,c.worker().getInventory().removeItem(i,1));c.equipped();return true;
                }
            }
            return true; // Vanilla allows unarmed log harvesting during bootstrap.
        }
        if(c.task().role!=Role.MINER)return true;
        var target=c.level().getBlockState(block(c.task().target));
        if(c.worker().getMainHandItem().getItem() instanceof PickaxeItem
                &&(!target.requiresCorrectToolForDrops()||c.worker().getMainHandItem().isCorrectToolForDrops(target)))return true;
        for(int i=0;i<c.worker().getInventory().getContainerSize();i++) {
            if(c.worker().getInventory().getItem(i).getItem() instanceof PickaxeItem
                    &&(!target.requiresCorrectToolForDrops()||c.worker().getInventory().getItem(i).isCorrectToolForDrops(target))) {
                if(!c.worker().getMainHandItem().isEmpty())Inventories.giveOrDrop(c.worker(),c.worker().getMainHandItem().copy());
                c.worker().setItemSlot(EquipmentSlot.MAINHAND,c.worker().getInventory().removeItem(i,1));c.equipped();return true;
            }
        }
        String wanted=target.is(BlockTags.NEEDS_IRON_TOOL)?"minecraft:iron_pickaxe":
                target.is(BlockTags.NEEDS_STONE_TOOL)?"minecraft:stone_pickaxe":"minecraft:wooden_pickaxe";
        if(c.fetch(wanted,1))return tool(c);
        return false;
    }
    private void replant(WorkContext c) {
        Task t=c.task();BlockPos pos=block(t.target);
        if(!c.level().getBlockState(pos).isAir())return;
        Block plant=null;Item seed=null;
        if(t.role==Role.FARMER) {
            switch(t.item) {
                case "minecraft:wheat" -> { plant=Blocks.WHEAT;seed=Items.WHEAT_SEEDS; }
                case "minecraft:carrots" -> { plant=Blocks.CARROTS;seed=Items.CARROT; }
                case "minecraft:potatoes" -> { plant=Blocks.POTATOES;seed=Items.POTATO; }
                case "minecraft:beetroots" -> { plant=Blocks.BEETROOTS;seed=Items.BEETROOT_SEEDS; }
            }
        } else if(t.role==Role.LUMBERJACK&&c.level().getBlockState(pos.below()).is(BlockTags.DIRT)) {
            String wood=t.item.replace("_log","_sapling");
            var candidate=BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(wood));
            if(candidate instanceof SaplingBlock) { plant=candidate;seed=candidate.asItem(); }
        }
        if(plant==null||seed==null||!plant.defaultBlockState().canSurvive(c.level(),pos))return;
        String id=BuiltInRegistries.ITEM.getKey(seed).toString();
        if(Inventories.count(c.worker().getInventory(),id)>0&&c.level().setBlock(pos,plant.defaultBlockState(),3)) {
            Inventories.consume(c.worker().getInventory(),Map.of(id,1));c.xp(1);
        }
    }
}
