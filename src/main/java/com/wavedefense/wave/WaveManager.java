package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SyncPlayerDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
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
    private final Map<UUID, PlayerWaveData> playerData = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> spawnedMobsByLocation = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerBackup> playerBackups = new ConcurrentHashMap<>();
    private final Map<String, Long> locationStartTimers = new ConcurrentHashMap<>();
    // ── Boundary / Leave timer ────────────────────────────────────────
    // playerId → ticks until surrender (countdown)
    private final Map<UUID, Integer> leaveCountdownTicks = new ConcurrentHashMap<>();
    // ── Portal state ──────────────────────────────────────────────────
    // locationName → current portal position (null = no portal)
    private final Map<String, net.minecraft.core.BlockPos> portalPositions = new ConcurrentHashMap<>();
    // locationName → ticks until penalty wave
    private final Map<String, Integer> portalPenaltyTimers = new ConcurrentHashMap<>();
    // locationName → ticks until portal respawn
    private final Map<String, Integer> portalRespawnTimers = new ConcurrentHashMap<>();
    // locationName → позиція входу в портал (для повернення гравців після виходу)
    private final Map<String, net.minecraft.core.BlockPos> portalEntryPositions = new ConcurrentHashMap<>();
    private final java.util.Set<String> portalFirstPlayerEntered = ConcurrentHashMap.newKeySet();
    // locationName → UUID гравців що потрапили через портал
    private final Map<String, java.util.Set<UUID>> portalEnteredPlayers = new ConcurrentHashMap<>();
    // ── Location trigger ──────────────────────────────────────────────
    // locationName → last wave-trigger fire tick (for cooldown)
    private final Map<String, Long> waveTriggerLastFired = new ConcurrentHashMap<>();
    // locationName → waves-completed counter for WAVES cooldown
    private final Map<String, Integer> waveTriggerWaveCounters = new ConcurrentHashMap<>();
    private final Map<String, Integer> locationWaveTimers = new ConcurrentHashMap<>();
    private final Map<String, GameStats> locationStats = new ConcurrentHashMap<>();
    // Відстежуємо поточну хвилю для кожної локації незалежно від гравців
    private final Map<String, Integer> locationCurrentWave = new ConcurrentHashMap<>();

    public void addPlayerToLocation(ServerPlayer player, Location location) {
        UUID playerId = player.getUUID();
        if (playerData.containsKey(playerId)) {
            player.displayClientMessage(Component.literal("§cВи вже берете участь у грі!"), false);
            return;
        }

        // ── Завжди зберігаємо речі та позицію гравця ──────────────────────
        playerBackups.put(playerId, new PlayerBackup(player));

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

        // Телепорт на точку спавну
        BlockPos spawnPos = location.getPlayerSpawn();
        if (spawnPos != null) {
            player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
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
        syncPlayerData(player);
        // Тригери PLAYER_JOIN + LOCATION_START для лут-спавну
        java.util.List<ServerPlayer> allInLoc = getPlayersInLocation(location.getName());
        if (!allInLoc.isEmpty()) {
            ServerLevel lootWorld = allInLoc.get(0).serverLevel();
            fireLootTrigger(location, lootWorld, com.wavedefense.data.LootSpawn.Trigger.PLAYER_JOIN);
            // LOCATION_START fires when wave 1 actually starts (see spawnWave)
        }
    }

    // ── Авто-активація зон (feature #4) ────────────────────────────────
    /** locationName → тіків до активації (відрахування від 600 до 0) */
    private final Map<String, Integer> zoneCountdownTickers = new ConcurrentHashMap<>();
    /** locationName → Set гравців у зоні активації */
    private final Map<String, Set<UUID>> zonePlayersInRange = new ConcurrentHashMap<>();

    public void tick() {
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
                int wave = locationCurrentWave.getOrDefault(entry.getKey(), 1);
                spawnWaveForLocation(entry.getKey(), wave);
            } else {
                long timeLeft = (entry.getValue() - System.currentTimeMillis()) / 1000;
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(entry.getKey())) {
                        data.setTimeUntilNextWave((int) timeLeft);
                        data.setTimerActive(true);
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
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(entry.getKey())) {
                        data.setTimeUntilNextWave(entry.getValue() / 20);
                        data.setTimerActive(true);
                        if (data.getPlayerUUID() != null) {
                            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                            if (player != null) syncPlayerData(player);
                        }
                    }
                }
            }
        }

        checkAllWavesComplete();

        // ── Перевірка кордону локації (вихід за радіус) ──────────────
        tickBoundaryCheck();

        // ── Портали ──────────────────────────────────────────────────
        tickPortals();

        // ── Тригери запуску локацій ───────────────────────────────────
        tickLocationTriggers();

        // ── Тригерні хвилі ────────────────────────────────────────────
        tickWaveTriggers();

        // ── Таймерні лут-тригери ──────────────────────────────────────
        globalLootTimer60++;
        if (globalLootTimer60 >= 60 * 20) {
            globalLootTimer60 = 0;
            for (String locName : getActiveLocationNames()) {
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_60);
            }
        }
        globalLootTimer120++;
        if (globalLootTimer120 >= 120 * 20) {
            globalLootTimer120 = 0;
            for (String locName : getActiveLocationNames()) {
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_120);
            }
        }
        globalLootTimer300++;
        if (globalLootTimer300 >= 300 * 20) {
            globalLootTimer300 = 0;
            for (String locName : getActiveLocationNames()) {
                fireLootTriggerByName(locName, com.wavedefense.data.LootSpawn.Trigger.TIMER_300);
            }
        }
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

        // Перевіряємо чи є ще хвилі
        if (waveNumber > location.getWaves().size()) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
            return;
        }

        List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) {
            locationWaveTimers.remove(locationName);
            locationCurrentWave.remove(locationName);
            return;
        }

        WaveConfig waveConfig = location.getWaves().get(waveNumber - 1);
        ServerLevel world = players.get(0).serverLevel();
        Set<UUID> spawnedMobs = spawnedMobsByLocation.computeIfAbsent(locationName, k -> new HashSet<>());

        broadcastToLocation(locationName, "§c§l⚔ Хвиля " + waveNumber + " розпочалася!");
        // Записуємо кількість мобів для HALF_MOBS_DEAD
        waveStartMobCounts.put(locationName, spawnedMobsByLocation.getOrDefault(locationName, java.util.Collections.emptySet()).size());
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
                BlockPos spawnPos = waveConfig.hasWaveSpawnPos()
                        ? waveConfig.getWaveSpawnPos()
                        : getRandomSpawnPoint(location);
                if (spawnPos == null) continue;

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
        return location.getMobSpawns().get(new Random().nextInt(location.getMobSpawns().size()));
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

        GameStats stats = locationStats.get(locationName);
        if (stats != null) stats.incrementWavesCompleted();

        int completedWave = locationCurrentWave.getOrDefault(locationName, 1);
        int nextWave = completedWave + 1;

        // Нагороди за хвилю
        if (completedWave >= 1 && completedWave <= location.getWaves().size()) {
            WaveConfig waveConfig = location.getWaves().get(completedWave - 1);
            int reward = waveConfig.getPointsReward();
            if (reward > 0) {
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                        location.addPoints(data.getPlayerUUID(), reward);
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
        }

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

        // Перевіряємо: чи завершились усі хвилі
        if (nextWave > location.getWaves().size()) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
        } else {
            broadcastToLocation(locationName, "§a§l✓ Хвилю " + completedWave + " завершено!");
            // Запускаємо таймер до наступної хвилі
            int waveTime = location.getWaves().get(nextWave - 1).getTimeBetweenWaves();
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
            if (location.getPlayerSpawn() == null) continue;

            String locName = location.getName();

            // Пропускаємо якщо локація вже активна
            boolean alreadyActive = playerData.values().stream()
                    .anyMatch(d -> d.getCurrentLocation() != null &&
                                  d.getCurrentLocation().getName().equals(locName));
            if (alreadyActive) {
                // Скасовуємо відлік якщо активна
                zoneCountdownTickers.remove(locName);
                zonePlayersInRange.remove(locName);
                continue;
            }

            // Збираємо гравців в радіусі
            net.minecraft.core.BlockPos spawn = location.getPlayerSpawn();
            int radius = Math.max(5, location.getAutoActivateRadius());
            Set<UUID> inRange = new java.util.HashSet<>();

            for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                if (playerData.containsKey(p.getUUID())) continue; // вже в грі
                double dist = p.blockPosition().distSqr(spawn);
                if (dist <= (double) radius * radius) {
                    inRange.add(p.getUUID());
                }
            }

            zonePlayersInRange.put(locName, inRange);

            if (inRange.isEmpty()) {
                // Якщо вийшли — скасовуємо таймер
                if (zoneCountdownTickers.containsKey(locName)) {
                    zoneCountdownTickers.remove(locName);
                    broadcastToNearby(spawn, location, "§7Активацію скасовано — зона порожня.");
                }
                continue;
            }

            // Є гравці — починаємо або продовжуємо відлік
            if (!zoneCountdownTickers.containsKey(locName)) {
                int countdown = com.wavedefense.config.WaveDefenseConfig.ZONE_ACTIVATION_COUNTDOWN.get();
                zoneCountdownTickers.put(locName, countdown * 20); // в тіках
                // Повідомляємо
                broadcastToNearby(spawn, location,
                    String.format("§e⚠ Активація локації §6%s§e через §a%d сек§e! Вийдіть щоб скасувати.", locName, countdown));
            }

            // Тікаємо
            int ticks = zoneCountdownTickers.getOrDefault(locName, 0) - 1;
            if (ticks <= 0) {
                // Час вийшов — активуємо!
                zoneCountdownTickers.remove(locName);
                zonePlayersInRange.remove(locName);
                activateZoneForPlayers(location, inRange);
            } else {
                zoneCountdownTickers.put(locName, ticks);
                // Частинки раз на секунду
                if (ticks % 20 == 0) {
                    spawnZoneParticles(spawn, radius);
                    int secsLeft = ticks / 20;
                    if (secsLeft <= 5 || secsLeft % 5 == 0) {
                        broadcastToNearby(spawn, location,
                            String.format("§c⏱ Активація через §e%d §cсек...", secsLeft));
                    }
                }
            }
        }
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
    private void spawnZoneParticles(net.minecraft.core.BlockPos center, int radius) {
        net.minecraft.server.level.ServerLevel overworld =
            (net.minecraft.server.level.ServerLevel) WaveDefenseMod.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        int steps = Math.max(16, radius * 8);
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = center.getX() + 0.5 + radius * Math.cos(angle);
            double pz = center.getZ() + 0.5 + radius * Math.sin(angle);
            overworld.sendParticles(
                net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                px, center.getY() + 0.1, pz,
                1, 0, 0.1, 0, 0.02
            );
        }
    }

    /**
     * Активує локацію для гравців які залишились у зоні.
     * autoActivate-локація автоматично зберігає інвентар.
     */
    private void activateZoneForPlayers(Location location, Set<UUID> playerIds) {
        // Форсуємо збереження інвентаря для авто-активованих локацій
        location.setKeepInventory(true);

        int activated = 0;
        for (UUID uid : playerIds) {
            net.minecraft.server.level.ServerPlayer player =
                WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;
            addPlayerToLocation(player, location);
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
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, waveMob.getMainHand().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, dropChance);
        }
        if (!waveMob.getOffHand().isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, waveMob.getOffHand().copy());
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, dropChance);
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

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
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
                    for (ServerPlayer p : getPlayersInLocation(locName)) syncPlayerData(p);
                }
            }
            data.setCurrentLocation(null);
            syncPlayerData(player);
        }
        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    private void endSessionForLocation(String locationName, String message) {
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
        for (UUID playerId : playersToRemove) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                PlayerBackup backup = playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
                PlayerWaveData data = playerData.remove(playerId);
                if (data != null) { data.setCurrentLocation(null); }
                // Якщо гравці потрапили через портал — телепортуємо до місця входу
                if (portalReturnPos != null) {
                    player.teleportTo(portalReturnPos.getX() + 0.5,
                                       portalReturnPos.getY(),
                                       portalReturnPos.getZ() + 0.5);
                }

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

    private void broadcastToLocation(String locationName, String message) {
        for (ServerPlayer player : getPlayersInLocation(locationName)) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    private List<ServerPlayer> getPlayersInLocation(String locationName) {
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
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), new SyncPlayerDataPacket(data));
        }
    }

    public PlayerWaveData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }


    // ════════════════════════════════════════════════════════════════════
    //  PvP — Round-Based System
    // ════════════════════════════════════════════════════════════════════

    /** Кількість мобів на початку хвилі (для HALF_MOBS_DEAD) */
    // Таймери для time-based лут-тригерів (в тіках)
    private int globalLootTimer60  = 0;
    private int globalLootTimer120 = 0;
    private int globalLootTimer300 = 0;

    private final java.util.Map<String, Integer> waveStartMobCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer>              locationMobsKilled = new ConcurrentHashMap<>();
    private final java.util.Set<String> halfMobsTriggered = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Стан раунду кожної PvP локації (за назвою) */
    private final Map<String, com.wavedefense.data.PvpRoundState> pvpStates = new ConcurrentHashMap<>();

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

        // Спектатор поки чекаємо старту
        setSpectator(player, true);
        teleportToSafeSpawn(player, spawnPoint.getPos());

        // Ініціалізуємо або оновлюємо PvpRoundState
        com.wavedefense.data.PvpRoundState state = pvpStates.computeIfAbsent(
            location.getName(),
            k -> new com.wavedefense.data.PvpRoundState(location.getPvpTotalRounds(), location.getPvpBuyTime())
        );
        state.registerPlayer(playerId, player.getName().getString(), spawnPoint.getTeamName());

        player.displayClientMessage(Component.literal(
            "§aВи в команді §e" + spawnPoint.getTeamName() +
            "§a! Чекаємо гравців... (потрібно мін. §e" + location.getPvpMinPlayers() + "§a)"), false);

        checkPvpStart(location);
        broadcastPvpSync(location);
        syncPlayerData(player);
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
                if (state.getTimerTicks() <= 0) startActiveRound(location, state);

            } else if (state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE) {
                String winner = state.checkRoundWinner();
                if (winner != null) endRound(location, state, winner);
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

        for (UUID pid : allInLoc) {
            ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (p == null) continue;
            setSpectator(p, false);
            String team = location.getPlayerTeam(pid);
            if (team != null) {
                for (com.wavedefense.data.PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) { teleportToSafeSpawn(p, sp.getPos()); break; }
                }
            }
        }

        // ROUND_START лут
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.ROUND_START);
        broadcastToLocation(location.getName(),
            String.format("§c⚔ РАУНД §e%d§c/§e%d §cпочався!",
                state.getCurrentRound(), state.getTotalRounds()));
        broadcastPvpSync(location);
    }

    private void endRound(Location location, com.wavedefense.data.PvpRoundState state, String winnerTeam) {
        state.recordTeamWin(winnerTeam);
        // ROUND_END лут
        fireLootTriggerByName(location.getName(), com.wavedefense.data.LootSpawn.Trigger.ROUND_END);
        broadcastToLocation(location.getName(),
            "§6🏆 §e" + winnerTeam + " §6виграли раунд! §7" + formatTeamWins(state));

        if (state.isAllRoundsDone()) {
            endPvpMatch(location, state);
            return;
        }

        // Наступний BUY раунд
        state.startBuyPhase();
        for (ServerPlayer p : getPlayersInLocation(location.getName())) {
            p.setHealth(p.getMaxHealth());
            setSpectator(p, true);
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
        broadcastPvpSync(location);

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> e : playerData.entrySet()) {
            if (e.getValue().getCurrentLocation() != null &&
                e.getValue().getCurrentLocation().getName().equals(location.getName()))
                toRemove.add(e.getKey());
        }
        for (UUID pid : toRemove) {
            ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (p != null) {
                setSpectator(p, false);
                PlayerBackup backup = playerBackups.remove(pid);
                if (backup != null) backup.restore(p);
            }
            playerData.remove(pid);
            location.removePlayerTeam(pid);
        }
        pvpStates.remove(location.getName());
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
        killer.displayClientMessage(
            Component.literal("§a+§e" + kill + " §aочків | вбито §e" + victim.getName().getString()), true);
        location.removePoints(victim.getUUID(), location.getPvpDeathPenalty());

        String roundWinner = state.recordDeath(victim.getUUID(), killer.getUUID());
        setSpectator(victim, true);

        if (roundWinner != null) endRound(location, state, roundWinner);
        else {
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

    public void onPvpPlayerDeath(ServerPlayer player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null || !data.getCurrentLocation().isPvp()) return;
        Location location = data.getCurrentLocation();
        com.wavedefense.data.PvpRoundState state = pvpStates.get(location.getName());

        if (state != null && state.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE) {
            location.removePoints(player.getUUID(), location.getPvpDeathPenalty());
            String roundWinner = state.recordDeath(player.getUUID(), null);
            setSpectator(player, true);
            if (roundWinner != null) endRound(location, state, roundWinner);
            else { updatePvpEnemyCounts(location, state); broadcastPvpSync(location); }
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

    private void teleportToSafeSpawn(ServerPlayer player, net.minecraft.core.BlockPos center) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        net.minecraft.core.BlockPos safe = findSafePos(level, center, 5);
        player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
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
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
        }
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
    private final Map<String, Long> triggerWaveGlobalCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    // Глобальний кулдаун між будь-якими тригерними хвилями на локацію (5 секунд)
    private final Map<String, Long> triggerWaveGlobalCooldown = new ConcurrentHashMap<>();
    /** Таймер виходу: секунд до здачі. 0 = не активний. */
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
            int radius = Math.max(5, loc.isAutoActivate()
                ? loc.getAutoActivateRadius()
                : (loc.isLocationBoundaryEnabled() ? loc.getLocationBoundaryRadius() : 30));

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

            // Запускаємо для всіх гравців поблизу
            java.util.Set<UUID> inRange = new java.util.HashSet<>();
            for (ServerPlayer p : nearbyPlayers) inRange.add(p.getUUID());
            // Якщо гравців поблизу немає але тригер спрацював — шукаємо будь-яких поблизу
            if (inRange.isEmpty()) {
                for (ServerPlayer p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                    if (!playerData.containsKey(p.getUUID())) inRange.add(p.getUUID());
                }
            }
            if (!inRange.isEmpty()) {
                activateZoneForPlayers(loc, inRange);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  TRIGGER WAVE — хвиля що запускається по тригеру
    // ════════════════════════════════════════════════════════════════════

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
                if (!checkWaveTriggerCondition(wave.getTriggerType(), locName, null)) continue;
                // Разово
                if (wave.isOneTimeOnly() && wave.isFiredThisSession()) continue;
                // activateFromWave
                int curWave2 = locationCurrentWave.getOrDefault(locName, 1);
                if (wave.getActivateFromWave() > 0 && curWave2 < wave.getActivateFromWave()) continue;
                // AND умови
                boolean andOk = true;
                for (com.wavedefense.data.WaveTrigger extra : wave.getExtraTriggers()) {
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

    private static final long MIN_TRIGGER_WAVE_COOLDOWN_MS = 5000L; // обов'язкові 5 сек між активаціями

    private boolean isTriggerWaveCooldownReady(com.wavedefense.data.WaveConfig wave, String key, String locName) {
        // Обов'язкова мінімальна пауза 5 секунд між будь-якими тригерними хвилями
        long lastFired = waveTriggerLastFired.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - lastFired < MIN_TRIGGER_WAVE_COOLDOWN_MS) return false;

        switch (wave.getCooldownMode()) {
            case NONE: return true;
            case SECONDS: {
                return (System.currentTimeMillis() - lastFired) >= wave.getCooldownValue() * 1000L;
            }
            case WAVES: {
                int wavesDone = waveTriggerWaveCounters.getOrDefault(key, 0);
                int required = wave.getCooldownValue();
                return wavesDone >= required;
            }
        }
        return true;
    }

    private void recordTriggerWaveFire(com.wavedefense.data.WaveConfig wave, String key, String locName) {
        switch (wave.getCooldownMode()) {
            case SECONDS: waveTriggerLastFired.put(key, System.currentTimeMillis()); break;
            case WAVES:   waveTriggerWaveCounters.put(key, 0); break;
            default: break;
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
                BlockPos sp = getRandomSpawnPoint(location);
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
                for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                    if (playerData.containsKey(p.getUUID())) continue; // вже в грі
                    double dist = p.blockPosition().distSqr(portalPos);
                    if (dist <= 4) { // 2 блоки
                        enterPortal(p, loc, portalPos);
                        break;
                    }
                }
                // Перевіряємо завершення grace period після входу першого гравця
                closePortalIfGraceExpired(name, portalPos);

                if (!active) {
                    // Штрафний таймер
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
    private final Map<String, Long> portalGraceEndTime = new ConcurrentHashMap<>();
    // Кількість гравців що вже увійшли через цей портал у поточній хвилі
    private final Map<String, Integer> portalEnteredCount = new ConcurrentHashMap<>();

    private void enterPortal(ServerPlayer player, Location loc, net.minecraft.core.BlockPos portalPos) {
        String name = loc.getName();
        int enteredCount = portalEnteredCount.getOrDefault(name, 0);

        if (enteredCount == 0) {
            // Перший гравець — зберігаємо позицію входу і відкриваємо grace period 30 сек
            portalEntryPositions.put(name, portalPos);
            portalFirstPlayerEntered.add(name); // close portal for others (grace period passed)
            portalGraceEndTime.put(name, System.currentTimeMillis() + 30000L);
            portalEnteredCount.put(name, 1);

            // Портал НЕ видаляємо одразу — ще 30 сек для інших
            addPlayerToLocation(player, loc);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§5🌀 Ви увійшли в портал §e" + loc.getName() + "§5! §7(30 сек для інших)"),
                false);
            broadcastNearPortal(portalPos, name, "§5🌀 §e" + player.getName().getString() + " §5увійшов у портал! У вас 30 сек щоб приєднатись.");
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

        broadcastNearPortal(portalPos, loc.getName(),
            "§c💥 Штрафна хвиля порталу §e" + loc.getName() + "§c!");

        if (loc.getPortalPenaltyWave() == -1) {
            // Всі хвилі по порядку навколо порталу
            for (com.wavedefense.data.WaveConfig wave : loc.getWaves()) {
                spawnWaveAroundPos(wave, loc, world, portalPos, 8);
            }
        } else {
            int wi = loc.getPortalPenaltyWave();
            if (wi >= 0 && wi < loc.getWaves().size()) {
                spawnWaveAroundPos(loc.getWaves().get(wi), loc, world, portalPos, 8);
            }
        }
    }

    private void spawnWaveAroundPos(com.wavedefense.data.WaveConfig wave, Location loc,
                                     net.minecraft.server.level.ServerLevel world,
                                     net.minecraft.core.BlockPos center, int radius) {
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
    private boolean checkWaveTriggerCondition(com.wavedefense.data.WaveTrigger trigger,
                                               String locName, ServerPlayer actor) {
        List<ServerPlayer> players = getPlayersInLocation(locName);
        GameStats stats = locationStats.get(locName);
        switch (trigger) {
            case WAVE_COMPLETE:     return false; // обробляється через onWaveComplete
            case MOBS_REMAINING_LOW: {
                Set<UUID> mobs = spawnedMobsByLocation.get(locName);
                if (mobs == null || mobs.isEmpty()) return false;
                int orig = waveStartMobCounts.getOrDefault(locName, 1);
                return mobs.size() <= orig / 5;
            }
            case TIMER_60:  case TIMER_120: case TIMER_300:
                return false; // handled by loot timer loop
            case PLAYER_JOIN:   return actor != null;
            case PLAYER_DEATH:  return actor != null;
            case PLAYER_OPEN_CHEST: case PLAYER_OPEN_DOOR:
                return false; // event-driven — fired directly via fireWaveTriggerForPlayer()
            case PLAYER_ENTER_ZONE: {
                Location loc = WaveDefenseMod.locationManager.getLocation(locName);
                if (loc == null || loc.getPlayerSpawn() == null) return false;
                int r = loc.isAutoActivate() ? loc.getAutoActivateRadius() : 30;
                for (var p : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
                    if (playerData.containsKey(p.getUUID())) continue;
                    if (p.blockPosition().distSqr(loc.getPlayerSpawn()) <= (double)r*r) return true;
                }
                return false;
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
                // Шукаємо customItemId у хвилях локації з цим тригером
                Location loc2 = WaveDefenseMod.locationManager.getLocation(locName);
                String itemId = "";
                if (loc2 != null) {
                    for (com.wavedefense.data.WaveConfig wc : loc2.getWaves()) {
                        if (wc.isTriggerEnabled() && wc.getTriggerType() == com.wavedefense.data.WaveTrigger.PLAYER_HAS_ITEM
                                && !wc.getTriggerCustomItemId().isBlank()) {
                            itemId = wc.getTriggerCustomItemId(); break;
                        }
                    }
                }
                if (itemId.isBlank()) return false;
                try {
                    net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(itemId);
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                    if (item == null) return false;
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                    for (ServerPlayer p : players) { if (p.getInventory().contains(stack)) return true; }
                } catch (Exception ignored) {}
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
    public void fireWaveTriggerForPlayer(net.minecraft.server.level.ServerPlayer player,
                                          com.wavedefense.data.WaveTrigger trigger) {
        PlayerWaveData data = playerData.get(player.getUUID());
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
            for (com.wavedefense.data.WaveTrigger extra : wave.getExtraTriggers()) {
                if (!checkWaveTriggerCondition(extra, locName, null)) { andOk = false; break; }
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
