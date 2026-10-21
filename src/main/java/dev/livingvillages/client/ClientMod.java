package dev.livingvillages.client;
import dev.livingvillages.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
@Mod(value="livingvillages",dist=Dist.CLIENT)
public final class ClientMod {
    public ClientMod() {
        LedgerNetwork.clientReceiver((json,refresh)->{
            var client=Minecraft.getInstance();
            if(client.level==null||client.player==null)return;
            try {
                var view=LedgerView.parse(json);
                var open=client.screen instanceof LedgerScreen screen?screen:null;
                switch(LedgerRouting.route(refresh,open==null?null:open.village(),view.village())) {
                    case OPEN -> client.setScreen(new LedgerScreen(view));
                    case UPDATE -> open.refresh(view);
                    case IGNORE -> { }
                }
            } catch(RuntimeException error) {
                dev.livingvillages.LivingVillagesMod.LOG.error("event=ledger_decode_failure",error);
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Unable to read Village Ledger snapshot."),false);
            }
        });
    }
}
