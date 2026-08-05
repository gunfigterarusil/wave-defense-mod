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
import com.wavedefense.data.LeaderboardManager;
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

    public void teleportToSpawnPoint(ServerPlayer player, PvpSpawnPoint spawnPoint) {
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
        // G4 fix: also sync per-player game stats so StatsScreen shows live data
        syncPlayerStats(player);
    }

    /**
     * G4 fix: sends the session's GameStats to a single player.
     * Called after mob kills, wave completions, and location joins so the client
     * StatsScreen always shows up-to-date numbers.
     */
    public void syncPlayerStats(ServerPlayer player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;
        LocationSession sess = waveCtx.getSession(data.getCurrentLocation().getName());
        if (sess == null || sess.stats == null) return;
        com.wavedefense.network.PacketHandler.sendToPlayer(
            player, new com.wavedefense.network.packets.SyncStatsPacket(sess.stats));
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
                    team,
                    p.getX(), p.getY(), p.getZ(), p.getYRot()));
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
        broadcastToNearby(center, location, net.minecraft.network.chat.Component.literal(message));
    }

    /** Component-overload: preserves translatability for every connected client. */
    public void broadcastToNearby(BlockPos center, Location location,
                                  net.minecraft.network.chat.Component message) {
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv == null || center == null || message == null) return;
        final double RADIUS_SQ = 80.0 * 80.0;
        for (net.minecraft.server.level.ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(center) <= RADIUS_SQ) {
                p.displayClientMessage(message, false);
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
        if (WaveDefenseMod.locationManager == null) return;
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
        if (WaveDefenseMod.locationManager == null) return;
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
        // Each subsystem is isolated: a bad location config — a malformed particle id, a
        // null spawn, one corrupt wave — used to propagate straight out of the Forge tick
        // event and take the server down with it. Now one broken arena degrades itself
        // and the rest of the server keeps running.
        safeTick("boundary", () -> boundaryMgr.tick(this));
        safeTick("triggers", () -> triggerEval.tick(this));
        safeTick("pvp",      () -> pvpMgr.tick(this));
        safeTick("capture",  () -> captureMgr.tick(this));
        safeTick("zone",     () -> zoneMgr.tick(this));
        safeTick("br",       () -> brManager.tick(this));
        safeTick("portal",   () -> portalMgr.tick(this));

        // Tick all active sessions — one failing session must not stop the others.
        // The location name is the key here, and it is already an interned field, so
        // this adds no per-tick allocation.
        for (LocationSession sess : waveCtx.sessions.values()) {
            safeTick(sess.locationName, () -> tickSession(sess));
        }

        // Update InfoPanel TextDisplay entities every second
        if (waveCtx.tickCounter % 20 == 0) {
            safeTick("infopanels", infoPanelMgr::tick);
        }

        // Refresh teammate HUD (HP bars + alive state) every second for every active
        // session so changes are visible in real time, not only on death / join / leave.
        if (waveCtx.tickCounter % 20 == 0) {
            // Push live wave number + countdown to the HUD. Without this, PlayerWaveData
            // keeps whatever was captured at join time, so the on-screen timer never
            // ticks down and the wave counter never advances.
            try { refreshHudState(); }
            catch (Throwable t) { /* HUD refresh must never break the tick loop */ }

            for (String locName : waveCtx.sessions.keySet()) {
                try { syncTeammates(locName); }
                catch (Throwable t) { /* one bad location shouldn't kill the loop */ }
            }
            // BBox outline — particle "fence" along bbox top edges, visible to ALL players
            // in the session (admin no longer the only one who knows where the box is).
            try { BboxRenderer.tick(waveCtx); }
            catch (Throwable t) { /* never let render glitches kill the tick */ }
        }

        // Nudge idle mobs back onto a player every 2 s. The targeting goal normally
        // handles this, but a mob that got stuck, was knocked outside its follow range
        // or had its target removed can end up wandering — which is exactly the
        // "mobs crawl away and never come back" players reported.
        if (waveCtx.tickCounter % 40 == 0) {
            try { retargetIdleMobs(); }
            catch (Throwable t) { /* AI nudging must never break the tick loop */ }
        }

        // Process delayed PvP respawns
        tickPendingPvpRespawns();

        // Autosave live match state every 30 s so a crash costs at most that much
        // progress (and never a player's stored inventory). Write is async+debounced;
        // skipped entirely when nothing is running.
        if (waveCtx.tickCounter % 600 == 0 && !waveCtx.sessions.isEmpty()) {
            try { saveRuntimeState(); }
            catch (Throwable t) { /* autosave must never break the tick loop */ }
        }

        waveCtx.tickCounter++;
    }

    /**
     * Pushes the live wave number and next-wave countdown into every PvE player's
     * {@link PlayerWaveData} once per second.
     *
     * <p>Previously these fields were only written by {@code SessionManager} when a
     * player <em>joined</em> a location, so the HUD froze at the values captured at
     * join time — the countdown never moved and the wave counter never advanced.
     *
     * <p>PvP locations are skipped: {@code PvpRoundManager} owns the timer/phase
     * state there and already syncs it on its own schedule.
     *
     * <p>A packet is only sent when a rendered value actually changed, so an idle
     * lobby costs nothing on the wire.
     */
    /** Last time each subsystem's tick failure was logged, to keep the log readable. */
    private final java.util.Map<String, Long> lastTickErrorLog = new java.util.HashMap<>();

    /** Minimum gap between repeated error reports for the same subsystem. */
    private static final long TICK_ERROR_LOG_INTERVAL_MS = 10_000L;

    /**
     * Runs one subsystem's tick, containing any failure to that subsystem.
     *
     * <p>Errors are logged, not swallowed — but at most once per
     * {@value #TICK_ERROR_LOG_INTERVAL_MS} ms per subsystem, because a fault that
     * reproduces every tick would otherwise write 20 stack traces a second and bury
     * everything else in the log.
     */
    private void safeTick(String subsystem, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            Long last = lastTickErrorLog.get(subsystem);
            if (last == null || now - last >= TICK_ERROR_LOG_INTERVAL_MS) {
                lastTickErrorLog.put(subsystem, now);
                WaveDefenseMod.LOGGER.error(
                    "[WaveDefense] '{}' tick failed; that subsystem is degraded but the server continues.",
                    subsystem, t);
            }
        }
    }

    /**
     * Re-points wave mobs that currently have no target at the nearest player in their
     * location.
     *
     * <p>Only touches mobs the session already tracks and only those whose target is
     * null or dead, so the cost is bounded by the number of live wave mobs and does no
     * area scan. Runs at 0.5 Hz — often enough that a player never notices a lull, rare
     * enough to be invisible in the tick profile.
     */
    private void retargetIdleMobs() {
        for (java.util.Map.Entry<String, LocationSession> entry : waveCtx.sessions.entrySet()) {
            LocationSession sess = entry.getValue();
            if (sess == null || sess.spawnedMobs.isEmpty()) continue;

            java.util.List<ServerPlayer> players = getPlayersInLocation(entry.getKey());
            if (players.isEmpty()) continue;

            for (UUID mobId : sess.spawnedMobs) {
                net.minecraft.world.entity.Entity e = null;
                for (ServerPlayer p : players) { e = p.serverLevel().getEntity(mobId); break; }
                if (!(e instanceof Mob mob) || !mob.isAlive()) continue;

                net.minecraft.world.entity.LivingEntity current = mob.getTarget();
                if (current != null && current.isAlive()) continue;

                ServerPlayer nearest = null;
                double best = Double.MAX_VALUE;
                for (ServerPlayer p : players) {
                    if (p.isSpectator() || !p.isAlive()) continue;
                    double d = p.distanceToSqr(mob);
                    if (d < best) { best = d; nearest = p; }
                }
                if (nearest != null) mob.setTarget(nearest);
            }
        }
    }

    private void refreshHudState() {
        for (java.util.Map.Entry<String, LocationSession> entry : waveCtx.sessions.entrySet()) {
            String locName = entry.getKey();
            LocationSession sess = entry.getValue();
            if (sess == null) continue;

            com.wavedefense.data.Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc == null || loc.isPvp()) continue; // PvP timer is PvpRoundManager's job

            // Which countdown is running?
            //   startTimerMs  > 0 → lobby phase (epoch-ms deadline)
            //   waveTimerTicks> 0 → between-waves delay (20 ticks per second)
            int secondsLeft;
            boolean timerActive;
            if (sess.startTimerMs > 0) {
                long msLeft = sess.startTimerMs - System.currentTimeMillis();
                secondsLeft = (int) Math.max(0L, (msLeft + 999L) / 1000L); // ceil
                timerActive = true;
            } else if (sess.waveTimerTicks > 0) {
                secondsLeft = (sess.waveTimerTicks + 19) / 20;             // ceil
                timerActive = true;
            } else {
                secondsLeft = 0;
                timerActive = false;
            }

            for (ServerPlayer player : getPlayersInLocation(locName)) {
                PlayerWaveData data = waveCtx.playerData.get(player.getUUID());
                if (data == null) continue;
                boolean changed = data.getCurrentWave() != sess.currentWave
                        || data.getTimeUntilNextWave() != secondsLeft
                        || data.isTimerActive() != timerActive;
                if (!changed) continue;

                data.setCurrentWave(sess.currentWave);
                data.setTimeUntilNextWave(secondsLeft);
                data.setTimerActive(timerActive);
                syncPlayerData(player);
            }
        }
    }

    private void tickPendingPvpRespawns() {
        if (pendingPvpRespawns.isEmpty()) return;
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv == null) return;
        Iterator<Map.Entry<UUID, PendingRespawnData>> it = pendingPvpRespawns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingRespawnData> entry = it.next();
            PendingRespawnData data = entry.getValue();
            data.ticksRemaining--;
            if (data.ticksRemaining > 0) continue;

            it.remove();
            ServerPlayer player = srv.getPlayerList().getPlayer(entry.getKey());
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
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false));
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("wavedefense.msg.respawn_continue"), true);
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
        if (WaveDefenseMod.locationManager == null) return;
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
                // G4 fix: push updated stats to the killing player immediately
                syncPlayerStats(player);
            }

            // Update player-specific stats
            int points = mob.getPersistentData().getInt("points");
            PlayerWaveData playerData = getPlayerData(player.getUUID());
            if (playerData != null) {
                // Award points for the kill
                if (points > 0) {
                    Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
                    if (loc != null) {
                        loc.addPoints(player.getUUID(), points);
                    }
                }
            }

            // Lifetime profile — accumulates across every run on every location.
            com.wavedefense.data.PlayerProfileManager pm = WaveDefenseMod.profileManager;
            if (pm != null) {
                com.wavedefense.data.PlayerProfile profile =
                    pm.getOrCreate(player.getUUID(), player.getName().getString());
                profile.recordKills(1);
                profile.recordPoints(points);
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
        // If we find it here, it was a trigger mob not in spawnedMobs — count the kill.
        for (Map.Entry<String, Set<UUID>> entry : sess.triggerMobs.entrySet()) {
            if (entry.getValue().remove(mobUuid)) {
                sess.mobsKilled++;
            }
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

        // Endless never reaches triggerVictory, so death is where the run is scored.
        // Without this an endless location would never produce a leaderboard entry.
        recordRunEnd(victim, location, locName);

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
            // The last defender died, so the arena is over. Clear the wave out of the
            // world first: these mobs are persistenceRequired and removeSession only
            // drops the tracking set, so skipping this stranded the entire live wave
            // permanently — every run that ended in death leaked its mobs.
            sessionMgr.despawnSessionMobs(locName);
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

    /**
     * Scores a PvE run that ended in death rather than victory.
     *
     * <p>Always updates the player's lifetime profile. For endless locations it also
     * writes a leaderboard record, because "how far did you get" <em>is</em> the score
     * there and {@code triggerVictory} — the only other place that records one — can
     * never fire. Non-endless deaths are not ranked: dying on wave 3 of 10 is not a
     * result worth listing next to a completed run.
     */
    private void recordRunEnd(ServerPlayer victim, Location location, String locName) {
        LocationSession sess = waveCtx.getSession(locName);
        int wavesReached = sess != null ? sess.currentWave : 0;
        int durationSec = (sess != null && sess.gameStartMs > 0)
            ? Math.max(0, (int) ((System.currentTimeMillis() - sess.gameStartMs) / 1000)) : 0;

        com.wavedefense.data.PlayerProfileManager pm = WaveDefenseMod.profileManager;
        if (pm != null) {
            com.wavedefense.data.PlayerProfile profile =
                pm.getOrCreate(victim.getUUID(), victim.getName().getString());
            profile.recordDeath();
            profile.recordMatchEnd(false, durationSec);
            pm.save();
        }

        if (location.isEndlessMode() && WaveDefenseMod.leaderboardManager != null) {
            WaveDefenseMod.leaderboardManager.addRecord(locName,
                LeaderboardManager.MODE_PVE_ENDLESS + location.getDifficultyPreset().getLeaderboardSuffix(),
                new com.wavedefense.data.LeaderboardRecord(
                    victim.getUUID(), victim.getName().getString(),
                    wavesReached, location.getPlayerPoints(victim.getUUID()), durationSec));
            WaveDefenseMod.leaderboardManager.saveToFile();
        }
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
    // ── Runtime-state persistence ───────────────────────────────────────────
    // Live match state (sessions, per-player data, inventory backups) used to exist
    // only in memory: a clean /stop was survivable because every player fires a
    // logout event (which surrenders them and restores their inventory), but a
    // crash or `kill -9` lost everything — including the inventories players had
    // when they entered an arena. These methods persist that state to disk.

    /** {@code world/data/wavedefense_runtime.dat} — resolved lazily (needs the server). */
    private java.io.File runtimeFile() {
        net.minecraft.server.MinecraftServer server = WaveDefenseMod.getServer();
        if (server == null) return null;
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/wavedefense_runtime.dat").toFile();
    }

    /** Async + debounced snapshot — safe to call periodically while the server runs. */
    public void saveRuntimeState() {
        java.io.File f = runtimeFile();
        if (f != null) com.wavedefense.data.NbtHelper.atomicWriteCompressedAsync(f, save());
    }

    /** Blocking snapshot — used on shutdown so nothing is left in the debounce queue. */
    public void saveRuntimeStateSync() {
        java.io.File f = runtimeFile();
        if (f != null) com.wavedefense.data.NbtHelper.atomicWriteCompressed(f, save());
    }

    /**
     * Restores runtime state written by {@link #saveRuntimeState()}.
     *
     * <p>Called on server start. If the file is missing (first run, or a clean
     * shutdown that already drained every session) this is a no-op.
     *
     * <p><b>Sessions are deliberately not resumed.</b> A restart despawns every mob
     * the wave spawned and the world has moved on, so continuing a half-finished
     * wave would strand players in an arena that can never complete. Instead we keep
     * only the {@linkplain com.wavedefense.data.PlayerBackup inventory backups} —
     * each player gets their gear, position, XP and game mode back the next time
     * they log in (see {@link #recoverCrashedPlayer}).
     */
    public void loadRuntimeState() {
        java.io.File f = runtimeFile();
        if (f == null) return;
        com.wavedefense.data.NbtHelper.LoadResult result =
            com.wavedefense.data.NbtHelper.readWithBackup(f, "runtime state");
        if (!result.isPresent()) return;
        try {
            load(result.tag);

            int pendingBackups = waveCtx.playerBackups.size();
            int abandonedSessions = waveCtx.sessions.size();

            // Drop un-resumable live state, keep the backups.
            waveCtx.sessions.clear();
            playerData.clear();

            if (pendingBackups > 0 || abandonedSessions > 0) {
                WaveDefenseMod.LOGGER.warn(
                    "[WaveDefense] Recovered from an unclean shutdown: {} abandoned session(s) discarded, "
                  + "{} player inventory backup(s) pending restore on next login.",
                    abandonedSessions, pendingBackups);
            }
            // Persist the trimmed state so a second restart doesn't re-report it.
            saveRuntimeStateSync();
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Could not restore runtime state: {}", e.getMessage());
        }
    }

    /**
     * Crash recovery for a single player, called on login.
     *
     * <p>If we still hold an inventory backup for them, the server went down while
     * they were inside an arena — their real inventory was in that backup. Restore
     * it (gear, position, health, XP, game mode) and tell them what happened.
     *
     * @return true if a backup was restored
     */
    public boolean recoverCrashedPlayer(ServerPlayer player) {
        com.wavedefense.data.PlayerBackup backup = waveCtx.playerBackups.remove(player.getUUID());
        if (backup == null) return false;
        try {
            backup.restore(player);
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("wavedefense.msg.crash_recovery"));
            WaveDefenseMod.LOGGER.info("[WaveDefense] Restored crash backup for {}", player.getGameProfile().getName());
            saveRuntimeState(); // backup consumed — persist the shrunken map
            return true;
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Failed to restore crash backup for {}: {}",
                player.getGameProfile().getName(), e.getMessage());
            return false;
        }
    }

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
        // In-session inventory backups. Persisted so a server crash can't lose a
        // player's pre-arena inventory (they used to live only in memory).
        ListTag backupList = new ListTag();
        for (Map.Entry<UUID, com.wavedefense.data.PlayerBackup> entry : waveCtx.playerBackups.entrySet()) {
            CompoundTag bTag = new CompoundTag();
            bTag.putUUID("uuid", entry.getKey());
            bTag.put("backup", entry.getValue().toNbt());
            backupList.add(bTag);
        }
        tag.put("playerBackups", backupList);
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
        // In-session inventory backups (restored so a crash mid-match is recoverable)
        if (tag.contains("playerBackups")) {
            ListTag backupList = tag.getList("playerBackups", 10);
            for (int i = 0; i < backupList.size(); i++) {
                CompoundTag bTag = backupList.getCompound(i);
                com.wavedefense.data.PlayerBackup backup =
                    com.wavedefense.data.PlayerBackup.fromNbt(bTag.getCompound("backup"));
                if (backup != null) waveCtx.playerBackups.put(bTag.getUUID("uuid"), backup);
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
