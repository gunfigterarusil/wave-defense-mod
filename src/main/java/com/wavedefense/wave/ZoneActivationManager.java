package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Відповідає за автоматичну активацію зонових локацій:
 * – відлік активації коли гравці заходять у радіус,
 * – підключення гравців до локації,
 * – таймер «відкритості» зони після старту,
 * – частинки зони.
 *
 * Стан (зонові поля) живе у {@link LocationSession}; методи-утиліти
 * (debugAdmin, broadcastToNearby тощо) делегуються WaveManager.
 */
public class ZoneActivationManager {

    private final WaveContext ctx;

    public ZoneActivationManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Головний тік — викликається з {@code WaveManager.tick()} щотік.
     */
    public void tick(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;

        for (Location location : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!location.isAutoActivate() || location.isPvp()) continue;

            BlockPos center = location.getEffectiveZoneCenter();
            if (center == null) continue;

            String locName = location.getName();
            int radius = Math.max(5, location.getAutoActivateRadius());

            boolean alreadyActive = ctx.playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null
                    && d.getCurrentLocation().getName().equals(locName));

            if (alreadyActive) {
                LocationSession s = ctx.getSession(locName);
                if (s != null && s.zoneOpenUntilMs > 0) {
                    if (System.currentTimeMillis() > s.zoneOpenUntilMs) {
                        s.zoneOpenUntilMs = 0L;
                    } else {
                        Set<UUID> lateJoiners = collectPlayersInZone(location, center, radius);
                        for (UUID uid : lateJoiners) {
                            ServerPlayer lp = WaveDefenseMod.getServer()
                                .getPlayerList().getPlayer(uid);
                            if (lp != null) {
                                wm.addPlayerToLocation(lp, location);
                                lp.displayClientMessage(Component.translatable("wavedefense.auto.ви_приєдналися_до_активної_локації_value_0a929e16", locName),
                                    false);
                            }
                        }
                    }
                }
                if (s != null) {
                    s.zoneCountdownTicker = 0;
                    s.zonePlayersInRange.clear();
                    s.zoneCountdownStartMs = 0L;
                }
                if (shouldSpawnParticles(location)) {
                    spawnZoneParticlesForLocation(locName, center, radius);
                }
                continue;
            }

            Set<UUID> inRange = collectPlayersInZone(location, center, radius);

            // Get or create session to store zone players in range
            LocationSession s = ctx.getOrCreateSession(locName, location);
            s.zonePlayersInRange.clear();
            s.zonePlayersInRange.addAll(inRange);

            if (shouldSpawnParticles(location)) {
                spawnZoneParticlesForLocation(locName, center, radius);
            }

            if (inRange.isEmpty()) {
                if (s.zoneCountdownTicker > 0) {
                    s.zoneCountdownTicker = 0;
                    s.zoneCountdownStartMs = 0L;
                    wm.broadcastToNearby(center, location,
                        Component.translatable("wavedefense.zone.activation_cancelled").getString());
                }
                continue;
            }

            int activationDelay = location.getZoneActivationTimeSec();

            if (s.zoneCountdownTicker <= 0) {
                if (activationDelay <= 0) {
                    s.zoneCountdownTicker = 0;
                    s.zonePlayersInRange.clear();
                    s.zoneCountdownStartMs = 0L;
                    startZoneLocationForPlayers(wm, location, inRange);
                    continue;
                }
                s.zoneCountdownTicker = activationDelay * 20;
                s.zoneCountdownStartMs = System.currentTimeMillis();
                wm.broadcastToNearby(center, location,
                    Component.translatable("wavedefense.zone.activating_countdown", locName, activationDelay).getString());
            }

            int ticks = s.zoneCountdownTicker - 1;
            if (ticks <= 0) {
                s.zoneCountdownTicker = 0;
                s.zonePlayersInRange.clear();
                s.zoneCountdownStartMs = 0L;
                startZoneLocationForPlayers(wm, location, inRange);
            } else {
                s.zoneCountdownTicker = ticks;
                if (ticks % 20 == 0) {
                    int secsLeft = ticks / 20;
                    if (secsLeft <= 5 || secsLeft % 5 == 0) {
                        wm.broadcastToNearby(center, location,
                            Component.translatable("wavedefense.msg.zone_activating", secsLeft)
                                .getString());
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — player collection & activation
    // ════════════════════════════════════════════════════════════════════

    /** Збирає UUID гравців що знаходяться в радіусі зони та ще не в грі. */
    private Set<UUID> collectPlayersInZone(Location location, BlockPos center, int radius) {
        Set<UUID> inRange = new HashSet<>();
        for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (ctx.playerData.containsKey(p.getUUID())) continue;
            Long cdExp = ctx.reEntryCooldowns.get(p.getUUID());
            if (cdExp != null && System.currentTimeMillis() < cdExp) continue;
            double dist = p.blockPosition().distSqr(center);
            if (dist <= (double) radius * radius) inRange.add(p.getUUID());
        }
        return inRange;
    }

    /** Активує локацію для гравців із зони, налаштовує zoneOpenUntil. */
    private void startZoneLocationForPlayers(WaveManager wm, Location location,
                                             Set<UUID> playerIds) {
        wm.debugAdmin("Зона §e" + location.getName()
            + " §7— активація для " + playerIds.size() + " гравців");
        activateZoneForPlayers(wm, location, playerIds);
        if (location.getZoneOpenAfterStartSec() > 0) {
            LocationSession s = ctx.getOrCreateSession(location.getName(), location);
            s.zoneOpenUntilMs = System.currentTimeMillis() + location.getZoneOpenAfterStartSec() * 1000L;
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.zone_open",
                    location.getZoneOpenAfterStartSec()));
        }
    }

    /** Додає кожного гравця із набору до локації. Також доступний зовні пакету для тригерів. */
    void activateZoneForPlayers(WaveManager wm, Location location, Set<UUID> playerIds) {
        wm.debugAdmin("Локація §e" + location.getName()
            + " §7запускається для " + playerIds.size() + " гравців");
        wm.debugLog("Location '" + location.getName()
            + "' activated for " + playerIds.size() + " players");

        int activated = 0;
        for (UUID uid : playerIds) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;
            wm.addPlayerToLocation(player, location);
            activated++;
        }

        if (activated > 0) {
            wm.broadcastToNearby(location.getPlayerSpawn(), location,
                Component.translatable("wavedefense.zone.activated_players", location.getName(), activated).getString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — particles
    // ════════════════════════════════════════════════════════════════════

    /** Чи час спавнити частинки для цієї локації (за налаштованим інтервалом). */
    private boolean shouldSpawnParticles(Location loc) {
        int interval = loc.getZoneParticleInterval();
        if (interval <= 1) return true;
        return (ctx.tickCounter % interval) == 0;
    }

    private void spawnZoneParticlesForLocation(String locName, BlockPos center, int radius) {
        ServerLevel overworld = (ServerLevel)
            WaveDefenseMod.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        SimpleParticleType particleType = ParticleTypes.SQUID_INK;
        if (locName != null) {
            Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc != null && loc.getZoneParticleType() != null
                    && !loc.getZoneParticleType().isBlank()) {
                try {
                    ResourceLocation rl = new ResourceLocation(loc.getZoneParticleType());
                    ParticleType<?> pt = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
                    if (pt instanceof SimpleParticleType spt) particleType = spt;
                } catch (Exception ignored) {}
            }
        }

        int steps;
        float speed = 0.02f;
        if (locName != null) {
            Location loc2 = WaveDefenseMod.locationManager.getLocation(locName);
            int customCount = (loc2 != null) ? loc2.getZoneParticleCount() : 0;
            speed = (loc2 != null) ? loc2.getZoneParticleSpeed() : 0.02f;
            steps = (customCount > 0)
                ? Math.min(64, customCount)
                : Math.min(12, Math.max(6, radius * 2));
        } else {
            steps = Math.min(12, Math.max(6, radius * 2));
        }

        final float particleSpeed = speed;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = center.getX() + 0.5 + radius * Math.cos(angle);
            double pz = center.getZ() + 0.5 + radius * Math.sin(angle);
            overworld.sendParticles(particleType,
                px, center.getY() + 0.1, pz,
                1, 0, 0.1, 0, particleSpeed);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану ZoneActivationManager. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // Zone state is stored in LocationSession, not here
        return tag;
    }

    /** Відновлення стану ZoneActivationManager. */
    public void load(CompoundTag tag) {
        // No state to restore
    }
}
