package dev.livingvillages.network;
import com.google.gson.Gson;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
public record LedgerView(UUID village,String name,Map<String,List<Row>> tabs) {
    public record Row(String title,List<String> detail) {}
    private static final Gson JSON=new Gson();
    public String json() { return JSON.toJson(this); }
    public static LedgerView parse(String json) { return JSON.fromJson(json,LedgerView.class); }
    public static LedgerView create(ServerLevel level,Village v,boolean admin) {
        Map<String,List<Row>> tabs=new LinkedHashMap<>();
        long active=v.plans.values().stream().filter(p->!p.state().terminal()).count();
        long blocked=v.plans.values().stream().filter(p->p.state()==State.BLOCKED).count();
        tabs.put("Overview",new ArrayList<>(List.of(
                row("Population: "+v.population()+" | known citizens: "+v.citizens.size()),
                row("Known beds: "+v.beds+" | buildings: "+v.buildings.size()),
                row("Active plans: "+active+" | blocked: "+blocked),
                row("Boundary: "+v.radius+" blocks | center: "+v.center),
                row("Food in storage: "+Inventories.stock(level,v).entrySet().stream()
                        .filter(e->e.getKey().matches(".*(bread|carrot|potato|beetroot|wheat)$")).toList()),
                row("Snapshot tick: "+level.getGameTime()+" (Refresh to update)"))));
        v.goals.forEach(g->tabs.get("Overview").add(row(g.need()+" need="+String.format(Locale.ROOT,"%.2f",g.urgency())+
                " utility="+String.format(Locale.ROOT,"%.2f",g.utility()))));
        tabs.put("Citizens",v.citizens.values().stream().limit(200).map(c-> {
            List<String> details=new ArrayList<>(List.of("ID: "+c.id,"XP: "+c.experience,"Skills: "+c.skills,
                "Task: "+c.currentTask,"Activity: "+c.activity,"Home: "+c.home,"Equipment: "+c.equipment,
                "Lifetime: "+c.statistics,"Registered: "+c.registeredAt,"Events: "+c.history.size()));
            v.history.stream().filter(e->c.id.equals(e.actor())).skip(Math.max(0,c.history.size()-12))
                    .forEach(e->details.add("Day "+e.tick()/24000+" — "+e.message()));
            return new Row(c.name+" — "+c.role+" "+c.level()+(c.dead?" [deceased]":""),details);
        }).toList());
        List<Row> resources=new ArrayList<>();
        Inventories.stock(level,v).forEach((id,n)->resources.add(row(id+" = "+n)));
        v.storage.forEach(pos->resources.add(row("Container: "+pos)));
        for(Citizen c:v.citizens.values())if(!c.dead&&level.getEntity(c.id) instanceof net.minecraft.world.entity.npc.Villager worker) {
            Map<String,Integer> carried=new TreeMap<>();Inventories.addStock(worker.getInventory(),carried);
            if(!carried.isEmpty())resources.add(new Row("Carried by "+c.name,List.of(carried.toString())));
        }
        tabs.put("Resources",resources);
        tabs.put("Buildings",v.buildings.values().stream().limit(200).map(b->new Row(b.blueprint+" — "+b.condition,
                List.of("ID: "+b.id,"Origin: "+b.origin,"Entrance: "+b.entrance,"Completed: "+b.completedAt))).toList());
        List<Plan> plans=v.plans.values().stream().sorted(Comparator.comparing((Plan p)->p.state().terminal())
                .thenComparing(Comparator.comparingLong((Plan p)->p.createdAt).reversed())).limit(150).toList();
        tabs.put("Plans",plans.stream().map(p-> {
            List<String> detail=new ArrayList<>(List.of("ID: "+p.id,"Priority: "+p.priority,
                    "Progress: "+p.progress+(p.totalWork>0?" / "+p.totalWork:"")+" blocks/actions","Requirements: "+p.requirements,"Reserved: "+p.reservations,
                    "Last progress: "+p.lastProgressTick,"Retry tick: "+p.nextRetryTick+" attempts: "+p.attempts,
                    "Selected site: "+p.site,"Site rejections: "+p.siteRejections));
            detail.addAll(Diagnostics.lines(Diagnostics.tree(p,v.plans)));
            for(Task task:p.tasks)detail.add("Worker: "+task.worker+" task: "+task.id+" progress="+task.progress);
            return new Row(p.kind+" — "+p.state()+(p.blocker==null?"":" / "+p.blocker.type()),detail);
        }).toList());
        List<Row> events=new ArrayList<>();
        for(int i=v.history.size()-1;i>=Math.max(0,v.history.size()-200);i--) {
            Event e=v.history.get(i);events.add(new Row("Day "+(e.tick()/24000)+" — "+e.message(),
                    List.of(e.type(),"Actor: "+e.actor(),"Target: "+e.target(),"Event: "+e.id())));
        }
        tabs.put("Chronicle",events);
        if(admin)tabs.put("Diagnostics",plans.stream().map(p->new Row(p.id+" "+p.state(),Diagnostics.lines(Diagnostics.tree(p,v.plans)))).toList());
        return new LedgerView(v.id,v.name,tabs);
    }
    private static Row row(String text) { return new Row(text,List.of(text)); }
}
