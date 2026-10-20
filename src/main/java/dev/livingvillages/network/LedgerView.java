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
    public LedgerView {
        Objects.requireNonNull(village,"Village identity");
        name=bounded(name,160);
        Map<String,List<Row>> safe=new LinkedHashMap<>();
        for(String tab:List.of("Overview","Citizens","Resources","Buildings","Plans","Chronicle","Diagnostics")) {
            if(tab.equals("Diagnostics")&&(tabs==null||!tabs.containsKey(tab)))continue;
            List<Row> rows=tabs==null?List.of():tabs.getOrDefault(tab,List.of());
            List<Row> limited=new ArrayList<>();
            if(rows!=null)for(Row row:rows.stream().filter(Objects::nonNull).limit(200).toList())
                limited.add(new Row(bounded(row.title,512),row.detail==null?List.of():
                        row.detail.stream().limit(32).map(s->bounded(s,512)).toList()));
            if(rows!=null&&rows.size()>200)limited.add(new Row("Showing first 200 rows",List.of("Full records remain saved.")));
            safe.put(tab,List.copyOf(limited));
        }
        tabs=Collections.unmodifiableMap(safe);
    }
    private static String bounded(String value,int max) {
        if(value==null)return "";
        return value.length()<=max?value:value.substring(0,max-1)+"…";
    }
    public String json() {
        Map<String,List<Row>> bounded=new LinkedHashMap<>();
        tabs.forEach((key,rows)->bounded.put(key,new ArrayList<>(rows)));
        String json=JSON.toJson(new LedgerView(village,name,bounded));
        while(json.length()>240000) {
            var largest=bounded.entrySet().stream().filter(e->!e.getValue().isEmpty())
                    .max(Comparator.comparingInt(e->e.getValue().stream().mapToInt(r->r.title.length()+
                            r.detail.stream().mapToInt(String::length).sum()).sum())).orElseThrow();
            int remove=Math.max(1,largest.getValue().size()/2);
            for(int i=0;i<remove;i++)largest.getValue().removeLast();
            bounded.get("Overview").removeIf(r->r.title.equals("Snapshot shortened to fit network limits"));
            bounded.get("Overview").add(new Row("Snapshot shortened to fit network limits",List.of("Full records remain saved; use inspect for a specific plan.")));
            json=JSON.toJson(new LedgerView(village,name,bounded));
        }
        return json;
    }
    public static LedgerView parse(String json) {
        LedgerView result=JSON.fromJson(json,LedgerView.class);
        return Objects.requireNonNull(result,"Empty Ledger snapshot");
    }
    public static LedgerView create(ServerLevel level,Village v,boolean admin) {
        Map<String,List<Row>> tabs=new LinkedHashMap<>();
        long active=v.activePlans().size();
        long blocked=v.activePlans().stream().filter(p->p.state()==State.BLOCKED).count();
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
        Map<UUID,List<Event>> personal=new HashMap<>();
        for(int i=v.history.size()-1;i>=Math.max(0,v.history.size()-4096);i--) {
            Event e=v.history.get(i);
            if(e.actor()!=null) {
                List<Event> recent=personal.computeIfAbsent(e.actor(),id->new ArrayList<>());
                if(recent.size()<12)recent.add(e);
            }
        }
        tabs.put("Citizens",v.citizens.values().stream().limit(200).map(c-> {
            List<String> details=new ArrayList<>(List.of("ID: "+c.id,"XP: "+c.experience,"Skills: "+c.skills,
                "Task: "+c.currentTask,"Activity: "+c.activity,"Home: "+c.home,"Equipment: "+c.equipment,
                "Lifetime: "+c.statistics,"Registered: "+c.registeredAt,"Events: "+c.history.size()));
            personal.getOrDefault(c.id,List.of()).stream()
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
        List<Plan> plans=java.util.stream.Stream.concat(v.activePlans().stream(),
                v.plans.reversed().values().stream().filter(p->p.state().terminal())).limit(150).toList();
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
