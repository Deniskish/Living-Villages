package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Rules;
import java.util.*;
import static dev.livingvillages.progression.RequirementType.*;

/**
 * Village tiers I–V derived only from physical/persistent facts. Requirements are
 * cumulative: tier N includes every lower-tier requirement (a higher tier overrides the
 * threshold of the same key), so qualification is monotonic in N. The final balance
 * values are documented in docs/V1_1_SPEC.md.
 */
public final class ProgressionPolicy {
    private ProgressionPolicy() {}
    /** One tier threshold. For HOUSING the threshold is a minimum; beds must also cover population. */
    public record Spec(RequirementType type,String subject,long required) {
        String key() { return type+(subject.isEmpty()?"":":"+subject); }
    }
    private static Spec s(RequirementType type,String subject,long required) { return new Spec(type,subject,required); }
    private static Spec s(RequirementType type,long required) { return new Spec(type,"",required); }
    private static final Map<Tier,List<Spec>> TIERS=new EnumMap<>(Map.of(
            Tier.SETTLEMENT,List.of(s(POPULATION,1),s(HOUSING,1)),
            Tier.VILLAGE,List.of(s(POPULATION,6),s(HOUSING,6),s(BUILDING,"HOUSING",1),s(STORAGE,1),
                    s(PROFESSION,"LUMBERJACK",1),s(PROFESSION,"FARMER",1),
                    s(PRODUCTION,"WOOD",16),s(PRODUCTION,"FOOD",16)),
            Tier.TOWN,List.of(s(POPULATION,10),s(HOUSING,10),s(BUILDING,"HOUSING",3),s(BUILDING,"STORAGE",1),
                    s(BUILDING,"SMITHY",1),s(PROFESSION,"MINER",2),s(PROFESSION,"SMITH",2),
                    s(PRODUCTION,"WOOD",64),s(PRODUCTION,"FOOD",64),s(PRODUCTION,"IRON",8),s(FOOD_RESERVE,2)),
            Tier.CITY,List.of(s(POPULATION,16),s(HOUSING,16),s(BUILDING,"HOUSING",5),s(BUILDING_TYPES,4),
                    s(SKILLED_WORKERS,"3",3),s(PROFESSION,"MINER",3),s(PROFESSION,"SMITH",3),
                    s(PRODUCTION,"IRON",32),s(PRODUCTION,"STONE",256),s(FOOD_RESERVE,3),s(TECHNOLOGIES,1)),
            Tier.CAPITAL,List.of(s(POPULATION,24),s(HOUSING,24),s(BUILDING,"HOUSING",8),
                    s(SKILLED_WORKERS,"4",4),s(PROFESSION,"MINER",4),s(PROFESSION,"SMITH",4),s(PROFESSION,"BUILDER",4),
                    s(PRODUCTION,"IRON",128),s(FOOD_RESERVE,4),s(TECHNOLOGIES,Technologies.ALL.size()))));
    /** Requirements specific to one tier (not cumulative). */
    public static List<Spec> specs(Tier tier) { return TIERS.get(tier); }
    /** Cumulative specs up to and including the tier, in first-introduced order. */
    public static List<Spec> cumulative(Tier tier) {
        Map<String,Spec> merged=new LinkedHashMap<>();
        for(Tier t:Tier.values()) {
            if(t.compareTo(tier)>0)break;
            for(Spec spec:TIERS.get(t))merged.merge(spec.key(),spec,(old,next)->next.required()>=old.required()?next:old);
        }
        return List.copyOf(merged.values());
    }
    public static List<Requirement> requirements(Tier tier,VillageFacts facts) {
        return cumulative(tier).stream().map(spec->evaluate(spec,facts)).toList();
    }
    public static boolean qualifies(Tier tier,VillageFacts facts) {
        return requirements(tier,facts).stream().allMatch(Requirement::fulfilled);
    }
    /** Highest tier currently qualified for, or null when Tier I itself is unmet. */
    public static Tier qualifiedTier(VillageFacts facts) {
        Tier result=null;
        for(Tier tier:Tier.values()) { if(!qualifies(tier,facts))break;result=tier; }
        return result;
    }
    public static Requirement evaluate(Spec spec,VillageFacts f) {
        long required=spec.required();
        return switch(spec.type()) {
            case POPULATION -> r(spec,required,f.population(),"Population "+f.population()+" / "+required);
            case HOUSING -> {
                long needed=Math.max(required,f.population());
                yield r(spec,needed,f.beds(),"Usable beds "+f.beds()+" / "+needed+" (at least "+required+", one per citizen)");
            }
            case BUILDING -> r(spec,required,f.buildings(spec.subject()),"Completed "+buildingLabel(spec.subject())+" "+f.buildings(spec.subject())+" / "+required);
            case BUILDING_TYPES -> r(spec,required,f.buildings().size(),"Completed building types "+f.buildings().size()+" / "+required);
            case STORAGE -> r(spec,required,f.storageContainers(),"Storage containers "+f.storageContainers()+" / "+required);
            case PROFESSION -> {
                Role role=Role.valueOf(spec.subject());int best=f.level(role);String title=TechnologyPolicy.title(role);
                yield r(spec,required,best,title+" "+Rules.numeral((int)required)+(best>0?" (best: "+Rules.numeral(best)+")":" (nobody has worked as "+title+")"));
            }
            case SKILLED_WORKERS -> {
                int level=Integer.parseInt(spec.subject());long n=f.workersAtLeast(level);
                yield r(spec,required,n,"Citizens at level "+Rules.numeral(level)+"+ "+n+" / "+required);
            }
            case PRODUCTION -> {
                ProductionCategory category=ProductionCategory.valueOf(spec.subject());long n=category.total(f.production());
                yield r(spec,required,n,category.label()+" "+n+" / "+required);
            }
            case FOOD_RESERVE -> {
                long needed=required*f.population();
                yield r(spec,needed,f.foodUnits(),"Food reserve "+f.foodUnits()+" / "+needed+" units ("+required+" per citizen)");
            }
            case TECHNOLOGIES -> r(spec,required,f.technologies().size(),"Technologies unlocked "+f.technologies().size()+" / "+required);
            default -> throw new IllegalArgumentException("Not a tier requirement: "+spec.type());
        };
    }
    private static Requirement r(Spec spec,long required,long actual,String message) {
        return new Requirement(spec.type(),spec.subject(),required,actual,actual>=required,message);
    }
    private static String buildingLabel(String category) {
        return switch(category) {
            case "HOUSING" -> "houses"; case "STORAGE" -> "storage buildings"; case "SMITHY" -> "smithies";
            case "ANIMALS" -> "animal pens"; default -> category.toLowerCase(Locale.ROOT)+" buildings";
        };
    }
    public static VillageProgressionSnapshot evaluate(VillageFacts facts,Tier highest,Set<String> researching,long tick) {
        Tier current=qualifiedTier(facts);
        Tier next=highest.next().orElse(null);
        List<Requirement> requirements=next==null?List.of():requirements(next,facts);
        var fulfilled=requirements.stream().filter(Requirement::fulfilled).toList();
        var missing=requirements.stream().filter(r->!r.fulfilled()).toList();
        var capabilities=CapabilityPolicy.all(highest,facts);
        String explanation;
        if(next==null)explanation=highest.label()+" is the highest tier.";
        else if(missing.isEmpty())explanation="All requirements for "+next.label()+" are met.";
        else explanation="Not yet "+next.label()+": "+String.join("; ",missing.stream().map(Requirement::message).toList());
        if(current==null)explanation+=" Tier I requirements are currently unmet; the historical tier is retained.";
        else if(current.compareTo(highest)<0)explanation+=" Currently qualifies for "+current.label()+"; historical "+highest.label()+" is retained.";
        return new VillageProgressionSnapshot(current,highest,next,requirements,fulfilled,missing,
                capabilities.stream().filter(CapabilityStatus::unlocked).toList(),
                capabilities.stream().filter(c->!c.unlocked()).toList(),
                TechnologyPolicy.all(highest,facts,researching),explanation,tick);
    }
    /**
     * Records each newly qualified tier exactly once. Tiers never decrease, so temporary
     * shortages cannot oscillate the village. A tier already present in tierHistory is
     * restored silently (no duplicate milestone).
     * @return tiers reached for the first time, ascending
     */
    public static List<Tier> advance(Village village,Tier qualified,long tick) {
        List<Tier> reached=new ArrayList<>();
        if(qualified==null)return reached;
        for(Tier tier:Tier.values()) {
            if(tier.compareTo(qualified)>0)break;
            if(tier.compareTo(village.tier)<=0)continue;
            village.tier=tier;
            if(village.tierHistory.putIfAbsent(tier.name(),tick)==null)reached.add(tier);
        }
        return reached;
    }
}
