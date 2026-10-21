package dev.livingvillages.network;
import com.google.gson.Gson;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.progression.*;
import dev.livingvillages.world.ProgressionService;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
public record LedgerView(UUID village,String name,Map<String,List<Row>> tabs) {
    /** @param key stable identity (UUID, requirement key, technology id) used to keep a selection across Refresh */
    public record Row(String title,List<String> detail,String key) {
        public Row(String title,List<String> detail) { this(title,detail,""); }
    }
    public static final List<String> TABS=List.of("Overview","Citizens","Resources","Buildings","Plans","Progression","Chronicle","Diagnostics");
    public static final int MAX_ROWS=200, MAX_DETAIL=32, MAX_TEXT=512;
    private static final Gson JSON=new Gson();
    public LedgerView {
        Objects.requireNonNull(village,"Village identity");
        name=bounded(name,160);
        Map<String,List<Row>> safe=new LinkedHashMap<>();
        for(String tab:TABS) {
            if(tab.equals("Diagnostics")&&(tabs==null||!tabs.containsKey(tab)))continue;
            List<Row> rows=tabs==null?List.of():tabs.getOrDefault(tab,List.of());
            List<Row> limited=new ArrayList<>();
            if(rows!=null)for(Row row:rows.stream().filter(Objects::nonNull).limit(MAX_ROWS).toList())
                limited.add(new Row(bounded(row.title,MAX_TEXT),row.detail==null?List.of():
                        row.detail.stream().limit(MAX_DETAIL).map(s->bounded(s,MAX_TEXT)).toList(),bounded(row.key,64)));
            if(rows!=null&&rows.size()>MAX_ROWS)limited.add(new Row("Showing first "+MAX_ROWS+" rows",List.of("Full records remain saved.")));
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
        Map<String,Integer> stock=Inventories.stock(level,v);
        var development=ProgressionService.inspect(level,v,stock);
        tabs.put("Overview",new ArrayList<>(List.of(
                new Row("Tier: "+v.tier.label()+(development.nextTier()==null?"":" | next: "+development.nextTier().label()),
                        List.of(development.explanation()),"tier"),
                row("Population: "+v.population()+" | known citizens: "+v.citizens.size()),
                row("Known beds: "+v.beds+" | buildings: "+v.buildings.size()),
                row("Active plans: "+active+" | blocked: "+blocked),
                row("Boundary: "+v.radius+" blocks | center: "+v.center),
                row("Local palette: "+v.palette+" "+v.paletteScores),
                row("Food in storage: "+stock.entrySet().stream()
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
            return new Row(c.name+" — "+c.role+" "+c.level()+(c.dead?" [deceased]":""),details,c.id.toString());
        }).toList());
        List<Row> resources=new ArrayList<>();
        stock.forEach((id,n)->resources.add(row(id+" = "+n)));
        if(!v.production.isEmpty())resources.add(new Row("Produced by village work: "+v.production.size()+" item types",
                v.production.entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).toList(),"production"));
        v.storage.forEach(pos->resources.add(row("Container: "+pos)));
        for(Citizen c:v.citizens.values())if(!c.dead&&level.getEntity(c.id) instanceof net.minecraft.world.entity.npc.Villager worker) {
            Map<String,Integer> carried=new TreeMap<>();Inventories.addStock(worker.getInventory(),carried);
            if(!carried.isEmpty())resources.add(new Row("Carried by "+c.name,List.of(carried.toString())));
        }
        tabs.put("Resources",resources);
        tabs.put("Buildings",v.buildings.values().stream().limit(MAX_ROWS).map(b->new Row(b.blueprint+" ("+b.palette+") — "+b.condition,
                List.of("ID: "+b.id,"Category: "+b.category,"Palette: "+b.palette,"Origin: "+b.origin,"Entrance: "+b.entrance,
                        "Completed: "+b.completedAt),b.id.toString())).toList());
        List<Plan> plans=java.util.stream.Stream.concat(v.activePlans().stream(),
                v.plans.reversed().values().stream().filter(p->p.state().terminal())).limit(150).toList();
        tabs.put("Plans",plans.stream().map(p-> {
            List<String> detail=new ArrayList<>(List.of("ID: "+p.id,"Priority: "+p.priority,
                    "Progress: "+p.progress+(p.totalWork>0?" / "+p.totalWork:"")+" blocks/actions","Requirements: "+p.requirements,"Reserved: "+p.reservations,
                    "Last progress: "+p.lastProgressTick,"Retry tick: "+p.nextRetryTick+" attempts: "+p.attempts,
                    "Selected site: "+p.site,"Site rejections: "+p.siteRejections));
            if(p.palette!=null)detail.add("Palette: "+p.palette);
            if(p.technology!=null)detail.add("Technology: "+p.technology);
            detail.addAll(Diagnostics.lines(Diagnostics.tree(p,v.plans)));
            for(Task task:p.tasks)detail.add("Worker: "+task.worker+" task: "+task.id+" progress="+task.progress);
            return new Row(p.kind+" — "+p.state()+(p.blocker==null?"":" / "+p.blocker.type()),detail,p.id.toString());
        }).toList());
        List<Row> events=new ArrayList<>();
        for(int i=v.history.size()-1;i>=Math.max(0,v.history.size()-200);i--) {
            Event e=v.history.get(i);events.add(new Row("Day "+(e.tick()/24000)+" — "+e.message(),
                    List.of(e.type(),"Actor: "+e.actor(),"Target: "+e.target(),"Event: "+e.id())));
        }
        tabs.put("Progression",progression(v,development,stock));
        tabs.put("Chronicle",events);
        if(admin) {
            List<Row> diagnostics=new ArrayList<>();
            diagnostics.add(new Row("Progression: "+(development.nextTier()==null?"highest tier":"next "+development.nextTier().label()),
                    Diagnostics.lines(ProgressionDiagnostics.tier(development)),"diagnostic:tier"));
            plans.stream().map(p->new Row(p.id+" "+p.state(),Diagnostics.lines(Diagnostics.tree(p,v.plans)),"diagnostic:"+p.id)).forEach(diagnostics::add);
            tabs.put("Diagnostics",diagnostics);
        }
        return new LedgerView(v.id,v.name,tabs);
    }
    private static Row row(String text) { return new Row(text,List.of(text)); }
    /** Progression tab: tier, requirements, capabilities and technologies, all with text markers. */
    static List<Row> progression(Village v,VillageProgressionSnapshot s,Map<String,Integer> stock) {
        List<Row> rows=new ArrayList<>();
        rows.add(new Row("Village Tier: "+v.tier.label(),List.of("Highest tier ever reached. It is never lost to temporary shortages;",
                "individual activities still require their physical infrastructure.",
                "Reached: "+v.tierHistory.entrySet().stream().map(e->e.getKey()+" day "+e.getValue()/24000).toList()),"tier"));
        rows.add(new Row("Highest unlocked: "+v.tier.numeral()+" | currently qualifies: "+(s.currentTier()==null?"none (Tier I unmet)":s.currentTier().label()),
                List.of(s.explanation(),"Evaluated at tick "+s.evaluatedAt()),"qualified"));
        rows.add(new Row(s.nextTier()==null?"Next: none — "+v.tier.label()+" is the highest tier":"Next: "+s.nextTier().label(),
                List.of(s.explanation()),"next"));
        if(s.nextTier()!=null) {
            rows.add(new Row("== Requirements for "+s.nextTier().label()+" ("+s.fulfilledRequirements().size()+"/"+s.requirements().size()+" met)",
                    List.of("Every requirement is measured from the physical world or the permanent record.","[✓] met, [✗] missing."),"requirements"));
            for(Requirement r:s.requirements())rows.add(new Row("  "+r.line(),List.of(r.line(),"Type: "+r.type(),"Subject: "+(r.subject().isEmpty()?"—":r.subject()),
                    "Required: "+r.required(),"Actual: "+r.actual(),r.fulfilled()?"Status: met":"Status: missing — "+r.message()),"requirement:"+r.key()));
        }
        rows.add(new Row("== Capabilities ([✓] available, [!] unlocked but infrastructure missing, [✗] locked)",
                List.of("Planner and workers ask these capabilities instead of the tier number."),"capabilities"));
        for(CapabilityStatus c:s.capabilities()) {
            List<String> detail=new ArrayList<>(List.of(c.line(),c.capability().description(),
                    "Unlocks at: Tier "+c.capability().tier().label()+(c.capability().technology()==null?"":" + "+c.capability().technology())));
            c.requirements().forEach(r->detail.add(r.line()));
            if(!c.available())detail.add("Reason: "+c.reason()+" — "+CapabilityPolicy.explain(c));
            rows.add(new Row("  "+c.line(),detail,"capability:"+c.capability()));
        }
        rows.add(new Row("== Technologies ([✓] unlocked, [~] researching, [ ] ready, [✗] locked)",
                List.of("Research consumes real items at a real workstation; nothing unlocks for free."),"technologies"));
        for(TechnologyBranch branch:TechnologyBranch.values()) {
            rows.add(new Row(branch.title(),List.of("Branch "+branch.name()),"branch:"+branch));
            for(TechnologyStatus t:s.technologies()) {
                if(t.technology().branch()!=branch)continue;
                var tech=t.technology();
                List<String> detail=new ArrayList<>(List.of(t.line(),"ID: "+tech.id(),"Effect: "+tech.effect(),
                        "Unlocks: "+tech.unlocks(),"Workstation: "+tech.workstation().label()+" | work: "+tech.workUnits()+" × "+Technology.UNIT_TICKS+" ticks",
                        "Start requirements:"));
                t.requirements().forEach(r->detail.add("  "+r.line()));
                detail.add("Physical cost ("+Palettes.normalize(v.palette)+" palette):");
                TechnologyPolicy.costs(tech,v.palette,stock).forEach(r->detail.add("  "+r.line()));
                var record=v.technologies.get(tech.id());
                if(record!=null)detail.add("Unlocked day "+record.unlockedAt()/24000+" by "+Optional.ofNullable(v.citizens.get(record.researcher())).map(c->c.name).orElse(String.valueOf(record.researcher())));
                v.active(tech.planKind()).ifPresent(p->detail.add("Plan "+p.id+" "+p.state()+" "+p.progress+"/"+tech.workUnits()
                        +(p.blocker==null?"":" — "+p.blocker.type()+": "+p.blocker.message())));
                rows.add(new Row("  "+t.line(),detail,"technology:"+tech.id()));
            }
        }
        return rows;
    }
}
