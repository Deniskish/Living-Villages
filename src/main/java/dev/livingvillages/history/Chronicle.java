package dev.livingvillages.history;
import dev.livingvillages.LivingVillagesMod;
import dev.livingvillages.domain.Model.*;
import java.util.*;
public final class Chronicle {
    public void emit(Village v,long tick,String type,UUID actor,UUID target,String message) {
        emit(v,tick,type,actor,target,message,Map.of());
    }
    /** @param parameters structured, machine-readable event data (e.g. tier, technology) */
    public void emit(Village v,long tick,String type,UUID actor,UUID target,String message,Map<String,String> parameters) {
        Event event=new Event(UUID.randomUUID(),tick,type,v.id,actor,target,message,Collections.unmodifiableMap(new TreeMap<>(parameters)));
        v.history.add(event);
        Citizen citizen=v.citizens.get(actor);
        if(citizen!=null)citizen.history.add(event.id());
        LivingVillagesMod.LOG.info("event={} village={} actor={} target={} message={} parameters={}",type,v.id,actor,target,message,event.parameters());
    }
    public void xp(Village v,Citizen citizen,long tick,int amount) {
        int old=citizen.level();
        citizen.experience.merge(citizen.role,(long)amount,Long::sum);
        String skill=switch(citizen.role) {
            case LUMBERJACK -> "WOODCUTTING"; case MINER -> "MINING"; case BUILDER -> "BUILDING";
            case FARMER,ANIMAL_KEEPER -> "FARMING"; case SMITH -> "SMITHING";
        };
        int oldSkill=dev.livingvillages.domain.Rules.level(citizen.skills.getOrDefault(skill,0L));
        citizen.skills.merge(skill,(long)amount,Long::sum);
        citizen.statistics.merge("successfulWork",(long)amount,Long::sum);
        if(citizen.level()>old)emit(v,tick,"ROLE_LEVEL_UP",citizen.id,null,citizen.name+" reached "+citizen.role+" "+citizen.level());
        int skillLevel=dev.livingvillages.domain.Rules.level(citizen.skills.get(skill));
        if(skillLevel>oldSkill)emit(v,tick,"SKILL_LEVEL_UP",citizen.id,null,citizen.name+" reached "+skill+" "+skillLevel);
    }
}
