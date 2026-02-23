package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    private int waveNumber;
    private int timeBetweenWaves; // In seconds
    private List<WaveMob> mobs;
    private int pointsReward;

    // Ефект що накладається на всіх гравців на час хвилі
    // null = без ефекту. Формат: "minecraft:speed" або "minecraft:strength" тощо
    private ResourceLocation waveEffect;
    private int waveEffectAmplifier; // 0 = рівень I, 1 = рівень II, тощо

    // Команда яка виконується при завершенні хвилі
    // Підтримує змінні: %location%, %wave%, %players%
    private String completionCommand;

    public WaveConfig(int waveNumber, int timeBetweenWaves) {
        this.waveNumber = waveNumber;
        this.timeBetweenWaves = timeBetweenWaves;
        this.mobs = new ArrayList<>();
        this.pointsReward = 0;
        this.waveEffect = null;
        this.waveEffectAmplifier = 0;
        this.completionCommand = "";
    }

    public int getWaveNumber() { return waveNumber; }
    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int time) { this.timeBetweenWaves = time; }

    public List<WaveMob> getMobs() { return mobs; }
    public void addMob(WaveMob mob) { if (mobs.size() < 10) mobs.add(mob); }
    public void removeMob(int index) { if (index >= 0 && index < mobs.size()) mobs.remove(index); }

    public int getPointsReward() { return pointsReward; }
    public void setPointsReward(int points) { this.pointsReward = points; }

    public ResourceLocation getWaveEffect() { return waveEffect; }
    public void setWaveEffect(ResourceLocation effect) { this.waveEffect = effect; }

    public int getWaveEffectAmplifier() { return waveEffectAmplifier; }
    public void setWaveEffectAmplifier(int amplifier) { this.waveEffectAmplifier = Math.max(0, Math.min(4, amplifier)); }

    public boolean hasEffect() { return waveEffect != null; }

    public String getCompletionCommand() { return completionCommand == null ? "" : completionCommand; }
    public void setCompletionCommand(String cmd) { this.completionCommand = cmd == null ? "" : cmd; }
    public boolean hasCompletionCommand() { return completionCommand != null && !completionCommand.isBlank(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("waveNumber", waveNumber);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);
        tag.putInt("pointsReward", pointsReward);
        tag.putInt("waveEffectAmplifier", waveEffectAmplifier);
        if (waveEffect != null) tag.putString("waveEffect", waveEffect.toString());
        if (!getCompletionCommand().isBlank()) tag.putString("completionCommand", completionCommand);

        ListTag mobsList = new ListTag();
        for (WaveMob mob : mobs) mobsList.add(mob.save());
        tag.put("mobs", mobsList);

        return tag;
    }

    public static WaveConfig load(CompoundTag tag) {
        WaveConfig config = new WaveConfig(
                tag.getInt("waveNumber"),
                tag.getInt("timeBetweenWaves")
        );
        if (tag.contains("pointsReward")) config.pointsReward = tag.getInt("pointsReward");
        if (tag.contains("waveEffect")) {
            try { config.waveEffect = new ResourceLocation(tag.getString("waveEffect")); }
            catch (Exception ignored) {}
        }
        config.waveEffectAmplifier = tag.contains("waveEffectAmplifier") ? tag.getInt("waveEffectAmplifier") : 0;
        config.completionCommand = tag.contains("completionCommand") ? tag.getString("completionCommand") : "";

        ListTag mobsList = tag.getList("mobs", 10);
        for (int i = 0; i < mobsList.size(); i++) config.mobs.add(WaveMob.load(mobsList.getCompound(i)));
        return config;
    }
}
