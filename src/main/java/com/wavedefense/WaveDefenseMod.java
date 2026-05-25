package com.wavedefense;

import com.wavedefense.commands.WaveDefenseCommand;
import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LocationManager;
import com.wavedefense.events.EventHandler;
import com.wavedefense.events.KeyBindings;
import com.wavedefense.gui.WaveDefenseConfigScreen;
import com.wavedefense.monitor.WaveDefenseMonitor;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (locationManager != null) {
            locationManager.saveToFile();
            LOGGER.info("[WaveDefense] Location data saved on server stop.");
        }
        if (leaderboardManager != null) {
            leaderboardManager.saveToFile();
            LOGGER.info("[WaveDefense] Leaderboard data saved on server stop.");
        }
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

        // Register in-game config screen (Mods menu → Wave Defense → Config)
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(
                (mc, parentScreen) -> new WaveDefenseConfigScreen(parentScreen)
            )
        );
    }
}
