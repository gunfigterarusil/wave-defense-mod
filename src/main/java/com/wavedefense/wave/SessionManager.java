package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.GameStats;
import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LeaderboardRecord;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.wave.WaveConfigValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Відповідає за вхід/вихід гравців та завершення сесій локацій.
 * Методи: addPlayer, surrender, triggerVictory, endSession.
 * WaveManager делегує сюди всі 4 lifecycle-операції.
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
            player.displayClientMessage(Component.translatable("wavedefense.msg.already_playing"), false);
            return;
        }
        // Перевіряємо КД повторного входу
        Long cooldownEnd = ctx.reEntryCooldowns.get(playerId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long secsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000 + 1;
            player.displayClientMessage(Component.translatable(
                "wavedefense.msg.entry_cooldown", secsLeft, location.getName()), false);
            return;
        }

        // Cancel grace period if this location is waiting for a rejoin
        LocationSession existingSess = ctx.getSession(location.getName());
        // Use > 0 (not >= 0): grace period only active when countdown is running (> 0).
        // At exactly 0 the session is already being torn down — don't cancel it.
        if (existingSess != null && existingSess.graceTicksRemaining > 0) {
            existingSess.graceTicksRemaining = -1;
            // Inform the rejoining player; others will get the broadcast below
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("wavedefense.msg.grace_cancelled"), false);
            wm.broadcastToLocation(location.getName(),
                net.minecraft.network.chat.Component.translatable("wavedefense.msg.grace_cancelled"));
        }

        // Н4: validate wave config before letting the first player in
        WaveConfigValidator validator = new WaveConfigValidator(location);
        if (!validator.validate()) {
            for (String err : validator.getErrors()) {
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Config error for '{}': {}", location.getName(), err);
            }
            if (!location.isPvp()) {
                // PvE with no waves — abort
                player.displayClientMessage(Component.translatable("wavedefense.msg.no_waves_configured"), false);
                return;
            }
        } else {
            for (String w : validator.getWarnings()) {
                WaveDefenseMod.LOGGER.debug("[WaveDefense] Config warning for '{}': {}", location.getName(), w);
            }
        }

        // Визначаємо точку спавну до будь-яких змін стану гравця
        BlockPos spawnPos = location.getAutoActivateEntryPos() != null
            ? location.getAutoActivateEntryPos() : location.getPlayerSpawn();
        if (spawnPos == null) {
            // Локація не налаштована — немає точки спавну
            player.displayClientMessage(Component.translatable("wavedefense.msg.no_spawn_set"), false);
            return;
        }

        // Завжди зберігаємо речі та позицію гравця
        ctx.playerBackups.put(playerId, new PlayerBackup(player));

        // Примусовий gamemode (якщо увімкнено для локації)
        if (location.isEnforceGameMode()) {
            net.minecraft.world.level.GameType requiredMode =
                com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                || player.gameMode.getGameModeForPlayer() != requiredMode) {
                player.setGameMode(requiredMode);
                player.displayClientMessage(Component.translatable(
                    "wavedefense.msg.gamemode_changed_join", requiredMode.getName(), location.getName()), true);
            }
        }

        // Очищаємо інвентар і видаємо стартові предмети
        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (ItemStack item : location.getStartingItems())
                player.getInventory().add(item.copy());
        }
        if (location.getStartingPoints() > 0)
            location.addPoints(playerId, location.getStartingPoints());

        // Телепорт
        wm.teleportToSafeSpawn(player, spawnPos, location.getPlayerSpawnRadius());

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);

        {
            LocationSession _initSess = ctx.getOrCreateSession(location.getName(), location);
            if (_initSess.stats == null) _initSess.stats = new GameStats();
            _initSess.stats.getPlayerStats(playerId);
        }

        int lobbyTime = com.wavedefense.config.WaveDefenseConfig.LOBBY_TIMER_SECONDS.get();

        LocationSession existSess = ctx.getSession(location.getName());
        if (existSess == null || (existSess.startTimerMs == 0 && existSess.waveTimerTicks == 0
                && existSess.currentWave == 1 && existSess.spawnedMobs.isEmpty())) {
            // Перший гравець — починаємо таймер лоббі
            LocationSession sess = ctx.getOrCreateSession(location.getName(), location);
            sess.startTimerMs = System.currentTimeMillis() + lobbyTime * 1000L;
            sess.currentWave = 1;
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.lobby_starting", lobbyTime));
            data.setCurrentWave(1);
            data.setTimerActive(true);
            data.setTimeUntilNextWave(lobbyTime);
        } else if (existSess.startTimerMs > 0) {
            // Новий гравець у лоббі — НЕ перезапускаємо таймер, показуємо залишок часу
            long secsLeft = Math.max(1L, (existSess.startTimerMs - System.currentTimeMillis()) / 1000L + 1L);
            wm.broadcastToLocation(location.getName(), Component.translatable(
                "wavedefense.msg.player_joined_lobby_countdown",
                player.getName().getString(), secsLeft));
            data.setCurrentWave(existSess.currentWave);
            data.setTimerActive(true);
            data.setTimeUntilNextWave((int) secsLeft);
        } else {
            // Гра вже йде — приєднуємось на льоту
            int currentWave = existSess.currentWave;
            data.setCurrentWave(currentWave);
            player.displayClientMessage(
                Component.translatable("wavedefense.msg.joined_wave", currentWave), false);
            if (existSess.waveTimerTicks > 0) {
                data.setTimerActive(true);
                data.setTimeUntilNextWave(existSess.waveTimerTicks / 20);
            }
        }

        ctx.playerData.put(playerId, data);
        wm.invalidatePlayersCache();
        // Спочатку надсилаємо актуальні дані локацій (щоб клієнт мав свіжу Location),
        // потім playerData (щоб HUD відразу відобразився коректно)
        wm.syncLocationDataToPlayer(player);
        wm.syncPlayerData(player);
        wm.syncTeammates(location.getName());
        // Тригери PLAYER_JOIN для лут-спавну; LOCATION_START fires when wave 1 starts (see spawnWave)
        List<ServerPlayer> allInLoc = wm.getPlayersInLocation(location.getName());
        if (!allInLoc.isEmpty()) {
            net.minecraft.server.level.ServerLevel lootWorld = allInLoc.get(0).serverLevel();
            wm.fireLootTrigger(location, lootWorld, com.wavedefense.data.LootSpawn.Trigger.PLAYER_JOIN);
        }

        // Notify monitoring system
        try {
            com.wavedefense.monitor.WaveDefenseMonitor.getInstance().onPlayerJoin(player);
        } catch (Exception e) {
            // Monitoring system error - don't break gameplay
        }
    }

    // ── Surrender ─────────────────────────────────────────────────────

    public void surrender(ServerPlayer player, WaveManager wm) {
        UUID playerId = player.getUUID();
        // Знімаємо ефекти очікування PvP і режим спектатора при виході
        wm.removeWaitEffects(player);
        wm.pvpMgr.getPvpPendingRespawn().remove(playerId);
        PlayerWaveData data = ctx.playerData.remove(playerId);
        wm.invalidatePlayersCache();
        if (data != null) {
            Location currentLoc = data.getCurrentLocation();
            boolean keepLoot = currentLoc != null && currentLoc.isKeepLootOnExit();

            // Якщо гравець у спектаторі (PvP смерть) — відновлюємо survival перед backup.restore
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR)
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

            if (keepLoot) {
                List<ItemStack> savedItems = new ArrayList<>();
                for (int si = 0; si < player.getInventory().getContainerSize(); si++)
                    savedItems.add(player.getInventory().getItem(si).copy());
                PlayerBackup backup = ctx.playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
                for (int si = 0; si < savedItems.size() && si < player.getInventory().getContainerSize(); si++)
                    if (!savedItems.get(si).isEmpty()) player.getInventory().setItem(si, savedItems.get(si));
            } else {
                PlayerBackup backup = ctx.playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
            }

            if (data.getCurrentLocation() != null) {
                String locName = data.getCurrentLocation().getName();
                Location locRef = data.getCurrentLocation();

                locRef.removePlayerTeam(playerId);
                wm.pvpMgr.onPlayerLeave(wm, playerId, locRef, locName);

                boolean anyLeft = ctx.playerData.values().stream()
                    .anyMatch(d -> d.getCurrentLocation() != null
                               && d.getCurrentLocation().getName().equals(locName));
                if (!anyLeft) {
                    LocationSession sess = ctx.getSession(locName);
                    // PvE mid-wave: start grace period instead of immediate shutdown.
                    // Players have 30 s to rejoin; grace is cancelled in addPlayer().
                    if (sess != null && sess.waveActive && !locRef.isPvp()) {
                        sess.graceTicksRemaining = LocationSession.GRACE_TICKS;
                        // No players present yet — first announcement fires from tick() at 10 s mark.
                    } else {
                        // PvP, or PvE not in an active wave (lobby/between waves) → shutdown immediately
                        despawnSessionMobs(locName);
                        ctx.removeSession(locName);
                        wm.pvpMgr.clearLocation(locName);
                    }
                } else {
                    wm.pvpMgr.rebalancePvpTeams(wm, locRef, playerId);
                    for (ServerPlayer p : wm.getPlayersInLocation(locName)) wm.syncPlayerData(p);
                    wm.syncTeammates(locName);
                }
            }
            data.setCurrentLocation(null);
            data.setVictoryCountdownSec(0);  // очищаємо таймер перемоги щоб HUD не застрягав
            wm.syncPlayerData(player);
            wm.clearTeammatesForPlayer(player);
            // Телепортуємо на точку виходу (здача) якщо задана
            if (currentLoc != null && currentLoc.getSurrenderExitPos() != null) {
                BlockPos ep = currentLoc.getSurrenderExitPos();
                player.teleportTo(ep.getX() + 0.5, ep.getY(), ep.getZ() + 0.5);
            }
         }
        player.displayClientMessage(Component.translatable("wavedefense.msg.surrendered"), false);

        // Surrender/logout is a clean leave, not a death. Actual deaths are counted
        // from WaveManager.onPvePlayerDeath and the PvP death handlers.
    }

    // ── Victory ───────────────────────────────────────────────────────

    /**
     * Запускає "екран перемоги" або одразу закінчує сесію залежно від налаштувань локації.
     * Якщо victoryScreenEnabled=true та victoryLingerTimeSec>0 — гравці залишаються,
     * бачать повідомлення, а через вказаний час — endSession.
     */
    public void triggerVictory(String locationName, WaveManager wm) {
        Location loc = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc == null) {
            endSession(locationName, Component.translatable("wavedefense.msg.all_waves_complete"), true, wm);
            return;
        }

        // Нагорода за завершення локації (поінти)
        int pts = loc.getCompletionPointsReward();
        if (pts > 0) {
            for (PlayerWaveData d : ctx.playerData.values()) {
                if (d.getCurrentLocation() != null && d.getCurrentLocation().getName().equals(locationName)) {
                    loc.addPoints(d.getPlayerUUID(), pts);
                    if (d.getPlayerUUID() != null) {
                        if (WaveDefenseMod.getServer() == null) { endSession(locationName, Component.translatable("wavedefense.msg.all_waves_complete"), true, wm); return; }
                        ServerPlayer rp = WaveDefenseMod.getServer().getPlayerList().getPlayer(d.getPlayerUUID());
                        if (rp != null) wm.syncPlayerData(rp);
                    }
                }
            }
        }

        // Fire LOCATION_END loot trigger before the victory screen
        wm.fireLootTriggerByName(locationName, com.wavedefense.data.LootSpawn.Trigger.LOCATION_END);

        // ── Leaderboard: record PvE session results ───────────────────────
        if (WaveDefenseMod.leaderboardManager != null) {
            LocationSession sess = ctx.getSession(locationName);
            int wavesCompleted = sess != null ? sess.currentWave : loc.getWaves().size();
            // gameStartMs is set when the lobby ends and wave 1 begins (fix H-5: was using
            // startTimerMs which stores future lobby-end time, understating duration by lobbyLen)
            int durationSec = (sess != null && sess.gameStartMs > 0)
                ? Math.max(0, (int)((System.currentTimeMillis() - sess.gameStartMs) / 1000)) : 0;
            for (Map.Entry<UUID, PlayerWaveData> entry : ctx.playerData.entrySet()) {
                if (entry.getValue().getCurrentLocation() == null) continue;
                if (!entry.getValue().getCurrentLocation().getName().equals(locationName)) continue;
                UUID pid = entry.getKey();
                int points = loc.getPlayerPoints(pid);
                String pname = "Unknown";
                net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
                if (srv != null) {
                    net.minecraft.server.level.ServerPlayer sp = srv.getPlayerList().getPlayer(pid);
                    if (sp != null) pname = sp.getName().getString();
                }
                WaveDefenseMod.leaderboardManager.addRecord(locationName,
                    LeaderboardManager.MODE_PVE,
                    new LeaderboardRecord(pid, pname, points, wavesCompleted, durationSec));
            }
            WaveDefenseMod.leaderboardManager.saveToFile();
        }

        if (loc.isVictoryScreenEnabled() && loc.getVictoryLingerTimeSec() > 0) {
            // Відображаємо title "ПЕРЕМОГА" всім гравцям на локації
            net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket anim =
                new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(20, 60, 40);
            net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket titlePkt =
                new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.translatable("wavedefense.msg.victory"));
            net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket subPkt =
                new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.translatable("wavedefense.msg.all_waves_done"));
            for (ServerPlayer p : wm.getPlayersInLocation(locationName)) {
                p.connection.send(anim);
                p.connection.send(titlePkt);
                p.connection.send(subPkt);
            }
            // Запускаємо linger timer — endSession відбудеться по таймеру в tick()
            int lingerTicks = loc.getVictoryLingerTimeSec() * 20;
            LocationSession victorySess = ctx.getOrCreateSession(locationName, loc);
            victorySess.victoryLingerTicks = lingerTicks;
            wm.broadcastToLocation(locationName,
                Component.translatable("wavedefense.msg.victory_closing", loc.getVictoryLingerTimeSec()));
         } else {
            endSession(locationName, Component.translatable("wavedefense.msg.all_waves_complete"), true, wm);
        }

        // Notify monitoring system - all waves completed
        try {
            com.wavedefense.monitor.WaveDefenseMonitor.getInstance().onWaveComplete(locationName, loc.getWaves().size());
        } catch (Exception e) {
            // Monitoring system error - don't break gameplay
        }
    }

    // ── End session ───────────────────────────────────────────────────

    public void endSession(String locationName, Component component, boolean isVictory, WaveManager wm) {
        // Скидаємо oneTimeOnly лічильники для всіх тригерних хвиль цієї локації
        Location loc0 = WaveDefenseMod.locationManager.getLocation(locationName);
        if (loc0 != null) {
            for (com.wavedefense.data.WaveConfig wc : loc0.getWaves())
                if (wc.isOneTimeOnly()) wc.setFiredThisSession(false);
        }

        // Зберігаємо лічильник вбитих мобів у постійну статистику локації
        Location locStats = WaveDefenseMod.locationManager.getLocation(locationName);
        {
            LocationSession endSess = ctx.getSession(locationName);
            int sessionKills = endSess != null ? endSess.mobsKilled : 0;
            if (locStats != null) {
                locStats.addTotalMobsKilled(sessionKills);
                WaveDefenseMod.locationManager.saveToFile();
                wm.debugLog("Location '" + locationName + "': session kills=" + sessionKills
                    + ", total=" + locStats.getTotalMobsKilledAllTime());
            }
        }
        wm.broadcastToLocation(locationName, component);

        // Якщо гравці потрапили через портал — запам'ятовуємо позицію для телепорту назад
        LocationSession endSess2 = ctx.getSession(locationName);
        BlockPos portalReturnPos = endSess2 != null ? endSess2.portalEntryPosition : null;

        // Знімаємо знімок поінтів ДО відновлення гравців
        Location locReward = WaveDefenseMod.locationManager.getLocation(locationName);
        Map<UUID, Integer> pointsSnapshot = new HashMap<>();
        List<UUID> playersToRemove = new ArrayList<>();
        for (Map.Entry<UUID, PlayerWaveData> entry : ctx.playerData.entrySet()) {
            if (entry.getValue().getCurrentLocation() != null &&
                    entry.getValue().getCurrentLocation().getName().equals(locationName)) {
                UUID pid = entry.getKey();
                playersToRemove.add(pid);
                if (locReward != null)
                    pointsSnapshot.put(pid, locReward.getPlayerPoints(pid));
            }
        }

        Location locCd = WaveDefenseMod.locationManager.getLocation(locationName);
        int cdSec = locCd != null ? locCd.getReEntryCooldownSec() : 0;

        // Збираємо онлайн-гравців для очищення тімейт-панелі
        List<ServerPlayer> onlinePlayers = new ArrayList<>();
        for (UUID pid : playersToRemove) {
            ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (sp != null) onlinePlayers.add(sp);
        }
        wm.clearTeammatesForAll(onlinePlayers);

        for (UUID playerId : playersToRemove) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(playerId);
            // Встановлюємо КД незалежно від того чи гравець онлайн
            if (cdSec > 0) ctx.reEntryCooldowns.put(playerId, System.currentTimeMillis() + cdSec * 1000L);
            if (player != null) {
                PlayerBackup backup = ctx.playerBackups.remove(playerId);
                if (backup != null) backup.restore(player);
                PlayerWaveData data = ctx.playerData.remove(playerId);
                if (data != null) { data.setCurrentLocation(null); }
                // Точка виходу: окремо для перемоги і здачі, без cross-fallback
                Location locExit = WaveDefenseMod.locationManager.getLocation(locationName);
                // isVictory передається явно — не залежимо від вмісту рядка
                BlockPos exitPos = null;
                if (locExit != null)
                    // ВИКЛЮЧНО відповідна точка — перемога → victoryExitPos, здача → surrenderExitPos
                    exitPos = isVictory ? locExit.getVictoryExitPos() : locExit.getSurrenderExitPos();
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
                        if (playerPts >= reward.getBuyPrice()) {
                            for (ItemStack item : reward.getItems()) {
                                if (!item.isEmpty()) player.getInventory().add(item.copy());
                            }
                        }
                    }
                }
                wm.syncPlayerData(player);
            } else {
                // Гравець офлайн — просто прибираємо дані
                ctx.playerBackups.remove(playerId);
                ctx.playerData.remove(playerId);
            }
        }

        wm.removeInfoPanelEntities(locationName);
        // Видаляємо spawned-мобів зі світу перед закриттям сесії
        despawnSessionMobs(locationName);
        // removeSession disposes all per-location state (spawnedMobs, timers, portals, etc.)
        ctx.removeSession(locationName);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Видаляє всіх живих spawned-мобів сесії зі світу.
     * Викликається при завершенні сесії (перемога, здача, вихід останнього гравця),
     * щоб моби не блукали сервером після закінчення гри.
     */
    private void despawnSessionMobs(String locationName) {
        LocationSession sess = ctx.getSession(locationName);
        if (sess == null || sess.spawnedMobs.isEmpty()) return;
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv == null) return;
        int despawned = 0;
        for (UUID uuid : sess.spawnedMobs) {
            // Search all loaded levels — location may be in Nether or End
            for (net.minecraft.server.level.ServerLevel world : srv.getAllLevels()) {
                net.minecraft.world.entity.Entity e = world.getEntity(uuid);
                if (e != null) {
                    e.discard();
                    despawned++;
                    break;
                }
            }
        }
        WaveDefenseMod.LOGGER.info("[WaveDefense] Despawned {} mobs for ended session '{}'",
            despawned, locationName);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану SessionManager (мінімальна — переважно через WaveContext). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // SessionManager не має власних полів, які потребують збереження
        // Усі дані вже збережені через WaveContext
        return tag;
    }

    /** Відновлення стану SessionManager. */
    public void load(CompoundTag tag) {
        // Немає стану для відновлення
    }
}
