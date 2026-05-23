package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Оцінює та запускає wave-тригери і location-тригери.
 * Відповідає за: polling-тригери, event-triggered хвилі,
 * cooldown-логіку і спавн trigger-хвиль.
 */
public class TriggerEvaluator {

    private final WaveContext ctx;

    private static final long MIN_TRIGGER_WAVE_COOLDOWN_MS = 5000L;

    public TriggerEvaluator(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ════════════════════════════════════════════════════════════════════

    public void tick(WaveManager wm) {
        tickLocationTriggers(wm);
        tickWaveTriggers(wm);
    }

    // ════════════════════════════════════════════════════════════════════
    //  LOCATION TRIGGERS
    // ════════════════════════════════════════════════════════════════════

    private void tickLocationTriggers(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;
        long now = System.currentTimeMillis();
        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!loc.isLocationTriggerEnabled()) continue;
            if (loc.isPvp()) continue;
            if (loc.getPlayerSpawn() == null) continue;

            String name = loc.getName();
            // Якщо вже активна — пропускаємо
            boolean active = ctx.playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(name));
            if (active) continue;

            // Збираємо гравців поблизу (не в локації)
            // Радіус: якщо locationTriggerEnabled — використовуємо autoActivateRadius або boundary
            int radius = Math.max(5, loc.isAutoActivate()
                ? loc.getAutoActivateRadius()
                : (loc.isLocationBoundaryEnabled() ? loc.getLocationBoundaryRadius() : 50));

            java.util.List<ServerPlayer> nearbyPlayers = new java.util.ArrayList<>();
            for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                if (ctx.playerData.containsKey(p.getUUID())) continue;
                if (p.blockPosition().distSqr(loc.getPlayerSpawn()) <= (double) radius * radius) {
                    nearbyPlayers.add(p);
                }
            }

            WaveTrigger trigger = loc.getLocationTriggerType();
            boolean fired = false;

            switch (trigger) {
                case PLAYER_ENTER_ZONE:
                    // Спрацьовує коли хтось увійшов у радіус
                    fired = !nearbyPlayers.isEmpty();
                    break;
                case PLAYER_DEATH:
                case PLAYER_JOIN:
                    // event-driven — обробляються в EventHandler.onEntityDeath / PlayerEvent
                    fired = false;
                    break;
                case PLAYER_FULL_INVENT:
                case PLAYER_HAS_DIAMOND:
                case PLAYER_HAS_IRON:
                case PLAYER_HAS_SWORD:
                case PLAYER_HAS_ITEM:
                case PLAYER_LOW_HEALTH:
                    // Для запуску локації — перевіряємо будь-якого гравця поблизу
                    fired = !nearbyPlayers.isEmpty() && checkWaveTriggerCondition(trigger, name, null);
                    break;
                default:
                    // Всі інші — стандартна перевірка
                    fired = checkWaveTriggerCondition(trigger, name, null);
                    break;
            }

            if (!fired) continue;

            // Запускаємо для всіх гравців що знаходяться поблизу
            java.util.Set<UUID> inRange = new java.util.HashSet<>();
            for (ServerPlayer p : nearbyPlayers) inRange.add(p.getUUID());
            // Примітка: якщо nearbyPlayers порожній — НЕ додаємо всіх гравців сервера
            // Телепортуємо тільки гравців що РЕАЛЬНО знаходяться у радіусі — без fallback
            if (!inRange.isEmpty()) {
                wm.boundaryMgr.activateZoneForPlayers(wm, loc, inRange);
                wm.debugAdmin("§7Location trigger §e" + name
                    + "§7: §e" + inRange.size() + "§7 players teleported to location");
                wm.debugLog("Location trigger '" + name + "': " + inRange.size() + " players teleported");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  TRIGGER WAVE — хвиля що запускається по тригеру
    // ════════════════════════════════════════════════════════════════════

    /**
     * Запускає тригерні хвилі для заданої локації по event-driven тригеру.
     * Використовується для: TIMER_60/120/300, MOBS_REMAINING_LOW, WAVE_COMPLETE тощо.
     */
    public void fireWaveTriggerForLocation(WaveManager wm, String locName, WaveTrigger trigger) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locName);
        if (loc == null || loc.isPvp()) return;
        LocationSession s = ctx.getSession(locName);
        int curWave = s != null ? s.currentWave : 1;

        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != trigger) continue;
            if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
            if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
            boolean andOk = true;
            java.util.List<WaveTrigger> extras = wave.getExtraTriggers();
            for (WaveTrigger extra : (extras != null ? extras : java.util.Collections.<WaveTrigger>emptyList())) {
                boolean extraOk = (extra == WaveTrigger.PLAYER_HAS_ITEM)
                    ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                    : checkWaveTriggerCondition(extra, locName, null);
                if (!extraOk) { andOk = false; break; }
            }
            if (!andOk) continue;
            String coolKey = LocationSession.triggerKey(wi);
            if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
            fireTriggerWave(wm, loc, wi);
            recordTriggerWaveFire(wave, coolKey, locName);
            if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
        }
    }

    private void tickWaveTriggers(WaveManager wm) {
        // Н1: removed dead loop that immediately broke without doing anything
        for (String locName : ctx.getActiveLocationNames()) {
            Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc == null || loc.isPvp()) continue;
            LocationSession s = ctx.getSession(locName);
            for (int wi = 0; wi < loc.getWaves().size(); wi++) {
                WaveConfig wave = loc.getWaves().get(wi);
                if (!wave.isTriggerEnabled()) continue;
                // Перевіряємо cooldown
                String coolKey = LocationSession.triggerKey(wi);
                if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
                // Перевіряємо тригер
                // TIMER_* обробляються окремим per-location timer loop — тут пропускаємо
                WaveTrigger wTrigType = wave.getTriggerType();
                if (wTrigType == WaveTrigger.TIMER_60 ||
                    wTrigType == WaveTrigger.TIMER_120 ||
                    wTrigType == WaveTrigger.TIMER_300 ||
                    wTrigType == WaveTrigger.TIMER_CUSTOM) continue; // handled separately
                // PLAYER_HAS_ITEM: перевіряємо itemId ЦІЄЇ хвилі, не шукаємо по всіх
                if (wTrigType == WaveTrigger.PLAYER_HAS_ITEM) {
                    if (!checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)) continue;
                } else {
                    if (!checkWaveTriggerCondition(wTrigType, locName, null)) continue;
                }
                // Разово
                if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
                // activateFromWave
                int curWave2 = s != null ? s.currentWave : 1;
                if (wave.getActivateFromWave() > 0 && curWave2 < wave.getActivateFromWave()) continue;
                // AND умови
                boolean andOk = true;
                java.util.List<WaveTrigger> extras = wave.getExtraTriggers();
                for (WaveTrigger extra : (extras != null ? extras : java.util.Collections.<WaveTrigger>emptyList())) {
                    if (!checkWaveTriggerCondition(extra, locName, null)) { andOk = false; break; }
                }
                if (!andOk) continue;
                // Запускаємо хвилю паралельно
                fireTriggerWave(wm, loc, wi);
                recordTriggerWaveFire(wave, coolKey, locName);
                if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
            }
        }
    }

    /**
     * Тікає TIMER_CUSTOM тригери для конкретної локації.
     * Кожна хвиля-тригер з TIMER_CUSTOM має свій незалежний лічильник.
     * Лічильники тіків зберігаються у session.waveTriggerWaveCounters з ключем "tc_" + waveIndex.
     */
    public void tickTimerCustomForLocation(WaveManager wm, String locName) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locName);
        if (loc == null) return;
        LocationSession s = ctx.getSession(locName);
        if (s == null) return;
        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != WaveTrigger.TIMER_CUSTOM) continue;
            int intervalTicks = Math.max(5, wave.getTriggerCustomValue()) * 20;
            String tcKey = "tc_" + wi;
            int ticks = s.waveTriggerWaveCounters.getOrDefault(tcKey, 0) + 1;
            s.waveTriggerWaveCounters.put(tcKey, ticks);
            if (ticks >= intervalTicks) {
                s.waveTriggerWaveCounters.put(tcKey, 0);
                // Перевіряємо всі умови і файримо хвилю
                if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
                int curWave = s.currentWave;
                if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
                boolean andOk = true;
                for (WaveTrigger extra : (wave.getExtraTriggers() != null ? wave.getExtraTriggers() : java.util.Collections.<WaveTrigger>emptyList())) {
                    boolean extraOk = (extra == WaveTrigger.PLAYER_HAS_ITEM)
                        ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                        : checkWaveTriggerCondition(extra, locName, null);
                    if (!extraOk) { andOk = false; break; }
                }
                if (!andOk) continue;
                String coolKey = LocationSession.triggerKey(wi);
                if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
                fireTriggerWave(wm, loc, wi);
                recordTriggerWaveFire(wave, coolKey, locName);
                if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
            }
        }
    }

    boolean isTriggerWaveCooldownReady(WaveConfig wave, String key, String locName) {
        // key is already in the normalized "w"+waveIndex form (LocationSession.triggerKey)
        LocationSession s = ctx.getSession(locName);
        long lastFired = s != null ? s.waveTriggerLastFired.getOrDefault(key, 0L) : 0L;
        long elapsed   = System.currentTimeMillis() - lastFired;

        switch (wave.getCooldownMode()) {
            case NONE:
                // Тільки обов'язкові 5 сек мінімум
                return elapsed >= MIN_TRIGGER_WAVE_COOLDOWN_MS;
            case SECONDS: {
                // Користувацький кулдаун (але мінімум 5 сек)
                long required = Math.max(MIN_TRIGGER_WAVE_COOLDOWN_MS, wave.getCooldownValue() * 1000L);
                return elapsed >= required;
            }
            case WAVES: {
                // Спочатку мінімальна пауза, потім хвильовий кулдаун
                if (elapsed < MIN_TRIGGER_WAVE_COOLDOWN_MS) return false;
                int wavesDone = s != null ? s.waveTriggerWaveCounters.getOrDefault(key, 0) : 0;
                return wavesDone >= wave.getCooldownValue();
            }
        }
        return elapsed >= MIN_TRIGGER_WAVE_COOLDOWN_MS;
    }

    void recordTriggerWaveFire(WaveConfig wave, String key, String locName) {
        // key is already in the normalized "w"+waveIndex form (LocationSession.triggerKey)
        LocationSession s = ctx.getSession(locName);
        // Завжди записуємо час — для забезпечення мінімальної 5-секундної паузи
        if (s != null) {
            s.waveTriggerLastFired.put(key, System.currentTimeMillis());
            if (wave.getCooldownMode() == WaveConfig.CooldownMode.WAVES) {
                s.waveTriggerWaveCounters.put(key, 0);
            }
        }
    }

    /** Скидаємо лічильник хвиль у cooldown при завершенні основної хвилі */
    void incrementWaveTriggerCounters(String locName) {
        LocationSession s = ctx.getSession(locName);
        if (s != null) s.waveTriggerWaveCounters.replaceAll((k, v) -> v + 1);
    }

    void fireTriggerWave(WaveManager wm, Location location, int waveIndex) {
        if (waveIndex < 0 || waveIndex >= location.getWaves().size()) return;
        List<ServerPlayer> players = ctx.getPlayersInLocation(location.getName());
        if (players.isEmpty()) return;
        ServerLevel world = players.get(0).serverLevel();
        WaveConfig wave = location.getWaves().get(waveIndex);

        wm.broadcastToLocation(location.getName(),
            Component.translatable("wavedefense.msg.trigger_wave_launched", (waveIndex+1)));

        // С8: if session is null mobs would go untracked and never despawn — abort instead
        LocationSession session = ctx.getSession(location.getName());
        if (session == null) return;
        Set<UUID> spawnedMobs = session.getTriggerMobs(waveIndex);

        Random rng = new Random();
        for (WaveMob waveMob : wave.getMobs()) {
            net.minecraft.world.entity.EntityType<?> entityType =
                ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;
            int count = waveMob.getCount();
            for (int i = 0; i < count; i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                // Пріоритет: waveSpawnPos хвилі → точки спавну локації
                BlockPos sp = wave.hasWaveSpawnPos()
                    ? wave.getWaveSpawnPos()
                    : wm.getRandomSpawnPoint(location);
                if (sp == null) continue;
                try {
                    net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) entityType.create(world);
                    if (mob != null) {
                        mob.moveTo(sp.getX()+0.5, sp.getY(), sp.getZ()+0.5, 0, 0);
                        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(sp),
                            net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                        mob.goalSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                            mob, Player.class, true));
                        mob.setPersistenceRequired();
                        mob.getPersistentData().putString("location", location.getName());
                        mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());
                        wm.applyMobEquipment(mob, waveMob);
                        world.addFreshEntity(mob);
                        spawnedMobs.add(mob.getUUID());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Перевіряє чи має будь-який гравець у локації один із предметів зі списку через кому.
     * Використовується для PLAYER_HAS_ITEM тригера з конкретної хвилі.
     */
    private boolean checkPlayerHasItemMulti(String itemIds, String locName) {
        if (itemIds == null || itemIds.isBlank()) return false;
        java.util.List<ServerPlayer> players = ctx.getPlayersInLocation(locName);
        for (String part : itemIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(trimmed);
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) continue;
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                for (ServerPlayer p : players) { if (p.getInventory().contains(stack)) return true; }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean checkWaveTriggerCondition(WaveTrigger trigger,
                                               String locName, ServerPlayer actor) {
        List<ServerPlayer> players = ctx.getPlayersInLocation(locName);
        LocationSession s = ctx.getSession(locName);
        GameStats stats = s != null ? s.stats : null;
        switch (trigger) {
            case WAVE_COMPLETE:
                // Як AND умова: перевіряємо чи завершена хоча б одна хвиля
                return (s != null ? s.currentWave : 1) > 1;
            case MOBS_REMAINING_LOW: {
                Set<UUID> mobs = s != null ? s.spawnedMobs : null;
                if (mobs == null || mobs.isEmpty()) return false;
                int orig = s != null ? s.waveStartMobCount : 1;
                return mobs.size() <= orig / 5;
            }
            case TIMER_60:  case TIMER_120: case TIMER_300:
                // Для AND умов: перевіряємо час від старту локації
                {
                    long startMs = s != null ? s.startTimerMs : 0L;
                    long elapsed = (System.currentTimeMillis() - startMs) / 1000;
                    if (trigger == WaveTrigger.TIMER_60)  return elapsed >= 60;
                    if (trigger == WaveTrigger.TIMER_120) return elapsed >= 120;
                    if (trigger == WaveTrigger.TIMER_300) return elapsed >= 300;
                    return false;
                }
            case PLAYER_JOIN:
                return actor != null || ctx.wasRecentlyFired(locName, WaveTrigger.PLAYER_JOIN);
            case PLAYER_DEATH:
                return actor != null || ctx.wasRecentlyFired(locName, WaveTrigger.PLAYER_DEATH);
            case PLAYER_OPEN_CHEST:
                return actor != null || ctx.wasRecentlyFired(locName, WaveTrigger.PLAYER_OPEN_CHEST);
            case PLAYER_OPEN_DOOR:
                return actor != null || ctx.wasRecentlyFired(locName, WaveTrigger.PLAYER_OPEN_DOOR);
            case PLAYER_ENTER_ZONE: {
                // Для тригерних хвиль: будь-який гравець у локації (вони вже всередині)
                // Для запуску локації: перевіряється окремо в tickLocationTriggers
                return !players.isEmpty();
            }
            case PLAYER_LOW_HEALTH: {
                for (ServerPlayer p : players) if (p.getHealth() <= 4) return true;
                return false;
            }
            case PLAYER_FULL_INVENT: {
                // Fires when ANY player has a completely full inventory.
                // Checks main inventory (27 slots) AND offhand — both must be occupied.
                for (ServerPlayer p : players) {
                    boolean mainFull    = p.getInventory().getFreeSlot() == -1;
                    boolean offhandFull = !p.getInventory().offhand.get(0).isEmpty();
                    if (mainFull && offhandFull) return true;
                }
                return false;
            }
            case PLAYER_HAS_DIAMOND: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(ss ->
                        ss.getItem() == net.minecraft.world.item.Items.DIAMOND ||
                        ss.getItem() instanceof net.minecraft.world.item.ArmorItem ai &&
                        ai.getMaterial() == net.minecraft.world.item.ArmorMaterials.DIAMOND)) return true;
                }
                return false;
            }
            case PLAYER_HAS_IRON: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(ss ->
                        ss.getItem() == net.minecraft.world.item.Items.IRON_INGOT ||
                        ss.getItem() instanceof net.minecraft.world.item.ArmorItem ai &&
                        ai.getMaterial() == net.minecraft.world.item.ArmorMaterials.IRON)) return true;
                }
                return false;
            }
            case PLAYER_HAS_SWORD: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(ss -> ss.getItem() instanceof net.minecraft.world.item.SwordItem)) return true;
                }
                return false;
            }
            case PLAYER_HAS_ITEM: {
                // Шукаємо customItemId(s) у хвилях локації з цим тригером (підтримує кілька через кому)
                Location loc2 = WaveDefenseMod.locationManager.getLocation(locName);
                String itemId = "";
                if (loc2 != null) {
                    for (WaveConfig wc : loc2.getWaves()) {
                        if (wc.isTriggerEnabled() && !wc.getTriggerCustomItemId().isBlank() &&
                            (wc.getTriggerType() == WaveTrigger.PLAYER_HAS_ITEM ||
                             (wc.getExtraTriggers() != null && wc.getExtraTriggers().contains(WaveTrigger.PLAYER_HAS_ITEM)))) {
                            itemId = wc.getTriggerCustomItemId(); break;
                        }
                    }
                }
                if (itemId.isBlank()) return false;
                // Підтримка кількох предметів через кому — ANY з них достатньо
                for (String part : itemId.split(",")) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) continue;
                    try {
                        net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(trimmed);
                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        if (item == null) continue;
                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                        for (ServerPlayer p : players) { if (p.getInventory().contains(stack)) return true; }
                    } catch (Exception ignored) {}
                }
                return false;
            }
            case SHOP_WAVE_START: case SHOP_WAVE_N: case SHOP_LOCATION_START: case SHOP_PLAYER_HAS_ITEM:
                return false; // shop-only triggers, not used in wave conditions
            case WAVES_SURVIVED_5:  { int w = s != null ? s.currentWave : 1; return w > 5; }
            case WAVES_SURVIVED_10: { int w = s != null ? s.currentWave : 1; return w > 10; }
            case WAVES_SURVIVED_20: { int w = s != null ? s.currentWave : 1; return w > 20; }
            case MOBS_KILLED_10:  { return (s != null ? s.mobsKilled : 0) >= 10; }
            case MOBS_KILLED_50:  { return (s != null ? s.mobsKilled : 0) >= 50; }
            case MOBS_KILLED_100: { return (s != null ? s.mobsKilled : 0) >= 100; }
            // Порогові тригери з кастомним значенням
            case MOBS_KILLED_N: {
                // Отримуємо N з першої хвилі що використовує цей тригер
                Location _loc = WaveDefenseMod.locationManager.getLocation(locName);
                int n = 10;
                if (_loc != null) for (WaveConfig _wc : _loc.getWaves()) {
                    if (_wc.isTriggerEnabled() && _wc.getTriggerType() == WaveTrigger.MOBS_KILLED_N) { n = _wc.getTriggerCustomValue(); break; }
                }
                return (s != null ? s.mobsKilled : 0) >= n;
            }
            case WAVES_SURVIVED_N: {
                Location _loc2 = WaveDefenseMod.locationManager.getLocation(locName);
                int n2 = 5;
                if (_loc2 != null) for (WaveConfig _wc2 : _loc2.getWaves()) {
                    if (_wc2.isTriggerEnabled() && _wc2.getTriggerType() == WaveTrigger.WAVES_SURVIVED_N) { n2 = _wc2.getTriggerCustomValue(); break; }
                }
                return (s != null ? s.currentWave : 1) > n2;
            }
            case TIMER_CUSTOM: return true; // TIMER_CUSTOM обробляється в tickTimerCustomForLocation, тут true для AND умов
            case ROUND_START: case ROUND_END: case BUY_PHASE:
            case TEAM_WIPE: case KILL_STREAK_3:
                return false; // PvP only, handled separately
            default:
                com.wavedefense.WaveDefenseMod.LOGGER.warn(
                    "[WaveDefense] TriggerEvaluator: unhandled trigger type '{}' in checkWaveTriggerCondition — returning false", trigger);
                return false;
        }
    }

    /** Записуємо нещодавно спрацьований event-driven тригер для AND-умов */
    private void recordEventTrigger(String locName, WaveTrigger trigger) {
        ctx.markRecentlyFired(locName, trigger);
    }

    /** Перевіряємо чи event-driven тригер спрацьовував нещодавно (для AND-умов) */
    private boolean wasRecentlyFired(String locName, WaveTrigger trigger) {
        return ctx.wasRecentlyFired(locName, trigger);
    }

    public void fireWaveTriggerForPlayer(WaveManager wm, ServerPlayer player,
                                          WaveTrigger trigger) {
        PlayerWaveData data = ctx.playerData.get(player.getUUID());
        // Записуємо для AND-умов (дозволяє polling-тригер + event AND)
        if (data != null && data.getCurrentLocation() != null) {
            recordEventTrigger(data.getCurrentLocation().getName(), trigger);
        }
        if (data == null || data.getCurrentLocation() == null) return;
        Location loc = data.getCurrentLocation();
        if (loc.isPvp()) return;
        String locName = loc.getName();
        LocationSession s = ctx.getSession(locName);
        int curWave = s != null ? s.currentWave : 1;

        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != trigger) continue;
            // Перевірка разово
            if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
            // Перевірка мінімальної хвилі активації
            if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
            // Перевірка AND умов (extra triggers)
            boolean andOk = true;
            java.util.List<WaveTrigger> extras = wave.getExtraTriggers();
            for (WaveTrigger extra : (extras != null ? extras : java.util.Collections.<WaveTrigger>emptyList())) {
                boolean extraOk = (extra == WaveTrigger.PLAYER_HAS_ITEM)
                    ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                    : checkWaveTriggerCondition(extra, locName, null);
                if (!extraOk) { andOk = false; break; }
            }
            if (!andOk) continue;
            String coolKey = LocationSession.triggerKey(wi);
            if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
            fireTriggerWave(wm, loc, wi);
            recordTriggerWaveFire(wave, coolKey, locName);
            if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
        }
    }

    /**
     * Перевіряє чи потрібно запустити локацію по event-driven тригеру для гравця поруч.
     */
    public void fireLocationTrigger(WaveManager wm, ServerPlayer player,
                                    WaveTrigger trigger) {
        if (WaveDefenseMod.getServer() == null) return;
        if (ctx.playerData.containsKey(player.getUUID())) return; // вже в локації

        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!loc.isLocationTriggerEnabled()) continue;
            if (loc.isPvp()) continue;
            if (loc.getLocationTriggerType() != trigger) continue;
            if (loc.getPlayerSpawn() == null) continue;

            String name = loc.getName();
            boolean active = ctx.playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(name));
            if (active) continue;

            int radius = loc.isAutoActivate() ? loc.getAutoActivateRadius()
                : (loc.isLocationBoundaryEnabled() ? loc.getLocationBoundaryRadius() : 30);
            if (player.blockPosition().distSqr(loc.getPlayerSpawn()) > (double) radius * radius) continue;

            java.util.Set<UUID> set = new java.util.HashSet<>();
            set.add(player.getUUID());
            wm.boundaryMgr.activateZoneForPlayers(wm, loc, set);
            break;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  STATIC DEBUG HELPER
    // ════════════════════════════════════════════════════════════════════

    private static void debugLog(String msg) {
        if (com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get())
            WaveDefenseMod.LOGGER.info("[WaveDefense] " + msg);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану TriggerEvaluator. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // Cooldowns are stored in LocationSession, not here
        return tag;
    }

    /** Відновлення стану TriggerEvaluator. */
    public void load(CompoundTag tag) {
        // No state to restore
    }
}
