package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Rules;
import java.util.*;

/** Answers "why can't this technology start?" with the same Requirement records as tiers. */
public final class TechnologyPolicy {
    private TechnologyPolicy() {}
    public static TechnologyStatus status(Technology t,Tier highest,VillageFacts facts,boolean researching) {
        List<Requirement> requirements=new ArrayList<>();
        requirements.add(CapabilityPolicy.tier(t.tier(),highest));
        for(String prerequisite:t.prerequisites())requirements.add(CapabilityPolicy.technology(prerequisite,facts));
        int best=facts.level(t.role());
        String role=title(t.role());
        requirements.add(new Requirement(RequirementType.PROFESSION,t.role().name(),t.roleLevel(),best,best>=t.roleLevel(),
                role+" "+Rules.numeral(t.roleLevel())+(best>0?" (best "+role+": "+Rules.numeral(best)+")":" (nobody has worked as "+role+")")));
        Requirement station=CapabilityPolicy.infrastructure(t.workstation(),facts);
        if(station!=null)requirements.add(station);
        if(facts.technologies().contains(t.id()))return new TechnologyStatus(t,TechnologyStatus.Stage.UNLOCKED,requirements,"Unlocked: "+t.effect());
        if(researching)return new TechnologyStatus(t,TechnologyStatus.Stage.RESEARCHING,requirements,"Research plan active");
        // A workstation is reported but is not a start gate: the planner places a missing
        // furnace/table as a dependency of the research plan. Materials are acquired the same way.
        var missing=requirements.stream().filter(r->!r.fulfilled()&&r.type()!=RequirementType.WORKSTATION).map(Requirement::message).toList();
        if(!missing.isEmpty())return new TechnologyStatus(t,TechnologyStatus.Stage.LOCKED,requirements,String.join("; ",missing));
        return new TechnologyStatus(t,TechnologyStatus.Stage.AVAILABLE,requirements,station!=null&&!station.fulfilled()
                ?station.message()+"; the research plan places one first":"All start requirements met; materials are acquired by the research plan");
    }
    public static boolean startable(TechnologyStatus status) { return status.stage()==TechnologyStatus.Stage.AVAILABLE; }
    public static List<TechnologyStatus> all(Tier highest,VillageFacts facts,Set<String> researching) {
        return Technologies.ALL.stream().map(t->status(t,highest,facts,researching.contains(t.id()))).toList();
    }
    /** Deterministic next research: first startable technology in registry order. */
    public static Optional<Technology> next(Tier highest,VillageFacts facts,Set<String> researching) {
        if(!researching.isEmpty())return Optional.empty();
        return all(highest,facts,researching).stream().filter(TechnologyPolicy::startable).map(TechnologyStatus::technology).findFirst();
    }
    /** Physical cost resolved through the plan palette, compared with current stock. */
    public static List<Requirement> costs(Technology t,String palette,Map<String,Integer> stock) {
        List<Requirement> result=new ArrayList<>();
        dev.livingvillages.domain.Palettes.resolve(t.costs(),palette).forEach((item,n)->{
            int have=stock.getOrDefault(item,0);
            result.add(new Requirement(RequirementType.RESOURCE,item,n,have,have>=n,item+" "+Math.min(have,n)+" / "+n));
        });
        return result;
    }
    static String title(Role role) {
        String s=role.name().replace('_',' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }
}
