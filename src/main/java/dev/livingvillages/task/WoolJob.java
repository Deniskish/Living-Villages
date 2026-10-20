package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import static dev.livingvillages.world.Positions.*;
public final class WoolJob implements Job {
    public void tick(WorkContext c) {
        if(c.task().phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        if(c.task().phase.equals("COLLECT")) {
            if(!c.move(c.task().target))return;
            for(ItemEntity item:c.level().getEntitiesOfClass(ItemEntity.class,new AABB(block(c.task().target)).inflate(2))) {
                if(c.worker().distanceToSqr(item)>6.25||!item.getItem().is(Items.WHITE_WOOL))continue;
                Inventories.insert(c.worker().getInventory(),item.getItem());
                if(item.getItem().isEmpty())item.discard();
            }
            c.task().phase="DELIVER";c.progress();return;
        }
        if(!(c.worker().getMainHandItem().getItem() instanceof ShearsItem)) {
            if(!c.fetch("minecraft:shears",1))return;
            for(int i=0;i<c.worker().getInventory().getContainerSize();i++)if(c.worker().getInventory().getItem(i).is(Items.SHEARS)) {
                if(!c.worker().getMainHandItem().isEmpty())Inventories.giveOrDrop(c.worker(),c.worker().getMainHandItem().copy());
                c.worker().setItemSlot(EquipmentSlot.MAINHAND,c.worker().getInventory().removeItem(i,1));c.equipped();break;
            }
        }
        Sheep sheep=c.level().getEntitiesOfClass(Sheep.class,new AABB(block(c.village().center)).inflate(c.village().radius),
                s->s.readyForShearing()&&s.getColor()==DyeColor.WHITE
                        &&dev.livingvillages.world.Ownership.owns(c.level(),c.village(),of(s.blockPosition()))).stream().findFirst().orElse(null);
        if(sheep==null) { c.block(Reason.TARGET_MISSING,"No shearable white sheep in village boundary");return; }
        if(!c.move(of(sheep.blockPosition())))return;
        c.task().target=of(sheep.blockPosition());sheep.shear(SoundSource.NEUTRAL);
        c.worker().getMainHandItem().hurtAndBreak(1,c.worker(),EquipmentSlot.MAINHAND);
        c.task().phase="COLLECT";c.progress();c.xp(3);
    }
}
