package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.Tier;

/**
 * What a village may do. Unlocking is strategic (tier, technology); availability also
 * requires current physical infrastructure. Planner and executors ask for a capability,
 * never for a tier number. New branches (e.g. military) add constants here.
 */
public enum VillageCapability {
    BASIC_FORESTRY(Tier.SETTLEMENT,null,Infrastructure.NONE,"Fell small trees and deliver logs"),
    BASIC_FARMING(Tier.SETTLEMENT,null,Infrastructure.NONE,"Harvest, replant and deliver crops"),
    BASIC_MINING(Tier.SETTLEMENT,null,Infrastructure.NONE,"Mine exposed stone and ores permitted by miner level"),
    BASIC_CONSTRUCTION(Tier.SETTLEMENT,null,Infrastructure.NONE,"Build tier-one houses, storage, smithy and pen"),
    ANIMAL_HUSBANDRY(Tier.SETTLEMENT,null,Infrastructure.NONE,"Breed and shear animals in fenced pens"),
    IRON_SMELTING(Tier.SETTLEMENT,null,Infrastructure.FURNACE,"Smelt raw iron in a real furnace"),
    IRON_TOOLSMITHING(Tier.VILLAGE,null,Infrastructure.SMITHY,"Craft iron tools in an intact smithy (worker Smith III)"),
    TECHNOLOGY_RESEARCH(Tier.VILLAGE,null,Infrastructure.NONE,"Research technologies with physical materials"),
    CHARCOAL_BURNING(Tier.VILLAGE,"economy.charcoal_burning",Infrastructure.FURNACE,"Burn logs into charcoal; iron smelting may use charcoal"),
    ADVANCED_CONSTRUCTION(Tier.TOWN,"infrastructure.timber_framing",Infrastructure.NONE,"Build timber-framed tier-two houses"),
    ADVANCED_MINING(Tier.CITY,null,Infrastructure.NONE,"Index and mine exposed ores deeper below the bell"),
    DIAMOND_MINING(Tier.CITY,"expansion.deep_prospecting",Infrastructure.NONE,"Mine exposed diamond ore (Miner V with iron pickaxe)"),
    EXPANDED_TERRITORY(Tier.CAPITAL,null,Infrastructure.NONE,"Working boundary grows by "+VillageCapability.TERRITORY_GROWTH+" blocks");
    /** Blocks added to the working radius when EXPANDED_TERRITORY unlocks. */
    public static final int TERRITORY_GROWTH=16;
    /** Additional depth below the bell indexed/mined with ADVANCED_MINING. */
    public static final int ADVANCED_MINING_DEPTH=8;
    public enum Infrastructure {
        NONE(""), FURNACE("Furnace"), CRAFTING_TABLE("Crafting table"), SMITHY("Smithy");
        private final String label;
        Infrastructure(String label) { this.label=label; }
        public String label() { return label; }
    }
    private final Tier tier;
    private final String technology;
    private final Infrastructure infrastructure;
    private final String description;
    VillageCapability(Tier tier,String technology,Infrastructure infrastructure,String description) {
        this.tier=tier;this.technology=technology;this.infrastructure=infrastructure;this.description=description;
    }
    public Tier tier() { return tier; }
    /** Technology id required in addition to the tier, or null. */
    public String technology() { return technology; }
    public Infrastructure infrastructure() { return infrastructure; }
    public String description() { return description; }
    public String title() {
        String s=name().replace('_',' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }
}
