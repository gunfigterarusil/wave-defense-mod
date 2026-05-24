package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.WaveTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state container — передається між усіма sub-managers.
 *
 * <p><b>Стан локації</b> (хвилі, моби, портал, PvP, тощо) живе у
 * {@link LocationSession} і доступний через {@link #sessions}.
 * <br><b>Стан гравця</b> (глобальний, не прив'язаний до локації) залишається тут.
 */
public class WaveContext {

    // ── Per-player state (not location-specific) ─────────────────────
    public final Map<UUID, PlayerWaveData>  playerData           = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerBackup>    playerBackups        = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerBackup>    pendingDeathRestores = new ConcurrentHashMap<>();
    public final Map<UUID, Long>            reEntryCooldowns     = new ConcurrentHashMap<>();

    // ── Boundary (per-player) ─────────────────────────────────────────
    public final Map<UUID, Integer>         leaveCountdownTicks  = new ConcurrentHashMap<>();

    // ── Active location sessions ──────────────────────────────────────
    /** locationName → all runtime state for that active location. */
    public final Map<String, LocationSession> sessions = new ConcurrentHashMap<>();

    // ── Global ───────────────────────────────────────────────────────
    public int tickCounter = 0;

    // ─────────────────────────────────────────────────────────────────
    //  Session lifecycle helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Повертає активну сесію або null якщо локація не запущена.
     */
    public LocationSession getSession(String locationName) {
        return sessions.get(locationName);
    }

    /**
     * Повертає активну сесію, або створює нову (якщо локація щойно стартує).
     */
    public LocationSession getOrCreateSession(String locationName, Location config) {
        return sessions.computeIfAbsent(locationName, k -> new LocationSession(locationName, config));
    }

    /**
     * Завершує сесію: видаляє зі мапи і викликає {@link LocationSession#dispose()}.
     */
    public void removeSession(String locationName) {
        LocationSession s = sessions.remove(locationName);
        if (s != null) s.dispose();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Query helpers
    // ─────────────────────────────────────────────────────────────────

    /** Повертає Set активних назв локацій (де є гравці). */
    public Set<String> getActiveLocationNames() {
        Set<String> names = new HashSet<>();
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() != null)
                names.add(d.getCurrentLocation().getName());
        }
        return names;
    }

    /** Повертає список серверних гравців у конкретній локації. */
    public List<ServerPlayer> getPlayersInLocation(String locationName) {
        // С9: server may be null during shutdown or world reload
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv == null) return java.util.Collections.emptyList();
        List<ServerPlayer> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            if (e.getValue().getCurrentLocation() != null &&
                    e.getValue().getCurrentLocation().getName().equals(locationName)) {
                ServerPlayer sp = srv.getPlayerList().getPlayer(e.getKey());
                if (sp != null) list.add(sp);
            }
        }
        return list;
    }

    /** Розсилає повідомлення всім у локації (Component — preferred overload). */
    public void broadcastToLocation(String locationName, net.minecraft.network.chat.Component component) {
        for (ServerPlayer p : getPlayersInLocation(locationName))
            p.displayClientMessage(component, false);
    }

    /**
     * Розсилає вже-перекладений рядок всім у локації.
     * @deprecated Використовуй {@link #broadcastToLocation(String, net.minecraft.network.chat.Component)}
     *             з {@code Component.translatable()} щоб повідомлення локалізувалось на стороні клієнта.
     */
    @Deprecated
    public void broadcastToLocation(String locationName, String message) {
        broadcastToLocation(locationName, net.minecraft.network.chat.Component.literal(message));
    }

    /** Записує нещодавно спрацьований event-triggered тригер у сесію. */
    public void markRecentlyFired(String locationName, WaveTrigger trigger) {
        LocationSession s = getSession(locationName);
        if (s != null) s.markRecentlyFired(trigger);
    }

    /** Перевіряє чи event-triggered тригер спрацьовував нещодавно. */
    public boolean wasRecentlyFired(String locationName, WaveTrigger trigger) {
        LocationSession s = getSession(locationName);
        return s != null && s.wasRecentlyFired(trigger, LocationSession.RECENTLY_FIRED_WINDOW_MS);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Очищає всі дані контексту. Використовується при повному відновленні. */
    public void clear() {
        playerData.clear();
        playerBackups.clear();
        pendingDeathRestores.clear();
        reEntryCooldowns.clear();
        leaveCountdownTicks.clear();
        sessions.clear();
        tickCounter = 0;
    }

    /** Зберігає всі активні сесії. */
    public ListTag saveSessions() {
        ListTag list = new ListTag();
        for (LocationSession sess : sessions.values()) {
            list.add(sess.save());
        }
        return list;
    }

    /** Відновлює сесії зі списку. */
    public void loadSessions(ListTag list) {
        sessions.clear();
        for (int i = 0; i < list.size(); i++) {
            LocationSession sess = LocationSession.load(list.getCompound(i));
            if (sess != null) {
                sessions.put(sess.locationName, sess);
            }
        }
    }

    /** Відновлює одну сесію. */
    public void loadSession(String locationName, CompoundTag tag) {
        LocationSession sess = LocationSession.load(tag);
        if (sess != null) {
            sessions.put(locationName, sess);
        }
    }
}
