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
        for(Village village:world.villages.values()) {
            village.storage=new java.util.ArrayList<>(new java.util.LinkedHashSet<>(village.storage));
            var assignments=new java.util.HashMap<java.util.UUID,java.util.UUID>();
            for(Plan plan:village.plans.values()) {
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
