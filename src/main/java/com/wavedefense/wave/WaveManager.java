package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SyncPlayerDataPacket;
import net.minecraft.core.BlockPos;
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

        playerBackups.put(playerId, new PlayerBackup(player));

        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (ItemStack item : location.getStartingItems()) {
                player.getInventory().add(item.copy());
            }
        }

        BlockPos spawnPos = location.getPlayerSpawn();
        player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);

        locationStats.computeIfAbsent(location.getName(), k -> new GameStats()).getPlayerStats(playerId);

        if (!locationStartTimers.containsKey(location.getName()) && !locationWaveTimers.containsKey(location.getName())
                && !locationCurrentWave.containsKey(location.getName())) {
            locationStartTimers.put(location.getName(), System.currentTimeMillis() + 30000);
            locationCurrentWave.put(location.getName(), 1);
            broadcastToLocation(location.getName(), "§aГра почнеться через 30 секунд!");
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave(30);
        } else if (locationStartTimers.containsKey(location.getName())) {
            long timeLeft = (locationStartTimers.get(location.getName()) - System.currentTimeMillis()) / 1000;
            player.displayClientMessage(Component.literal("§aГра почнеться через " + timeLeft + " секунд!"), false);
            data.setCurrentWave(locationCurrentWave.getOrDefault(location.getName(), 1));
            data.setTimerActive(true);
            data.setTimeUntilNextWave((int) timeLeft);
        } else {
            int currentWave = locationCurrentWave.getOrDefault(location.getName(), 1);
            data.setCurrentWave(currentWave);
            player.displayClientMessage(Component.literal("§aВи приєдналися до гри на хвилі " + currentWave), false);
            Integer timer = locationWaveTimers.get(location.getName());
            if (timer != null) {
                data.setTimerActive(true);
                data.setTimeUntilNextWave(timer / 20);
            }
        }

        playerData.put(playerId, data);
        syncPlayerData(player);
    }

    public void tick() {
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

        if (mobs.isEmpty()) {
            spawnedMobsByLocation.remove(locationName);
            onWaveComplete(locationName);
        }
    }

    private void onWaveComplete(String locationName) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return;

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

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.remove(playerId);
        if (data != null) {
            PlayerBackup backup = playerBackups.remove(playerId);
            if (backup != null) backup.restore(player);
            if (data.getCurrentLocation() != null) {
                String locName = data.getCurrentLocation().getName();
                // Якщо більше немає гравців — очищаємо
                boolean anyLeft = playerData.values().stream()
                        .anyMatch(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locName));
                if (!anyLeft) {
                    spawnedMobsByLocation.remove(locName);
                    locationWaveTimers.remove(locName);
                    locationStartTimers.remove(locName);
                    locationCurrentWave.remove(locName);
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
     * Для кожного LootSpawn кидається шанс, і якщо вдалось — скидає предмети на землю.
     */
    private void spawnLootForLocation(Location location, ServerLevel world, int waveNumber) {
        if (location.getLootSpawns().isEmpty()) return;
        Random rng = new Random();
        for (com.wavedefense.data.LootSpawn lootSpawn : location.getLootSpawns()) {
            // Перевіряємо шанс для цієї точки
            if (rng.nextInt(100) >= lootSpawn.getSpawnChance()) continue;
            net.minecraft.core.BlockPos pos = lootSpawn.getPos();
            for (net.minecraft.world.item.ItemStack item : lootSpawn.getItems()) {
                if (item.isEmpty()) continue;
                for (int n = 0; n < lootSpawn.getCount(); n++) {
                    net.minecraft.world.entity.item.ItemEntity itemEntity =
                        new net.minecraft.world.entity.item.ItemEntity(
                            world,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            item.copy()
                        );
                    itemEntity.setPickUpDelay(20); // 1 секунда перед підбором
                    world.addFreshEntity(itemEntity);
                }
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
}
