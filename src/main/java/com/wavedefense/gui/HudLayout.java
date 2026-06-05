package com.wavedefense.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.nio.file.*;

/**
 * Зберігає та завантажує позиції HUD-елементів.
 * Позиції задаються як відсотки від ширини/висоти екрану,
 * щоб коректно масштабуватись при зміні роздільної здатності.
 *
 * Пресети:
 *   TOP_CENTER   — по замовчуванню (зверху по центру)
 *   TOP_LEFT     — верхній лівий кут
 *   TOP_RIGHT    — верхній правий кут
 *   BOTTOM_LEFT  — нижній лівий
 */
@OnlyIn(Dist.CLIENT)
public class HudLayout {

    public enum Preset {
        TOP_CENTER("wavedefense.hud.preset.top_center"),
        TOP_LEFT("wavedefense.hud.preset.top_left"),
        TOP_RIGHT("wavedefense.hud.preset.top_right"),
        BOTTOM_LEFT("wavedefense.hud.preset.bottom_left"),
        CUSTOM("wavedefense.hud.preset.custom");

        public final String key;
        Preset(String k) { this.key = k; }
    }

    // Позиція блоку HUD — верхній лівий кут у пікселях
    // (перераховується з preset або drag)
    public int blockX   = -1;   // -1 = auto (by preset)
    public int blockY   = -1;
    public Preset preset = Preset.TOP_CENTER;

    // ── Singleton ──────────────────────────────────────────────────
    private static HudLayout INSTANCE = null;

    public static HudLayout get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void invalidate() { INSTANCE = null; }

    // ── Розрахунок пікселів позиції ─────────────────────────────────
    /**
     * Повертає X-координату лівого краю HUD-блоку.
     * Ширина блоку estimatedW задається для вирівнювання по центру/правому краю.
     */
    public int resolveX(int screenW, int estimatedW) {
        if (preset == Preset.CUSTOM && blockX >= 0) return blockX;
        switch (preset) {
            case TOP_CENTER:   return (screenW - estimatedW) / 2;
            case TOP_LEFT:     return 6;
            case TOP_RIGHT:    return screenW - estimatedW - 6;
            case BOTTOM_LEFT:  return 6;
            default:           return (screenW - estimatedW) / 2;
        }
    }

    public int resolveY(int screenH, int estimatedH) {
        if (preset == Preset.CUSTOM && blockY >= 0) return blockY;
        switch (preset) {
            case TOP_CENTER:
            case TOP_LEFT:
            case TOP_RIGHT:    return 8;
            case BOTTOM_LEFT:  return screenH - estimatedH - 40;
            default:           return 8;
        }
    }

    // ── Persistence (config/wavedefense_hud.json) ───────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
               .resolve("config").resolve("wavedefense_hud.json");
    }

    public void save() {
        try {
            Files.createDirectories(configPath().getParent());
            try (Writer w = new FileWriter(configPath().toFile())) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static HudLayout load() {
        try {
            Path p = configPath();
            if (Files.exists(p)) {
                try (Reader r = new FileReader(p.toFile())) {
                    HudLayout loaded = GSON.fromJson(r, HudLayout.class);
                    if (loaded != null) {
                        if (loaded.preset == null) loaded.preset = Preset.TOP_CENTER;
                        return loaded;
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return new HudLayout();
    }
}
