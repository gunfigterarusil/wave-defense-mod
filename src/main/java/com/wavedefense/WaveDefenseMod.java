package com.wavedefense;

import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.LocationManager;
import com.wavedefense.events.EventHandler;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        LOGGER.info("Wave Defense Mod initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        locationManager = new LocationManager(serverInstance);
        LOGGER.info("Server starting - LocationManager initialized");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (locationManager != null) {
            locationManager.saveToFile();
            LOGGER.info("Server stopping - Data saved");
        }
        serverInstance = null;
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            LOGGER.info("Wave Defense Mod - Common Setup Complete");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Wave Defense Mod - Client Setup Complete");
    }
}
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Wave Defense Mod - Client Setup");
    }
}
