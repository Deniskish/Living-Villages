package dev.livingvillages.task;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.progression.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.world.Observations;
import dev.livingvillages.world.Positions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static dev.livingvillages.world.Positions.*;

public record WorkContext(ServerLevel level,Village village,Plan plan,Task task,
                          Citizen citizen,Villager worker,Observations observations,Chronicle chronicle) {
    public long now() { return level.getGameTime(); }
    public void block(Reason reason,String message,Pos pos,String item,int required,int available) {
        Blocker b=Diagnostics.reason(reason,message,plan,task,now(),pos,item,required,available);
        if(task.blocker!=null&&task.blocker.type()==reason&&task.blocker.message().equals(message))
            b=new Blocker(reason,message,task.blocker.createdAt(),now(),plan.id,task.id,citizen.id,pos,item,required,available,plan.dependencies);
        String old=task.blocker==null?"":task.blocker.type()+task.blocker.message();
        task.block(b,now(),VillageConfig.RETRY_LIMIT.get());
        plan.block(b,now(),VillageConfig.RETRY_LIMIT.get());
        WorkerNavigation.release(level,worker);
        citizen.currentTask=null;task.worker=null;citizen.activity="WAITING";
        if(!old.equals(reason+message))chronicle.emit(village,now(),"PLAN_BLOCKED",citizen.id,plan.id,message);
    }
    public void block(Reason reason,String message) { block(reason,message,task.target,"",0,0); }
    public void progress() { task.progress(now()); plan.progress(now()); }
    public void xp(int n) { chronicle.xp(village,citizen,now(),n); }
    /** Records goods that this action physically produced (never transfers or deliveries). */
    public void produced(String item,int count) { dev.livingvillages.world.ProgressionService.recordProduction(chronicle,village,item,count,now()); }
    public CapabilityStatus capability(VillageCapability capability) {
        return dev.livingvillages.world.Capabilities.status(village,capability,observations);
    }
    /** Structured CAPABILITY_LOCKED / INFRASTRUCTURE_MISSING blocker for this task. */
    public void blockCapability(CapabilityStatus status) {
        if(status.available())throw new IllegalArgumentException("Capability is available: "+status.capability());
        var missing=status.requirements().stream().filter(r->!r.fulfilled()).findFirst();
        block(status.reason(),CapabilityPolicy.explain(status),task.target,status.capability().name(),
                missing.map(r->(int)r.required()).orElse(1),missing.map(r->(int)r.actual()).orElse(0));
    }
    public void equipped() {
        worker.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND,1.0F);
        citizen.equipment=worker.getMainHandItem().toString();
        chronicle.emit(village,now(),"TOOL_UPGRADED",citizen.id,plan.id,citizen.name+" equipped "+citizen.equipment);
        progress();
    }
    public boolean loaded(Pos p) {
        if(level.hasChunkAt(Positions.block(p)))return true;
        block(Reason.CHUNK_UNAVAILABLE,"Required chunk is unloaded",p,"",0,0);return false;
    }
    public boolean near(Pos p) {
        return worker.position().distanceToSqr(Vec3.atCenterOf(Positions.block(p)))<=9;
    }
    public boolean move(Pos p) {
        if(!loaded(p))return false;
        if(near(p)&&WorkerNavigation.visible(level,worker,worker.getEyePosition(),p)) { stopWalking();return true; }
        if(now()-Math.max(task.lastProgressTick,task.attemptStartedAt)>VillageConfig.STUCK_TIMEOUT.get()) {
            block(Reason.PATH_UNREACHABLE,"No progress reaching "+p,p,"",0,0);return false;
        }
        if(now()%40==0||worker.getNavigation().isDone()) {
            var approach=WorkerNavigation.approach(level,worker,p,p);
            if(approach==null) {
                block(Reason.PATH_UNREACHABLE,"No reachable route to "+p,p,"",0,0);return false;
            }
            WorkerNavigation.start(level,worker,approach);
        }
        return false;
    }
    public boolean interactAt(Pos target,int standingY) {
        if(!loaded(target))return false;
        if(WorkerNavigation.visible(level,worker,worker.getEyePosition(),target)) { stopWalking();return true; }
        if(now()-Math.max(task.lastProgressTick,task.attemptStartedAt)>VillageConfig.STUCK_TIMEOUT.get()) {
            block(Reason.PATH_UNREACHABLE,"Cannot reach visible interaction point",target,"",0,0);return false;
        }
        if(now()%40==0||worker.getNavigation().isDone()) {
            var approach=WorkerNavigation.approach(level,worker,new Pos(target.x(),standingY,target.z()),target);
            if(approach==null) { block(Reason.PATH_UNREACHABLE,"No reachable visible work position",target,"",0,0);return false; }
            WorkerNavigation.start(level,worker,approach);
        }
        return false;
    }
    private void stopWalking() {
        worker.getNavigation().stop();
        worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
    public boolean fetch(String item,int amount) {
        int carried=Inventories.count(worker.getInventory(),item);
        if(carried>=amount)return true;
        int physical=Inventories.stock(level,village).getOrDefault(item,0);
        for(Citizen c:village.citizens.values())if(!c.dead&&level.getEntity(c.id) instanceof Villager entity)
            physical+=Inventories.count(entity.getInventory(),item);
        int claimed=village.activePlans().stream().filter(p->p!=plan)
                .mapToInt(p->p.reservations.getOrDefault(item,0)).sum();
        int free=Math.max(0,physical-carried-claimed);
        if(free<amount-carried) {
            block(Reason.MISSING_RESOURCE,item+" required="+amount+" available="+(carried+free)+" reservedByOthers="+claimed,
                    task.target,item,amount,carried+free);return false;
        }
        Pos source=village.storage.stream().filter(p-> {
            if(!dev.livingvillages.world.Ownership.owns(level,village,p))return false;
            var inv=Inventories.container(level,p);
            return inv!=null&&Inventories.count(inv,item)>0;
        }).min(Comparator.comparingDouble(p->worker.distanceToSqr(p.x(),p.y(),p.z()))).orElse(null);
        if(source==null) {
            for(Citizen carrier:village.citizens.values()) {
                if(carrier.id.equals(citizen.id)||carrier.dead||carriesFor(carrier,item))continue;
                if(level.getEntity(carrier.id) instanceof Villager donor&&Inventories.count(donor.getInventory(),item)>0) {
                    if(!move(of(donor.blockPosition())))return false;
                    if(Inventories.transfer(donor.getInventory(),worker.getInventory(),item,amount-carried)>0)progress();
                    return Inventories.count(worker.getInventory(),item)>=amount;
                }
            }
            block(village.storage.isEmpty()?Reason.STORAGE_MISSING:Reason.MISSING_RESOURCE,
                    item+" required="+amount+" carried="+carried,task.target,item,amount,carried);return false;
        }
        if(!move(source))return false;
        var inv=Inventories.container(level,source);
        if(inv==null) { block(Reason.STORAGE_MISSING,"Storage disappeared");return false; }
        if(Inventories.transfer(inv,worker.getInventory(),item,amount-carried)>0)progress();
        else block(Reason.STORAGE_FULL,"Worker inventory is full");
        return Inventories.count(worker.getInventory(),item)>=amount;
    }
    /** True when the carrier is working a different plan that reserves this item (e.g. research cost). */
    private boolean carriesFor(Citizen carrier,String item) {
        if(carrier.currentTask==null)return false;
        for(Plan other:village.activePlans())
            if(other!=plan&&other.reservations.getOrDefault(item,0)>0&&other.tasks.stream().anyMatch(t->carrier.currentTask.equals(t.id)))return true;
        return false;
    }
    public boolean deliver() {
        if(worker.getInventory().isEmpty())return true;
        Pos destination=village.storage.stream().filter(p-> {
                    if(!dev.livingvillages.world.Ownership.owns(level,village,p))return false;
                    var container=Inventories.container(level,p);
                    return container!=null&&Inventories.canAccept(container,worker.getInventory());
                })
                .min(Comparator.comparingDouble(p->worker.distanceToSqr(p.x(),p.y(),p.z()))).orElse(null);
        if(destination==null) {
            boolean exists=village.storage.stream().anyMatch(p->Inventories.container(level,p)!=null);
            block(exists?Reason.STORAGE_FULL:Reason.STORAGE_MISSING,
                    exists?"No loaded container can accept this cargo":"No loaded village chest or barrel; cargo retained");return false;
        }
        if(!move(destination))return false;
        var container=Inventories.container(level,destination);
        if(container==null) { block(Reason.STORAGE_MISSING,"Storage disappeared");return false; }
        int moved=Inventories.transfer(worker.getInventory(),container,"",512);
        if(moved>0) { progress();citizen.statistics.merge("itemsDelivered",(long)moved,Long::sum); }
        if(!worker.getInventory().isEmpty()) { block(Reason.STORAGE_FULL,"Storage cannot accept remaining cargo");return false; }
        return true;
    }
    public void finish() {
        task.complete(now());plan.complete(now());plan.reservations.clear();citizen.currentTask=null;
        citizen.activity="IDLE";WorkerNavigation.release(level,worker);
        chronicle.emit(village,now(),"PLAN_COMPLETED",citizen.id,plan.id,plan.kind);
    }
}
