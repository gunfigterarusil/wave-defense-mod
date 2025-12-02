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

        GameStats stats = locationStats.computeIfAbsent(location.getName(), k -> new GameStats());
        stats.getPlayerStats(playerId);

        if (!locationStartTimers.containsKey(location.getName()) && !locationWaveTimers.containsKey(location.getName())) {
            locationStartTimers.put(location.getName(), System.currentTimeMillis() + 30000);
            broadcastToLocation(location.getName(), "§aГра почнеться через 30 секунд!");
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave(30);
        } else if (locationStartTimers.containsKey(location.getName())) {
            long timeLeft = (locationStartTimers.get(location.getName()) - System.currentTimeMillis()) / 1000;
            player.displayClientMessage(Component.literal("§aГра почнеться через " + timeLeft + " секунд!"), false);
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave((int) timeLeft);
        } else {
            int currentWave = playerData.values().stream()
                .filter(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(location.getName()))
                .mapToInt(PlayerWaveData::getCurrentWave).max().orElse(1);
            data.setCurrentWave(currentWave);
            player.displayClientMessage(Component.literal("§aВи приєдналися до гри на хвилі " + currentWave), false);

            Integer timer = locationWaveTimers.get(location.getName());
            if (timer != null) {
                data.setTimerActive(true);
                data.setTimeUntilNextWave(timer / 20);
            } else {
                data.setTimerActive(false);
            }
        }
        playerData.put(playerId, data);
        syncPlayerData(player);
    }

    public void tick() {
        Iterator<Map.Entry<String, Long>> startIterator = locationStartTimers.entrySet().iterator();
        while (startIterator.hasNext()) {
            Map.Entry<String, Long> entry = startIterator.next();
            if (System.currentTimeMillis() >= entry.getValue()) {
                startIterator.remove();
                spawnWaveForLocation(entry.getKey(), 1);
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

        Iterator<Map.Entry<String, Integer>> waveIterator = locationWaveTimers.entrySet().iterator();
        while (waveIterator.hasNext()) {
            Map.Entry<String, Integer> entry = waveIterator.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                waveIterator.remove();
                Optional<PlayerWaveData> anyPlayer = playerData.values().stream()
                        .filter(d -> d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(entry.getKey()))
                        .findFirst();
                if (anyPlayer.isPresent()) {
                    spawnWaveForLocation(entry.getKey(), anyPlayer.get().getCurrentWave());
                }
            } else {
                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(entry.getKey())) {
                        data.setTimeUntilNextWave(entry.getValue() / 20);
                        data.setTimerActive(true);
                        if (data.getPlayerUUID() != null) {
                            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                            if (player != null) {
                                syncPlayerData(player);
                            }
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

        if (waveNumber > location.getWaves().size()) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
            return;
        }

        WaveConfig waveConfig = location.getWaves().get(waveNumber - 1);
        List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) {
            locationWaveTimers.remove(locationName);
            return;
        }

        ServerLevel world = players.get(0).serverLevel();
        Set<UUID> spawnedMobs = spawnedMobsByLocation.computeIfAbsent(locationName, k -> new HashSet<>());

        broadcastToLocation(locationName, "§c§l⚔ Хвиля " + waveNumber + " розпочалася!");

        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                data.setTimerActive(false);
                data.setTimeUntilNextWave(0);
                if (data.getPlayerUUID() != null) {
                    ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                    if (player != null) {
                        syncPlayerData(player);
                    }
                }
            }
        }

        for (WaveMob waveMob : waveConfig.getMobs()) {
            if (new Random().nextInt(100) >= waveMob.getSpawnChance()) continue;

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            int mobCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            for (int i = 0; i < mobCount; i++) {
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
                    }
                } catch (Exception e) {
                    WaveDefenseMod.LOGGER.error("Failed to spawn mob: " + waveMob.getMobType(), e);
                }
            }
        }
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
        if (mobs == null || mobs.isEmpty()) return;

        List<ServerPlayer> players = getPlayersInLocation(locationName);
        if (players.isEmpty()) {
            spawnedMobsByLocation.remove(locationName);
            locationWaveTimers.remove(locationName);
            return;
        }

        ServerLevel world = players.get(0).serverLevel();
        mobs.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());

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

        int nextWave = -1;
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                int currentWave = data.getCurrentWave();
                if (currentWave > 0 && currentWave <= location.getWaves().size()) {
                    WaveConfig waveConfig = location.getWaves().get(currentWave - 1);
                    int reward = waveConfig.getPointsReward();
                    if (reward > 0) {
                        location.addPoints(data.getPlayerUUID(), reward);
                    }
                }
                nextWave = currentWave + 1;
                data.setCurrentWave(nextWave);

                if (data.getPlayerUUID() != null) {
                    ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(data.getPlayerUUID());
                    if (player != null) {
                        syncPlayerData(player);
                    }
                }
            }
        }

        if (nextWave > location.getTotalWaves()) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
        } else {
            broadcastToLocation(locationName, "§a§l✓ Хвилю " + (nextWave - 1) + " завершено!");

            if (nextWave <= location.getWaves().size()) {
                int waveTime = location.getWaves().get(nextWave - 1).getTimeBetweenWaves();
                locationWaveTimers.put(locationName, waveTime * 20);

                for (PlayerWaveData data : playerData.values()) {
                    if (data.getCurrentLocation() != null && data.getCurrentLocation().getName().equals(locationName)) {
                        data.setTimerActive(true);
                        data.setTimeUntilNextWave(waveTime);
                    }
                }
            }
        }
    }

    public void onMobKilled(ServerPlayer player, Mob mob) {
        String locationName = mob.getPersistentData().getString("location");
        if (locationName.isEmpty()) return;

        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null || !data.getCurrentLocation().getName().equals(locationName)) return;

        int points = mob.getPersistentData().getInt("points");
        data.getCurrentLocation().addPoints(player.getUUID(), points);

        GameStats stats = locationStats.get(locationName);
        if (stats != null) {
            stats.incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).addPoints(points);
        }

        Set<UUID> mobs = spawnedMobsByLocation.get(locationName);
        if (mobs != null) {
            mobs.remove(mob.getUUID());
        }

        syncPlayerData(player);
    }

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.remove(playerId);
        if (data != null) {
            PlayerBackup backup = playerBackups.remove(playerId);
            if (backup != null) {
                backup.restore(player);
            }
            if (data.getCurrentLocation() != null) {
                checkWaveComplete(data.getCurrentLocation().getName());
            }
            data.setCurrentLocation(null);
            syncPlayerData(player);
        }
        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    private void endSessionForLocation(String locationName, String message) {
        broadcastToLocation(locationName, message);

        List<UUID> playersToRemove = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> entry : playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null &&
                entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                playersToRemove.add(entry.getKey());
            }
        }

        for (UUID playerId : playersToRemove) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                surrenderPlayer(player);
            }
        }

        spawnedMobsByLocation.remove(locationName);
        locationWaveTimers.remove(locationName);
        locationStartTimers.remove(locationName);
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
                if (player != null) {
                    players.add(player);
                }
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