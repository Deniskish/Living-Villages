package dev.livingvillages.task;
import com.google.common.collect.ImmutableList;
import dev.livingvillages.domain.Model.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

/** Vanilla CORE locomotion stays active; competing daytime work is suspended temporarily. */
public final class WorkerNavigation {
    public static final DeferredRegister<Activity> ACTIVITIES=DeferredRegister.create(Registries.ACTIVITY,"livingvillages");
    public static final DeferredHolder<Activity,Activity> WORK=ACTIVITIES.register("work",()->new Activity("livingvillages:work"));
    private WorkerNavigation() {}
    public record Approach(BlockPos feet,Path path) {}
    public static Approach approach(ServerLevel level,Villager worker,Pos target) {
        return approach(level,worker,target,null);
    }
    public static Approach approach(ServerLevel level,Villager worker,Pos target,Pos interaction) {
        List<BlockPos> positions=new ArrayList<>();
        for(int dy=-1;dy<=1;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            BlockPos pos=block(target).offset(dx,dy,dz);
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.above()))continue;
            if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                    ||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                    ||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                    ||!level.getFluidState(pos).isEmpty())continue;
            if(pos.distSqr(block(target))>5)continue;
            if(interaction!=null&&!visible(level,worker,new Vec3(pos.getX()+0.5,pos.getY()+worker.getEyeHeight(),pos.getZ()+0.5),interaction))continue;
            positions.add(pos);
        }
        positions.sort(Comparator.comparingDouble(p->worker.distanceToSqr(p.getX()+0.5,p.getY(),p.getZ()+0.5)));
        for(BlockPos pos:positions.stream().limit(6).toList()) {
            Path path=worker.getNavigation().createPath(pos,0);
            if(path!=null&&path.canReach())return new Approach(pos,path);
        }
        return null;
    }
    public static boolean visible(ServerLevel level,Villager worker,Vec3 eye,Pos target) {
        for(Vec3 point:List.of(new Vec3(.5,.5,.5),new Vec3(.5,.95,.5),new Vec3(.5,.5,.95),
                new Vec3(.5,.5,.05),new Vec3(.95,.5,.5),new Vec3(.05,.5,.5))) {
            Vec3 end=point.add(target.x(),target.y(),target.z());
            if(eye.distanceToSqr(end)>12.25)continue;
            var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,worker));
            if(hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(block(target)))return true;
        }
        return false;
    }
    public static void start(ServerLevel level,Villager worker,Approach approach) {
        var brain=worker.getBrain();
        if(!brain.isActive(WORK.get())) {
            brain.stopAll(level,worker);
            brain.addActivity(WORK.get(),0,ImmutableList.of());
            brain.setActiveActivityIfPossible(WORK.get());
        }
        brain.setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach.feet(),0.6F,0));
        worker.getNavigation().moveTo(approach.path(),0.6);
    }
    public static void release(ServerLevel level,Villager worker) {
        worker.getNavigation().stop();
        worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        worker.getBrain().useDefaultActivity();
        worker.getBrain().updateActivityFromSchedule(level.getDayTime(),level.getGameTime());
    }
}
