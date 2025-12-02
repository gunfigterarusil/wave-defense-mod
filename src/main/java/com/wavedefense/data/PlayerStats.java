package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;

public class PlayerStats {
    private int mobsKilled;
    private int pointsEarned;

    public void incrementMobsKilled() {
        mobsKilled++;
    }

    public void addPoints(int points) {
        pointsEarned += points;
    }

    public int getMobsKilled() {
        return mobsKilled;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mobsKilled", mobsKilled);
        tag.putInt("pointsEarned", pointsEarned);
        return tag;
    }

    public static PlayerStats load(CompoundTag tag) {
        PlayerStats stats = new PlayerStats();
        stats.mobsKilled = tag.getInt("mobsKilled");
        stats.pointsEarned = tag.getInt("pointsEarned");
        return stats;
    }
}
