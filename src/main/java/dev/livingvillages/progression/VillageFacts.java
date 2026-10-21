package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import java.util.*;

/**
 * Immutable snapshot of a village's physical/persistent facts used by progression,
 * capability and technology policies. Built on the server from validated world state.
 *
 * @param buildings intact completed buildings by category (DAMAGED excluded)
 * @param bestLevel best level per role among living citizens who have practised it
 * @param citizenLevels best practised level of each living citizen
 * @param foodUnits edible stock in planner food units
 */
public record VillageFacts(int population,int beds,Map<String,Integer> buildings,int storageContainers,
                           Map<Role,Integer> bestLevel,List<Integer> citizenLevels,Map<String,Long> production,
                           int foodUnits,Set<String> technologies,boolean furnace,boolean craftingTable) {
    public VillageFacts {
        buildings=Map.copyOf(buildings);bestLevel=Map.copyOf(bestLevel);citizenLevels=List.copyOf(citizenLevels);
        production=Map.copyOf(production);technologies=Set.copyOf(technologies);
    }
    /** Domain-derived facts plus the physical observations supplied by the caller. */
    public static VillageFacts of(Village v,int beds,int storageContainers,int foodUnits,boolean furnace,boolean craftingTable) {
        Map<String,Integer> buildings=new TreeMap<>();
        for(Building b:v.buildings.values())
            if(!"DAMAGED".equals(b.condition)&&b.category!=null)buildings.merge(b.category,1,Integer::sum);
        Map<Role,Integer> best=new EnumMap<>(Role.class);List<Integer> levels=new ArrayList<>();
        for(Citizen c:v.citizens.values()) {
            if(c.dead)continue;
            int citizenBest=0;
            for(var e:c.experience.entrySet()) {
                if(e.getKey()==null||e.getValue()==null||e.getValue()<=0)continue;
                int level=dev.livingvillages.domain.Rules.level(e.getValue());
                best.merge(e.getKey(),level,Math::max);citizenBest=Math.max(citizenBest,level);
            }
            if(citizenBest>0)levels.add(citizenBest);
        }
        return new VillageFacts((int)v.population(),beds,buildings,storageContainers,best,levels,v.production,
                foodUnits,v.technologies.keySet(),furnace,craftingTable);
    }
    public int buildings(String category) { return buildings.getOrDefault(category,0); }
    public int level(Role role) { return bestLevel.getOrDefault(role,0); }
    public long workersAtLeast(int level) { return citizenLevels.stream().filter(l->l>=level).count(); }
}
