package dev.livingvillages.domain;

import java.util.*;
import static dev.livingvillages.domain.Model.*;

public final class Rules {
    private Rules() {}
    private static final long[] XP = {0,64,256,768,2048};
    public static int level(long xp) {
        int level=1;
        for(int i=1;i<XP.length;i++) if(xp>=XP[i]) level=i+1;
        return level;
    }
    public static double shortage(int available,int desired) {
        return desired<=0 ? 0 : Math.clamp(1.0-(double)available/desired,0,1);
    }
    public static Goal goal(Need need,double urgency,double feasibility,double cooldown) {
        double u=Math.clamp(urgency,0,1), f=Math.clamp(feasibility,0,1), c=Math.clamp(cooldown,0,1);
        return new Goal(need,u,f,c,u*f*c,"urgency × feasibility × cooldown");
    }
    public static long backoff(int attempts) { return Math.min(6000L,100L << Math.min(Math.max(attempts-1,0),6)); }
    public static int miningLevel(String id) {
        if (id.endsWith("stone") || id.endsWith("coal_ore")) return 1;
        if (id.endsWith("copper_ore")) return 2;
        if (id.endsWith("iron_ore")) return 3;
        if (id.endsWith("gold_ore")||id.endsWith("redstone_ore")||id.endsWith("lapis_ore")) return 4;
        return Integer.MAX_VALUE; // Diamond explicitly awaits village progression.
    }
    public static Map<String,Integer> reserve(Map<String,Integer> stock,Map<String,Integer> claimed,
                                               Map<String,Integer> demand) {
        Map<String,Integer> result=new TreeMap<>();
        demand.forEach((item,n)->result.put(item,Math.min(Math.max(n,0),
                Math.max(0,stock.getOrDefault(item,0)-claimed.getOrDefault(item,0)))));
        return result;
    }
    public static String siteRejection(boolean boundary, boolean collision, boolean water,
                                       int heightVariance, boolean support, boolean entrance) {
        if(!boundary)return "BOUNDARY";
        if(collision)return "COLLISION";
        if(water)return "WATER";
        if(heightVariance>0)return "SLOPE";
        if(!support)return "SUPPORT";
        if(!entrance)return "ENTRANCE";
        return "";
    }
    public static double siteScore(long centerDistanceSquared, long pathDistanceSquared) {
        return 1.0/(1+Math.sqrt(centerDistanceSquared)+0.5*Math.sqrt(pathDistanceSquared));
    }
}
