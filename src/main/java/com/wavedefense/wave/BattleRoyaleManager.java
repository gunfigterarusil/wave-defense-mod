package com.wavedefense.wave;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.wave.WaveManager;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;

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
    /** Initial-wait countdown ticks (0 = first shrink can fire). */
    private final Map<String, Integer> initialWaitTicker = new HashMap<>();

    // ── Публічне API ────────────────────────────────────────────────────

    /** Викликається коли перший гравець входить на BR-локацію */
    public void initLocation(Location loc) {
        String name = loc.getName();
        if (!currentRadius.containsKey(name)) {
            currentRadius.put(name, loc.getBrBorderRadius());
            shrinkTicker.put(name, loc.getBrShrinkIntervalSec() * 20);
            initialWaitTicker.put(name, loc.getBrInitialWaitSec() * 20);
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
        initialWaitTicker.remove(locName);
    }

    /**
     * Тікає кожні 20 тіків (раз на секунду).
     * Оновлює кордон, відображає частинки, наносить шкоду.
     */
    public void tick(WaveManager wm) {
        if (WaveDefenceMod.getServer() == null) return;

        for (Map.Entry<String, Integer> entry : new HashMap<>(currentRadius).entrySet()) {
            String locName  = entry.getKey();
            int    radius   = entry.getValue();
            Location loc = WaveDefenceMod.locationManager.getLocation(locName);
            if (loc == null || !loc.isBattleRoyale()) { clearLocation(locName); continue; }

            // Перевіряємо чи є активні гравці
            List<ServerPlayerEntity> players = wm.getPlayersInLocation(locName);
            if (players.isEmpty()) continue;

            ServerWorld world = ((net.minecraft.world.server.ServerWorld) players.get(0).level);
            BlockPos center = loc.getPlayerSpawn();
            if (center == null) continue;

            // ── Initial wait before shrinking begins ─────────────────────
            int initWait = initialWaitTicker.getOrDefault(locName, 0);
            if (initWait > 0) {
                initWait -= 20; // tick() runs every 20 ticks (1 sec)
                initialWaitTicker.put(locName, initWait);
                if (initWait == 0) {
                    wm.broadcastToLocation(locName,
                        new TranslationTextComponent("wavedefense.msg.br_border_starts"));
                }
                // Still spawn particles + apply damage even during initial wait
            } else {
                // ── Звуження кордону ──────────────────────────────────────
                int finalR = loc.getBrFinalRadius();
                int ticks  = shrinkTicker.getOrDefault(locName, 0) - 20; // tick() runs every 20 ticks
                if (ticks <= 0 && radius > finalR) {
                    int amount = loc.getBrShrinkAmountBlocks();
                    int newRadius = Math.max(finalR, radius - amount);
                    currentRadius.put(locName, newRadius);
                    shrinkTicker.put(locName, loc.getBrShrinkIntervalSec() * 20);

                    // Broadcast when crossing thresholds (every 10 blocks or near final)
                    if (newRadius % 10 == 0 || newRadius <= 20 || newRadius == finalR) {
                        wm.broadcastToLocation(locName,
                            new TranslationTextComponent("wavedefense.auto.border_narrowed_radius_value_7b82d92e", newRadius + " §cblocks"));
                    }
                    if (newRadius == finalR) {
                        wm.broadcastToLocation(locName,
                            new TranslationTextComponent("wavedefense.msg.br_border_final", finalR));
                    }
                    radius = newRadius;
                } else {
                    shrinkTicker.put(locName, ticks);
                }
            }

            // ── Частинки кордону ──────────────────────────────────────────
            spawnBorderParticles(world, center, radius, loc);

            // ── Шкода поза кордоном ────────────────────────────────────────
            // FIX: don't damage players during the initial wait phase — the border
            // hasn't started shrinking yet and players need time to spread out.
            // Damage applies only once initialWaitTicker has counted down to 0.
            boolean shrinkActive = initialWaitTicker.getOrDefault(locName, 0) <= 0;
            if (shrinkActive && loc.isBrBorderDamage() && loc.getBrBorderDamageAmt() > 0) {
                for (ServerPlayerEntity p : players) {
                    double dist = Math.sqrt(p.blockPosition().distSqr(center));
                    if (dist > radius) {
                        p.hurt(net.minecraft.util.DamageSource.MAGIC, loc.getBrBorderDamageAmt());
                        p.displayClientMessage(
                            new TranslationTextComponent("wavedefense.msg.outside_zone", loc.getBrBorderDamageAmt()), true);
                    }
                }
            }
        }
    }

    // ── Приватні методи ──────────────────────────────────────────────────

    private void spawnBorderParticles(ServerWorld world, BlockPos center, int radius, Location loc) {
        net.minecraft.particles.IParticleData particle = resolveParticle(loc.getBrBorderParticle());
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

    private static net.minecraft.particles.IParticleData resolveParticle(String id) {
        if (id == null || id.trim().isEmpty()) return ParticleTypes.FLAME;
        try {
            net.minecraft.particles.ParticleType<?> type = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(id));
            if (type instanceof net.minecraft.particles.IParticleData) { net.minecraft.particles.IParticleData po = (net.minecraft.particles.IParticleData) type; return po; }
            // net.minecraft.particles.BasicParticleType
            if (type instanceof net.minecraft.particles.BasicParticleType) { net.minecraft.particles.BasicParticleType spt = (net.minecraft.particles.BasicParticleType) type; return spt; }
        } catch (Exception ignored) {}
        // Fallback map
        switch (id) {
            case "minecraft:flame": return ParticleTypes.FLAME;
            case "minecraft:smoke": return ParticleTypes.SMOKE;
            case "minecraft:crit": return ParticleTypes.CRIT;
            case "minecraft:large_smoke": return ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal": return ParticleTypes.PORTAL;
            case "minecraft:enchant": return ParticleTypes.ENCHANT;
            case "minecraft:end_rod": return ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame": return ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:dripping_lava": return ParticleTypes.DRIPPING_LAVA;
            default: return ParticleTypes.FLAME;

        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану BattleRoyaleManager. */
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        // currentRadius
        CompoundNBT radiusTag = new CompoundNBT();
        for (Map.Entry<String, Integer> entry : currentRadius.entrySet()) {
            radiusTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("currentRadius", radiusTag);
        // shrinkTicker
        CompoundNBT shrinkTag = new CompoundNBT();
        for (Map.Entry<String, Integer> entry : shrinkTicker.entrySet()) {
            shrinkTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("shrinkTicker", shrinkTag);
        // initialWaitTicker
        CompoundNBT waitTag = new CompoundNBT();
        for (Map.Entry<String, Integer> entry : initialWaitTicker.entrySet()) {
            waitTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("initialWaitTicker", waitTag);
        return tag;
    }

    /** Відновлення стану BattleRoyaleManager. */
    public void load(CompoundNBT tag) {
        currentRadius.clear();
        shrinkTicker.clear();
        if (tag.contains("currentRadius")) {
            CompoundNBT radiusTag = tag.getCompound("currentRadius");
            for (String key : radiusTag.getAllKeys()) {
                currentRadius.put(key, radiusTag.getInt(key));
            }
        }
        if (tag.contains("shrinkTicker")) {
            CompoundNBT shrinkTag = tag.getCompound("shrinkTicker");
            for (String key : shrinkTag.getAllKeys()) {
                shrinkTicker.put(key, shrinkTag.getInt(key));
            }
        }
        initialWaitTicker.clear();
        if (tag.contains("initialWaitTicker")) {
            CompoundNBT waitTag = tag.getCompound("initialWaitTicker");
            for (String key : waitTag.getAllKeys()) {
                initialWaitTicker.put(key, waitTag.getInt(key));
            }
        }
    }
}
