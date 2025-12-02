package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdatePointsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WaveManager {
    private final Map<UUID, PlayerWaveData> playerData = new ConcurrentHashMap<>();
    private final Map<String, List<UUID>> spawnedMobsByLocation = new ConcurrentHashMap<>();
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
        } else if (locationStartTimers.containsKey(location.getName())) {
            long timeLeft = (locationStartTimers.get(location.getName()) - System.currentTimeMillis()) / 1000;
            player.displayClientMessage(Component.literal("§aГра почнеться через " + timeLeft + " секунд!"), false);
            data.setCurrentWave(1);
        } else {
            int currentWave = playerData.values().stream()
                .filter(d -> d.getCurrentLocation().getName().equals(location.getName()))
                .mapToInt(PlayerWaveData::getCurrentWave).max().orElse(1);
            data.setCurrentWave(currentWave);
            player.displayClientMessage(Component.literal("§aВи приєдналися до гри на хвилі " + currentWave), false);
        }
        playerData.put(playerId, data);
    }

    public void tick() {
        Iterator<Map.Entry<String, Long>> startIterator = locationStartTimers.entrySet().iterator();
        while (startIterator.hasNext()) {
            Map.Entry<String, Long> entry = startIterator.next();
            if (System.currentTimeMillis() >= entry.getValue()) {
                startIterator.remove();
                spawnWaveForLocation(entry.getKey(), 1);
            }
        }

        Iterator<Map.Entry<String, Integer>> waveIterator = locationWaveTimers.entrySet().iterator();
        while (waveIterator.hasNext()) {
            Map.Entry<String, Integer> entry = waveIterator.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                waveIterator.remove();
                int nextWave = playerData.values().stream()
                        .filter(d -> d.getCurrentLocation().getName().equals(entry.getKey()))
                        .findFirst().get().getCurrentWave();
                spawnWaveForLocation(entry.getKey(), nextWave);
            }
        }
    }

    private void spawnWaveForLocation(String locationName, int waveNumber) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null || waveNumber > location.getWaves().size() || location.getWaves().isEmpty()) return;

        List<ServerPlayer> playersInLocation = getPlayersInLocation(locationName);
        if (playersInLocation.isEmpty()) return;

        int playerCount = playersInLocation.size();
        WaveConfig waveConfig = location.getWaves().get(waveNumber - 1);
        ServerLevel level = playersInLocation.get(0).serverLevel();
        List<BlockPos> spawnPoints = location.getMobSpawns();
        Random random = new Random();
        List<UUID> mobIds = new ArrayList<>();

        for (WaveMob waveMob : waveConfig.getMobs()) {
            if (random.nextInt(100) + 1 > waveMob.getSpawnChance()) continue;

            int count = (waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1))) * playerCount;
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = spawnPoints.get(random.nextInt(spawnPoints.size()));
                Entity entity = entityType.create(level);
                if (entity instanceof Mob mob) {
                    mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360, 0);
                    mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));
                    mob.getPersistentData().putString("location", locationName);
                    mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());
                    level.addFreshEntity(mob);
                    mobIds.add(mob.getUUID());
                }
            }
        }
        spawnedMobsByLocation.put(locationName, mobIds);
        broadcastToLocation(locationName, "§6Хвиля " + waveNumber + " розпочалася!");
    }

    public void onMobKilled(ServerPlayer player, Mob mob) {
        String locationName = mob.getPersistentData().getString("location");
        if (locationName.isEmpty()) return;

        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || !data.getCurrentLocation().getName().equals(locationName)) return;

        int points = mob.getPersistentData().getInt("points");
        data.getCurrentLocation().addPoints(player.getUUID(), points);

        GameStats stats = locationStats.get(locationName);
        if (stats != null) {
            stats.incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).incrementMobsKilled();
            stats.getPlayerStats(player.getUUID()).addPoints(points);
        }

        WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), new UpdatePointsPacket(data.getCurrentLocation().getPlayerPoints(player.getUUID()), locationName));
        checkWaveComplete(locationName);
    }

    private void checkWaveComplete(String locationName) {
        List<UUID> mobIds = spawnedMobsByLocation.get(locationName);
        if (mobIds == null) return;

        ServerLevel level = WaveDefenseMod.getServer().overworld();
        mobIds.removeIf(mobId -> {
            Entity entity = level.getEntity(mobId);
            return entity == null || !entity.isAlive();
        });

        if (mobIds.isEmpty()) {
            onWaveComplete(locationName);
        }
    }

    private void onWaveComplete(String locationName) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return;

        GameStats stats = locationStats.get(locationName);
        if(stats != null) stats.incrementWavesCompleted();

        int currentWave = -1;
        for (PlayerWaveData data : playerData.values()) {
            if (data.getCurrentLocation().getName().equals(locationName)) {
                currentWave = data.getCurrentWave();
                if(currentWave > 0 && currentWave <= location.getWaves().size()) {
                    location.addPoints(data.getPlayerUUID(), location.getWaves().get(currentWave - 1).getPointsReward());
                }
                data.setCurrentWave(currentWave + 1);
            }
        }
        currentWave++;

        if (currentWave > location.getTotalWaves()) {
            endSessionForLocation(locationName, "§6§l✓ Всі хвилі завершено! Вітаємо!");
        } else {
            broadcastToLocation(locationName, "§a§l✓ Хвилю " + (currentWave - 1) + " завершено!");
            locationWaveTimers.put(locationName, location.getTimeBetweenWaves() * 20);
        }
    }

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.remove(playerId);
        if (data != null) {
            PlayerBackup backup = playerBackups.remove(playerId);
            if (backup != null) {
                backup.restore(player);
            }
            checkWaveComplete(data.getCurrentLocation().getName());
        }
        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    private void endSessionForLocation(String locationName, String message) {
        broadcastToLocation(locationName, message);
        List<ServerPlayer> players = getPlayersInLocation(locationName);
        for (ServerPlayer player : players) {
            surrenderPlayer(player);
        }
        spawnedMobsByLocation.remove(locationName);
        locationStats.remove(locationName);
    }

    private void broadcastToLocation(String locationName, String message) {
        List<ServerPlayer> players = getPlayersInLocation(locationName);
        for (ServerPlayer p : players) {
            p.displayClientMessage(Component.literal(message), false);
        }
    }

    private List<ServerPlayer> getPlayersInLocation(String locationName) {
        return playerData.entrySet().stream()
                .filter(entry -> entry.getValue().getCurrentLocation().getName().equals(locationName))
                .map(entry -> WaveDefenseMod.getServer().getPlayerList().getPlayer(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public PlayerWaveData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    public void removePlayer(UUID playerId) {
        PlayerWaveData data = playerData.remove(playerId);
        if (data != null) {
            playerBackups.remove(playerId);
            checkWaveComplete(data.getCurrentLocation().getName());
        }
    }
}
