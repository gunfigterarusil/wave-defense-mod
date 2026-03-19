package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.WaveManager;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Управляє режимом Королівська Битва (Battle Royale) для PvP локацій.
 *
 * Кожна активна BR-локація має:
 *   - поточний радіус кордону (зменшується кожні brShrinkIntervalSec секунд)
 *   - частинки по периметру (відображаються гравцям)
 *   - шкода при перетині кордону
 *
 * Керується з WaveManager.tick() кожні 20 тіків (1 сек).
 */
public class BattleRoyaleManager {

    /** Поточний радіус кордону для кожної активної BR-локації */
    private final Map<String, Integer> currentRadius   = new HashMap<>();
    /** Лічильник тіків до наступного звуження */
    private final Map<String, Integer> shrinkTicker    = new HashMap<>();

    // ── Публічне API ────────────────────────────────────────────────────

    /** Викликається коли перший гравець входить на BR-локацію */
    public void initLocation(Location loc) {
        String name = loc.getName();
        if (!currentRadius.containsKey(name)) {
            currentRadius.put(name, loc.getBrBorderRadius());
            shrinkTicker.put(name, loc.getBrShrinkIntervalSec() * 20);
        }
    }

    /** Повертає поточний радіус BR-кордону (або початковий якщо не ініціалізовано) */
    public int getRadius(String locName, Location loc) {
        return currentRadius.getOrDefault(locName, loc.getBrBorderRadius());
    }

    /** Очищає стан локації після завершення матчу */
    public void clearLocation(String locName) {
        currentRadius.remove(locName);
        shrinkTicker.remove(locName);
    }

    /**
     * Тікає кожні 20 тіків (раз на секунду).
     * Оновлює кордон, відображає частинки, наносить шкоду.
     */
    public void tick(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;

        for (Map.Entry<String, Integer> entry : new HashMap<>(currentRadius).entrySet()) {
            String locName  = entry.getKey();
            int    radius   = entry.getValue();
            Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc == null || !loc.isBattleRoyale()) { clearLocation(locName); continue; }

            // Перевіряємо чи є активні гравці
            List<ServerPlayer> players = wm.getPlayersInLocation(locName);
            if (players.isEmpty()) continue;

            ServerLevel world = players.get(0).serverLevel();
            BlockPos center = loc.getPlayerSpawn();
            if (center == null) continue;

            // ── Звуження кордону ─────────────────────────────────────────
            int ticks = shrinkTicker.getOrDefault(locName, 0) - 1;
            if (ticks <= 0) {
                int newRadius = Math.max(5, radius - 1);
                currentRadius.put(locName, newRadius);
                shrinkTicker.put(locName, loc.getBrShrinkIntervalSec() * 20);

                // Повідомлення кожні 10 блоків зменшення
                if (newRadius % 10 == 0 || newRadius <= 20) {
                    wm.broadcastToLocation(locName,
                        "§c⚠ §eКордон звузився! §cРадіус: §e" + newRadius + " §cблоків");
                }
                radius = newRadius;
            } else {
                shrinkTicker.put(locName, ticks);
            }

            // ── Частинки кордону ──────────────────────────────────────────
            spawnBorderParticles(world, center, radius, loc);

            // ── Шкода поза кордоном ────────────────────────────────────────
            if (loc.isBrBorderDamage()) {
                for (ServerPlayer p : players) {
                    double dist = Math.sqrt(p.blockPosition().distSqr(center));
                    if (dist > radius) {
                        p.hurt(p.damageSources().magic(), loc.getBrBorderDamageAmt());
                        p.displayClientMessage(
                            Component.literal("§c☠ Ви поза зоною безпеки! (-" + loc.getBrBorderDamageAmt() + " HP/сек)"), true);
                    }
                }
            }
        }
    }

    // ── Приватні методи ──────────────────────────────────────────────────

    private void spawnBorderParticles(ServerLevel world, BlockPos center, int radius, Location loc) {
        ParticleOptions particle = resolveParticle(loc.getBrBorderParticle());
        int count = loc.getBrBorderParticleCount();
        // Відображаємо кільце частинок по периметру (кожні 3 блоки по окружності)
        int steps = Math.max(8, (int)(2 * Math.PI * radius / 3));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = center.getX() + 0.5 + radius * Math.cos(angle);
            double pz = center.getZ() + 0.5 + radius * Math.sin(angle);
            // Кілька висот щоб кордон був видно
            for (int dy = 0; dy <= 4; dy += 2) {
                world.sendParticles(particle,
                    px, center.getY() + dy, pz,
                    1, 0, 0, 0, 0.01);
            }
        }
    }

    private static ParticleOptions resolveParticle(String id) {
        if (id == null || id.isBlank()) return ParticleTypes.FLAME;
        try {
            var type = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (type instanceof ParticleOptions po) return po;
            // SimpleParticleType
            if (type instanceof net.minecraft.core.particles.SimpleParticleType spt) return spt;
        } catch (Exception ignored) {}
        // Fallback map
        return switch (id) {
            case "minecraft:flame"         -> ParticleTypes.FLAME;
            case "minecraft:smoke"         -> ParticleTypes.SMOKE;
            case "minecraft:crit"          -> ParticleTypes.CRIT;
            case "minecraft:large_smoke"   -> ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal"        -> ParticleTypes.PORTAL;
            case "minecraft:enchant"       -> ParticleTypes.ENCHANT;
            case "minecraft:end_rod"       -> ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame"->ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:snowflake"     -> ParticleTypes.SNOWFLAKE;
            case "minecraft:dripping_lava" -> ParticleTypes.DRIPPING_LAVA;
            default                        -> ParticleTypes.FLAME;
        };
    }
}
