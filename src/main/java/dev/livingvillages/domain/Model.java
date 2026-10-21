package dev.livingvillages.domain;

import java.util.*;

/** Persistent domain only: no world objects, client types or authoritative stock cache. */
public final class Model {
    private Model() {}
    public enum Role { LUMBERJACK, FARMER, MINER, ANIMAL_KEEPER, SMITH, BUILDER }
    public enum Need { FOOD, HOUSING, WOOD, STONE, METAL, TOOLS, STORAGE, WORKFORCE, DEVELOPMENT }
    /** Settlement development stage. Persisted by name; the order is the progression order. */
    public enum Tier {
        SETTLEMENT("Settlement"), VILLAGE("Village"), TOWN("Town"), CITY("City"), CAPITAL("Capital");
        private final String title;
        Tier(String title) { this.title=title; }
        public String title() { return title; }
        public int number() { return ordinal()+1; }
        public String numeral() { return Rules.numeral(number()); }
        public String label() { return numeral()+" — "+title; }
        public Optional<Tier> next() { return ordinal()+1<values().length?Optional.of(values()[ordinal()+1]):Optional.empty(); }
        public boolean atLeast(Tier other) { return compareTo(other)>=0; }
    }
    public enum State {
        PLANNED, WAITING, ASSIGNED, IN_PROGRESS, BLOCKED, COMPLETED, FAILED, CANCELLED;
        public boolean terminal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
    }
    public enum Reason {
        MISSING_RESOURCE, MISSING_TOOL, NO_QUALIFIED_WORKER, WORKER_UNAVAILABLE,
        NO_VALID_SITE, PATH_UNREACHABLE, CHUNK_UNAVAILABLE, BUILD_SITE_OBSTRUCTED,
        DEPENDENCY_BLOCKED, TARGET_MISSING, STORAGE_FULL, STORAGE_MISSING,
        RETRY_LIMIT_REACHED, WORLD_CHANGED, UNKNOWN,
        /** Village tier or technology does not yet permit the activity. */
        CAPABILITY_LOCKED,
        /** Capability is unlocked but required physical infrastructure is absent. */
        INFRASTRUCTURE_MISSING,
        /** A progression or technology requirement is not met by the current world state. */
        REQUIREMENT_UNMET
    }
    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x+dx,y+dy,z+dz); }
        public long distanceSquared(Pos other) {
            long dx=x-other.x,dy=y-other.y,dz=z-other.z; return dx*dx+dy*dy+dz*dz;
        }
    }
    public record Blocker(Reason type, String message, long createdAt, long lastCheckedAt,
                          UUID planId, UUID taskId, UUID citizenId, Pos position,
                          String resource, int required, int available, List<UUID> dependencies) {
        public Blocker {
            Objects.requireNonNull(type);
            if (message == null || message.isBlank()) throw new IllegalArgumentException("Blocker needs explanation");
            dependencies = List.copyOf(dependencies);
        }
    }
    public record Event(UUID id, long tick, String type, UUID village, UUID actor,
                        UUID target, String message, Map<String,String> parameters) {}
    public record Goal(Need need, double urgency, double feasibility, double cooldown,
                       double utility, String explanation) {}
    /** Permanent record of an unlocked technology; never removed by later world changes. */
    public record TechnologyRecord(String technology, long unlockedAt, UUID plan, UUID researcher) {}

    public static final class Citizen {
        public UUID id, village, currentTask;
        public String name;
        public Role role;
        public Map<Role,Long> experience = new EnumMap<>(Role.class);
        public Map<String,Long> skills = new TreeMap<>();
        public Map<String,Long> statistics = new TreeMap<>();
        public List<UUID> history = new ArrayList<>();
        public Pos home;
        public String equipment = "", activity = "IDLE";
        public long registeredAt;
        public int ageTicks;
        public boolean dead;
        public int level() { return Rules.level(experience.getOrDefault(role,0L)); }
    }
    public static class Work {
        public UUID id = UUID.randomUUID();
        private State state = State.PLANNED;
        public Blocker blocker;
        public long createdAt, lastProgressTick, nextRetryTick, attemptStartedAt;
        public int attempts, progress;
        public List<UUID> dependencies = new ArrayList<>();
        public State state() { return state; }
        public void transition(State next) {
            if (state.terminal() && state != next) throw new IllegalStateException("Terminal work " + id);
            if (next == State.BLOCKED && blocker == null) throw new IllegalStateException("Missing blocker");
            if (next == State.COMPLETED && blocker != null) throw new IllegalStateException("Blocked completion");
            if(next==State.PLANNED&&state!=State.PLANNED)throw new IllegalStateException("Cannot reset work history");
            state = next;
        }
        public void progress(long tick) {
            if(state.terminal())throw new IllegalStateException("Terminal work "+id);
            lastProgressTick=tick; attempts=0; nextRetryTick=0; blocker=null;
            transition(State.IN_PROGRESS);
        }
        public void block(Blocker why, long tick, int retryLimit) {
            if(state.terminal())throw new IllegalStateException("Terminal work "+id);
            blocker=Objects.requireNonNull(why); attempts=Math.min(attempts+1,30);
            nextRetryTick=tick+Rules.backoff(attempts);
            transition(attempts >= retryLimit ? State.BLOCKED : State.WAITING);
        }
        public void complete(long tick) {
            if(state==State.COMPLETED)return;
            progress(tick); transition(State.COMPLETED);
        }
    }
    public static final class Task extends Work {
        public UUID plan, worker, lastAssignedWorker;
        public Role role;
        public String phase="SELECT", item="";
        public String desiredResource="";
        public Pos target;
        public Pos workOrigin;
        public long actionStarted;
        public int scanCursor;
        /** Minimum role level required in addition to built-in item rules (research, diamonds). */
        public int minimumLevel;
        public List<Pos> targets = new ArrayList<>();
    }
    public static final class Plan extends Work {
        public UUID village;
        public Need need;
        public String kind, blueprint;
        /** Material palette selected once for construction/recipes; persisted for resume. */
        public String palette;
        /** Technology researched by a RESEARCH plan. */
        public String technology;
        public Goal goal;
        public double priority;
        public int totalWork;
        public Pos site;
        public List<Task> tasks = new ArrayList<>();
        public Map<String,Integer> requirements = new TreeMap<>();
        public Map<String,Integer> reservations = new TreeMap<>();
        public Map<String,Integer> siteRejections = new TreeMap<>();
    }
    public static final class Building {
        public UUID id, village, plan;
        public String blueprint, category, condition="COMPLETE";
        /** Palette the building was constructed with; inspection compares against it. */
        public String palette=Palettes.DEFAULT;
        public Pos origin, entrance;
        public long completedAt;
    }
    public static final class Village {
        public UUID id = UUID.randomUUID();
        public String name;
        public Pos center;
        public int radius=64;
        public long createdAt, lastPlanTick;
        public Map<UUID,Citizen> citizens = new LinkedHashMap<>();
        public LinkedHashMap<UUID,Plan> plans = new LinkedHashMap<>();
        public Map<UUID,Building> buildings = new LinkedHashMap<>();
        public List<Pos> storage = new ArrayList<>();
        public List<Event> history = new ArrayList<>();
        public Map<Need,Double> needs = new EnumMap<>(Need.class);
        public List<Goal> goals = new ArrayList<>();
        /** Highest tier ever reached. It is historical and never decreases. */
        public Tier tier = Tier.SETTLEMENT;
        /** Tier name -> game tick when it was first reached. Guards against duplicate milestones. */
        public Map<String,Long> tierHistory = new TreeMap<>();
        /** Item id -> amount physically produced by village work (loot, smelting, crafting). */
        public Map<String,Long> production = new TreeMap<>();
        /** Technology id -> permanent unlock record. */
        public Map<String,TechnologyRecord> technologies = new TreeMap<>();
        /** Most recent local palette choice and its candidate scores (diagnostic). */
        public String palette = Palettes.DEFAULT;
        public Map<String,Double> paletteScores = new TreeMap<>();
        /** Last progression evaluation; rebuilt by the strategic tick and on demand. */
        public transient dev.livingvillages.progression.VillageProgressionSnapshot progression;
        private transient Map<UUID,Plan> liveIndex=new LinkedHashMap<>();
        private transient Map<String,Plan> latestKind=new HashMap<>();
        private transient int indexedPlanCount=-1;
        public transient long scanSteps,strategicSteps,workerSteps,workerNanos,maxWorkerNanos;
        public Collection<Plan> activePlans() {
            if(indexedPlanCount<0||indexedPlanCount>plans.size()) {
                liveIndex=new LinkedHashMap<>();latestKind=new HashMap<>();
                plans.forEach((id,p)->{
                    if(!p.state().terminal())liveIndex.put(id,p);
                    latestKind.put(p.kind,p);
                });
                indexedPlanCount=plans.size();
            } else if(indexedPlanCount<plans.size()) {
                // Production plans are append-only. Historical plans never re-enter the hot queue.
                List<Plan> added=new ArrayList<>(plans.reversed().values().stream().limit(plans.size()-indexedPlanCount).toList());
                Collections.reverse(added);
                for(Plan p:added) { if(!p.state().terminal())liveIndex.put(p.id,p);latestKind.put(p.kind,p); }
                indexedPlanCount=plans.size();
            }
            liveIndex.values().removeIf(p->p.state().terminal());
            return List.copyOf(liveIndex.values());
        }
        public Optional<Plan> latestPlan(String kind) { activePlans();return Optional.ofNullable(latestKind.get(kind)); }
        public int beds;
        public boolean contains(Pos p) { return center.distanceSquared(p) <= (long)radius*radius; }
        public Optional<Plan> active(String kind) {
            return activePlans().stream().filter(p->p.kind.equals(kind)).findFirst();
        }
        public long population() { return citizens.values().stream().filter(c->!c.dead).count(); }
    }
    public static final class World {
        public int version=StateCodec.VERSION;
        public Map<UUID,Village> villages = new LinkedHashMap<>();
        public transient List<String> recoveryNotes=new ArrayList<>();
        public transient List<String> migrationNotes=new ArrayList<>();
    }
}
