package com.wavedefense;

import com.wavedefense.backup.WaveDefenseBackupSystem;
import com.wavedefense.commands.WaveDefenseCommand;
import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LocationManager;
import com.wavedefense.events.EventHandler;
import com.wavedefense.monitor.WaveDefenseMonitor;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(WaveDefenseMod.MODID)
public class WaveDefenseMod {
    public static final String MODID = "wavedefense";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static MinecraftServer serverInstance;
    public static LocationManager locationManager;
    public static LeaderboardManager leaderboardManager;
    public static WaveManager waveManager;
    public static PacketHandler packetHandler;

    public WaveDefenseMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        WaveDefenseConfig.register();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.register(this);

        waveManager = new WaveManager();
        packetHandler = new PacketHandler();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        locationManager = new LocationManager(serverInstance);
        leaderboardManager = new LeaderboardManager(serverInstance);
        leaderboardManager.loadFromFile();
        WaveDefenseBackupSystem.getInstance().initialize();
        WaveDefenseBackupSystem.getInstance().startScheduledBackups();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Synchronous save on shutdown — bypass debounce so no data is lost.
        if (locationManager != null) {
            locationManager.saveToFileSync();
            LOGGER.info("[WaveDefense] Location data saved on server stop.");
        }
        if (leaderboardManager != null) {
            leaderboardManager.saveToFileSync();
            LOGGER.info("[WaveDefense] Leaderboard data saved on server stop.");
        }
        // Drain any other in-flight debounced writes (defensive — should be empty if
        // both sync saves above ran).
        com.wavedefense.data.NbtHelper.flushPendingWrites();
        WaveDefenseBackupSystem.getInstance().shutdown();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WaveDefenseMonitor.getInstance().onServerTick();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WaveDefenseCommand.register(event.getDispatcher());
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Wave Defense Mod - Common Setup");
        PacketHandler.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Wave Defense Mod - Client Setup");
        // Delegate to an @OnlyIn(Dist.CLIENT) inner class so that ConfigScreenHandler
        // and WaveDefenseConfigScreen (which extend/import net.minecraft.client.* classes
        // absent on dedicated servers) are never loaded on the server JVM.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::register);
    }

    /**
     * All client-only setup code lives here.
     * This class is only loaded when running on the client dist.
     */
    @OnlyIn(Dist.CLIENT)
    private static final class ClientSetup {
        static void register() {
            // Register in-game config screen (Mods menu → Wave Defense → Config)
            ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parentScreen) -> new com.wavedefense.gui.WaveDefenseConfigScreen(parentScreen)
                )
            );
        }
    }
}
