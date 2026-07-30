package com.wavedefense.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Wave Defense mod configuration.
 * File: config/wavedefense-common.toml
 *
 * Sections:
 *   [general]         — general settings
 *   [shop]            — shop settings
 *   [zone_activation] — auto-activation zones
 *   [pvp]             — PvP rounds
 *   [mob_equipment]   — mob armor/weapons
 *   [hotkeys]         — hotkeys
 *   [limits]          — value limits (up to 9999)
 */
public class WaveDefenseConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ── General ───────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue ENABLE_HUD;
    public static final ForgeConfigSpec.IntValue DEFAULT_WAVE_TIME;
    public static final ForgeConfigSpec.BooleanValue ENABLE_UI_TOOLTIPS;
    public static final ForgeConfigSpec.BooleanValue SHOW_JOIN_HINTS;
    public static final ForgeConfigSpec.IntValue LOBBY_TIMER_SECONDS;
    /**
     * Game mode applied to players when joining a location (and when they are in Creative).
     * Accepted values: "survival", "adventure"
     */
    public static final ForgeConfigSpec.ConfigValue<String> LOCATION_GAME_MODE;

    // ── Shop ──────────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue SHOP_CATEGORIES_ENABLED;

    // ── PvE zone auto-activation ──────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue ZONE_ACTIVATION_RADIUS;
    public static final ForgeConfigSpec.IntValue ZONE_ACTIVATION_COUNTDOWN;
    public static final ForgeConfigSpec.BooleanValue ZONE_ACTIVATION_PARTICLES;

    // ── PvP ───────────────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue PVP_HIDE_ENEMY_NAMETAGS;
    public static final ForgeConfigSpec.IntValue PVP_DEFAULT_ROUNDS;
    public static final ForgeConfigSpec.IntValue PVP_DEFAULT_BUY_TIME;
    public static final ForgeConfigSpec.IntValue PVP_RESPAWN_DELAY_SECONDS;
    // v0.2.61 — Ready-check + PvP score caps
    public static final ForgeConfigSpec.IntValue PVP_MAX_READY_CHECK_TIMEOUT_SEC;
    public static final ForgeConfigSpec.IntValue PVP_MAX_KILL_POINTS;
    public static final ForgeConfigSpec.IntValue PVP_MAX_DEATH_PENALTY;
    public static final ForgeConfigSpec.IntValue PVP_MAX_WIN_POINTS;
    public static final ForgeConfigSpec.IntValue PVP_MAX_LOSE_POINTS;

    // ── Mob equipment ─────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue MOBS_CAN_HAVE_EQUIPMENT;
    public static final ForgeConfigSpec.DoubleValue MOB_ARMOR_DROP_CHANCE;

    // ── Debug / Logging ──────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue DEBUG_ADMIN_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MONITOR_BROADCAST_ALERTS;

    // ── Gameplay ──────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue     MAX_PLAYERS_PER_LOCATION;
    public static final ForgeConfigSpec.BooleanValue WAVE_START_TITLE_ENABLED;

    // ── Hotkeys ───────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue SHOP_HOTKEY_ENABLED;

    // ── Limits (up to 9999) ───────────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue MAX_MOB_TYPES;       // mob types per wave
    public static final ForgeConfigSpec.IntValue MAX_WAVES;            // waves per location
    public static final ForgeConfigSpec.IntValue MAX_MOB_SPAWNS;       // mob spawn points
    public static final ForgeConfigSpec.IntValue MAX_PLAYER_SPAWNS;    // PvP team spawn points
    public static final ForgeConfigSpec.IntValue MAX_SHOP_ITEMS;       // shop items
    public static final ForgeConfigSpec.IntValue MAX_LOOT_SPAWNS;      // loot spawn points

    static {
        BUILDER.push("general");

        ENABLE_HUD = BUILDER
                .comment("Enables the HUD overlay showing wave information.")
                .define("enableHUD", true);

        DEFAULT_WAVE_TIME = BUILDER
                .comment("Default time between waves (seconds).")
                .defineInRange("defaultWaveTime", 60, 10, 600);

        ENABLE_UI_TOOLTIPS = BUILDER
                .comment("Show tooltips when hovering over UI elements.")
                .define("enableUITooltips", true);

        SHOW_JOIN_HINTS = BUILDER
                .comment("Send a short intro in chat when a player joins a location:",
                         "the objective for that mode plus the mod's hotkeys.",
                         "Turn off if your server explains this its own way.")
                .define("showJoinHints", true);

        LOBBY_TIMER_SECONDS = BUILDER
                .comment("Lobby countdown timer in seconds — resets when a new player joins.")
                .defineInRange("lobbyTimerSeconds", 30, 5, 300);

        LOCATION_GAME_MODE = BUILDER
                .comment("Game mode forced on players when they join a location.\n"
                        + "Creative players are automatically switched to this mode.\n"
                        + "Accepted values: \"survival\", \"adventure\"")
                .define("locationGameMode", "survival");

        MAX_PLAYERS_PER_LOCATION = BUILDER
                .comment("Maximum number of players allowed in one location simultaneously.\n"
                        + "0 = no limit.")
                .defineInRange("maxPlayersPerLocation", 0, 0, 200);

        WAVE_START_TITLE_ENABLED = BUILDER
                .comment("Show a large title/subtitle on the player's screen when each wave starts.")
                .define("waveStartTitleEnabled", true);

        BUILDER.pop();

        BUILDER.push("shop");

        SHOP_CATEGORIES_ENABLED = BUILDER
                .comment("Enables shop categories (weapon, armor, consumable, other).")
                .define("categoriesEnabled", true);

        BUILDER.pop();

        BUILDER.push("zone_activation");

        ZONE_ACTIVATION_RADIUS = BUILDER
                .comment("Auto-activation zone radius in blocks.\n" +
                         "Players entering the zone start the countdown timer.")
                .defineInRange("radius", 5, 1, 20);

        ZONE_ACTIVATION_COUNTDOWN = BUILDER
                .comment("Countdown (seconds) before the zone activates — players can leave to cancel.")
                .defineInRange("countdown", 30, 5, 120);

        ZONE_ACTIVATION_PARTICLES = BUILDER
                .comment("Show a particle ring around the auto-activation zone.")
                .define("particles", true);

        BUILDER.pop();

        BUILDER.push("pvp");

        PVP_HIDE_ENEMY_NAMETAGS = BUILDER
                .comment("Hide enemy player name tags during an active PvP round.")
                .define("hideEnemyNametags", true);

        PVP_DEFAULT_ROUNDS = BUILDER
                .comment("Default number of rounds for new PvP locations.")
                .defineInRange("defaultRounds", 10, 1, 100);

        PVP_DEFAULT_BUY_TIME = BUILDER
                .comment("Default buy-phase duration between rounds (seconds).")
                .defineInRange("defaultBuyTime", 20, 5, 120);

        PVP_RESPAWN_DELAY_SECONDS = BUILDER
                .comment("Respawn delay (seconds) after death in Deathmatch and Battle Royale.\n"
                        + "0 = instant respawn.")
                .defineInRange("pvpRespawnDelaySeconds", 0, 0, 30);

        // v0.2.61 — Ready-check + PvP point caps
        PVP_MAX_READY_CHECK_TIMEOUT_SEC = BUILDER
                .comment("Maximum allowed ready-check timeout (seconds) admins can set per location.\n"
                        + "Hard cap against absurdly long waits. 0 in per-location = wait forever.")
                .defineInRange("pvpMaxReadyCheckTimeoutSec", 600, 0, 3600);

        PVP_MAX_KILL_POINTS = BUILDER
                .comment("Maximum points awarded per kill — caps admin input.")
                .defineInRange("pvpMaxKillPoints", 100000, 0, 9999999);

        PVP_MAX_DEATH_PENALTY = BUILDER
                .comment("Maximum point penalty per death — caps admin input.")
                .defineInRange("pvpMaxDeathPenalty", 100000, 0, 9999999);

        PVP_MAX_WIN_POINTS = BUILDER
                .comment("Maximum points awarded to round-winning team — caps admin input.")
                .defineInRange("pvpMaxWinPoints", 100000, 0, 9999999);

        PVP_MAX_LOSE_POINTS = BUILDER
                .comment("Maximum points awarded to round-losing team — caps admin input.")
                .defineInRange("pvpMaxLosePoints", 100000, 0, 9999999);

        BUILDER.pop();

        BUILDER.push("mob_equipment");

        MOBS_CAN_HAVE_EQUIPMENT = BUILDER
                .comment("Allow mobs to be given armor and weapons.")
                .define("enabled", true);

        MOB_ARMOR_DROP_CHANCE = BUILDER
                .comment("Drop chance for mob equipment (0.0 = never, 1.0 = always).")
                .defineInRange("armorDropChance", 0.085, 0.0, 1.0);

        BUILDER.pop();

        BUILDER.push("hotkeys");

        SHOP_HOTKEY_ENABLED = BUILDER
                .comment("B hotkey to open the shop directly (without the admin menu).")
                .define("shopHotkeyEnabled", true);

        BUILDER.pop();

        BUILDER.push("debug");
        BUILDER.comment("Debug and logging settings.");

        DEBUG_ADMIN_MESSAGES = BUILDER
                .comment("Show location state messages (start, waves) to administrators.\n" +
                         "Only visible to players with permission level >= 2.")
                .define("adminDebugMessages", true);

        DEBUG_LOGGING_ENABLED = BUILDER
                .comment("Write mod events to the server log (waves, triggers, errors).\n" +
                         "Disable to reduce log volume.")
                .define("loggingEnabled", true);

        MONITOR_BROADCAST_ALERTS = BUILDER
                .comment("Broadcast WaveDefenseMonitor alerts (low TPS, high memory, wave timeout)\n" +
                         "to all online operators (permission level >= 2).\n" +
                         "Disabled by default — alerts are still written to the server log.\n" +
                         "Enable only for live diagnostics; otherwise leave off to avoid spam.")
                .define("monitorBroadcastAlerts", false);

        BUILDER.pop();

        BUILDER.push("limits");
        BUILDER.comment("Maximum limits for location settings. Range: 1-9999.");

        MAX_MOB_TYPES = BUILDER
                .comment("Maximum number of mob types in a single wave.")
                .defineInRange("maxMobTypes", 20, 1, 9999);

        MAX_WAVES = BUILDER
                .comment("Maximum number of waves in a location.")
                .defineInRange("maxWaves", 100, 1, 9999);

        MAX_MOB_SPAWNS = BUILDER
                .comment("Maximum number of mob spawn points in a location.")
                .defineInRange("maxMobSpawns", 50, 1, 9999);

        MAX_PLAYER_SPAWNS = BUILDER
                .comment("Maximum number of PvP team spawn points.")
                .defineInRange("maxPlayerSpawns", 20, 1, 9999);

        MAX_SHOP_ITEMS = BUILDER
                .comment("Maximum number of items in a location's shop.")
                .defineInRange("maxShopItems", 100, 1, 9999);

        MAX_LOOT_SPAWNS = BUILDER
                .comment("Maximum number of loot spawn points.")
                .defineInRange("maxLootSpawns", 50, 1, 9999);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "wavedefense-common.toml");
    }

    /** Returns the GameType configured for locations (survival or adventure). */
    public static net.minecraft.world.level.GameType getLocationGameType() {
        String val = LOCATION_GAME_MODE.get();
        if ("adventure".equalsIgnoreCase(val)) return net.minecraft.world.level.GameType.ADVENTURE;
        return net.minecraft.world.level.GameType.SURVIVAL; // default
    }
}
