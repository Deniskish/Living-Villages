package dev.livingvillages.building;
import com.google.gson.*;
import dev.livingvillages.domain.Model.Pos;
import dev.livingvillages.domain.Palettes;
import dev.livingvillages.domain.Palettes.Token;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Templates hold semantic symbols. Wood symbols are palette tokens resolved per plan:
 * P planks, L log, D door, | fence, G fence gate, S bottom slab. Fixed symbols:
 * # cobblestone, B white bed, C chest, T crafting table, F furnace. v1.0 templates are
 * unchanged files and resolve to their original blocks with the oak palette.
 */
public final class Blueprints {
    public record Cell(Pos offset,BlockState state,String item,BlockState partner,Pos partnerOffset) {}
    public record Blueprint(String id,String category,int width,int depth,int height,List<Cell> cells,Pos entrance,
                            String palette,String capability) {
        public Map<String,Integer> manifest() {
            Map<String,Integer> map=new TreeMap<>();
            for(Cell c:cells)map.merge(c.item,1,Integer::sum);return map;
        }
    }
    private record Template(String id,String category,int width,int depth,int height,List<Pos> offsets,List<Character> symbols,
                            Pos entrance,String capability) {}
    public static final List<String> IDS=List.of("house_t1_01","house_t1_02","house_t1_03","house_t2_01","storage_t1","animal_pen_t1","smithy_t1");
    private final Map<String,Template> templates=new LinkedHashMap<>();
    private final Map<String,Blueprint> resolved=new HashMap<>();
    public Blueprints() {
        for(String id:IDS) {
            String file="/data/livingvillages/blueprints/"+id+".json";
            try(InputStream stream=Blueprints.class.getResourceAsStream(file)) {
                if(stream==null)throw new IllegalStateException("Missing blueprint "+file);
                JsonObject data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                List<Pos> offsets=new ArrayList<>();List<Character> symbols=new ArrayList<>();
                int width=0,depth=0,y=0;Pos entrance=null;
                for(JsonElement layer:data.getAsJsonArray("layers")) {
                    int z=0;
                    for(JsonElement row:layer.getAsJsonArray()) {
                        String line=row.getAsString();width=Math.max(width,line.length());
                        for(int x=0;x<line.length();x++) {
                            char symbol=line.charAt(x);
                            if(symbol=='.'||symbol==' ')continue;
                            if(!known(symbol))throw new IllegalStateException(file+": unknown symbol '"+symbol+"'");
                            offsets.add(new Pos(x,y,z));symbols.add(symbol);
                            if(entrance==null&&(symbol=='D'||symbol=='G'))entrance=new Pos(x,1,z-1);
                        }
                        z++;
                    }
                    depth=Math.max(depth,z);y++;
                }
                String capability=data.has("capability")?data.get("capability").getAsString():null;
                if(capability!=null)dev.livingvillages.progression.VillageCapability.valueOf(capability); // Fail at load, not mid-planning.
                templates.put(id,new Template(id,data.get("category").getAsString(),width,depth,y,List.copyOf(offsets),
                        List.copyOf(symbols),entrance==null?new Pos(2,1,-1):entrance,capability));
            } catch(IOException ex) { throw new IllegalStateException(file,ex); }
        }
    }
    /** Oak resolution: identical to the v1.0 oak-only templates. */
    public Blueprint get(String id) { return get(id,Palettes.DEFAULT); }
    public Blueprint get(String id,String palette) {
        String wood=Palettes.normalize(palette);
        return resolved.computeIfAbsent(id+"/"+wood,key->resolve(Objects.requireNonNull(templates.get(id),"Unknown blueprint "+id),wood));
    }
    /** Every template resolved with the default palette. */
    public Collection<Blueprint> all() { return templates.keySet().stream().map(this::get).toList(); }
    public boolean exists(String id) { return templates.containsKey(id); }
    private static Blueprint resolve(Template t,String palette) {
        List<Cell> cells=new ArrayList<>();
        for(int i=0;i<t.offsets().size();i++)cells.add(cell(t.symbols().get(i),t.offsets().get(i),palette));
        cells.sort(Comparator.comparingInt(c->c.offset.y()));
        return new Blueprint(t.id(),t.category(),t.width(),t.depth(),t.height(),List.copyOf(cells),t.entrance(),palette,t.capability());
    }
    public static boolean matches(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        for(var property:List.of(BlockStateProperties.HORIZONTAL_FACING,BedBlock.PART,DoorBlock.HALF,BlockStateProperties.AXIS,BlockStateProperties.SLAB_TYPE))
            if(expected.hasProperty(property)&&!actual.getValue(property).equals(expected.getValue(property)))return false;
        return true;
    }
    private static boolean known(char symbol) { return "#PLBDCTF|GS".indexOf(symbol)>=0; }
    /** Resolves a palette token to its 1.21.1 block; a missing block is a packaging error, not air. */
    public static Block wood(String palette,Token token) {
        String id=Palettes.id(palette,token);
        Block block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if(block==Blocks.AIR)throw new IllegalStateException("Palette block missing from registry: "+id);
        return block;
    }
    private static Cell cell(char symbol,Pos p,String palette) {
        Block block=switch(symbol) {
            case '#' -> Blocks.COBBLESTONE;case 'B' -> Blocks.WHITE_BED;case 'C' -> Blocks.CHEST;
            case 'T' -> Blocks.CRAFTING_TABLE;case 'F' -> Blocks.FURNACE;
            case 'P' -> wood(palette,Token.WOOD_PLANKS);case 'L' -> wood(palette,Token.WOOD_LOG);
            case 'D' -> wood(palette,Token.WOOD_DOOR);case '|' -> wood(palette,Token.WOOD_FENCE);
            case 'G' -> wood(palette,Token.WOOD_FENCE_GATE);case 'S' -> wood(palette,Token.WOOD_SLAB);
            default -> throw new IllegalStateException("Unknown blueprint symbol "+symbol);
        };
        BlockState state=block.defaultBlockState(),partner=null;Pos other=null;
        if(block instanceof BedBlock) {
            state=state.setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.FOOT);
            partner=state.setValue(BedBlock.PART,BedPart.HEAD);other=p.offset(0,0,1);
        } else if(block instanceof DoorBlock) {
            state=state.setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
            partner=state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER);other=p.offset(0,1,0);
        } else if(block instanceof SlabBlock) state=state.setValue(SlabBlock.TYPE,SlabType.BOTTOM);
        String item=BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
        return new Cell(p,state,item,partner,other);
    }
}
