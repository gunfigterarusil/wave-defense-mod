package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    private int waveNumber;
    private int timeBetweenWaves; // В секундах
    private List<WaveMob> mobs;
    private List<ItemStack> rewards;

    public WaveConfig(int waveNumber, int timeBetweenWaves) {
        this.waveNumber = waveNumber;
        this.timeBetweenWaves = timeBetweenWaves;
        this.mobs = new ArrayList<>();
        this.rewards = new ArrayList<>();
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

    public List<ItemStack> getRewards() { return rewards; }
    public void addReward(ItemStack item) { rewards.add(item.copy()); }
    public void removeReward(int index) {
        if (index >= 0 && index < rewards.size()) {
            rewards.remove(index);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("waveNumber", waveNumber);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);

        ListTag mobsList = new ListTag();
        for (WaveMob mob : mobs) {
            mobsList.add(mob.save());
        }
        tag.put("mobs", mobsList);

        ListTag rewardsList = new ListTag();
        for (ItemStack item : rewards) {
            rewardsList.add(item.save(new CompoundTag()));
        }
        tag.put("rewards", rewardsList);

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

        ListTag rewardsList = tag.getList("rewards", 10);
        for (int i = 0; i < rewardsList.size(); i++) {
            config.rewards.add(ItemStack.of(rewardsList.getCompound(i)));
        }

        return config;
    }
}
