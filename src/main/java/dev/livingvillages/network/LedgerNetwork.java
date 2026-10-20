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
import java.util.function.BiConsumer;
import static dev.livingvillages.world.Positions.*;
public final class LedgerNetwork {
    // Installed only by the client entrypoint. No client class is resolved on a server.
    private static BiConsumer<String,Boolean> receiver=(json,refresh)->{};
    private LedgerNetwork() {}
    public static void clientReceiver(BiConsumer<String,Boolean> callback) { receiver=callback; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("2");
        registrar.playToClient(LedgerPayload.TYPE,LedgerPayload.CODEC,(payload,context)->context.enqueueWork(()->receiver.accept(payload.json(),payload.refresh())));
        registrar.playToServer(RefreshPayload.TYPE,RefreshPayload.CODEC,LedgerNetwork::refresh);
    }
    private static void refresh(RefreshPayload payload,IPayloadContext context) {
        context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;
            long now=player.serverLevel().getGameTime();
            long last=player.getPersistentData().getLong("livingvillages:lastRefresh");
            if(now-last<20)return;
            player.getPersistentData().putLong("livingvillages:lastRefresh",now);
            Village village=accessible(player,payload.village());
            if(village==null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Village Ledger unavailable: remain near its bell holding a Book."));
                return;
            }
            send(player,village,true);
        });
    }
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(Items.BOOK)
                ||!event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL))return;
        var village=dev.livingvillages.world.Ownership.at(
                VillageSavedData.get(player.serverLevel()).world.villages.values(),of(event.getPos())).orElse(null);
        if(village!=null) { send(player,village,false);event.setCanceled(true); }
    }
    public static Village accessible(ServerPlayer player,java.util.UUID id) {
        Village village=VillageSavedData.get(player.serverLevel()).world.villages.get(id);
        if(village==null||(!player.getMainHandItem().is(Items.BOOK)&&!player.getOffhandItem().is(Items.BOOK)))return null;
        // The interaction may have used a secondary bell, not the original center.
        return player.serverLevel().getPoiManager().getInRange(t->t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING),
                player.blockPosition(),12,net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .filter(p->player.serverLevel().hasChunkAt(p.getPos())&&player.serverLevel().getBlockState(p.getPos()).is(Blocks.BELL))
                .anyMatch(p->dev.livingvillages.world.Ownership.at(
                        VillageSavedData.get(player.serverLevel()).world.villages.values(),of(p.getPos()))
                        .filter(owner->owner.id.equals(id)).isPresent())?village:null;
    }
    private static void send(ServerPlayer player,Village village,boolean refresh) {
        String json=LedgerView.create(player.serverLevel(),village,player.hasPermissions(2)).json();
        if(json.length()<=262144)PacketDistributor.sendToPlayer(player,new LedgerPayload(json,refresh));
        else player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Ledger snapshot too large; use inspect commands."));
    }
}
