package dev.livingvillages.world;
import dev.livingvillages.ai.VillagePlanner;
import dev.livingvillages.building.*;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.task.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;
public final class Simulation {
    private final Chronicle chronicle=new Chronicle();
    private final Discovery discovery=new Discovery(chronicle);
    private final Map<ServerLevel,Map<UUID,Observations>> observations=new IdentityHashMap<>();
    private Blueprints blueprints;
    private VillagePlanner planner;
    private ConstructionJob construction;
    private final Job harvest=new HarvestJob(),production=new ProductionJob(),animals=new AnimalJob(),utility=new PlaceUtilityJob(),
            smelting=new SmeltingJob(),population=new PopulationJob(),wool=new WoolJob(),road=new RoadJob();
    private long workerCursor;
    public void clear() { observations.clear();blueprints=null;planner=null;construction=null; }
    public void tick(ServerLevel level) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Village simulation must run on the server thread");
        if(level.getGameTime()%10!=0)return;
        if(blueprints==null) { blueprints=new Blueprints();planner=new VillagePlanner(chronicle,blueprints);construction=new ConstructionJob(blueprints); }
        VillageSavedData data=VillageSavedData.get(level);
        if(level.getGameTime()%200==0)discovery.discover(level,data.world);
        var indexes=observations.computeIfAbsent(level,k->new HashMap<>());
        indexes.keySet().removeIf(id->!data.world.villages.containsKey(id));
        List<Runnable> work=new ArrayList<>();
        for(Village v:data.world.villages.values()) {
            boolean centerLoaded=level.hasChunkAt(Positions.block(v.center));
            if(!centerLoaded)indexes.remove(v.id);
            Observations observed=centerLoaded?indexes.computeIfAbsent(v.id,k->{
                Observations result=new Observations();
                level.getPoiManager().getInRange(t->t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                        Positions.block(v.center),v.radius,net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                        .filter(p->level.hasChunkAt(p.getPos())&&Ownership.owns(level,v,Positions.of(p.getPos())))
                        .forEach(p->result.beds.add(Positions.of(p.getPos())));
                return result;
            }):new Observations();
            if(centerLoaded) { observed.scan(level,v);v.scanSteps++; }
            if(centerLoaded&&observed.ready(v)&&level.getGameTime()-v.lastPlanTick>=VillageConfig.PLANNER_INTERVAL.get()) {
                planner.tick(level,v,observed);v.strategicSteps++;
            }
            var plans=v.activePlans().stream()
                    .sorted(Comparator.comparingDouble((Plan p)->p.priority).reversed()).toList();
            for(Plan p:plans)for(Task task:p.tasks)
                if(!task.state().terminal()&&level.getGameTime()>=task.nextRetryTick)work.add(()->execute(level,v,p,task,observed));
        }
        for(int n=0;n<Math.min(work.size(),VillageConfig.WORKERS.get());n++)
            work.get((int)((workerCursor+n)%work.size())).run();
        workerCursor+=Math.min(work.size(),VillageConfig.WORKERS.get());
        if(level.getGameTime()%100==0)for(var player:level.players())
            if(player.hasPermissions(2)&&player.getPersistentData().getBoolean("livingvillages:debug"))
                data.world.villages.values().stream().filter(v->v.contains(Positions.of(player.blockPosition()))).findFirst()
                        .ifPresent(v->player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                v.name+" | active="+v.plans.values().stream().filter(p->!p.state().terminal()).count()
                                        +" | blocked="+v.plans.values().stream().filter(p->p.blocker!=null&&!p.state().terminal()).count()
                                        +" | citizens="+v.population()),true));
        if(VillageConfig.DEBUG.get()&&level.getGameTime()%200==0)
            dev.livingvillages.LivingVillagesMod.LOG.info("event=simulation_budget dimension={} villages={} runnable_tasks={} budget={}",
                    level.dimension().location(),data.world.villages.size(),work.size(),VillageConfig.WORKERS.get());
        if(!data.world.villages.isEmpty())data.setDirty();
    }
    private void execute(ServerLevel level,Village v,Plan p,Task task,Observations observations) {
        long now=level.getGameTime();
        var invalid=Diagnostics.invalidDependency(p,v.plans);
        if(invalid.isPresent()) {
            Citizen assigned=v.citizens.get(task.worker);
            if(assigned!=null) {
                assigned.currentTask=null;
                if(level.getEntity(assigned.id) instanceof Villager worker)WorkerNavigation.release(level,worker);
            }
            task.worker=null;
            task.block(Diagnostics.reason(Reason.DEPENDENCY_BLOCKED,invalid.get(),p,task,now,null,"",0,0),now,1);
            p.blocker=task.blocker;p.transition(State.BLOCKED);return;
        }
        Citizen citizen=task.worker==null?null:v.citizens.get(task.worker);
        if(citizen!=null&&citizen.dead) { citizen.currentTask=null;task.worker=null;citizen=null; }
        if(citizen==null) {
            citizen=v.citizens.values().stream().filter(c->!c.dead&&c.currentTask==null)
                    .filter(c->level.getEntity(c.id) instanceof Villager e&&!e.isBaby())
                    .filter(c->qualified(c,task))
                    .min(Comparator.comparingLong(c->(c.role==task.role?0:10000)-c.experience.getOrDefault(task.role,0L))).orElse(null);
            if(citizen==null) {
                task.block(Diagnostics.reason(Reason.NO_QUALIFIED_WORKER,"No loaded available adult worker",p,task,now,null,"",1,0),now,VillageConfig.RETRY_LIMIT.get());
                p.blocker=task.blocker;p.transition(State.BLOCKED);return;
            }
            if(citizen.role!=task.role) {
                citizen.role=task.role;chronicle.emit(v,now,"ROLE_ASSIGNED",citizen.id,task.id,citizen.role.toString());
            }
            task.worker=citizen.id;citizen.currentTask=task.id;task.attemptStartedAt=now;task.transition(State.ASSIGNED);
            if(!citizen.id.equals(task.lastAssignedWorker)) {
                chronicle.emit(v,now,"TASK_ASSIGNED",citizen.id,task.id,p.kind);
                task.lastAssignedWorker=citizen.id;
            }
        }
        if(!(level.getEntity(citizen.id) instanceof Villager worker)) {
            task.block(Diagnostics.reason(Reason.CHUNK_UNAVAILABLE,"Citizen is unloaded; assignment retained",p,task,now,null,"",0,0),now,VillageConfig.RETRY_LIMIT.get());
            p.blocker=task.blocker;p.transition(State.BLOCKED);return;
        }
        if(worker.isSleeping()||level.isNight()||worker.isTrading()
                ||worker.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE)) {
            WorkerNavigation.release(level,worker);
            citizen.activity=worker.isTrading()?"TRADING":worker.isSleeping()||level.isNight()?"RESTING":"AVOIDING_THREAT";
            task.nextRetryTick=now+100;return;
        }
        citizen.activity=task.phase;citizen.equipment=worker.getMainHandItem().toString();
        citizen.ageTicks=worker.getAge();
        worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
                .ifPresent(home->citizenHome(v,worker.getUUID(),Positions.of(home.pos())));
        WorkContext context=new WorkContext(level,v,p,task,citizen,worker,observations,chronicle);
        if(task.nextRetryTick>0) { task.attemptStartedAt=now;task.nextRetryTick=0; }
        Job job=p.blueprint!=null?construction:p.kind.startsWith("CRAFT:")?production:p.kind.startsWith("PLACE:")?utility:
                p.kind.equals("SMELT_IRON")?smelting:p.kind.equals("FEED_CITIZENS")?population:
                p.kind.equals("SHEAR")?wool:p.kind.startsWith("ROAD:")?road:task.role==Role.ANIMAL_KEEPER?animals:harvest;
        long started=System.nanoTime();v.workerSteps++;
        try { job.tick(context); }
        catch(RuntimeException error) {
            dev.livingvillages.LivingVillagesMod.LOG.error("event=unrecoverable_ai_error village={} plan={} task={}",v.id,p.id,task.id,error);
            context.block(Reason.UNKNOWN,"Executor failed: "+error.getClass().getSimpleName()+"; inspect server log");
            task.transition(State.FAILED);p.transition(State.FAILED);p.reservations.clear();
        }
        finally {
            long elapsed=System.nanoTime()-started;v.workerNanos+=elapsed;v.maxWorkerNanos=Math.max(v.maxWorkerNanos,elapsed);
        }
    }
    private static void citizenHome(Village v,UUID citizen,Pos home) { v.citizens.get(citizen).home=home; }
    private static boolean qualified(Citizen c,Task task) {
        int minimum=task.role==Role.MINER&&task.desiredResource.equals("minecraft:raw_iron")?3:
                task.role==Role.SMITH&&task.item.contains("iron_")?3:1;
        return Rules.level(c.experience.getOrDefault(task.role,0L))>=minimum;
    }
    public void death(LivingEntity entity) {
        if(!(entity instanceof Villager worker)||!(entity.level() instanceof ServerLevel level))return;
        var data=VillageSavedData.get(level);
        for(Village v:data.world.villages.values()) {
            Citizen c=v.citizens.get(worker.getUUID());
            if(c==null||c.dead)continue;
            c.dead=true;c.currentTask=null;
            // Vanilla villagers do not normally drop their complete working inventory.
            for(int i=0;i<worker.getInventory().getContainerSize();i++) {
                var stack=worker.getInventory().removeItemNoUpdate(i);
                if(!stack.isEmpty())worker.spawnAtLocation(stack);
            }
            chronicle.emit(v,level.getGameTime(),"CITIZEN_DIED",c.id,null,c.name);
            for(Plan p:v.plans.values())for(Task t:p.tasks)if(c.id.equals(t.worker)&&!t.state().terminal()) {
                t.worker=null;t.block(Diagnostics.reason(Reason.WORKER_UNAVAILABLE,"Assigned citizen died",p,t,level.getGameTime(),null,"",0,0),level.getGameTime(),1);
                p.blocker=t.blocker;p.transition(State.BLOCKED);
            }
            data.setDirty();
        }
    }
}
