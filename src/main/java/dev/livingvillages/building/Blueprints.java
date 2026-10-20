package dev.livingvillages.building;
import com.google.gson.*;
import dev.livingvillages.domain.Model.Pos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Blueprints {
    public record Cell(Pos offset,BlockState state,String item,BlockState partner,Pos partnerOffset) {}
    public record Blueprint(String id,String category,int width,int depth,List<Cell> cells,Pos entrance) {
        public Map<String,Integer> manifest() {
            Map<String,Integer> map=new TreeMap<>();
            for(Cell c:cells)map.merge(c.item,1,Integer::sum);return map;
        }
    }
    private final Map<String,Blueprint> blueprints=new LinkedHashMap<>();
    public Blueprints() {
        for(String id:List.of("house_t1_01","house_t1_02","house_t1_03","storage_t1","animal_pen_t1","smithy_t1")) {
            String file="/data/livingvillages/blueprints/"+id+".json";
            try(InputStream stream=Blueprints.class.getResourceAsStream(file)) {
                if(stream==null)throw new IllegalStateException("Missing blueprint "+file);
                JsonObject data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                List<Cell> cells=new ArrayList<>();int width=0,depth=0,y=0;
                for(JsonElement layer:data.getAsJsonArray("layers")) {
                    int z=0;
                    for(JsonElement row:layer.getAsJsonArray()) {
                        String line=row.getAsString();width=Math.max(width,line.length());
                        for(int x=0;x<line.length();x++) {
                            Cell cell=cell(line.charAt(x),new Pos(x,y,z));
                            if(cell!=null)cells.add(cell);
                        }
                        z++;
                    }
                    depth=Math.max(depth,z);y++;
                }
                cells.sort(Comparator.comparingInt(c->c.offset.y()));
                blueprints.put(id,new Blueprint(id,data.get("category").getAsString(),width,depth,List.copyOf(cells),new Pos(2,1,-1)));
            } catch(IOException ex) { throw new IllegalStateException(file,ex); }
        }
    }
    public Blueprint get(String id) { return Objects.requireNonNull(blueprints.get(id),"Unknown blueprint "+id); }
    public Collection<Blueprint> all() { return blueprints.values(); }
    public static boolean matches(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        for(var property:List.of(BlockStateProperties.HORIZONTAL_FACING,BedBlock.PART,DoorBlock.HALF,BlockStateProperties.AXIS))
            if(expected.hasProperty(property)&&!actual.getValue(property).equals(expected.getValue(property)))return false;
        return true;
    }
    private static Cell cell(char symbol,Pos p) {
        Block block=switch(symbol) {
            case '#' -> Blocks.COBBLESTONE;case 'P' -> Blocks.OAK_PLANKS;case 'L' -> Blocks.OAK_LOG;
            case 'B' -> Blocks.WHITE_BED;case 'D' -> Blocks.OAK_DOOR;case 'C' -> Blocks.CHEST;
            case 'T' -> Blocks.CRAFTING_TABLE;case 'F' -> Blocks.FURNACE;case '|' -> Blocks.OAK_FENCE;
            case 'G' -> Blocks.OAK_FENCE_GATE;default -> null;
        };
        if(block==null)return null;
        BlockState state=block.defaultBlockState(),partner=null;Pos other=null;
        if(block instanceof BedBlock) {
            state=state.setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.FOOT);
            partner=state.setValue(BedBlock.PART,BedPart.HEAD);other=p.offset(0,0,1);
        } else if(block instanceof DoorBlock) {
            state=state.setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
            partner=state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER);other=p.offset(0,1,0);
        }
        String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
        return new Cell(p,state,item,partner,other);
    }
}
