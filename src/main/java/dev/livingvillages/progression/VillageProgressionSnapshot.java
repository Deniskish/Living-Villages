package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.Tier;
import java.util.List;

/**
 * Result of one progression evaluation.
 *
 * @param currentTier tier the village qualifies for right now, or null when even Tier I is unmet
 * @param highestUnlockedTier historical tier; never decreases
 * @param nextTier highestUnlockedTier + 1, or null at CAPITAL
 * @param requirements cumulative requirements of nextTier (lower-tier regressions included)
 */
public record VillageProgressionSnapshot(Tier currentTier,Tier highestUnlockedTier,Tier nextTier,
                                         List<Requirement> requirements,List<Requirement> fulfilledRequirements,
                                         List<Requirement> missingRequirements,List<CapabilityStatus> unlockedCapabilities,
                                         List<CapabilityStatus> lockedCapabilities,List<TechnologyStatus> technologies,
                                         String explanation,long evaluatedAt) {
    public VillageProgressionSnapshot {
        requirements=List.copyOf(requirements);fulfilledRequirements=List.copyOf(fulfilledRequirements);
        missingRequirements=List.copyOf(missingRequirements);unlockedCapabilities=List.copyOf(unlockedCapabilities);
        lockedCapabilities=List.copyOf(lockedCapabilities);technologies=List.copyOf(technologies);
    }
    public List<CapabilityStatus> capabilities() {
        return java.util.stream.Stream.concat(unlockedCapabilities.stream(),lockedCapabilities.stream())
                .sorted(java.util.Comparator.comparing(CapabilityStatus::capability)).toList();
    }
    public CapabilityStatus capability(VillageCapability capability) {
        return capabilities().stream().filter(s->s.capability()==capability).findFirst().orElseThrow();
    }
}
