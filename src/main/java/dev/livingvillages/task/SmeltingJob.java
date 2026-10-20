package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
/** Furnace block entity performs timing, fuel consumption and recipe execution. */
public final class SmeltingJob implements Job {
    @Override public void tick(WorkContext c) {
        Task t=c.task();
        if(t.phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        if(t.target==null)t.target=c.observations().furnaces.stream()
                .filter(p->Inventories.container(c.level(),p) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity).findFirst().orElse(null);
        if(t.target==null) { c.block(Reason.TARGET_MISSING,"A loaded furnace is required");return; }
        var furnace=Inventories.container(c.level(),t.target);
        if(!(furnace instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity)) {
            c.observations().furnaces.remove(t.target);t.target=null;c.block(Reason.TARGET_MISSING,"Furnace disappeared");return;
        }
        if(t.phase.equals("SMELT")) {
            if(!c.move(t.target))return;
            var output=furnace.getItem(2);
            if(output.is(Items.IRON_INGOT)) {
                var copy=output.copyWithCount(1);
                if(Inventories.insert(c.worker().getInventory(),copy)==1) {
                    output.shrink(1);furnace.setChanged();c.progress();c.xp(4);t.phase="DELIVER";
                } else c.block(Reason.STORAGE_FULL,"Worker cannot carry furnace output");
                return;
            }
            if(c.now()-t.actionStarted>600)c.block(Reason.WORLD_CHANGED,"Furnace did not produce iron; check input, fuel and output");
            return;
        }
        if(!c.fetch("minecraft:raw_iron",1)||!c.fetch("minecraft:coal",1)||!c.move(t.target))return;
        if(!furnace.getItem(0).isEmpty()&&!furnace.getItem(0).is(Items.RAW_IRON)) {
            c.block(Reason.WORLD_CHANGED,"Furnace is processing another recipe");return;
        }
        if(!furnace.getItem(1).isEmpty()&&!furnace.getItem(1).is(Items.COAL)) {
            c.block(Reason.WORLD_CHANGED,"Furnace contains another fuel");return;
        }
        if(furnace.getItem(0).getCount()>=64||furnace.getItem(1).getCount()>=64) {
            c.block(Reason.STORAGE_FULL,"Furnace input is full");return;
        }
        if(!Inventories.consume(c.worker().getInventory(),Map.of("minecraft:raw_iron",1,"minecraft:coal",1)))return;
        if(furnace.getItem(0).isEmpty())furnace.setItem(0,new ItemStack(Items.RAW_IRON));else furnace.getItem(0).grow(1);
        if(furnace.getItem(1).isEmpty())furnace.setItem(1,new ItemStack(Items.COAL));else furnace.getItem(1).grow(1);
        furnace.setChanged();t.phase="SMELT";t.actionStarted=c.now();c.progress();
    }
}
