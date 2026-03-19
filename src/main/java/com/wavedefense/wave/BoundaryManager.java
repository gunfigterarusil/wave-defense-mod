package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Перевірка кордону локації: частинки, наслідки (TIMER_SURRENDER / DAMAGE / TELEPORT_BACK / INSTANT_SURRENDER).
 */
public class BoundaryManager {

    private final WaveContext ctx;
    private int tickCounter = 0;

    public BoundaryManager(WaveContext ctx) { this.ctx = ctx; }

    public void tick(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;
        tickCounter++;

        // Частинки та шкода — раз на секунду
        boolean doSecond = (tickCounter % 20 == 0);
        if (doSecond) tickBorderParticles();

        for (Map.Entry<UUID, PlayerWaveData> e : ctx.playerData.entrySet()) {
            UUID uid = e.getKey();
            PlayerWaveData data = e.getValue();
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (!loc.isLocationBoundaryEnabled()) continue;

            BlockPos center = loc.getPlayerSpawn();
            if (center == null) continue;

            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;
            if (player.isSpectator()) continue;

            double dist = Math.sqrt(player.blockPosition().distSqr(center));
            boolean outside = dist > loc.getLocationBoundaryRadius();

            if (outside) {
                applyConsequence(wm, player, uid, loc, doSecond);
            } else {
                // Повернувся — скидаємо таймер
                if (ctx.leaveCountdownTicks.containsKey(uid)) {
                    ctx.leaveCountdownTicks.remove(uid);
                    clearTitle(player);
                }
            }
        }
    }

    // ── Наслідки ────────────────────────────────────────────────────────

    private void applyConsequence(WaveManager wm, ServerPlayer player, UUID uid, Location loc, boolean doSecond) {
        switch (loc.getBoundaryConsequence()) {

            case TIMER_SURRENDER -> {
                int ticks = ctx.leaveCountdownTicks.getOrDefault(uid, 0);
                if (ticks <= 0) {
                    int secs = loc.getLocationLeaveTimerSec();
                    ctx.leaveCountdownTicks.put(uid, secs * 20);
                    sendBoundaryTitle(player, "§c⚠ Повертайтесь!", "§eЗалишилось: §c" + secs + " сек");
                } else {
                    int remaining = ticks - 1;
                    ctx.leaveCountdownTicks.put(uid, remaining);
                    if (remaining % 20 == 0) {
                        sendBoundaryTitle(player, "§c⚠ Ви поза зоною!", "§eПоверніться: §c" + (remaining / 20) + " сек");
                        wm.syncPlayerData(player);
                    }
                    if (remaining <= 0) {
                        ctx.leaveCountdownTicks.remove(uid);
                        wm.surrenderPlayer(player);
                    }
                }
            }

            case DAMAGE -> {
                // Шкода тільки раз на секунду
                if (doSecond) {
                    float dmg = loc.getBoundaryDamagePerSec();
                    if (dmg > 0) player.hurt(player.damageSources().magic(), dmg);
                    sendBoundaryTitle(player, "§c☠ Поза зоною безпеки!", "§c-" + dmg + " HP/сек");
                }
            }

            case TELEPORT_BACK -> {
                // Миттєва телепортація назад на точку спавну
                wm.teleportToSafeSpawn(player, loc.getPlayerSpawn(),
                    loc.getPlayerSpawnRadius());
                player.displayClientMessage(
                    Component.literal("§e↩ Вас телепортовано назад у зону гри!"), true);
                ctx.leaveCountdownTicks.remove(uid);
            }

            case INSTANT_SURRENDER -> {
                ctx.leaveCountdownTicks.remove(uid);
                wm.surrenderPlayer(player);
            }
        }
    }

    // ── Частинки кордону ────────────────────────────────────────────────

    private void tickBorderParticles() {
        if (WaveDefenseMod.getServer() == null) return;
        // Збираємо унікальні активні локації з кордоном і частинками
        Set<String> done = new HashSet<>();
        for (PlayerWaveData data : ctx.playerData.values()) {
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (!loc.isLocationBoundaryEnabled()) continue;
            if (!loc.isBoundaryParticlesEnabled()) continue;
            if (!done.add(loc.getName())) continue;
            if (loc.getPlayerSpawn() == null) continue;

            // Знаходимо world через першого гравця локації
            List<ServerPlayer> inLoc = WaveDefenseMod.getServer().getPlayerList()
                .getPlayers().stream()
                .filter(p -> {
                    PlayerWaveData d = ctx.playerData.get(p.getUUID());
                    return d != null && d.getCurrentLocation() != null
                        && d.getCurrentLocation().getName().equals(loc.getName());
                })
                .toList();
            if (inLoc.isEmpty()) continue;
            ServerLevel world = inLoc.get(0).serverLevel();
            spawnBorderRing(world, loc);
        }
    }

    private void spawnBorderRing(ServerLevel world, Location loc) {
        ParticleOptions particle = resolveParticle(loc.getBoundaryParticleType());
        int radius   = loc.getLocationBoundaryRadius();
        int height   = loc.getBoundaryParticleHeight();
        int count    = loc.getBoundaryParticleCount();
        BlockPos ctr = loc.getPlayerSpawn();

        int steps = Math.max(8, (int)(2 * Math.PI * radius / 2));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = ctr.getX() + 0.5 + radius * Math.cos(angle);
            double pz = ctr.getZ() + 0.5 + radius * Math.sin(angle);
            for (int dy = 0; dy < height; dy++) {
                world.sendParticles(particle, px, ctr.getY() + dy, pz,
                    1, 0, 0, 0, 0.01);
            }
        }
    }

    // ── Заголовки ────────────────────────────────────────────────────────

    private void sendBoundaryTitle(ServerPlayer p, String title, String subtitle) {
        p.connection.send(new ClientboundSetTitleTextPacket(
            net.minecraft.network.chat.Component.literal(title)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(
            net.minecraft.network.chat.Component.literal(subtitle)));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
    }

    private void clearTitle(ServerPlayer p) {
        p.connection.send(new ClientboundClearTitlesPacket(true));
    }

    // ── Утиліти ──────────────────────────────────────────────────────────

    public static ParticleOptions resolveParticle(String id) {
        if (id == null || id.isBlank()) return ParticleTypes.SMOKE;
        try {
            var type = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (type instanceof net.minecraft.core.particles.SimpleParticleType spt) return spt;
        } catch (Exception ignored) {}
        return switch (id) {
            case "minecraft:flame"          -> ParticleTypes.FLAME;
            case "minecraft:smoke"          -> ParticleTypes.SMOKE;
            case "minecraft:crit"           -> ParticleTypes.CRIT;
            case "minecraft:large_smoke"    -> ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal"         -> ParticleTypes.PORTAL;
            case "minecraft:enchant"        -> ParticleTypes.ENCHANT;
            case "minecraft:end_rod"        -> ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame"-> ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:snowflake"      -> ParticleTypes.SNOWFLAKE;
            case "minecraft:dripping_lava"  -> ParticleTypes.DRIPPING_LAVA;
            case "minecraft:dust"           -> new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0f, 0f), 1.0f);
            default                         -> ParticleTypes.SMOKE;
        };
    }
}
