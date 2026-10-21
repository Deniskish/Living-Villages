package dev.livingvillages.world;

import dev.livingvillages.domain.Model.*;
import dev.livingvillages.progression.*;

/** World adapter: capability status from domain facts plus the loaded workstation index. */
public final class Capabilities {
    private Capabilities() {}
    public static VillageFacts facts(Village v,Observations observed) {
        boolean furnace=observed!=null&&!observed.furnaces.isEmpty(),table=observed!=null&&!observed.tables.isEmpty();
        return VillageFacts.of(v,v.beds,distinct(v),0,furnace,table);
    }
    public static CapabilityStatus status(Village v,VillageCapability capability,Observations observed) {
        return CapabilityPolicy.status(capability,v.tier,facts(v,observed));
    }
    public static boolean available(Village v,VillageCapability capability,Observations observed) {
        return status(v,capability,observed).available();
    }
    static int distinct(Village v) { return (int)v.storage.stream().distinct().count(); }
}
