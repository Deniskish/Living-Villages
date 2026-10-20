package dev.livingvillages.domain;

import java.util.*;

/** Persistent domain only: no world objects, client types or authoritative stock cache. */
public final class Model {
    private Model() {}
    public enum Role { LUMBERJACK, FARMER, MINER, ANIMAL_KEEPER, SMITH, BUILDER }
    public enum Need { FOOD, HOUSING, WOOD, STONE, METAL, TOOLS, STORAGE, WORKFORCE }
    public enum State {
        PLANNED, WAITING, ASSIGNED, IN_PROGRESS, BLOCKED, COMPLETED, FAILED, CANCELLED;
        public boolean terminal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
    }
    public enum Reason {
        MISSING_RESOURCE, MISSING_TOOL, NO_QUALIFIED_WORKER, WORKER_UNAVAILABLE,
        NO_VALID_SITE, PATH_UNREACHABLE, CHUNK_UNAVAILABLE, BUILD_SITE_OBSTRUCTED,
        DEPENDENCY_BLOCKED, TARGET_MISSING, STORAGE_FULL, STORAGE_MISSING,
        RETRY_LIMIT_REACHED, WORLD_CHANGED, UNKNOWN
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
            lastProgressTick=tick; attempts=0; nextRetryTick=0; blocker=null;
            transition(State.IN_PROGRESS);
        }
        public void block(Blocker why, long tick, int retryLimit) {
            blocker=why; attempts=Math.min(attempts+1,30);
            nextRetryTick=tick+Rules.backoff(attempts);
            transition(attempts >= retryLimit ? State.BLOCKED : State.WAITING);
        }
        public void complete(long tick) { blocker=null; progress(tick); transition(State.COMPLETED); }
    }
    public static final class Task extends Work {
        public UUID plan, worker;
        public Role role;
        public String phase="SELECT", item="";
        public String desiredResource="";
        public Pos target;
        public Pos workOrigin;
        public long actionStarted;
        public int scanCursor;
        public List<Pos> targets = new ArrayList<>();
    }
    public static final class Plan extends Work {
        public UUID village;
        public Need need;
        public String kind, blueprint;
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
        public Map<UUID,Plan> plans = new LinkedHashMap<>();
        public Map<UUID,Building> buildings = new LinkedHashMap<>();
        public List<Pos> storage = new ArrayList<>();
        public List<Event> history = new ArrayList<>();
        public Map<Need,Double> needs = new EnumMap<>(Need.class);
        public List<Goal> goals = new ArrayList<>();
        public int beds;
        public boolean contains(Pos p) { return center.distanceSquared(p) <= (long)radius*radius; }
        public Optional<Plan> active(String kind) {
            return plans.values().stream().filter(p->p.kind.equals(kind)&&!p.state().terminal()).findFirst();
        }
        public long population() { return citizens.values().stream().filter(c->!c.dead).count(); }
    }
    public static final class World {
        public int version=1;
        public Map<UUID,Village> villages = new LinkedHashMap<>();
        public transient List<String> recoveryNotes=new ArrayList<>();
    }
}
