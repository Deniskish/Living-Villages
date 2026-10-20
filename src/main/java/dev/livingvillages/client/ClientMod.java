package dev.livingvillages.client;
import dev.livingvillages.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
@Mod(value="livingvillages",dist=Dist.CLIENT)
public final class ClientMod {
    public ClientMod() {
        LedgerNetwork.clientReceiver(json->Minecraft.getInstance().setScreen(new LedgerScreen(LedgerView.parse(json))));
    }
}
