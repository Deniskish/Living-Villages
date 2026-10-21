package dev.livingvillages.progression;

import dev.livingvillages.domain.Diagnostics;
import dev.livingvillages.domain.Model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Renders progression answers through the shared Diagnostics tree (commands, Ledger, logs, tests). */
public final class ProgressionDiagnostics {
    private ProgressionDiagnostics() {}
    /** "Why is the village not the next tier?" */
    public static Diagnostics.Node tier(VillageProgressionSnapshot s) {
        if(s.nextTier()==null)return new Diagnostics.Node(id("tier"),State.COMPLETED,s.highestUnlockedTier().label()+" (highest tier)",null,List.of());
        List<Diagnostics.Node> children=s.requirements().stream().map(r->requirement(r,s.evaluatedAt())).toList();
        boolean blocked=!s.missingRequirements().isEmpty();
        return new Diagnostics.Node(id("tier:"+s.nextTier()),blocked?State.BLOCKED:State.COMPLETED,"Tier "+s.nextTier().label(),
                blocked?blocker(Reason.REQUIREMENT_UNMET,s.explanation(),null,s.evaluatedAt()):null,children);
    }
    /** "Why can't this job run?" */
    public static Diagnostics.Node capability(CapabilityStatus c,long tick) {
        return new Diagnostics.Node(id("capability:"+c.capability()),c.available()?State.COMPLETED:State.BLOCKED,c.capability().name(),
                c.available()?null:blocker(c.reason(),CapabilityPolicy.explain(c),null,tick),
                c.requirements().stream().map(r->requirement(r,tick)).toList());
    }
    /** "Why can't this technology start?" */
    public static Diagnostics.Node technology(TechnologyStatus t,long tick) {
        boolean blocked=t.stage()==TechnologyStatus.Stage.LOCKED;
        State state=switch(t.stage()) {
            case UNLOCKED -> State.COMPLETED; case RESEARCHING -> State.IN_PROGRESS; case AVAILABLE -> State.PLANNED; case LOCKED -> State.BLOCKED;
        };
        return new Diagnostics.Node(id("technology:"+t.technology().id()),state,
                t.technology().name()+" ("+t.technology().id()+")",blocked?blocker(Reason.REQUIREMENT_UNMET,t.explanation(),null,tick):null,
                t.requirements().stream().map(r->requirement(r,tick)).toList());
    }
    public static Diagnostics.Node requirement(Requirement r,long tick) {
        return new Diagnostics.Node(id(r.key()),r.fulfilled()?State.COMPLETED:State.BLOCKED,r.line(),
                r.fulfilled()?null:blocker(Reason.REQUIREMENT_UNMET,r.message(),r,tick),List.of());
    }
    private static Blocker blocker(Reason reason,String message,Requirement r,long tick) {
        return new Blocker(reason,message,tick,tick,null,null,null,null,r==null?"":r.subject(),
                r==null?0:(int)Math.min(Integer.MAX_VALUE,r.required()),r==null?0:(int)Math.min(Integer.MAX_VALUE,r.actual()),List.of());
    }
    private static UUID id(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
}
