package com.wavedefense.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class WaveDefenseConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_HUD;
    public static final ForgeConfigSpec.IntValue DEFAULT_WAVE_TIME;
    public static final ForgeConfigSpec.IntValue MAX_LOCATIONS;

    static {
        BUILDER.push("General Settings");

        ENABLE_HUD = BUILDER
                .comment("Enable HUD overlay for wave information")
                .define("enableHUD", true);

        DEFAULT_WAVE_TIME = BUILDER
                .comment("Default time between waves in seconds")
                .defineInRange("defaultWaveTime", 60, 10, 600);

        MAX_LOCATIONS = BUILDER
                .comment("Maximum number of locations that can be created")
                .defineInRange("maxLocations", 50, 1, 100);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
