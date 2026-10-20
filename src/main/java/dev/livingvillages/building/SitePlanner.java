package dev.livingvillages.building;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.task.WorkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.livingvillages.task.WorkerNavigation;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class SitePlanner {
    private static final List<Pos> OFFSETS=candidates();
    private static List<Pos> candidates() {
        List<Pos> result=new ArrayList<>();
        for(int x=-8;x<8;x++)for(int z=-8;z<8;z++)result.add(new Pos(x*5,0,z*5));
        result.sort(Comparator.comparingLong(p->p.distanceSquared(new Pos(0,0,0))));
        return List.copyOf(result);
    }
    public Pos select(WorkContext c,Blueprints.Blueprint blueprint) {
        Pos best=null;double bestScore=-1;
        for(int n=0;n<8;n++) {
            int index=c.task().scanCursor++%256;
            int dx=OFFSETS.get(index).x(),dz=OFFSETS.get(index).z();
            BlockPos column=block(c.village().center).offset(dx,0,dz);
            if(!c.level().hasChunkAt(column)) { reject(c,"CHUNK_UNAVAILABLE");continue; }
            int y=c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
            Pos site=new Pos(column.getX(),y,column.getZ());
            String rejection=validate(c,blueprint,site);
            if(!rejection.isEmpty()) { reject(c,rejection);continue; }
            Pos entrance=site.offset(2,1,-1);
            var path=WorkerNavigation.approach(c.level(),c.worker(),entrance.offset(0,-1,0),entrance.offset(0,-1,0));
            if(path==null) { reject(c,"PATH");continue; }
            long pathDistance=c.observations().paths.stream().mapToLong(p->p.distanceSquared(entrance)).min().orElse(400);
            double score=Rules.siteScore(site.distanceSquared(c.village().center),pathDistance);
            if(score>bestScore) { best=site;bestScore=score; }
        }
        return best;
    }
    private static void reject(WorkContext c,String reason) { c.plan().siteRejections.merge(reason,1,Integer::sum); }
    public String validate(WorkContext c,Blueprints.Blueprint bp,Pos site) {
        if(!c.village().contains(site)||!c.village().contains(site.offset(bp.width(),3,bp.depth())))return "BOUNDARY";
        for(var building:c.village().buildings.values())
            if(Math.abs(building.origin.x()-site.x())<bp.width()+3&&Math.abs(building.origin.z()-site.z())<bp.depth()+3)return "COLLISION";
        for(Plan other:c.village().activePlans()) {
            if(other==c.plan()||other.site==null||other.state()==State.CANCELLED||other.state()==State.FAILED)continue;
            if(Math.abs(other.site.x()-site.x())<bp.width()+3&&Math.abs(other.site.z()-site.z())<bp.depth()+3)return "COLLISION";
        }
        for(int x=0;x<bp.width();x++)for(int z=0;z<bp.depth();z++) {
            BlockPos p=block(site.offset(x,0,z));
            if(!dev.livingvillages.world.Ownership.owns(c.level(),c.village(),of(p)))return "BOUNDARY";
            if(!c.level().hasChunkAt(p))return "CHUNK_UNAVAILABLE";
            var support=c.level().getBlockState(p.below());
            if(!support.getFluidState().isEmpty())return "WATER";
            if(!support.isFaceSturdy(c.level(),p.below(),Direction.UP))return "SUPPORT";
            if(!support.is(net.minecraft.tags.BlockTags.DIRT)&&!support.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                    &&!support.is(net.minecraft.tags.BlockTags.SAND))return "PROTECTED_SUPPORT";
            int height=c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            if(height!=site.y())return "SLOPE";
            for(int y=0;y<5;y++) {
                var state=c.level().getBlockState(p.above(y));
                if(!state.getFluidState().isEmpty())return "WATER";
                if(!state.canBeReplaced()||c.level().getBlockEntity(p.above(y))!=null)return "COLLISION";
            }
        }
        BlockPos entrance=block(site.offset(2,0,-1));
        if(!c.level().hasChunkAt(entrance))return "CHUNK_UNAVAILABLE";
        if(!c.level().getBlockState(entrance).canBeReplaced()||!c.level().getBlockState(entrance.above()).canBeReplaced())return "ENTRANCE";
        return "";
    }
}
