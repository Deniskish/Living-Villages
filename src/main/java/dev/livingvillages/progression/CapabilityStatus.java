package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.Reason;
import java.util.List;

/**
 * @param unlocked tier/technology permit the capability
 * @param available unlocked and the physical infrastructure exists now
 * @param reason null when available; CAPABILITY_LOCKED or INFRASTRUCTURE_MISSING otherwise
 */
public record CapabilityStatus(VillageCapability capability,boolean unlocked,boolean available,
                               List<Requirement> requirements,Reason reason,String explanation) {
    public CapabilityStatus { requirements=List.copyOf(requirements); }
    public String line() {
        return (available?"[✓] ":unlocked?"[!] ":"[✗] ")+capability.title()+(available?"":" — "+explanation);
    }
}
