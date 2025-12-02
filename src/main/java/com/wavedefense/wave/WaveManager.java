package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaveManager {
    private final Map<UUID, PlayerWaveData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> spawnedMobs = new ConcurrentHashMap<>();

    public void startWave(ServerPlayer player, Location location) {
        UUID playerId = player.getUUID();

        if (playerData.containsKey(playerId)) {
            WaveDefenseMod.LOGGER.warn("Player {} already in wave", player.getName().getString());
            return;
        }

        PlayerWaveData data = new PlayerWaveData();

        data.setCurrentLocation(location);
        data.setCurrentWave(1);
        data.setTimerActive(true);
        data.setWaveStartTime(System.currentTimeMillis());
        data.setOriginalPos(player.blockPosition());

        // Зберігаємо інвентар
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (!item.isEmpty()) {
                inventory.add(item.copy());
            }
        }
        data.setOriginalInventory(inventory);

        playerData.put(playerId, data);
        location.resetPoints(playerId);

        player.displayClientMessage(Component.literal("§6Хвиля 1 почнеться незабаром!"), false);

        scheduleNextWave(player, location, data);
    }

    private void scheduleNextWave(ServerPlayer player, Location location, PlayerWaveData data) {
        if (data.getCurrentWave() > location.getWaves().size()) {
            endSession(player, location);
            return;
        }

        WaveConfig waveConfig = location.getWaves().get(data.getCurrentWave() - 1);
        int delaySeconds = waveConfig.getTimeBetweenWaves();

        data.setTimeUntilNextWave(delaySeconds);
        data.setTimerActive(true);
    }

    public void spawnWave(ServerPlayer player, Location location, PlayerWaveData data) {
        ServerLevel level = player.serverLevel();
        WaveConfig waveConfig = location.getWaves().get(data.getCurrentWave() - 1);

        List<UUID> mobIds = new ArrayList<>();
        List<BlockPos> spawnPoints = location.getMobSpawns();

        if (spawnPoints.isEmpty()) {
            player.displayClientMessage(Component.literal("§cПомилка: не налаштовані точки спавну!"), false);
            return;
        }

        Random random = new Random();

        // ОНОВЛЕНО: Використовуємо нову систему WaveMob
        for (WaveMob waveMob : waveConfig.getMobs()) {
            // Перевірка шансу появи
            if (random.nextInt(100) + 1 > waveMob.getSpawnChance()) {
                continue;
            }

            // Кількість мобів для цієї хвилі
            int count = waveMob.getCount() + (waveMob.getGrowthPerWave() * (data.getCurrentWave() - 1));
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());

            if (entityType == null) {
                WaveDefenseMod.LOGGER.warn("Unknown mob type: " + waveMob.getMobType());
                continue;
            }

            for (int i = 0; i < count; i++) {
                BlockPos spawnPos = spawnPoints.get(random.nextInt(spawnPoints.size()));
                Entity entity = entityType.create(level);

                if (entity instanceof Mob mob) {
                    mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                            random.nextFloat() * 360, 0);

                    // Додаємо AI для атаки гравця
                    mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));

                    if (level.addFreshEntity(mob)) {
                        mobIds.add(mob.getUUID());

                        // Зберігаємо метадані моба
                        mob.getPersistentData().putString("wavedefense_player", player.getUUID().toString());
                        mob.getPersistentData().putInt("wavedefense_points", waveMob.getPointsPerKill());
                    } else {
                        WaveDefenseMod.LOGGER.warn("Failed to spawn mob: " + waveMob.getMobType());
                    }
                } else {
                    WaveDefenseMod.LOGGER.warn("Entity is not a Mob: " + waveMob.getMobType());
                    if (entity != null) {
                        entity.discard();
                    }
                }
            }
        }

        spawnedMobs.put(player.getUUID(), mobIds);

        player.displayClientMessage(
                Component.literal("§6Хвиля " + data.getCurrentWave() + " розпочалася! Мобів: " + mobIds.size()),
                false
        );

        // Якщо мобів немає, одразу завершуємо хвилю
        if (mobIds.isEmpty()) {
            onWaveComplete(player, data);
        }
    }

    public void onMobKilled(ServerPlayer player, Mob mob) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.get(playerId);

        if (data == null || data.getCurrentLocation() == null) return;

        String storedPlayerId = mob.getPersistentData().getString("wavedefense_player");
        if (storedPlayerId.isEmpty() || !playerId.toString().equals(storedPlayerId)) {
            return;
        }

        int points = mob.getPersistentData().getInt("wavedefense_points");
        data.getCurrentLocation().addPoints(playerId, points);

        // Синхронізація поінтів з клієнтом
        PacketHandler.sendToPlayer(
                new UpdatePointsPacket(data.getCurrentLocation().getPlayerPoints(playerId), data.getCurrentLocation().getName()),
                player
        );

        if (data.isShowNotifications()) {
            player.displayClientMessage(
                    Component.literal("§a+" + points + " поінтів! Всього: " +
                            data.getCurrentLocation().getPlayerPoints(playerId)),
                    true
            );
        }

        // Перевіряємо чи всі моби вбиті
        checkWaveComplete(player, data);
    }

    private void checkWaveComplete(ServerPlayer player, PlayerWaveData data) {
        UUID playerId = player.getUUID();
        List<UUID> mobIds = spawnedMobs.get(playerId);

        if (mobIds == null || mobIds.isEmpty()) return;

        ServerLevel level = player.serverLevel();
        boolean allDead = true;

        Iterator<UUID> iterator = mobIds.iterator();
        while (iterator.hasNext()) {
            UUID mobId = iterator.next();
            Entity entity = level.getEntity(mobId);

            if (entity == null || !entity.isAlive()) {
                iterator.remove();
            } else {
                allDead = false;
            }
        }

        if (allDead) {
            onWaveComplete(player, data);
        }
    }

    private void onWaveComplete(ServerPlayer player, PlayerWaveData data) {
        Location location = data.getCurrentLocation();
        WaveConfig waveConfig = location.getWaves().get(data.getCurrentWave() - 1);

        if (data.isShowNotifications()) {
            player.displayClientMessage(
                    Component.literal("§a§l✓ Хвилю " + data.getCurrentWave() + " завершено!"),
                    false
            );
        }

        // Видаємо нагороди
        for (ItemStack reward : waveConfig.getRewards()) {
            player.getInventory().add(reward.copy());
        }

        spawnedMobs.remove(player.getUUID());

        data.setCurrentWave(data.getCurrentWave() + 1);
        scheduleNextWave(player, location, data);
    }

    public void surrenderPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = playerData.remove(playerId);

        if (data != null) {
            // Телепортація на оригінальну позицію
            if (data.getOriginalPos() != null) {
                BlockPos pos = data.getOriginalPos();
                player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }

            // Відновлення інвентаря
            if (!data.getCurrentLocation().isKeepInventory() && data.getOriginalInventory() != null) {
                player.getInventory().clearContent();
                for (ItemStack item : data.getOriginalInventory()) {
                    player.getInventory().add(item.copy());
                }
            }
        }

        // Видаляємо всіх мобів цієї сесії
        cleanupMobs(player);

        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    private void endSession(ServerPlayer player, Location location) {
        player.displayClientMessage(
                Component.literal("§6§l✓ Всі хвилі завершено! Вітаємо!"),
                false
        );

        surrenderPlayer(player);
    }

    private void cleanupMobs(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<UUID> mobIds = spawnedMobs.remove(playerId);

        if (mobIds != null) {
            ServerLevel level = player.serverLevel();
            for (UUID mobId : mobIds) {
                Entity entity = level.getEntity(mobId);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    public PlayerWaveData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    public void updateTimer(ServerPlayer player) {
        PlayerWaveData data = playerData.get(player.getUUID());
        if (data == null || !data.isTimerActive()) return;

        int timeLeft = data.getTimeUntilNextWave();

        if (timeLeft > 0) {
            data.setTimeUntilNextWave(timeLeft - 1);
        } else if (timeLeft == 0) {
            data.setTimerActive(false);
            spawnWave(player, data.getCurrentLocation(), data);
        }
    }

    public void removePlayer(UUID playerId) {
        playerData.remove(playerId);
        spawnedMobs.remove(playerId);
    }
}