package dev.livingvillages.building;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import net.minecraft.server.level.ServerLevel;
import static dev.livingvillages.world.Positions.*;
public final class BuildingInspection {
    private final Blueprints blueprints;
    private final Chronicle chronicle;
    public BuildingInspection(Blueprints blueprints,Chronicle chronicle) { this.blueprints=blueprints;this.chronicle=chronicle; }
    public void inspect(ServerLevel level,Village village) {
        if(village.buildings.isEmpty())return;
        int index=(int)((level.getGameTime()/200)%village.buildings.size());
        Building building=village.buildings.values().stream().skip(index).findFirst().orElseThrow();
        String condition="COMPLETE";
        for(var cell:blueprints.get(building.blueprint).cells()) {
            var pos=block(building.origin.offset(cell.offset().x(),cell.offset().y(),cell.offset().z()));
            if(!level.hasChunkAt(pos)) { condition="UNLOADED";break; }
            if(!Blueprints.matches(level.getBlockState(pos),cell.state())) { condition="DAMAGED";break; }
            if(cell.partnerOffset()!=null) {
                var other=block(building.origin.offset(cell.partnerOffset().x(),cell.partnerOffset().y(),cell.partnerOffset().z()));
                if(!level.hasChunkAt(other)) { condition="UNLOADED";break; }
                if(!Blueprints.matches(level.getBlockState(other),cell.partner())) { condition="DAMAGED";break; }
            }
        }
        if(!condition.equals(building.condition)&&condition.equals("DAMAGED"))
            chronicle.emit(village,level.getGameTime(),"BUILDING_DAMAGED",null,building.id,building.blueprint+" no longer matches its completed blueprint");
        building.condition=condition;
    }
}
