package dev.livingvillages.world;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.domain.Model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
/** Incremental loaded-chunk index. Executors revalidate every action. */
public final class Observations {
    public final Set<Pos> logs=new LinkedHashSet<>(), crops=new LinkedHashSet<>(), stone=new LinkedHashSet<>(),
            beds=new LinkedHashSet<>(), tables=new LinkedHashSet<>(), paths=new LinkedHashSet<>(), furnaces=new LinkedHashSet<>();
    private int cursor;
    public boolean ready(Village v) { int width=v.radius*2+1;return cursor>=width*width*3; }
    public void scan(ServerLevel level,Village v) {
        int r=v.radius, width=2*r+1, height=17, total=width*width*height;
        for(int n=0;n<VillageConfig.SCAN_BUDGET.get();n++) {
            int i=cursor++%total;
            int layer=i/(width*width),dy=layer==0?0:(layer%2==1?(layer+1)/2:-layer/2);
            Pos p=v.center.offset(i%width-r,dy,(i/width)%width-r);
            BlockPos b=block(p);
            if(!level.hasChunkAt(b))continue;
            var state=level.getBlockState(b);
            update(logs,p,state.is(BlockTags.LOGS));
            update(crops,p,state.getBlock() instanceof CropBlock);
            update(stone,p,state.is(Blocks.STONE)||state.is(BlockTags.COAL_ORES)||state.is(BlockTags.COPPER_ORES)
                    ||state.is(BlockTags.IRON_ORES)||state.is(BlockTags.GOLD_ORES)
                    ||state.is(BlockTags.REDSTONE_ORES)||state.is(BlockTags.LAPIS_ORES));
            update(beds,p,state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART)==BedPart.HEAD
                    &&level.getBlockState(b.above()).isAir()&&level.getBlockState(b.above(2)).isAir());
            update(tables,p,state.is(Blocks.CRAFTING_TABLE));
            update(furnaces,p,state.is(Blocks.FURNACE));
            update(paths,p,state.is(Blocks.DIRT_PATH));
            boolean storage=state.is(Blocks.CHEST)||state.is(Blocks.BARREL);
            if(storage && level.getBlockEntity(b) instanceof Container) {
                if(!v.storage.contains(p))v.storage.add(p);
            } else v.storage.remove(p);
        }
        v.beds=beds.size();
    }
    private static void update(Set<Pos> set,Pos p,boolean exists) { if(exists)set.add(p);else set.remove(p); }
}
