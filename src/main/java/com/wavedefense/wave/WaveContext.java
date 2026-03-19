package com.wavedefense.wave;

import com.wavedefense.data.GameStats;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state container — передається між усіма sub-managers.
 * Зберігає всі Map-поля що раніше жили в WaveManager.
 * Sub-managers читають і пишуть через цей об'єкт.
 */
public class WaveContext {

    // ── Player / session state ────────────────────────────────────────
    public final Map<UUID, PlayerWaveData>      playerData           = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerBackup>        playerBackups        = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerBackup>        pendingDeathRestores = new ConcurrentHashMap<>();
    public final Map<String, Set<UUID>>         spawnedMobsByLocation= new ConcurrentHashMap<>();
    public final Map<UUID, Long>                reEntryCooldowns     = new ConcurrentHashMap<>();

    // ── Wave timers ───────────────────────────────────────────────────
    public final Map<String, Long>              locationStartTimers  = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationWaveTimers   = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationCurrentWave  = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationTimer60      = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationTimer120     = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationTimer300     = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationTimerCustom  = new ConcurrentHashMap<>();
    public final Map<String, Integer>           victoryLingerTimers  = new ConcurrentHashMap<>();
    public final Map<String, GameStats>         locationStats        = new ConcurrentHashMap<>();
    public int                                  tickCounter          = 0;

    // ── Mob tracking ──────────────────────────────────────────────────
    public final Map<String, Integer>           waveStartMobCounts   = new ConcurrentHashMap<>();
    public final Map<String, Integer>           locationMobsKilled   = new ConcurrentHashMap<>();
    public final Set<String>                    halfMobsTriggered    = ConcurrentHashMap.newKeySet();

    // ── Wave triggers ─────────────────────────────────────────────────
    public final Map<String, Long>              waveTriggerLastFired = new ConcurrentHashMap<>();
    public final Map<String, Integer>           waveTriggerWaveCounters = new ConcurrentHashMap<>();
    public final Map<String, Long>              recentlyFiredTriggers= new ConcurrentHashMap<>();
    public static final long                    RECENTLY_FIRED_WINDOW_MS = 10_000L;

    // ── Boundary ──────────────────────────────────────────────────────
    public final Map<UUID, Integer>             leaveCountdownTicks  = new ConcurrentHashMap<>();

    // ── Zone activation ───────────────────────────────────────────────
    public final Map<String, Integer>           zoneCountdownTickers = new ConcurrentHashMap<>();
    public final Map<String, Set<UUID>>         zonePlayersInRange   = new ConcurrentHashMap<>();
    public final Map<String, Long>              zoneCountdownStartMs = new ConcurrentHashMap<>();
    public final Map<String, Integer>           zoneLateJoinTimers   = new ConcurrentHashMap<>();
    public final Map<String, Long>              zoneOpenUntilMs      = new ConcurrentHashMap<>();

    // ── Portal ────────────────────────────────────────────────────────
    public final Map<String, net.minecraft.core.BlockPos> portalPositions      = new ConcurrentHashMap<>();
    public final Map<String, Integer>           portalPenaltyTimers  = new ConcurrentHashMap<>();
    public final Map<String, Integer>           portalRespawnTimers  = new ConcurrentHashMap<>();
    public final Map<String, Integer>           portalPenaltyWaveIndex = new ConcurrentHashMap<>();
    public final Map<String, Set<UUID>>         portalPenaltyMobs    = new ConcurrentHashMap<>();
    public final Map<String, net.minecraft.core.BlockPos> portalEntryPositions = new ConcurrentHashMap<>();
    public final Set<String>                    portalFirstPlayerEntered = ConcurrentHashMap.newKeySet();
    public final Map<String, Set<UUID>>         portalEnteredPlayers = new ConcurrentHashMap<>();
    public final Map<String, Long>              portalOpenUntilMs    = new ConcurrentHashMap<>();

    // ── PvP ───────────────────────────────────────────────────────────
    public final Map<String, com.wavedefense.data.PvpRoundState> pvpStates = new ConcurrentHashMap<>();

    // ── InfoPanel ─────────────────────────────────────────────────────
    public final Map<String, UUID>              infoPanelEntityIds   = new ConcurrentHashMap<>();

    // ── Helpers ───────────────────────────────────────────────────────

    /** Повертає Set активних назв локацій (де є гравці). */
    public Set<String> getActiveLocationNames() {
        Set<String> names = new HashSet<>();
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() != null)
                names.add(d.getCurrentLocation().getName());
        }
        return names;
    }

    /** Повертає список гравців у конкретній локації. */
    public List<ServerPlayer> getPlayersInLocation(String locationName) {
        List<ServerPlayer> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            if (e.getValue().getCurrentLocation() != null &&
                    e.getValue().getCurrentLocation().getName().equals(locationName)) {
                ServerPlayer sp = com.wavedefense.WaveDefenseMod.getServer()
                        .getPlayerList().getPlayer(e.getKey());
                if (sp != null) list.add(sp);
            }
        }
        return list;
    }

    /** Розсилає повідомлення всім у локації. */
    public void broadcastToLocation(String locationName, String message) {
        for (ServerPlayer p : getPlayersInLocation(locationName))
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    /** Записує нещодавно спрацьований event-triggered тригер. */
    public void markRecentlyFired(String locationName, com.wavedefense.data.WaveTrigger trigger) {
        recentlyFiredTriggers.put(locationName + "_" + trigger.name(), System.currentTimeMillis());
    }

    /** Перевіряє чи event-triggered тригер спрацьовував в останні RECENTLY_FIRED_WINDOW_MS мс. */
    public boolean wasRecentlyFired(String locationName, com.wavedefense.data.WaveTrigger trigger) {
        Long t = recentlyFiredTriggers.get(locationName + "_" + trigger.name());
        return t != null && (System.currentTimeMillis() - t) < RECENTLY_FIRED_WINDOW_MS;
    }

    /** Очищає стан локації після завершення сесії. */
    public void clearLocationState(String locationName) {
        spawnedMobsByLocation.remove(locationName);
        locationWaveTimers.remove(locationName);
        locationStartTimers.remove(locationName);
        locationCurrentWave.remove(locationName);
        pvpStates.remove(locationName);
        victoryLingerTimers.remove(locationName);
        locationTimer60.remove(locationName);
        locationTimer120.remove(locationName);
        locationTimer300.remove(locationName);
        locationTimerCustom.remove(locationName);
        waveStartMobCounts.remove(locationName);
        locationMobsKilled.remove(locationName);
        waveTriggerLastFired.remove(locationName);
        waveTriggerWaveCounters.remove(locationName);
        halfMobsTriggered.removeIf(s -> s.startsWith(locationName));
        zoneCountdownTickers.remove(locationName);
        zonePlayersInRange.remove(locationName);
        zoneCountdownStartMs.remove(locationName);
        zoneLateJoinTimers.remove(locationName);
        zoneOpenUntilMs.remove(locationName);
        portalPositions.remove(locationName);
        portalPenaltyTimers.remove(locationName);
        portalRespawnTimers.remove(locationName);
        portalPenaltyWaveIndex.remove(locationName);
        portalPenaltyMobs.remove(locationName);
        portalEntryPositions.remove(locationName);
        portalFirstPlayerEntered.remove(locationName);
        portalEnteredPlayers.remove(locationName);
        portalOpenUntilMs.remove(locationName);
    }
}
