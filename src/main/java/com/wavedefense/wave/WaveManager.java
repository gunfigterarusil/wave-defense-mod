package com.wavedefense.wave;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.GameStats;
import com.wavedefense.data.Location;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.wave.BattleRoyaleManager;
import com.wavedefense.wave.BoundaryManager;
import com.wavedefense.wave.PortalManager;
import com.wavedefense.wave.TriggerEvaluator;
import com.wavedefense.wave.PvpRoundManager;
import com.wavedefense.data.PvpRoundState;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import com.wavedefense.data.LootSpawn;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.network.packets.SyncLocationDataPacket;
import com.wavedefense.network.packets.SyncPlayerDataPacket;
import com.wavedefense.network.packets.SyncTeammatesPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.entity.MobEntity;
import net.minecraft.world.GameType;
import javax.annotation.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основний менеджер хвиль — координує запуск хвиль, трекає стан гравців,
 * керує PvP, порталами, кордонами та тригерами.
 *
 * <p>Делегує lifecycle-операції {@link SessionManager}, а серіалізацію —
 * {@link WaveContext} і підменеджерам.
 */
public class WaveManager {

    // ── PvP respawn delay ─────────────────────────────────────────────────
    /** Holds the countdown until a delayed PvP respawn fires for a player. */
    public static final class PendingRespawnData {
        public final com.wavedefense.data.PvpSpawnPoint spawnPoint; // may be null
        public int ticksRemaining;
        public PendingRespawnData(com.wavedefense.data.PvpSpawnPoint spawnPoint, int ticksRemaining) {
            this.spawnPoint = spawnPoint;
            this.ticksRemaining = ticksRemaining;
        }
    }
    /** UUID → pending delayed respawn. Transient; not persisted to NBT. */
    public final Map<UUID, PendingRespawnData> pendingPvpRespawns = new ConcurrentHashMap<>();

    public final WaveContext waveCtx;
    public final Map<UUID, PlayerWaveData> playerData;
    public final PvpRoundManager pvpMgr;
    public final BoundaryManager boundaryMgr;
    public final PortalManager portalMgr;
    public final BattleRoyaleManager brManager;
    public final TriggerEvaluator triggerEval;
    public final SessionManager sessionMgr;
    public final MobSpawnManager mobSpawnMgr;
    public final WaveAutoScaler autoScaler;
    public final InfoPanelManager infoPanelMgr;
    public final ZoneActivationManager zoneMgr;
    public final CapturePointManager captureMgr;

    public final Map<UUID, Integer> leaveCountdownTicks;
    public final Map<UUID, Long> reEntryCooldowns;

    public WaveManager() {
        this.waveCtx = new WaveContext();
        this.playerData = waveCtx.playerData;
        this.leaveCountdownTicks = waveCtx.leaveCountdownTicks;
        this.reEntryCooldowns = waveCtx.reEntryCooldowns;
        this.pvpMgr = new PvpRoundManager(waveCtx);
        this.boundaryMgr = new BoundaryManager(waveCtx);
        this.portalMgr = new PortalManager(waveCtx);
        this.brManager = new BattleRoyaleManager();
        this.triggerEval = new TriggerEvaluator(waveCtx);
        this.sessionMgr = new SessionManager(waveCtx);
        this.mobSpawnMgr = new MobSpawnManager(waveCtx);
        this.autoScaler = new WaveAutoScaler();
        this.infoPanelMgr = new InfoPanelManager(waveCtx);
        this.zoneMgr = new ZoneActivationManager(waveCtx);
        this.captureMgr = new CapturePointManager(waveCtx);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Player lifecycle
    // ──────────────────────────────────────────────────────────────────────

    public void addPlayerToLocation(ServerPlayerEntity player, Location location) {
        sessionMgr.addPlayer(player, location, this);
    }

    public void surrenderPlayer(ServerPlayerEntity player) {
        sessionMgr.surrender(player, this);
    }

    public void triggerVictory(String locationName) {
        sessionMgr.triggerVictory(locationName, this);
    }

    public void endSessionForLocation(String locationName) {
        sessionMgr.endSession(locationName,
            new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.all_waves_complete"), true, this);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Teleport & spawn helpers
    // ──────────────────────────────────────────────────────────────────────

    public void teleportToSafeSpawn(ServerPlayerEntity player, BlockPos pos, int radius) {
        // H-1 fix: apply scatter radius so all players don't stack on the same block.
        // Uses sqrt(rand)*radius for a uniform-area distribution inside the circle.
        if (radius > 0) {
            double angle = Math.random() * 2 * Math.PI;
            double dist  = Math.sqrt(Math.random()) * radius;
            player.teleportTo(pos.getX() + 0.5 + dist * Math.cos(angle),
                              pos.getY(),
                              pos.getZ() + 0.5 + dist * Math.sin(angle));
        } else {
            player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
    }

    public void teleportToSpawnPoint(ServerPlayerEntity player, PvpSpawnPoint spawnPoint) {
        if (spawnPoint == null) return;
        int radius = spawnPoint.getSpawnRadius();
        BlockPos pos = spawnPoint.getPos();
        // H-1 fix: respect per-spawn-point radius (same formula as teleportToSafeSpawn)
        if (radius > 0) {
            double angle = Math.random() * 2 * Math.PI;
            double dist  = Math.sqrt(Math.random()) * radius;
            player.teleportTo(pos.getX() + 0.5 + dist * Math.cos(angle),
                              pos.getY(),
                              pos.getZ() + 0.5 + dist * Math.sin(angle));
        } else {
            player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
    }

    public void removeWaitEffects(ServerPlayerEntity player) {
        player.removeEffect(Effects.MOVEMENT_SLOWDOWN);
        player.removeEffect(Effects.BLINDNESS);
    }

    public void setSpectator(ServerPlayerEntity player, boolean spectator) {
        if (spectator) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    public void applyWaitEffects(ServerPlayerEntity player) {
        player.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 20 * 60 * 60, 10, false, false));
        player.addEffect(new EffectInstance(Effects.BLINDNESS, 20 * 60 * 60, 0, false, false));
    }

    public void reapplyWaitEffects(ServerPlayerEntity player) {
        removeWaitEffects(player);
        applyWaitEffects(player);
    }

    public void applyMobEquipment(MobEntity mob, com.wavedefense.data.WaveMob waveMob) {
        mobSpawnMgr.applyMobEquipment(mob, waveMob);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Sync & cache
    // ──────────────────────────────────────────────────────────────────────

    public void invalidatePlayersCache() {
        // Player lists are calculated from WaveContext on demand.
    }

    public void syncLocationDataToPlayer(ServerPlayerEntity player) {
        if (WaveDefenceMod.locationManager == null) return;
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, new SyncLocationDataPacket(WaveDefenceMod.locationManager.save()));
    }

    public void syncPlayerData(ServerPlayerEntity player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null) {
            data = new PlayerWaveData();
            data.setPlayerUUID(player.getUUID());
        } else if (data.getCurrentLocation() != null) {
            data.setPlayerPoints(data.getCurrentLocation().getPlayerPoints(player.getUUID()));
        }
        com.wavedefense.network.PacketHandler.sendToPlayer(player, new SyncPlayerDataPacket(data));
        // G4 fix: also sync per-player game stats so StatsScreen shows live data
        syncPlayerStats(player);
    }

    /**
     * G4 fix: sends the session's GameStats to a single player.
     * Called after mob kills, wave completions, and location joins so the client
     * StatsScreen always shows up-to-date numbers.
     */
    public void syncPlayerStats(ServerPlayerEntity player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;
        LocationSession sess = waveCtx.getSession(data.getCurrentLocation().getName());
        if (sess == null || sess.stats == null) return;
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, new com.wavedefense.network.packets.SyncStatsPacket(sess.stats));
    }

    public void syncTeammates(String locationName) {
        List<ServerPlayerEntity> players = getPlayersInLocation(locationName);
        Location loc = WaveDefenceMod.locationManager != null
            ? WaveDefenceMod.locationManager.getLocation(locationName) : null;

        for (ServerPlayerEntity viewer : players) {
            String viewerTeam = loc != null ? loc.getPlayerTeam(viewer.getUUID()) : null;
            List<SyncTeammatesPacket.PlayerEntry> entries = new ArrayList<>();
            for (ServerPlayerEntity p : players) {
                String team = loc != null ? loc.getPlayerTeam(p.getUUID()) : null;
                if (loc != null && loc.isPvp() && viewerTeam != null && team != null && !viewerTeam.equals(team)) {
                    continue;
                }
                entries.add(new SyncTeammatesPacket.PlayerEntry(
                    p.getName().getString(),
                    p.getUUID(),
                    (int) Math.ceil(p.getHealth()),
                    (int) Math.ceil(p.getMaxHealth()),
                    !p.isDeadOrDying(),
                    team,
                    p.getX(), p.getY(), p.getZ(), p.yRot));
            }
            com.wavedefense.network.PacketHandler.sendToPlayer(
                viewer, SyncTeammatesPacket.build(locationName, entries));
        }
    }

    public void clearTeammatesForPlayer(ServerPlayerEntity player) {
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, SyncTeammatesPacket.build("", Collections.emptyList()));
    }

    public void clearTeammatesForAll(java.util.List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            clearTeammatesForPlayer(player);
        }
    }

    public void removeInfoPanelEntities(String locationName) {
        infoPanelMgr.removeInfoPanelEntities(locationName);
    }

    public java.util.List<ServerPlayerEntity> getPlayersInLocation(String locationName) {
        return waveCtx.getPlayersInLocation(locationName);
    }

    public void broadcastToLocation(String locationName, net.minecraft.util.text.ITextComponent message) {
        for (ServerPlayerEntity p : getPlayersInLocation(locationName)) {
            p.displayClientMessage(message, false);
        }
    }

    public void broadcastToNearby(BlockPos center, Location location, String message) {
        broadcastToNearby(center, location, new net.minecraft.util.text.StringTextComponent(message));
    }

    /** Component-overload: preserves translatability for every connected client. */
    public void broadcastToNearby(BlockPos center, Location location,
                                  net.minecraft.util.text.ITextComponent message) {
        net.minecraft.server.MinecraftServer srv = WaveDefenceMod.getServer();
        if (srv == null || center == null || message == null) return;
        final double RADIUS_SQ = 80.0 * 80.0;
        for (net.minecraft.entity.player.ServerPlayerEntity p : srv.getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(center) <= RADIUS_SQ) {
                p.displayClientMessage(message, false);
            }
        }
    }

    public void debugLog(String message) {
        WaveDefenceMod.LOGGER.info("[WaveDebug] " + message);
    }

    public void debugAdmin(String message) {
        WaveDefenceMod.LOGGER.info("[WaveAdmin] " + message);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Loot triggers
    // ──────────────────────────────────────────────────────────────────────

    public void fireLootTrigger(Location loc, net.minecraft.world.server.ServerWorld world,
                                 LootSpawn.Trigger trigger) {
        fireLootTrigger(loc, world, trigger, -1);
    }

    /**
     * @param requiredValue Pass -1 to ignore; pass >=0 to match only LootSpawns
     *                      whose stored value for this trigger equals requiredValue.
     *                      Used for WAVE_N and MOBS_KILLED_N triggers.
     */
    public void fireLootTrigger(Location loc, net.minecraft.world.server.ServerWorld world,
                                 LootSpawn.Trigger trigger, int requiredValue) {
        if (loc == null || world == null || trigger == null) return;
        List<LootSpawn> lootSpawns = loc.getLootSpawns();
        if (lootSpawns == null || lootSpawns.isEmpty()) return;
        java.util.Random rng = new java.util.Random();
        for (LootSpawn ls : lootSpawns) {
            if (!ls.hasTrigger(trigger)) continue;
            if (requiredValue >= 0 && ls.getTriggerValue(trigger) != requiredValue) continue;
            if (rng.nextInt(100) >= ls.getSpawnChance()) continue;  // chance check
            BlockPos spawnPos = ls.getPos();
            if (spawnPos == null) continue;  // no position configured for this loot spawn
            for (net.minecraft.item.ItemStack stack : ls.getItems()) {
                if (stack.isEmpty()) continue;
                for (int i = 0; i < ls.getCount(); i++) {
                    net.minecraft.entity.item.ItemEntity ie =
                        new net.minecraft.entity.item.ItemEntity(
                            world,
                            spawnPos.getX() + 0.5,
                            spawnPos.getY() + 0.5,
                            spawnPos.getZ() + 0.5,
                            stack.copy());
                    ie.setPickUpDelay(10);
                    world.addFreshEntity(ie);
                }
            }
        }
    }

    public void fireLootTriggerByName(String locationName, com.wavedefense.data.LootSpawn.Trigger trigger) {
        if (WaveDefenceMod.locationManager == null) return;
        Location loc = WaveDefenceMod.locationManager.getLocation(locationName);
        if (loc == null || WaveDefenceMod.getServer() == null) return;
        // Resolve the correct dimension from the players currently in this location
        List<net.minecraft.entity.player.ServerPlayerEntity> inLoc = getPlayersInLocation(locationName);
        net.minecraft.world.server.ServerWorld world = inLoc.isEmpty()
            ? WaveDefenceMod.getServer().overworld()
            : ((net.minecraft.world.server.ServerWorld) inLoc.get(0).level);
        fireLootTrigger(loc, world, trigger);
    }

    /** Fires loot for parameterized triggers (WAVE_N, MOBS_KILLED_N): only spawns
     *  whose stored trigger value equals {@code value} will be activated. */
    public void fireLootTriggerByNameWithValue(String locationName,
                                               com.wavedefense.data.LootSpawn.Trigger trigger,
                                               int value) {
        if (WaveDefenceMod.locationManager == null) return;
        Location loc = WaveDefenceMod.locationManager.getLocation(locationName);
        if (loc == null || WaveDefenceMod.getServer() == null) return;
        List<net.minecraft.entity.player.ServerPlayerEntity> inLoc = getPlayersInLocation(locationName);
        net.minecraft.world.server.ServerWorld world = inLoc.isEmpty()
            ? WaveDefenceMod.getServer().overworld()
            : ((net.minecraft.world.server.ServerWorld) inLoc.get(0).level);
        fireLootTrigger(loc, world, trigger, value);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Tick update
    // ──────────────────────────────────────────────────────────────────────

    public void onServerTick() {
        boundaryMgr.tick(this);
        triggerEval.tick(this);
        pvpMgr.tick(this);
        captureMgr.tick(this);
        zoneMgr.tick(this);
        brManager.tick(this);
        portalMgr.tick(this);

        // Tick all active sessions
        for (LocationSession sess : waveCtx.sessions.values()) {
            tickSession(sess);
        }

        // Update InfoPanel TextDisplay entities every second
        if (waveCtx.tickCounter % 20 == 0) {
            infoPanelMgr.tick();
        }

        // Refresh teammate HUD (HP bars + alive state) every second for every active
        // session so changes are visible in real time, not only on death / join / leave.
        if (waveCtx.tickCounter % 20 == 0) {
            for (String locName : waveCtx.sessions.keySet()) {
                try { syncTeammates(locName); }
                catch (Throwable t) { /* one bad location shouldn't kill the loop */ }
            }
            // BBox outline — particle "fence" along bbox top edges, visible to ALL players
            // in the session (admin no longer the only one who knows where the box is).
            try { BboxRenderer.tick(waveCtx); }
            catch (Throwable t) { /* never let render glitches kill the tick */ }
        }

        // Process delayed PvP respawns
        tickPendingPvpRespawns();

        waveCtx.tickCounter++;
    }

    private void tickPendingPvpRespawns() {
        if (pendingPvpRespawns.isEmpty()) return;
        net.minecraft.server.MinecraftServer srv = WaveDefenceMod.getServer();
        if (srv == null) return;
        Iterator<Map.Entry<UUID, PendingRespawnData>> it = pendingPvpRespawns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingRespawnData> entry = it.next();
            PendingRespawnData data = entry.getValue();
            data.ticksRemaining--;
            if (data.ticksRemaining > 0) continue;

            it.remove();
            ServerPlayerEntity player = srv.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            // Only proceed if still in an active PvP location
            PlayerWaveData pData = playerData.get(entry.getKey());
            if (pData == null || pData.getCurrentLocation() == null
                    || !pData.getCurrentLocation().isPvp()) continue;

            // Execute the respawn
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.setGameMode(GameType.SURVIVAL);
            if (data.spawnPoint != null) {
                teleportToSpawnPoint(player, data.spawnPoint);
            }
            player.invulnerableTime = 60;
            player.addEffect(new EffectInstance(Effects.REGENERATION, 60, 1, false, false));
            player.displayClientMessage(
                new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.respawn_continue"), true);
        }
    }

    /** Schedules a delayed PvP respawn for {@code playerId}. */
    public void schedulePvpRespawn(UUID playerId, @Nullable com.wavedefense.data.PvpSpawnPoint spawnPoint, int delaySeconds) {
        pendingPvpRespawns.put(playerId, new PendingRespawnData(spawnPoint, Math.max(1, delaySeconds * 20)));
    }

    /** Cancels any pending delayed respawn for {@code playerId}. */
    public void cancelPvpRespawn(UUID playerId) {
        pendingPvpRespawns.remove(playerId);
    }

    public void tick() {
        onServerTick();
    }

    private void tickSession(LocationSession sess) {
        // Session tick logic - delegate to LocationSession
        if (WaveDefenceMod.locationManager == null) return;
        Location location = WaveDefenceMod.locationManager.getLocation(sess.locationName);
        if (location != null) {
            sess.tick(this, location);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Player data management
    // ──────────────────────────────────────────────────────────────────────

    public MobSpawnManager getMobSpawnManager() {
        return mobSpawnMgr;
    }

    public WaveAutoScaler getAutoScaler() {
        return autoScaler;
    }

    public PlayerWaveData getPlayerData(UUID playerUuid) {
        return playerData.get(playerUuid);
    }

    public int getCurrentWaveForLocation(String locationName) {
        LocationSession sess = waveCtx.getSession(locationName);
        return sess != null ? sess.currentWave : 1;
    }

    public int getAutoBalancedSpawnIndex(Location location, UUID playerUuid) {
        return pvpMgr.getAutoBalancedSpawnIndex(location, playerUuid);
    }

    public void addPlayerToPvpLocation(ServerPlayerEntity player, Location location, int spawnIdx) {
        pvpMgr.addPlayerToPvpLocation(this, player, location, spawnIdx);
    }

    public void exitPvpLocation(ServerPlayerEntity player) {
        surrenderPlayer(player);
    }

    public PvpRoundState getPvpState(String locationName) {
        return pvpMgr.getPvpState(locationName);
    }

    public Set<UUID> getPvpPendingRespawn() {
        return pvpMgr.getPvpPendingRespawn();
    }

    public PlayerBackup consumePendingDeathRestore(UUID playerUuid) {
        return waveCtx.pendingDeathRestores.remove(playerUuid);
    }

    public BlockPos getRandomSpawnPoint(Location location) {
        return mobSpawnMgr.getRandomSpawnPoint(location);
    }

    public void broadcastLocationData() {
        if (WaveDefenceMod.getServer() == null) return;
        for (ServerPlayerEntity player : WaveDefenceMod.getServer().getPlayerList().getPlayers()) {
            syncLocationDataToPlayer(player);
        }
    }

    public void onMobKilled(ServerPlayerEntity player, MobEntity mob) {
        // Get the location name from the mob's persistent data
        String locationName = mob.getPersistentData().getString("location");
        if (locationName.isEmpty()) {
            return; // Not a wave mob
        }

        // Get the session for this location
        LocationSession sess = waveCtx.getSession(locationName);
        if (sess == null) {
            return; // No active session
        }

        // Remove the mob from spawned mobs list
        UUID mobUuid = mob.getUUID();
        boolean wasTracked = sess.spawnedMobs.remove(mobUuid);

        // Increment kill counters
        if (wasTracked) {
            sess.mobsKilled++;
            // Fire loot triggers for every mob kill
            fireLootTriggerByName(locationName, LootSpawn.Trigger.MOB_KILL);
            fireLootTriggerByNameWithValue(locationName, LootSpawn.Trigger.MOBS_KILLED_N, sess.mobsKilled);

            // Update player stats
            GameStats stats = sess.stats;
            if (stats != null) {
                stats.incrementMobsKilled();
                // G4 fix: push updated stats to the killing player immediately
                syncPlayerStats(player);
            }

            // Update player-specific stats
            PlayerWaveData playerData = getPlayerData(player.getUUID());
            if (playerData != null) {
                // Award points for the kill
                int points = mob.getPersistentData().getInt("points");
                if (points > 0) {
                    Location loc = WaveDefenceMod.locationManager.getLocation(locationName);
                    if (loc != null) {
                        loc.addPoints(player.getUUID(), points);
                    }
                }
            }

            // Record wave metrics for auto-scaling
            WaveAutoScaler.WaveMetrics metrics = autoScaler.getCurrentWaveMetrics();
            if (metrics != null) {
                metrics.enemiesKilled++;
                metrics.totalEnemies = sess.waveStartMobCount;
            }

            // Check for HALF_MOBS_DEAD trigger
            if (!sess.halfMobsTriggered && sess.waveStartMobCount > 0) {
                int killed = sess.mobsKilled;
                int total = sess.waveStartMobCount;
                if (killed * 2 >= total) {
                    sess.halfMobsTriggered = true;
                    // Fire HALF_MOBS_DEAD as a custom trigger event
                    fireLocationTrigger(player, WaveTrigger.MOBS_REMAINING_LOW);
                    fireLootTriggerByName(locationName, LootSpawn.Trigger.HALF_MOBS_DEAD);
                }
            }

            // Check if all mobs for trigger waves are dead
            for (Map.Entry<String, Set<UUID>> entry : sess.triggerMobs.entrySet()) {
                if (entry.getValue().remove(mobUuid)) {
                    // This was a trigger mob - check if all trigger mobs for this wave are dead
                    if (entry.getValue().isEmpty()) {
                        // All trigger mobs for this trigger are dead
                        String triggerKey = entry.getKey();
                        // Extract wave index from key (format: "trigger_N")
                        try {
                            int waveIndex = Integer.parseInt(triggerKey.substring("trigger_".length()));
                            // Trigger mob keys use 0-based waveIndex ("trigger_0", "trigger_1", …)
                            Location loc = WaveDefenceMod.locationManager.getLocation(locationName);
                            if (loc != null && waveIndex >= 0 && waveIndex < loc.getWaves().size()) {
                                WaveConfig waveConfig = loc.getWaves().get(waveIndex);
                                if (waveConfig.isTriggerEnabled()) {
                                    // Notify that all trigger-wave mobs are dead (e.g. log, future hooks)
                                    debugLog("All trigger mobs dead for wave " + waveIndex + " (" + waveConfig.getTriggerType() + ") in '" + locationName + "'");
                                }
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }

            // Check for wave completion (all mobs killed) - this will be picked up by tick loop
            // but we can also check here for immediate response
            if (sess.spawnedMobs.isEmpty() && sess.currentWave >= 1) {
                // All mobs are dead, check if we should progress
                Location loc = WaveDefenceMod.locationManager.getLocation(locationName);
                if (loc != null) {
                    int delay = loc.getTimeBetweenWaves();
                    if (delay == 0 && sess.currentWave <= loc.getTotalWaves()) {
                        // No delay, start next wave immediately
                        // Note: We don't call startNextWave here to avoid double-triggering
                        // The tick loop will handle it on the next tick
                    }
                }
            }
        }

        // Also check trigger mob lists for untracked mobs (safety check)
        // If we find it here, it was a trigger mob not in spawnedMobs — count the kill.
        for (Map.Entry<String, Set<UUID>> entry : sess.triggerMobs.entrySet()) {
            if (entry.getValue().remove(mobUuid)) {
                sess.mobsKilled++;
            }
        }
    }

    public void onPlayerKilledPlayer(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        pvpMgr.onPlayerKilledPlayer(this, killer, victim);
    }

    public void onPvpPlayerDeath(ServerPlayerEntity victim) {
        pvpMgr.onPvpPlayerDeath(this, victim);
    }

    public void onPvePlayerDeath(ServerPlayerEntity victim) {
        UUID playerId = victim.getUUID();
        PlayerWaveData data = playerData.get(playerId);
        if (data == null || data.getCurrentLocation() == null) return;

        Location location = data.getCurrentLocation();
        String locName = location.getName();
        triggerEval.fireWaveTriggerForPlayer(this, victim, WaveTrigger.PLAYER_DEATH);
        fireLootTriggerByName(locName, LootSpawn.Trigger.PLAYER_DEATH);

        playerData.remove(playerId);
        invalidatePlayersCache();
        PlayerBackup backup = waveCtx.playerBackups.remove(playerId);
        if (backup != null) {
            waveCtx.pendingDeathRestores.put(playerId, backup);
        }

        boolean anyLeft = playerData.values().stream()
            .anyMatch(d -> d.getCurrentLocation() != null
                && d.getCurrentLocation().getName().equals(locName));
        if (!anyLeft) {
            waveCtx.removeSession(locName);
        } else {
            syncTeammates(locName);
            for (ServerPlayerEntity p : getPlayersInLocation(locName)) syncPlayerData(p);
        }

        data.setCurrentLocation(null);
        data.setVictoryCountdownSec(0);
        syncPlayerData(victim);
        clearTeammatesForPlayer(victim);

        try {
            /* monitor not ported on 1.16.5 */;
        } catch (Exception ignored) {}
    }

    public void fireWaveTriggerForPlayer(ServerPlayerEntity player, WaveTrigger trigger) {
        triggerEval.fireWaveTriggerForPlayer(this, player, trigger);
    }

    public void fireLocationTrigger(ServerPlayerEntity player, WaveTrigger trigger) {
        triggerEval.fireLocationTrigger(this, player, trigger);
    }

    /**
     * Fires a wave trigger for all players in a location.
     * Used for wave-specific triggers like trigger mob completion.
     */
    private void fireWaveTriggerForLocation(String locationName, WaveTrigger trigger) {
        List<ServerPlayerEntity> players = getPlayersInLocation(locationName);
        for (ServerPlayerEntity player : players) {
            fireLocationTrigger(player, trigger);
        }
        // Mark as recently fired for AND-condition support
        waveCtx.markRecentlyFired(locationName, trigger);
    }

    public boolean canPvpAttack(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        return pvpMgr.canPvpAttack(attacker, target);
    }

    public void onPvpHit(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        pvpMgr.onPvpHit(this, attacker, victim);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Серіалізація стану WaveManager для резервного копіювання.
     * Зберігає: сесії, стан гравців, пвп-стан, кордони, портал тощо.
     */
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        // Session data
        tag.put("sessions", waveCtx.saveSessions());
        // Player data
        ListNBT playerList = new ListNBT();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            CompoundNBT pTag = new CompoundNBT();
            pTag.putUUID("uuid", entry.getKey());
            pTag.put("data", entry.getValue().saveClientData());
            playerList.add(pTag);
        }
        tag.put("players", playerList);
        // PvP manager state
        tag.put("pvp", pvpMgr.save());
        // Boundary/leave countdowns
        ListNBT leaveList = new ListNBT();
        for (Map.Entry<UUID, Integer> entry : leaveCountdownTicks.entrySet()) {
            CompoundNBT eTag = new CompoundNBT();
            eTag.putUUID("uuid", entry.getKey());
            eTag.putInt("ticks", entry.getValue());
            leaveList.add(eTag);
        }
        tag.put("leaveCountdowns", leaveList);
        // Re-entry cooldowns
        ListNBT cooldownList = new ListNBT();
        for (Map.Entry<UUID, Long> entry : reEntryCooldowns.entrySet()) {
            CompoundNBT eTag = new CompoundNBT();
            eTag.putUUID("uuid", entry.getKey());
            eTag.putLong("expiry", entry.getValue());
            cooldownList.add(eTag);
        }
        tag.put("reEntryCooldowns", cooldownList);
        // Zone manager state
        tag.put("zoneStates", boundaryMgr.save());
        // Portal manager state
        tag.put("portalStates", portalMgr.save());
        // Battle royale state
        tag.put("brState", brManager.save());
        // Trigger evaluator cooldowns
        tag.put("triggerCooldowns", triggerEval.save());
        // Auto scaler state
        tag.put("autoScaler", autoScaler.save());
        // Tick counter
        tag.putInt("tickCounter", waveCtx.tickCounter);
        return tag;
    }

    /**
     * Відновлення стану WaveManager з резервної копії.
     */
    public void load(CompoundNBT tag) {
        waveCtx.clear();
        playerData.clear();
        leaveCountdownTicks.clear();
        reEntryCooldowns.clear();
        // Session data
        if (tag.contains("sessions")) {
            waveCtx.loadSessions(tag.getList("sessions", 10));
        }
        // Player data
        if (tag.contains("players")) {
            ListNBT playerList = tag.getList("players", 10);
            for (int i = 0; i < playerList.size(); i++) {
                CompoundNBT pTag = playerList.getCompound(i);
                UUID uuid = pTag.getUUID("uuid");
                PlayerWaveData data = new PlayerWaveData();
                data.loadClientData(pTag.getCompound("data"));
                playerData.put(uuid, data);
            }
        }
        // PvP manager state
        if (tag.contains("pvp")) {
            pvpMgr.load(tag.getCompound("pvp"));
        }
        // Leave countdowns
        if (tag.contains("leaveCountdowns")) {
            ListNBT leaveList = tag.getList("leaveCountdowns", 10);
            for (int i = 0; i < leaveList.size(); i++) {
                CompoundNBT eTag = leaveList.getCompound(i);
                leaveCountdownTicks.put(eTag.getUUID("uuid"), eTag.getInt("ticks"));
            }
        }
        // Re-entry cooldowns
        if (tag.contains("reEntryCooldowns")) {
            ListNBT cooldownList = tag.getList("reEntryCooldowns", 10);
            for (int i = 0; i < cooldownList.size(); i++) {
                CompoundNBT eTag = cooldownList.getCompound(i);
                reEntryCooldowns.put(eTag.getUUID("uuid"), eTag.getLong("expiry"));
            }
        }
        // Zone manager state
        if (tag.contains("zoneStates")) {
            boundaryMgr.load(tag.getCompound("zoneStates"));
        }
        // Portal manager state
        if (tag.contains("portalStates")) {
            portalMgr.load(tag.getCompound("portalStates"));
        }
        // Battle royale state
        if (tag.contains("brState")) {
            brManager.load(tag.getCompound("brState"));
        }
        // Trigger evaluator cooldowns
        if (tag.contains("triggerCooldowns")) {
            triggerEval.load(tag.getCompound("triggerCooldowns"));
        }
        // Auto scaler state — use loadFrom() to update the final field in-place
        if (tag.contains("autoScaler")) {
            autoScaler.loadFrom(tag.getCompound("autoScaler"));
        }
        // Tick counter
        waveCtx.tickCounter = tag.getInt("tickCounter");
    }

    /**
     * Серіалізація стану конкретної локації для інкрементного бекапу.
     */
    @Nullable
    public CompoundNBT saveLocationState(String locationName) {
        LocationSession sess = waveCtx.getSession(locationName);
        if (sess == null) return null;
        CompoundNBT tag = new CompoundNBT();
        tag.put("session", sess.save());
        // Player states for this location
        ListNBT playerList = new ListNBT();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null
                    && entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                CompoundNBT pTag = new CompoundNBT();
                pTag.putUUID("uuid", entry.getKey());
                pTag.put("data", entry.getValue().saveClientData());
                playerList.add(pTag);
            }
        }
        tag.put("players", playerList);
        return tag;
    }

    /**
     * Відновлення стану конкретної локації з інкрементного бекапу.
     */
    public void loadLocationState(String locationName, CompoundNBT tag) {
        if (tag.contains("session")) {
            waveCtx.loadSession(locationName, tag.getCompound("session"));
        }
        if (tag.contains("players")) {
            ListNBT playerList = tag.getList("players", 10);
            for (int i = 0; i < playerList.size(); i++) {
                CompoundNBT pTag = playerList.getCompound(i);
                UUID uuid = pTag.getUUID("uuid");
                PlayerWaveData data = playerData.computeIfAbsent(uuid, k -> new PlayerWaveData());
                data.loadClientData(pTag.getCompound("data"));
            }
        }
    }
}
