package dev.livingvillages.task;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class AnimalJob implements Job {
    @Override public void tick(WorkContext c) {
        List<Animal> animals=c.level().getEntitiesOfClass(Animal.class,new AABB(block(c.village().center)).inflate(c.village().radius),
                a->a instanceof Cow||a instanceof Sheep||a instanceof Pig||a instanceof Chicken);
        // Reserve room for all pregnancies, not just currently spawned animals.
        long pending=animals.stream().filter(Animal::isInLove).count();
        if(animals.size()+pending>=VillageConfig.ANIMAL_CAP.get()) { c.finish();return; }
        Animal first=null,second=null;
        outer:for(Animal a:animals) {
            if(!a.canFallInLove()||!enclosed(c,a.blockPosition()))continue;
            for(Animal b:animals) if(b!=a&&b.getType()==a.getType()&&b.canFallInLove()&&a.distanceToSqr(b)<64) {
                first=a;second=b;break outer;
            }
        }
        if(first==null) { c.block(Reason.TARGET_MISSING,"No breedable pair in a bounded fenced pen");return; }
        String food=first instanceof Pig?"minecraft:carrot":first instanceof Chicken?"minecraft:wheat_seeds":"minecraft:wheat";
        if(!c.fetch(food,2)||!c.move(of(first.blockPosition())))return;
        if(c.worker().distanceToSqr(second)>6.25) { c.move(of(second.blockPosition()));return; }
        if(Inventories.consume(c.worker().getInventory(),Map.of(food,2))) {
            first.setInLove(null);second.setInLove(null);c.xp(4);c.finish();
        }
    }
    private boolean enclosed(WorkContext c,BlockPos p) {
        // Bounded horizontal flood fill: a pen must close within 81 walkable cells.
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(p);
        while(!queue.isEmpty()) {
            BlockPos next=queue.remove();
            if(!seen.add(next))continue;
            if(seen.size()>81||!c.level().hasChunkAt(next))return false;
            for(Direction d:Direction.Plane.HORIZONTAL) {
                BlockPos n=next.relative(d);var state=c.level().getBlockState(n);
                if(state.is(BlockTags.FENCES)||state.is(BlockTags.FENCE_GATES)||state.isSolid())continue;
                if(!seen.contains(n))queue.add(n);
            }
        }
        return true;
    }
}
