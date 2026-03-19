package com.wavedefense.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Конфіг-файл моду Wave Defense.
 * Файл: config/wavedefense-common.toml
 *
 * Категорії:
 *   [general]        — загальні налаштування
 *   [shop]           — магазин
 *   [zone_activation] — авто-активація зон
 *   [pvp]            — PvP раунди
 *   [mob_equipment]  — броня/зброя мобів
 *   [hotkeys]        — гарячі клавіші
 *   [limits]         — ліміти (до 9999)
 */
public class WaveDefenseConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ── Загальні ──────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue ENABLE_HUD;
    public static final ForgeConfigSpec.IntValue DEFAULT_WAVE_TIME;
    public static final ForgeConfigSpec.BooleanValue ENABLE_UI_TOOLTIPS;
    public static final ForgeConfigSpec.IntValue LOBBY_TIMER_SECONDS;
    /**
     * Режим гри що встановлюється гравцю при вході на локацію (і якщо він в Creative).
     * Допустимі значення: "survival", "adventure"
     */
    public static final ForgeConfigSpec.ConfigValue<String> LOCATION_GAME_MODE;

    // ── Магазин ───────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue SHOP_CATEGORIES_ENABLED;

    // ── PvE зона авто-активація ───────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue ZONE_ACTIVATION_RADIUS;
    public static final ForgeConfigSpec.IntValue ZONE_ACTIVATION_COUNTDOWN;
    public static final ForgeConfigSpec.BooleanValue ZONE_ACTIVATION_PARTICLES;

    // ── PvP ───────────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue PVP_HIDE_ENEMY_NAMETAGS;
    public static final ForgeConfigSpec.IntValue PVP_DEFAULT_ROUNDS;
    public static final ForgeConfigSpec.IntValue PVP_DEFAULT_BUY_TIME;

    // ── Спавн мобів ───────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue MOBS_CAN_HAVE_EQUIPMENT;
    public static final ForgeConfigSpec.DoubleValue MOB_ARMOR_DROP_CHANCE;

    // ── Debug / Logging ───────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue DEBUG_ADMIN_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING_ENABLED;

    // ── Hotkeys ───────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue SHOP_HOTKEY_ENABLED;

    // ── Ліміти (до 9999) ─────────────────────────────────────────────────
    public static final ForgeConfigSpec.IntValue MAX_MOB_TYPES;       // типів мобів у хвилі
    public static final ForgeConfigSpec.IntValue MAX_WAVES;            // хвиль у локації
    public static final ForgeConfigSpec.IntValue MAX_MOB_SPAWNS;       // точок спавну мобів
    public static final ForgeConfigSpec.IntValue MAX_PLAYER_SPAWNS;    // PvP точок спавну команди
    public static final ForgeConfigSpec.IntValue MAX_SHOP_ITEMS;       // товарів у магазині
    public static final ForgeConfigSpec.IntValue MAX_LOOT_SPAWNS;      // точок лут-спавну

    static {
        BUILDER.push("general");

        ENABLE_HUD = BUILDER
                .comment("Вмикає HUD оверлей з інформацією про хвилі")
                .define("enableHUD", true);

        DEFAULT_WAVE_TIME = BUILDER
                .comment("Час між хвилями за замовчуванням (секунди)")
                .defineInRange("defaultWaveTime", 60, 10, 600);


        ENABLE_UI_TOOLTIPS = BUILDER
                .comment("Показувати підказки при наведенні на елементи інтерфейсу")
                .define("enableUITooltips", true);

        LOBBY_TIMER_SECONDS = BUILDER
                .comment("Таймер лоббі — перезапускається при кожному новому гравці (секунди)")
                .defineInRange("lobbyTimerSeconds", 30, 5, 300);

        LOCATION_GAME_MODE = BUILDER
                .comment("Режим гри що примусово встановлюється гравцю при вході на локацію.\n"
                        + "Якщо гравець в Creative — автоматично переводиться в цей режим.\n"
                        + "Допустимі значення: \"survival\", \"adventure\"")
                .define("locationGameMode", "survival");

        BUILDER.pop();

        BUILDER.push("shop");

        SHOP_CATEGORIES_ENABLED = BUILDER
                .comment("Вмикає категорії в магазині (зброя, броня, розхідники, інше)")
                .define("categoriesEnabled", true);

        BUILDER.pop();

        BUILDER.push("zone_activation");

        ZONE_ACTIVATION_RADIUS = BUILDER
                .comment("Радіус зони авто-активації локації (блоки)\n" +
                         "Гравці входять у зону → починається таймер")
                .defineInRange("radius", 5, 1, 20);

        ZONE_ACTIVATION_COUNTDOWN = BUILDER
                .comment("Таймер (секунди) до активації — гравець може вийти щоб скасувати")
                .defineInRange("countdown", 30, 5, 120);

        ZONE_ACTIVATION_PARTICLES = BUILDER
                .comment("Показувати чорне коло з частинок навколо зони активації")
                .define("particles", true);

        BUILDER.pop();

        BUILDER.push("pvp");

        PVP_HIDE_ENEMY_NAMETAGS = BUILDER
                .comment("Ховати ніки ворожих гравців під час активного PvP раунду")
                .define("hideEnemyNametags", true);

        PVP_DEFAULT_ROUNDS = BUILDER
                .comment("Кількість раундів за замовчуванням для нових PvP локацій")
                .defineInRange("defaultRounds", 10, 1, 100);

        PVP_DEFAULT_BUY_TIME = BUILDER
                .comment("Час покупок між раундами за замовчуванням (секунди)")
                .defineInRange("defaultBuyTime", 20, 5, 120);

        BUILDER.pop();

        BUILDER.push("mob_equipment");

        MOBS_CAN_HAVE_EQUIPMENT = BUILDER
                .comment("Дозволити видавати мобам броню та зброю")
                .define("enabled", true);

        MOB_ARMOR_DROP_CHANCE = BUILDER
                .comment("Ймовірність дропу спорядження мобів (0.0 = ніколи, 1.0 = завжди)")
                .defineInRange("armorDropChance", 0.085, 0.0, 1.0);

        BUILDER.pop();

        BUILDER.push("hotkeys");

        SHOP_HOTKEY_ENABLED = BUILDER
                .comment("Гаряча клавіша B для прямого відкриття магазину (без меню)")
                .define("shopHotkeyEnabled", true);

        BUILDER.pop();

        BUILDER.push("debug");
        BUILDER.comment("Налаштування відлагодження та логування");

        DEBUG_ADMIN_MESSAGES = BUILDER
                .comment("Показувати повідомлення про стан локацій (старт, хвилі) адміністраторам.\n" +
                         "Повідомлення видно тільки гравцям з permission level >= 2.")
                .define("adminDebugMessages", true);

        DEBUG_LOGGING_ENABLED = BUILDER
                .comment("Записувати події моду у server log (waves, triggers, errors).\n" +
                         "Вимкніть щоб зменшити обсяг логів.")
                .define("loggingEnabled", true);

        BUILDER.pop();

        BUILDER.push("limits");
        BUILDER.comment("Максимальні ліміти для налаштувань локацій. Значення 1-9999.");

        MAX_MOB_TYPES = BUILDER
                .comment("Максимальна кількість типів мобів у одній хвилі")
                .defineInRange("maxMobTypes", 20, 1, 9999);

        MAX_WAVES = BUILDER
                .comment("Максимальна кількість хвиль у локації")
                .defineInRange("maxWaves", 100, 1, 9999);

        MAX_MOB_SPAWNS = BUILDER
                .comment("Максимальна кількість точок спавну мобів у локації")
                .defineInRange("maxMobSpawns", 50, 1, 9999);

        MAX_PLAYER_SPAWNS = BUILDER
                .comment("Максимальна кількість PvP точок спавну команд")
                .defineInRange("maxPlayerSpawns", 20, 1, 9999);

        MAX_SHOP_ITEMS = BUILDER
                .comment("Максимальна кількість товарів у магазині локації")
                .defineInRange("maxShopItems", 100, 1, 9999);

        MAX_LOOT_SPAWNS = BUILDER
                .comment("Максимальна кількість точок спавну луту")
                .defineInRange("maxLootSpawns", 50, 1, 9999);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "wavedefense-common.toml");
    }

    /** Повертає GameType налаштований для локацій (survival або adventure). */
    public static net.minecraft.world.level.GameType getLocationGameType() {
        String val = LOCATION_GAME_MODE.get();
        if ("adventure".equalsIgnoreCase(val)) return net.minecraft.world.level.GameType.ADVENTURE;
        return net.minecraft.world.level.GameType.SURVIVAL; // default
    }
}
