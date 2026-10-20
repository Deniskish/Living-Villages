package dev.livingvillages.world;

import dev.livingvillages.domain.Model.*;
import dev.livingvillages.persistence.VillageSavedData;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Deterministic tie-break for overlapping working boundaries; no shared stock. */
public final class Ownership {
    private Ownership() {}
    public static Optional<Village> at(Collection<Village> villages,Pos position) {
        return villages.stream().filter(v->v.contains(position))
                .min(Comparator.comparingLong((Village v)->v.center.distanceSquared(position)).thenComparing(v->v.id));
    }
    public static boolean owns(ServerLevel level,Village village,Pos position) {
        if(!village.contains(position))return false;
        var villages=VillageSavedData.get(level).world.villages;
        // Standalone executor fixtures are not registered live simulations.
        if(!villages.containsKey(village.id))return true;
        return at(villages.values(),position).map(v->v.id.equals(village.id)).orElse(false);
    }
}
