package com.wavedefense.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

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

    public static void update(CompoundTag tag) {
        location      = tag.getString("location");
        phase         = tag.getString("phase");
        currentRound  = tag.getInt("currentRound");
        totalRounds   = tag.getInt("totalRounds");
        timerSeconds  = tag.getInt("timerSeconds");
        myTeam        = tag.getString("myTeam");

        teamWins.clear();
        CompoundTag wins = tag.getCompound("teamWins");
        for (String key : wins.getAllKeys()) teamWins.put(key, wins.getInt(key));

        players.clear();
        ListTag pl = tag.getList("players", 10);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag pe = pl.getCompound(i);
            players.add(new PlayerRow(
                pe.getString("name"), pe.getString("team"),
                pe.getInt("kills"), pe.getInt("deaths"), pe.getInt("assists"),
                pe.getBoolean("alive")
            ));
        }
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
    }
}
