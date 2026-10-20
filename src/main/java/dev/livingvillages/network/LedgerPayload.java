package dev.livingvillages.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LedgerPayload(String json) implements CustomPacketPayload {
    public static final Type<LedgerPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("livingvillages","ledger"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LedgerPayload> CODEC=StreamCodec.of(
            (buffer,value)->buffer.writeUtf(value.json,262144),buffer->new LedgerPayload(buffer.readUtf(262144)));
    @Override public Type<LedgerPayload> type() { return TYPE; }
}
