package com.wavedefense;

import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LocationManager;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for Wave Defence — Minecraft 1.16.5 / Forge 36.x port.
 *
 * <p>Ported from the 1.20.1 / Forge 47.x codebase. This is the entry point
 * registered via {@code @Mod} that wires up the ModEventBus listeners,
 * exposes static manager singletons used across packets/commands/GUI, and
 * provides {@link #LOGGER} for module-wide logging.
 *
 * <p>Phase 1 of the port (v0.0.1): data layer + persistence + minimal command
 * stub. Network packets, server runtime, full GUI follow in subsequent phases.
 */
@Mod(WaveDefenceMod.MODID)
public class WaveDefenceMod {

    /** modId — must match {@code mods.toml} and resource namespaces. */
    public static final String MODID = "wave_defence";

    public static final Logger LOGGER = LogManager.getLogger("wavedefense");

    /** Server-side managers. Populated on {@link FMLServerStartingEvent}. */
    public static LocationManager    locationManager;
    public static WaveManager        waveManager;
    public static LeaderboardManager leaderboardManager;
    private static MinecraftServer   serverInstance;

    public WaveDefenceMod() {
        // ── ModEventBus listeners ────────────────────────────────────
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        // ── Forge config registration ────────────────────────────────
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WaveDefenseConfig.SPEC,
            "wavedefense-common.toml");

        // ── Forge event bus subscription ─────────────────────────────
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Wave Defence — 1.16.5 port constructor finished");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Wave Defence — Common Setup (1.16.5)");
        // Network packet registration (Phase 2)
        PacketHandler.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Wave Defence — Client Setup (1.16.5)");
        // 1.16.5: register keybindings here (no RegisterKeyMappingsEvent in this Forge version)
        com.wavedefense.events.KeyBindings.registerAll();
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        serverInstance = event.getServer();
        // Initialise managers (Phase 1 + 2)
        locationManager    = new LocationManager(serverInstance);
        leaderboardManager = new LeaderboardManager(serverInstance);
        leaderboardManager.loadFromFile();
        waveManager        = new WaveManager();
        LOGGER.info("Wave Defence — server started, {} locations loaded",
            locationManager.getAllLocationNames().size());

        com.wavedefense.commands.WaveDefenseCommand.register(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (locationManager != null) locationManager.saveToFile();
        if (leaderboardManager != null) leaderboardManager.saveToFile();
        LOGGER.info("Wave Defence — server stopped, state saved");
    }

    public static MinecraftServer getServer() { return serverInstance; }
}
