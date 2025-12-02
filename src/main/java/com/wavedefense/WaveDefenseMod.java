package com.wavedefense;

import com.wavedefense.config.ModConfig;
import com.wavedefense.data.LocationManager;
import com.wavedefense.events.EventHandler;
import com.wavedefense.events.KeyBindings;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
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

    public WaveDefenseMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        ModConfig.register();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.register(this); // Register for server events

        waveManager = new WaveManager();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        locationManager = new LocationManager(serverInstance);
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
        KeyBindings.register();
    }
}
