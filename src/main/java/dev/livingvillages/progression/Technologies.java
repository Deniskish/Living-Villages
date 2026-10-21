package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import java.util.*;
import static dev.livingvillages.progression.VillageCapability.Infrastructure.*;

/** Bundled technology registry. Immutable; deterministic research order is list order. */
public final class Technologies {
    private Technologies() {}
    public static final Technology CHARCOAL_BURNING=new Technology("economy.charcoal_burning","Charcoal Burning",
            TechnologyBranch.ECONOMY,List.of(),Tier.VILLAGE,Role.SMITH,2,
            Map.of("WOOD_LOG",16,"minecraft:cobblestone",8),FURNACE,6,
            "Smiths burn logs into charcoal; iron smelting may use charcoal when no coal is stocked");
    public static final Technology TIMBER_FRAMING=new Technology("infrastructure.timber_framing","Timber Framing",
            TechnologyBranch.INFRASTRUCTURE,List.of(),Tier.TOWN,Role.BUILDER,3,
            Map.of("WOOD_LOG",24,"WOOD_PLANKS",32),CRAFTING_TABLE,8,
            "Builders may choose the timber-framed three-bed house_t2_01");
    public static final Technology DEEP_PROSPECTING=new Technology("expansion.deep_prospecting","Deep Prospecting",
            TechnologyBranch.EXPANSION,List.of(CHARCOAL_BURNING.id()),Tier.CITY,Role.MINER,4,
            Map.of("minecraft:iron_ingot",8,"minecraft:cobblestone",64,"WOOD_PLANKS",16),CRAFTING_TABLE,10,
            "Miner V citizens with an iron pickaxe may mine exposed, reachable diamond ore");
    public static final List<Technology> ALL=List.of(CHARCOAL_BURNING,TIMBER_FRAMING,DEEP_PROSPECTING);
    public static Optional<Technology> get(String id) { return ALL.stream().filter(t->t.id().equals(id)).findFirst(); }
    public static Optional<Technology> ofPlanKind(String kind) {
        return kind!=null&&kind.startsWith("RESEARCH:")?get(kind.substring("RESEARCH:".length())):Optional.empty();
    }
}
