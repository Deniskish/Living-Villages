package dev.livingvillages;
import com.mojang.logging.LogUtils;
import dev.livingvillages.config.VillageConfig;
import dev.livingvillages.world.Simulation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
@Mod(LivingVillagesMod.ID)
public final class LivingVillagesMod {
    public static final String ID="livingvillages";
    public static final Logger LOG=LogUtils.getLogger();
    private final Simulation simulation=new Simulation();
    public LivingVillagesMod(IEventBus bus,ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER,VillageConfig.SPEC);
        dev.livingvillages.task.WorkerNavigation.ACTIVITIES.register(bus);
        bus.addListener(dev.livingvillages.network.LedgerNetwork::register);
        NeoForge.EVENT_BUS.addListener(dev.livingvillages.network.LedgerNetwork::interact);
        NeoForge.EVENT_BUS.addListener(dev.livingvillages.command.VillageCommands::register);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::death);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,simulation::drops);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }
    private void tick(LevelTickEvent.Post e) { if(e.getLevel() instanceof ServerLevel level)simulation.tick(level); }
    private void death(LivingDeathEvent e) { simulation.death(e.getEntity()); }
    private void stopped(ServerStoppedEvent e) { simulation.clear(); }
}
