package dev.livingvillages.progression;

import dev.livingvillages.domain.Model.*;
import java.util.*;

/** Single source of capability decisions: tier/technology unlock plus physical prerequisites. */
public final class CapabilityPolicy {
    private CapabilityPolicy() {}
    public static CapabilityStatus status(VillageCapability capability,Tier highest,VillageFacts facts) {
        List<Requirement> requirements=new ArrayList<>();
        requirements.add(tier(capability.tier(),highest));
        if(capability.technology()!=null)requirements.add(technology(capability.technology(),facts));
        boolean unlocked=requirements.stream().allMatch(Requirement::fulfilled);
        Requirement infrastructure=infrastructure(capability.infrastructure(),facts);
        if(infrastructure!=null)requirements.add(infrastructure);
        boolean available=unlocked&&(infrastructure==null||infrastructure.fulfilled());
        String explanation;Reason reason=null;
        if(available)explanation=capability.description();
        else if(!unlocked) {
            reason=Reason.CAPABILITY_LOCKED;
            explanation="locked: "+requirements.stream().filter(r->!r.fulfilled()).map(Requirement::message).findFirst().orElseThrow();
        } else {
            reason=Reason.INFRASTRUCTURE_MISSING;explanation="unavailable: "+infrastructure.message();
        }
        return new CapabilityStatus(capability,unlocked,available,requirements,reason,explanation);
    }
    public static List<CapabilityStatus> all(Tier highest,VillageFacts facts) {
        return Arrays.stream(VillageCapability.values()).map(c->status(c,highest,facts)).toList();
    }
    /** Full diagnostic sentence, e.g. "ADVANCED_MINING locked: Village tier IV — City required". */
    public static String explain(CapabilityStatus status) { return status.capability().name()+" "+status.explanation(); }
    static Requirement tier(Tier required,Tier highest) {
        boolean ok=highest.atLeast(required);
        return new Requirement(RequirementType.TIER,required.name(),required.number(),highest.number(),ok,
                ok?"Village tier "+required.label()+" reached":"Village tier "+required.label()+" required (current "+highest.label()+")");
    }
    static Requirement technology(String id,VillageFacts facts) {
        boolean ok=facts.technologies().contains(id);
        String name=Technologies.get(id).map(Technology::name).orElse(id);
        return new Requirement(RequirementType.TECHNOLOGY,id,1,ok?1:0,ok,ok?"Technology "+name+" unlocked":"Technology "+name+" required");
    }
    static Requirement infrastructure(VillageCapability.Infrastructure infrastructure,VillageFacts facts) {
        return switch(infrastructure) {
            case NONE -> null;
            case FURNACE -> present(RequirementType.WORKSTATION,"minecraft:furnace",facts.furnace(),"Furnace");
            case CRAFTING_TABLE -> present(RequirementType.WORKSTATION,"minecraft:crafting_table",facts.craftingTable(),"Crafting table");
            case SMITHY -> present(RequirementType.INFRASTRUCTURE,"SMITHY",facts.buildings("SMITHY")>0,"Smithy");
        };
    }
    static Requirement present(RequirementType type,String subject,boolean ok,String label) {
        return new Requirement(type,subject,1,ok?1:0,ok,ok?label+" present":label+" is missing");
    }
}
