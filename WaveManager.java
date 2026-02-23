package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class WaveMob {
    private ResourceLocation mobType;
    private int count;
    private int growthPerWave;
    private int spawnChance;
    private int pointsPerKill;

    public WaveMob(ResourceLocation mobType, int count, int growthPerWave, int spawnChance, int pointsPerKill) {
        this.mobType = mobType;
        this.count = count;
        this.growthPerWave = growthPerWave;
        this.spawnChance = spawnChance;
        this.pointsPerKill = pointsPerKill;
    }

    public ResourceLocation getMobType() { return mobType; }
    public void setMobType(ResourceLocation mobType) { this.mobType = mobType; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getGrowthPerWave() { return growthPerWave; }
    public void setGrowthPerWave(int growthPerWave) { this.growthPerWave = growthPerWave; }

    public int getSpawnChance() { return spawnChance; }
    public void setSpawnChance(int spawnChance) { this.spawnChance = spawnChance; }

    public int getPointsPerKill() { return pointsPerKill; }
    public void setPointsPerKill(int pointsPerKill) { this.pointsPerKill = pointsPerKill; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mobType", mobType.toString());
        tag.putInt("count", count);
        tag.putInt("growthPerWave", growthPerWave);
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("pointsPerKill", pointsPerKill);
        return tag;
    }

    public static WaveMob load(CompoundTag tag) {
        return new WaveMob(
                new ResourceLocation(tag.getString("mobType")),
                tag.getInt("count"),
                tag.getInt("growthPerWave"),
                tag.getInt("spawnChance"),
                tag.getInt("pointsPerKill")
        );
    }
}
