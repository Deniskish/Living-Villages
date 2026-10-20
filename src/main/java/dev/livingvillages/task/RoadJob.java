package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class RoadJob implements Job {
    public void tick(WorkContext c) {
        Task t=c.task();
        if(t.progress>=t.targets.size()) { c.finish();return; }
        if(!(c.worker().getMainHandItem().getItem() instanceof ShovelItem)) {
            if(!c.fetch("minecraft:wooden_shovel",1))return;
            for(int i=0;i<c.worker().getInventory().getContainerSize();i++)if(c.worker().getInventory().getItem(i).getItem() instanceof ShovelItem) {
                if(!c.worker().getMainHandItem().isEmpty())Inventories.giveOrDrop(c.worker(),c.worker().getMainHandItem().copy());
                c.worker().setItemSlot(EquipmentSlot.MAINHAND,c.worker().getInventory().removeItem(i,1));c.equipped();break;
            }
        }
        Pos target=t.targets.get(t.progress);
        if(!c.loaded(target)||!c.move(target.offset(0,1,0)))return;
        var p=block(target);var state=c.level().getBlockState(p);
        if(state.is(Blocks.DIRT_PATH)) { t.progress++;c.progress();return; }
        if(!(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT))||!c.level().getBlockState(p.above()).isAir()) {
            c.block(Reason.BUILD_SITE_OBSTRUCTED,"Road can only flatten clear dirt/grass",target,"",0,0);return;
        }
        if(c.level().setBlock(p,Blocks.DIRT_PATH.defaultBlockState(),3)) {
            c.worker().getMainHandItem().hurtAndBreak(1,c.worker(),EquipmentSlot.MAINHAND);
            t.progress++;c.progress();c.xp(1);
        }
    }
}
