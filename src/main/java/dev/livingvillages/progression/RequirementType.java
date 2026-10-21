package dev.livingvillages.progression;

/** Machine-readable requirement kinds shared by tiers, capabilities and technologies. */
public enum RequirementType {
    // Village tier facts.
    POPULATION, HOUSING, BUILDING, BUILDING_TYPES, STORAGE, PROFESSION, SKILLED_WORKERS,
    PRODUCTION, FOOD_RESERVE, TECHNOLOGIES,
    // Capability and technology gates.
    TIER, TECHNOLOGY, WORKSTATION, INFRASTRUCTURE, RESOURCE
}
