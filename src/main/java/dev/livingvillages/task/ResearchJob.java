package dev.livingvillages.task;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Palettes;
import dev.livingvillages.progression.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.world.Capabilities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
/**
 * Physical research: the qualified worker carries the complete material cost to a real
 * workstation and performs timed work units there. The cost is consumed from the worker's
 * inventory in the same server tick that records the unlock, so an interruption (death,
 * restart, missing chest, unloaded chunk) can neither lose the materials twice nor unlock
 * without them. Progress units persist; carried materials persist as vanilla entity data.
 */
public final class ResearchJob implements Job {
    @Override public void tick(WorkContext c) {
        Plan p=c.plan();Task t=c.task();
        if(t.phase.equals("DELIVER")) { if(c.deliver())c.finish();return; }
        Technology tech=Technologies.get(p.technology).orElse(null);
        if(tech==null) { c.block(Reason.UNKNOWN,"Unknown technology "+p.technology+"; inspect plan "+p.id);return; }
        if(c.village().technologies.containsKey(tech.id())) {
            // Already unlocked (e.g. duplicate plan): never consume twice; return carried materials.
            t.phase="DELIVER";return;
        }
        var research=c.capability(VillageCapability.TECHNOLOGY_RESEARCH);
        if(!research.unlocked()) { c.blockCapability(research);return; }
        var status=TechnologyPolicy.status(tech,c.village().tier,Capabilities.facts(c.village(),c.observations()),false);
        if(status.stage()==TechnologyStatus.Stage.LOCKED) {
            c.block(Reason.REQUIREMENT_UNMET,tech.name()+" cannot start: "+status.explanation());return;
        }
        if(dev.livingvillages.domain.Rules.level(c.citizen().experience.getOrDefault(tech.role(),0L))<tech.roleLevel()) {
            c.block(Reason.NO_QUALIFIED_WORKER,tech.name()+" requires "+tech.role()+" "+dev.livingvillages.domain.Rules.numeral(tech.roleLevel()));return;
        }
        Pos station=station(c,tech);
        if(station==null) {
            c.block(Reason.INFRASTRUCTURE_MISSING,tech.name()+" requires a loaded "+tech.workstation().label().toLowerCase(Locale.ROOT)+" in the village");return;
        }
        Map<String,Integer> cost=Palettes.resolve(tech.costs(),p.palette);
        for(var e:cost.entrySet())if(!c.fetch(e.getKey(),e.getValue()))return;
        if(!c.move(station))return;
        p.totalWork=tech.workUnits();
        if(t.actionStarted==0) { t.actionStarted=c.now();c.progress();return; }
        if(c.now()-t.actionStarted<Technology.UNIT_TICKS)return;
        c.worker().swing(InteractionHand.MAIN_HAND);
        if(t.progress+1<tech.workUnits()) {
            t.progress++;p.progress=t.progress;t.actionStarted=c.now();c.progress();c.xp(1);return;
        }
        if(!Inventories.consume(c.worker().getInventory(),cost)) {
            c.block(Reason.MISSING_RESOURCE,"Research materials changed before completion: "+cost,station,cost.keySet().iterator().next(),1,0);return;
        }
        t.progress=tech.workUnits();p.progress=t.progress;
        c.village().technologies.put(tech.id(),new TechnologyRecord(tech.id(),c.now(),p.id,c.citizen().id));
        c.xp(8);
        c.chronicle().emit(c.village(),c.now(),"TECHNOLOGY_UNLOCKED",c.citizen().id,p.id,
                c.citizen().name+" completed "+tech.name()+" ("+tech.branch().title()+"): "+tech.effect(),
                Map.of("technology",tech.id(),"branch",tech.branch().name(),"consumed",cost.toString()));
        c.finish();
    }
    /** Nearest loaded block of the technology's workstation type, validated against the world. */
    private static Pos station(WorkContext c,Technology tech) {
        Set<Pos> index=switch(tech.workstation()) {
            case FURNACE -> c.observations().furnaces;
            case CRAFTING_TABLE -> c.observations().tables;
            default -> Set.of();
        };
        var block=tech.workstation()==VillageCapability.Infrastructure.FURNACE?Blocks.FURNACE:Blocks.CRAFTING_TABLE;
        return index.stream().filter(p->c.level().hasChunkAt(block(p))&&c.level().getBlockState(block(p)).is(block))
                .min(Comparator.comparingLong(p->p.distanceSquared(of(c.worker().blockPosition())))).orElse(null);
    }
}
