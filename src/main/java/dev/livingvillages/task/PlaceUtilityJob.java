package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
/** Bootstrap actual chest/table before a full storage/smithy building exists. */
public final class PlaceUtilityJob implements Job {
    public void tick(WorkContext c) {
        if(!c.fetch(c.task().item,1))return;
        if(c.task().target==null) {
            for(int r=2;r<7&&c.task().target==null;r++)for(Direction d:Direction.Plane.HORIZONTAL) {
                var p=block(c.village().center).relative(d,r);
                if(!c.level().hasChunkAt(p))continue;
                if(!dev.livingvillages.world.Ownership.owns(c.level(),c.village(),of(p)))continue;
                if(c.village().plans.values().stream().anyMatch(plan->plan.site!=null
                        &&p.getX()>=plan.site.x()&&p.getX()<plan.site.x()+6&&p.getZ()>=plan.site.z()&&p.getZ()<plan.site.z()+6))continue;
                if(c.level().getBlockState(p).isAir()&&c.level().getBlockState(p.below()).isFaceSturdy(c.level(),p.below(),Direction.UP))
                    c.task().target=of(p);
            }
        }
        if(c.task().target==null) { c.block(Reason.NO_VALID_SITE,"No supported utility-block position near bell");return; }
        if(!c.move(c.task().target))return;
        var p=block(c.task().target);
        var item=Inventories.item(c.task().item);
        if(!(item instanceof BlockItem bi)) { c.block(Reason.WORLD_CHANGED,"Utility material is not a block");return; }
        if(!c.level().getBlockState(p).isAir()||!bi.getBlock().defaultBlockState().canSurvive(c.level(),p)) {
            c.task().target=null;c.block(Reason.BUILD_SITE_OBSTRUCTED,"Utility site changed");return;
        }
        if(!c.level().isUnobstructed(bi.getBlock().defaultBlockState(),p,net.minecraft.world.phys.shapes.CollisionContext.empty())) {
            c.block(Reason.BUILD_SITE_OBSTRUCTED,"Utility block would intersect an entity");return;
        }
        if(c.level().setBlock(p,bi.getBlock().defaultBlockState(),3)) {
            Inventories.consume(c.worker().getInventory(),Map.of(c.task().item,1));
            if(bi.getBlock()==Blocks.CHEST)c.village().storage.add(of(p));
            if(bi.getBlock()==Blocks.CRAFTING_TABLE)c.observations().tables.add(of(p));
            if(bi.getBlock()==Blocks.FURNACE)c.observations().furnaces.add(of(p));
            c.xp(2);c.finish();
        } else c.block(Reason.WORLD_CHANGED,"Utility placement rejected");
    }
}
