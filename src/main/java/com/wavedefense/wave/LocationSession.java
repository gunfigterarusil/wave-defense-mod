package com.wavedefense.wave;

import com.wavedefense.data.GameStats;
import com.wavedefense.data.Location;
import com.wavedefense.data.PvpRoundState;
import com.wavedefense.data.WaveConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Весь стан однієї активної локації — замінює ~30 Map<String,T> у WaveContext.
 *
 * Створюється у {@link WaveManager#addPlayerToLocation} /
 * {@link PvpRoundManager#addPlayerToPvpLocation} і зберігається у
 * {@link WaveContext#sessions}.
 *
 * Завершення сесії → {@link WaveContext#removeSession(String)} → автоматично
 * очищає всі поля без ризику залишити «сироту» у десятках окремих Maps.
 */
public class LocationSession {

    public final String   locationName;
    /**
     * @deprecated Always {@code null} after deserialization — use
     * {@code WaveDefenseMod.locationManager.getLocation(locationName)} instead.
     * Retained to avoid breaking the constructor signature.
     */
    @Deprecated
    public final Location config;

    // ── Wave state ────────────────────────────────────────────────────
    public int  currentWave      = 1;
    public int  waveTimerTicks   = 0;
    public long startTimerMs     = 0L;   // 0 = таймер не активний; >0 = epoch-ms коли стартує хвиля
    public int  victoryLingerTicks = 0;
    public int  timer60          = 0;
    public int  timer120         = 0;
    public int  timer300         = 0;
    /** @deprecated Serialized for NBT compatibility but never written during gameplay (tickTimerCustomForLocation uses waveTriggerWaveCounters). */
    @Deprecated
    public int  timerCustom      = 0;
    /**
     * Grace-period countdown ticks when all players left mid-wave (PvE only).
     * -1 = inactive; > 0 = counting down; reaches 0 → session ends cleanly.
     * Not persisted — resets to -1 on server reload (safe: session re-syncs).
     */
    public int  graceTicksRemaining = -1;
    /** Default grace period: 30 seconds = 600 ticks. */
    public static final int GRACE_TICKS = 600;
    public GameStats stats       = null;

    // ── Mob tracking ─────────────────────────────────────────────────
    /** UUID мобів поточної основної хвилі. */
    public final Set<UUID> spawnedMobs = ConcurrentHashMap.newKeySet();
    /**
     * Моби тригерних хвиль: ключ = "trigger_N" (де N = індекс WaveConfig).
     * Зберігається окремо, щоб не заважати перевірці основної хвилі.
     */
    public final Map<String, Set<UUID>> triggerMobs = new ConcurrentHashMap<>();
    public int     waveStartMobCount = 0;
    public int     mobsKilled        = 0;
    /** True з моменту, коли HALF_MOBS_DEAD лут вже видано цієї хвилі. */
    public boolean halfMobsTriggered = false;

    // ── Wave triggers ─────────────────────────────────────────────────
    /**
     * Час останнього спрацювання кожної тригерної хвилі.
     * Ключ: "w" + waveIndex (без префіксу locName).
     */
    public final Map<String, Long>    waveTriggerLastFired    = new ConcurrentHashMap<>();
    /**
     * Лічильник завершених хвиль для cooldownMode=WAVES.
     * Ключ: "w" + waveIndex.
     */
    public final Map<String, Integer> waveTriggerWaveCounters = new ConcurrentHashMap<>();
    /**
     * Нещодавно спрацьовані event-triggered тригери.
     * Ключ: trigger.name() (без префіксу locName).
     */
    public final Map<String, Long>    recentlyFiredTriggers   = new ConcurrentHashMap<>();

    // ── Zone activation ───────────────────────────────────────────────
    public int        zoneCountdownTicker  = 0;   // тіків до активації; 0 = не активний
    public final Set<UUID> zonePlayersInRange = ConcurrentHashMap.newKeySet();
    public long       zoneCountdownStartMs = 0L;
    public int        zoneLateJoinTimer    = 0;
    public long       zoneOpenUntilMs      = 0L;  // до якого часу (epoch ms) зона відкрита

    // ── Portal ────────────────────────────────────────────────────────
    public BlockPos       portalPosition         = null;
    public int            portalPenaltyTimer      = 0;
    public int            portalRespawnTimer      = 0;
    public int            portalPenaltyWaveIndex  = 0;
    public final Set<UUID> portalPenaltyMobs      = ConcurrentHashMap.newKeySet();
    public BlockPos       portalEntryPosition     = null;
    public boolean        portalFirstPlayerEntered = false;
    public final Set<UUID> portalEnteredPlayers   = ConcurrentHashMap.newKeySet();
    public long           portalOpenUntilMs       = 0L;
    public long           portalGraceEndTime      = 0L;
    public int            portalEnteredCount      = 0;

    // ── PvP ───────────────────────────────────────────────────────────
    public PvpRoundState pvpState = null;  // null для PvE локацій

    // ── InfoPanel TextDisplay ─────────────────────────────────────────
    /**
     * UUID TextDisplay-ентітей: ключ "spawn" → панель спавну, "mob_N" → mob-panel.
     */
    public final Map<String, UUID> infoPanelEntityIds = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────

    /** Час дії "нещодавно спрацьованого" тригера (мс) — 10 секунд. */
    public static final long RECENTLY_FIRED_WINDOW_MS = 10_000L;

    /** Internal tick counter for periodic tasks (not persisted). */
    private int tickCount = 0;

    /**
     * True while a normal (non-trigger) wave is actively running.
     * Set to true in startNextWave() after a successful spawnWave(); false after
     * wave completion. Guards onWaveCompleted() so it only fires for real waves.
     * Not persisted — resets to false on reload (safe: the session will re-sync).
     */
    public boolean waveActive = false;

    /**
     * ResourceLocation of the MobEffect currently applied to all players by the
     * active wave's waveEffect setting. Null if no effect is active.
     * Not persisted — the effect is re-applied via the wave config on reload.
     */
    public net.minecraft.resources.ResourceLocation currentWaveEffectId = null;

    public LocationSession(String locationName, Location config) {
        this.locationName = locationName;
        this.config       = config;
    }

    /**
     * Очищає всі колекції сесії.
     * Викликається з {@link WaveContext#removeSession(String)} після завершення гри.
     */
    public void dispose() {
        spawnedMobs.clear();
        triggerMobs.clear();
        waveTriggerLastFired.clear();
        waveTriggerWaveCounters.clear();
        recentlyFiredTriggers.clear();
        zonePlayersInRange.clear();
        portalPenaltyMobs.clear();
        portalEnteredPlayers.clear();
        infoPanelEntityIds.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Повертає Set мобів тригерної хвилі з індексом {@code waveIndex}, створюючи якщо треба. */
    public Set<UUID> getTriggerMobs(int waveIndex) {
        return triggerMobs.computeIfAbsent("trigger_" + waveIndex, k -> ConcurrentHashMap.newKeySet());
    }

    /** Ключ для waveTriggerLastFired / waveTriggerWaveCounters. */
    public static String triggerKey(int waveIndex) {
        return "w" + waveIndex;
    }

    /** Marks a recently-fired event trigger (for AND-condition support). */
    public void markRecentlyFired(com.wavedefense.data.WaveTrigger trigger) {
        recentlyFiredTriggers.put(trigger.name(), System.currentTimeMillis());
    }

    /** Checks if an event trigger fired within the last {@code windowMs} ms. */
    public boolean wasRecentlyFired(com.wavedefense.data.WaveTrigger trigger, long windowMs) {
        Long t = recentlyFiredTriggers.get(trigger.name());
        return t != null && (System.currentTimeMillis() - t) < windowMs;
    }

    /**
     * Returns the start time of the current wave in milliseconds since epoch.
     * If no wave is active (startTimerMs is 0), returns 0.
     */
    public long getWaveStartTime() {
        if (startTimerMs > 0) {
            return startTimerMs;
        }
        // If wave timer is active but startTimerMs is 0, estimate from waveTimerTicks
        // This is a fallback for waves that started before monitoring was enabled
        if (waveTimerTicks > 0) {
            return System.currentTimeMillis() - (waveTimerTicks * 50L); // Approximate
        }
        return 0;
    }

    /**
     * Тік-апдейт для сесії локації — обробляє прогрес хвиль, таймери, мобів.
     * Викликається з {@link WaveManager#tickSession(LocationSession)} кожного тику.
     *
     * @param wm WaveManager для доступу до менеджерів та спавну
     * @param location поточна локація
     */
    public void tick(WaveManager wm, Location location) {
        tickCount++;

        // ── Victory linger countdown ──────────────────────────────────────
        // triggerVictory() sets victoryLingerTicks; we decrement here and end
        // the session when it reaches zero. This must be checked first so the
        // rest of tick() does not keep re-triggering victory every tick.
        if (victoryLingerTicks > 0) {
            victoryLingerTicks--;
            if (victoryLingerTicks == 0) {
                wm.endSessionForLocation(locationName);
            }
            return;
        }

        // ── Grace period: all players left mid-wave (PvE only) ───────────
        // Counts down; if nobody rejoins → clean shutdown.
        // Cancelled by SessionManager.addPlayer() when a player rejoins.
        if (graceTicksRemaining > 0) {
            graceTicksRemaining--;
            // Announce at 30 s, 20 s, 10 s, 5 s, 3 s, 1 s intervals
            int secsLeft = graceTicksRemaining / 20;
            boolean shouldAnnounce = graceTicksRemaining % 200 == 0  // every 10 s
                    || (secsLeft <= 5 && graceTicksRemaining % 20 == 0); // every 1 s in last 5 s
            if (shouldAnnounce && graceTicksRemaining > 0) {
                wm.broadcastToLocation(locationName,
                    net.minecraft.network.chat.Component.translatable(
                        "wavedefense.msg.grace_closing", secsLeft));
            }
            if (graceTicksRemaining == 0) {
                wm.endSessionForLocation(locationName);
            }
            return;
        }

        // ── Dead mob cleanup (every 40 ticks ≈ 2 seconds) ────────────────
        // Removes mobs that died outside onMobKilled() (void, /kill, fire, unloaded chunk…)
        // so their wave does not stall forever.
        // Searches ALL loaded dimensions, not just the first player's level.
        if (tickCount % 40 == 0 && !spawnedMobs.isEmpty()) {
            net.minecraft.server.MinecraftServer srv = com.wavedefense.WaveDefenseMod.getServer();
            if (srv != null) {
                int cleaned = 0;
                java.util.Iterator<UUID> it = spawnedMobs.iterator();
                while (it.hasNext()) {
                    UUID uuid = it.next();
                    boolean alive = false;
                    for (net.minecraft.server.level.ServerLevel lvl : srv.getAllLevels()) {
                        net.minecraft.world.entity.Entity e = lvl.getEntity(uuid);
                        if (e != null && e.isAlive()) { alive = true; break; }
                    }
                    if (!alive) { it.remove(); cleaned++; }
                }
                if (cleaned > 0) mobsKilled += cleaned;
            }
        }

        // ── Session timers (TIMER_60 / TIMER_120 / TIMER_300) ────────────────
        // Increment every tick once the lobby countdown has ended.
        // Each threshold fires: (a) the loot trigger once, (b) the wave trigger once.
        // TIMER_CUSTOM wave triggers are dispatched every tick via TriggerEvaluator.
        if (startTimerMs == 0L) {
            if (timer60  < 1200) {
                timer60++;
                if (timer60  == 1200) {
                    wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.TIMER_60);
                    wm.triggerEval.fireWaveTriggerForLocation(wm, locationName, com.wavedefense.data.WaveTrigger.TIMER_60);
                }
            }
            if (timer120 < 2400) {
                timer120++;
                if (timer120 == 2400) {
                    wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.TIMER_120);
                    wm.triggerEval.fireWaveTriggerForLocation(wm, locationName, com.wavedefense.data.WaveTrigger.TIMER_120);
                }
            }
            if (timer300 < 6000) {
                timer300++;
                if (timer300 == 6000) {
                    wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.TIMER_300);
                    wm.triggerEval.fireWaveTriggerForLocation(wm, locationName, com.wavedefense.data.WaveTrigger.TIMER_300);
                }
            }
            // TIMER_CUSTOM: independent per-wave-index counters, ticked every server tick
            wm.triggerEval.tickTimerCustomForLocation(wm, locationName);
        }

        // Обробка таймера старту локації (лоббі)
        if (startTimerMs > 0) {
            long now = System.currentTimeMillis();
            if (now >= startTimerMs) {
                // Час лоббі вийшов — застосовуємо firstWaveDelaySec перед 1-ю хвилею
                startTimerMs = 0L;
                int firstDelay = location.getFirstWaveDelaySec();
                if (firstDelay > 0 && currentWave == 1) {
                    waveTimerTicks = firstDelay * 20; // use wave-between timer to add delay
                } else {
                    waveTimerTicks = 0;
                    startNextWave(wm, location);
                }
            }
            return; // Поки активний таймер старту, інші логіки не потрібні
        }

        // Обробка таймера між хвиль
        if (waveTimerTicks > 0) {
            waveTimerTicks--;
            if (waveTimerTicks <= 0) {
                // Час між хвилями вийшов — запускаємо наступну хвилю
                startNextWave(wm, location);
            }
            return;
        }

        // Перевірка на завершення хвилі: всі моби вбиті?
        if (spawnedMobs.isEmpty() && currentWave >= 1 && startTimerMs == 0L) {
            // Всі моби знищено, перевіряємо чи є ще хвилі
            if (currentWave <= location.getTotalWaves()) {
                // Fire per-wave rewards/commands only when a real wave was running
                if (waveActive) {
                    List<WaveConfig> waves = location.getWaves();
                    if (!waves.isEmpty()) {
                        int completedIdx = (currentWave - 1) % waves.size();
                        onWaveCompleted(waves.get(completedIdx), wm, location);
                    }
                    waveActive = false;
                }
                int delay = location.getTimeBetweenWaves();
                if (delay > 0) {
                    waveTimerTicks = delay * 20; // секунди → тіки
                    currentWave++; // Переходимо до наступної хвилі після затримки
                } else {
                    currentWave++; // Переходимо до наступної хвилі
                    startNextWave(wm, location);
                }
            } else {
                // Всі хвилі пройдено — фінальна нагорода, тригер перемоги
                if (waveActive) {
                    List<WaveConfig> waves = location.getWaves();
                    if (!waves.isEmpty()) {
                        int completedIdx = (currentWave - 1) % waves.size();
                        onWaveCompleted(waves.get(completedIdx), wm, location);
                    }
                    waveActive = false;
                }
                removeWaveEffect(wm);
                wm.triggerVictory(location.getName());
            }
        }
    }

    /**
     * Запускає наступну хвилю для цієї сесії.
     */
    private void startNextWave(WaveManager wm, Location location) {
        // Перевіряємо чи є ще хвилі
        if (currentWave > location.getTotalWaves()) {
            // Всі хвилі вже пройдено
            removeWaveEffect(wm);
            wm.triggerVictory(location.getName());
            return;
        }

        // Отримуємо конфігурацію хвилі
        List<WaveConfig> waves = location.getWaves();
        if (waves.isEmpty()) {
            // Немає конфігурацій хвиль — завершуємо
            removeWaveEffect(wm);
            wm.triggerVictory(location.getName());
            return;
        }

        // Визначаємо індекс хвилі (циклічний якщо хвиль менше ніж totalWaves)
        int waveIndex = (currentWave - 1) % waves.size();
        WaveConfig waveConfig = waves.get(waveIndex);

        // Start tracking metrics for auto-scaling this wave
        WaveAutoScaler.WaveMetrics metrics = wm.getAutoScaler().startWave(currentWave, 20.0f);

        // Скидаємо стан тригерів для нової хвилі
        halfMobsTriggered = false;

        // Remove the previous wave's effect before the new wave starts
        removeWaveEffect(wm);

        // Спавнимо хвилю
        boolean spawned = wm.getMobSpawnManager().spawnWave(location.getName(), waveConfig, currentWave, wm);

        if (spawned) {
            // Хвиля успішно запущена
            waveTimerTicks = 0;
            waveActive = true;
            // Fire loot triggers
            if (currentWave == 1) {
                wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.LOCATION_START);
            }
            wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.WAVE_START);
            wm.fireLootTriggerByNameWithValue(locationName, com.wavedefense.data.LootSpawn.Trigger.WAVE_N, currentWave);
            // Apply this wave's effect to all current players (if configured)
            if (waveConfig.hasEffect()) {
                applyWaveEffect(waveConfig.getWaveEffect(), waveConfig.getWaveEffectAmplifier(), wm);
            }
        } else {
            // Spawn failed — log the issue; do NOT double-increment currentWave
            // (caller in tick() already incremented it once). The main tick() loop
            // will advance to the next wave on the very next tick.
            waveActive = false;
            com.wavedefense.WaveDefenseMod.LOGGER.warn(
                "[WaveDefense] spawnWave failed for wave {}/{} in '{}' — skipping",
                currentWave, location.getTotalWaves(), locationName);
        }
    }

    // ── Per-wave completion handling ───────────────────────────────────

    /**
     * Called when the last mob of a normal wave dies. Distributes per-wave
     * pointsReward to all players and executes completionCommand if configured.
     */
    private void onWaveCompleted(WaveConfig wc, WaveManager wm, Location location) {
        // Fire WAVE_END loot trigger
        wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.WAVE_END);
        // Distribute per-wave points to every player in the location
        if (wc.getPointsReward() > 0) {
            for (net.minecraft.server.level.ServerPlayer player : wm.getPlayersInLocation(locationName)) {
                location.addPoints(player.getUUID(), wc.getPointsReward());
                wm.syncPlayerData(player);
            }
        }
        // Execute per-wave completion command (%location%, %wave%, %players% substitutions)
        if (wc.hasCompletionCommand()) {
            try {
                String cmd = wc.getCompletionCommand()
                    .replace("%location%", locationName)
                    .replace("%wave%", String.valueOf(currentWave))
                    .replace("%players%", String.valueOf(wm.getPlayersInLocation(locationName).size()));
                net.minecraft.server.MinecraftServer srv = com.wavedefense.WaveDefenseMod.getServer();
                if (srv != null) {
                    srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd);
                }
            } catch (Exception e) {
                com.wavedefense.WaveDefenseMod.LOGGER.warn(
                    "[WaveDefense] completionCommand failed for wave {} in '{}': {}",
                    currentWave, locationName, e.getMessage());
            }
        }
    }

    // ── Wave-effect helpers ───────────────────────────────────────────

    /**
     * Applies {@code effectId} (level {@code amplifier}) to all players in this
     * location. Duration: 1 h in ticks — effectively lasts the whole wave.
     * Stores the id in {@link #currentWaveEffectId} so it can be removed later.
     */
    private void applyWaveEffect(net.minecraft.resources.ResourceLocation effectId, int amplifier, WaveManager wm) {
        try {
            net.minecraft.world.effect.MobEffect effect =
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(effectId);
            if (effect == null) {
                com.wavedefense.WaveDefenseMod.LOGGER.warn(
                    "[WaveDefense] Unknown wave effect '{}' for wave {} in '{}' — skipping",
                    effectId, currentWave, locationName);
                return;
            }
            // 1 hour in ticks — the effect will be refreshed each wave
            int duration = 72000;
            for (net.minecraft.server.level.ServerPlayer player : wm.getPlayersInLocation(locationName)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effect, duration, amplifier, false, true, true));
            }
            currentWaveEffectId = effectId;
        } catch (Exception e) {
            com.wavedefense.WaveDefenseMod.LOGGER.warn(
                "[WaveDefense] Failed to apply wave effect '{}' in '{}': {}",
                effectId, locationName, e.getMessage());
        }
    }

    /**
     * Removes the currently-active wave effect from all players.
     * No-op if {@link #currentWaveEffectId} is null.
     */
    private void removeWaveEffect(WaveManager wm) {
        if (currentWaveEffectId == null) return;
        try {
            net.minecraft.world.effect.MobEffect effect =
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(currentWaveEffectId);
            if (effect != null) {
                for (net.minecraft.server.level.ServerPlayer player : wm.getPlayersInLocation(locationName)) {
                    player.removeEffect(effect);
                }
            }
        } catch (Exception e) {
            com.wavedefense.WaveDefenseMod.LOGGER.warn(
                "[WaveDefense] Failed to remove wave effect '{}' in '{}': {}",
                currentWaveEffectId, locationName, e.getMessage());
        }
        currentWaveEffectId = null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану сесії локації. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("locationName", locationName);
        // Wave state
        tag.putInt("currentWave", currentWave);
        tag.putInt("waveTimerTicks", waveTimerTicks);
        tag.putLong("startTimerMs", startTimerMs);
        tag.putInt("victoryLingerTicks", victoryLingerTicks);
        tag.putInt("timer60", timer60);
        tag.putInt("timer120", timer120);
        tag.putInt("timer300", timer300);
        // timerCustom intentionally NOT saved — field is deprecated/dead (tickTimerCustomForLocation uses waveTriggerWaveCounters)
        if (stats != null) {
            tag.put("stats", stats.save());
        }
        // Mob tracking
        tag.put("spawnedMobs", saveUuidSet(spawnedMobs));
        CompoundTag triggerMobsTag = new CompoundTag();
        for (Map.Entry<String, Set<UUID>> entry : triggerMobs.entrySet()) {
            triggerMobsTag.put(entry.getKey(), saveUuidSet(entry.getValue()));
        }
        tag.put("triggerMobs", triggerMobsTag);
        tag.putInt("waveStartMobCount", waveStartMobCount);
        tag.putInt("mobsKilled", mobsKilled);
        tag.putBoolean("halfMobsTriggered", halfMobsTriggered);
        // Wave triggers
        tag.put("waveTriggerLastFired", saveLongMap(waveTriggerLastFired));
        tag.put("waveTriggerWaveCounters", saveIntMap(waveTriggerWaveCounters));
        tag.put("recentlyFiredTriggers", saveLongMap(recentlyFiredTriggers));
        // Zone activation
        tag.putInt("zoneCountdownTicker", zoneCountdownTicker);
        tag.put("zonePlayersInRange", saveUuidSet(zonePlayersInRange));
        tag.putLong("zoneCountdownStartMs", zoneCountdownStartMs);
        tag.putInt("zoneLateJoinTimer", zoneLateJoinTimer);
        tag.putLong("zoneOpenUntilMs", zoneOpenUntilMs);
        // Portal
        if (portalPosition != null) {
            tag.putInt("portalX", portalPosition.getX());
            tag.putInt("portalY", portalPosition.getY());
            tag.putInt("portalZ", portalPosition.getZ());
        }
        tag.putInt("portalPenaltyTimer", portalPenaltyTimer);
        tag.putInt("portalRespawnTimer", portalRespawnTimer);
        tag.putInt("portalPenaltyWaveIndex", portalPenaltyWaveIndex);
        tag.put("portalPenaltyMobs", saveUuidSet(portalPenaltyMobs));
        if (portalEntryPosition != null) {
            tag.putInt("portalEntryX", portalEntryPosition.getX());
            tag.putInt("portalEntryY", portalEntryPosition.getY());
            tag.putInt("portalEntryZ", portalEntryPosition.getZ());
        }
        tag.putBoolean("portalFirstPlayerEntered", portalFirstPlayerEntered);
        tag.put("portalEnteredPlayers", saveUuidSet(portalEnteredPlayers));
        tag.putLong("portalOpenUntilMs", portalOpenUntilMs);
        tag.putLong("portalGraceEndTime", portalGraceEndTime);
        tag.putInt("portalEnteredCount", portalEnteredCount);
        // PvP
        if (pvpState != null) {
            tag.put("pvpState", pvpState.save());
        }
        // InfoPanel
        CompoundTag infoPanelTag = new CompoundTag();
        for (Map.Entry<String, UUID> entry : infoPanelEntityIds.entrySet()) {
            infoPanelTag.putUUID(entry.getKey(), entry.getValue());
        }
        tag.put("infoPanelEntityIds", infoPanelTag);
        return tag;
    }

    /** Відновлення стану сесії локації. */
    public static LocationSession load(CompoundTag tag) {
        String locationName = tag.getString("locationName");
        // config will be loaded separately from LocationManager
        LocationSession sess = new LocationSession(locationName, null);
        // Wave state
        sess.currentWave = tag.getInt("currentWave");
        sess.waveTimerTicks = tag.getInt("waveTimerTicks");
        sess.startTimerMs = tag.getLong("startTimerMs");
        sess.victoryLingerTicks = tag.getInt("victoryLingerTicks");
        sess.timer60 = tag.getInt("timer60");
        sess.timer120 = tag.getInt("timer120");
        sess.timer300 = tag.getInt("timer300");
        // timerCustom intentionally NOT loaded — field is deprecated/dead
        if (tag.contains("stats")) {
            sess.stats = GameStats.load(tag.getCompound("stats"));
        }
        // Mob tracking
        sess.spawnedMobs.addAll(loadUuidSet(tag.getList("spawnedMobs", 8)));
        CompoundTag triggerMobsTag = tag.getCompound("triggerMobs");
        for (String key : triggerMobsTag.getAllKeys()) {
            sess.triggerMobs.put(key, loadUuidSet(triggerMobsTag.getList(key, 8)));
        }
        sess.waveStartMobCount = tag.getInt("waveStartMobCount");
        sess.mobsKilled = tag.getInt("mobsKilled");
        sess.halfMobsTriggered = tag.getBoolean("halfMobsTriggered");
        // Wave triggers
        sess.waveTriggerLastFired.putAll(loadLongMap(tag.getCompound("waveTriggerLastFired")));
        sess.waveTriggerWaveCounters.putAll(loadIntMap(tag.getCompound("waveTriggerWaveCounters")));
        sess.recentlyFiredTriggers.putAll(loadLongMap(tag.getCompound("recentlyFiredTriggers")));
        // Zone activation
        sess.zoneCountdownTicker = tag.getInt("zoneCountdownTicker");
        sess.zonePlayersInRange.addAll(loadUuidSet(tag.getList("zonePlayersInRange", 8)));
        sess.zoneCountdownStartMs = tag.getLong("zoneCountdownStartMs");
        sess.zoneLateJoinTimer = tag.getInt("zoneLateJoinTimer");
        sess.zoneOpenUntilMs = tag.getLong("zoneOpenUntilMs");
        // Portal
        if (tag.contains("portalX")) {
            sess.portalPosition = new BlockPos(
                tag.getInt("portalX"), tag.getInt("portalY"), tag.getInt("portalZ"));
        }
        sess.portalPenaltyTimer = tag.getInt("portalPenaltyTimer");
        sess.portalRespawnTimer = tag.getInt("portalRespawnTimer");
        sess.portalPenaltyWaveIndex = tag.getInt("portalPenaltyWaveIndex");
        sess.portalPenaltyMobs.addAll(loadUuidSet(tag.getList("portalPenaltyMobs", 8)));
        if (tag.contains("portalEntryX")) {
            sess.portalEntryPosition = new BlockPos(
                tag.getInt("portalEntryX"), tag.getInt("portalEntryY"), tag.getInt("portalEntryZ"));
        }
        sess.portalFirstPlayerEntered = tag.getBoolean("portalFirstPlayerEntered");
        sess.portalEnteredPlayers.addAll(loadUuidSet(tag.getList("portalEnteredPlayers", 8)));
        sess.portalOpenUntilMs = tag.getLong("portalOpenUntilMs");
        sess.portalGraceEndTime = tag.getLong("portalGraceEndTime");
        sess.portalEnteredCount = tag.getInt("portalEnteredCount");
        // PvP
        if (tag.contains("pvpState")) {
            sess.pvpState = PvpRoundState.load(tag.getCompound("pvpState"));
        }
        // InfoPanel
        if (tag.contains("infoPanelEntityIds")) {
            CompoundTag infoPanelTag = tag.getCompound("infoPanelEntityIds");
            for (String key : infoPanelTag.getAllKeys()) {
                sess.infoPanelEntityIds.put(key, infoPanelTag.getUUID(key));
            }
        }
        return sess;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helper methods for NBT serialization
    // ─────────────────────────────────────────────────────────────────

    private static ListTag saveUuidSet(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID uuid : set) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        return list;
    }

    private static Set<UUID> loadUuidSet(ListTag list) {
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < list.size(); i++) {
            set.add(UUID.fromString(list.getString(i)));
        }
        return set;
    }

    private static CompoundTag saveLongMap(Map<String, Long> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            tag.putLong(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    private static Map<String, Long> loadLongMap(CompoundTag tag) {
        Map<String, Long> map = new ConcurrentHashMap<>();
        for (String key : tag.getAllKeys()) {
            map.put(key, tag.getLong(key));
        }
        return map;
    }

    private static CompoundTag saveIntMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            tag.putInt(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    private static Map<String, Integer> loadIntMap(CompoundTag tag) {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        for (String key : tag.getAllKeys()) {
            map.put(key, tag.getInt(key));
        }
        return map;
    }
}
