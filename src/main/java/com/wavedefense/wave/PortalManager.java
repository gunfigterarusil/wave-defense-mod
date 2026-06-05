package com.wavedefense.wave;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Відповідає за всю логіку порталів: спавн, вхід гравця,
 * grace period, штрафні хвилі та частинки.
 *
 * Стан, спільний для всіх sub-managers, живе у {@link WaveContext}.
 * Стан, специфічний лише для порталів, живе тут.
 */
public class PortalManager {

    private static final int PORTAL_PARTICLE_RADIUS_BASE = 2;
    private static final int PORTAL_HEIGHT_BASE          = 2;

    private final WaveContext ctx;

    public PortalManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Головний тік — викликається з {@code WaveManager.tick()} щотік.
     *
     * @param wm посилання на WaveManager для виклику {@code addPlayerToLocation}
     *           та {@code applyMobEquipment}
     */
    public void tick(WaveManager wm) {
        if (WaveDefenceMod.getServer() == null) return;

        for (Location loc : WaveDefenceMod.locationManager.getAllLocations()) {
            if (!loc.isPortalEnabled()) continue;
            String name = loc.getName();

            boolean active = ctx.playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null
                    && d.getCurrentLocation().getName().equals(name));

            LocationSession s = ctx.getSession(name);

            BlockPos portalPos = (s != null) ? s.portalPosition : null;

            if (portalPos != null) {
                spawnPortalParticles(loc, portalPos);

                boolean portalOpenForLate = false;
                if (s.portalOpenUntilMs != 0L) {
                    if (System.currentTimeMillis() > s.portalOpenUntilMs) {
                        s.portalOpenUntilMs = 0L;
                        s.portalPosition = null;
                        broadcastNearPortal(portalPos, name,
                            new TranslationTextComponent("wavedefense.portal.closed", name));
                        continue;
                    }
                    portalOpenForLate = true;
                }

                for (ServerPlayerEntity p : WaveDefenceMod.getServer().getPlayerList().getPlayers()) {
                    if (ctx.playerData.containsKey(p.getUUID())) continue;
                    double dist = p.blockPosition().distSqr(portalPos);
                    if (dist <= 4) {
                        if (portalOpenForLate) {
                            wm.addPlayerToLocation(p, loc);
                            p.displayClientMessage(
                                new TranslationTextComponent("wavedefense.portal.joined_active", loc.getName()), false);
                        } else {
                            enterPortal(wm, p, loc, portalPos);
                        }
                        break;
                    }
                }

                if (!portalOpenForLate) {
                    closePortalIfGraceExpired(name, portalPos);
                }

                if (!active) {
                    if (loc.getPortalPenaltyWave() == -1) {
                        Set<UUID> prevMobs = s.portalPenaltyMobs;
                        if (!prevMobs.isEmpty()) {
                            ServerWorld checkWorld = (ServerWorld)
                                WaveDefenceMod.getServer().getLevel(net.minecraft.world.World.OVERWORLD);
                            if (checkWorld != null) {
                                prevMobs.removeIf(uuid -> {
                                    net.minecraft.entity.Entity ent = checkWorld.getEntity(uuid);
                                    return ent == null || !ent.isAlive();
                                });
                            }
                            if (!prevMobs.isEmpty()) continue;
                        }
                    }
                    int timer = s.portalPenaltyTimer > 0
                        ? s.portalPenaltyTimer
                        : loc.getPortalPenaltyTimerSec() * 20;
                    timer--;
                    if (timer <= 0) {
                        firePenaltyWaveAtPortal(wm, loc, portalPos);
                        s.portalPenaltyTimer = loc.getPortalPenaltyTimerSec() * 20;
                    } else {
                        s.portalPenaltyTimer = timer;
                        if (timer % (20 * 15) == 0) {
                            int secsLeft = timer / 20;
                            broadcastNearPortal(portalPos, name,
                                new TranslationTextComponent("wavedefense.portal.penalty_warning", name, secsLeft));
                        }
                    }
                }

            } else {
                int respawn = (s != null) ? s.portalRespawnTimer : -1;
                if (respawn < 0) {
                    spawnPortalAtRandom(loc);
                } else if (respawn > 0) {
                    s.portalRespawnTimer = respawn - 1;
                } else {
                    spawnPortalAtRandom(loc);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — spawn / enter
    // ════════════════════════════════════════════════════════════════════

    private void spawnPortalAtRandom(Location loc) {
        // Guard: do not spawn a second portal while one is already active
        LocationSession existingS = ctx.getSession(loc.getName());
        if (existingS != null && existingS.portalPosition != null) return;

        if (WaveDefenceMod.getServer() == null) return;
        ServerWorld world = (ServerWorld)
            WaveDefenceMod.getServer().getLevel(net.minecraft.world.World.OVERWORLD);
        if (world == null) return;

        List<ServerPlayerEntity> all = WaveDefenceMod.getServer().getPlayerList().getPlayers();
        if (all.isEmpty()) return;
        Random rng = new Random();
        ServerPlayerEntity target = all.get(rng.nextInt(all.size()));
        int ox = rng.nextInt(61) - 30;
        int oz = rng.nextInt(61) - 30;
        BlockPos base = target.blockPosition().offset(ox, 0, oz);
        // В5: skip if the target chunk is not loaded — getHeightmapPos returns Y=0 for unloaded chunks
        if (!world.isLoaded(base)) return;
        int y = world.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, base).getY();
        BlockPos portalPos = new BlockPos(base.getX(), y + 1, base.getZ());

        LocationSession s = ctx.getOrCreateSession(loc.getName(), loc);
        s.portalPosition = portalPos;
        s.portalPenaltyTimer = loc.getPortalPenaltyTimerSec() * 20;

        for (ServerPlayerEntity p : WaveDefenceMod.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(
                new TranslationTextComponent("wavedefense.portal.appeared", loc.getName()), false);
        }
    }

    private void enterPortal(WaveManager wm, ServerPlayerEntity player, Location loc, BlockPos portalPos) {
        String name = loc.getName();
        LocationSession s = ctx.getSession(name);
        if (s == null) return;

        int enteredCount = s.portalEnteredCount;

        if (enteredCount == 0) {
            s.portalEntryPosition = portalPos;
            s.portalFirstPlayerEntered = true;
            int graceMs = loc.getPortalOpenAfterStartSec() >= 0
                ? loc.getPortalOpenAfterStartSec() * 1000
                : 30_000;
            if (graceMs == 0) graceMs = 1;
            s.portalGraceEndTime = System.currentTimeMillis() + graceMs;
            if (loc.getPortalOpenAfterStartSec() > 0) {
                s.portalOpenUntilMs =
                    System.currentTimeMillis() + loc.getPortalOpenAfterStartSec() * 1000L;
            }
            s.portalEnteredCount = 1;

            wm.addPlayerToLocation(player, loc);
            int graceDisplay = graceMs / 1000;
            if (graceDisplay > 0) {
                player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.portal_entered_grace",
                        loc.getName(), graceDisplay), false);
                broadcastNearPortal(portalPos, name,
                    new TranslationTextComponent("wavedefense.msg.portal_nearby_entered",
                        player.getName(), graceDisplay));
            } else {
                player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.portal_entered", loc.getName()),
                    false);
            }
        } else {
            long grace = s.portalGraceEndTime;
            if (System.currentTimeMillis() <= grace) {
                s.portalEnteredCount = enteredCount + 1;
                wm.addPlayerToLocation(player, loc);
                player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.portal_entered", loc.getName()),
                    false);
            } else {
                player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.portal_closed"), true);
            }
        }
    }

    private void closePortalIfGraceExpired(String locName, BlockPos portalPos) {
        LocationSession s = ctx.getSession(locName);
        if (s == null) return;
        if (s.portalGraceEndTime != 0L && System.currentTimeMillis() > s.portalGraceEndTime) {
            s.portalPosition = null;
            s.portalPenaltyTimer = 0;
            s.portalPenaltyWaveIndex = 0;
            // Видаляємо penalty-мобів зі світу (раніше вони залишались назавжди)
            if (!s.portalPenaltyMobs.isEmpty() && WaveDefenceMod.getServer() != null) {
                ServerWorld world = WaveDefenceMod.getServer().getLevel(net.minecraft.world.World.OVERWORLD);
                if (world != null) {
                    for (UUID uuid : s.portalPenaltyMobs) {
                        net.minecraft.entity.Entity e = world.getEntity(uuid);
                        if (e != null) e.remove();
                    }
                }
            }
            s.portalPenaltyMobs.clear();
            s.portalGraceEndTime = 0L;
            s.portalEnteredCount = 0;
            Location loc = WaveDefenceMod.locationManager.getLocation(locName);
            if (loc != null && loc.isPortalDisappearsOnComplete()) {
                s.portalRespawnTimer = loc.getPortalRespawnTimerSec() * 20;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — penalty wave
    // ════════════════════════════════════════════════════════════════════

    private void firePenaltyWaveAtPortal(WaveManager wm, Location loc, BlockPos portalPos) {
        if (WaveDefenceMod.getServer() == null) return;
        ServerWorld world = (ServerWorld)
            WaveDefenceMod.getServer().getLevel(net.minecraft.world.World.OVERWORLD);
        if (world == null || loc.getWaves().isEmpty()) return;

        LocationSession s = ctx.getSession(loc.getName());
        if (s == null) return;

        if (loc.getPortalPenaltyWave() == -1) {
            List<WaveConfig> normalWaves = loc.getWaves().stream()
                .filter(w -> !w.isTriggerEnabled())
                .collect(Collectors.toList());
            if (normalWaves.isEmpty()) return;

            Set<UUID> prevMobs = s.portalPenaltyMobs;
            if (!prevMobs.isEmpty()) {
                prevMobs.removeIf(uuid -> {
                    net.minecraft.entity.Entity e = world.getEntity(uuid);
                    return e == null || !e.isAlive();
                });
                if (!prevMobs.isEmpty()) return;
            }

            int idx = s.portalPenaltyWaveIndex;
            if (idx >= normalWaves.size()) {
                idx = 0;
                broadcastNearPortal(portalPos, loc.getName(),
                    new TranslationTextComponent("wavedefense.portal.penalty_reset", loc.getName()));
            }
            broadcastNearPortal(portalPos, loc.getName(),
                new TranslationTextComponent("wavedefense.portal.penalty_wave_seq",
                    idx + 1, normalWaves.size(), loc.getName()));
            Set<UUID> spawnedNow = new HashSet<>();
            spawnWaveAroundPos(wm, normalWaves.get(idx), loc, world, portalPos, 8, spawnedNow);
            s.portalPenaltyMobs.clear();
            s.portalPenaltyMobs.addAll(spawnedNow);
            s.portalPenaltyWaveIndex = idx + 1;
            debugLog("Portal sequential penalty wave " + (idx + 1)
                + "/" + normalWaves.size() + " for " + loc.getName());
        } else {
            int wi = loc.getPortalPenaltyWave();
            broadcastNearPortal(portalPos, loc.getName(),
                new TranslationTextComponent("wavedefense.portal.penalty_wave", loc.getName()));
            if (wi >= 0 && wi < loc.getWaves().size()) {
                // Track spawned mobs so closePortalIfGraceExpired() can discard them
                Set<UUID> spawnedNow = new HashSet<>();
                spawnWaveAroundPos(wm, loc.getWaves().get(wi), loc, world, portalPos, 8, spawnedNow);
                s.portalPenaltyMobs.clear();
                s.portalPenaltyMobs.addAll(spawnedNow);
            }
        }
    }

    private void spawnWaveAroundPos(WaveManager wm, WaveConfig wave, Location loc,
                                    ServerWorld world, BlockPos center, int radius,
                                    Set<UUID> trackSet) {
        Random rng = new Random();
        for (WaveMob waveMob : wave.getMobs()) {
            net.minecraft.entity.EntityType<?> et = ForgeRegistries.ENTITIES.getValue(waveMob.getMobType());
            if (et == null) continue;
            for (int i = 0; i < waveMob.getCount(); i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                double angle = rng.nextDouble() * 2 * Math.PI;
                double r     = rng.nextDouble() * radius;
                BlockPos sp  = center.offset(
                    (int)(r * Math.cos(angle)), 0, (int)(r * Math.sin(angle)));
                try {
                    net.minecraft.entity.MobEntity mob =
                        (net.minecraft.entity.MobEntity) et.create(world);
                    if (mob == null) continue;
                    mob.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                    mob.finalizeSpawn(world, world.getCurrentDifficultyAt(sp),
                        SpawnReason.COMMAND, null, null);
                    mob.setPersistenceRequired();
                    mob.getPersistentData().putString("location", loc.getName() + "_portal");
                    wm.applyMobEquipment(mob, waveMob);
                    world.addFreshEntity(mob);
                    if (trackSet != null) trackSet.add(mob.getUUID());
                } catch (Exception e) {
                    WaveDefenceMod.LOGGER.warn("[WaveDefense] Portal mob spawn failed for '{}' at {}: {}",
                        et.getDescriptionId(), sp, e.getMessage());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — particles / broadcast
    // ════════════════════════════════════════════════════════════════════

    private void spawnPortalParticles(Location loc, BlockPos pos) {
        if (pos == null) return;
        ServerWorld world = (ServerWorld)
            WaveDefenceMod.getServer().getLevel(net.minecraft.world.World.OVERWORLD);
        if (world == null) return;

        int waveCount = loc.getWaves().size();
        int totalMobs = loc.getWaves().stream()
            .mapToInt(w -> w.getMobs().stream().mapToInt(WaveMob::getCount).sum()).sum();
        float radiusF = Math.min(10,
            PORTAL_PARTICLE_RADIUS_BASE + waveCount * 0.3f + totalMobs * 0.02f);
        float heightF = Math.min(8,
            PORTAL_HEIGHT_BASE          + waveCount * 0.2f + totalMobs * 0.01f);
        radiusF = Math.max(2, radiusF);
        heightF = Math.max(2, heightF);

        float r = Math.min(1, totalMobs / 200f);
        // float g = 0, b = 1 - r * 0.3f;  — used for future colour API

        int steps = Math.max(16, (int)(radiusF * 12));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = pos.getX() + 0.5 + radiusF * Math.cos(angle);
            double py = pos.getY() + 1   + heightF * Math.sin(angle);
            double pz = pos.getZ() + 0.5;
            world.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, 0, 0, 0.05);
            world.sendParticles(ParticleTypes.WITCH,  px, py, pz, 1, 0, 0, 0, 0.02);
        }
        for (int fy = 0; fy < (int)(heightF * 2); fy++) {
            for (int fx = 0; fx < (int)(radiusF * 2); fx++) {
                double px = pos.getX() + 0.5 - radiusF + fx;
                double py = pos.getY() + 1   - heightF + fy;
                if (Math.pow((px - pos.getX() - 0.5) / radiusF, 2)
                    + Math.pow((py - pos.getY() - 1) / heightF, 2) <= 1.0) {
                    world.sendParticles(ParticleTypes.END_ROD,
                        px, py, pos.getZ() + 0.5, 0, 0, 0, 0, 0.01);
                }
            }
        }
    }

    private void broadcastNearPortal(BlockPos pos, String locName, String msg) {
        broadcastNearPortal(pos, locName, new StringTextComponent(msg));
    }

    private void broadcastNearPortal(BlockPos pos, String locName, ITextComponent msg) {
        if (WaveDefenceMod.getServer() == null) return;
        for (ServerPlayerEntity p : WaveDefenceMod.getServer().getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(pos) <= 2500) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Utility
    // ════════════════════════════════════════════════════════════════════

    private static void debugLog(String msg) {
        if (com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get())
            WaveDefenceMod.LOGGER.info("[WaveDefense] " + msg);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану PortalManager. */
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        // Portal state is stored in LocationSession, not here
        return tag;
    }

    /** Відновлення стану PortalManager. */
    public void load(CompoundNBT tag) {
        // No state to restore
    }
}
