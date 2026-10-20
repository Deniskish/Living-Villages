package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

/** Recipes are matched by the server recipe manager; no synthetic/free production. */
public final class ProductionJob implements Job {
    public record Recipe(String output,int count,int width,int height,List<String> slots) {
        public Map<String,Integer> ingredients() {
            Map<String,Integer> map=new TreeMap<>();
            slots.stream().filter(s->!s.isEmpty()).forEach(s->map.merge(s,1,Integer::sum));return map;
        }
    }
    private static Recipe r(String output,int count,String...slots) {
        return new Recipe("minecraft:"+output,count,3,3,Arrays.stream(slots).map(s->s.isEmpty()?s:"minecraft:"+s).toList());
    }
    public static Recipe recipe(String output) {
        String item=output.replace("minecraft:","");
        if(item.endsWith("_planks"))return new Recipe(output,4,1,1,List.of("minecraft:"+item.replace("_planks","_log")));
        return switch(item) {
            case "stick" -> new Recipe(output,4,1,2,List.of("minecraft:oak_planks","minecraft:oak_planks"));
            case "crafting_table" -> new Recipe(output,1,2,2,Collections.nCopies(4,"minecraft:oak_planks"));
            case "chest" -> r(item,1,"oak_planks","oak_planks","oak_planks","oak_planks","","oak_planks","oak_planks","oak_planks","oak_planks");
            case "white_bed" -> r(item,1,"white_wool","white_wool","white_wool","oak_planks","oak_planks","oak_planks","","","");
            case "oak_door" -> r(item,3,"oak_planks","oak_planks","","oak_planks","oak_planks","","oak_planks","oak_planks","");
            case "oak_fence" -> r(item,3,"","","","oak_planks","stick","oak_planks","oak_planks","stick","oak_planks");
            case "oak_fence_gate" -> r(item,1,"","","","stick","oak_planks","stick","stick","oak_planks","stick");
            case "furnace" -> r(item,1,"cobblestone","cobblestone","cobblestone","cobblestone","","cobblestone","cobblestone","cobblestone","cobblestone");
            case "bread" -> new Recipe(output,1,3,1,Collections.nCopies(3,"minecraft:wheat"));
            case "shears" -> new Recipe(output,1,2,2,List.of("","minecraft:iron_ingot","minecraft:iron_ingot",""));
            case "wooden_pickaxe","stone_pickaxe","iron_pickaxe","wooden_axe","stone_axe","iron_axe","wooden_shovel","stone_shovel","iron_shovel" -> {
                String material=item.startsWith("wooden")?"oak_planks":item.startsWith("stone")?"cobblestone":"iron_ingot";
                if(item.endsWith("pickaxe"))yield r(item,1,material,material,material,"","stick","","","stick","");
                if(item.endsWith("_axe"))yield r(item,1,material,material,"",material,"stick","","","stick","");
                yield r(item,1,"",material,"","","stick","","","stick","");
            }
            default -> null;
        };
    }
    @Override public void tick(WorkContext c) {
        if(c.task().phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        Recipe recipe=recipe(c.task().item);
        if(recipe==null) { c.block(Reason.MISSING_RESOURCE,"No supported production recipe for "+c.task().item);return; }
        if(recipe.output().contains("iron_")&&c.citizen().level()<3) {
            c.block(Reason.NO_QUALIFIED_WORKER,"Iron tools require Smith III");return;
        }
        for(var e:recipe.ingredients().entrySet())if(!c.fetch(e.getKey(),e.getValue()))return;
        if(recipe.width()>2||recipe.height()>2) {
            Pos table=c.observations().tables.stream().filter(p->c.level().hasChunkAt(block(p))&&c.level().getBlockState(block(p)).is(Blocks.CRAFTING_TABLE))
                    .min(Comparator.comparingLong(p->p.distanceSquared(of(c.worker().blockPosition())))).orElse(null);
            if(table==null) { c.block(Reason.TARGET_MISSING,"Crafting table required for "+recipe.output());return; }
            if(!c.move(table))return;
        }
        if(c.task().actionStarted==0) { c.task().actionStarted=c.now();c.progress();return; }
        if(c.now()-c.task().actionStarted<60)return;
        List<ItemStack> stacks=recipe.slots().stream().map(s->s.isEmpty()?ItemStack.EMPTY:new ItemStack(Inventories.item(s))).toList();
        CraftingInput input=CraftingInput.of(recipe.width(),recipe.height(),stacks);
        var match=c.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,c.level());
        if(match.isEmpty()) { c.block(Reason.WORLD_CHANGED,"Server recipes reject "+recipe.output());return; }
        ItemStack result=match.get().value().assemble(input,c.level().registryAccess());
        if(!BuiltInRegistries.ITEM.getKey(result.getItem()).toString().equals(recipe.output())) {
            c.block(Reason.WORLD_CHANGED,"Recipe output changed by datapack");return;
        }
        if(Inventories.consume(c.worker().getInventory(),recipe.ingredients())) {
            Inventories.giveOrDrop(c.worker(),result);
            for(ItemStack remainder:match.get().value().getRemainingItems(input))
                if(!remainder.isEmpty())Inventories.giveOrDrop(c.worker(),remainder);
            c.xp(4);c.progress();c.task().phase="DELIVER";
            c.chronicle().emit(c.village(),c.now(),"RESOURCE_MILESTONE",c.citizen().id,c.plan().id,"Crafted "+recipe.output());
        } else c.block(Reason.MISSING_RESOURCE,"Ingredients changed before production");
    }
}
