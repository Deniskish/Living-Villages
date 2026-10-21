package dev.livingvillages.client;

import java.util.UUID;

/** Pure decision for an incoming Ledger snapshot; keeps the late-refresh rule testable. */
public final class LedgerRouting {
    private LedgerRouting() {}
    public enum Action { OPEN, UPDATE, IGNORE }
    /**
     * @param openVillage village of the Ledger screen currently shown, or null if none is open
     */
    public static Action route(boolean refresh,UUID openVillage,UUID snapshotVillage) {
        if(!refresh)return Action.OPEN; // Explicit Book + bell interaction.
        // A refresh answer never opens a screen: the player may have closed it meanwhile.
        if(openVillage==null)return Action.IGNORE;
        return openVillage.equals(snapshotVillage)?Action.UPDATE:Action.IGNORE;
    }
}
