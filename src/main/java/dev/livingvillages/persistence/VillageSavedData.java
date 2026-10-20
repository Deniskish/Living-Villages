package dev.livingvillages.persistence;
import dev.livingvillages.domain.Model;
import dev.livingvillages.domain.StateCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
public final class VillageSavedData extends SavedData {
    public Model.World world=new Model.World();
    public static VillageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(()->createNew(net.minecraft.world.level.dimension.DimensionType.getStorageFolder(
                        level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT))
                        .resolve("data/livingvillages.dat")),VillageSavedData::load,null),"livingvillages");
    }
    public static VillageSavedData createNew(java.nio.file.Path existingFile) {
        // DimensionDataStorage catches deserializer exceptions and returns null.
        // Its fallback constructor must never replace an unreadable existing save.
        if(java.nio.file.Files.exists(existingFile))
            throw new IllegalStateException("Refusing to replace unreadable Living Villages save: "+existingFile);
        return new VillageSavedData();
    }
    public static VillageSavedData load(CompoundTag tag,HolderLookup.Provider registries) {
        VillageSavedData data=new VillageSavedData();
        try {
            String json=tag.contains("state",net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)
                    ?new String(tag.getByteArray("state"),java.nio.charset.StandardCharsets.UTF_8):tag.getString("state");
            data.world=StateCodec.decode(json);
            for(String note:data.world.recoveryNotes)
                dev.livingvillages.LivingVillagesMod.LOG.warn("event=corrupted_reference recovery={}",note);
        }
        catch(RuntimeException ex) {
            dev.livingvillages.LivingVillagesMod.LOG.error("event=serialization_failure dimension_save=livingvillages",ex);
            throw ex;
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("dataVersion",1);
        // NBT strings use modified UTF with a 65535-byte limit. Chronicles easily exceed it.
        tag.putByteArray("state",StateCodec.encode(world).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return tag;
    }
}
