package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
/** Food provisioning only; vanilla villagers choose mates and create babies. */
public final class PopulationJob implements Job {
    public void tick(WorkContext c) {
        if(c.village().beds<=c.village().population()) { c.finish();return; }
        List<Villager> adults=new ArrayList<>();
        for(Citizen citizen:c.village().citizens.values())
            if(!citizen.dead&&c.level().getEntity(citizen.id) instanceof Villager villager&&!villager.isBaby())
                adults.add(villager);
        for(Villager adult:adults) {
            int food=Inventories.count(adult.getInventory(),"minecraft:bread")*4
                    +Inventories.count(adult.getInventory(),"minecraft:carrot")+Inventories.count(adult.getInventory(),"minecraft:potato");
            if(food>=12)continue;
            if(!c.fetch("minecraft:bread",3))return;
            if(adult==c.worker())continue;
            if(!c.move(of(adult.blockPosition())))return;
            if(Inventories.transfer(c.worker().getInventory(),adult.getInventory(),"minecraft:bread",3)>0)c.progress();
            else { c.block(Reason.STORAGE_FULL,"Citizen cannot accept breeding food");return; }
        }
        c.finish();
    }
}
