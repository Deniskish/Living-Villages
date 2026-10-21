package dev.livingvillages.progression;

import java.util.Map;

/** Groups the physical production record into progression categories. Primary goods only. */
public enum ProductionCategory {
    FOOD("Food harvested"), WOOD("Wood harvested"), STONE("Stone mined"), IRON("Iron smelted"),
    CHARCOAL("Charcoal burned"), DIAMOND("Diamonds mined");
    private final String label;
    ProductionCategory(String label) { this.label=label; }
    public String label() { return label; }
    public boolean matches(String id) {
        return switch(this) {
            // Harvested crops; bread is crafted from wheat and would count the same food twice.
            case FOOD -> id.equals("minecraft:wheat")||id.equals("minecraft:carrot")||id.equals("minecraft:potato")||id.equals("minecraft:beetroot");
            case WOOD -> id.endsWith("_log");
            case STONE -> id.equals("minecraft:cobblestone")||id.equals("minecraft:cobbled_deepslate");
            case IRON -> id.equals("minecraft:iron_ingot");
            case CHARCOAL -> id.equals("minecraft:charcoal");
            case DIAMOND -> id.equals("minecraft:diamond");
        };
    }
    public long total(Map<String,Long> production) {
        long n=0;for(var e:production.entrySet())if(matches(e.getKey()))n+=e.getValue();return n;
    }
}
