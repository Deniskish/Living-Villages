package dev.livingvillages.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import static dev.livingvillages.domain.Model.*;

public final class StateCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private StateCodec() {}
    public static String encode(World world) { return GSON.toJson(world); }
    public static World decode(String json) {
        World world=GSON.fromJson(json,World.class);
        if(world==null||world.version!=1) throw new IllegalArgumentException("Unsupported Living Villages save version");
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
}
