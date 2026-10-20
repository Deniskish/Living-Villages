package dev.livingvillages.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record RefreshPayload(UUID village) implements CustomPacketPayload {
    public static final Type<RefreshPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("livingvillages","refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RefreshPayload> CODEC=StreamCodec.of(
            (buffer,value)->buffer.writeUUID(value.village),buffer->new RefreshPayload(buffer.readUUID()));
    @Override public Type<RefreshPayload> type() { return TYPE; }
}
