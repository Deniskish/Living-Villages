package dev.livingvillages.world;

import dev.livingvillages.LivingVillagesMod;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Rules;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.progression.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/**
 * Evaluates village progression from validated world facts on the strategic tick and
 * applies level-ups exactly once (Chronicle event + persisted milestone + capabilities).
 */
public final class ProgressionService {
    /** Production milestones recorded in history, per category. */
    static final long[] MILESTONES={1,64,512,4096};
    private final Chronicle chronicle;
    public ProgressionService(Chronicle chronicle) { this.chronicle=chronicle; }
    /** Called by the planner after stock and observations were refreshed; low frequency. */
    public VillageProgressionSnapshot evaluate(ServerLevel level,Village v,Observations observed,Map<String,Integer> stock) {
        long now=level.getGameTime();
        v.tierHistory.putIfAbsent(Tier.SETTLEMENT.name(),v.createdAt);
        VillageFacts facts=facts(v,observed,stock);
        Tier before=v.tier;
        for(Tier reached:ProgressionPolicy.advance(v,ProgressionPolicy.qualifiedTier(facts),now))unlocked(v,reached,before,facts,now);
        var snapshot=ProgressionPolicy.evaluate(facts,v.tier,researching(v),now);
        v.progression=snapshot;
        if(VillageConfig.DEBUG.get())LivingVillagesMod.LOG.info("event=progression_evaluated village={} tier={} qualified={} next={} missing={}",
                v.id,v.tier,snapshot.currentTier(),snapshot.nextTier(),snapshot.missingRequirements().stream().map(Requirement::key).toList());
        return snapshot;
    }
    private void unlocked(Village v,Tier reached,Tier before,VillageFacts facts,long now) {
        // Capabilities first unlocked by this tier (technology-gated ones may still wait for research).
        var unlocks=Arrays.stream(VillageCapability.values()).filter(c->c.tier()==reached)
                .filter(c->CapabilityPolicy.status(c,reached,facts).unlocked()).map(Enum::name).toList();
        if(unlocks.contains(VillageCapability.EXPANDED_TERRITORY.name())) {
            int old=v.radius;
            v.radius=Math.min(v.radius+VillageCapability.TERRITORY_GROWTH,VillageConfig.RADIUS_MAX);
            LivingVillagesMod.LOG.info("event=territory_expanded village={} radius_before={} radius_after={}",v.id,old,v.radius);
        }
        chronicle.emit(v,now,"VILLAGE_TIER_REACHED",null,v.id,v.name+" became a "+reached.title()+" (Tier "+reached.numeral()+")"
                +(unlocks.isEmpty()?"":"; unlocked "+String.join(", ",unlocks)),
                Map.of("tier",reached.name(),"previous",before.name(),"capabilities",String.join(",",unlocks)));
    }
    public static VillageFacts facts(Village v,Observations observed,Map<String,Integer> stock) {
        return VillageFacts.of(v,v.beds,Capabilities.distinct(v),Rules.foodUnits(stock),
                observed!=null&&!observed.furnaces.isEmpty(),observed!=null&&!observed.tables.isEmpty());
    }
    public static Set<String> researching(Village v) {
        Set<String> result=new TreeSet<>();
        for(Plan p:v.activePlans())if(p.technology!=null)result.add(p.technology);
        return result;
    }
    /**
     * Read-only snapshot for Ledger/commands: the last strategic evaluation, or a fresh
     * evaluation without level-up when none exists yet (workstation index not ready).
     */
    public static VillageProgressionSnapshot inspect(ServerLevel level,Village v,Map<String,Integer> stock) {
        if(v.progression!=null)return v.progression;
        return ProgressionPolicy.evaluate(facts(v,null,stock),v.tier,researching(v),level.getGameTime());
    }
    /** Physical production record; only called where a world action created the goods. */
    public static void recordProduction(Chronicle chronicle,Village v,String item,long count,long tick) {
        if(count<=0||item==null||item.isBlank())return;
        Map<ProductionCategory,Long> before=new EnumMap<>(ProductionCategory.class);
        for(ProductionCategory category:ProductionCategory.values())if(category.matches(item))before.put(category,category.total(v.production));
        v.production.merge(item,count,Long::sum);
        before.forEach((category,old)->{
            long now=category.total(v.production);
            for(long milestone:MILESTONES)if(old<milestone&&now>=milestone)
                chronicle.emit(v,tick,"PRODUCTION_MILESTONE",null,v.id,category.label()+": "+milestone+(milestone==1?" (first)":""),
                        Map.of("category",category.name(),"milestone",Long.toString(milestone),"item",item));
        });
    }
}
