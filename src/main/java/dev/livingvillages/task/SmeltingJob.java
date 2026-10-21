package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Palettes;
import dev.livingvillages.progression.VillageCapability;
import dev.livingvillages.resource.Inventories;
import net.minecraft.world.item.*;
import java.util.*;
/** Furnace block entity performs timing, fuel consumption and recipe execution. */
public final class SmeltingJob implements Job {
    /** One furnace cycle: one input item plus one fuel item for one output item. */
    public record Smelt(String input,String fuel,String output,VillageCapability capability) {
        public Map<String,Integer> ingredients() {
            Map<String,Integer> m=new TreeMap<>();m.merge(input,1,Integer::sum);m.merge(fuel,1,Integer::sum);return m;
        }
    }
    /** SMELT_CHARCOAL burns a log with a log; every other smelting plan is raw iron (v1.0). */
    public static Smelt recipe(Plan plan,Task task) {
        if("SMELT_CHARCOAL".equals(plan.kind)) {
            String log=task.item.endsWith("_log")?task.item:Palettes.log(plan.palette);
            return new Smelt(log,log,"minecraft:charcoal",VillageCapability.CHARCOAL_BURNING);
        }
        String fuel=task.desiredResource.equals("minecraft:charcoal")?"minecraft:charcoal":"minecraft:coal";
        return new Smelt("minecraft:raw_iron",fuel,"minecraft:iron_ingot",VillageCapability.IRON_SMELTING);
    }
    @Override public void tick(WorkContext c) {
        Task t=c.task();
        if(t.phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        Smelt recipe=recipe(c.plan(),t);
        var capability=c.capability(recipe.capability());
        if(!capability.unlocked()) { c.blockCapability(capability);return; }
        if(t.target==null)t.target=c.observations().furnaces.stream()
                .filter(p->Inventories.container(c.level(),p) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity).findFirst().orElse(null);
        if(t.target==null) { c.block(Reason.INFRASTRUCTURE_MISSING,recipe.capability()+" unavailable: a loaded furnace is required");return; }
        var furnace=Inventories.container(c.level(),t.target);
        if(!(furnace instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity)) {
            c.observations().furnaces.remove(t.target);t.target=null;c.block(Reason.TARGET_MISSING,"Furnace disappeared");return;
        }
        Item output=Inventories.item(recipe.output()),input=Inventories.item(recipe.input()),fuel=Inventories.item(recipe.fuel());
        if(t.phase.equals("SMELT")) {
            if(!c.move(t.target))return;
            var result=furnace.getItem(2);
            if(result.is(output)) {
                var copy=result.copyWithCount(1);
                if(Inventories.insert(c.worker().getInventory(),copy)==1) {
                    result.shrink(1);furnace.setChanged();c.produced(recipe.output(),1);c.progress();c.xp(4);t.phase="DELIVER";
                } else c.block(Reason.STORAGE_FULL,"Worker cannot carry furnace output");
                return;
            }
            if(c.now()-t.actionStarted>600)c.block(Reason.WORLD_CHANGED,"Furnace did not produce "+recipe.output()+"; check input, fuel and output");
            return;
        }
        for(var e:recipe.ingredients().entrySet())if(!c.fetch(e.getKey(),e.getValue()))return;
        if(!c.move(t.target))return;
        if(!furnace.getItem(0).isEmpty()&&!furnace.getItem(0).is(input)) {
            c.block(Reason.WORLD_CHANGED,"Furnace is processing another recipe");return;
        }
        if(!furnace.getItem(1).isEmpty()&&!furnace.getItem(1).is(fuel)) {
            c.block(Reason.WORLD_CHANGED,"Furnace contains another fuel");return;
        }
        if(!furnace.getItem(2).isEmpty()&&!furnace.getItem(2).is(output)) {
            c.block(Reason.WORLD_CHANGED,"Furnace output holds another item");return;
        }
        if(furnace.getItem(0).getCount()>=64||furnace.getItem(1).getCount()>=64) {
            c.block(Reason.STORAGE_FULL,"Furnace input is full");return;
        }
        if(!Inventories.consume(c.worker().getInventory(),recipe.ingredients()))return;
        if(furnace.getItem(0).isEmpty())furnace.setItem(0,new ItemStack(input));else furnace.getItem(0).grow(1);
        if(furnace.getItem(1).isEmpty())furnace.setItem(1,new ItemStack(fuel));else furnace.getItem(1).grow(1);
        furnace.setChanged();t.phase="SMELT";t.actionStarted=c.now();c.progress();
    }
}
