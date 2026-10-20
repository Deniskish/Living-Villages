package dev.livingvillages.config;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class VillageConfig {
    private static final ModConfigSpec.Builder B=new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue PLANNER_INTERVAL=B.defineInRange("plannerInterval",200,40,24000);
    public static final ModConfigSpec.IntValue RADIUS=B.defineInRange("workingRadius",48,16,96);
    public static final ModConfigSpec.IntValue SCAN_BUDGET=B.defineInRange("blocksScannedPerVillageStep",256,32,2048);
    public static final ModConfigSpec.IntValue STUCK_TIMEOUT=B.defineInRange("stuckTimeout",600,100,6000);
    public static final ModConfigSpec.IntValue RETRY_LIMIT=B.defineInRange("retryLimit",3,1,20);
    public static final ModConfigSpec.IntValue ACTIVE_PLANS=B.defineInRange("maxActivePlans",24,12,48);
    public static final ModConfigSpec.IntValue ANIMAL_CAP=B.defineInRange("maxAnimals",16,2,64);
    public static final ModConfigSpec.IntValue WORKERS=B.defineInRange("workerStepsPerTick",8,1,64);
    public static final ModConfigSpec.BooleanValue DEBUG=B.define("debugLogging",false);
    public static final ModConfigSpec SPEC=B.build();
    private VillageConfig() {}
}
