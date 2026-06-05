package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    private int waveNumber;
    private int timeBetweenWaves; // In seconds
    private List<WaveMob> mobs;
    private int pointsReward;

    // Effect applied to all players for the duration of the wave.
    // null = no effect. Format: "minecraft:speed", "minecraft:strength", etc.
    private ResourceLocation waveEffect;
    private int waveEffectAmplifier; // 0 = level I, 1 = level II, etc.

    // Command executed when the wave is completed.
    // Supports variables: %location%, %wave%, %players%
    private String completionCommand;

    // ── Trigger wave ─────────────────────────────────────────────────
    // When triggerEnabled=true, this wave spawns independently on its trigger
    // rather than in the normal sequence. It can run alongside another wave.
    private boolean triggerEnabled   = false;
    private WaveTrigger triggerType  = WaveTrigger.PLAYER_OPEN_CHEST;
    // Multi-triggers: ALL conditions must be satisfied simultaneously (AND)
    private java.util.List<WaveTrigger> extraTriggers = new java.util.ArrayList<>();
    // Item for the PLAYER_HAS_ITEM trigger (registry id, e.g. "minecraft:diamond")
    // Supports multiple items separated by commas: "minecraft:diamond,minecraft:iron_ingot"
    private String triggerCustomItemId = "";
    // Numeric value for TIMER_CUSTOM (sec), MOBS_KILLED_N (count), WAVES_SURVIVED_N (count)
    private int    triggerCustomValue  = 60;   // default 60 sec for TIMER_CUSTOM
    // Cooldown after firing:
    //   cooldownMode=NONE    — no cooldown
    //   cooldownMode=SECONDS — cooldownValue seconds
    //   cooldownMode=WAVES   — cooldownValue main waves
    public enum CooldownMode { NONE, SECONDS, WAVES }
    private CooldownMode cooldownMode  = CooldownMode.NONE;
    private int          cooldownValue = 0;

    // Dedicated mob spawn point for this wave (takes priority over location spawn points).
    // null = use location spawn points.
    private net.minecraft.util.math.BlockPos waveSpawnPos = null;

    // Only activate this wave starting from a specific wave number (0 = always)
    private int activateFromWave = 0;

    // One-time: fire only once per location session
    private boolean oneTimeOnly = false;
    private boolean firedThisSession = false; // runtime-only, not serialized

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
    public void addMob(WaveMob mob) { if (mobs.size() < com.wavedefense.config.WaveDefenseConfig.MAX_MOB_TYPES.get()) mobs.add(mob); }
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
    public boolean hasCompletionCommand() { return completionCommand != null && !completionCommand.trim().isEmpty(); }

    // ── Trigger getters/setters ──────────────────────────────────────
    public boolean isTriggerEnabled()            { return triggerEnabled; }
    public void    setTriggerEnabled(boolean v)  { this.triggerEnabled = v; }

    public WaveTrigger getTriggerType()              { return triggerType; }
    public void        setTriggerType(WaveTrigger t) { this.triggerType = t; }

    public java.util.List<WaveTrigger> getExtraTriggers() { return extraTriggers; }
    public void setExtraTriggers(java.util.List<WaveTrigger> t) { this.extraTriggers = t != null ? t : new java.util.ArrayList<>(); }
    public void addExtraTrigger(WaveTrigger t) { if (!extraTriggers.contains(t)) extraTriggers.add(t); }
    public void removeExtraTrigger(WaveTrigger t) { extraTriggers.remove(t); }

    public String getTriggerCustomItemId() { return triggerCustomItemId == null ? "" : triggerCustomItemId; }
    public void setTriggerCustomItemId(String id) { this.triggerCustomItemId = id == null ? "" : id; }
    public int  getTriggerCustomValue()    { return triggerCustomValue; }
    public void setTriggerCustomValue(int v) { this.triggerCustomValue = Math.max(1, v); }

    public CooldownMode getCooldownMode()                { return cooldownMode; }
    public void         setCooldownMode(CooldownMode m)  { this.cooldownMode = m; }

    public int  getCooldownValue()         { return cooldownValue; }
    public void setCooldownValue(int v)    { this.cooldownValue = Math.max(0, v); }

    public net.minecraft.util.math.BlockPos getWaveSpawnPos() { return waveSpawnPos; }
    public void setWaveSpawnPos(net.minecraft.util.math.BlockPos pos) { this.waveSpawnPos = pos; }
    public boolean hasWaveSpawnPos() { return waveSpawnPos != null; }

    public int  getActivateFromWave()      { return activateFromWave; }
    public void setActivateFromWave(int w) { this.activateFromWave = Math.max(0, w); }

    public boolean isOneTimeOnly()           { return oneTimeOnly; }
    public void    setOneTimeOnly(boolean v) { this.oneTimeOnly = v; }
    public boolean isFiredThisSession()      { return firedThisSession; }
    public void    setFiredThisSession(boolean v) { this.firedThisSession = v; }

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("waveNumber", waveNumber);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);
        tag.putInt("pointsReward", pointsReward);
        tag.putInt("waveEffectAmplifier", waveEffectAmplifier);
        if (waveEffect != null) tag.putString("waveEffect", waveEffect.toString());
        if (!getCompletionCommand().trim().isEmpty()) tag.putString("completionCommand", completionCommand);

        ListNBT mobsList = new ListNBT();
        for (WaveMob mob : mobs) mobsList.add(mob.save());
        tag.put("mobs", mobsList);

        // Wave spawn pos
        if (waveSpawnPos != null) tag.putLong("waveSpawnPos", waveSpawnPos.asLong());
        tag.putInt("activateFromWave", activateFromWave);
        tag.putBoolean("oneTimeOnly", oneTimeOnly);

        // Trigger fields — always saved so that disabling and re-enabling a trigger
        // doesn't lose the configured type, cooldown, and AND conditions.
        tag.putBoolean("triggerEnabled", triggerEnabled);
        tag.putString("triggerType", triggerType.name());
        tag.putString("cooldownMode", cooldownMode.name());
        tag.putInt("cooldownValue", cooldownValue);
        if (!triggerCustomItemId.isEmpty()) tag.putString("triggerCustomItemId", triggerCustomItemId);
        tag.putInt("triggerCustomValue", triggerCustomValue);
        if (!extraTriggers.isEmpty()) {
            net.minecraft.nbt.ListNBT etList = new net.minecraft.nbt.ListNBT();
            for (WaveTrigger t : extraTriggers) {
                net.minecraft.nbt.CompoundNBT et = new net.minecraft.nbt.CompoundNBT();
                et.putString("t", t.name());
                etList.add(et);
            }
            tag.put("extraTriggers", etList);
        }
        return tag;
    }

    public static WaveConfig load(CompoundNBT tag) {
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

        if (tag.contains("waveSpawnPos")) config.waveSpawnPos = net.minecraft.util.math.BlockPos.of(tag.getLong("waveSpawnPos"));
        config.activateFromWave = tag.contains("activateFromWave") ? tag.getInt("activateFromWave") : 0;
        config.oneTimeOnly = tag.contains("oneTimeOnly") && tag.getBoolean("oneTimeOnly");

        // Trigger fields — always loaded regardless of triggerEnabled so that
        // disabling and re-enabling a trigger restores the full configuration.
        config.triggerEnabled = tag.contains("triggerEnabled") && tag.getBoolean("triggerEnabled");
        try { config.triggerType = WaveTrigger.valueOf(tag.getString("triggerType")); } catch (Exception ignored) {}
        try { config.cooldownMode = CooldownMode.valueOf(tag.getString("cooldownMode")); } catch (Exception ignored) {}
        config.cooldownValue = tag.contains("cooldownValue") ? tag.getInt("cooldownValue") : 0;
        config.triggerCustomItemId = tag.contains("triggerCustomItemId") ? tag.getString("triggerCustomItemId") : "";
        config.triggerCustomValue  = tag.contains("triggerCustomValue")  ? tag.getInt("triggerCustomValue")  : 60;
        if (tag.contains("extraTriggers")) {
            net.minecraft.nbt.ListNBT etList = tag.getList("extraTriggers", 10);
            for (int i = 0; i < etList.size(); i++) {
                try { config.extraTriggers.add(WaveTrigger.valueOf(etList.getCompound(i).getString("t"))); }
                catch (Exception ignored) {}
            }
        }

        ListNBT mobsList = tag.getList("mobs", 10);
        for (int i = 0; i < mobsList.size(); i++) config.mobs.add(WaveMob.load(mobsList.getCompound(i)));
        return config;
    }
}
