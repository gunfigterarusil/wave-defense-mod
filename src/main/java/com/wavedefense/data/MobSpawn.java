package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class MobSpawn {
    private ResourceLocation mobType;
    private int baseCount;
    private int countIncreasePerWave;
    private int spawnChance; // 1-100
    private int pointsPerKill;

    public MobSpawn(ResourceLocation mobType, int baseCount, int countIncreasePerWave,
                    int spawnChance, int pointsPerKill) {
        this.mobType = mobType;
        this.baseCount = baseCount;
        this.countIncreasePerWave = countIncreasePerWave;
        this.spawnChance = Math.min(100, Math.max(1, spawnChance));
        this.pointsPerKill = pointsPerKill;
    }

    public ResourceLocation getMobType() { return mobType; }
    public int getBaseCount() { return baseCount; }
    public int getCountIncreasePerWave() { return countIncreasePerWave; }
    public int getSpawnChance() { return spawnChance; }
    public int getPointsPerKill() { return pointsPerKill; }

    public int getCountForWave(int waveNumber) {
        return baseCount + (countIncreasePerWave * (waveNumber - 1));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mobType", mobType.toString());
        tag.putInt("baseCount", baseCount);
        tag.putInt("countIncrease", countIncreasePerWave);
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("pointsPerKill", pointsPerKill);
        return tag;
    }

    public static MobSpawn load(CompoundTag tag) {
        return new MobSpawn(
                new ResourceLocation(tag.getString("mobType")),
                tag.getInt("baseCount"),
                tag.getInt("countIncrease"),
                tag.getInt("spawnChance"),
                tag.getInt("pointsPerKill")
        );
    }
}