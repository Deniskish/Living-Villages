package dev.livingvillages.domain;

import java.util.*;

/**
 * Vanilla 1.21.1 wood families. Blueprints contain semantic tokens; a palette maps
 * each token to a concrete item/block id. Pure logic: no world access.
 */
public final class Palettes {
    private Palettes() {}
    public static final String DEFAULT="oak";
    /** Order is the deterministic tie-break. Pale oak (1.21.4+) and nether stems are excluded. */
    public static final List<String> WOODS=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry");
    private static final List<String> MATCH_ORDER=List.of("dark_oak","oak","spruce","birch","jungle","acacia","mangrove","cherry");
    public enum Token {
        WOOD_PLANKS("planks"), WOOD_LOG("log"), WOOD_STAIRS("stairs"), WOOD_SLAB("slab"),
        WOOD_DOOR("door"), WOOD_FENCE("fence"), WOOD_FENCE_GATE("fence_gate");
        private final String suffix;
        Token(String suffix) { this.suffix=suffix; }
    }
    /**
     * Weight of one indexed log block near the village relative to one stocked log. The biome
     * only breaks ties: choosing a wood with no reachable trees or stock would block gathering.
     */
    static final double OBSERVED_WEIGHT=2, PLANK_WEIGHT=0.25, BIOME_BONUS=0.5;
    public record Choice(String palette,Map<String,Double> scores,String reason) {
        public Choice { scores=Collections.unmodifiableMap(new LinkedHashMap<>(scores)); }
    }
    public static boolean known(String palette) { return palette!=null&&WOODS.contains(palette); }
    /** Unknown or missing palettes resolve as oak, the only v1.0 material family. */
    public static String normalize(String palette) { return known(palette)?palette:DEFAULT; }
    public static String id(String palette,Token token) { return "minecraft:"+normalize(palette)+"_"+token.suffix; }
    public static String planks(String palette) { return id(palette,Token.WOOD_PLANKS); }
    public static String log(String palette) { return id(palette,Token.WOOD_LOG); }
    /** A cost/requirement key is either a token name (WOOD_LOG) or a literal item id. */
    public static String resolve(String key,String palette) {
        for(Token token:Token.values())if(token.name().equals(key))return id(palette,token);
        return key;
    }
    public static Map<String,Integer> resolve(Map<String,Integer> costs,String palette) {
        Map<String,Integer> result=new TreeMap<>();
        costs.forEach((key,n)->result.merge(resolve(key,palette),n,Integer::sum));
        return result;
    }
    /** Wood family of an item or block id, e.g. minecraft:stripped_dark_oak_log -> dark_oak. */
    public static Optional<String> woodOf(String id) {
        if(id==null)return Optional.empty();
        String name=id.substring(id.indexOf(':')+1);
        if(name.startsWith("stripped_"))name=name.substring("stripped_".length());
        for(String wood:MATCH_ORDER)if(name.startsWith(wood+"_"))return Optional.of(wood);
        return Optional.empty();
    }
    /**
     * Wood family for generic wooden recipes (sticks, chests, tools): the most stocked
     * planks, else the most stocked logs, else the village palette. Deterministic.
     */
    public static String material(Map<String,Integer> stock,String fallback) {
        for(Token token:List.of(Token.WOOD_PLANKS,Token.WOOD_LOG)) {
            String best=null;int most=0;
            for(String wood:WOODS) { int n=stock.getOrDefault(id(wood,token),0);if(n>most) { best=wood;most=n; } }
            if(best!=null)return best;
        }
        return normalize(fallback);
    }
    /** Weak hint only: the observed environment and stock dominate the choice. */
    public static Optional<String> forBiome(String biomePath) {
        if(biomePath==null)return Optional.empty();
        String b=biomePath.substring(biomePath.indexOf(':')+1);
        if(b.contains("cherry"))return Optional.of("cherry");
        if(b.contains("mangrove"))return Optional.of("mangrove");
        if(b.contains("dark_forest"))return Optional.of("dark_oak");
        if(b.contains("birch"))return Optional.of("birch");
        if(b.contains("jungle"))return Optional.of("jungle");
        if(b.contains("savanna"))return Optional.of("acacia");
        if(b.contains("taiga")||b.contains("snowy")||b.contains("grove")||b.contains("spruce")||b.contains("pine"))return Optional.of("spruce");
        if(b.contains("plains")||b.contains("forest")||b.contains("meadow")||b.contains("swamp"))return Optional.of("oak");
        return Optional.empty();
    }
    /**
     * Deterministic local palette: indexed nearby log blocks, stocked wood and a biome hint.
     * @param observedLogs block id -> indexed count inside the village boundary
     * @param stock physical item id -> count in storage/cargo
     */
    public static Choice select(Map<String,Integer> observedLogs,Map<String,Integer> stock,String biomePath) {
        Map<String,Double> scores=new LinkedHashMap<>();
        for(String wood:WOODS)scores.put(wood,0.0);
        observedLogs.forEach((id,n)->woodOf(id).ifPresent(w->scores.merge(w,OBSERVED_WEIGHT*n,Double::sum)));
        stock.forEach((id,n)->woodOf(id).ifPresent(w->{
            if(id.endsWith("_log")||id.endsWith("_wood"))scores.merge(w,(double)n,Double::sum);
            else if(id.endsWith("_planks"))scores.merge(w,PLANK_WEIGHT*n,Double::sum);
        }));
        Optional<String> biome=forBiome(biomePath);
        biome.ifPresent(w->scores.merge(w,BIOME_BONUS,Double::sum));
        String best=DEFAULT;double bestScore=0;
        for(String wood:WOODS)if(scores.get(wood)>bestScore) { best=wood;bestScore=scores.get(wood); }
        String reason=bestScore<=0?"No local wood observed; default "+DEFAULT
                :best+" score "+String.format(Locale.ROOT,"%.1f",bestScore)+" from indexed logs, stock"
                +(biome.filter(best::equals).isPresent()?" and biome "+biomePath:"");
        scores.values().removeIf(v->v<=0);
        return new Choice(best,scores,reason);
    }
}
