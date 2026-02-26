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
        // Тригер PLAYER_JOIN для лут-спавну
        java.util.List<ServerPlayer> allInLoc = getPlayersInLocation(location.getName());
        if (!allInLoc.isEmpty()) {
            fireLootTrigger(location, allInLoc.get(0).serverLevel(),
                com.wavedefense.data.LootSpawn.Trigger.PLAYER_JOIN);
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

                BlockPos spawnPos = getRandomSpawnPoint(location);
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

        // WAVE_END тригер
        fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.WAVE_END);


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
            int radius = location.getAutoActivateRadius();
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
            // Якщо гравець у спектаторі (PvP смерть) — відновлюємо survival перед backup.restore
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
            PlayerBackup backup = playerBackups.remove(playerId);
            if (backup != null) backup.restore(player);

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
        // Legacy: викликається при старті хвилі
        fireLootTrigger(location, world, com.wavedefense.data.LootSpawn.Trigger.WAVE_START);
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
    /** Локації де вже спрацював тригер HALF_MOBS_DEAD у цій хвилі */
    private final java.util.Set<String> halfMobsTriggered = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final java.util.Map<String, Integer> waveStartMobCounts = new java.util.concurrent.ConcurrentHashMap<>();
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
}
