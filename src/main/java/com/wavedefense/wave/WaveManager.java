package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

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
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Player lifecycle
    // ──────────────────────────────────────────────────────────────────────

    public void addPlayerToLocation(ServerPlayer player, Location location) {
        sessionMgr.addPlayer(player, location, this);
    }

    public void surrenderPlayer(ServerPlayer player) {
        sessionMgr.surrender(player, this);
    }

    public void triggerVictory(String locationName) {
        sessionMgr.triggerVictory(locationName, this);
    }

    public void endSessionForLocation(String locationName) {
        sessionMgr.endSession(locationName,
            net.minecraft.network.chat.Component.translatable("wavedefense.msg.all_waves_complete"), true, this);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Teleport & spawn helpers
    // ──────────────────────────────────────────────────────────────────────

    public void teleportToSafeSpawn(ServerPlayer player, BlockPos pos, int radius) {
        // Safe spawn logic - for now just teleport
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public void teleportToSpawnPoint(ServerPlayer player, PvpSpawnPoint spawnPoint) {
        if (spawnPoint != null) {
            player.teleportTo(spawnPoint.getPos().getX() + 0.5, spawnPoint.getPos().getY(), spawnPoint.getPos().getZ() + 0.5);
        }
    }

    public void removeWaitEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
    }

    public void setSpectator(ServerPlayer player, boolean spectator) {
        if (spectator) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    public void applyWaitEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60 * 60, 10, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 60 * 60, 0, false, false));
    }

    public void reapplyWaitEffects(ServerPlayer player) {
        removeWaitEffects(player);
        applyWaitEffects(player);
    }

    public void applyMobEquipment(Mob mob, com.wavedefense.data.WaveMob waveMob) {
        mobSpawnMgr.applyMobEquipment(mob, waveMob);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Sync & cache
    // ──────────────────────────────────────────────────────────────────────

    public void invalidatePlayersCache() {
        // Player lists are calculated from WaveContext on demand.
    }

    public void syncLocationDataToPlayer(ServerPlayer player) {
        if (WaveDefenseMod.locationManager == null) return;
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, new SyncLocationDataPacket(WaveDefenseMod.locationManager.save()));
    }

    public void syncPlayerData(ServerPlayer player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null) {
            data = new PlayerWaveData();
            data.setPlayerUUID(player.getUUID());
        } else if (data.getCurrentLocation() != null) {
            data.setPlayerPoints(data.getCurrentLocation().getPlayerPoints(player.getUUID()));
        }
        com.wavedefense.network.PacketHandler.sendToPlayer(player, new SyncPlayerDataPacket(data));
    }

    public void syncTeammates(String locationName) {
        List<ServerPlayer> players = getPlayersInLocation(locationName);
        Location loc = WaveDefenseMod.locationManager != null
            ? WaveDefenseMod.locationManager.getLocation(locationName) : null;

        for (ServerPlayer viewer : players) {
            String viewerTeam = loc != null ? loc.getPlayerTeam(viewer.getUUID()) : null;
            List<SyncTeammatesPacket.PlayerEntry> entries = new ArrayList<>();
            for (ServerPlayer p : players) {
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
                    team));
            }
            com.wavedefense.network.PacketHandler.sendToPlayer(
                viewer, SyncTeammatesPacket.build(locationName, entries));
        }
    }

    public void clearTeammatesForPlayer(ServerPlayer player) {
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, SyncTeammatesPacket.build("", Collections.emptyList()));
    }

    public void clearTeammatesForAll(java.util.List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            clearTeammatesForPlayer(player);
        }
    }

    public void removeInfoPanelEntities(String locationName) {
        infoPanelMgr.removeInfoPanelEntities(locationName);
    }

    public java.util.List<ServerPlayer> getPlayersInLocation(String locationName) {
        return waveCtx.getPlayersInLocation(locationName);
    }

    public void broadcastToLocation(String locationName, net.minecraft.network.chat.Component message) {
        for (ServerPlayer p : getPlayersInLocation(locationName)) {
            p.displayClientMessage(message, false);
        }
    }

    public void broadcastToNearby(BlockPos center, Location location, String message) {
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv == null || center == null || message == null) return;
        net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.literal(message);
        final double RADIUS_SQ = 80.0 * 80.0;
        for (net.minecraft.server.level.ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(center) <= RADIUS_SQ) {
                p.displayClientMessage(comp, false);
            }
        }
    }

    public void debugLog(String message) {
        WaveDefenseMod.LOGGER.info("[WaveDebug] " + message);
    }

    public void debugAdmin(String message) {
        WaveDefenseMod.LOGGER.info("[WaveAdmin] " + message);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Loot triggers
    // ──────────────────────────────────────────────────────────────────────

    public void fireLootTrigger(Location loc, net.minecraft.server.level.ServerLevel world,
                                 LootSpawn.Trigger trigger) {
        fireLootTrigger(loc, world, trigger, -1);
    }

    /**
     * @param requiredValue Pass -1 to ignore; pass >=0 to match only LootSpawns
     *                      whose stored value for this trigger equals requiredValue.
     *                      Used for WAVE_N and MOBS_KILLED_N triggers.
     */
    public void fireLootTrigger(Location loc, net.minecraft.server.level.ServerLevel world,
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
            for (net.minecraft.world.item.ItemStack stack : ls.getItems()) {
                if (stack.isEmpty()) continue;
                for (int i = 0; i < ls.getCount(); i++) {
                    net.minecraft.world.entity.item.ItemEntity ie =
                        new net.minecraft.world.entity.item.ItemEntity(
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
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc == null || WaveDefenseMod.getServer() == null) return;
        // Resolve the correct dimension from the players currently in this location
        List<net.minecraft.server.level.ServerPlayer> inLoc = getPlayersInLocation(locationName);
        net.minecraft.server.level.ServerLevel world = inLoc.isEmpty()
            ? WaveDefenseMod.getServer().overworld()
            : inLoc.get(0).serverLevel();
        fireLootTrigger(loc, world, trigger);
    }

    /** Fires loot for parameterized triggers (WAVE_N, MOBS_KILLED_N): only spawns
     *  whose stored trigger value equals {@code value} will be activated. */
    public void fireLootTriggerByNameWithValue(String locationName,
                                               com.wavedefense.data.LootSpawn.Trigger trigger,
                                               int value) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc == null || WaveDefenseMod.getServer() == null) return;
        List<net.minecraft.server.level.ServerPlayer> inLoc = getPlayersInLocation(locationName);
        net.minecraft.server.level.ServerLevel world = inLoc.isEmpty()
            ? WaveDefenseMod.getServer().overworld()
            : inLoc.get(0).serverLevel();
        fireLootTrigger(loc, world, trigger, value);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Tick update
    // ──────────────────────────────────────────────────────────────────────

    public void onServerTick() {
        boundaryMgr.tick(this);
        triggerEval.tick(this);
        pvpMgr.tick(this);
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

        waveCtx.tickCounter++;
    }

    public void tick() {
        onServerTick();
    }

    private void tickSession(LocationSession sess) {
        // Session tick logic - delegate to LocationSession
        Location location = WaveDefenseMod.locationManager.getLocation(sess.locationName);
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

    public void addPlayerToPvpLocation(ServerPlayer player, Location location, int spawnIdx) {
        pvpMgr.addPlayerToPvpLocation(this, player, location, spawnIdx);
    }

    public void exitPvpLocation(ServerPlayer player) {
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
        if (WaveDefenseMod.getServer() == null) return;
        for (ServerPlayer player : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            syncLocationDataToPlayer(player);
        }
    }

    public void onMobKilled(ServerPlayer player, Mob mob) {
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
            }

            // Update player-specific stats
            PlayerWaveData playerData = getPlayerData(player.getUUID());
            if (playerData != null) {
                // Award points for the kill
                int points = mob.getPersistentData().getInt("points");
                if (points > 0) {
                    Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
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
                            Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
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
                Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
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
        for (Map.Entry<String, Set<UUID>> entry : sess.triggerMobs.entrySet()) {
            entry.getValue().remove(mobUuid);
        }
    }

    public void onPlayerKilledPlayer(ServerPlayer killer, ServerPlayer victim) {
        pvpMgr.onPlayerKilledPlayer(this, killer, victim);
    }

    public void onPvpPlayerDeath(ServerPlayer victim) {
        pvpMgr.onPvpPlayerDeath(this, victim);
    }

    public void onPvePlayerDeath(ServerPlayer victim) {
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
            for (ServerPlayer p : getPlayersInLocation(locName)) syncPlayerData(p);
        }

        data.setCurrentLocation(null);
        data.setVictoryCountdownSec(0);
        syncPlayerData(victim);
        clearTeammatesForPlayer(victim);

        try {
            com.wavedefense.monitor.WaveDefenseMonitor.getInstance().onPlayerDeath(victim);
        } catch (Exception ignored) {}
    }

    public void fireWaveTriggerForPlayer(ServerPlayer player, WaveTrigger trigger) {
        triggerEval.fireWaveTriggerForPlayer(this, player, trigger);
    }

    public void fireLocationTrigger(ServerPlayer player, WaveTrigger trigger) {
        triggerEval.fireLocationTrigger(this, player, trigger);
    }

    /**
     * Fires a wave trigger for all players in a location.
     * Used for wave-specific triggers like trigger mob completion.
     */
    private void fireWaveTriggerForLocation(String locationName, WaveTrigger trigger) {
        List<ServerPlayer> players = getPlayersInLocation(locationName);
        for (ServerPlayer player : players) {
            fireLocationTrigger(player, trigger);
        }
        // Mark as recently fired for AND-condition support
        waveCtx.markRecentlyFired(locationName, trigger);
    }

    public boolean canPvpAttack(ServerPlayer attacker, ServerPlayer target) {
        return pvpMgr.canPvpAttack(attacker, target);
    }

    public void onPvpHit(ServerPlayer attacker, ServerPlayer victim) {
        pvpMgr.onPvpHit(this, attacker, victim);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Серіалізація стану WaveManager для резервного копіювання.
     * Зберігає: сесії, стан гравців, пвп-стан, кордони, портал тощо.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // Session data
        tag.put("sessions", waveCtx.saveSessions());
        // Player data
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", entry.getKey());
            pTag.put("data", entry.getValue().saveClientData());
            playerList.add(pTag);
        }
        tag.put("players", playerList);
        // PvP manager state
        tag.put("pvp", pvpMgr.save());
        // Boundary/leave countdowns
        ListTag leaveList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : leaveCountdownTicks.entrySet()) {
            CompoundTag eTag = new CompoundTag();
            eTag.putUUID("uuid", entry.getKey());
            eTag.putInt("ticks", entry.getValue());
            leaveList.add(eTag);
        }
        tag.put("leaveCountdowns", leaveList);
        // Re-entry cooldowns
        ListTag cooldownList = new ListTag();
        for (Map.Entry<UUID, Long> entry : reEntryCooldowns.entrySet()) {
            CompoundTag eTag = new CompoundTag();
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
    public void load(CompoundTag tag) {
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
            ListTag playerList = tag.getList("players", 10);
            for (int i = 0; i < playerList.size(); i++) {
                CompoundTag pTag = playerList.getCompound(i);
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
            ListTag leaveList = tag.getList("leaveCountdowns", 10);
            for (int i = 0; i < leaveList.size(); i++) {
                CompoundTag eTag = leaveList.getCompound(i);
                leaveCountdownTicks.put(eTag.getUUID("uuid"), eTag.getInt("ticks"));
            }
        }
        // Re-entry cooldowns
        if (tag.contains("reEntryCooldowns")) {
            ListTag cooldownList = tag.getList("reEntryCooldowns", 10);
            for (int i = 0; i < cooldownList.size(); i++) {
                CompoundTag eTag = cooldownList.getCompound(i);
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
        // Auto scaler state
        if (tag.contains("autoScaler")) {
            autoScaler.load(tag.getCompound("autoScaler"));
        }
        // Tick counter
        waveCtx.tickCounter = tag.getInt("tickCounter");
    }

    /**
     * Серіалізація стану конкретної локації для інкрементного бекапу.
     */
    @Nullable
    public CompoundTag saveLocationState(String locationName) {
        LocationSession sess = waveCtx.getSession(locationName);
        if (sess == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.put("session", sess.save());
        // Player states for this location
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null
                    && entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                CompoundTag pTag = new CompoundTag();
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
    public void loadLocationState(String locationName, CompoundTag tag) {
        if (tag.contains("session")) {
            waveCtx.loadSession(locationName, tag.getCompound("session"));
        }
        if (tag.contains("players")) {
            ListTag playerList = tag.getList("players", 10);
            for (int i = 0; i < playerList.size(); i++) {
                CompoundTag pTag = playerList.getCompound(i);
                UUID uuid = pTag.getUUID("uuid");
                PlayerWaveData data = playerData.computeIfAbsent(uuid, k -> new PlayerWaveData());
                data.loadClientData(pTag.getCompound("data"));
            }
        }
    }
}
