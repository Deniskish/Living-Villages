package dev.livingvillages.ai;
import dev.livingvillages.building.Blueprints;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.history.Chronicle;
import dev.livingvillages.progression.*;
import dev.livingvillages.resource.Inventories;
import dev.livingvillages.task.ProductionJob;
import dev.livingvillages.world.Observations;
import dev.livingvillages.world.ProgressionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import static dev.livingvillages.world.Positions.*;
import java.util.*;

public final class VillagePlanner {
    private final Chronicle chronicle;
    private final Blueprints blueprints;
    private final dev.livingvillages.building.BuildingInspection inspection;
    private final ProgressionService progression;
    public VillagePlanner(Chronicle chronicle,Blueprints blueprints,ProgressionService progression) {
        this.chronicle=chronicle;this.blueprints=blueprints;this.progression=progression;
        inspection=new dev.livingvillages.building.BuildingInspection(blueprints,chronicle);
    }
    public void tick(ServerLevel level,Village v,Observations observed) {
        long now=level.getGameTime();
        inspection.inspect(level,v);
        Map<String,Integer> stock=Inventories.stock(level,v);
        for(Citizen citizen:v.citizens.values())if(!citizen.dead&&level.getEntity(citizen.id) instanceof Villager e)
            Inventories.addStock(e.getInventory(),stock);
        // Local material palette: indexed logs, stocked wood and a biome hint (bounded inputs).
        var palette=Palettes.select(observed.woodCounts(),stock,
                level.getBiome(block(v.center)).unwrapKey().map(k->k.location().toString()).orElse(null));
        v.palette=palette.palette();v.paletteScores=new TreeMap<>(palette.scores());
        VillageProgressionSnapshot development=progression.evaluate(level,v,observed,stock);
        observed.extraDepth=development.capability(VillageCapability.ADVANCED_MINING).available()?VillageCapability.ADVANCED_MINING_DEPTH:0;
        int population=(int)v.population();
        Set<String> researching=ProgressionService.researching(v);
        Optional<Technology> nextTechnology=development.capability(VillageCapability.TECHNOLOGY_RESEARCH).available()
                ?development.technologies().stream().filter(TechnologyPolicy::startable).map(TechnologyStatus::technology)
                        .filter(t->v.latestPlan(t.planKind()).map(p->p.state()!=State.FAILED).orElse(true)).findFirst()
                :Optional.empty();
        v.needs.put(Need.FOOD,Rules.shortage(Rules.foodUnits(stock),population*12));
        v.needs.put(Need.HOUSING,Rules.shortage(v.beds,population+2));
        v.needs.put(Need.WOOD,Rules.shortage(Rules.logs(stock),32));
        v.needs.put(Need.STONE,Rules.shortage(stock.getOrDefault("minecraft:cobblestone",0),128));
        v.needs.put(Need.METAL,Rules.shortage(stock.getOrDefault("minecraft:iron_ingot",0),6));
        v.needs.put(Need.TOOLS,Rules.shortage(stock.getOrDefault("minecraft:wooden_pickaxe",0)+stock.getOrDefault("minecraft:stone_pickaxe",0),1));
        boolean storageBlocked=v.activePlans().stream().anyMatch(p->p.blocker!=null&&p.blocker.type()==Reason.STORAGE_FULL);
        v.needs.put(Need.STORAGE,v.storage.isEmpty()||storageBlocked?1.0:0.0);
        v.needs.put(Need.WORKFORCE,Rules.shortage(population,6));
        v.needs.put(Need.DEVELOPMENT,nextTechnology.isPresent()||!researching.isEmpty()?0.5:0.0);
        v.goals.clear();
        v.needs.forEach((need,urgency)->{
            double feasibility=population==0?0:switch(need) {
                case WOOD -> observed.logs.isEmpty()?0.2:1;
                case STONE -> observed.stone.isEmpty()?0.2:1;
                case FOOD -> observed.crops.isEmpty()?0.3:1;
                case HOUSING -> v.citizens.values().stream().anyMatch(c->!c.dead&&c.role==Role.BUILDER)?0.9:0.6;
                case METAL -> v.citizens.values().stream().anyMatch(c->!c.dead&&Rules.level(c.experience.getOrDefault(Role.MINER,0L))>=3)?0.9:0.1;
                case TOOLS -> observed.tables.isEmpty()?0.5:1;
                case DEVELOPMENT -> development.capability(VillageCapability.TECHNOLOGY_RESEARCH).available()?1:0;
                default -> 1;
            };
            double cooldown=v.activePlans().stream().anyMatch(p->p.need==need)?0.7:1;
            v.goals.add(Rules.goal(need,urgency,feasibility,cooldown));
        });
        v.goals.sort(Comparator.comparingDouble(Goal::utility).reversed());
        // Bootstrap has higher utility than discretionary expansion.
        if(v.storage.isEmpty()||storageBlocked)ensure(level,v,"PLACE:minecraft:chest",Need.STORAGE,Role.BUILDER,2,"minecraft:chest");
        if(observed.tables.isEmpty())ensure(level,v,"PLACE:minecraft:crafting_table",Need.TOOLS,Role.BUILDER,1.9,"minecraft:crafting_table");
        if(observed.furnaces.isEmpty()&&v.active("SMELT_IRON").isPresent()) {
            Plan furnace=ensure(level,v,"PLACE:minecraft:furnace",Need.METAL,Role.BUILDER,1.8,"minecraft:furnace");
            if(furnace!=null)v.active("SMELT_IRON").ifPresent(p->{if(!p.dependencies.contains(furnace.id))p.dependencies.add(furnace.id);});
        }
        if(!observed.tables.isEmpty()&&Rules.logs(stock)>=4) {
            for(Citizen citizen:v.citizens.values())if(!citizen.dead&&citizen.role==Role.LUMBERJACK
                    &&level.getEntity(citizen.id) instanceof Villager worker
                    &&!(worker.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem)) {
                if(stock.getOrDefault("minecraft:wooden_axe",0)==0)
                    acquire(level,v,"minecraft:wooden_axe",0.4,stock,new HashSet<>(),0);
                break;
            }
        }
        if(v.beds>population&&(Rules.foodUnits(stock)>=6||stock.getOrDefault("minecraft:wheat",0)>=6)) {
            Plan feeding=ensure(level,v,"FEED_CITIZENS",Need.WORKFORCE,Role.FARMER,0.7,"");
            if(feeding!=null)feeding.requirements=new TreeMap<>(Map.of("minecraft:bread",6));
        }
        for(Goal goal:v.goals) {
            if(goal.utility()<=0)continue;
            switch(goal.need()) {
                case WOOD -> { if(allowed(v,VillageCapability.BASIC_FORESTRY))ensure(level,v,"GATHER_WOOD",Need.WOOD,Role.LUMBERJACK,goal.utility(),""); }
                case FOOD -> {
                    if(!allowed(v,VillageCapability.BASIC_FARMING))break;
                    ensure(level,v,"FARM",Need.FOOD,Role.FARMER,goal.utility(),"");
                    if(stock.getOrDefault("minecraft:wheat",0)>=3)
                        acquire(level,v,"minecraft:bread",goal.utility()+0.01,stock,new HashSet<>(),0);
                }
                case STONE -> { if(allowed(v,VillageCapability.BASIC_MINING))ensure(level,v,"MINE",Need.STONE,Role.MINER,goal.utility(),""); }
                case METAL -> {
                    if(allowed(v,VillageCapability.IRON_SMELTING)&&v.citizens.values().stream().anyMatch(c->Rules.level(c.experience.getOrDefault(Role.MINER,0L))>=3))
                        acquire(level,v,"minecraft:iron_ingot",goal.utility(),stock,new HashSet<>(),0);
                }
                case HOUSING -> {
                    if(allowed(v,VillageCapability.BASIC_CONSTRUCTION)&&now-v.createdAt>1200&&v.active("BUILD_HOUSE").isEmpty()) {
                        Plan p=ensure(level,v,"BUILD_HOUSE",Need.HOUSING,Role.BUILDER,goal.utility(),"");
                        if(p!=null&&p.blueprint==null) {
                            p.blueprint=house(v);p.palette=v.palette;
                            p.requirements.putAll(blueprints.get(p.blueprint,p.palette).manifest());
                        }
                    }
                }
                case DEVELOPMENT -> nextTechnology.ifPresent(t->research(level,v,t,goal.utility()));
                default -> { }
            }
        }
        // Required infrastructure is useful once the initial economy can function.
        if(now-v.createdAt>2400&&!v.storage.isEmpty()&&!observed.tables.isEmpty()
                &&v.buildings.values().stream().anyMatch(b->b.category.equals("HOUSING"))&&allowed(v,VillageCapability.BASIC_CONSTRUCTION)) {
            infrastructure(level,v,"storage_t1",Need.STORAGE);
            infrastructure(level,v,"smithy_t1",Need.TOOLS);
            infrastructure(level,v,"animal_pen_t1",Need.FOOD);
            if(allowed(v,VillageCapability.ANIMAL_HUSBANDRY))ensure(level,v,"BREED_ANIMALS",Need.FOOD,Role.ANIMAL_KEEPER,0.2,"");
        }
        // Diamond mining exists only when the capability and an indexed exposed ore exist.
        if(development.capability(VillageCapability.DIAMOND_MINING).available()&&!observed.diamonds.isEmpty()) {
            Plan diamonds=ensure(level,v,"MINE:"+dev.livingvillages.task.HarvestJob.DIAMOND,Need.METAL,Role.MINER,0.12,"");
            if(diamonds!=null) { diamonds.tasks.getFirst().desiredResource=dev.livingvillages.task.HarvestJob.DIAMOND;diamonds.tasks.getFirst().minimumLevel=5; }
        }
        for(Plan p:v.activePlans()) {
            if(p.kind.startsWith("CRAFT:")) {
                var recipe=ProductionJob.recipe(p.tasks.getFirst().item,Palettes.planks(p.palette));
                if(recipe!=null)p.requirements=new TreeMap<>(recipe.ingredients());
            }
            if(p.kind.startsWith("PLACE:"))p.requirements= new TreeMap<>(Map.of(p.tasks.getFirst().item,1));
            if(p.kind.startsWith("MINE")) {
                boolean diamond=p.tasks.getFirst().desiredResource.equals(dev.livingvillages.task.HarvestJob.DIAMOND);
                boolean tool=v.citizens.values().stream().anyMatch(c->level.getEntity(c.id) instanceof Villager worker
                        &&worker.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem
                        &&(!diamond||diamondPickaxe(Inventories.id(worker.getMainHandItem()))));
                p.requirements=tool?new TreeMap<>():new TreeMap<>(Map.of(diamond?"minecraft:iron_pickaxe":"minecraft:wooden_pickaxe",1));
            }
            if(p.blueprint!=null) {
                var bp=blueprints.get(p.blueprint,p.palette);
                p.totalWork=bp.cells().size();
                p.requirements.clear();
                for(int i=p.tasks.getFirst().progress;i<bp.cells().size();i++)p.requirements.merge(bp.cells().get(i).item(),1,Integer::sum);
            }
            if(p.technology!=null)workstation(level,v,p,observed);
            // Resource edges represent outstanding acquisition, not a permanent completion barrier.
            p.dependencies.removeIf(id->v.plans.containsKey(id)&&v.plans.get(id).state().terminal());
            for(var requirement:p.requirements.entrySet()) {
                if(stock.getOrDefault(requirement.getKey(),0)>=requirement.getValue())continue;
                Plan dependency=acquire(level,v,requirement.getKey(),p.priority+0.01,stock,new HashSet<>(),0);
                if(dependency!=null&&dependency!=p&&!p.dependencies.contains(dependency.id))p.dependencies.add(dependency.id);
            }
            if(p.blocker!=null&&p.blocker.type()==Reason.MISSING_RESOURCE&&!p.blocker.resource().isBlank()) {
                Plan dependency=acquire(level,v,p.blocker.resource(),p.priority+0.01,stock,new HashSet<>(),0);
                if(dependency!=null&&dependency!=p&&!p.dependencies.contains(dependency.id))p.dependencies.add(dependency.id);
            }
        }
        for(Building building:v.buildings.values()) {
            String kind="ROAD:"+building.id;
            if(v.latestPlan(kind).isPresent())continue;
            Pos target=observed.paths.stream().filter(p->p.y()==building.origin.y()-1)
                    .min(Comparator.comparingLong(p->p.distanceSquared(building.entrance))).orElse(null);
            if(target==null||target.distanceSquared(building.entrance)>144)continue;
            Plan road=ensure(level,v,kind,Need.HOUSING,Role.BUILDER,0.05,"");
            if(road!=null) {
                Pos cursor=building.entrance.offset(0,-2,0);
                for(int n=0;n<16&&!cursor.equals(target);n++) {
                    road.tasks.getFirst().targets.add(cursor);
                    cursor=cursor.offset(Integer.compare(target.x(),cursor.x()),0,
                            target.x()==cursor.x()?Integer.compare(target.z(),cursor.z()):0);
                }
                road.requirements=new TreeMap<>(Map.of("minecraft:wooden_shovel",1));
            }
        }
        // Deterministic priority order; claims are rebuilt against physical stock each strategic tick.
        Map<String,Integer> claimed=new TreeMap<>();
        v.activePlans().stream().sorted(Comparator.comparingDouble((Plan p)->p.priority).reversed())
                .forEach(p->{p.reservations=Rules.reserve(stock,claimed,p.requirements);p.reservations.forEach((k,n)->claimed.merge(k,n,Integer::sum));});
        v.lastPlanTick=now;
    }
    /** Capability gate for planning: unlocked by tier/technology; infrastructure is planned as dependencies. */
    private static boolean allowed(Village v,VillageCapability capability) {
        return v.progression==null||v.progression.capability(capability).unlocked();
    }
    private static boolean diamondPickaxe(String id) {
        return id.equals("minecraft:iron_pickaxe")||id.equals("minecraft:diamond_pickaxe")||id.equals("minecraft:netherite_pickaxe");
    }
    /** Deterministic house variant; ADVANCED_CONSTRUCTION alternates the three-bed timber house. */
    private String house(Village v) {
        int built=(int)v.buildings.values().stream().filter(b->b.category.equals("HOUSING")).count();
        List<String> basic=Blueprints.IDS.stream().filter(id->id.startsWith("house_")&&blueprints.get(id).capability()==null).toList();
        List<String> advanced=Blueprints.IDS.stream().filter(id->id.startsWith("house_")&&blueprints.get(id).capability()!=null)
                .filter(id->v.progression!=null&&v.progression.capability(VillageCapability.valueOf(blueprints.get(id).capability())).available()).toList();
        if(!advanced.isEmpty()&&built%2==0)return advanced.get((built/2)%advanced.size());
        return basic.get(built%basic.size());
    }
    private void research(ServerLevel level,Village v,Technology tech,double utility) {
        if(!ProgressionService.researching(v).isEmpty())return;
        Plan p=ensure(level,v,tech.planKind(),Need.DEVELOPMENT,tech.role(),Math.min(0.3,utility),"");
        if(p!=null&&p.technology==null) {
            p.technology=tech.id();p.palette=v.palette;p.totalWork=tech.workUnits();
            p.requirements=new TreeMap<>(Palettes.resolve(tech.costs(),p.palette));
            p.tasks.getFirst().minimumLevel=tech.roleLevel();
        }
    }
    /** A research plan depends on placing its missing workstation, like SMELT_IRON on a furnace. */
    private void workstation(ServerLevel level,Village v,Plan p,Observations observed) {
        var tech=Technologies.get(p.technology).orElse(null);
        if(tech==null)return;
        String item=switch(tech.workstation()) {
            case FURNACE -> observed.furnaces.isEmpty()?"minecraft:furnace":null;
            case CRAFTING_TABLE -> observed.tables.isEmpty()?"minecraft:crafting_table":null;
            default -> null;
        };
        if(item==null)return;
        Plan station=ensure(level,v,"PLACE:"+item,Need.TOOLS,Role.BUILDER,Math.max(1.8,p.priority+0.01),item);
        if(station!=null&&!p.dependencies.contains(station.id))p.dependencies.add(station.id);
    }
    private void infrastructure(ServerLevel level,Village v,String blueprint,Need need) {
        if(v.buildings.values().stream().anyMatch(b->b.blueprint.equals(blueprint)))return;
        Plan p=ensure(level,v,"BUILD:"+blueprint,need,Role.BUILDER,0.15,"");
        if(p!=null&&p.blueprint==null) { p.blueprint=blueprint;p.palette=v.palette;p.requirements.putAll(blueprints.get(blueprint,p.palette).manifest()); }
    }
    private Plan acquire(ServerLevel level,Village v,String item,double priority,Map<String,Integer> stock,Set<String> visiting,int depth) {
        if(depth>6||!visiting.add(item))return null;
        if(item.endsWith("_log")) {
            // A specific wood family is gathered only from matching trees.
            Plan wood=ensure(level,v,"GATHER_WOOD:"+item,Need.WOOD,Role.LUMBERJACK,priority,"");
            if(wood!=null)wood.tasks.getFirst().desiredResource=item;
            return wood;
        }
        if(item.equals("minecraft:cobblestone"))return ensure(level,v,"MINE",Need.STONE,Role.MINER,priority,"");
        if(item.equals("minecraft:wheat"))return ensure(level,v,"FARM",Need.FOOD,Role.FARMER,priority,"");
        if(item.equals("minecraft:white_wool")) {
            Plan wool=ensure(level,v,"SHEAR",Need.HOUSING,Role.ANIMAL_KEEPER,priority,"");
            if(wool!=null)wool.requirements=new TreeMap<>(Map.of("minecraft:shears",1));
            return wool;
        }
        if(item.equals("minecraft:iron_ingot")) {
            // Charcoal replaces mined coal only after the village learned charcoal burning.
            boolean charcoal=stock.getOrDefault("minecraft:coal",0)==0&&v.progression!=null
                    &&v.progression.capability(VillageCapability.CHARCOAL_BURNING).available();
            String fuel=charcoal?"minecraft:charcoal":"minecraft:coal";
            Plan iron=ensure(level,v,"SMELT_IRON",Need.METAL,Role.SMITH,priority,"");
            if(iron!=null) {
                // The fuel is chosen once per plan so a resumed plan never switches material mid-fetch.
                if(iron.tasks.getFirst().desiredResource.isEmpty())iron.tasks.getFirst().desiredResource=fuel;
                iron.requirements=new TreeMap<>(Map.of("minecraft:raw_iron",1,iron.tasks.getFirst().desiredResource,1));
            }
            return iron;
        }
        if(item.equals("minecraft:charcoal")) {
            if(v.progression==null||!v.progression.capability(VillageCapability.CHARCOAL_BURNING).available())return null;
            Plan burn=ensure(level,v,"SMELT_CHARCOAL",Need.METAL,Role.SMITH,priority,"");
            if(burn!=null&&burn.palette==null) {
                burn.palette=Palettes.material(stock,v.palette);burn.tasks.getFirst().item=Palettes.log(burn.palette);
                burn.requirements=new TreeMap<>(Map.of(burn.tasks.getFirst().item,2));
            }
            return burn;
        }
        if(item.equals("minecraft:raw_iron")||item.equals("minecraft:coal")) {
            Plan mining=ensure(level,v,"MINE:"+item,Need.METAL,Role.MINER,priority,"");
            if(mining!=null)mining.tasks.getFirst().desiredResource=item;return mining;
        }
        if(ProductionJob.recipe(item)==null)return null;
        Plan p=ensure(level,v,"CRAFT:"+item,Need.TOOLS,Role.SMITH,priority,item);
        if(p!=null) {
            if(p.palette==null)p.palette=Palettes.woodOf(item).orElse(Palettes.material(stock,v.palette));
            var recipe=ProductionJob.recipe(item,Palettes.planks(p.palette));
            p.requirements=new TreeMap<>(recipe.ingredients());
            for(var ingredient:recipe.ingredients().entrySet())if(stock.getOrDefault(ingredient.getKey(),0)<ingredient.getValue()) {
                Plan child=acquire(level,v,ingredient.getKey(),priority+0.01,stock,new HashSet<>(visiting),depth+1);
                if(child!=null&&child!=p&&!p.dependencies.contains(child.id))p.dependencies.add(child.id);
            }
            // An unlocked iron-tool order whose smithy is missing depends on building one.
            if(ProductionJob.ironTool(item)&&v.progression!=null) {
                var smithing=v.progression.capability(VillageCapability.IRON_TOOLSMITHING);
                if(smithing.unlocked()&&!smithing.available()&&allowed(v,VillageCapability.BASIC_CONSTRUCTION)) {
                    infrastructure(level,v,"smithy_t1",Need.TOOLS);
                    v.active("BUILD:smithy_t1").ifPresent(smithy->{if(!p.dependencies.contains(smithy.id))p.dependencies.add(smithy.id);});
                }
            }
        }
        return p;
    }
    public Plan ensure(ServerLevel level,Village v,String kind,Need need,Role role,double priority,String item) {
        Plan existing=v.active(kind).orElse(null);
        if(existing!=null) { existing.priority=Math.max(existing.priority,priority);return existing; }
        Plan latest=v.latestPlan(kind).orElse(null);
        if(latest!=null&&latest.state()==State.FAILED)return null;
        long cooldown=kind.startsWith("CRAFT:")||kind.startsWith("GATHER_WOOD")||kind.equals("MINE")?40:1200;
        if(latest!=null&&latest.state().terminal()&&level.getGameTime()-latest.lastProgressTick<cooldown)return null;
        if(v.activePlans().size()>=VillageConfig.ACTIVE_PLANS.get())return null;
        Plan p=new Plan();p.village=v.id;p.kind=kind;p.need=need;p.priority=priority;p.createdAt=level.getGameTime();
        p.lastProgressTick=p.createdAt;p.goal=v.goals.stream().filter(g->g.need()==need).findFirst()
                .orElse(Rules.goal(need,v.needs.getOrDefault(need,1.0),1,1));
        Task t=new Task();t.plan=p.id;t.role=role;t.item=item;t.createdAt=p.createdAt;t.lastProgressTick=p.createdAt;p.tasks.add(t);
        v.plans.put(p.id,p);chronicle.emit(v,p.createdAt,"PLAN_CREATED",null,p.id,kind+" utility="+priority);
        return p;
    }
}
