package dev.livingvillages.progression;
import dev.livingvillages.domain.Diagnostics;
import dev.livingvillages.domain.Model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.livingvillages.progression.RequirementType.*;

class ProgressionPolicyTest {
    /** Mutable fixture that is turned into immutable VillageFacts. */
    static final class F {
        int population,beds,storage,food;
        Map<String,Integer> buildings=new TreeMap<>();
        Map<Role,Integer> best=new EnumMap<>(Role.class);
        List<Integer> levels=new ArrayList<>();
        Map<String,Long> production=new TreeMap<>();
        Set<String> technologies=new TreeSet<>();
        boolean furnace,table;
        VillageFacts build() { return new VillageFacts(population,beds,buildings,storage,best,levels,production,food,technologies,furnace,table); }
    }
    static final Map<String,String> SAMPLE=Map.of("WOOD","minecraft:oak_log","FOOD","minecraft:wheat",
            "STONE","minecraft:cobblestone","IRON","minecraft:iron_ingot");
    /** Facts that meet every cumulative requirement of the tier exactly at its threshold. */
    static F exact(Tier tier) {
        F f=new F();
        var specs=ProgressionPolicy.cumulative(tier);
        for(var s:specs)if(s.type()==POPULATION)f.population=(int)s.required();
        for(var s:specs) {
            switch(s.type()) {
                case HOUSING -> f.beds=(int)Math.max(s.required(),f.population);
                case BUILDING -> f.buildings.put(s.subject(),(int)s.required());
                case BUILDING_TYPES -> { for(String c:List.of("HOUSING","STORAGE","SMITHY","ANIMALS"))
                    if(f.buildings.size()<s.required())f.buildings.putIfAbsent(c,1); }
                case STORAGE -> f.storage=(int)s.required();
                case PROFESSION -> f.best.put(Role.valueOf(s.subject()),(int)s.required());
                case SKILLED_WORKERS -> { for(int i=0;i<s.required();i++)f.levels.add(Integer.parseInt(s.subject())); }
                case PRODUCTION -> f.production.put(SAMPLE.get(s.subject()),s.required());
                case FOOD_RESERVE -> f.food=(int)(s.required()*f.population);
                case TECHNOLOGIES -> Technologies.ALL.stream().limit(s.required()).forEach(t->f.technologies.add(t.id()));
                default -> { }
            }
        }
        return f;
    }
    /** Reduces exactly the fact measured by one requirement by one unit. */
    static F reduce(Tier tier,ProgressionPolicy.Spec spec) {
        F f=exact(tier);
        switch(spec.type()) {
            case POPULATION -> { f.population--;f.food=0; }
            case HOUSING -> f.beds--;
            case BUILDING -> f.buildings.merge(spec.subject(),-1,Integer::sum);
            case BUILDING_TYPES -> f.buildings.remove("ANIMALS");
            case STORAGE -> f.storage--;
            case PROFESSION -> f.best.merge(Role.valueOf(spec.subject()),-1,Integer::sum);
            case SKILLED_WORKERS -> {
                // Leave exactly required-1 citizens at or above the level.
                int level=Integer.parseInt(spec.subject());
                long above=f.levels.stream().filter(l->l>=level).count();
                for(int i=0;i<f.levels.size()&&above>=spec.required();i++)
                    if(f.levels.get(i)>=level) { f.levels.set(i,level-1);above--; }
            }
            case PRODUCTION -> f.production.merge(SAMPLE.get(spec.subject()),-1L,Long::sum);
            case FOOD_RESERVE -> f.food--;
            case TECHNOLOGIES -> f.technologies.remove(f.technologies.iterator().next());
            default -> fail("Unexpected tier requirement "+spec);
        }
        f.buildings.values().removeIf(n->n<=0);
        return f;
    }
    @Test void tierOneRequiresALivingHousedPopulation() {
        F f=new F();
        assertNull(ProgressionPolicy.qualifiedTier(f.build()),"No citizens: not viable");
        f.population=2;f.beds=1;
        assertNull(ProgressionPolicy.qualifiedTier(f.build()),"Beds must cover the population");
        f.beds=2;
        assertEquals(Tier.SETTLEMENT,ProgressionPolicy.qualifiedTier(f.build()));
        assertTrue(ProgressionPolicy.requirements(Tier.SETTLEMENT,f.build()).stream().allMatch(Requirement::fulfilled));
    }
    @Test void everyTierBoundaryIsExactAndEachRequirementMatters() {
        for(Tier tier:Tier.values()) {
            assertEquals(tier,ProgressionPolicy.qualifiedTier(exact(tier).build()),"Exact facts qualify for "+tier);
            if(tier==Tier.SETTLEMENT)continue;
            for(var spec:ProgressionPolicy.cumulative(tier)) {
                var facts=reduce(tier,spec).build();
                Tier qualified=ProgressionPolicy.qualifiedTier(facts);
                assertTrue(qualified==null||qualified.compareTo(tier)<0,tier+" must not qualify without "+spec);
                var missing=ProgressionPolicy.requirements(tier,facts).stream().filter(r->!r.fulfilled()).map(Requirement::key).toList();
                assertTrue(missing.contains(spec.type()+(spec.subject().isEmpty()?"":":"+spec.subject())),
                        "Diagnosis names the reduced requirement "+spec+": "+missing);
            }
        }
    }
    @Test void tierTwoBoundaryValues() {
        var f=exact(Tier.VILLAGE);
        assertEquals(6,f.population);assertEquals(6,f.beds);assertEquals(1,f.buildings.get("HOUSING"));
        assertEquals(16L,f.production.get("minecraft:oak_log"));assertEquals(16L,f.production.get("minecraft:wheat"));
        assertEquals(Tier.VILLAGE,ProgressionPolicy.qualifiedTier(f.build()));
        f.production.put("minecraft:oak_log",15L);
        assertEquals(Tier.SETTLEMENT,ProgressionPolicy.qualifiedTier(f.build()));
    }
    @Test void tierThreeFourFiveBoundaryValues() {
        assertEquals(10,exact(Tier.TOWN).population);assertEquals(8L,exact(Tier.TOWN).production.get("minecraft:iron_ingot"));
        assertEquals(16,exact(Tier.CITY).population);assertEquals(1,exact(Tier.CITY).technologies.size());
        assertEquals(24,exact(Tier.CAPITAL).population);assertEquals(Technologies.ALL.size(),exact(Tier.CAPITAL).technologies.size());
        var town=exact(Tier.TOWN);town.best.put(Role.SMITH,1);
        assertEquals(Tier.VILLAGE,ProgressionPolicy.qualifiedTier(town.build()),"Smith II required for Town");
        var city=exact(Tier.CITY);city.food=city.population*3-1;
        assertEquals(Tier.TOWN,ProgressionPolicy.qualifiedTier(city.build()),"Food reserve required for City");
        var capital=exact(Tier.CAPITAL);capital.best.put(Role.BUILDER,3);
        assertEquals(Tier.CITY,ProgressionPolicy.qualifiedTier(capital.build()),"Builder IV required for Capital");
    }
    @Test void cumulativeThresholdsNeverDecreaseSoQualificationIsMonotonic() {
        Tier[] tiers=Tier.values();
        for(int i=1;i<tiers.length;i++) {
            Map<String,Long> lower=new HashMap<>();
            ProgressionPolicy.cumulative(tiers[i-1]).forEach(s->lower.put(s.type()+":"+s.subject(),s.required()));
            Map<String,Long> higher=new HashMap<>();
            ProgressionPolicy.cumulative(tiers[i]).forEach(s->higher.put(s.type()+":"+s.subject(),s.required()));
            Tier tier=tiers[i];
            lower.forEach((key,n)->assertTrue(higher.getOrDefault(key,-1L)>=n,tier+" keeps "+key));
        }
    }
    @Test void missingRequirementsAreStructuredAndExplained() {
        var f=exact(Tier.VILLAGE);f.population=5;f.food=0;f.beds=5;
        var snapshot=ProgressionPolicy.evaluate(f.build(),Tier.SETTLEMENT,Set.of(),100);
        assertEquals(Tier.VILLAGE,snapshot.nextTier());
        var population=snapshot.missingRequirements().stream().filter(r->r.type()==POPULATION).findFirst().orElseThrow();
        assertEquals(6,population.required());assertEquals(5,population.actual());assertFalse(population.fulfilled());
        assertEquals("Population 5 / 6",population.message());
        assertEquals("[✗] Population 5 / 6",population.line());
        assertTrue(snapshot.explanation().contains("Population 5 / 6"),snapshot.explanation());
        var lines=Diagnostics.lines(ProgressionDiagnostics.tier(snapshot));
        assertTrue(lines.getFirst().contains("BLOCKED")&&lines.getFirst().contains("REQUIREMENT_UNMET"),lines.toString());
        assertTrue(lines.stream().anyMatch(l->l.contains("[✗] Usable beds 5 / 6")),lines.toString());
        var housing=snapshot.requirements().stream().filter(r->r.type()==HOUSING).findFirst().orElseThrow();
        assertFalse(housing.fulfilled(),"Tier II needs at least six beds even for five citizens");
        assertEquals(6,housing.required());assertEquals(5,housing.actual());
    }
    @Test void historicalTierNeverOscillatesAndMilestonesAreUnique() {
        Village v=new Village();v.tierHistory.put(Tier.SETTLEMENT.name(),0L);
        assertEquals(List.of(Tier.VILLAGE),ProgressionPolicy.advance(v,ProgressionPolicy.qualifiedTier(exact(Tier.VILLAGE).build()),100));
        assertEquals(Tier.VILLAGE,v.tier);
        // Temporary shortages: storage destroyed, food eaten, a villager died.
        var shortage=exact(Tier.VILLAGE);shortage.storage=0;shortage.population=5;shortage.beds=5;
        for(long tick=200;tick<2000;tick+=200) {
            assertTrue(ProgressionPolicy.advance(v,ProgressionPolicy.qualifiedTier(shortage.build()),tick).isEmpty());
            assertTrue(ProgressionPolicy.advance(v,ProgressionPolicy.qualifiedTier(exact(Tier.VILLAGE).build()),tick+100).isEmpty(),"No duplicate milestone");
            assertEquals(Tier.VILLAGE,v.tier,"Historical tier retained");
        }
        assertEquals(100L,v.tierHistory.get("VILLAGE"));
        assertTrue(ProgressionPolicy.advance(v,null,5000).isEmpty(),"Unviable village keeps its history");
        assertEquals(List.of(Tier.TOWN),ProgressionPolicy.advance(v,Tier.TOWN,6000));
        var snapshot=ProgressionPolicy.evaluate(shortage.build(),v.tier,Set.of(),6100);
        assertEquals(Tier.TOWN,snapshot.highestUnlockedTier());assertEquals(Tier.SETTLEMENT,snapshot.currentTier());
        assertTrue(snapshot.explanation().contains("historical III — Town is retained"),snapshot.explanation());
    }
    @Test void multiTierJumpRecordsEachTierOnceInOrder() {
        Village v=new Village();
        assertEquals(List.of(Tier.VILLAGE,Tier.TOWN),ProgressionPolicy.advance(v,Tier.TOWN,10));
        assertEquals(Set.of("VILLAGE","TOWN"),v.tierHistory.keySet());
        // A restored milestone without the matching tier field is repaired silently.
        Village restored=new Village();restored.tierHistory.put("VILLAGE",5L);
        assertTrue(ProgressionPolicy.advance(restored,Tier.VILLAGE,10).isEmpty());
        assertEquals(Tier.VILLAGE,restored.tier);
    }
}
