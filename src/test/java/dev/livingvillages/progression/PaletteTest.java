package dev.livingvillages.progression;
import dev.livingvillages.domain.*;
import dev.livingvillages.domain.Model.*;
import dev.livingvillages.domain.Palettes.Token;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaletteTest {
    @Test void everyVanillaWoodFamilyResolvesEveryToken() {
        assertEquals(List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"),Palettes.WOODS);
        for(String wood:Palettes.WOODS) {
            assertEquals("minecraft:"+wood+"_planks",Palettes.id(wood,Token.WOOD_PLANKS));
            assertEquals("minecraft:"+wood+"_log",Palettes.id(wood,Token.WOOD_LOG));
            assertEquals("minecraft:"+wood+"_stairs",Palettes.id(wood,Token.WOOD_STAIRS));
            assertEquals("minecraft:"+wood+"_slab",Palettes.id(wood,Token.WOOD_SLAB));
            assertEquals("minecraft:"+wood+"_door",Palettes.id(wood,Token.WOOD_DOOR));
            assertEquals("minecraft:"+wood+"_fence",Palettes.id(wood,Token.WOOD_FENCE));
            assertEquals("minecraft:"+wood+"_fence_gate",Palettes.id(wood,Token.WOOD_FENCE_GATE));
            for(Token token:Token.values())assertEquals(Optional.of(wood),Palettes.woodOf(Palettes.id(wood,token)));
        }
        assertEquals(Optional.of("dark_oak"),Palettes.woodOf("minecraft:stripped_dark_oak_log"));
        assertEquals(Optional.of("oak"),Palettes.woodOf("minecraft:oak_wood"));
        assertEquals(Optional.empty(),Palettes.woodOf("minecraft:crimson_stem"));
        assertEquals(Optional.empty(),Palettes.woodOf("minecraft:pale_oak_log"),"1.21.4 wood is not a 1.21.1 palette");
        assertEquals("oak",Palettes.normalize(null));assertEquals("oak",Palettes.normalize("pale_oak"));
        assertEquals(Map.of("minecraft:cherry_log",16,"minecraft:cobblestone",8),
                Palettes.resolve(Map.of("WOOD_LOG",16,"minecraft:cobblestone",8),"cherry"));
    }
    @Test void localEnvironmentSelectsPaletteDeterministically() {
        var spruce=Palettes.select(Map.of("minecraft:spruce_log",20,"minecraft:oak_log",4),Map.of(),"minecraft:plains");
        assertEquals("spruce",spruce.palette());
        assertEquals(40.0,spruce.scores().get("spruce"));assertEquals(8.5,spruce.scores().get("oak"),"Indexed logs ×2 plus the biome tie-break");
        assertEquals("birch",Palettes.select(Map.of("minecraft:birch_log",1),Map.of(),"minecraft:taiga").palette(),
                "One real log outweighs the biome: no plan waits for trees that do not exist");
        assertEquals("birch",Palettes.select(Map.of(),Map.of("minecraft:birch_log",10,"minecraft:oak_planks",8),null).palette(),"Stocked logs outweigh planks");
        assertEquals("acacia",Palettes.select(Map.of(),Map.of(),"minecraft:savanna_plateau").palette(),"Biome hint without wood");
        assertEquals("cherry",Palettes.select(Map.of(),Map.of(),"minecraft:cherry_grove").palette());
        assertEquals("dark_oak",Palettes.select(Map.of(),Map.of(),"minecraft:dark_forest").palette());
        assertEquals("mangrove",Palettes.select(Map.of(),Map.of(),"minecraft:mangrove_swamp").palette());
        assertEquals("spruce",Palettes.select(Map.of(),Map.of(),"minecraft:snowy_taiga").palette());
        assertEquals("jungle",Palettes.select(Map.of(),Map.of(),"minecraft:sparse_jungle").palette());
        var none=Palettes.select(Map.of(),Map.of(),"minecraft:desert");
        assertEquals("oak",none.palette());assertTrue(none.reason().contains("default"));
        var tie=Palettes.select(Map.of("minecraft:jungle_log",3,"minecraft:birch_log",3),Map.of(),null);
        assertEquals("birch",tie.palette(),"Tie broken by stable family order");
        assertEquals(tie,Palettes.select(Map.of("minecraft:birch_log",3,"minecraft:jungle_log",3),Map.of(),null),"Input order does not matter");
        assertEquals("mangrove",Palettes.material(Map.of("minecraft:mangrove_planks",4,"minecraft:oak_log",64),"oak"),"Planks before logs");
        assertEquals("spruce",Palettes.material(Map.of("minecraft:spruce_log",1),"oak"));
        assertEquals("cherry",Palettes.material(Map.of(),"cherry"));
    }
    @Test void selectedPaletteIsPersistedWithPlansAndBuildings() {
        World w=new World();Village v=new Village();v.center=new Pos(0,64,0);v.palette="spruce";v.paletteScores.put("spruce",40.0);
        w.villages.put(v.id,v);
        Plan plan=new Plan();plan.kind="BUILD_HOUSE";plan.blueprint="house_t1_02";plan.palette="spruce";plan.village=v.id;
        Task t=new Task();t.progress=40;plan.tasks.add(t);v.plans.put(plan.id,plan);
        Building b=new Building();b.id=UUID.randomUUID();b.blueprint="storage_t1";b.category="STORAGE";b.palette="birch";v.buildings.put(b.id,b);
        // The village later prefers another wood; persisted plans keep their own palette.
        Village copy=StateCodec.decode(StateCodec.encode(w)).villages.get(v.id);
        copy.palette="oak";
        assertEquals("spruce",copy.plans.get(plan.id).palette);assertEquals(40,copy.plans.get(plan.id).tasks.getFirst().progress);
        assertEquals("birch",copy.buildings.get(b.id).palette);
        assertEquals(40.0,copy.paletteScores.get("spruce"));
    }
}
