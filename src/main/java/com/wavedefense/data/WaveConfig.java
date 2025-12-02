package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    private int waveNumber;
    private int timeBetweenWaves; // In seconds
    private List<WaveMob> mobs;
    private int pointsReward; // Changed from List<ItemStack> to int

    public WaveConfig(int waveNumber, int timeBetweenWaves) {
        this.waveNumber = waveNumber;
        this.timeBetweenWaves = timeBetweenWaves;
        this.mobs = new ArrayList<>();
        this.pointsReward = 0; // Default to 0 points
    }

    public int getWaveNumber() { return waveNumber; }
    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int time) { this.timeBetweenWaves = time; }

    public List<WaveMob> getMobs() { return mobs; }
    public void addMob(WaveMob mob) {
        if (mobs.size() < 10) mobs.add(mob);
    }
    public void removeMob(int index) {
        if (index >= 0 && index < mobs.size()) {
            mobs.remove(index);
        }
    }

    public int getPointsReward() { return pointsReward; }
    public void setPointsReward(int points) { this.pointsReward = points; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("waveNumber", waveNumber);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);

        ListTag mobsList = new ListTag();
        for (WaveMob mob : mobs) {
            mobsList.add(mob.save());
        }
        tag.put("mobs", mobsList);
        tag.putInt("pointsReward", pointsReward); // Save points reward

        return tag;
    }

    public static WaveConfig load(CompoundTag tag) {
        WaveConfig config = new WaveConfig(
                tag.getInt("waveNumber"),
                tag.getInt("timeBetweenWaves")
        );

        ListTag mobsList = tag.getList("mobs", 10);
        for (int i = 0; i < mobsList.size(); i++) {
            config.mobs.add(WaveMob.load(mobsList.getCompound(i)));
        }

        if (tag.contains("pointsReward")) {
            config.pointsReward = tag.getInt("pointsReward");
        }

        return config;
    }
}
