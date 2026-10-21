package dev.livingvillages.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import static dev.livingvillages.domain.Model.*;

public final class StateCodec {
    /** 1: v1.0. 2: v1.1 progression, production record, technologies and palettes. */
    public static final int VERSION = 2;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private StateCodec() {}
    public static String encode(World world) { return GSON.toJson(world); }
    public static World decode(String json) {
        var root=json==null?null:com.google.gson.JsonParser.parseString(json);
        if(root==null||!root.isJsonObject())throw new IllegalArgumentException("Unsupported Living Villages save version");
        // Read the stored version itself: a Gson field default must never stand in for it.
        var stored=root.getAsJsonObject().get("version");
        int version=stored==null||stored.isJsonNull()?1:stored.getAsInt();
        if(version<1||version>VERSION)
            throw new IllegalArgumentException("Unsupported Living Villages save version "+version+"; refusing to overwrite it");
        World world=GSON.fromJson(root,World.class);
        world.version=version;
        if(world.version==1)migrateV1(world);
        normalize(world);
        var owners=new java.util.HashSet<java.util.UUID>();
        for(Village village:world.villages.values()) {
            village.citizens.entrySet().removeIf(entry->{
                if(owners.add(entry.getKey()))return false;
                world.recoveryNotes.add("Removed duplicate citizen membership "+entry.getKey()+" from "+village.id);return true;
            });
            village.storage=new java.util.ArrayList<>(new java.util.LinkedHashSet<>(village.storage));
            var assignments=new java.util.HashMap<java.util.UUID,java.util.UUID>();
            for(Plan plan:village.plans.values()) {
                plan.village=village.id;
                if(plan.state().terminal()) {
                    plan.reservations.clear();
                    for(Task task:plan.tasks) {
                        if(!task.state().terminal())task.transition(State.CANCELLED);
                        task.worker=null;
                    }
                }
                if(plan.state()==State.BLOCKED&&plan.blocker==null)
                    throw new IllegalArgumentException("Corrupt plan without diagnostic: "+plan.id);
                for(Task task:plan.tasks) {
                    task.plan=plan.id;
                    if(task.state()==State.BLOCKED&&task.blocker==null)
                        throw new IllegalArgumentException("Corrupt task without diagnostic: "+task.id);
                    if(task.state().terminal())continue;
                    if(task.worker!=null&&(!village.citizens.containsKey(task.worker)||assignments.containsKey(task.worker))) {
                        world.recoveryNotes.add("Repaired worker reference on task "+task.id);
                        task.worker=null;
                        task.block(Diagnostics.reason(Reason.WORKER_UNAVAILABLE,"Saved worker reference is missing or assigned twice",
                                plan,task,plan.lastProgressTick,null,"",0,0),plan.lastProgressTick,1);
                    }
                    if(task.worker!=null)assignments.put(task.worker,task.id);
                }
            }
            for(Citizen c:village.citizens.values()) {
                c.village=village.id;
                var task=assignments.get(c.id);
                if(!java.util.Objects.equals(c.currentTask,task)) {
                    c.currentTask=task;c.activity="RECOVERED_ASSIGNMENT";
                    world.recoveryNotes.add("Rebuilt assignment for citizen "+c.id);
                }
            }
        }
        return world;
    }
    /**
     * v1.0 -> v1.1. Identities, XP, plans, history and buildings are kept unchanged.
     * Every v1.0 template and recipe was oak, so in-flight construction, crafting and
     * completed buildings keep the oak palette. Production records cannot be
     * reconstructed from v1.0 data and start empty; tiers are then derived again.
     */
    static void migrateV1(World world) {
        for(Village village:world.villages.values()) {
            int oak=0;
            for(Plan plan:village.plans.values())
                if(plan.palette==null&&(plan.blueprint!=null||(plan.kind!=null&&plan.kind.startsWith("CRAFT:")))) { plan.palette=Palettes.DEFAULT;oak++; }
            for(Building building:village.buildings.values())if(building.palette==null)building.palette=Palettes.DEFAULT;
            if(village.tier==null)village.tier=Tier.SETTLEMENT;
            village.tierHistory.putIfAbsent(Tier.SETTLEMENT.name(),village.createdAt);
            world.migrationNotes.add("Village "+village.id+" migrated from schema 1 to "+VERSION+": tier "+village.tier
                    +", "+village.citizens.size()+" citizens, "+village.plans.size()+" plans, "+village.buildings.size()
                    +" buildings, "+village.history.size()+" events retained; "+oak+" plans keep oak; production record starts empty");
        }
        world.version=VERSION;
    }
    /** Null-safe defaults for fields introduced after a document was written. */
    private static void normalize(World world) {
        if(world.villages==null)world.villages=new java.util.LinkedHashMap<>();
        for(Village v:world.villages.values()) {
            if(v.tier==null)v.tier=Tier.SETTLEMENT;
            if(v.tierHistory==null)v.tierHistory=new java.util.TreeMap<>();
            if(v.production==null)v.production=new java.util.TreeMap<>();
            if(v.technologies==null)v.technologies=new java.util.TreeMap<>();
            if(v.paletteScores==null)v.paletteScores=new java.util.TreeMap<>();
            if(v.palette==null)v.palette=Palettes.DEFAULT;
            for(Building b:v.buildings.values())if(b.palette==null)b.palette=Palettes.DEFAULT;
        }
    }
}
