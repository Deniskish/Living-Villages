package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import java.util.*;

/**
 * Immutable technology definition with a stable id. Costs are palette tokens (WOOD_LOG)
 * or literal item ids and are resolved once when a research plan is created.
 *
 * @param workUnits research actions at the workstation; each takes {@link #UNIT_TICKS}
 */
public record Technology(String id,String name,TechnologyBranch branch,List<String> prerequisites,Tier tier,
                         Role role,int roleLevel,Map<String,Integer> costs,
                         VillageCapability.Infrastructure workstation,int workUnits,String effect) {
    public static final int UNIT_TICKS=60;
    public Technology {
        prerequisites=List.copyOf(prerequisites);costs=Collections.unmodifiableMap(new TreeMap<>(costs));
        if(workUnits<=0||costs.isEmpty())throw new IllegalArgumentException("Technology needs physical cost and work: "+id);
    }
    /** Capabilities whose unlock depends on this technology (derived; single source of truth). */
    public List<VillageCapability> unlocks() {
        return Arrays.stream(VillageCapability.values()).filter(c->id.equals(c.technology())).toList();
    }
    public String planKind() { return "RESEARCH:"+id; }
}
