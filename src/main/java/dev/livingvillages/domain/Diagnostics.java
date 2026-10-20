package dev.livingvillages.domain;

import java.util.*;
import static dev.livingvillages.domain.Model.*;

public final class Diagnostics {
    private Diagnostics() {}
    public record Node(UUID id, State state, String title, Blocker blocker,List<Node> children) {}
    public static Optional<String> invalidDependency(Plan p,Map<UUID,Plan> plans) {
        return invalidDependency(p,plans,new HashSet<>());
    }
    private static Optional<String> invalidDependency(Plan p,Map<UUID,Plan> plans,Set<UUID> path) {
        if(!path.add(p.id))return Optional.of("Cyclic dependency at "+p.id);
        for(UUID id:p.dependencies) {
            Plan dependency=plans.get(id);
            if(dependency==null)return Optional.of("Missing dependency "+id);
            var invalid=invalidDependency(dependency,plans,new HashSet<>(path));
            if(invalid.isPresent())return invalid;
        }
        return Optional.empty();
    }
    public static Node tree(Plan plan,Map<UUID,Plan> plans) { return tree(plan,plans,new HashSet<>(),new int[]{128}); }
    private static Node tree(Plan plan,Map<UUID,Plan> plans,Set<UUID> visited,int[] budget) {
        if(budget[0]--<=0||visited.size()>=16)return new Node(plan.id,plan.state(),
                "Further dependencies omitted; inspect "+plan.id,plan.blocker,List.of());
        if(!visited.add(plan.id)) return new Node(plan.id,State.BLOCKED,"Dependency cycle",
                reason(Reason.DEPENDENCY_BLOCKED,"Cyclic dependency",plan,null,0,null,"",0,0),List.of());
        List<Node> children=new ArrayList<>();
        for(UUID id:plan.dependencies) {
            if(budget[0]<=0)break;
            Plan dependency=plans.get(id);
            if(dependency==null) children.add(new Node(id,State.BLOCKED,"Missing dependency",
                    reason(Reason.DEPENDENCY_BLOCKED,"Persisted dependency does not exist: "+id,plan,null,0,null,"",0,0),List.of()));
            else children.add(tree(dependency,plans,new HashSet<>(visited),budget));
        }
        for(Task task:plan.tasks) {
            if(budget[0]--<=0)break;
            children.add(new Node(task.id,task.state(),task.role+" / "+task.phase,task.blocker,List.of()));
        }
        return new Node(plan.id,plan.state(),plan.kind,plan.blocker,List.copyOf(children));
    }
    public static Blocker reason(Reason type,String message,Plan plan,Task task,long tick,Pos position,
                                 String resource,int required,int available) {
        return new Blocker(type,message,tick,tick,plan==null?null:plan.id,task==null?null:task.id,
                task==null?null:task.worker,position,resource,required,available,
                plan==null?List.of():plan.dependencies);
    }
    public static List<String> lines(Node node) {
        List<String> result=new ArrayList<>(); append(node,result,0); return result;
    }
    private static void append(Node node,List<String> lines,int depth) {
        lines.add("  ".repeat(Math.min(depth,16))+node.title()+" "+node.state()+
                (node.blocker()==null?"":" — "+node.blocker().type()+": "+node.blocker().message()));
        if(depth<16) for(Node child:node.children())append(child,lines,depth+1);
    }
}
