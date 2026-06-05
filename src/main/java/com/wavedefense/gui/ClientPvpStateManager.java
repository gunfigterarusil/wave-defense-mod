package com.wavedefense.gui;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

import java.util.*;

/**
 * Клієнтський кеш стану PvP, отриманого від сервера.
 * Оновлюється SyncPvpStatePacket.
 */
public class ClientPvpStateManager {

    public static class PlayerRow {
        public final String name;
        public final String team;
        public final int kills, deaths, assists;
        public final boolean alive;
        public PlayerRow(String n, String t, int k, int d, int a, boolean alive) {
            name = n; team = t; kills = k; deaths = d; assists = a; this.alive = alive;
        }
    }

    private static String location   = "";
    private static String phase       = "WAITING";
    private static int currentRound   = 0;
    private static int totalRounds    = 0;
    private static int timerSeconds   = 0;
    private static String myTeam      = "";
    private static Map<String, Integer> teamWins = new LinkedHashMap<>();
    private static List<PlayerRow> players = new ArrayList<>();
    // v0.2.62: ready-check tracking
    private static Set<String> readyNames = new HashSet<>();
    // v0.2.65: timestamp of last sync packet for AdminDebugHud freshness display
    private static long lastUpdateMs = -1L;

    /** v0.2.65: ms since last SyncPvpStatePacket processed, or -1 if never. */
    public static long getLastUpdateAgoMs() {
        return lastUpdateMs < 0 ? -1L : (System.currentTimeMillis() - lastUpdateMs);
    }

    public static void update(CompoundNBT tag) {
        lastUpdateMs = System.currentTimeMillis();
        location      = tag.getString("location");
        phase         = tag.getString("phase");
        currentRound  = tag.getInt("currentRound");
        totalRounds   = tag.getInt("totalRounds");
        timerSeconds  = tag.getInt("timerSeconds");
        myTeam        = tag.getString("myTeam");

        teamWins.clear();
        CompoundNBT wins = tag.getCompound("teamWins");
        for (String key : wins.getAllKeys()) teamWins.put(key, wins.getInt(key));

        players.clear();
        ListNBT pl = tag.getList("players", 10);
        for (int i = 0; i < pl.size(); i++) {
            CompoundNBT pe = pl.getCompound(i);
            players.add(new PlayerRow(
                pe.getString("name"), pe.getString("team"),
                pe.getInt("kills"), pe.getInt("deaths"), pe.getInt("assists"),
                pe.getBoolean("alive")
            ));
        }

        // v0.2.62: ready-check player set (may be absent in non-READY_CHECK syncs)
        readyNames.clear();
        if (tag.contains("readyPlayers")) {
            ListNBT rl = tag.getList("readyPlayers", 8); // 8 = StringNBT
            for (int i = 0; i < rl.size(); i++) readyNames.add(rl.getString(i));
        }
    }

    /** v0.2.62: names of players who pressed ready in current READY_CHECK */
    public static Set<String> getReadyNames() { return readyNames; }

    /** v0.2.62: true if the local player has pressed ready */
    public static boolean isMeReady() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return false;
        return readyNames.contains(mc.player.getGameProfile().getName());
    }

    public static String getPhase()       { return phase; }
    public static int getCurrentRound()   { return currentRound; }
    public static int getTotalRounds()    { return totalRounds; }
    public static int getTimerSeconds()   { return timerSeconds; }
    public static String getMyTeam()      { return myTeam; }
    public static Map<String, Integer> getTeamWins() { return teamWins; }
    public static List<PlayerRow> getPlayers() { return players; }
    public static String getLocation()    { return location; }
    public static boolean isActive()      { return !phase.equals("WAITING") && !location.isEmpty(); }

    /** Скидає PvP-стан при виході гравця з локації. */
    public static void reset() {
        location     = "";
        phase        = "WAITING";
        currentRound = 0;
        totalRounds  = 0;
        timerSeconds = 0;
        myTeam       = "";
        teamWins.clear();
        players.clear();
        readyNames.clear();
    }
}
