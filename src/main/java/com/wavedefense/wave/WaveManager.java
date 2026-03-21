package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SyncPlayerDataPacket;
import com.wavedefense.network.packets.SyncTeammatesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaveManager {

    // ══ Refactored sub-managers (v0.2.25) ════════════════════════════
    /** Shared state — all Maps moved here, accessible by sub-managers */
    public final WaveContext waveCtx = new WaveContext();
    /** Boundary check — кордон локації */
    private final BoundaryManager    boundaryMgr = new BoundaryManager(waveCtx);
    /** Battle Royale — кордон що звужується */
    public  final BattleRoyaleManager brManager   = new BattleRoyaleManager();
    /** Mob spawning + equipment */
    public final MobSpawnManager mobSpawnMgr = new MobSpawnManager(waveCtx);
    /** Session lifecycle: join / surrender / victory / end */
    public final SessionManager sessionMgr = new SessionManager(waveCtx);
    // ═════════════════════════════════════════════════════════════════

    private final Map<UUID, PlayerWaveData> playerData = waveCtx.playerData;
    private final Map<String, Set<UUID>> spawnedMobsByLocation = waveCtx.spawnedMobsByLocation;
    private final Map<UUID, PlayerBackup> playerBackups        = waveCtx.playerBackups;
    private final Map<UUID, PlayerBackup> pendingDeathRestores = waveCtx.pendingDeathRestores;
    private final Map<String, Long> locationStartTimers = waveCtx.locationStartTimers;
    /** UUID → тіків до повторного надсилання SyncLocationDataPacket */
    private final Map<UUID, Integer> pendingLocationSync = new java.util.concurrent.ConcurrentHashMap<>();
    // ── Boundary / Leave timer (delegated to BoundaryManager) ───────────
    private final Map<UUID, Integer> leaveCountdownTicks = waveCtx.leaveCountdownTicks;
    // ── Portal state ──────────────────────────────────────────────────
    private final Map<String, net.minecraft.core.BlockPos> portalPositions = waveCtx.portalPositions;
    // locationName → ticks until penalty wave
    private final Map<String, Integer> portalPenaltyTimers = waveCtx.portalPenaltyTimers;
    // locationName → ticks until portal respawn
    private final Map<String, Integer> portalRespawnTimers = waveCtx.portalRespawnTimers;
    // locationName → current wave index for "all in order" portal penalty (mode -1)
    private final Map<String, Integer> portalPenaltyWaveIndex = waveCtx.portalPenaltyWaveIndex;
    // UUIDs of portal penalty mobs being tracked (for completion detection)
    private final Map<String, Set<UUID>> portalPenaltyMobs = waveCtx.portalPenaltyMobs;
    private final Map<String, net.minecraft.core.BlockPos> portalEntryPositions = waveCtx.portalEntryPositions;
    private final Set<String> portalFirstPlayerEntered = waveCtx.portalFirstPlayerEntered;
    private final Map<String, Set<UUID>> portalEnteredPlayers = waveCtx.portalEnteredPlayers;
    // playerUUID → time (ms) when re-entry cooldown expires
    private final Map<UUID, Long> reEntryCooldowns = waveCtx.reEntryCooldowns;
    // ── Location trigger ──────────────────────────────────────────────
    // locationName → last wave-trigger fire tick (for cooldown)
    private final Map<String, Long> waveTriggerLastFired = waveCtx.waveTriggerLastFired;
    // locationName → waves-completed counter for WAVES cooldown
    private final Map<String, Integer> waveTriggerWaveCounters = waveCtx.waveTriggerWaveCounters;
    private final Map<String, Integer> locationWaveTimers = waveCtx.locationWaveTimers;
    private final Map<String, GameStats> locationStats = waveCtx.locationStats;
    // Відстежуємо поточну хвилю для кожної локації незалежно від гравців
    private final Map<String, Integer> locationCurrentWave = waveCtx.locationCurrentWave;

    public void addPlayerToLocation(ServerPlayer player, Location location) {
        UUID playerId = player.getUUID();
        if (playerData.containsKey(playerId)) {
            player.displayClientMessage(Component.literal("§cВи вже берете участь у грі!"), false);
            return;
        }
        // Перевіряємо КД повторного входу
        Long cooldownEnd = reEntryCooldowns.get(playerId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long secsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000 + 1;
            player.displayClientMessage(Component.literal(
                "§c⏳ Зачекайте ще §e" + secsLeft + "§c сек перед повторним входом у локацію §e" + location.getName()), false);
            return;
        }

        // ── Завжди зберігаємо речі та позицію гравця ──────────────────────
        playerBackups.put(playerId, new PlayerBackup(player));

        // ── Примусовий gamemode (якщо увімкнено для локації) ─────────────
        if (location.isEnforceGameMode()) {
            net.minecraft.world.level.GameType requiredMode =
                com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                || player.gameMode.getGameModeForPlayer() != requiredMode) {
                player.setGameMode(requiredMode);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§e⚠ Режим гри змінено на §a" + requiredMode.getName()
                    + " §eдля участі в локації «§6" + location.getName() + "§e»."), true);
            }
        }

        // Очищаємо інвентар і видаємо стартові предмети (keepInventory вимкнено за замовч.)
        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (ItemStack item : location.getStartingItems()) {
                player.getInventory().add(item.copy());
            }
        }

        // Стартові поінти (якщо налаштовано)
        if (location.getStartingPoints() > 0) {
            location.addPoints(playerId, location.getStartingPoints());
        }

        // Телепорт: якщо є кастомна точка входу (autoActivateEntryPos) — використовуємо її,
        // інакше — стандартна точка спавну гравця
        BlockPos spawnPos = location.getAutoActivateEntryPos() != null
                ? location.getAutoActivateEntryPos()
                : location.getPlayerSpawn();
        if (spawnPos != null) {
            // Використовуємо playerSpawnRadius для PvE розкиду гравців
            teleportToSafeSpawn(player, spawnPos, location.getPlayerSpawnRadius());
        }

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);

        locationStats.computeIfAbsent(location.getName(), k -> new GameStats()).getPlayerStats(playerId);

        int lobbyTime = com.wavedefense.config.WaveDefenseConfig.LOBBY_TIMER_SECONDS.get();

        if (!locationStartTimers.containsKey(location.getName()) && !locationWaveTimers.containsKey(location.getName())
                && !locationCurrentWave.containsKey(location.getName())) {
            // ── Перший гравець — починаємо таймер лоббі ──────────────────
            locationStartTimers.put(location.getName(), System.currentTimeMillis() + lobbyTime * 1000L);
            locationCurrentWave.put(location.getName(), 1);
            broadcastToLocation(location.getName(),
                String.format("§a🕐 Гра починається через §e%d §aсек! (очікуємо гравців)", lobbyTime));
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave(lobbyTime);
        } else if (locationStartTimers.containsKey(location.getName())) {
            // ── Новий гравець у лоббі — ПЕРЕЗАПУСКАЄМО таймер ───────────
            long newEnd = System.currentTimeMillis() + lobbyTime * 1000L;
            locationStartTimers.put(location.getName(), newEnd);
            broadcastToLocation(location.getName(),
                String.format("§e👤 §6%s §eприєднався! Таймер скинуто: §a%d сек",
                    player.getName().getString(), lobbyTime));
            data.setCurrentWave(locationCurrentWave.getOrDefault(location.getName(), 1));
            data.setTimerActive(true);
            data.setTimeUntilNextWave(lobbyTime);
        } else {
            // ── Гра вже йде — приєднуємось на льоту ──────────────────────
            int currentWave = locationCurrentWave.getOrDefault(location.getName(), 1);
            data.setCurrentWave(currentWave);
            player.displayClientMessage(
                Component.literal("§aВи приєдналися до гри на хвилі " + currentWave), false);
            Integer timer = locationWaveTimers.get(location.getName());
            if (timer != null) {
                data.setTimerActive(true);
                data.setTimeUntilNextWave(timer / 20);
            }
        }

        playerData.put(playerId, data);
        // Спочатку надсилаємо актуальні дані локацій (щоб клієнт мав свіжу Location),
        // потім playerData (щоб HUD відразу відобразився коректно)
        syncLocationDataToPlayer(player);
        syncPlayerData(player);
        syncTeammates(location.getName());
        // Тригери PLAYER_JOIN + LOCATION_START для лут-спавну
        java.util.List<ServerPlayer> allInLoc = getPlayersInLocation(location.getName());
        if (!allInLoc.isEmpty()) {
            ServerLevel lootWorld = allInLoc.get(0).serverLevel();
            fireLootTrigger(location, lootWorld, com.wavedefense.data.LootSpawn.Trigger.PLAYER_JOIN);
            // LOCATION_START fires when wave 1 actually starts (see spawnWave)
        }
    }

    // ── Авто-активація зон (feature #4) ────────────────────────────────
    private final Map<String, Integer> zoneCountdownTickers = waveCtx.zoneCountdownTickers;
    private final Map<String, Set<UUID>> zonePlayersInRange = waveCtx.zonePlayersInRange;

    public void tick() {
        waveCtx.tickCounter++;
        // PvP раундова логіка
        tickPvp();

        // Авто-активація зон
        tickZoneActivation();

        // Старт-таймер
        Iterator<Map.Entry<String, Long>> startIterator = locationStartTimers.entrySet().iterator();
        while (startIterator.hasNext()) {
            Map.Entry<String, Long> entry = startIterator.next();
            if (System.currentTimeMillis() >= entry.getValue()) {
                startIterator.remove();
                String lobbyLocName = entry.getKey();
                int wave = locationCurrentWave.getOrDefault(lobbyLocName, 1);
                // Затримка першої хвилі: береться з timeBetweenWaves хвилі №1 (якщо >0)
                com.wavedefense.data.Location lobbyLoc = WaveDefenseMod.locationManager.getLocation(lobbyLocName);
                int firstDelay = 0;
                if (lobbyLoc != null && wave == 1 && !lobbyLoc.getWaves().isEmpty()) {
                    firstDelay = lobbyLoc.getWaves().get(0).getTimeBetweenWaves();
                }
                if (firstDelay > 0) {
                    locationWaveTimers.put(lobbyLocName, firstDelay * 20);
                    broadcastToLocation(lobbyLocName, "§e⏱ Перша хвиля через §a" + firstDelay + " §eсек...");
                } else {
                    spawnWaveForLocation(lobbyLocName, wave);
                }
            } else {
                long timeLeft = (entry.getValue() - System.currentTimeMillis()) / 1000;
                boolean doSync = (waveCtx.tickCounter % 20 == 0);
                for (Map.Entry<UUID, PlayerWaveData> pd : playerData.entrySet()) {
                    if (pd.getValue().getCurrentLocation() != null &&
                        pd.getValue().getCurrentLocation().getName().equals(entry.getKey())) {
                        pd.getValue().setTimeUntilNextWave((int) timeLeft);
                        pd.getValue().setTimerActive(true);
                        if (doSync && WaveDefenseMod.getServer() != null) {
                            ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(pd.getKey());
                            if (sp != null) syncPlayerData(sp);
                        }
                    }
                }
            }
        }

        // Таймер між хвилями
        Iterator<Map.Entry<String, Integer>> waveIterator = locationWaveTimers.entrySet().iterator();
        while (waveIterator.hasNext()) {
            Map.Entry<String, Integer> entry = waveIterator.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                waveIterator.remove();
                int wave = locationCurrentWave.getOrDefault(entry.getKey(), 1);
                spawnWaveForLocation(entry.getKey(), wave);
            } else {
                // Sync раз на 20 тіків (1 раз/сек) — не кожен тік щоб не просаджувати FPS
                boolean doSync = (entry.getValue() % 20 == 0);
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(entry.getKey())) {
                        data.setTimeUntilNextWave(entry.getValue() / 20);
                        data.setTimerActive(true);
                        if (doSync && data.getPlayerUUID() != null) {
                            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                            if (player != null) syncPlayerData(player);
                        }
                    }
                }
            }
        }

        checkAllWavesComplete();

        // ── Оновлення InfoPanel TextDisplay (раз на секунду) ─────────
        if (waveCtx.tickCounter % 20 == 0) tickInfoPanels();

        // ── Синхронізація HP тімейтів (раз на 2 секунди) ─────────────
        if (waveCtx.tickCounter % 40 == 0) tickTeammatesHpSync();

        // ── Примусовий gamemode для гравців на локації (раз на секунду)
        if (waveCtx.tickCounter % 20 == 0) tickEnforceGameMode();

        // ── Відкладений повторний SyncLocationData (при логіні/join) ─
        tickPendingLocationSync();

        // ── Перевірка кордону локації (вихід за радіус) ──────────────
        boundaryMgr.tick(this); // delegated to BoundaryManager

        // ── Battle Royale: звуження кордону, частинки, шкода ──────────
        if (waveCtx.tickCounter % 20 == 0) brManager.tick(this);

        // ── Портали ──────────────────────────────────────────────────
        tickPortals();

        // ── Тригери запуску локацій ───────────────────────────────────
        tickLocationTriggers();

        // ── Тригерні хвилі ────────────────────────────────────────────
        tickWaveTriggers();

        // ── Per-location таймери (60/120/300 сек від початку сесії) ─────
        for (String locName : getActiveLocationNames()) {
            int t60  = locationTimer60.getOrDefault(locName, 0) + 1;
            int t120 = locationTimer120.getOrDefault(locName, 0) + 1;
            int t300 = locationTimer300.getOrDefault(locName, 0) + 1;
            locationTimer60.put(locName, t60);
            locationTimer120.put(locName, t120);
            locationTimer300.put(locName, t300);
            if (t60 >= 60 * 20) {
                locationTimer60.put(locName, 0);
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_60);
                fireWaveTriggerForLocation(locName, com.wavedefense.data.WaveTrigger.TIMER_60);
            }
            if (t120 >= 120 * 20) {
                locationTimer120.put(locName, 0);
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_120);
                fireWaveTriggerForLocation(locName, com.wavedefense.data.WaveTrigger.TIMER_120);
            }
            if (t300 >= 300 * 20) {
                locationTimer300.put(locName, 0);
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_300);
                fireWaveTriggerForLocation(locName, com.wavedefense.data.WaveTrigger.TIMER_300);
            }

            // ── TIMER_CUSTOM: окремий інтервал для кожної хвилі-тригера ──
            tickTimerCustomForLocation(locName);
        }

        // ── Victory linger timers ──────────────────────────────────────
        Iterator<Map.Entry<String, Integer>> victoryIt = victoryLingerTimers.entrySet().iterator();
        while (victoryIt.hasNext()) {
            Map.Entry<String, Integer> ve = victoryIt.next();
            int remaining = ve.getValue() - 1;
            if (remaining <= 0) {
                victoryIt.remove();
                endSessionForLocation(ve.getKey(), "§7Час вийшов — всіх виведено з локації.");
            } else {
                ve.setValue(remaining);
                // Sync раз на 20 тіків (1 раз/сек)
                int hudSecs = remaining / 20;
                boolean doVictorySync = (remaining % 20 == 0);
                for (PlayerWaveData d : playerData.values()) {
                    if (d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(ve.getKey())) {
                        d.setVictoryCountdownSec(hudSecs);
                        if (doVictorySync && d.getPlayerUUID() != null) {
                            ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(d.getPlayerUUID());
                            if (sp != null) syncPlayerData(sp);
                        }
                    }
                }
            }
        }
    }

    /** Публічний доступ до поточної хвилі (для перевірки тригерів доступності магазину) */
    public int getCurrentWaveForLocation(String locationName) {
        return locationCurrentWave.getOrDefault(locationName, 0);
    }

    private java.util.Set<String> getActiveLocationNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() != null) names.add(d.getCurrentLocation().getName());
        }
        return names;
    }

    private void spawnWaveForLocation(String locationName, int waveNumber) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null || location.getWaves().isEmpty()) return;

        // Перевіряємо чи є ще хвилі (рахуємо тільки НЕ тригерні хвилі)
        // Знаходимо наступну не-тригерну хвилю починаючи з waveNumber
        int actualWaveIndex = -1;
        int normalWaveCount = 0;
        for (int i = 0; i < location.getWaves().size(); i++) {
            com.wavedefense.data.WaveConfig wc = location.getWaves().get(i);
            if (!wc.isTriggerEnabled()) {
                normalWaveCount++;
                if (normalWaveCount == waveNumber) {
                    actualWaveIndex = i;
                    break;
                }
            }
        }
        if (actualWaveIndex == -1) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
            return;
        }
        // Використовуємо actualWaveIndex для отримання конфігу хвилі
        final int waveConfigIndex = actualWaveIndex;

        List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) {
            locationWaveTimers.remove(locationName);
            locationCurrentWave.remove(locationName);
            return;
        }

        WaveConfig waveConfig = location.getWaves().get(waveConfigIndex);
        ServerLevel world = players.get(0).serverLevel();
        Set<UUID> spawnedMobs = spawnedMobsByLocation.computeIfAbsent(locationName, k -> new HashSet<>());

        broadcastToLocation(locationName, "§c§l⚔ Хвиля " + waveNumber + " розпочалася!");
        debugAdmin("Локація §e" + locationName + " §7— хвиля §e" + waveNumber + " §7розпочалася");
        debugLog("Location '" + locationName + "' wave " + waveNumber + " started");
        halfMobsTriggered.remove(locationName); // скидаємо між хвилями

        // Оновлюємо стан для всіх гравців
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                data.setCurrentWave(waveNumber);
                data.setTimerActive(false);
                data.setTimeUntilNextWave(0);
                if (data.getPlayerUUID() != null) {
                    ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                    if (player != null) syncPlayerData(player);
                }
            }
        }

        // Масштаб мобів: кількість гравців на локації
        int playerCount = Math.max(1, players.size());

        boolean anyMobSpawned = false;
        Random rng = new Random();
        for (WaveMob waveMob : waveConfig.getMobs()) {
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            // Базова кількість × кількість гравців (з урахуванням приросту)
            int baseCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            int mobCount = baseCount * playerCount;
            int spawnChance = waveMob.getSpawnChance(); // 1–100

            for (int i = 0; i < mobCount; i++) {
                // Шанс для кожного моба окремо
                if (rng.nextInt(100) >= spawnChance) continue;

                // Пріоритет: спавн хвилі → точки спавну локації
                BlockPos baseSpawn = waveConfig.hasWaveSpawnPos()
                        ? waveConfig.getWaveSpawnPos()
                        : getRandomSpawnPoint(location);
                if (baseSpawn == null) continue;
                // Розкидаємо мобів у радіусі 3 блоків від точки спавну щоб не стакались
                BlockPos spawnPos = waveConfig.hasWaveSpawnPos()
                        ? baseSpawn.offset(rng.nextInt(7)-3, 0, rng.nextInt(7)-3)
                        : baseSpawn;

                try {
                    Mob mob = (Mob) entityType.create(world);
                    if (mob != null) {
                        mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(spawnPos), MobSpawnType.COMMAND, null, null);
                        mob.goalSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
                        mob.setPersistenceRequired();
                        mob.getPersistentData().putString("location", locationName);
                        mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());

                        // ── Спорядження мобів (feature #5) ─────────────────────────
                        applyMobEquipment(mob, waveMob);

                        world.addFreshEntity(mob);
                        spawnedMobs.add(mob.getUUID());
                        anyMobSpawned = true;
                    }
                } catch (Exception e) {
                    WaveDefenseMod.LOGGER.error("Failed to spawn mob: " + waveMob.getMobType(), e);
                }
            }
        }

        // Накладаємо ефект хвилі на всіх гравців
        if (waveConfig.hasEffect()) {
            net.minecraft.world.effect.MobEffect effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(waveConfig.getWaveEffect());
            if (effect != null) {
                // Тривалість = час між хвилями * 20 тіків + запас 600 тіків (30 сек)
                int duration = waveConfig.getTimeBetweenWaves() * 20 + 600;
                for (ServerPlayer p : players) {
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, duration, waveConfig.getWaveEffectAmplifier(), false, false));
                }
            }
        }

        // Записуємо кількість мобів після спавну (для HALF_MOBS_DEAD і MOBS_REMAINING_LOW)
        waveStartMobCounts.put(locationName, spawnedMobs.size());

        // Синхронізуємо лічильник мобів усім гравцям
        int totalMobs = spawnedMobs.size();
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                data.setMobsRemaining(totalMobs);
                if (data.getPlayerUUID() != null) {
                    ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                    if (p != null) syncPlayerData(p);
                }
            }
        }

        // Якщо жодного моба не з'явилось — одразу завершуємо хвилю
        if (!anyMobSpawned) {
            spawnedMobsByLocation.remove(locationName);
            onWaveComplete(locationName);
        }

        // Спавнимо лут
        spawnLootForLocation(location, world, waveNumber);
    }

    private BlockPos getRandomSpawnPoint(Location location) {
        if (location.getMobSpawns().isEmpty()) return null;
        return location.getMobSpawns().get(new Random().nextInt(location.getMobSpawns().size())).getPos();
    }

    private void checkAllWavesComplete() {
        for (String locationName : new HashSet<>(spawnedMobsByLocation.keySet())) {
            checkWaveComplete(locationName);
        }
    }

    private void checkWaveComplete(String locationName) {
        Set<UUID> mobs = spawnedMobsByLocation.get(locationName);
        if (mobs == null) return;

        List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) {
            spawnedMobsByLocation.remove(locationName);
            locationWaveTimers.remove(locationName);
            return;
        }

        ServerLevel world = players.get(0).serverLevel();
        // Видаляємо мертвих або неіснуючих мобів
        mobs.removeIf(uuid -> {
            var entity = world.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        // Тригер HALF_MOBS_DEAD — один раз коли половина знищена
        int currentCount = mobs.size();
        int originalCount = waveStartMobCounts.getOrDefault(locationName, 0);
        if (originalCount > 0 && currentCount > 0 && currentCount <= originalCount / 2
                && !halfMobsTriggered.contains(locationName)) {
            halfMobsTriggered.add(locationName);
            fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.HALF_MOBS_DEAD);
            // Тригерні хвилі MOBS_REMAINING_LOW (≤20% мобів)
            if (currentCount <= originalCount / 5) {
                fireWaveTriggerForLocation(locationName, com.wavedefense.data.WaveTrigger.MOBS_REMAINING_LOW);
            }
        } else if (originalCount > 0 && currentCount > 0 && currentCount <= originalCount / 5) {
            // MOBS_REMAINING_LOW може спрацьовувати щоразу (залежить від cooldown)
            fireWaveTriggerForLocation(locationName, com.wavedefense.data.WaveTrigger.MOBS_REMAINING_LOW);
        }

        if (mobs.isEmpty()) {
            spawnedMobsByLocation.remove(locationName);
            halfMobsTriggered.remove(locationName);
            onWaveComplete(locationName);
        }
    }

    private void onWaveComplete(String locationName) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return;

        // WAVE_END тригер для луту
        fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.WAVE_END);

        // Тригерні хвилі з тригером WAVE_COMPLETE
        int curW = locationCurrentWave.getOrDefault(locationName, 1);
        for (int wi = 0; wi < location.getWaves().size(); wi++) {
            com.wavedefense.data.WaveConfig wc = location.getWaves().get(wi);
            if (!wc.isTriggerEnabled()) continue;
            if (wc.getTriggerType() != com.wavedefense.data.WaveTrigger.WAVE_COMPLETE) continue;
            if (wc.isOneTimeOnly() && wc.isFiredThisSession()) continue;
            if (wc.getActivateFromWave() > 0 && curW < wc.getActivateFromWave()) continue;
            String coolKey = locationName + "_w" + wi;
            if (!isTriggerWaveCooldownReady(wc, coolKey, locationName)) continue;
            fireTriggerWave(location, wi);
            recordTriggerWaveFire(wc, coolKey, locationName);
            if (wc.isOneTimeOnly()) wc.setFiredThisSession(true);
        }

        GameStats stats = locationStats.get(locationName);
        if (stats != null) stats.incrementWavesCompleted();

        int completedWave = locationCurrentWave.getOrDefault(locationName, 1);
        int nextWave = completedWave + 1;

        // Нагороди за хвилю — знаходимо індекс конфігу completedWave-ї НЕ-тригерної хвилі
        if (completedWave >= 1 && completedWave <= location.getWaves().size()) {
            int normalCount2 = 0; int rewardIdx2 = completedWave - 1;
            for (int ri = 0; ri < location.getWaves().size(); ri++) {
                if (!location.getWaves().get(ri).isTriggerEnabled()) {
                    normalCount2++;
                    if (normalCount2 == completedWave) { rewardIdx2 = ri; break; }
                }
            }
            WaveConfig waveConfig = location.getWaves().get(rewardIdx2);
            int reward = waveConfig.getPointsReward();
            if (reward > 0) {
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                        location.addPoints(data.getPlayerUUID(), reward);
                        // Синхронізуємо поінти до клієнта
                        if (data.getPlayerUUID() != null) {
                            ServerPlayer rp2 = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                            if (rp2 != null) syncPlayerData(rp2);
                        }
                    }
                }
            }

            // Виконуємо команду при завершенні хвилі
            if (waveConfig.hasCompletionCommand()) {
                String cmd = waveConfig.getCompletionCommand();
                // Замінюємо змінні
                cmd = cmd.replace("%location%", locationName);
                cmd = cmd.replace("%wave%", String.valueOf(completedWave));
                List<ServerPlayer> wavePlayers = getPlayersInLocation(locationName);
                String playerNames = wavePlayers.stream()
                        .map(p -> p.getGameProfile().getName())
                        .collect(java.util.stream.Collectors.joining(","));
                cmd = cmd.replace("%players%", playerNames);
                // Виконуємо від імені сервера
                final String finalCmd = cmd;
                try {
                    WaveDefenseMod.getServer().getCommands().performPrefixedCommand(
                            WaveDefenseMod.getServer().createCommandSourceStack().withSuppressedOutput().withMaximumPermission(4),
                            finalCmd
                    );
                } catch (Exception e) {
                    WaveDefenseMod.LOGGER.error("Failed to execute wave completion command: " + finalCmd, e);
                }
            }
        } // end reward block

        // Оновлюємо поточну хвилю для локації
        locationCurrentWave.put(locationName, nextWave);

        // Оновлюємо гравців
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                data.setCurrentWave(nextWave);
                if (data.getPlayerUUID() != null) {
                    ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                    if (player != null) syncPlayerData(player);
                }
            }
        }

        // Збільшуємо лічильник хвиль для trigger-cooldown WAVES
        incrementWaveTriggerCounters(locationName);

        // Перевіряємо: чи завершились усі хвилі — враховуємо тільки не-тригерні
        int totalNormal = (int)location.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();
        if (nextWave > totalNormal) {
            Location locWin = WaveDefenseMod.locationManager.getLocation(locationName);
            if (locWin != null) locWin.incrementSessionsCompleted();
            triggerVictory(locationName);
        } else {
            broadcastToLocation(locationName, "§a§l✓ Хвилю " + completedWave + " завершено!");
        debugAdmin("Локація §e" + locationName + " §7— хвиля §e" + completedWave + " §7завершена");
        debugLog("Location '" + locationName + "' wave " + completedWave + " completed");
            // Знаходимо config наступної не-тригерної хвилі для таймера
            int nextTimeBetween = 30; // default
            int nCount = 0;
            for (int ni = 0; ni < location.getWaves().size(); ni++) {
                if (!location.getWaves().get(ni).isTriggerEnabled()) {
                    nCount++;
                    if (nCount == nextWave) { nextTimeBetween = location.getWaves().get(ni).getTimeBetweenWaves(); break; }
                }
            }
            int waveTime = nextTimeBetween;
            locationMobsKilled.putIfAbsent(location.getName(), 0);
            locationWaveTimers.put(locationName, waveTime * 20);

            for (PlayerWaveData data : playerData.values()) {
                if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                    data.setTimerActive(true);
                    data.setTimeUntilNextWave(waveTime);
                    data.setMobsRemaining(0); // скидаємо лічильник між хвилями
                }
            }
        }
    }

    public void onMobKilled(ServerPlayer player, Mob mob) {
        String locationName = mob.getPersistentData().getString("location");
        if (locationName.isEmpty()) return;

        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null
                || !data.getCurrentLocation().getName().equals(locationName)) return;

        int points = mob.getPersistentData().getInt("points");
        data.getCurrentLocation().addPoints(player.getUUID(), points);

        GameStats stats = locationStats.get(locationName);
        locationMobsKilled.merge(locationName, 1, Integer::sum);
        if (stats != null) {
            stats.incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).addPoints(points);
        }

        Set<UUID> mobs = spawnedMobsByLocation.get(locationName);
        if (mobs != null) mobs.remove(mob.getUUID());

        // Оновлюємо лічильник мобів для ВСІХ гравців на цій локації
        int remaining = mobs != null ? mobs.size() : 0;
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locationName)) {
                d.setMobsRemaining(remaining);
                if (d.getPlayerUUID() != null) {
                    ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(d.getPlayerUUID());
                    if (p != null) syncPlayerData(p);
                }
            }
        }

        // ── Лут-тригер MOB_KILL — спрацьовує при кожному вбивстві моба на локації ──
        fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.MOB_KILL);
    }

    /**
     * Авто-активація зон (feature #4).
     * Щотіку перевіряємо гравців поруч з точкою спавну PvE локацій з autoActivate=true.
     * Якщо гравці в радіусі — починаємо 30-секундний зворотний відлік (відображаємо частинки).
     * Якщо всі вийшли — скасовуємо відлік.
     * Після відліку — автоматично запускаємо локацію для гравців що залишились.
     */
    private void tickZoneActivation() {
        if (WaveDefenseMod.getServer() == null) return;

        for (Location location : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!location.isAutoActivate() || location.isPvp()) continue;

            net.minecraft.core.BlockPos center = location.getEffectiveZoneCenter();
            if (center == null) continue;

            String locName = location.getName();
            int radius = Math.max(5, location.getAutoActivateRadius());

            // Перевіряємо чи локація вже активна
            boolean alreadyActive = playerData.values().stream()
                    .anyMatch(d -> d.getCurrentLocation() != null &&
                                  d.getCurrentLocation().getName().equals(locName));

            if (alreadyActive) {
                // Чи зона відкрита після старту?
                Long openUntil = zoneOpenUntilMs.get(locName);
                if (openUntil != null) {
                    if (System.currentTimeMillis() > openUntil) {
                        // Час відкритості вийшов — деактивуємо зону
                        zoneOpenUntilMs.remove(locName);
                    } else {
                        // Зона ще відкрита — збираємо гравців і підключаємо їх
                        Set<UUID> lateJoiners = collectPlayersInZone(location, center, radius);
                        for (UUID uid : lateJoiners) {
                            ServerPlayer lp = WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
                            if (lp != null) {
                                addPlayerToLocation(lp, location);
                                lp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                    "§e⚠ Ви приєдналися до активної локації §6" + locName), false);
                            }
                        }
                    }
                }
                // Скасовуємо відлік якщо активна (але зона не відкрита)
                zoneCountdownTickers.remove(locName);
                zonePlayersInRange.remove(locName);
                zoneCountdownStartMs.remove(locName);
                // Показуємо частинки зони з урахуванням інтервалу
                if (shouldSpawnParticles(location)) {
                    spawnZoneParticlesForLocation(locName, center, radius);
                }
                continue;
            }

            // Зона неактивна — збираємо гравців в радіусі
            Set<UUID> inRange = collectPlayersInZone(location, center, radius);
            zonePlayersInRange.put(locName, inRange);

            // Частинки зони з урахуванням інтервалу
            if (shouldSpawnParticles(location)) {
                spawnZoneParticlesForLocation(locName, center, radius);
            }

            if (inRange.isEmpty()) {
                // Якщо всі вийшли — скасовуємо таймер
                if (zoneCountdownTickers.containsKey(locName)) {
                    zoneCountdownTickers.remove(locName);
                    zoneCountdownStartMs.remove(locName);
                    broadcastToNearby(center, location, "§7Активацію скасовано — зона порожня.");
                }
                continue;
            }

            int activationDelay = location.getZoneActivationTimeSec(); // 0 = миттєво

            // Є гравці — починаємо або продовжуємо відлік
            if (!zoneCountdownTickers.containsKey(locName)) {
                if (activationDelay <= 0) {
                    // Миттєво активуємо
                    zoneCountdownTickers.remove(locName);
                    zonePlayersInRange.remove(locName);
                    zoneCountdownStartMs.remove(locName);
                    startZoneLocationForPlayers(location, inRange);
                    continue;
                }
                zoneCountdownTickers.put(locName, activationDelay * 20);
                zoneCountdownStartMs.put(locName, System.currentTimeMillis());
                broadcastToNearby(center, location,
                    String.format("§e⚠ Зона §6%s§e активується через §a%d сек§e!", locName, activationDelay));
            }

            // Тікаємо
            int ticks = zoneCountdownTickers.getOrDefault(locName, 0) - 1;
            if (ticks <= 0) {
                // Час вийшов — активуємо для всіх хто зараз у зоні
                zoneCountdownTickers.remove(locName);
                zonePlayersInRange.remove(locName);
                zoneCountdownStartMs.remove(locName);
                startZoneLocationForPlayers(location, inRange);
            } else {
                zoneCountdownTickers.put(locName, ticks);
                if (ticks % 20 == 0) {
                    int secsLeft = ticks / 20;
                    if (secsLeft <= 5 || secsLeft % 5 == 0) {
                        broadcastToNearby(center, location,
                            String.format("§c⏱ Зона активується через §e%d §cсек...", secsLeft));
                    }
                }
            }
        }
    }

    /** Збирає гравців що знаходяться в радіусі зони та ще не в грі */
    private Set<UUID> collectPlayersInZone(Location location, net.minecraft.core.BlockPos center, int radius) {
        Set<UUID> inRange = new java.util.HashSet<>();
        for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (playerData.containsKey(p.getUUID())) continue; // вже в грі
            // КД повторного входу
            Long cdExp = reEntryCooldowns.get(p.getUUID());
            if (cdExp != null && System.currentTimeMillis() < cdExp) continue;
            double dist = p.blockPosition().distSqr(center);
            if (dist <= (double) radius * radius) inRange.add(p.getUUID());
        }
        return inRange;
    }

    /** Активує локацію для гравців із зони, налаштовує zoneOpenUntil */
    private void startZoneLocationForPlayers(Location location, Set<UUID> playerIds) {
        debugAdmin("Зона §e" + location.getName() + " §7— активація для " + playerIds.size() + " гравців");
        activateZoneForPlayers(location, playerIds);
        // Якщо zoneOpenAfterStartSec > 0 — ставимо таймер відкритості зони після старту
        if (location.getZoneOpenAfterStartSec() > 0) {
            zoneOpenUntilMs.put(location.getName(),
                System.currentTimeMillis() + location.getZoneOpenAfterStartSec() * 1000L);
            broadcastToLocation(location.getName(),
                "§e⚠ Зона активації залишається відкритою ще §a"
                + location.getZoneOpenAfterStartSec() + " §eсек.");
        }
    }

    /** Відправляє повідомлення адмінам (op level ≥2) якщо DEBUG_ADMIN_MESSAGES увімкнено */
    private void debugAdmin(String msg) {
        if (!com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.get()) return;
        if (WaveDefenseMod.getServer() == null) return;
        Component component = Component.literal("§8[WD-Debug] §7" + msg);
        for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.displayClientMessage(component, false);
        }
    }

    /** Логування у server log якщо DEBUG_LOGGING_ENABLED увімкнено */
    private void debugLog(String msg) {
        if (com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get())
            WaveDefenseMod.LOGGER.info("[WaveDefense] " + msg);
    }

    private void broadcastToNearby(net.minecraft.core.BlockPos pos, Location location, String msg) {
        for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(pos) <= 400) { // 20 блоків
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
            }
        }
    }

    /**
     * Спавнить коло чорних частинок навколо точки спавну (у площині Y).
     */
    /** Перевіряє чи час спавнити частинки для цієї локації (за налаштованим інтервалом) */
    private boolean shouldSpawnParticles(com.wavedefense.data.Location loc) {
        int interval = loc.getZoneParticleInterval();
        if (interval <= 1) return true;
        return (waveCtx.tickCounter % interval) == 0;
    }

    private void spawnZoneParticles(net.minecraft.core.BlockPos center, int radius) {
        spawnZoneParticlesForLocation(null, center, radius);
    }

    private void spawnZoneParticlesForLocation(String locName, net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.server.level.ServerLevel overworld =
            (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        // Вибираємо тип частинки з налаштувань локації (або за замовчуванням)
        net.minecraft.core.particles.SimpleParticleType particleType = net.minecraft.core.particles.ParticleTypes.SQUID_INK;
        if (locName != null) {
            com.wavedefense.data.Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc != null && loc.getZoneParticleType() != null && !loc.getZoneParticleType().isBlank()) {
                try {
                    net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(loc.getZoneParticleType());
                    net.minecraft.core.particles.ParticleType<?> pt = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(rl);
                    if (pt instanceof net.minecraft.core.particles.SimpleParticleType spt) particleType = spt;
                } catch (Exception ignored) {}
            }
        }

        // Кількість точок у кільці: з налаштувань або авто (min 6, max 12, = radius*2)
        int steps;
        float speed = 0.02f;
        if (locName != null) {
            com.wavedefense.data.Location loc2 = WaveDefenseMod.locationManager.getLocation(locName);
            int customCount = (loc2 != null) ? loc2.getZoneParticleCount() : 0;
            speed = (loc2 != null) ? loc2.getZoneParticleSpeed() : 0.02f;
            steps = (customCount > 0) ? Math.min(64, customCount) : Math.min(12, Math.max(6, radius * 2));
        } else {
            steps = Math.min(12, Math.max(6, radius * 2));
        }
        final float particleSpeed = speed;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = center.getX() + 0.5 + radius * Math.cos(angle);
            double pz = center.getZ() + 0.5 + radius * Math.sin(angle);
            overworld.sendParticles(
                particleType,
                px, center.getY() + 0.1, pz,
                1, 0, 0.1, 0, particleSpeed
            );
        }
    }

    /**
     * Активує локацію для гравців які залишились у зоні.
     * autoActivate-локація автоматично зберігає інвентар.
     */
    private void activateZoneForPlayers(Location location, Set<UUID> playerIds) {
        debugAdmin("Локація §e" + location.getName() + " §7запускається для " + playerIds.size() + " гравців");
        debugLog("Location '" + location.getName() + "' activated for " + playerIds.size() + " players");

        int activated = 0;
        for (UUID uid : playerIds) {
            net.minecraft.server.level.ServerPlayer player =
                WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;
            addPlayerToLocation(player, location);
            // Для autoActivate: стартові предмети вже видаються всередині addPlayerToLocation
            // якщо keepInventory=false. Якщо keepInventory=true — не чіпаємо інвентар.
            activated++;
        }

        if (activated > 0) {
            broadcastToNearby(location.getPlayerSpawn(), location,
                String.format("§a🎮 Локацію §e%s §aактивовано для §e%d §aгравців!", location.getName(), activated));
        }
    }

    /**
     * Applies mob equipment and effects when spawning.
     */
    private void applyMobEquipment(Mob mob, com.wavedefense.data.WaveMob waveMob) {
        if (!com.wavedefense.config.WaveDefenseConfig.MOBS_CAN_HAVE_EQUIPMENT.get()) return;

        float dropChance = (float) com.wavedefense.config.WaveDefenseConfig.MOB_ARMOR_DROP_CHANCE.get().floatValue();

        if (!waveMob.getHelmet().isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, waveMob.getHelmet().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, dropChance);
        }
        if (!waveMob.getChestplate().isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, waveMob.getChestplate().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, dropChance);
        }
        if (!waveMob.getLeggings().isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, waveMob.getLeggings().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.LEGS, dropChance);
        }
        if (!waveMob.getBoots().isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, waveMob.getBoots().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.FEET, dropChance);
        }
        if (!waveMob.getMainHand().isEmpty()) {
            forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                             waveMob.getMainHand().copy(), dropChance);
        }
        if (!waveMob.getOffHand().isEmpty()) {
            forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                             waveMob.getOffHand().copy(), dropChance);
        }

        // Ефекти: format "namespace:path:amplifier:durationTicks"
        for (String effectStr : waveMob.getEffects()) {
            try {
                String[] parts = effectStr.split(":");
                // Формат: "namespace:path:amp:dur"
                if (parts.length == 4) {
                    ResourceLocation effectId = new ResourceLocation(parts[0], parts[1]);
                    int amp = Integer.parseInt(parts[2]);
                    int dur = Integer.parseInt(parts[3]);
                    net.minecraft.world.effect.MobEffect effect =
                        net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                    if (effect != null) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, dur, amp));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Встановлює предмет у слот моба.
     * Для модів (наприклад tacz/GeckoLib) що перевизначають логіку екіпірування,
     * додатково пишемо напряму в NBT-тег сутності щоб обійти перевизначені методи.
     */
    private void forceSetItemSlot(net.minecraft.world.entity.Mob mob,
                                   net.minecraft.world.entity.EquipmentSlot slot,
                                   net.minecraft.world.item.ItemStack item, float dropChance) {
        // 1. Стандартний шлях (vanilla mobs)
        mob.setItemSlot(slot, item);
        mob.setDropChance(slot, dropChance);

        // 2. NBT fallback для modded/GeckoLib сутностей що ігнорують setItemSlot
        try {
            net.minecraft.nbt.CompoundTag entityTag = new net.minecraft.nbt.CompoundTag();
            mob.save(entityTag);

            net.minecraft.nbt.ListTag armorItems  = entityTag.contains("ArmorItems")
                ? entityTag.getList("ArmorItems", 10) : new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag handItems   = entityTag.contains("HandItems")
                ? entityTag.getList("HandItems",  10) : new net.minecraft.nbt.ListTag();

            // Ініціалізуємо списки до потрібного розміру
            while (armorItems.size() < 4) armorItems.add(new net.minecraft.nbt.CompoundTag());
            while (handItems.size()  < 2) handItems.add(new net.minecraft.nbt.CompoundTag());

            net.minecraft.nbt.CompoundTag itemTag = item.save(new net.minecraft.nbt.CompoundTag());
            switch (slot) {
                case MAINHAND -> handItems.set(0, itemTag);
                case OFFHAND  -> handItems.set(1, itemTag);
                case FEET     -> armorItems.set(0, itemTag);
                case LEGS     -> armorItems.set(1, itemTag);
                case CHEST    -> armorItems.set(2, itemTag);
                case HEAD     -> armorItems.set(3, itemTag);
            }
            entityTag.put("ArmorItems", armorItems);
            entityTag.put("HandItems",  handItems);

            // Drop chances
            net.minecraft.nbt.ListTag armorDrops = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag handDrops  = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 4; i++) armorDrops.add(net.minecraft.nbt.FloatTag.valueOf(dropChance));
            for (int i = 0; i < 2; i++) handDrops.add(net.minecraft.nbt.FloatTag.valueOf(dropChance));
            entityTag.put("ArmorDropChances", armorDrops);
            entityTag.put("HandDropChances",  handDrops);

            mob.load(entityTag);
        } catch (Exception ignored) {
            // NBT fallback не вдався — стандартний шлях вже застосовано
        }
    }


    /**
     * Вихід з PvP локації без штрафних очків (кнопка "Вийти з PvP").
     * На відміну від surrenderPlayer — не нараховує смерті/пенальті,
     * просто прибирає гравця з раунду і відновлює його стан.
     */
    public void exitPvpLocation(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.get(playerId);
        if (data == null || data.getCurrentLocation() == null || !data.getCurrentLocation().isPvp()) {
            // Якщо не PvP — fallback до звичайного surrender
            surrenderPlayer(player);
            return;
        }
        // Відновлюємо gamemode якщо спектатор
        if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }
        // Викликаємо звичайний surrender (він вже обробляє backup, команду, pvpState)
        // але повідомлення — нейтральне
        String locName = data.getCurrentLocation().getName();
        surrenderPlayer(player);
        broadcastToLocation(locName, "§7" + player.getName().getString() + " покинув локацію.");
    }

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        // Знімаємо ефекти очікування PvP і режим спектатора при виході
        removeWaitEffects(player);
        pvpPendingRespawn.remove(playerId);
        PlayerWaveData data = playerData.remove(playerId);
        if (data != null) {
            Location currentLoc = data.getCurrentLocation();
            boolean keepLoot = currentLoc != null && currentLoc.isKeepLootOnExit();

            // Якщо гравець у спектаторі (PvP смерть) — відновлюємо survival перед backup.restore
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            // Якщо keepLootOnExit — зберігаємо поточний інвентар, але відновлюємо позицію/ефекти
            if (keepLoot) {
                // Зберігаємо поточні предмети
                java.util.List<net.minecraft.world.item.ItemStack> savedItems = new java.util.ArrayList<>();
                for (int si = 0; si < player.getInventory().getContainerSize(); si++) {
                    savedItems.add(player.getInventory().getItem(si).copy());
                }
                PlayerBackup backup = playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
                // Відновлюємо зібраний лут поверх
                for (int si = 0; si < savedItems.size() && si < player.getInventory().getContainerSize(); si++) {
                    if (!savedItems.get(si).isEmpty()) player.getInventory().setItem(si, savedItems.get(si));
                }
            } else {
                PlayerBackup backup = playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
            }

            if (data.getCurrentLocation() != null) {
                String locName = data.getCurrentLocation().getName();
                Location locRef = data.getCurrentLocation();

                // Очищаємо команду гравця в PvP
                locRef.removePlayerTeam(playerId);

                // Видаляємо з PvP round state
                com.wavedefense.data.PvpRoundState state = pvpStates.get(locName);
                if (state != null) {
                    state.removePlayer(playerId);
                    // Перевіряємо чи завершився раунд після виходу
                    String winner = state.checkRoundWinner();
                    if (winner != null && state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE) {
                        endRound(locRef, state, winner);
                    } else {
                        updatePvpEnemyCounts(locRef, state);
                        broadcastPvpSync(locRef);
                    }
                }

                // Якщо більше немає гравців — очищаємо все
                boolean anyLeft = playerData.values().stream()
                        .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locName));
                if (!anyLeft) {
                    spawnedMobsByLocation.remove(locName);
                    locationWaveTimers.remove(locName);
                    locationStartTimers.remove(locName);
                    locationCurrentWave.remove(locName);
                    pvpStates.remove(locName);
                } else {
                    rebalancePvpTeams(locRef, playerId);
                    for (ServerPlayer p : getPlayersInLocation(locName)) syncPlayerData(p);
                    syncTeammates(locName);
                }
            }
            data.setCurrentLocation(null);
            data.setVictoryCountdownSec(0);  // очищаємо таймер перемоги щоб HUD не застрягав
            syncPlayerData(player);
            clearTeammatesForPlayer(player);
            // Телепортуємо на точку виходу (здача) якщо задана
            if (currentLoc != null && currentLoc.getSurrenderExitPos() != null) {
                net.minecraft.core.BlockPos ep = currentLoc.getSurrenderExitPos();
                player.teleportTo(ep.getX() + 0.5, ep.getY(), ep.getZ() + 0.5);
            }
        }
        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    /**
     * Запускає "екран перемоги" або одразу закінчує сесію залежно від налаштувань локації.
     * Якщо victoryScreenEnabled=true та victoryLingerTimeSec>0 — гравці залишаються,
     * бачать повідомлення, а через вказаний час — endSession.
     */
    private void triggerVictory(String locationName) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc == null) { endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено!"); return; }

        // Нагорода за завершення локації (поінти)
        int pts = loc.getCompletionPointsReward();
        if (pts > 0) {
            for (PlayerWaveData d : playerData.values()) {
                if (d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locationName)) {
                    loc.addPoints(d.getPlayerUUID(), pts);
                    // Одразу синхронізуємо поінти до клієнта
                    if (d.getPlayerUUID() != null) {
                        ServerPlayer rp = WaveDefenseMod.getServer().getPlayerList().getPlayer(d.getPlayerUUID());
                        if (rp != null) syncPlayerData(rp);
                    }
                }
            }
        }

        if (loc.isVictoryScreenEnabled() && loc.getVictoryLingerTimeSec() > 0) {
            // Відображаємо title "ПЕРЕМОГА" всім гравцям на локації
            net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket anim =
                new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(20, 60, 40);
            net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket titlePkt =
                new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    net.minecraft.network.chat.Component.literal("§6§l✦ ПЕРЕМОГА ✦"));
            net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket subPkt =
                new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    net.minecraft.network.chat.Component.literal("§eВсі хвилі пройдено!"));
            for (ServerPlayer p : getPlayersInLocation(locationName)) {
                p.connection.send(anim);
                p.connection.send(titlePkt);
                p.connection.send(subPkt);
            }
            // Запускаємо linger timer — endSession відбудеться по таймеру в tick()
            int lingerTicks = loc.getVictoryLingerTimeSec() * 20;
            victoryLingerTimers.put(locationName, lingerTicks);
            broadcastToLocation(locationName, "§6§l✦ ПЕРЕМОГА! §r§7Локація закриється через §e"
                + loc.getVictoryLingerTimeSec() + " §7сек.");
        } else {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
        }
    }

    private void endSessionForLocation(String locationName, String message) {
        // Скидаємо oneTimeOnly лічильники для всіх тригерних хвиль цієї локації
        Location loc0 = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc0 != null) {
            for (com.wavedefense.data.WaveConfig wc : loc0.getWaves()) {
                if (wc.isOneTimeOnly()) wc.setFiredThisSession(false);
            }
        }
        // Зберігаємо лічильник вбитих мобів у постійну статистику локації
        Location locStats = WaveDefenseMod.locationManager.getLocation(locationName);
        if (locStats != null) {
            int sessionKills = locationMobsKilled.getOrDefault(locationName, 0);
            locStats.addTotalMobsKilled(sessionKills);
            WaveDefenseMod.locationManager.saveToFile(); // зберігаємо статистику вбивств на диск
            debugLog("Location '" + locationName + "': session kills=" + sessionKills
                + ", total=" + locStats.getTotalMobsKilledAllTime());
        }
        locationMobsKilled.remove(locationName);
        broadcastToLocation(locationName, message);

        // Якщо гравці потрапили через портал — запам'ятовуємо позицію для телепорту назад
        net.minecraft.core.BlockPos portalReturnPos = portalEntryPositions.get(locationName);
        // Знімаємо знімок поінтів ДО відновлення гравців
        Location locReward = WaveDefenseMod.locationManager.getLocation(locationName);
        Map<UUID, Integer> pointsSnapshot = new HashMap<>();
        List<UUID> playersToRemove = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null &&
                    entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                UUID pid = entry.getKey();
                playersToRemove.add(pid);
                if (locReward != null) {
                    pointsSnapshot.put(pid, locReward.getPlayerPoints(pid));
                }
            }
        }

        // Відновлюємо гравців (backup restore)
        Location locCd = WaveDefenseMod.locationManager.getLocation(locationName);
        int cdSec = locCd != null ? locCd.getReEntryCooldownSec() : 0;

        // Збираємо онлайн-гравців для очищення тімейт-панелі
        List<ServerPlayer> onlinePlayers = new ArrayList<>();
        for (UUID pid : playersToRemove) {
            ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (sp != null) onlinePlayers.add(sp);
        }
        clearTeammatesForAll(onlinePlayers);

        for (UUID playerId : playersToRemove) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(playerId);
            // Встановлюємо КД незалежно від того чи гравець онлайн
            if (cdSec > 0) reEntryCooldowns.put(playerId, System.currentTimeMillis() + cdSec * 1000L);
            if (player != null) {
                PlayerBackup backup = playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
                PlayerWaveData data = playerData.remove(playerId);
                if (data != null) { data.setCurrentLocation(null); }
                // Точка виходу: окремо для перемоги і здачі, без cross-fallback
                Location locExit = WaveDefenseMod.locationManager.getLocation(locationName);
                boolean isVictory = message.contains("✓") || message.contains("Вітаємо");
                net.minecraft.core.BlockPos exitPos = null;
                if (locExit != null) {
                    // ВИКЛЮЧНО відповідна точка — перемога → victoryExitPos, здача → surrenderExitPos
                    exitPos = isVictory ? locExit.getVictoryExitPos() : locExit.getSurrenderExitPos();
                }
                if (exitPos != null) {
                    player.teleportTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5);
                } else if (portalReturnPos != null) {
                    // Гравці прийшли через портал — повертаємо до порталу
                    player.teleportTo(portalReturnPos.getX() + 0.5,
                                       portalReturnPos.getY(),
                                       portalReturnPos.getZ() + 0.5);
                }
                // Якщо exitPos==null і portalReturnPos==null — backup.restore вже відновив позицію

                // Видаємо нагороди за проходження ПІСЛЯ restore (щоб предмети не губились)
                if (locReward != null && !locReward.getCompletionRewards().isEmpty()) {
                    int playerPts = pointsSnapshot.getOrDefault(playerId, 0);
                    for (com.wavedefense.data.ShopItem reward : locReward.getCompletionRewards()) {
                        // Нагорода видається якщо поінтів гравця >= мінімуму
                        if (playerPts >= reward.getBuyPrice()) {
                            for (ItemStack item : reward.getItems()) {
                                if (!item.isEmpty()) {
                                    player.getInventory().add(item.copy());
                                }
                            }
                        }
                    }
                }

                syncPlayerData(player);
            } else {
                // Гравець офлайн — просто прибираємо дані
                playerBackups.remove(playerId);
                playerData.remove(playerId);
            }
        }

        spawnedMobsByLocation.remove(locationName);
        locationWaveTimers.remove(locationName);
        locationStartTimers.remove(locationName);
        locationCurrentWave.remove(locationName);
        // Очищаємо per-location timer counters
        locationTimer60.remove(locationName);
        locationTimer120.remove(locationName);
        locationTimer300.remove(locationName);
        victoryLingerTimers.remove(locationName);
        zoneLateJoinTimers.remove(locationName);
        { String _lcn = locationName; locationTimerCustom.entrySet().removeIf(e -> e.getKey().startsWith(_lcn + "_tc_")); }
        removeInfoPanelEntities(locationName);
        // Очищаємо recently-fired тригери для цієї локації
        recentlyFiredTriggers.entrySet().removeIf(e -> e.getKey().startsWith(locationName + "_"));
    }


    /**
     * Спавнить лут у точках луту локації.
     * Спавнить лут для вказаного тригера (перевіряє trigger + шанс).
     */
    private void spawnLootForLocation(Location location, ServerLevel world, int waveNumber) {
        // Лут тригер WAVE_START кожну хвилю
        fireLootTrigger(location, world, com.wavedefense.data.LootSpawn.Trigger.WAVE_START);
        // LOCATION_START — тільки при першій хвилі (коли гра реально починається)
        if (waveNumber == 1) {
            fireLootTrigger(location, world, com.wavedefense.data.LootSpawn.Trigger.LOCATION_START);
        }
    }

    public void fireLootTrigger(Location location, ServerLevel world,
                                 com.wavedefense.data.LootSpawn.Trigger trigger) {
        if (location == null || location.getLootSpawns().isEmpty()) return;
        Random rng = new Random();
        for (com.wavedefense.data.LootSpawn lootSpawn : location.getLootSpawns()) {
            if (!lootSpawn.hasTrigger(trigger)) continue;
            if (rng.nextInt(100) >= lootSpawn.getSpawnChance()) continue;
            dropLootAt(lootSpawn, world);
        }
    }

    public void fireLootTriggerByName(String locationName, com.wavedefense.data.LootSpawn.Trigger trigger) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return;
        java.util.List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) return;
        ServerLevel world = players.get(0).serverLevel();
        fireLootTrigger(location, world, trigger);
    }

    private void dropLootAt(com.wavedefense.data.LootSpawn lootSpawn, ServerLevel world) {
        net.minecraft.core.BlockPos pos = lootSpawn.getPos();
        for (net.minecraft.world.item.ItemStack item : lootSpawn.getItems()) {
            if (item.isEmpty()) continue;
            for (int n = 0; n < lootSpawn.getCount(); n++) {
                net.minecraft.world.entity.item.ItemEntity itemEntity =
                    new net.minecraft.world.entity.item.ItemEntity(
                        world,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        item.copy()
                    );
                itemEntity.setPickUpDelay(20);
                world.addFreshEntity(itemEntity);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  INFO PANEL — TextDisplay entities
    // ════════════════════════════════════════════════════════════════════

    /**
     * Оновлює (або створює) TextDisplay панелі для активних локацій.
     * Викликається раз на секунду (кожні 20 тіків).
     */
    public void tickInfoPanels() {
        if (WaveDefenseMod.getServer() == null) return;
        // InfoPanel відображається для ВСІХ локацій що мають увімкнену панель —
        // незалежно від того чи локація зараз активна.
        // Видаляємо панелі тільки для локацій яких більше немає.
        java.util.Set<String> allLocNames = new java.util.HashSet<>(
            WaveDefenseMod.locationManager.getAllLocations().stream()
                .map(l -> l.getName()).collect(java.util.stream.Collectors.toSet()));

        // Видаляємо панелі для локацій що більше не існують
        java.util.Set<String> keysToRemove = new java.util.HashSet<>();
        for (String key : infoPanelEntityIds.keySet()) {
            String locPart = key.contains("_mob_") ? key.substring(0, key.lastIndexOf("_mob_"))
                           : key.endsWith("_spawn") ? key.substring(0, key.length() - 6) : key;
            if (!allLocNames.contains(locPart)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            removeTextDisplay(getOverworld(), key);
            infoPanelEntityIds.remove(key);
        }

        java.util.Set<String> activeNames = new java.util.HashSet<>(getActiveLocationNames());

        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            String locName = loc.getName();
            com.wavedefense.data.InfoPanelSettings ips = loc.getInfoPanel();
            net.minecraft.server.level.ServerLevel world = getOverworld();
            if (world == null) continue;

            // ── Панель на точці спавну гравців ─────────────────────────────
            if (ips.isSpawnPanelEnabled() && loc.getPlayerSpawn() != null) {
                boolean isActive = activeNames.contains(locName);
                String text = isActive
                    ? buildSpawnPanelText(locName, loc, ips)
                    : buildIdlePanelText(locName, loc, ips);
                updateOrCreateTextDisplay(world, locName + "_spawn",
                    loc.getPlayerSpawn(), ips.getSpawnPanelOffsetY(),
                    text, ips);
            } else {
                removeTextDisplay(getOverworld(), locName + "_spawn");
                infoPanelEntityIds.remove(locName + "_spawn");
            }

            // ── Панелі на точках спавну мобів ──────────────────────────────
            java.util.List<com.wavedefense.data.MobSpawnPoint> mobSpawns = loc.getMobSpawns();
            for (int mi = 0; mi < mobSpawns.size(); mi++) {
                String key = locName + "_mob_" + mi;
                if (ips.isMobSpawnPanelEnabled()) {
                    String text = buildMobSpawnPanelText(locName, loc, ips, mi);
                    updateOrCreateTextDisplay(world, key, mobSpawns.get(mi).getPos(), ips.getMobSpawnOffsetY(), text, ips);
                } else {
                    removeTextDisplay(world, key);
                    infoPanelEntityIds.remove(key);
                }
            }
            // Видаляємо панелі для видалених точок мобів
            int idx = mobSpawns.size();
            while (infoPanelEntityIds.containsKey(locName + "_mob_" + idx)) {
                removeTextDisplay(world, locName + "_mob_" + idx);
                infoPanelEntityIds.remove(locName + "_mob_" + idx);
                idx++;
            }
        }
    }

    private String buildSpawnPanelText(String locName, Location loc, com.wavedefense.data.InfoPanelSettings ips) {
        StringBuilder sb = new StringBuilder();
        int curWave = locationCurrentWave.getOrDefault(locName, 1);
        int normalWaves = (int) loc.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();
        int secretWaves = (int) loc.getWaves().stream().filter(w -> w.isTriggerEnabled() && w.isOneTimeOnly()).count();
        int shopSecrets = (int) loc.getShopItems().stream().filter(s -> s.hasAvailabilityTrigger() &&
            s.getAvailabilityTrigger() != com.wavedefense.data.WaveTrigger.SHOP_LOCATION_START).count();
        int playerCount = getPlayersInLocation(locName).size();
        int mobsLeft = spawnedMobsByLocation.getOrDefault(locName, new java.util.HashSet<>()).size();

        if (ips.isShowWaveNumber())
            sb.append("§e⚔ Хвиля §6").append(curWave).append("§e/§6").append(normalWaves).append("\n");
        if (ips.isShowMobsRemaining() && mobsLeft > 0)
            sb.append("§c☠ Мобів: §f").append(mobsLeft).append("\n");
        if (ips.isShowWaveTimer()) {
            int wt = locationWaveTimers.getOrDefault(locName, 0);
            if (wt > 0) {
                int secsLeft = wt / 20;
                if (secsLeft > 0) sb.append("§b⏱ До хвилі: §f").append(secsLeft).append("с\n");
            }
        }
        // Таймер до початку першої хвилі (після лоббі)
        if (ips.isShowFirstWaveTimer()) {
            int lwt = locationWaveTimers.getOrDefault(locName, 0);
            int curW = locationCurrentWave.getOrDefault(locName, 1);
            if (lwt > 0 && curW == 1 && !locationWaveTimers.containsKey(locName) && locationStartTimers.containsKey(locName)) {
                // Лобі ще йде — показуємо таймер запуску
                long startMs = locationStartTimers.getOrDefault(locName, 0L);
                int secsToStart = (int) Math.max(0, (startMs - System.currentTimeMillis()) / 1000);
                if (secsToStart > 0) sb.append("§a🕐 Старт через: §f").append(secsToStart).append("с\n");
            } else if (lwt > 0 && curW == 1) {
                // Перша хвиля з затримкою
                int secsLeft = lwt / 20;
                if (secsLeft > 0) sb.append("§a🕐 Перша хвиля: §f").append(secsLeft).append("с\n");
            }
        }
        // Таймер лоббі (окремий показник)
        if (ips.isShowLobbyTimer() && locationStartTimers.containsKey(locName)) {
            long startMs = locationStartTimers.get(locName);
            int secsToStart = (int) Math.max(0, (startMs - System.currentTimeMillis()) / 1000);
            if (secsToStart > 0) sb.append("§e⏳ Лоббі: §f").append(secsToStart).append("с\n");
        }
        if (ips.isShowPlayerCount())
            sb.append("§a👥 Гравців: §f").append(playerCount).append("\n");
        if (ips.isShowSecretCount() && secretWaves > 0)
            sb.append("§d🔮 Секретів: §f").append(secretWaves).append("\n");
        if (ips.isShowShopSecrets() && shopSecrets > 0)
            sb.append("§6🛒 Умовних товарів: §f").append(shopSecrets).append("\n");
        if (ips.isShowPoints()) {
            int totalPts = getPlayersInLocation(locName).stream()
                .mapToInt(p -> loc.getPlayerPoints(p.getUUID())).sum();
            if (totalPts > 0)
                sb.append("§e💎 Поінти: §f").append(totalPts).append("\n");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "§7" + loc.getName() : result;
    }

    /** Текст панелі коли локація ще не запущена (очікує старту) */
    private String buildIdlePanelText(String locName, Location loc, com.wavedefense.data.InfoPanelSettings ips) {
        StringBuilder sb = new StringBuilder();
        int normalWaves = (int) loc.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();
        sb.append("§e§l").append(loc.getName()).append("\n");
        if (normalWaves > 0)
            sb.append("§7Хвиль: §f").append(normalWaves).append("\n");
        sb.append("§a▶ Готово до старту");
        return sb.toString();
    }

    private String buildMobSpawnPanelText(String locName, Location loc, com.wavedefense.data.InfoPanelSettings ips, int spawnIdx) {
        StringBuilder sb = new StringBuilder();
        int curWave = locationCurrentWave.getOrDefault(locName, 1);
        int normalWaves = (int) loc.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();

        if (ips.isMobShowWaveNumber())
            sb.append("§e").append(curWave).append("/").append(normalWaves).append("\n");
        if (ips.isMobShowWaveTimer()) {
            int wt = locationWaveTimers.getOrDefault(locName, 0);
            if (wt > 0) {
                int secsLeft = wt / 20;
                if (secsLeft > 0) sb.append("§b⏱ ").append(secsLeft).append("с");
            }
        }
        if (ips.isMobShowMobCount()) {
            int cnt = 0;
            if (curWave > 0 && curWave <= loc.getWaves().size()) {
                com.wavedefense.data.WaveConfig wc = null;
                int normalCount = 0;
                for (com.wavedefense.data.WaveConfig w : loc.getWaves()) {
                    if (!w.isTriggerEnabled()) { normalCount++; if (normalCount == curWave) { wc = w; break; } }
                }
                if (wc != null) cnt = wc.getMobs().stream().mapToInt(m -> m.getCount()).sum();
            }
            if (cnt > 0) sb.append("\n§c").append(cnt).append(" мобів");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "§7►" : result;
    }

    private net.minecraft.server.level.ServerLevel getOverworld() {
        if (WaveDefenseMod.getServer() == null) return null;
        return (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer()
            .getLevel(net.minecraft.world.level.Level.OVERWORLD);
    }

    /**
     * Налаштовує TextDisplay через NBT — єдиний надійний спосіб у Forge 1.20.1,
     * оскільки всі методи TextDisplay/Display є private (setText, setTransformation,
     * setBillboardConstraints недоступні напряму).
     *
     * NBT-поля TextDisplay (wiki.vg/Entity_metadata + MC source):
     *   text          — JSON-компонент (рядок)
     *   billboard     — String: "fixed"|"vertical"|"horizontal"|"center" (StringRepresentable via CODEC)
     *   transformation — 16 float (4x4 матриця)
     *   shadow_strength — float (0=без тіні, 1=нормальна)
     */
    /**
     * Оновлює TextDisplay entity: текст, billboard=CENTER, масштаб, тінь.
     * Комбінований підхід: рефлексія для text/billboard + NBT для transformation/shadow.
     */
    private void applyTextDisplayNbt(Display.TextDisplay td, String text,
            com.wavedefense.data.InfoPanelSettings ips) {
        // ─── 1. Текст через рефлексію (Optional<Component>) ─────────────────
        setTextDisplayText(td, text);

        // ─── 2. Billboard = CENTER через рефлексію ───────────────────────────
        setBillboardViaReflection(td);

        // ─── 3. Shadow + Transformation + текст через NBT (резервний шлях) ──
        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        String json = net.minecraft.network.chat.Component.Serializer.toJson(
                net.minecraft.network.chat.Component.literal(text));
        nbt.putString("text", json);
        nbt.putString("billboard", "center");
        nbt.putFloat("shadow_strength", ips.isHasShadow() ? 1.0f : 0.0f);

        float s = ips.getTextScale();
        net.minecraft.nbt.CompoundTag transTag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag lq = new net.minecraft.nbt.ListTag();
        lq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        lq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        lq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        lq.add(net.minecraft.nbt.FloatTag.valueOf(1f));
        net.minecraft.nbt.ListTag scaleList = new net.minecraft.nbt.ListTag();
        scaleList.add(net.minecraft.nbt.FloatTag.valueOf(s));
        scaleList.add(net.minecraft.nbt.FloatTag.valueOf(s));
        scaleList.add(net.minecraft.nbt.FloatTag.valueOf(s));
        net.minecraft.nbt.ListTag rq = new net.minecraft.nbt.ListTag();
        rq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        rq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        rq.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        rq.add(net.minecraft.nbt.FloatTag.valueOf(1f));
        net.minecraft.nbt.ListTag trl = new net.minecraft.nbt.ListTag();
        trl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        trl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        trl.add(net.minecraft.nbt.FloatTag.valueOf(0f));
        transTag.put("left_rotation", lq);
        transTag.put("scale", scaleList);
        transTag.put("right_rotation", rq);
        transTag.put("translation", trl);
        nbt.put("transformation", transTag);
        try { td.load(nbt); } catch (Exception ignored) {}

        // ─── 4. Billboard знову після load (load може скинути) ───────────────
        setBillboardViaReflection(td);

        // ─── 5. Синхронізація до клієнтів ────────────────────────────────────
        broadcastEntityData(td);
    }

    /**
     * Встановлює текст TextDisplay через SynchedEntityData.
     * TextDisplay зберігає текст як EntityDataAccessor<Optional<Component>>.
     */
    @SuppressWarnings("unchecked")
    private void setTextDisplayText(Display.TextDisplay td, String text) {
        net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.literal(text);
        try {
            Class<?> cls = Display.TextDisplay.class;
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try {
                        Object val = f.get(null);
                        if (!(val instanceof net.minecraft.network.syncher.EntityDataAccessor<?> accessor)) continue;
                        Object cur = td.getEntityData().get(
                            (net.minecraft.network.syncher.EntityDataAccessor<Object>) accessor);
                        if (cur instanceof java.util.Optional) {
                            td.getEntityData().set(
                                (net.minecraft.network.syncher.EntityDataAccessor<java.util.Optional<net.minecraft.network.chat.Component>>) accessor,
                                java.util.Optional.of(comp));
                            return;
                        }
                    } catch (Exception ignored2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            debugLog("setTextDisplayText failed: " + e.getMessage());
        }
    }

    private void broadcastEntityData(net.minecraft.world.entity.Entity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> dirty =
            entity.getEntityData().getNonDefaultValues();
        if (dirty != null && !dirty.isEmpty()) {
            sl.getChunkSource().broadcastAndSend(entity,
                new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                    entity.getId(), dirty));
        }
    }

    private void updateOrCreateTextDisplay(net.minecraft.server.level.ServerLevel world,
            String key, net.minecraft.core.BlockPos blockPos, float offsetY,
            String text, com.wavedefense.data.InfoPanelSettings ips) {
        if (world == null || blockPos == null) return;
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + offsetY;
        double z = blockPos.getZ() + 0.5;

        // Перевіряємо чи вже є ентіті
        java.util.UUID existingId = infoPanelEntityIds.get(key);
        if (existingId != null) {
            net.minecraft.world.entity.Entity ent = world.getEntity(existingId);
            if (ent instanceof Display.TextDisplay td) {
                // Оновлюємо через NBT
                applyTextDisplayNbt(td, text, ips);
                return;
            } else {
                infoPanelEntityIds.remove(key);
            }
        }

        // Створюємо нову TextDisplay
        try {
            Display.TextDisplay td = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
            td.moveTo(x, y, z, 0f, 0f);
            td.setNoGravity(true);
            td.setInvulnerable(true);
            td.setSilent(true);

            world.addFreshEntity(td);
            infoPanelEntityIds.put(key, td.getUUID());
            // Застосовуємо параметри відображення через NBT — ПІСЛЯ додавання в world
            // (SynchedEntityData ініціалізується при addFreshEntity)
            applyTextDisplayNbt(td, text, ips);
        } catch (Exception e) {
            debugLog("Failed to create TextDisplay for " + key + ": " + e.getMessage());
        }
    }

    /**
     * Встановлює billboard=CENTER через рефлексію.
     * Шукає EntityDataAccessor<BillboardConstraints> у Display та його суперкласах.
     * Після встановлення примусово синхронізує до клієнтів через markDirty.
     */
    @SuppressWarnings("unchecked")
    private void setBillboardViaReflection(Display.TextDisplay td) {
        try {
            // Шукаємо статичне поле EntityDataAccessor<BillboardConstraints> у Display і суперкласах
            Class<?> cls = Display.class;
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    // Потрібні тільки static поля (EntityDataAccessor зберігається як static)
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try {
                        Object val = f.get(null);
                        if (!(val instanceof net.minecraft.network.syncher.EntityDataAccessor<?> accessor)) continue;
                        // Перевіряємо чи тип значення — BillboardConstraints
                        Object cur = td.getEntityData().get(
                            (net.minecraft.network.syncher.EntityDataAccessor<Object>) accessor);
                        if (cur instanceof Display.BillboardConstraints) {
                            // Знайшли! Встановлюємо CENTER
                            td.getEntityData().set(
                                (net.minecraft.network.syncher.EntityDataAccessor<Display.BillboardConstraints>) accessor,
                                Display.BillboardConstraints.CENTER);
                            return; // Done
                        }
                    } catch (Exception ignored2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            debugLog("setBillboardViaReflection failed: " + e.getMessage());
        }
    }

    private void removeTextDisplay(net.minecraft.server.level.ServerLevel world, String key) {
        if (world == null) return;
        java.util.UUID id = infoPanelEntityIds.remove(key);
        if (id != null) {
            net.minecraft.world.entity.Entity ent = world.getEntity(id);
            if (ent != null) ent.discard();
        }
    }

    /** Видаляє TextDisplay entity для інфо-панелей локації. Public для SessionManager. */
    public void removeInfoPanelEntities(String locationName) {
        net.minecraft.server.level.ServerLevel world = getOverworld();
        // Remove spawn panel
        removeTextDisplay(world, locationName + "_spawn");
        // Remove mob panels
        int idx = 0;
        while (infoPanelEntityIds.containsKey(locationName + "_mob_" + idx)) {
            removeTextDisplay(world, locationName + "_mob_" + idx);
            idx++;
        }
    }


    public void broadcastToLocation(String locationName, String message) {
        for (ServerPlayer player : getPlayersInLocation(locationName)) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    public List<ServerPlayer> getPlayersInLocation(String locationName) {
        List<ServerPlayer> players = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null &&
                    entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) players.add(player);
            }
        }
        return players;
    }

    public void syncPlayerData(ServerPlayer player) {
        if (player == null) return;
        PlayerWaveData data = getPlayerData(player.getUUID());
        if (data != null) {
            // Синхронізуємо поточні поінти гравця перед відправкою
            if (data.getCurrentLocation() != null) {
                data.setPlayerPoints(data.getCurrentLocation().getPlayerPoints(player.getUUID()));
            }
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), new SyncPlayerDataPacket(data));
        } else {
            // Гравець вийшов з локації — надсилаємо порожні дані щоб клієнт скинув HUD
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), new SyncPlayerDataPacket(new PlayerWaveData()));
        }
    }

    /** Надсилає повний список локацій конкретному гравцю (при логіні або за запитом). */
    public void syncLocationDataToPlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        com.wavedefense.network.packets.SyncLocationDataPacket pkt =
            new com.wavedefense.network.packets.SyncLocationDataPacket(
                WaveDefenseMod.locationManager.save());
        WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        // Повторна відправка через 60 тіків (3 сек) через tick-scheduler (надійніше за Thread.sleep)
        pendingLocationSync.put(player.getUUID(), 60);
    }

    /** Надсилає повний список локацій всім гравцям на сервері. */
    public void broadcastLocationData() {
        if (WaveDefenseMod.getServer() == null) return;
        com.wavedefense.network.packets.SyncLocationDataPacket pkt =
            new com.wavedefense.network.packets.SyncLocationDataPacket(
                WaveDefenseMod.locationManager.save());
        for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    /**
     * Надсилає список тімейтів всім гравцям на локації.
     * Викликається при join, leave, death, surrender.
     */
    /**
     * Синхронізує список тімейтів всім гравцям на локації.
     * PvE: кожен бачить усіх на локації.
     * PvP: кожен бачить лише гравців своєї команди.
     */
    public void syncTeammates(String locationName) {
        if (WaveDefenseMod.getServer() == null) return;
        List<ServerPlayer> inLoc = getPlayersInLocation(locationName);
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        boolean isPvp = loc != null && loc.isPvp();

        // Будуємо повний список записів для PvE (або базу для PvP)
        List<SyncTeammatesPacket.PlayerEntry> allEntries = new ArrayList<>();
        for (ServerPlayer p : inLoc) {
            int hp    = (int) p.getHealth();
            int maxHp = (int) p.getMaxHealth();
            boolean alive = !p.isSpectator() && p.isAlive();
            String team = loc != null ? loc.getPlayerTeam(p.getUUID()) : null;
            allEntries.add(new SyncTeammatesPacket.PlayerEntry(
                p.getName().getString(), p.getUUID(), hp, maxHp, alive, team));
        }

        for (ServerPlayer receiver : inLoc) {
            List<SyncTeammatesPacket.PlayerEntry> toSend;
            if (isPvp) {
                // PvP: тільки своя команда
                String myTeam = loc.getPlayerTeam(receiver.getUUID());
                if (myTeam == null) {
                    toSend = allEntries; // команда ще не призначена → показуємо всіх
                } else {
                    final String ft = myTeam;
                    toSend = allEntries.stream()
                        .filter(e -> ft.equals(e.team()))
                        .collect(java.util.stream.Collectors.toList());
                }
            } else {
                toSend = allEntries;
            }
            SyncTeammatesPacket pkt = SyncTeammatesPacket.build(locationName, toSend);
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> receiver), pkt);
        }
    }

    /**
     * Надсилає порожній список тімейтів конкретному гравцю (при виході з локації)
     * або всім хто залишився після закінчення сесії.
     */
    public void clearTeammatesForPlayer(ServerPlayer player) {
        SyncTeammatesPacket pkt = SyncTeammatesPacket.build("", java.util.Collections.emptyList());
        WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    public void clearTeammatesForAll(List<ServerPlayer> players) {
        SyncTeammatesPacket pkt = SyncTeammatesPacket.build("", java.util.Collections.emptyList());
        for (ServerPlayer p : players) {
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    /**
     * Оновлює HP тімейтів кожні 40 тіків (~2 сек) для живого HUD.
     * Групуємо гравців по локаціях — надсилаємо лише одне повідомлення на локацію.
     */
    private void tickTeammatesHpSync() {
        Set<String> visited = new java.util.HashSet<>();
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() == null) continue;
            String locName = data.getCurrentLocation().getName();
            if (!visited.add(locName)) continue; // вже синхронізували цю локацію
            syncTeammates(locName);
        }
    }

    /**
     * Раз на секунду перевіряємо gamemode всіх гравців на локації.
     * Creative → автоматично перемикаємо на режим з конфігурації (default: Survival).
     */
    private void tickEnforceGameMode() {
        if (WaveDefenseMod.getServer() == null) return;
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            com.wavedefense.data.Location loc = entry.getValue().getCurrentLocation();
            if (loc == null) continue;
            if (!loc.isEnforceGameMode()) continue;  // опція вимкнена — не примусово
            ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(entry.getKey());
            if (sp == null) continue;
            net.minecraft.world.level.GameType current = sp.gameMode.getGameModeForPlayer();
            net.minecraft.world.level.GameType required =
                com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
            // Дозволяємо Spectator (PvP очікування/смерть) і потрібний режим
            if (current == net.minecraft.world.level.GameType.CREATIVE) {
                sp.setGameMode(required);
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§e⚠ Creative заблоковано на локації. Режим повернуто до §a"
                    + required.getName() + "§e."), true);
            }
        }
    }
    private void tickPendingLocationSync() {
        if (pendingLocationSync.isEmpty() || WaveDefenseMod.getServer() == null) return;
        java.util.Iterator<Map.Entry<UUID, Integer>> it = pendingLocationSync.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                it.remove();
                ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    com.wavedefense.network.packets.SyncLocationDataPacket pkt =
                        new com.wavedefense.network.packets.SyncLocationDataPacket(
                            WaveDefenseMod.locationManager.save());
                    WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), pkt);
                    syncPlayerData(player);
                }
            } else {
                entry.setValue(ticks);
            }
        }
    }

    public PlayerWaveData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    /**
     * Повертає і видаляє backup для PvE-смерті.
     * Викликається з PlayerRespawnHandler після того як гравець відродився.
     */
    public com.wavedefense.data.PlayerBackup consumePendingDeathRestore(UUID playerId) {
        return pendingDeathRestores.remove(playerId);
    }


    // ════════════════════════════════════════════════════════════════════
    //  PvP — Round-Based System
    // ════════════════════════════════════════════════════════════════════

    /** Кількість мобів на початку хвилі (для HALF_MOBS_DEAD) */
    // Таймери для time-based лут-тригерів (в тіках)
    // Per-location timer тіки (рахуємо від старту сесії)
    // key = locationName, value = тіки від початку сесії
    private final Map<String, Integer> locationTimer60  = waveCtx.locationTimer60;
    private final Map<String, Integer> locationTimer120 = waveCtx.locationTimer120;
    private final Map<String, Integer> locationTimer300 = waveCtx.locationTimer300;
    // Per-session victory linger timer: locationName → remaining ticks
    // tickCounter delegated to waveCtx
    private final Map<String, Integer> victoryLingerTimers = waveCtx.victoryLingerTimers;
    // Per-location custom timer для TIMER_CUSTOM тригерів (тіки)
    private final Map<String, Integer> locationTimerCustom = waveCtx.locationTimerCustom;
    // TextDisplay entity UUID map: locName -> spawn panel UUID, locName+"_mob_"+idx -> mob panel UUID
    private final Map<String, java.util.UUID> infoPanelEntityIds = waveCtx.infoPanelEntityIds;
    // Таймер "пізнього приєднання" до зони авто-активації після запуску локації
    private final Map<String, Integer> zoneLateJoinTimers = waveCtx.zoneLateJoinTimers;
    // Zone activation: locationName → time (ms) when zone countdown started (тобто перший гравець зайшов)
    private final Map<String, Long> zoneCountdownStartMs = waveCtx.zoneCountdownStartMs;
    // Portal: locationName → time (ms) until which portal stays open after location start
    private final Map<String, Long> portalOpenUntilMs = waveCtx.portalOpenUntilMs;
    // Zone: after location started, zone stays open until: locationName → time (ms)
    private final Map<String, Long> zoneOpenUntilMs = waveCtx.zoneOpenUntilMs;

    private final Map<String, Integer> waveStartMobCounts = waveCtx.waveStartMobCounts;
    private final Map<String, Integer> locationMobsKilled = waveCtx.locationMobsKilled;
    /** Нещодавно спрацьовані event-driven тригери: locName+"_"+trigger.name() → timestamp (ms).
     * Використовується для AND-умов де primary є polling-тригер, а extra — event-driven. */
    private final Map<String, Long> recentlyFiredTriggers = waveCtx.recentlyFiredTriggers;
    /** Час дії "нещодавно спрацьованого" тригера (мс) — 10 секунд */
    private static final long RECENTLY_FIRED_WINDOW_MS = 10_000L;
    private final Set<String> halfMobsTriggered = waveCtx.halfMobsTriggered;

    /** Стан раунду кожної PvP локації (за назвою) */
    private final Map<String, com.wavedefense.data.PvpRoundState> pvpStates = waveCtx.pvpStates;

    /**
     * Додає гравця до PvP локації, призначає команду і ставить у WAITING фазу.
     */
    public void addPlayerToPvpLocation(ServerPlayer player, Location location, int spawnIndex) {
        UUID playerId = player.getUUID();
        if (playerData.containsKey(playerId)) {
            player.displayClientMessage(Component.literal("§cВи вже берете участь у грі!"), false);
            return;
        }

        playerBackups.put(playerId, new PlayerBackup(player));

        // ── Примусовий gamemode (якщо увімкнено для локації) ─────────────
        if (location.isEnforceGameMode()) {
            net.minecraft.world.level.GameType requiredMode =
                com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                || player.gameMode.getGameModeForPlayer() != requiredMode) {
                player.setGameMode(requiredMode);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§e⚠ Режим гри змінено на §a" + requiredMode.getName()
                    + " §eдля участі в локації «§6" + location.getName() + "§e»."), true);
            }
        }

        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (ItemStack item : location.getStartingItems()) player.getInventory().add(item.copy());
        }

        // Стартові поінти для покупок у магазині
        if (location.getStartingPoints() > 0) {
            location.addPoints(playerId, location.getStartingPoints());
        }

        com.wavedefense.data.PvpSpawnPoint spawnPoint = location.getPvpSpawnPoints().get(spawnIndex);
        location.setPlayerTeam(playerId, spawnPoint.getTeamName());

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);
        data.setInPvp(true);
        data.setCurrentWave(0);
        data.setTimerActive(false);
        playerData.put(playerId, data);

        // Режим очікування: ефекти slowness+blindness або spectator
        if (location.isPvpWaitEffect()) {
            applyWaitEffects(player);
        } else {
            setSpectator(player, true);
        }
        teleportToSpawnPoint(player, spawnPoint);

        // Ініціалізуємо або оновлюємо PvpRoundState
        com.wavedefense.data.PvpRoundState state = pvpStates.computeIfAbsent(
            location.getName(),
            k -> {
                com.wavedefense.data.PvpRoundState s =
                    new com.wavedefense.data.PvpRoundState(location.getPvpTotalRounds(), location.getPvpBuyTime());
                s.setDmKillsToWin(location.getDmKillsToWin());
                return s;
            }
        );
        state.registerPlayer(playerId, player.getName().getString(), spawnPoint.getTeamName());

        // BR: ініціалізуємо кордон
        if (location.isBattleRoyale()) brManager.initLocation(location);

        player.displayClientMessage(Component.literal(
            "§aВи в команді §e" + spawnPoint.getTeamName() +
            "§a! Чекаємо гравців... (потрібно мін. §e" + location.getPvpMinPlayers() + "§a)"), false);

        checkPvpStart(location);
        broadcastPvpSync(location);
        syncPlayerData(player);
    }

    /**
     * Повертає індекс точки спавну з найменшою кількістю гравців (автобаланс).
     * Якщо є обраний гравцем - ігнорується при autoBalance=true.
     */
    public int getAutoBalancedSpawnIndex(Location location, java.util.UUID joiningPlayer) {
        java.util.List<com.wavedefense.data.PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        if (spawns.isEmpty()) return 0;
        // Підрахунок гравців по командам
        java.util.Map<String, Integer> teamCounts = new java.util.LinkedHashMap<>();
        for (com.wavedefense.data.PvpSpawnPoint sp : spawns) teamCounts.put(sp.getTeamName(), 0);
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() == null || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            if (d.getPlayerUUID() == null || d.getPlayerUUID().equals(joiningPlayer)) continue;
            String team = location.getPlayerTeam(d.getPlayerUUID());
            if (team != null) teamCounts.merge(team, 1, Integer::sum);
        }
        // Знаходимо команду з мінімальною кількістю
        String minTeam = teamCounts.entrySet().stream()
            .min(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
        for (int i = 0; i < spawns.size(); i++) {
            if (spawns.get(i).getTeamName().equals(minTeam)) return i;
        }
        return 0;
    }

    /** При виході гравця з PvP — перебалансувати якщо потрібно */
    private void rebalancePvpTeams(Location location, UUID leftPlayer) {
        if (!location.isPvpTeamAutoBalance()) return;
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        if (state == null || state.getPhase() != com.wavedefense.data.PvpRoundState.Phase.WAITING) return;

        // Підрахунок гравців по командах
        java.util.Map<String, java.util.List<UUID>> teamPlayers = new java.util.LinkedHashMap<>();
        for (com.wavedefense.data.PvpSpawnPoint sp : location.getPvpSpawnPoints())
            teamPlayers.put(sp.getTeamName(), new java.util.ArrayList<>());
        for (PlayerWaveData d : playerData.values()) {
            if (d.getCurrentLocation() == null || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            if (d.getPlayerUUID() == null) continue;
            String t = location.getPlayerTeam(d.getPlayerUUID());
            if (t != null && teamPlayers.containsKey(t)) teamPlayers.get(t).add(d.getPlayerUUID());
        }
        if (teamPlayers.size() < 2) return;
        // Знаходимо найбільшу і найменшу команду
        String bigTeam = null, smallTeam = null;
        int bigCount = 0, smallCount = Integer.MAX_VALUE;
        for (java.util.Map.Entry<String, java.util.List<UUID>> e : teamPlayers.entrySet()) {
            if (e.getValue().size() > bigCount) { bigCount = e.getValue().size(); bigTeam = e.getKey(); }
            if (e.getValue().size() < smallCount) { smallCount = e.getValue().size(); smallTeam = e.getKey(); }
        }
        if (bigTeam == null || smallTeam == null || bigTeam.equals(smallTeam)) return;
        if (bigCount - smallCount < 2) return; // різниця менше 2 — не балансуємо
        // Переносимо одного гравця з великої команди в малу
        UUID toMove = teamPlayers.get(bigTeam).get(0);
        ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(toMove);
        if (sp == null) return;
        // Знаходимо нову точку спавну
        com.wavedefense.data.PvpSpawnPoint newSpawn = null;
        for (com.wavedefense.data.PvpSpawnPoint spn : location.getPvpSpawnPoints()) {
            if (spn.getTeamName().equals(smallTeam)) { newSpawn = spn; break; }
        }
        if (newSpawn == null) return;
        final String finalSmallTeam = smallTeam;
        final String finalSpName    = sp.getName().getString();
        location.setPlayerTeam(toMove, finalSmallTeam);
        state.getAllStats().computeIfAbsent(toMove,
            id -> new com.wavedefense.data.PvpPlayerStats(finalSpName, finalSmallTeam))
            .setTeamName(finalSmallTeam);
        teleportToSpawnPoint(sp, newSpawn);
        sp.displayClientMessage(Component.literal(
            "§e⚖ Вас переведено до команди §a" + finalSmallTeam + " §eдля балансу."), false);
    }

    private void checkPvpStart(Location location) {
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        if (state == null || state.getPhase() != com.wavedefense.data.PvpRoundState.Phase.WAITING) return;

        long count = playerData.values().stream()
            .filter(d -> d.getCurrentLocation() != null &&
                         d.getCurrentLocation().getName().equals(location.getName()))
            .count();

        if (count >= location.getPvpMinPlayers()) {
            state.startBuyPhase();
            broadcastToLocation(location.getName(), "§a✓ Достатньо гравців! §eЧас покупок: §a" +
                location.getPvpBuyTime() + " сек.");
        }
    }

    /** PvP tick — викликається з загального tick() */
    public void tickPvp() {
        for (Map.Entry<String, com.wavedefense.data.PvpRoundState> entry : pvpStates.entrySet()) {
            String locName = entry.getKey();
            com.wavedefense.data.PvpRoundState state = entry.getValue();
            Location location = WaveDefenseMod.locationManager.getLocation(locName);
            if (location == null) continue;

            if (state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.BUY) {
                state.tickDown();
                if (state.getTimerTicks() % 20 == 0) broadcastPvpSync(location);
                if (state.getTimerTicks() <= 0) {
                    // Перехід: BUY → COUNTDOWN (якщо є затримка) або одразу ACTIVE
                    int delay = location.getPvpRoundStartDelay();
                    if (delay > 0) {
                        state.startCountdown(delay);
                        broadcastToLocation(location.getName(),
                            "§c⏳ Раунд починається через §e" + delay + " §cсек!");
                        broadcastPvpSync(location);
                    } else {
                        startActiveRound(location, state);
                    }
                }

            } else if (state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.COUNTDOWN) {
                state.tickDown();
                // Повідомлення кожну секунду
                if (state.getTimerTicks() % 20 == 0 && state.getTimerTicks() > 0) {
                    broadcastToLocation(location.getName(),
                        "§c⚔ Раунд " + state.getCurrentRound() + " через §e" + state.getTimerSeconds() + "§c...");
                    broadcastPvpSync(location);
                }
                if (state.getTimerTicks() <= 0) {
                    startActiveRound(location, state);
                }

            } else if (state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE) {
                String winner = state.checkRoundWinner();
                if (winner != null) {
                    // Починаємо затримку перед завершенням раунду (5 сек щоб встигнути відродитись)
                    state.setPendingWinner(winner);
                    state.startRoundEndDelay(5);
                    broadcastToLocation(location.getName(),
                        "§6🏆 §e" + winner + " §6перемогли! §7Раунд завершиться через §e5 §7сек...");
                    broadcastPvpSync(location);
                }

            } else if (state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ROUND_END_DELAY) {
                state.tickDown();
                if (state.getTimerTicks() <= 0) {
                    String pendingWinner = state.getPendingWinner();
                    if (pendingWinner != null) {
                        state.clearPendingWinner();
                        endRound(location, state, pendingWinner);
                    }
                }
            }
        }
    }

    private void startActiveRound(Location location, com.wavedefense.data.PvpRoundState state) {
        Set<UUID> allInLoc = new HashSet<>();
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            if (e.getValue().getCurrentLocation() != null &&
                e.getValue().getCurrentLocation().getName().equals(location.getName())) {
                allInLoc.add(e.getKey());
            }
        }
        state.startActiveRound(allInLoc);
        pvpKillStreaks.clear(); // скидаємо стріки на початку раунду

        for (UUID pid : allInLoc) {
            ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (p == null) continue;
            // Знімаємо ефекти очікування + spectator
            removeWaitEffects(p);
            setSpectator(p, false);
            String team = location.getPlayerTeam(pid);
            if (team != null) {
                for (com.wavedefense.data.PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) { teleportToSpawnPoint(p, sp); break; }
                }
            }
            // Поінти на початку раунду
            if (location.getPvpRoundStartPoints() > 0) {
                location.addPoints(pid, location.getPvpRoundStartPoints());
                p.displayClientMessage(Component.literal(
                    "§a+" + location.getPvpRoundStartPoints() + " §7поінтів на початок раунду"), true);
            }
        }

        // ROUND_START + MATCH_START (тільки для раунду 1)
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.ROUND_START);
        if (state.getCurrentRound() == 1) {
            fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.MATCH_START);
        }
        broadcastToLocation(location.getName(),
            String.format("§c⚔ РАУНД §e%d§c/§e%d §cпочався!",
                state.getCurrentRound(), state.getTotalRounds()));
        broadcastPvpSync(location);
    }

    private void endRound(Location location, com.wavedefense.data.PvpRoundState state, String winnerTeam) {
        state.recordTeamWin(winnerTeam);
        // ROUND_END + TEAM_WIPE лут
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.ROUND_END);
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.TEAM_WIPE);
        broadcastToLocation(location.getName(),
            "§6🏆 §e" + winnerTeam + " §6виграли раунд! §7" + formatTeamWins(state));

        // Поінти за перемогу/поразку у раунді
        if (location.getPvpWinPoints() > 0 || location.getPvpLosePoints() > 0) {
            for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
                if (e.getValue().getCurrentLocation() == null ||
                    !e.getValue().getCurrentLocation().getName().equals(location.getName())) continue;
                String team = location.getPlayerTeam(e.getKey());
                ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(e.getKey());
                if (team == null) continue;
                if (team.equals(winnerTeam) && location.getPvpWinPoints() > 0) {
                    location.addPoints(e.getKey(), location.getPvpWinPoints());
                    if (p != null) p.displayClientMessage(Component.literal(
                        "§a+" + location.getPvpWinPoints() + " §7поінтів за перемогу у раунді"), true);
                } else if (!team.equals(winnerTeam) && location.getPvpLosePoints() > 0) {
                    location.addPoints(e.getKey(), location.getPvpLosePoints());
                    if (p != null) p.displayClientMessage(Component.literal(
                        "§e+" + location.getPvpLosePoints() + " §7поінтів за поразку у раунді"), true);
                }
            }
        }

        if (state.isAllRoundsDone()) {
            endPvpMatch(location, state);
            return;
        }

        // Наступний BUY раунд
        state.startBuyPhase();
        for (ServerPlayer p : getPlayersInLocation(location.getName())) {
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            // BUY фаза: завжди survival (щоб гравці могли торгувати)
            setSpectator(p, false); // знімає spectator якщо був
            removeWaitEffects(p);
            // Якщо pvpWaitEffect — накладаємо slowness щоб не бігали
            if (location.isPvpWaitEffect()) {
                applyWaitEffects(p);
            }
            // Телепортуємо на точку спавну команди
            String team = location.getPlayerTeam(p.getUUID());
            if (team != null) {
                for (com.wavedefense.data.PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) {
                        teleportToSpawnPoint(p, sp);
                        break;
                    }
                }
            }
            syncPlayerData(p);
        }
        // BUY_PHASE лут
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.BUY_PHASE);
        broadcastToLocation(location.getName(),
            "§aЧас покупок: §e" + location.getPvpBuyTime() + " сек. перед раундом §e" +
            state.getCurrentRound() + "§a!");
        broadcastPvpSync(location);
    }

    private void endPvpMatch(Location location, com.wavedefense.data.PvpRoundState state) {
        state.setPhase(com.wavedefense.data.PvpRoundState.Phase.ENDED);

        String champion = state.getTeamWins().entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Нікого");

        broadcastToLocation(location.getName(),
            "§6§l🏆 МАТЧ ЗАВЕРШЕНО! Переможець: §e§l" + champion + " §6§l🏆");
        // MATCH_END лут
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.MATCH_END);
        broadcastPvpSync(location);

        // Визначаємо переможну і програшну команди для видачі поінтів
        String winTeam  = champion;
        int    winPts   = location.getPvpWinPoints();
        int    losePts  = location.getPvpLosePoints();

        List<UUID> toRemove = new ArrayList<>(playerData.entrySet().stream()
            .filter(e -> e.getValue().getCurrentLocation() != null &&
                         e.getValue().getCurrentLocation().getName().equals(location.getName()))
            .map(Map.Entry::getKey)
            .toList());

        // Видача поінтів за результат
        if (winPts > 0 || losePts > 0) {
            for (UUID pid : toRemove) {
                String team = location.getPlayerTeam(pid);
                if (team == null) continue;
                if (team.equals(winTeam) && winPts > 0)  location.addPoints(pid, winPts);
                else if (!team.equals(winTeam) && losePts > 0) location.addPoints(pid, losePts);
            }
        }

        net.minecraft.core.BlockPos victoryExit   = location.getVictoryExitPos();
        net.minecraft.core.BlockPos surrenderExit = location.getSurrenderExitPos();

        for (UUID pid : toRemove) {
            ServerPlayer p = WaveDefenseMod.getServer() != null
                ? WaveDefenseMod.getServer().getPlayerList().getPlayer(pid) : null;
            pvpPendingRespawn.remove(pid);
            if (p != null) {
                // Знімаємо spectator/ефекти очікування
                removeWaitEffects(p);
                if (p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
                // Відновлюємо backup
                PlayerBackup backup = playerBackups.remove(pid);
                if (backup != null) backup.restore(p);
                // Телепортуємо на точку виходу
                String team = location.getPlayerTeam(pid);
                net.minecraft.core.BlockPos exitPos =
                    (team != null && team.equals(winTeam) && victoryExit != null) ? victoryExit
                    : (surrenderExit != null) ? surrenderExit : null;
                if (exitPos != null) {
                    p.teleportTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5);
                }
            }
            playerData.remove(pid);
            location.removePlayerTeam(pid);
            // Синк: порожній PlayerWaveData → клієнт знімає HUD
            if (p != null) syncPlayerData(p);
            if (p != null) clearTeammatesForPlayer(p);
        }
        pvpStates.remove(location.getName());
        spawnedMobsByLocation.remove(location.getName());
        brManager.clearLocation(location.getName());
    }

    private String formatTeamWins(com.wavedefense.data.PvpRoundState state) {
        StringBuilder sb = new StringBuilder();
        state.getTeamWins().forEach((t, w) -> sb.append("§e").append(t).append("§7:").append(w).append(" "));
        return sb.toString().trim();
    }

    public void onPlayerKilledPlayer(ServerPlayer killer, ServerPlayer victim) {
        PlayerWaveData victimData = playerData.get(victim.getUUID());
        if (victimData == null || victimData.getCurrentLocation() == null) return;
        Location location = victimData.getCurrentLocation();
        if (!location.isPvp()) return;

        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        if (state == null || state.getPhase() != com.wavedefense.data.PvpRoundState.Phase.ACTIVE) return;

        String killerTeam = location.getPlayerTeam(killer.getUUID());
        String victimTeam = location.getPlayerTeam(victim.getUUID());
        if (killerTeam != null && killerTeam.equals(victimTeam)) return;

        int kill = location.getPvpKillPoints();
        location.addPoints(killer.getUUID(), kill);
        location.removePoints(victim.getUUID(), location.getPvpDeathPenalty());
        // Kill streak
        int streak = pvpKillStreaks.merge(killer.getUUID(), 1, Integer::sum);
        pvpKillStreaks.remove(victim.getUUID()); // жертва скидає свій стрік
        String streakSuffix = streak >= 3 ? " §6§l[" + streak + " ФРАГИ!]" : "";
        killer.displayClientMessage(
            Component.literal("§a+§e" + kill + " §aочків | вбито §e" + victim.getName().getString() + streakSuffix), true);
        // KILL_STREAK_3 лут (кожен множник 3: 3, 6, 9...)
        if (streak % 3 == 0) {
            fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.KILL_STREAK_3);
        }

        String roundWinner = null;

        if (location.isDeathmatch()) {
            // Deathmatch: смерть не прибирає з aliveThisRound; перемога по кількості вбивств
            state.recordHit(killer.getUUID(), victim.getUUID());
            PvpPlayerStats ks = state.getStats(killer.getUUID());
            if (ks != null) ks.addKill();
            PvpPlayerStats vs = state.getStats(victim.getUUID());
            if (vs != null) vs.addDeath();
            state.recordDmKill(killerTeam); // killerTeam вже оголошено вище
            roundWinner = state.checkDmWinner();
            broadcastToLocation(location.getName(),
                "§c☠ §e" + victim.getName().getString() + " §7вбитий §e" + killer.getName().getString()
                + " §7| §e" + killerTeam + ": §a" + state.getDmTeamKills().getOrDefault(killerTeam, 0)
                + "§7/§a" + state.getDmKillsToWin() + " §7вбивств");
        } else if (location.isBattleRoyale()) {
            // Battle Royale: вибуваємо назавжди, перевіряємо чи залишився 1 гравець
            state.recordDeath(victim.getUUID(), killer.getUUID());
            java.util.UUID brWinnerUuid = state.checkBrWinner();
            if (brWinnerUuid != null) {
                ServerPlayer brWinner = WaveDefenseMod.getServer().getPlayerList().getPlayer(brWinnerUuid);
                roundWinner = brWinner != null ? brWinner.getName().getString() : "Невідомо";
            }
            int alive = state.getAliveThisRound().size();
            broadcastToLocation(location.getName(),
                "§c☠ §e" + victim.getName().getString() + " §7вибув! §7Залишилось живих: §e" + alive);
        } else {
            roundWinner = state.recordDeath(victim.getUUID(), killer.getUUID());
        }
        // Не ставимо spectator тут — PlayerRespawnHandler зробить це після відродження
        // (victim потрапив до pvpPendingRespawn через onPvpPlayerDeath який викликається далі)

        if (roundWinner != null) {
            state.setPendingWinner(roundWinner);
            state.startRoundEndDelay(5);
            broadcastToLocation(location.getName(),
                "§6🏆 §e" + roundWinner + " §6перемогли! §7Раунд завершиться через §e5 §7сек...");
            broadcastPvpSync(location);
        } else {
            updatePvpEnemyCounts(location, state);
            broadcastPvpSync(location);
        }

        for (ServerPlayer p : getPlayersInLocation(location.getName())) syncPlayerData(p);
    }

    public void onPvpHit(ServerPlayer attacker, ServerPlayer victim) {
        PlayerWaveData data = playerData.get(victim.getUUID());
        if (data == null || data.getCurrentLocation() == null || !data.getCurrentLocation().isPvp()) return;
        com.wavedefense.data.PvpRoundState state = pvpStates.get(data.getCurrentLocation().getName());
        if (state != null) state.recordHit(attacker.getUUID(), victim.getUUID());
    }

    /**
     * PvE: гравець загинув → виходить з локації.
     * Backup відновлюється при PlayerRespawnEvent, а не одразу
     * (гравець ще мертвий — teleport/inventory restore має бути після респавну).
     */
    public void onPvePlayerDeath(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // ── Захист від подвійного виклику (race condition / Forge подвійний івент) ──
        // Якщо pendingDeathRestore вже є — гравець вже був оброблений при першій смерті
        if (pendingDeathRestores.containsKey(playerId)) {
            WaveDefenseMod.LOGGER.debug("onPvePlayerDeath: ignored double-call for " + player.getName().getString());
            return;
        }

        PlayerWaveData data = playerData.remove(playerId);
        if (data == null || data.getCurrentLocation() == null) return;

        String locName = data.getCurrentLocation().getName();
        Location locRef = data.getCurrentLocation();
        // PLAYER_DEATH лут (PvE)
        fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.PLAYER_DEATH);

        // Переносимо backup до відкладеного відновлення (виконається після респавну)
        PlayerBackup backup = playerBackups.remove(playerId);
        if (backup != null) {
            pendingDeathRestores.put(playerId, backup);
        }

        // Очищаємо стан локації для цього гравця
        locRef.removePlayerTeam(playerId);
        locRef.removePoints(playerId, locRef.getPlayerPoints(playerId)); // обнуляємо поінти

        boolean anyLeft = playerData.values().stream()
            .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locName));
        if (!anyLeft) {
            spawnedMobsByLocation.remove(locName);
            locationWaveTimers.remove(locName);
            locationStartTimers.remove(locName);
            locationCurrentWave.remove(locName);
        } else {
            for (ServerPlayer p : getPlayersInLocation(locName)) syncPlayerData(p);
            syncTeammates(locName);
        }

        data.setCurrentLocation(null);
        syncPlayerData(player);
        clearTeammatesForPlayer(player);

        broadcastToLocation(locName,
            "§c☠ §e" + player.getName().getString() + " §cзагинув і вийшов з локації.");
    }

    /** UUID гравців що загинули у PvP ACTIVE раунді — очікують на респавн → spectator */
    private final java.util.Set<UUID> pvpPendingRespawn = java.util.Collections.synchronizedSet(
        new java.util.HashSet<>());

    public java.util.Set<UUID> getPvpPendingRespawn() { return pvpPendingRespawn; }

    /** Kill streak counter: UUID → consecutive kills without dying */
    private final Map<UUID, Integer> pvpKillStreaks = new java.util.concurrent.ConcurrentHashMap<>();

    public void onPvpPlayerDeath(ServerPlayer player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null || !data.getCurrentLocation().isPvp()) return;
        Location location = data.getCurrentLocation();
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        // PLAYER_DEATH лут (загальний тригер)
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.PLAYER_DEATH);

        if (state != null && state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE) {
            location.removePoints(player.getUUID(), location.getPvpDeathPenalty());

            if (location.isDeathmatch()) {
                // Deathmatch: штраф є, але гравець НЕ виходить з raund — pvpPendingRespawn додається
                // щоб PlayerRespawnHandler спрацював і одразу повернув гравця в бій
                pvpPendingRespawn.add(player.getUUID());
                // Рахунок вбивств — onPlayerKilledPlayer вже обробив, тут тільки без killer
                String dmWinner = state.checkDmWinner();
                if (dmWinner != null) {
                    state.setPendingWinner(dmWinner);
                    state.startRoundEndDelay(3);
                    broadcastToLocation(location.getName(),
                        "§6🏆 §e" + dmWinner + " §6набрали §e" + state.getDmKillsToWin()
                        + "§6 вбивств — перемога! §7Завершення через §e3 §7сек...");
                    broadcastPvpSync(location);
                } else {
                    broadcastPvpSync(location);
                }
            } else if (location.isBattleRoyale()) {
                // BR: гравець вибуває, але pvpPendingRespawn щоб PlayerRespawnHandler
                // перемістив у spectator
                pvpPendingRespawn.add(player.getUUID());
                int alive = state.getAliveThisRound().size();
                java.util.UUID brWinnerUuid = state.checkBrWinner();
                if (brWinnerUuid != null) {
                    ServerPlayer brWinner = WaveDefenseMod.getServer().getPlayerList().getPlayer(brWinnerUuid);
                    String winName = brWinner != null ? brWinner.getName().getString() : "Невідомий";
                    state.setPendingWinner(winName);
                    state.startRoundEndDelay(5);
                    broadcastToLocation(location.getName(),
                        "§6🏆 §e" + winName + " §6— останній вцілілий! §7Завершення через §e5 §7сек...");
                    broadcastPvpSync(location);
                } else {
                    broadcastToLocation(location.getName(),
                        "§c☠ §e" + player.getName().getString() + " §7вибув! Залишилось: §e" + alive);
                    broadcastPvpSync(location);
                }
            } else {
                // Standard: гравець іде у spectator
                String roundWinner = state.recordDeath(player.getUUID(), null);
                pvpPendingRespawn.add(player.getUUID());
                if (roundWinner != null) {
                    state.setPendingWinner(roundWinner);
                    state.startRoundEndDelay(5);
                    broadcastToLocation(location.getName(),
                        "§6🏆 §e" + roundWinner + " §6перемогли! §7Раунд завершиться через §e5 §7сек...");
                    broadcastPvpSync(location);
                } else {
                    updatePvpEnemyCounts(location, state);
                    broadcastPvpSync(location);
                }
            }
        }
    }

    public boolean canPvpAttack(ServerPlayer attacker, ServerPlayer target) {
        PlayerWaveData data = playerData.get(attacker.getUUID());
        if (data == null || data.getCurrentLocation() == null) return true;
        Location location = data.getCurrentLocation();
        if (!location.isPvp()) return true;
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        if (state == null || state.getPhase() != com.wavedefense.data.PvpRoundState.Phase.ACTIVE) return false;
        if (location.isPvpFriendlyFire()) return true;
        return !location.isSameTeam(attacker.getUUID(), target.getUUID());
    }

    private void updatePvpEnemyCounts(Location location, com.wavedefense.data.PvpRoundState state) {
        Set<UUID> alive = state.getAliveThisRound();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            PlayerWaveData d = entry.getValue();
            if (d.getCurrentLocation() == null || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            UUID pid = entry.getKey();
            String myTeam = location.getPlayerTeam(pid);
            int enemies = (int) alive.stream()
                .filter(id -> !id.equals(pid))
                .filter(id -> { String t = location.getPlayerTeam(id); return t != null && !t.equals(myTeam); })
                .count();
            d.setMobsRemaining(enemies);
        }
    }

    /** Телепортує гравця в центр (або у зону radius навколо нього якщо radius>0). */
    public void teleportToSafeSpawn(ServerPlayer player, net.minecraft.core.BlockPos center) {
        teleportToSafeSpawn(player, center, 0);
    }

    /** Телепортує гравця у випадкову безпечну позицію в радіусі radius від center. */
    public void teleportToSafeSpawn(ServerPlayer player, net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (radius > 0) {
            java.util.Random rng = new java.util.Random();
            // Кілька спроб знайти безпечне місце в заданому радіусі
            for (int attempt = 0; attempt < 16; attempt++) {
                int dx = rng.nextInt(radius * 2 + 1) - radius;
                int dz = rng.nextInt(radius * 2 + 1) - radius;
                net.minecraft.core.BlockPos candidate = center.offset(dx, 0, dz);
                // Шукаємо безпечний блок на цій XZ позиції
                net.minecraft.core.BlockPos safe = findSafePos(level, candidate, 3);
                if (isSafePos(level, safe)) {
                    player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                    return;
                }
            }
        }
        // Fallback: звичайний безпечний спавн біля центру
        net.minecraft.core.BlockPos safe = findSafePos(level, center, 5);
        player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }

    /** Зручний метод для PvpSpawnPoint — враховує його спawnRadius. */
    public void teleportToSpawnPoint(ServerPlayer player,
                                     com.wavedefense.data.PvpSpawnPoint sp) {
        teleportToSafeSpawn(player, sp.getPos(), sp.getSpawnRadius());
    }

    private net.minecraft.core.BlockPos findSafePos(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos center, int radius) {
        if (isSafePos(level, center)) return center;
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy : new int[]{0, 1, -1}) {
                        net.minecraft.core.BlockPos c = center.offset(dx, dy, dz);
                        if (isSafePos(level, c)) return c;
                    }
                }
            }
        }
        return center;
    }

    private boolean isSafePos(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && !level.getBlockState(pos.below()).isAir();
    }

    private void setSpectator(ServerPlayer player, boolean spectator) {
        if (spectator) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        } else {
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Накладає ефекти slowness+blindness рівня 127 (нерухомість і темрява) на час очікування PvP */
    private void applyWaitEffects(ServerPlayer player) {
        // Рівень 127 = фактично нерухомість
        net.minecraft.world.effect.MobEffectInstance slow = new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 127, false, false);
        net.minecraft.world.effect.MobEffectInstance blind = new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, false);
        player.addEffect(slow);
        player.addEffect(blind);
    }

    /** Публічний wrapper — для виклику з PlayerRespawnHandler */
    public void reapplyWaitEffects(ServerPlayer player) { applyWaitEffects(player); }

    /** Знімає ефекти очікування (slowness і blindness) */
    private void removeWaitEffects(ServerPlayer player) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
    }

    private void broadcastPvpSync(Location location) {
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());
        if (state == null) return;

        List<com.wavedefense.network.packets.SyncPvpStatePacket.PlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, com.wavedefense.data.PvpPlayerStats> e : state.getAllStats().entrySet()) {
            com.wavedefense.data.PvpPlayerStats ps = e.getValue();
            boolean alive = state.getAliveThisRound().contains(e.getKey());
            entries.add(new com.wavedefense.network.packets.SyncPvpStatePacket.PlayerEntry(
                ps.getPlayerName(), ps.getTeamName(), ps.getKills(), ps.getDeaths(), ps.getAssists(), alive));
        }

        for (ServerPlayer p : getPlayersInLocation(location.getName())) {
            String myTeam = location.getPlayerTeam(p.getUUID());
            net.minecraft.nbt.CompoundTag tag = com.wavedefense.network.packets.SyncPvpStatePacket.build(
                location.getName(), state.getPhase().name(),
                state.getCurrentRound(), state.getTotalRounds(), state.getTimerSeconds(),
                state.getTeamWins(), entries, myTeam);
            WaveDefenseMod.packetHandler.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavedefense.network.packets.SyncPvpStatePacket(tag));
        }
    }

    public com.wavedefense.data.PvpRoundState getPvpState(String locationName) {
        return pvpStates.get(locationName);
    }

    // ════════════════════════════════════════════════════════════════════
    //  BOUNDARY CHECK — вихід за радіус локації
    // ════════════════════════════════════════════════════════════════════

    // Глобальний кулдаун між будь-якими тригерними хвилями на локацію (5 секунд)
    // Глобальний кулдаун між будь-якими тригерними хвилями на локацію (5 секунд)
    /** Таймер виходу: секунд до здачі. 0 = не активний. */
    /**
     * @deprecated Logic moved to {@link BoundaryManager#tick(WaveManager)}.
     *             Called from tick() via boundaryMgr.tick(this).
     *             Kept for reference only — not called directly.
     */
    @Deprecated
    private void tickBoundaryCheck() {
        if (WaveDefenseMod.getServer() == null) return;
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            UUID uid = e.getKey();
            PlayerWaveData data = e.getValue();
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (!loc.isLocationBoundaryEnabled()) continue;
            if (loc.getPlayerSpawn() == null) continue;

            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;

            double dist = Math.sqrt(player.blockPosition().distSqr(loc.getPlayerSpawn()));
            boolean outside = dist > loc.getLocationBoundaryRadius();

            if (outside) {
                int ticks = leaveCountdownTicks.getOrDefault(uid, 0);
                if (ticks <= 0) {
                    // Починаємо відлік — одразу показуємо title
                    int secs = loc.getLocationLeaveTimerSec();
                    leaveCountdownTicks.put(uid, secs * 20);
                    // Title
                    net.minecraft.network.chat.MutableComponent title0 =
                        net.minecraft.network.chat.Component.literal("§c⚠ Ви покидаєте Бій");
                    net.minecraft.network.chat.MutableComponent subtitle0 =
                        net.minecraft.network.chat.Component.literal("§eПоверніться! §c" + secs + " сек");
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title0));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle0));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 25, 10));
                } else {
                    ticks--;
                    leaveCountdownTicks.put(uid, ticks);
                    // Title кожну секунду
                    if (ticks % 20 == 0) {
                        int secsLeft = ticks / 20;
                        net.minecraft.network.chat.MutableComponent title =
                            net.minecraft.network.chat.Component.literal("§c⚠ Ви покидаєте Бій");
                        net.minecraft.network.chat.MutableComponent subtitle =
                            net.minecraft.network.chat.Component.literal(secsLeft > 0
                                ? "§eПоверніться! §c" + secsLeft + " сек"
                                : "§cЧас вийшов!");
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 25, 10));
                    }
                    if (ticks <= 0) {
                        // Час вийшов — здається
                        leaveCountdownTicks.remove(uid);
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cВи не повернулись у зону бою — здача!"), false);
                        surrenderPlayer(player);
                    }
                }
            } else {
                // Повернувся — скасовуємо відлік
                if (leaveCountdownTicks.containsKey(uid)) {
                    leaveCountdownTicks.remove(uid);
                    // Прибираємо title
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(false));
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a✓ Ви повернулись у зону бою"), false);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  LOCATION TRIGGER — запуск локації по тригеру
    // ════════════════════════════════════════════════════════════════════

    private void tickLocationTriggers() {
        if (WaveDefenseMod.getServer() == null) return;
        long now = System.currentTimeMillis();
        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!loc.isLocationTriggerEnabled()) continue;
            if (loc.isPvp()) continue;
            if (loc.getPlayerSpawn() == null) continue;

            String name = loc.getName();
            // Якщо вже активна — пропускаємо
            boolean active = playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(name));
            if (active) continue;

            // Збираємо гравців поблизу (не в локації)
            // Радіус: якщо locationTriggerEnabled — використовуємо autoActivateRadius або boundary
            int radius = Math.max(5, loc.isAutoActivate()
                ? loc.getAutoActivateRadius()
                : (loc.isLocationBoundaryEnabled() ? loc.getLocationBoundaryRadius() : 50));

            java.util.List<ServerPlayer> nearbyPlayers = new java.util.ArrayList<>();
            for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                if (playerData.containsKey(p.getUUID())) continue;
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
                activateZoneForPlayers(loc, inRange);
                debugAdmin("§7Location trigger §e" + name
                    + "§7: §e" + inRange.size() + "§7 гравців телепортовано до локації");
                debugLog("Location trigger '" + name + "': " + inRange.size() + " players teleported");
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
    private void fireWaveTriggerForLocation(String locName, com.wavedefense.data.WaveTrigger trigger) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locName);
        if (loc == null || loc.isPvp()) return;
        int curWave = locationCurrentWave.getOrDefault(locName, 1);

        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            com.wavedefense.data.WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != trigger) continue;
            if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
            if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
            boolean andOk = true;
            java.util.List<com.wavedefense.data.WaveTrigger> extras = wave.getExtraTriggers();
            for (com.wavedefense.data.WaveTrigger extra : (extras != null ? extras : java.util.Collections.<com.wavedefense.data.WaveTrigger>emptyList())) {
                boolean extraOk = (extra == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM)
                    ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                    : checkWaveTriggerCondition(extra, locName, null);
                if (!extraOk) { andOk = false; break; }
            }
            if (!andOk) continue;
            String coolKey = locName + "_w" + wi;
            if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
            fireTriggerWave(loc, wi);
            recordTriggerWaveFire(wave, coolKey, locName);
            if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
        }
    }

    private void tickWaveTriggers() {
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            PlayerWaveData data = e.getValue();
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (loc.isPvp()) continue;
            String locName = loc.getName();

            // Тільки один раз на локацію за тік
            break; // Обробляємо локацію нижче окремо
        }
        // Обробка по локаціях
        for (String locName : getActiveLocationNames()) {
            Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc == null || loc.isPvp()) continue;
            for (int wi = 0; wi < loc.getWaves().size(); wi++) {
                com.wavedefense.data.WaveConfig wave = loc.getWaves().get(wi);
                if (!wave.isTriggerEnabled()) continue;
                // Перевіряємо cooldown
                String coolKey = locName + "_w" + wi;
                if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
                // Перевіряємо тригер
                // TIMER_* обробляються окремим per-location timer loop — тут пропускаємо
                com.wavedefense.data.WaveTrigger wTrigType = wave.getTriggerType();
                if (wTrigType == com.wavedefense.data.WaveTrigger.TIMER_60 ||
                    wTrigType == com.wavedefense.data.WaveTrigger.TIMER_120 ||
                    wTrigType == com.wavedefense.data.WaveTrigger.TIMER_300 ||
                    wTrigType == com.wavedefense.data.WaveTrigger.TIMER_CUSTOM) continue; // handled separately
                // PLAYER_HAS_ITEM: перевіряємо itemId ЦІЄЇ хвилі, не шукаємо по всіх
                if (wTrigType == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM) {
                    if (!checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)) continue;
                } else {
                    if (!checkWaveTriggerCondition(wTrigType, locName, null)) continue;
                }
                // Разово
                if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
                // activateFromWave
                int curWave2 = locationCurrentWave.getOrDefault(locName, 1);
                if (wave.getActivateFromWave() > 0 && curWave2 < wave.getActivateFromWave()) continue;
                // AND умови
                boolean andOk = true;
                java.util.List<com.wavedefense.data.WaveTrigger> extras = wave.getExtraTriggers();
                for (com.wavedefense.data.WaveTrigger extra : (extras != null ? extras : java.util.Collections.<com.wavedefense.data.WaveTrigger>emptyList())) {
                    if (!checkWaveTriggerCondition(extra, locName, null)) { andOk = false; break; }
                }
                if (!andOk) continue;
                // Запускаємо хвилю паралельно
                fireTriggerWave(loc, wi);
                recordTriggerWaveFire(wave, coolKey, locName);
                if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
            }
        }
    }

    /**
     * Тікає TIMER_CUSTOM тригери для конкретної локації.
     * Кожна хвиля-тригер з TIMER_CUSTOM має свій незалежний лічильник.
     */
    private void tickTimerCustomForLocation(String locName) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locName);
        if (loc == null) return;
        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            com.wavedefense.data.WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != com.wavedefense.data.WaveTrigger.TIMER_CUSTOM) continue;
            int intervalTicks = Math.max(5, wave.getTriggerCustomValue()) * 20;
            String key = locName + "_tc_" + wi;
            int ticks = locationTimerCustom.getOrDefault(key, 0) + 1;
            locationTimerCustom.put(key, ticks);
            if (ticks >= intervalTicks) {
                locationTimerCustom.put(key, 0);
                // Перевіряємо всі умови і файримо хвилю
                if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
                int curWave = locationCurrentWave.getOrDefault(locName, 1);
                if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
                boolean andOk = true;
                for (com.wavedefense.data.WaveTrigger extra : (wave.getExtraTriggers() != null ? wave.getExtraTriggers() : java.util.Collections.<com.wavedefense.data.WaveTrigger>emptyList())) {
                    boolean extraOk = (extra == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM)
                        ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                        : checkWaveTriggerCondition(extra, locName, null);
                    if (!extraOk) { andOk = false; break; }
                }
                if (!andOk) continue;
                String coolKey = locName + "_w" + wi;
                if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
                fireTriggerWave(loc, wi);
                recordTriggerWaveFire(wave, coolKey, locName);
                if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
            }
        }
    }

    private static final long MIN_TRIGGER_WAVE_COOLDOWN_MS = 5000L; // обов'язкові 5 сек між активаціями

    private boolean isTriggerWaveCooldownReady(com.wavedefense.data.WaveConfig wave, String key, String locName) {
        long lastFired = waveTriggerLastFired.getOrDefault(key, 0L);
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
                int wavesDone = waveTriggerWaveCounters.getOrDefault(key, 0);
                return wavesDone >= wave.getCooldownValue();
            }
        }
        return elapsed >= MIN_TRIGGER_WAVE_COOLDOWN_MS;
    }

    private void recordTriggerWaveFire(com.wavedefense.data.WaveConfig wave, String key, String locName) {
        // Завжди записуємо час — для забезпечення мінімальної 5-секундної паузи
        waveTriggerLastFired.put(key, System.currentTimeMillis());
        if (wave.getCooldownMode() == com.wavedefense.data.WaveConfig.CooldownMode.WAVES) {
            waveTriggerWaveCounters.put(key, 0);
        }
    }

    /** Скидаємо лічильник хвиль у cooldown при завершенні основної хвилі */
    private void incrementWaveTriggerCounters(String locName) {
        waveTriggerWaveCounters.replaceAll((k, v) -> k.startsWith(locName + "_w") ? v + 1 : v);
    }

    private void fireTriggerWave(Location location, int waveIndex) {
        if (waveIndex < 0 || waveIndex >= location.getWaves().size()) return;
        List<ServerPlayer> players = getPlayersInLocation(location.getName());
        if (players.isEmpty()) return;
        ServerLevel world = players.get(0).serverLevel();
        com.wavedefense.data.WaveConfig wave = location.getWaves().get(waveIndex);

        broadcastToLocation(location.getName(),
            "§d⚡ Тригерна хвиля §e" + (waveIndex+1) + " §dзапущена!");

        Set<UUID> spawnedMobs = spawnedMobsByLocation.computeIfAbsent(
            location.getName() + "_trigger_" + waveIndex, k -> new HashSet<>());

        Random rng = new Random();
        for (com.wavedefense.data.WaveMob waveMob : wave.getMobs()) {
            net.minecraft.world.entity.EntityType<?> entityType =
                ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;
            int count = waveMob.getCount();
            for (int i = 0; i < count; i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                // Пріоритет: waveSpawnPos хвилі → точки спавну локації
                BlockPos sp = wave.hasWaveSpawnPos()
                    ? wave.getWaveSpawnPos()
                    : getRandomSpawnPoint(location);
                if (sp == null) continue;
                try {
                    net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) entityType.create(world);
                    if (mob != null) {
                        mob.moveTo(sp.getX()+0.5, sp.getY(), sp.getZ()+0.5, 0, 0);
                        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(sp),
                            net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                        mob.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                            mob, net.minecraft.world.entity.player.Player.class, true));
                        mob.setPersistenceRequired();
                        mob.getPersistentData().putString("location", location.getName());
                        mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());
                        applyMobEquipment(mob, waveMob);
                        world.addFreshEntity(mob);
                        spawnedMobs.add(mob.getUUID());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  PORTALS — рандомна поява порталу
    // ════════════════════════════════════════════════════════════════════

    private static final int PORTAL_PARTICLE_RADIUS_BASE = 2; // блоки
    private static final int PORTAL_HEIGHT_BASE          = 2;

    private void tickPortals() {
        if (WaveDefenseMod.getServer() == null) return;

        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!loc.isPortalEnabled()) continue;
            String name = loc.getName();

            // Якщо гравці в локації — тікаємо штрафний таймер
            boolean active = playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(name));

            net.minecraft.core.BlockPos portalPos = portalPositions.get(name);

            if (portalPos != null) {
                // Рендер частинок порталу
                spawnPortalParticles(loc, portalPos);

                // Перевіряємо вхід гравця в портал
                // Якщо є portalOpenUntilMs — портал відкритий для запізнілих
                boolean portalOpenForLate = false;
                Long portalOpenUntil = portalOpenUntilMs.get(name);
                if (portalOpenUntil != null) {
                    if (System.currentTimeMillis() > portalOpenUntil) {
                        portalOpenUntilMs.remove(name);
                        portalPositions.remove(name); // закриваємо портал
                        broadcastNearPortal(portalPos, name, "§5🌀 Портал §e" + name + "§5 зачинено.");
                        continue;
                    }
                    portalOpenForLate = true;
                }

                for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                    if (playerData.containsKey(p.getUUID())) continue; // вже в грі
                    double dist = p.blockPosition().distSqr(portalPos);
                    if (dist <= 4) { // 2 блоки
                        if (portalOpenForLate) {
                            // Запізнілий гравець — одразу підключаємо до активної локації
                            addPlayerToLocation(p, loc);
                            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§5🌀 Ви приєдналися до активної локації §e" + loc.getName() + "§5!"), false);
                        } else {
                            enterPortal(p, loc, portalPos);
                        }
                        break;
                    }
                }
                if (!portalOpenForLate) {
                    // Перевіряємо завершення grace period після входу першого гравця
                    closePortalIfGraceExpired(name, portalPos);
                }

                if (!active) {
                    // Штрафний таймер
                    // Для режиму "всі хвилі по порядку" (penaltyWave=-1): пауза поки є живі моби
                    if (loc.getPortalPenaltyWave() == -1) {
                        Set<UUID> prevMobs = portalPenaltyMobs.get(name);
                        if (prevMobs != null && !prevMobs.isEmpty()) {
                            ServerLevel checkWorld = (ServerLevel) WaveDefenseMod.getServer()
                                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
                            if (checkWorld != null) {
                                prevMobs.removeIf(uuid -> {
                                    var ent = checkWorld.getEntity(uuid);
                                    return ent == null || !ent.isAlive();
                                });
                            }
                            if (!prevMobs.isEmpty()) continue; // чекаємо поки всі моби не вбиті
                        }
                    }
                    int timer = portalPenaltyTimers.getOrDefault(name, loc.getPortalPenaltyTimerSec() * 20);
                    timer--;
                    if (timer <= 0) {
                        // Запускаємо штрафну хвилю навколо порталу
                        firePenaltyWaveAtPortal(loc, portalPos);
                        // Скидаємо таймер
                        portalPenaltyTimers.put(name, loc.getPortalPenaltyTimerSec() * 20);
                    } else {
                        portalPenaltyTimers.put(name, timer);
                        // Попередження
                        if (timer % (20 * 15) == 0) {
                            int secsLeft = timer / 20;
                            broadcastNearPortal(portalPos, name,
                                "§c⚠ Портал §e" + name + "§c: штрафна хвиля через §e" + secsLeft + " сек§c!");
                        }
                    }
                }
            } else {
                // Портал відсутній — перевіряємо таймер відродження
                int respawn = portalRespawnTimers.getOrDefault(name, -1);
                if (respawn < 0) {
                    // Перший запуск — одразу спавнимо портал
                    spawnPortalAtRandom(loc);
                } else if (respawn > 0) {
                    portalRespawnTimers.put(name, respawn - 1);
                } else {
                    // Час — спавнимо портал у новому місці
                    spawnPortalAtRandom(loc);
                }
            }
        }
    }

    private void spawnPortalAtRandom(Location loc) {
        if (WaveDefenseMod.getServer() == null) return;
        // Знаходимо позицію де є гравці (або random у overworld)
        net.minecraft.server.level.ServerLevel world =
            (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer()
                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (world == null) return;

        // Беремо позицію найближчого онлайн-гравця поза локацією + рандомний оффсет
        java.util.List<ServerPlayer> all = WaveDefenseMod.getServer().getPlayerList().getPlayers();
        if (all.isEmpty()) return;
        ServerPlayer target = all.get(new Random().nextInt(all.size()));
        Random rng = new Random();
        int ox = (rng.nextInt(61) - 30);
        int oz = (rng.nextInt(61) - 30);
        BlockPos base = target.blockPosition().offset(ox, 0, oz);
        // Знаходимо безпечну висоту
        int y = world.getHeightmapPos(
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base).getY();
        BlockPos portalPos = new BlockPos(base.getX(), y + 1, base.getZ());

        portalPositions.put(loc.getName(), portalPos);
        portalPenaltyTimers.put(loc.getName(), loc.getPortalPenaltyTimerSec() * 20);

        // Оголошення всім гравцям
        for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§5🌀 Портал до §e" + loc.getName() + "§5 зʼявився!"),
                false);
        }
    }

    // Таймер grace period після входу першого гравця (30 сек)
    // NOTE: these two are local to portal logic only, kept in WaveManager intentionally
    private final Map<String, Long> portalGraceEndTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> portalEnteredCount = new ConcurrentHashMap<>();

    private void enterPortal(ServerPlayer player, Location loc, net.minecraft.core.BlockPos portalPos) {
        String name = loc.getName();
        int enteredCount = portalEnteredCount.getOrDefault(name, 0);

        if (enteredCount == 0) {
            // Перший гравець — зберігаємо позицію входу і відкриваємо grace period
            portalEntryPositions.put(name, portalPos);
            portalFirstPlayerEntered.add(name);
            // Grace period: або порталOpenAfterStartSec локації, або 30 сек за замовчуванням
            int graceMs = loc.getPortalOpenAfterStartSec() >= 0
                ? loc.getPortalOpenAfterStartSec() * 1000
                : 30000; // -1 = стара поведінка: 30 сек
            if (graceMs == 0) graceMs = 1; // одразу — але обробить on next tick
            portalGraceEndTime.put(name, System.currentTimeMillis() + graceMs);
            // portalOpenUntilMs — для "запізнілих" після старту локації
            if (loc.getPortalOpenAfterStartSec() > 0) {
                portalOpenUntilMs.put(name, System.currentTimeMillis() + loc.getPortalOpenAfterStartSec() * 1000L);
            }
            portalEnteredCount.put(name, 1);

            addPlayerToLocation(player, loc);
            int graceDisplay = graceMs / 1000;
            String graceMsg = graceDisplay > 0 ? " §7(" + graceDisplay + " сек для інших)" : "";
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§5🌀 Ви увійшли в портал §e" + loc.getName() + "§5!" + graceMsg),
                false);
            if (graceDisplay > 0) {
                broadcastNearPortal(portalPos, name, "§5🌀 §e" + player.getName().getString()
                    + " §5увійшов у портал! У вас §e" + graceDisplay + " §5сек щоб приєднатись.");
            }
        } else {
            // Наступні гравці в grace period
            long grace = portalGraceEndTime.getOrDefault(name, 0L);
            if (System.currentTimeMillis() <= grace) {
                portalEnteredCount.put(name, enteredCount + 1);
                addPlayerToLocation(player, loc);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§5🌀 Ви увійшли в портал §e" + loc.getName() + "§5!"),
                    false);
            } else {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cПортал вже закрито. Зачекайте наступного."),
                    true);
            }
        }
    }

    /** Закриває портал після grace period (викликається з tickPortals) */
    private void closePortalIfGraceExpired(String locName, net.minecraft.core.BlockPos portalPos) {
        Long graceEnd = portalGraceEndTime.get(locName);
        if (graceEnd != null && System.currentTimeMillis() > graceEnd) {
            // Grace period скінчився — видаляємо портал
            portalPositions.remove(locName);
            portalPenaltyTimers.remove(locName);
        portalPenaltyWaveIndex.remove(locName);
        portalPenaltyMobs.remove(locName);
            portalGraceEndTime.remove(locName);
            portalEnteredCount.remove(locName);
            // Якщо після проходження локації потрібно відродити — налаштовується автоматично
            Location loc = WaveDefenseMod.locationManager.getLocation(locName);
            if (loc != null && loc.isPortalDisappearsOnComplete()) {
                portalRespawnTimers.put(locName, loc.getPortalRespawnTimerSec() * 20);
            }
        }
    }

    private void firePenaltyWaveAtPortal(Location loc, net.minecraft.core.BlockPos portalPos) {
        if (WaveDefenseMod.getServer() == null) return;
        net.minecraft.server.level.ServerLevel world =
            (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer()
                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (world == null || loc.getWaves().isEmpty()) return;

        if (loc.getPortalPenaltyWave() == -1) {
            // Режим "всі хвилі по порядку" — спавнимо наступну після зачистки попередньої
            java.util.List<com.wavedefense.data.WaveConfig> normalWaves = loc.getWaves().stream()
                .filter(w -> !w.isTriggerEnabled())
                .collect(java.util.stream.Collectors.toList());
            if (normalWaves.isEmpty()) return;

            // Перевіряємо чи попередня штрафна хвиля зачищена
            Set<UUID> prevMobs = portalPenaltyMobs.get(loc.getName());
            if (prevMobs != null && !prevMobs.isEmpty()) {
                prevMobs.removeIf(uuid -> { var e = world.getEntity(uuid); return e == null || !e.isAlive(); });
                if (!prevMobs.isEmpty()) return; // ще є живі — чекаємо наступного тіку таймера
            }

            // Визначаємо індекс наступної хвилі
            int idx = portalPenaltyWaveIndex.getOrDefault(loc.getName(), 0);
            if (idx >= normalWaves.size()) {
                idx = 0; // цикл по-новому
                broadcastNearPortal(portalPos, loc.getName(),
                    "§c⚡ Штрафний цикл перезапущено! §e" + loc.getName());
            }
            broadcastNearPortal(portalPos, loc.getName(),
                "§c💥 Штрафна хвиля §e" + (idx + 1) + "§c/" + normalWaves.size()
                + " порталу §e" + loc.getName() + "§c!");
            Set<UUID> spawnedNow = new HashSet<>();
            spawnWaveAroundPos(normalWaves.get(idx), loc, world, portalPos, 8, spawnedNow);
            portalPenaltyMobs.put(loc.getName(), spawnedNow);
            portalPenaltyWaveIndex.put(loc.getName(), idx + 1);
            debugLog("Portal sequential penalty wave " + (idx + 1) + "/" + normalWaves.size() + " for " + loc.getName());
        } else {
            int wi = loc.getPortalPenaltyWave();
            broadcastNearPortal(portalPos, loc.getName(),
                "§c💥 Штрафна хвиля порталу §e" + loc.getName() + "§c!");
            if (wi >= 0 && wi < loc.getWaves().size()) {
                spawnWaveAroundPos(loc.getWaves().get(wi), loc, world, portalPos, 8, new HashSet<>());
            }
        }
    }

    private void spawnWaveAroundPos(com.wavedefense.data.WaveConfig wave, Location loc,
                                     net.minecraft.server.level.ServerLevel world,
                                     net.minecraft.core.BlockPos center, int radius,
                                     Set<UUID> trackSet) {
        Random rng = new Random();
        for (com.wavedefense.data.WaveMob waveMob : wave.getMobs()) {
            net.minecraft.world.entity.EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (et == null) continue;
            for (int i = 0; i < waveMob.getCount(); i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                double angle = rng.nextDouble() * 2 * Math.PI;
                double r = rng.nextDouble() * radius;
                BlockPos sp = center.offset((int)(r * Math.cos(angle)), 0, (int)(r * Math.sin(angle)));
                try {
                    net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) et.create(world);
                    if (mob == null) continue;
                    mob.moveTo(sp.getX()+0.5, sp.getY(), sp.getZ()+0.5, 0, 0);
                    mob.finalizeSpawn(world, world.getCurrentDifficultyAt(sp),
                        net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                    mob.setPersistenceRequired();
                    mob.getPersistentData().putString("location", loc.getName() + "_portal");
                    applyMobEquipment(mob, waveMob);
                    world.addFreshEntity(mob);
                    if (trackSet != null) trackSet.add(mob.getUUID());
                } catch (Exception ignored) {}
            }
        }
    }

    private void spawnPortalParticles(Location loc, net.minecraft.core.BlockPos pos) {
        net.minecraft.server.level.ServerLevel world =
            (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer()
                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (world == null) return;

        // Розмір порталу залежить від кількості хвиль та мобів
        int waveCount = loc.getWaves().size();
        int totalMobs = loc.getWaves().stream()
            .mapToInt(w -> w.getMobs().stream().mapToInt(m -> m.getCount()).sum()).sum();
        float radiusF = Math.min(10, PORTAL_PARTICLE_RADIUS_BASE + waveCount * 0.3f + totalMobs * 0.02f);
        float heightF = Math.min(8,  PORTAL_HEIGHT_BASE           + waveCount * 0.2f + totalMobs * 0.01f);
        radiusF = Math.max(2, radiusF);
        heightF = Math.max(2, heightF);

        // Кольір: від синього (мало) до пурпурного (багато)
        float r = Math.min(1, totalMobs / 200f);
        float g = 0;
        float b = 1 - r * 0.3f;

        // Вертикальне коло (в площині XZ з кутом) + заповнення
        int steps = Math.max(16, (int)(radiusF * 12));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            // Зовнішнє кільце — вертикально (XY площина)
            double px = pos.getX() + 0.5 + radiusF * Math.cos(angle);
            double py = pos.getY() + 1 + heightF * Math.sin(angle);
            double pz = pos.getZ() + 0.5;
            world.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                px, py, pz, 1, 0, 0, 0, 0.05);
            world.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                px, py, pz, 1, 0, 0, 0, 0.02);
        }
        // Заповнення
        for (int fy = 0; fy < (int)(heightF * 2); fy++) {
            for (int fx = 0; fx < (int)(radiusF * 2); fx++) {
                double px = pos.getX() + 0.5 - radiusF + fx;
                double py = pos.getY() + 1 - heightF + fy;
                if (Math.pow((px - pos.getX() - 0.5) / radiusF, 2) +
                    Math.pow((py - pos.getY() - 1) / heightF, 2) <= 1.0) {
                    world.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        px, py, pos.getZ() + 0.5, 0, 0, 0, 0, 0.01);
                }
            }
        }
    }

    private void broadcastNearPortal(net.minecraft.core.BlockPos pos, String locName, String msg) {
        if (WaveDefenseMod.getServer() == null) return;
        for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (p.blockPosition().distSqr(pos) <= 2500) { // 50 блоків
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), false);
            }
        }
    }

    /** Перевіряємо умову WaveTrigger для даної локації */
    /**
     * Перевіряє чи має будь-який гравець у локації один із предметів зі списку через кому.
     * Використовується для PLAYER_HAS_ITEM тригера з конкретної хвилі.
     */
    private boolean checkPlayerHasItemMulti(String itemIds, String locName) {
        if (itemIds == null || itemIds.isBlank()) return false;
        java.util.List<ServerPlayer> players = getPlayersInLocation(locName);
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

    private boolean checkWaveTriggerCondition(com.wavedefense.data.WaveTrigger trigger,
                                               String locName, ServerPlayer actor) {
        List<ServerPlayer> players = getPlayersInLocation(locName);
        GameStats stats = locationStats.get(locName);
        switch (trigger) {
            case WAVE_COMPLETE:
                // Як AND умова: перевіряємо чи завершена хоча б одна хвиля
                return locationCurrentWave.getOrDefault(locName, 1) > 1;
            case MOBS_REMAINING_LOW: {
                Set<UUID> mobs = spawnedMobsByLocation.get(locName);
                if (mobs == null || mobs.isEmpty()) return false;
                int orig = waveStartMobCounts.getOrDefault(locName, 1);
                return mobs.size() <= orig / 5;
            }
            case TIMER_60:  case TIMER_120: case TIMER_300:
                // Для AND умов: перевіряємо час від старту локації
                {
                    long startMs = locationStartTimers.getOrDefault(locName, 0L);
                    long elapsed = (System.currentTimeMillis() - startMs) / 1000;
                    if (trigger == com.wavedefense.data.WaveTrigger.TIMER_60)  return elapsed >= 60;
                    if (trigger == com.wavedefense.data.WaveTrigger.TIMER_120) return elapsed >= 120;
                    if (trigger == com.wavedefense.data.WaveTrigger.TIMER_300) return elapsed >= 300;
                    return false;
                }
            case PLAYER_JOIN:
                return actor != null || wasRecentlyFired(locName, com.wavedefense.data.WaveTrigger.PLAYER_JOIN);
            case PLAYER_DEATH:
                return actor != null || wasRecentlyFired(locName, com.wavedefense.data.WaveTrigger.PLAYER_DEATH);
            case PLAYER_OPEN_CHEST:
                return actor != null || wasRecentlyFired(locName, com.wavedefense.data.WaveTrigger.PLAYER_OPEN_CHEST);
            case PLAYER_OPEN_DOOR:
                return actor != null || wasRecentlyFired(locName, com.wavedefense.data.WaveTrigger.PLAYER_OPEN_DOOR);
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
                for (ServerPlayer p : players) {
                    if (p.getInventory().getFreeSlot() != -1) return false; // getFreeSlot() returns -1 when full
                }
                return !players.isEmpty();
            }
            case PLAYER_HAS_DIAMOND: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(s ->
                        s.getItem() == net.minecraft.world.item.Items.DIAMOND ||
                        s.getItem() instanceof net.minecraft.world.item.ArmorItem ai &&
                        ai.getMaterial() == net.minecraft.world.item.ArmorMaterials.DIAMOND)) return true;
                }
                return false;
            }
            case PLAYER_HAS_IRON: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(s ->
                        s.getItem() == net.minecraft.world.item.Items.IRON_INGOT ||
                        s.getItem() instanceof net.minecraft.world.item.ArmorItem ai &&
                        ai.getMaterial() == net.minecraft.world.item.ArmorMaterials.IRON)) return true;
                }
                return false;
            }
            case PLAYER_HAS_SWORD: {
                for (ServerPlayer p : players) {
                    if (p.getInventory().hasAnyMatching(s -> s.getItem() instanceof net.minecraft.world.item.SwordItem)) return true;
                }
                return false;
            }
            case PLAYER_HAS_ITEM: {
                // Шукаємо customItemId(s) у хвилях локації з цим тригером (підтримує кілька через кому)
                Location loc2 = WaveDefenseMod.locationManager.getLocation(locName);
                String itemId = "";
                if (loc2 != null) {
                    for (com.wavedefense.data.WaveConfig wc : loc2.getWaves()) {
                        if (wc.isTriggerEnabled() && !wc.getTriggerCustomItemId().isBlank() &&
                            (wc.getTriggerType() == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM ||
                             (wc.getExtraTriggers() != null && wc.getExtraTriggers().contains(com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM)))) {
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
            case WAVES_SURVIVED_5:  { int w = locationCurrentWave.getOrDefault(locName,1); return w > 5; }
            case WAVES_SURVIVED_10: { int w = locationCurrentWave.getOrDefault(locName,1); return w > 10; }
            case WAVES_SURVIVED_20: { int w = locationCurrentWave.getOrDefault(locName,1); return w > 20; }
            case MOBS_KILLED_10:  { return locationMobsKilled.getOrDefault(locName, 0) >= 10; }
            case MOBS_KILLED_50:  { return locationMobsKilled.getOrDefault(locName, 0) >= 50; }
            case MOBS_KILLED_100: { return locationMobsKilled.getOrDefault(locName, 0) >= 100; }
            // Порогові тригери з кастомним значенням
            case MOBS_KILLED_N: {
                // Отримуємо N з першої хвилі що використовує цей тригер
                Location _loc = WaveDefenseMod.locationManager.getLocation(locName);
                int n = 10;
                if (_loc != null) for (com.wavedefense.data.WaveConfig _wc : _loc.getWaves()) {
                    if (_wc.isTriggerEnabled() && _wc.getTriggerType() == com.wavedefense.data.WaveTrigger.MOBS_KILLED_N) { n = _wc.getTriggerCustomValue(); break; }
                }
                return locationMobsKilled.getOrDefault(locName, 0) >= n;
            }
            case WAVES_SURVIVED_N: {
                Location _loc2 = WaveDefenseMod.locationManager.getLocation(locName);
                int n2 = 5;
                if (_loc2 != null) for (com.wavedefense.data.WaveConfig _wc2 : _loc2.getWaves()) {
                    if (_wc2.isTriggerEnabled() && _wc2.getTriggerType() == com.wavedefense.data.WaveTrigger.WAVES_SURVIVED_N) { n2 = _wc2.getTriggerCustomValue(); break; }
                }
                return locationCurrentWave.getOrDefault(locName, 1) > n2;
            }
            case TIMER_CUSTOM: return true; // TIMER_CUSTOM обробляється в tickTimerCustomForLocation, тут true для AND умов
            case ROUND_START: case ROUND_END: case BUY_PHASE:
            case TEAM_WIPE: case KILL_STREAK_3:
                return false; // PvP only, handled separately
            default: return false;
        }
    }

    /**
     * Викликається з EventHandler при діях гравця (відкриття скрині, дверей).
     * Перевіряємо чи гравець у локації та запускаємо тригерні хвилі з цим тригером.
     */
    /** Записуємо нещодавно спрацьований event-driven тригер для AND-умов */
    private void recordEventTrigger(String locName, com.wavedefense.data.WaveTrigger trigger) {
        recentlyFiredTriggers.put(locName + "_" + trigger.name(), System.currentTimeMillis());
    }

    /** Перевіряємо чи event-driven тригер спрацьовував нещодавно (для AND-умов) */
    private boolean wasRecentlyFired(String locName, com.wavedefense.data.WaveTrigger trigger) {
        Long ts = recentlyFiredTriggers.get(locName + "_" + trigger.name());
        return ts != null && (System.currentTimeMillis() - ts) < RECENTLY_FIRED_WINDOW_MS;
    }

    public void fireWaveTriggerForPlayer(net.minecraft.server.level.ServerPlayer player,
                                          com.wavedefense.data.WaveTrigger trigger) {
        PlayerWaveData data = playerData.get(player.getUUID());
        // Записуємо для AND-умов (дозволяє polling-тригер + event AND)
        if (data != null && data.getCurrentLocation() != null) {
            recordEventTrigger(data.getCurrentLocation().getName(), trigger);
        }
        if (data == null || data.getCurrentLocation() == null) return;
        Location loc = data.getCurrentLocation();
        if (loc.isPvp()) return;
        String locName = loc.getName();
        int curWave = locationCurrentWave.getOrDefault(locName, 1);

        for (int wi = 0; wi < loc.getWaves().size(); wi++) {
            com.wavedefense.data.WaveConfig wave = loc.getWaves().get(wi);
            if (!wave.isTriggerEnabled()) continue;
            if (wave.getTriggerType() != trigger) continue;
            // Перевірка разово
            if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
            // Перевірка мінімальної хвилі активації
            if (wave.getActivateFromWave() > 0 && curWave < wave.getActivateFromWave()) continue;
            // Перевірка AND умов (extra triggers)
            boolean andOk = true;
            java.util.List<com.wavedefense.data.WaveTrigger> extras = wave.getExtraTriggers();
            for (com.wavedefense.data.WaveTrigger extra : (extras != null ? extras : java.util.Collections.<com.wavedefense.data.WaveTrigger>emptyList())) {
                boolean extraOk = (extra == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM)
                    ? checkPlayerHasItemMulti(wave.getTriggerCustomItemId(), locName)
                    : checkWaveTriggerCondition(extra, locName, null);
                if (!extraOk) { andOk = false; break; }
            }
            if (!andOk) continue;
            String coolKey = locName + "_w" + wi;
            if (!isTriggerWaveCooldownReady(wave, coolKey, locName)) continue;
            fireTriggerWave(loc, wi);
            recordTriggerWaveFire(wave, coolKey, locName);
            if (wave.isOneTimeOnly()) wave.setFiredThisSession(true);
        }
    }

    /**
     * Перевіряє чи потрібно запустити локацію по event-driven тригеру для гравця поруч.
     */
    public void fireLocationTrigger(net.minecraft.server.level.ServerPlayer player,
                                    com.wavedefense.data.WaveTrigger trigger) {
        if (WaveDefenseMod.getServer() == null) return;
        if (playerData.containsKey(player.getUUID())) return; // вже в локації

        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            if (!loc.isLocationTriggerEnabled()) continue;
            if (loc.isPvp()) continue;
            if (loc.getLocationTriggerType() != trigger) continue;
            if (loc.getPlayerSpawn() == null) continue;

            String name = loc.getName();
            boolean active = playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(name));
            if (active) continue;

            int radius = loc.isAutoActivate() ? loc.getAutoActivateRadius()
                : (loc.isLocationBoundaryEnabled() ? loc.getLocationBoundaryRadius() : 30);
            if (player.blockPosition().distSqr(loc.getPlayerSpawn()) > (double) radius * radius) continue;

            java.util.Set<UUID> set = new java.util.HashSet<>();
            set.add(player.getUUID());
            activateZoneForPlayers(loc, set);
            break;
        }
    }

    /** Перевірка getFreeSlot — повертає індекс або -1 */
    private static final class InventoryUtils {
        private InventoryUtils() {}
    }
}
