package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.GameStats;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Відповідає за вхід/вихід гравців та завершення сесій локацій.
 * Методи: addPlayerToLocation, surrenderPlayer, triggerVictory,
 *         endSessionForLocation, removeInfoPanelEntities.
 */
public class SessionManager {

    private final WaveContext ctx;

    public SessionManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ── Player join ───────────────────────────────────────────────────

    public void addPlayer(ServerPlayer player, Location location, WaveManager wm) {
        UUID playerId = player.getUUID();

        if (ctx.playerData.containsKey(playerId)) {
            player.displayClientMessage(Component.literal("§cВи вже берете участь у грі!"), false);
            return;
        }
        Long cooldownEnd = ctx.reEntryCooldowns.get(playerId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long secsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000 + 1;
            player.displayClientMessage(Component.literal(
                "§c⏳ Зачекайте ще §e" + secsLeft + "§c сек перед повторним входом у локацію §e" + location.getName()), false);
            return;
        }

        ctx.playerBackups.put(playerId, new PlayerBackup(player));

        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (ItemStack item : location.getStartingItems())
                player.getInventory().add(item.copy());
        }
        if (location.getStartingPoints() > 0)
            location.addPoints(playerId, location.getStartingPoints());

        net.minecraft.core.BlockPos spawnPos = location.getAutoActivateEntryPos() != null
                ? location.getAutoActivateEntryPos() : location.getPlayerSpawn();
        if (spawnPos != null)
            player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);
        ctx.locationStats.computeIfAbsent(location.getName(), k -> new GameStats()).getPlayerStats(playerId);

        int lobbyTime = com.wavedefense.config.WaveDefenseConfig.LOBBY_TIMER_SECONDS.get();

        if (!ctx.locationStartTimers.containsKey(location.getName())
                && !ctx.locationWaveTimers.containsKey(location.getName())
                && !ctx.locationCurrentWave.containsKey(location.getName())) {
            ctx.locationStartTimers.put(location.getName(), System.currentTimeMillis() + lobbyTime * 1000L);
            ctx.locationCurrentWave.put(location.getName(), 1);
            ctx.broadcastToLocation(location.getName(),
                String.format("§a🕐 Гра починається через §e%d §aсек!", lobbyTime));
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave(lobbyTime);
        } else if (ctx.locationStartTimers.containsKey(location.getName())) {
            long newEnd = System.currentTimeMillis() + lobbyTime * 1000L;
            ctx.locationStartTimers.put(location.getName(), newEnd);
            ctx.broadcastToLocation(location.getName(),
                String.format("§e👤 §6%s §eприєднався! Таймер скинуто: §a%d сек",
                    player.getName().getString(), lobbyTime));
            data.setCurrentWave(ctx.locationCurrentWave.getOrDefault(location.getName(), 1));
            data.setTimerActive(true);
            data.setTimeUntilNextWave(lobbyTime);
        } else {
            int currentWave = ctx.locationCurrentWave.getOrDefault(location.getName(), 1);
            data.setCurrentWave(currentWave);
            player.displayClientMessage(Component.literal("§aВи приєдналися до гри на хвилі " + currentWave), false);
            Integer timer = ctx.locationWaveTimers.get(location.getName());
            if (timer != null) { data.setTimerActive(true); data.setTimeUntilNextWave(timer / 20); }
        }

        ctx.playerData.put(playerId, data);
        wm.syncPlayerData(player);

        List<ServerPlayer> allInLoc = ctx.getPlayersInLocation(location.getName());
        if (!allInLoc.isEmpty()) {
            wm.fireLootTrigger(location, allInLoc.get(0).serverLevel(),
                com.wavedefense.data.LootSpawn.Trigger.PLAYER_JOIN);
        }
    }

    // ── Surrender ─────────────────────────────────────────────────────

    public void surrender(ServerPlayer player, WaveManager wm) {
        UUID playerId = player.getUUID();
        PlayerWaveData data = ctx.playerData.remove(playerId);
        if (data == null) return;

        Location loc = data.getCurrentLocation();
        boolean keepLoot = loc != null && loc.isKeepLootOnExit();

        if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR)
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        PlayerBackup backup = ctx.playerBackups.remove(playerId);
        if (keepLoot) {
            java.util.List<ItemStack> saved = new java.util.ArrayList<>();
            for (int si = 0; si < player.getInventory().getContainerSize(); si++)
                saved.add(player.getInventory().getItem(si).copy());
            if (backup != null) backup.restore(player);
            for (int si = 0; si < saved.size() && si < player.getInventory().getContainerSize(); si++)
                if (!saved.get(si).isEmpty()) player.getInventory().setItem(si, saved.get(si));
        } else {
            if (backup != null) backup.restore(player);
        }

        if (loc != null) {
            String locName = loc.getName();
            loc.removePlayerTeam(playerId);
            com.wavedefense.data.PvpRoundState state = ctx.pvpStates.get(locName);
            if (state != null) state.removePlayer(playerId);

            // Teleport to surrender exit
            net.minecraft.core.BlockPos exitPos = loc.getSurrenderExitPos();
            if (exitPos != null)
                player.teleportTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5);

            // Apply re-entry cooldown
            int cooldownSec = loc.getReEntryCooldownSec();
            if (cooldownSec > 0)
                ctx.reEntryCooldowns.put(playerId, System.currentTimeMillis() + cooldownSec * 1000L);

            boolean anyLeft = ctx.playerData.values().stream()
                .anyMatch(d -> d.getCurrentLocation() != null
                            && d.getCurrentLocation().getName().equals(locName));
            if (!anyLeft) ctx.clearLocationState(locName);
            else ctx.getPlayersInLocation(locName).forEach(wm::syncPlayerData);
        }

        data.setCurrentLocation(null);
        data.setVictoryCountdownSec(0);
        wm.syncPlayerData(player);
        player.displayClientMessage(Component.literal("§cВи здалися!"), false);
    }

    // ── Victory ───────────────────────────────────────────────────────

    public void triggerVictory(String locationName, WaveManager wm) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc == null) return;

        // Apply re-entry cooldown for all participants
        for (PlayerWaveData d : ctx.playerData.values()) {
            if (d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locationName)) {
                int cd = loc.getReEntryCooldownSec();
                if (cd > 0 && d.getPlayerUUID() != null)
                    ctx.reEntryCooldowns.put(d.getPlayerUUID(), System.currentTimeMillis() + cd * 1000L);
            }
        }

        if (loc.isVictoryScreenEnabled() && loc.getVictoryLingerTimeSec() > 0) {
            int lingerTicks = loc.getVictoryLingerTimeSec() * 20;
            ctx.victoryLingerTimers.put(locationName, lingerTicks);
            ctx.broadcastToLocation(locationName,
                "§a§l⭐ ПЕРЕМОГА! §r§7Виходимо через §e" + loc.getVictoryLingerTimeSec() + " §7сек...");
        } else {
            endSession(locationName, "§6§l✓ Локацію пройдено! Вітаємо!", wm);
        }
    }

    // ── End session ───────────────────────────────────────────────────

    public void endSession(String locationName, String message, WaveManager wm) {
        List<ServerPlayer> players = ctx.getPlayersInLocation(locationName);
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);

        for (ServerPlayer player : players) {
            UUID uid = player.getUUID();
            PlayerWaveData data = ctx.playerData.remove(uid);
            boolean keepLoot = loc != null && loc.isKeepLootOnExit();

            PlayerBackup backup = ctx.playerBackups.remove(uid);
            if (keepLoot) {
                java.util.List<ItemStack> saved = new java.util.ArrayList<>();
                for (int si = 0; si < player.getInventory().getContainerSize(); si++)
                    saved.add(player.getInventory().getItem(si).copy());
                if (backup != null) backup.restore(player);
                for (int si = 0; si < saved.size() && si < player.getInventory().getContainerSize(); si++)
                    if (!saved.get(si).isEmpty()) player.getInventory().setItem(si, saved.get(si));
            } else {
                if (backup != null) backup.restore(player);
            }

            // Teleport to victory exit
            if (loc != null && loc.getVictoryExitPos() != null) {
                net.minecraft.core.BlockPos vep = loc.getVictoryExitPos();
                player.teleportTo(vep.getX() + 0.5, vep.getY(), vep.getZ() + 0.5);
            }

            if (data != null) { data.setCurrentLocation(null); data.setVictoryCountdownSec(0); }
            wm.syncPlayerData(player);
            player.displayClientMessage(Component.literal(message), false);
        }

        ctx.clearLocationState(locationName);
        wm.removeInfoPanelEntities(locationName);
    }
}
