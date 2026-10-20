package dev.livingvillages.resource;
import dev.livingvillages.domain.Model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.livingvillages.world.Positions.*;
public final class Inventories {
    private Inventories() {}
    public static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
    public static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    public static int count(Container inv,String id) {
        int n=0;for(int i=0;i<inv.getContainerSize();i++)if(id(inv.getItem(i)).equals(id))n+=inv.getItem(i).getCount();
        return n;
    }
    public static Map<String,Integer> stock(ServerLevel level,Village v) {
        Map<String,Integer> result=new TreeMap<>();
        for(Pos p:new LinkedHashSet<>(v.storage)) {
            if(!dev.livingvillages.world.Ownership.owns(level,v,p)) { v.storage.removeIf(p::equals);continue; }
            if(!level.hasChunkAt(block(p)))continue;
            Container container=container(level,p);
            if(container==null) { v.storage.remove(p);continue; }
            addStock(container,result);
        }
        return result;
    }
    public static void addStock(Container inv,Map<String,Integer> result) {
        for(int i=0;i<inv.getContainerSize();i++) {
            ItemStack stack=inv.getItem(i);
            if(!stack.isEmpty())result.merge(id(stack),stack.getCount(),Integer::sum);
        }
    }
    public static Container container(ServerLevel level,Pos p) {
        if(p==null||!level.hasChunkAt(block(p)))return null;
        var be=level.getBlockEntity(block(p));
        return be instanceof Container c ? c : null;
    }
    public static int insert(Container inv,ItemStack source) {
        int original=source.getCount();
        for(int i=0;i<inv.getContainerSize()&&!source.isEmpty();i++) {
            ItemStack slot=inv.getItem(i);
            if(!inv.canPlaceItem(i,source))continue;
            int max=Math.min(inv.getMaxStackSize(),source.getMaxStackSize());
            if(slot.isEmpty())inv.setItem(i,source.split(Math.min(max,source.getCount())));
            else if(ItemStack.isSameItemSameComponents(slot,source)) {
                int move=Math.min(Math.max(0,max-slot.getCount()),source.getCount());
                slot.grow(move);source.shrink(move);
            }
        }
        if(source.getCount()!=original)inv.setChanged();
        return original-source.getCount();
    }
    public static boolean canAccept(Container destination,Container cargo) {
        for(int i=0;i<cargo.getContainerSize();i++) {
            ItemStack item=cargo.getItem(i);if(item.isEmpty())continue;
            for(int j=0;j<destination.getContainerSize();j++) {
                ItemStack slot=destination.getItem(j);
                if(destination.canPlaceItem(j,item)&&(slot.isEmpty()||
                        ItemStack.isSameItemSameComponents(slot,item)&&slot.getCount()<Math.min(slot.getMaxStackSize(),destination.getMaxStackSize())))
                    return true;
            }
        }
        return false;
    }
    public static int transfer(Container from,Container to,String item,int maximum) {
        if(from==to)return 0;
        int moved=0;
        for(int i=0;i<from.getContainerSize()&&moved<maximum;i++) {
            ItemStack source=from.getItem(i);
            if(source.isEmpty()||(!item.isEmpty()&&!id(source).equals(item)))continue;
            ItemStack offered=source.copyWithCount(Math.min(source.getCount(),maximum-moved));
            int accepted=insert(to,offered);
            source.shrink(accepted); moved+=accepted;
        }
        if(moved>0)from.setChanged();
        return moved;
    }
    public static boolean consume(Container inv,Map<String,Integer> ingredients) {
        if(ingredients.entrySet().stream().anyMatch(e->count(inv,e.getKey())<e.getValue()))return false;
        ingredients.forEach((id,count)->{
            int left=count;
            for(int i=0;i<inv.getContainerSize()&&left>0;i++) {
                ItemStack s=inv.getItem(i);
                if(id(s).equals(id)) { int take=Math.min(left,s.getCount());s.shrink(take);left-=take; }
            }
        });
        inv.setChanged();return true;
    }
    public static void giveOrDrop(Villager worker,ItemStack stack) {
        insert(worker.getInventory(),stack);
        if(!stack.isEmpty())worker.spawnAtLocation(stack);
    }
}
