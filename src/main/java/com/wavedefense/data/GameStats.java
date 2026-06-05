package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameStats {
    private int wavesCompleted;
    private int mobsKilled;
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    public void incrementWavesCompleted() {
        wavesCompleted++;
    }

    public void incrementMobsKilled() {
        mobsKilled++;
    }

    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, k -> new PlayerStats());
    }

    public Map<UUID, PlayerStats> getPlayerStats() {
        return playerStats;
    }

    public int getWavesCompleted() {
        return wavesCompleted;
    }

    public int getMobsKilled() {
        return mobsKilled;
    }

    public void reset() {
        wavesCompleted = 0;
        mobsKilled = 0;
        playerStats.clear();
    }

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("wavesCompleted", wavesCompleted);
        tag.putInt("mobsKilled", mobsKilled);
        ListNBT playerStatsList = new ListNBT();
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            CompoundNBT playerTag = new CompoundNBT();
            playerTag.putUUID("player", entry.getKey());
            playerTag.put("stats", entry.getValue().save());
            playerStatsList.add(playerTag);
        }
        tag.put("playerStats", playerStatsList);
        return tag;
    }

    public static GameStats load(CompoundNBT tag) {
        GameStats stats = new GameStats();
        stats.wavesCompleted = tag.getInt("wavesCompleted");
        stats.mobsKilled = tag.getInt("mobsKilled");
        ListNBT playerStatsList = tag.getList("playerStats", 10);
        for (int i = 0; i < playerStatsList.size(); i++) {
            CompoundNBT playerTag = playerStatsList.getCompound(i);
            stats.playerStats.put(playerTag.getUUID("player"), PlayerStats.load(playerTag.getCompound("stats")));
        }
        return stats;
    }
}
