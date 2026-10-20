package dev.livingvillages.network;
import dev.livingvillages.persistence.VillageSavedData;
import dev.livingvillages.domain.Model.Village;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.function.Consumer;
import static dev.livingvillages.world.Positions.*;
public final class LedgerNetwork {
    // Installed only by the client entrypoint. No client class is resolved on a server.
    private static Consumer<String> receiver=json->{};
    private LedgerNetwork() {}
    public static void clientReceiver(Consumer<String> callback) { receiver=callback; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToClient(LedgerPayload.TYPE,LedgerPayload.CODEC,(payload,context)->context.enqueueWork(()->receiver.accept(payload.json())));
        registrar.playToServer(RefreshPayload.TYPE,RefreshPayload.CODEC,LedgerNetwork::refresh);
    }
    private static void refresh(RefreshPayload payload,IPayloadContext context) {
        context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;
            long now=player.serverLevel().getGameTime();
            long last=player.getPersistentData().getLong("livingvillages:lastRefresh");
            if(now-last<20)return;
            player.getPersistentData().putLong("livingvillages:lastRefresh",now);
            Village village=VillageSavedData.get(player.serverLevel()).world.villages.get(payload.village());
            if(village==null||player.distanceToSqr(village.center.x(),village.center.y(),village.center.z())>144
                    ||!player.serverLevel().hasChunkAt(block(village.center))
                    ||!player.serverLevel().getBlockState(block(village.center)).is(Blocks.BELL)
                    ||(!player.getMainHandItem().is(Items.BOOK)&&!player.getOffhandItem().is(Items.BOOK)))return;
            send(player,village);
        });
    }
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(Items.BOOK)
                ||!event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL))return;
        var village=VillageSavedData.get(player.serverLevel()).world.villages.values().stream()
                .filter(v->v.contains(of(event.getPos()))).findFirst().orElse(null);
        if(village!=null) { send(player,village);event.setCanceled(true); }
    }
    private static void send(ServerPlayer player,Village village) {
        String json=LedgerView.create(player.serverLevel(),village,player.hasPermissions(2)).json();
        if(json.length()<=262144)PacketDistributor.sendToPlayer(player,new LedgerPayload(json));
        else player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Ledger snapshot too large; use inspect commands."));
    }
}
