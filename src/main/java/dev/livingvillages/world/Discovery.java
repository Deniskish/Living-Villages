package dev.livingvillages.world;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class Discovery {
    private final Chronicle chronicle;
    public Discovery(Chronicle chronicle) { this.chronicle=chronicle; }
    public void discover(ServerLevel level,World world) {
        for(var player:level.players()) {
            var bells=level.getPoiManager().getInRange(t->t.is(PoiTypes.MEETING),player.blockPosition(),80,PoiManager.Occupancy.ANY)
                    .map(p->p.getPos()).filter(level::hasChunkAt).limit(16).toList();
            for(var bell:bells) {
                Pos center=of(bell);
                var entities=level.getEntitiesOfClass(Villager.class,new AABB(bell).inflate(VillageConfig.RADIUS.get()));
                if(entities.isEmpty())continue;
                Village v=world.villages.values().stream().filter(candidate->candidate.contains(center)).findFirst().orElse(null);
                if(v==null) {
                    v=new Village();v.center=center;v.radius=VillageConfig.RADIUS.get();
                    v.name="Village "+Math.abs(center.x())+" / "+Math.abs(center.z());v.createdAt=level.getGameTime();
                    world.villages.put(v.id,v);chronicle.emit(v,level.getGameTime(),"VILLAGE_REGISTERED",null,v.id,v.name);
                }
            }
        }
        for(Village v:world.villages.values()) if(level.hasChunkAt(block(v.center)))
            register(level,world,v,level.getEntitiesOfClass(Villager.class,new AABB(block(v.center)).inflate(v.radius)));
    }
    private void register(ServerLevel level,World world,Village v,List<Villager> entities) {
        for(Villager entity:entities) {
            if(Ownership.at(world.villages.values(),of(entity.blockPosition())).filter(owner->owner.id.equals(v.id)).isEmpty())continue;
            if(world.villages.values().stream().anyMatch(other->other.citizens.containsKey(entity.getUUID())))continue;
            Citizen c=new Citizen();c.id=entity.getUUID();c.village=v.id;c.registeredAt=level.getGameTime();
            String[] names={"Anna","Erik","Mira","Hans","Lina","Otto","Ada","Finn","Nora","Leo","Elsa","Emil"};
            c.name=entity.hasCustomName()?entity.getName().getString():
                    names[Math.floorMod(c.id.hashCode(),names.length)]+" "+c.id.toString().substring(0,4);
            c.role=Role.values()[v.citizens.size()%Role.values().length];
            entity.setCustomName(Component.literal(c.name));v.citizens.put(c.id,c);
            chronicle.emit(v,level.getGameTime(),entity.isBaby()?"CITIZEN_BORN":"CITIZEN_REGISTERED",c.id,null,c.name);
            chronicle.emit(v,level.getGameTime(),"ROLE_ASSIGNED",c.id,null,c.role.toString());
        }
    }
}
