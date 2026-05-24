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

    // ── Тригерна хвиля ───────────────────────────────────────────────
    // Якщо triggerEnabled=true — хвиля запускається незалежно по тригеру
    // а не по загальному порядку. Може бути одночасно з іншою хвилею.
    private boolean triggerEnabled   = false;
    private WaveTrigger triggerType  = WaveTrigger.PLAYER_OPEN_CHEST;
    // Мультитригери: всі умови мають бути виконані одночасно (AND)
    private java.util.List<WaveTrigger> extraTriggers = new java.util.ArrayList<>();
    // Предмет для тригера PLAYER_HAS_ITEM (registry id, наприклад "minecraft:diamond")
    // Підтримує кілька предметів через кому: "minecraft:diamond,minecraft:iron_ingot"
    private String triggerCustomItemId = "";
    // Числове значення для тригерів TIMER_CUSTOM (сек), MOBS_KILLED_N (кількість), WAVES_SURVIVED_N (кількість)
    private int    triggerCustomValue  = 60;   // за замовчуванням 60 сек для TIMER_CUSTOM
    // Перезарядка після спрацювання:
    //   cooldownMode=NONE — немає перезарядки
    //   cooldownMode=SECONDS — cooldownValue секунд
    //   cooldownMode=WAVES — cooldownValue хвиль основних хвиль
    public enum CooldownMode { NONE, SECONDS, WAVES }
    private CooldownMode cooldownMode  = CooldownMode.NONE;
    private int          cooldownValue = 0;

    // Окрема точка спавну мобів для цієї хвилі (пріорітет над точками локації)
    // null = використовуються точки спавну локації
    private net.minecraft.core.BlockPos waveSpawnPos = null;

    // Активувати хвилю тільки починаючи з певної хвилі (0 = завжди)
    private int activateFromWave = 0;

    // Разово: спрацювати лише один раз за сесію локації
    private boolean oneTimeOnly = false;
    private boolean firedThisSession = false; // runtime, не serialized

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
    public boolean hasCompletionCommand() { return completionCommand != null && !completionCommand.isBlank(); }

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

    public net.minecraft.core.BlockPos getWaveSpawnPos() { return waveSpawnPos; }
    public void setWaveSpawnPos(net.minecraft.core.BlockPos pos) { this.waveSpawnPos = pos; }
    public boolean hasWaveSpawnPos() { return waveSpawnPos != null; }

    public int  getActivateFromWave()      { return activateFromWave; }
    public void setActivateFromWave(int w) { this.activateFromWave = Math.max(0, w); }

    public boolean isOneTimeOnly()           { return oneTimeOnly; }
    public void    setOneTimeOnly(boolean v) { this.oneTimeOnly = v; }
    public boolean isFiredThisSession()      { return firedThisSession; }
    public void    setFiredThisSession(boolean v) { this.firedThisSession = v; }

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
            net.minecraft.nbt.ListTag etList = new net.minecraft.nbt.ListTag();
            for (WaveTrigger t : extraTriggers) {
                net.minecraft.nbt.CompoundTag et = new net.minecraft.nbt.CompoundTag();
                et.putString("t", t.name());
                etList.add(et);
            }
            tag.put("extraTriggers", etList);
        }
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

        if (tag.contains("waveSpawnPos")) config.waveSpawnPos = net.minecraft.core.BlockPos.of(tag.getLong("waveSpawnPos"));
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
            net.minecraft.nbt.ListTag etList = tag.getList("extraTriggers", 10);
            for (int i = 0; i < etList.size(); i++) {
                try { config.extraTriggers.add(WaveTrigger.valueOf(etList.getCompound(i).getString("t"))); }
                catch (Exception ignored) {}
            }
        }

        ListTag mobsList = tag.getList("mobs", 10);
        for (int i = 0; i < mobsList.size(); i++) config.mobs.add(WaveMob.load(mobsList.getCompound(i)));
        return config;
    }
}
