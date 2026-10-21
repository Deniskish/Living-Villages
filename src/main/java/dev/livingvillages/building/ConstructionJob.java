package dev.livingvillages.building;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.task.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class ConstructionJob implements Job {
    private final Blueprints blueprints;
    private final SitePlanner sites=new SitePlanner();
    public ConstructionJob(Blueprints blueprints) { this.blueprints=blueprints; }
    @Override public void tick(WorkContext c) {
        Plan p=c.plan();Task t=c.task();var bp=blueprints.get(p.blueprint,p.palette);
        if(t.progress==0&&t.attempts>=3&&t.blocker!=null&&t.blocker.type()==Reason.PATH_UNREACHABLE)
            p.site=null;
        if(p.site==null) {
            p.site=sites.select(c,bp);
            if(p.site==null) { c.block(Reason.NO_VALID_SITE,"No valid site in candidate batch: "+p.siteRejections);return; }
            c.progress();c.chronicle().emit(c.village(),c.now(),"BUILDING_STARTED",c.citizen().id,p.id,bp.id()+" ("+bp.palette()+") at "+p.site);
        }
        for(int i=0;i<t.progress;i++) {
            var cell=bp.cells().get(i);BlockPos pos=absolute(p.site,cell.offset());
            if(!c.loaded(of(pos)))return;
            if(cell.partner()!=null&&!c.loaded(of(absolute(p.site,cell.partnerOffset()))))return;
            if(!matches(c,pos,cell.state())||(cell.partner()!=null&&!matches(c,absolute(p.site,cell.partnerOffset()),cell.partner()))) {
                c.block(Reason.WORLD_CHANGED,"Previously built block changed at "+pos,of(pos),cell.item(),1,0);return;
            }
        }
        if(t.progress>=bp.cells().size()) { complete(c,bp);return; }
        var cell=bp.cells().get(t.progress);BlockPos pos=absolute(p.site,cell.offset());
        if(!c.loaded(of(pos)))return;
        BlockPos support=new BlockPos(pos.getX(),p.site.y()-1,pos.getZ());
        if(!c.level().getBlockState(support).isFaceSturdy(c.level(),support,net.minecraft.core.Direction.UP)) {
            c.block(Reason.WORLD_CHANGED,"Foundation support was removed",of(support),"",1,0);return;
        }
        var current=c.level().getBlockState(pos);
        if(!current.canBeReplaced()||!current.getFluidState().isEmpty()) {
            c.block(Reason.BUILD_SITE_OBSTRUCTED,"Expected empty construction position",of(pos),cell.item(),1,0);return;
        }
        if(Inventories.count(c.worker().getInventory(),cell.item())==0) {
            int remaining=0;
            for(int i=t.progress;i<bp.cells().size();i++)if(bp.cells().get(i).item().equals(cell.item()))remaining++;
            if(!c.fetch(cell.item(),Math.min(16,remaining)))return;
        }
        if(!c.interactAt(of(pos),p.site.y()))return;
        if(!c.level().isUnobstructed(cell.state(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty())) {
            c.block(Reason.BUILD_SITE_OBSTRUCTED,"An entity occupies the placement volume",of(pos),cell.item(),1,0);return;
        }
        if(!cell.state().canSurvive(c.level(),pos)) { c.block(Reason.BUILD_SITE_OBSTRUCTED,"Block lacks valid support",of(pos),cell.item(),1,0);return; }
        if(cell.partner()!=null) {
            BlockPos other=absolute(p.site,cell.partnerOffset());
            if(!c.loaded(of(other)))return;
            if(!c.level().getBlockState(other).canBeReplaced()) { c.block(Reason.BUILD_SITE_OBSTRUCTED,"Two-part block is obstructed",of(other),cell.item(),1,0);return; }
            if(cell.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    &&!c.level().getBlockState(other.below()).isSolid()) {
                c.block(Reason.BUILD_SITE_OBSTRUCTED,"Bed head lacks support");return;
            }
        }
        if(c.now()-t.actionStarted<20)return;
        if(!c.level().setBlock(pos,cell.state(),2)) { c.block(Reason.WORLD_CHANGED,"Placement rejected");return; }
        if(cell.partner()!=null&&!c.level().setBlock(absolute(p.site,cell.partnerOffset()),cell.partner(),2)) {
            c.level().setBlock(pos,current,2);
            c.block(Reason.WORLD_CHANGED,"Two-part placement failed; material retained");return;
        }
        c.level().updateNeighborsAt(pos,cell.state().getBlock());
        if(!Inventories.consume(c.worker().getInventory(),Map.of(cell.item(),1)))
            throw new IllegalStateException("Construction inventory changed within synchronous placement");
        c.worker().swing(InteractionHand.MAIN_HAND);
        t.progress++;p.progress=t.progress;t.actionStarted=c.now();c.progress();c.xp(1);
        if(cell.state().is(Blocks.CHEST))c.village().storage.add(of(pos));
    }
    private boolean matches(WorkContext c,BlockPos pos,net.minecraft.world.level.block.state.BlockState expected) {
        return Blueprints.matches(c.level().getBlockState(pos),expected);
    }
    private static BlockPos absolute(Pos site,Pos offset) { return block(site.offset(offset.x(),offset.y(),offset.z())); }
    private void complete(WorkContext c,Blueprints.Blueprint bp) {
        Building b=new Building();b.id=UUID.randomUUID();b.plan=c.plan().id;b.village=c.village().id;
        b.blueprint=bp.id();b.category=bp.category();b.origin=c.plan().site;b.palette=bp.palette();
        b.entrance=b.origin.offset(bp.entrance().x(),bp.entrance().y(),bp.entrance().z());b.completedAt=c.now();
        c.village().buildings.put(b.id,b);c.xp(16);
        c.chronicle().emit(c.village(),c.now(),"BUILDING_COMPLETED",c.citizen().id,b.id,b.blueprint+" ("+b.palette+")");
        c.finish();
    }
}
