package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.packets.SyncPvpStatePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Відповідає за PvP-раунди:
 * – приєднання гравців до PvP-локації,
 * – авто-баланс команд,
 * – state machine (WAITING → BUY → COUNTDOWN → ACTIVE → ROUND_END_DELAY → ENDED),
 * – обробку вбивств / смертей / виходу гравця,
 * – синхронізацію стану PvP до клієнтів.
 *
 * Стан раундів ({@code pvpState}) знаходиться у {@link LocationSession}.
 * Власні поля: {@code pvpPendingRespawn} і {@code pvpKillStreaks}.
 */
public class PvpRoundManager {

    private final WaveContext ctx;

    /** UUID гравців, що загинули у ACTIVE раунді і очікують на респавн → spectator */
    private final Set<UUID> pvpPendingRespawn =
        Collections.synchronizedSet(new HashSet<>());

    /** Kill streak counter: UUID → consecutive kills without dying */
    private final Map<UUID, Integer> pvpKillStreaks = new ConcurrentHashMap<>();

    /**
     * Tracks victims whose death-penalty was already deducted by {@link #onPlayerKilledPlayer}.
     * Prevents double-deduction when {@link #onPvpPlayerDeath} fires for the same death event.
     */
    private final Set<UUID> pvpPenaltyDeducted = ConcurrentHashMap.newKeySet();

    public PvpRoundManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API — getters
    // ════════════════════════════════════════════════════════════════════

    public Set<UUID> getPvpPendingRespawn() { return pvpPendingRespawn; }

    public PvpRoundState getPvpState(String locationName) {
        LocationSession s = ctx.getSession(locationName);
        return s != null ? s.pvpState : null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API — player join / auto-balance
    // ════════════════════════════════════════════════════════════════════

    /**
     * Додає гравця до PvP локації, призначає команду і ставить у WAITING фазу.
     * Викликається з WaveManager.addPlayerToPvpLocation (public delegation).
     */
    public void addPlayerToPvpLocation(WaveManager wm, ServerPlayer player,
                                       Location location, int spawnIndex) {
        UUID playerId = player.getUUID();
        if (ctx.playerData.containsKey(playerId)) {
            player.displayClientMessage(
                Component.translatable("wavedefense.msg.already_playing"), false);
            return;
        }

        ctx.playerBackups.put(playerId, new PlayerBackup(player));

        // ── Примусовий gamemode (якщо увімкнено для локації) ─────────────
        if (location.isEnforceGameMode()) {
            net.minecraft.world.level.GameType requiredMode =
                com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
            if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                || player.gameMode.getGameModeForPlayer() != requiredMode) {
                player.setGameMode(requiredMode);
                player.displayClientMessage(Component.translatable(
                    "wavedefense.msg.gamemode_changed_join_pvp", requiredMode.getName(), location.getName()), true);
            }
        }

        if (!location.isKeepInventory()) {
            player.getInventory().clearContent();
            for (net.minecraft.world.item.ItemStack item : location.getStartingItems())
                player.getInventory().add(item.copy());
        }

        // Стартові поінти для покупок у магазині
        if (location.getStartingPoints() > 0) {
            location.addPoints(playerId, location.getStartingPoints());
        }

        PvpSpawnPoint spawnPoint = location.getPvpSpawnPoints().get(spawnIndex);
        location.setPlayerTeam(playerId, spawnPoint.getTeamName());
        assignScoreboardTeam(player, location.getName(), spawnPoint.getTeamName());

        PlayerWaveData data = new PlayerWaveData();
        data.setPlayerUUID(playerId);
        data.setCurrentLocation(location);
        data.setInPvp(true);
        data.setCurrentWave(0);
        data.setTimerActive(false);
        ctx.playerData.put(playerId, data);

        // Режим очікування: ефекти slowness+blindness або spectator
        if (location.isPvpWaitEffect()) {
            wm.applyWaitEffects(player);
        } else {
            wm.setSpectator(player, true);
        }
        wm.teleportToSpawnPoint(player, spawnPoint);

        // Ініціалізуємо або оновлюємо PvpRoundState
        LocationSession _s = ctx.getOrCreateSession(location.getName(), location);
        if (_s.pvpState == null) {
            _s.pvpState = new PvpRoundState(
                location.getPvpTotalRounds(), location.getPvpBuyTime());
            _s.pvpState.setDmKillsToWin(location.getDmKillsToWin());
        }
        PvpRoundState state = _s.pvpState;
        state.registerPlayer(playerId, player.getName().getString(), spawnPoint.getTeamName());

        // BR: ініціалізуємо кордон
        if (location.isBattleRoyale()) wm.brManager.initLocation(location);

        player.displayClientMessage(Component.translatable(
            "wavedefense.msg.team_assigned", spawnPoint.getTeamName()),
            false);

        checkPvpStart(wm, location);
        broadcastPvpSync(wm, location);
        wm.syncPlayerData(player);
    }

    /**
     * Повертає індекс точки спавну з найменшою кількістю гравців (автобаланс).
     */
    public int getAutoBalancedSpawnIndex(Location location, UUID joiningPlayer) {
        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        if (spawns.isEmpty()) return 0;

        Map<String, Integer> teamCounts = new java.util.LinkedHashMap<>();
        for (PvpSpawnPoint sp : spawns) teamCounts.put(sp.getTeamName(), 0);

        for (PlayerWaveData d : ctx.playerData.values()) {
            if (d.getCurrentLocation() == null
                || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            if (d.getPlayerUUID() == null || d.getPlayerUUID().equals(joiningPlayer)) continue;
            String team = location.getPlayerTeam(d.getPlayerUUID());
            if (team != null) teamCounts.merge(team, 1, Integer::sum);
        }

        String minTeam = teamCounts.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(null);
        for (int i = 0; i < spawns.size(); i++) {
            if (spawns.get(i).getTeamName().equals(minTeam)) return i;
        }
        return 0;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API — main tick
    // ════════════════════════════════════════════════════════════════════

    /** PvP tick — викликається з WaveManager.tick() щотік. */
    public void tick(WaveManager wm) {
        for (Map.Entry<String, LocationSession> entry : ctx.sessions.entrySet()) {
            String locName = entry.getKey();
            LocationSession session = entry.getValue();
            PvpRoundState state = session.pvpState;
            if (state == null) continue;
            Location location = WaveDefenseMod.locationManager.getLocation(locName);
            if (location == null) continue;

            if (state.getPhase() == PvpRoundState.Phase.BUY) {
                state.tickDown();
                if (state.getTimerTicks() % 20 == 0) broadcastPvpSync(wm, location);
                if (state.getTimerTicks() <= 0) {
                    int delay = location.getPvpRoundStartDelay();
                    if (delay > 0) {
                        state.startCountdown(delay);
                        wm.broadcastToLocation(location.getName(),
                            Component.translatable("wavedefense.msg.pvp_round_starting", delay));
                        broadcastPvpSync(wm, location);
                    } else {
                        startActiveRound(wm, location, state);
                    }
                }

            } else if (state.getPhase() == PvpRoundState.Phase.COUNTDOWN) {
                state.tickDown();
                if (state.getTimerTicks() % 20 == 0 && state.getTimerTicks() > 0) {
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_round_countdown",
                            state.getCurrentRound(), state.getTimerSeconds()));
                    broadcastPvpSync(wm, location);
                }
                if (state.getTimerTicks() <= 0) {
                    startActiveRound(wm, location, state);
                }

            } else if (state.getPhase() == PvpRoundState.Phase.ACTIVE) {
                String winner = state.checkRoundWinner();
                if (winner != null) {
                    state.setPendingWinner(winner);
                    state.startRoundEndDelay(5);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_team_wins_round", winner));
                    broadcastPvpSync(wm, location);
                } else if (location.isBattleRoyale() && state.getAliveThisRound().isEmpty()) {
                    // H3 fix: both last BR players died in the same tick — declare draw.
                    // pendingWinner = null signals endRound() to record no team win.
                    state.setPendingWinner(null);
                    state.startRoundEndDelay(5);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_br_draw"));
                    broadcastPvpSync(wm, location);
                }

            } else if (state.getPhase() == PvpRoundState.Phase.ROUND_END_DELAY) {
                state.tickDown();
                if (state.getTimerTicks() <= 0) {
                    // C1 / H3 fix: always call endRound regardless of pendingWinner being
                    // null (null = draw — endRound is now null-safe for winnerTeam).
                    String pendingWinner = state.getPendingWinner();
                    state.clearPendingWinner();
                    endRound(wm, location, state, pendingWinner);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API — event handlers (called from EventHandler / WaveManager)
    // ════════════════════════════════════════════════════════════════════

    public void onPlayerKilledPlayer(WaveManager wm, ServerPlayer killer, ServerPlayer victim) {
        PlayerWaveData victimData = ctx.playerData.get(victim.getUUID());
        if (victimData == null || victimData.getCurrentLocation() == null) return;
        Location location = victimData.getCurrentLocation();
        if (!location.isPvp()) return;

        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state == null || state.getPhase() != PvpRoundState.Phase.ACTIVE) return;

        String killerTeam = location.getPlayerTeam(killer.getUUID());
        String victimTeam = location.getPlayerTeam(victim.getUUID());
        if (killerTeam != null && killerTeam.equals(victimTeam)) return;

        int kill = location.getPvpKillPoints();
        location.addPoints(killer.getUUID(), kill);
        // Mark penalty as handled here so onPvpPlayerDeath doesn't deduct it again.
        pvpPenaltyDeducted.add(victim.getUUID());
        location.removePoints(victim.getUUID(), location.getPvpDeathPenalty());

        // Kill streak
        int streak = pvpKillStreaks.merge(killer.getUUID(), 1, Integer::sum);
        pvpKillStreaks.remove(victim.getUUID());
        String streakSuffix = streak >= 3
            ? " " + Component.translatable("wavedefense.msg.pvp_kill_streak", streak).getString()
            : "";
        killer.displayClientMessage(
            Component.translatable("wavedefense.msg.pvp_kill",
                kill, victim.getName().getString(), streakSuffix), true);

        if (streak % 3 == 0) {
            wm.fireLootTriggerByName(location.getName(),
                LootSpawn.Trigger.KILL_STREAK_3);
        }

        String roundWinner = null;

        if (location.isDeathmatch()) {
            state.recordHit(killer.getUUID(), victim.getUUID());
            PvpPlayerStats ks = state.getStats(killer.getUUID());
            if (ks != null) ks.addKill();
            PvpPlayerStats vs = state.getStats(victim.getUUID());
            if (vs != null) vs.addDeath();
            pvpPendingRespawn.add(victim.getUUID());
            state.recordDmKill(killerTeam);
            roundWinner = state.checkDmWinner();
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_dm_kill",
                    victim.getName().getString(), killer.getName().getString(),
                    killerTeam,
                    state.getDmTeamKills().getOrDefault(killerTeam, 0),
                    state.getDmKillsToWin()));
        } else if (location.isBattleRoyale()) {
            state.recordDeath(victim.getUUID(), killer.getUUID());
            pvpPendingRespawn.add(victim.getUUID());
            UUID brWinnerUuid = state.checkBrWinner();
            int alive = state.getAliveThisRound().size();
            if (brWinnerUuid != null) {
                ServerPlayer brWinner = WaveDefenseMod.getServer()
                    .getPlayerList().getPlayer(brWinnerUuid);
                roundWinner = brWinner != null ? brWinner.getName().getString()
                    : Component.translatable("wavedefense.msg.pvp_unknown").getString();
            } else if (alive == 0) {
                // H3 fix: killer and victim died in the same tick (e.g. mutual kill).
                // The tick() watchdog will also catch this, but handle it here too
                // so the response is immediate rather than delayed by one tick.
                state.setPendingWinner(null);
                state.startRoundEndDelay(5);
                wm.broadcastToLocation(location.getName(),
                    Component.translatable("wavedefense.msg.pvp_br_draw"));
                broadcastPvpSync(wm, location);
                for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) wm.syncPlayerData(p);
                return; // state machine transition already applied
            }
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_br_out",
                    victim.getName().getString(), alive));
        } else {
            roundWinner = state.recordDeath(victim.getUUID(), killer.getUUID());
            pvpPendingRespawn.add(victim.getUUID());
        }

         if (roundWinner != null) {
            state.setPendingWinner(roundWinner);
            state.startRoundEndDelay(5);
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_team_wins_round", roundWinner));
            broadcastPvpSync(wm, location);
         } else {
             updatePvpEnemyCounts(location, state);
             broadcastPvpSync(wm, location);
         }

        // Notify monitoring system
        try {
            com.wavedefense.monitor.WaveDefenseMonitor.getInstance().onPvpKill(killer, victim);
        } catch (Exception e) {
            // Monitoring system error - don't break gameplay
        }

        for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) wm.syncPlayerData(p);
    }

    public void onPvpHit(WaveManager wm, ServerPlayer attacker, ServerPlayer victim) {
        PlayerWaveData data = ctx.playerData.get(victim.getUUID());
        if (data == null || data.getCurrentLocation() == null
            || !data.getCurrentLocation().isPvp()) return;
        LocationSession _s = ctx.getSession(data.getCurrentLocation().getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state != null) state.recordHit(attacker.getUUID(), victim.getUUID());
    }

    public void onPvpPlayerDeath(WaveManager wm, ServerPlayer player) {
        PlayerWaveData data = ctx.playerData.get(player.getUUID());
        if (data == null || data.getCurrentLocation() == null
            || !data.getCurrentLocation().isPvp()) return;
        Location location = data.getCurrentLocation();
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;

        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.PLAYER_DEATH);

        if (state != null && state.getPhase() == PvpRoundState.Phase.ACTIVE) {
            // Only deduct death-penalty if onPlayerKilledPlayer hasn't already done it.
            if (!pvpPenaltyDeducted.remove(player.getUUID())) {
                location.removePoints(player.getUUID(), location.getPvpDeathPenalty());
            }

            if (location.isDeathmatch()) {
                pvpPendingRespawn.add(player.getUUID());
                String dmWinner = state.checkDmWinner();
                if (dmWinner != null) {
                    state.setPendingWinner(dmWinner);
                    state.startRoundEndDelay(3);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_dm_winner",
                            dmWinner, state.getDmKillsToWin()));
                    broadcastPvpSync(wm, location);
                } else {
                    broadcastPvpSync(wm, location);
                }

            } else if (location.isBattleRoyale()) {
                // H4 fix: recordDeath() removes player from aliveThisRound so
                // checkBrWinner() sees the correct count. Without this call the
                // player stayed in aliveThisRound forever (environment kills bypass
                // onPlayerKilledPlayer which is the normal caller of recordDeath).
                state.recordDeath(player.getUUID(), null);
                pvpPendingRespawn.add(player.getUUID());
                int alive = state.getAliveThisRound().size();
                UUID brWinnerUuid = state.checkBrWinner();
                if (brWinnerUuid != null) {
                    ServerPlayer brWinner = WaveDefenseMod.getServer()
                        .getPlayerList().getPlayer(brWinnerUuid);
                    String winName = brWinner != null ? brWinner.getName().getString()
                        : Component.translatable("wavedefense.msg.pvp_unknown").getString();
                    state.setPendingWinner(winName);
                    state.startRoundEndDelay(5);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_last_survivor", winName));
                    broadcastPvpSync(wm, location);
                } else if (alive == 0) {
                    // H3 fix: last two players died simultaneously via environment —
                    // null pendingWinner signals endRound() to record a draw.
                    state.setPendingWinner(null);
                    state.startRoundEndDelay(5);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_br_draw"));
                    broadcastPvpSync(wm, location);
                } else {
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_br_out",
                            player.getName().getString(), alive));
                    broadcastPvpSync(wm, location);
                }

            } else {
                // Standard: гравець іде у spectator
                String roundWinner = state.recordDeath(player.getUUID(), null);
                pvpPendingRespawn.add(player.getUUID());
                if (roundWinner != null) {
                    state.setPendingWinner(roundWinner);
                    state.startRoundEndDelay(5);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_team_wins_round", roundWinner));
                    broadcastPvpSync(wm, location);
                } else {
                    updatePvpEnemyCounts(location, state);
                    broadcastPvpSync(wm, location);
                }
            }
        }
    }

    public boolean canPvpAttack(ServerPlayer attacker, ServerPlayer target) {
        PlayerWaveData data = ctx.playerData.get(attacker.getUUID());
        if (data == null || data.getCurrentLocation() == null) return true;
        Location location = data.getCurrentLocation();
        if (!location.isPvp()) return true;
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state == null || state.getPhase() != PvpRoundState.Phase.ACTIVE) return false;
        if (location.isPvpFriendlyFire()) return true;
        return !location.isSameTeam(attacker.getUUID(), target.getUUID());
    }

    // ════════════════════════════════════════════════════════════════════
    //  Package-private — called from WaveManager.surrenderPlayer
    // ════════════════════════════════════════════════════════════════════

    /**
     * Обробляє PvP-специфічну логіку коли гравець покидає локацію через surrender/exit.
     * Викликається з WaveManager.surrenderPlayer() після видалення playerData.
     */
    void onPlayerLeave(WaveManager wm, UUID playerId, Location locRef, String locName) {
        pvpPendingRespawn.remove(playerId);
        // Remove player from scoreboard team on leave
        net.minecraft.server.MinecraftServer srv = WaveDefenseMod.getServer();
        if (srv != null) {
            ServerPlayer leavingPlayer = srv.getPlayerList().getPlayer(playerId);
            if (leavingPlayer != null) removeFromScoreboardTeam(leavingPlayer);
        }
        LocationSession _s = ctx.getSession(locName);
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state != null) {
            state.removePlayer(playerId);
            String winner = state.checkRoundWinner();
            if (winner != null && state.getPhase() == PvpRoundState.Phase.ACTIVE) {
                // Use the state-machine path (setPendingWinner + delay) instead of
                // calling endRound() directly, which skips the ROUND_END_DELAY phase.
                state.setPendingWinner(winner);
                state.startRoundEndDelay(5);
                wm.broadcastToLocation(locRef.getName(),
                    Component.translatable("wavedefense.msg.pvp_team_wins_round", winner));
                broadcastPvpSync(wm, locRef);
            } else {
                updatePvpEnemyCounts(locRef, state);
                broadcastPvpSync(wm, locRef);
            }
        }
    }

    /**
     * Видаляє PvP state для локації — викликається з surrenderPlayer якщо гравців не залишилось.
     */
    void clearLocation(String locName) {
        LocationSession _s = ctx.getSession(locName);
        if (_s != null) _s.pvpState = null;
        cleanupScoreboardTeams(locName);
    }

    /**
     * Перебалансовує команди після виходу гравця — викликається з surrenderPlayer.
     */
    void rebalancePvpTeams(WaveManager wm, Location location, UUID leftPlayer) {
        if (!location.isPvpTeamAutoBalance()) return;
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        // H2 fix: also rebalance during BUY phase (between rounds) — the most common
        // moment when a player leaves and teams become lopsided.
        if (state == null
            || (state.getPhase() != PvpRoundState.Phase.WAITING
                && state.getPhase() != PvpRoundState.Phase.BUY)) return;

        Map<String, List<UUID>> teamPlayers = new java.util.LinkedHashMap<>();
        for (PvpSpawnPoint sp : location.getPvpSpawnPoints())
            teamPlayers.put(sp.getTeamName(), new java.util.ArrayList<>());
        for (PlayerWaveData d : ctx.playerData.values()) {
            if (d.getCurrentLocation() == null
                || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            if (d.getPlayerUUID() == null) continue;
            String t = location.getPlayerTeam(d.getPlayerUUID());
            if (t != null && teamPlayers.containsKey(t)) teamPlayers.get(t).add(d.getPlayerUUID());
        }
        if (teamPlayers.size() < 2) return;

        String bigTeam = null, smallTeam = null;
        int bigCount = 0, smallCount = Integer.MAX_VALUE;
        for (Map.Entry<String, List<UUID>> e : teamPlayers.entrySet()) {
            if (e.getValue().size() > bigCount) { bigCount = e.getValue().size(); bigTeam = e.getKey(); }
            if (e.getValue().size() < smallCount) { smallCount = e.getValue().size(); smallTeam = e.getKey(); }
        }
        if (bigTeam == null || smallTeam == null || bigTeam.equals(smallTeam)) return;
        if (bigCount - smallCount < 2) return;

        UUID toMove = teamPlayers.get(bigTeam).get(0);
        if (WaveDefenseMod.getServer() == null) return;
        ServerPlayer sp = WaveDefenseMod.getServer().getPlayerList().getPlayer(toMove);
        if (sp == null) return;

        PvpSpawnPoint newSpawn = null;
        for (PvpSpawnPoint spn : location.getPvpSpawnPoints()) {
            if (spn.getTeamName().equals(smallTeam)) { newSpawn = spn; break; }
        }
        if (newSpawn == null) return;

        final String finalSmallTeam = smallTeam;
        final String finalSpName    = sp.getName().getString();
        location.setPlayerTeam(toMove, finalSmallTeam);
        state.getAllStats().computeIfAbsent(toMove,
            id -> new PvpPlayerStats(finalSpName, finalSmallTeam))
            .setTeamName(finalSmallTeam);
        wm.teleportToSpawnPoint(sp, newSpawn);
        sp.displayClientMessage(Component.translatable(
            "wavedefense.msg.team_rebalanced", finalSmallTeam), false);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — state machine transitions
    // ════════════════════════════════════════════════════════════════════

    private void checkPvpStart(WaveManager wm, Location location) {
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state == null || state.getPhase() != PvpRoundState.Phase.WAITING) return;

        long count = ctx.playerData.values().stream()
            .filter(d -> d.getCurrentLocation() != null
                && d.getCurrentLocation().getName().equals(location.getName()))
            .count();

        if (count >= location.getPvpMinPlayers()) {
            state.startBuyPhase();
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_enough_players",
                    location.getPvpBuyTime()));
        }
    }

    private void startActiveRound(WaveManager wm, Location location, PvpRoundState state) {
        Set<UUID> allInLoc = new HashSet<>();
        for (Map.Entry<UUID, PlayerWaveData> e : ctx.playerData.entrySet()) {
            if (e.getValue().getCurrentLocation() != null
                && e.getValue().getCurrentLocation().getName().equals(location.getName())) {
                allInLoc.add(e.getKey());
            }
        }
        state.startActiveRound(allInLoc);
        pvpKillStreaks.clear();
        pvpPendingRespawn.clear(); // скидаємо черги respawn з попереднього раунду

        for (UUID pid : allInLoc) {
            ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (p == null) continue;
            wm.removeWaitEffects(p);
            wm.setSpectator(p, false);
            String team = location.getPlayerTeam(pid);
            if (team != null) {
                for (PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) {
                        wm.teleportToSpawnPoint(p, sp);
                        break;
                    }
                }
            }
            if (location.getPvpRoundStartPoints() > 0) {
                location.addPoints(pid, location.getPvpRoundStartPoints());
                p.displayClientMessage(Component.translatable(
                    "wavedefense.msg.pvp_round_start_points",
                    location.getPvpRoundStartPoints()), true);
            }
        }

        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.ROUND_START);
        if (state.getCurrentRound() == 1) {
            wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.MATCH_START);
        }
        wm.broadcastToLocation(location.getName(),
            Component.translatable("wavedefense.msg.pvp_round_started",
                state.getCurrentRound(), state.getTotalRounds()));
        broadcastPvpSync(wm, location);
    }

    void endRound(WaveManager wm, Location location, PvpRoundState state, String winnerTeam) {
        // winnerTeam == null means the round ended in a draw (H3 / H4 fix).
        // recordTeamWin() is null-safe and won't record anything for draws.
        state.recordTeamWin(winnerTeam);
        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.ROUND_END);
        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.TEAM_WIPE);

        if (winnerTeam != null) {
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_round_won",
                    winnerTeam, formatTeamWins(state)));
        } else {
            // Draw — already broadcast in the caller, but ensure team-wins summary is shown
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_br_draw"));
        }

        // Per-round point rewards — only meaningful when there's an actual winner
        if (winnerTeam != null && (location.getPvpWinPoints() > 0 || location.getPvpLosePoints() > 0)) {
            for (Map.Entry<UUID, PlayerWaveData> e : ctx.playerData.entrySet()) {
                if (e.getValue().getCurrentLocation() == null
                    || !e.getValue().getCurrentLocation().getName().equals(location.getName())) continue;
                String team = location.getPlayerTeam(e.getKey());
                ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(e.getKey());
                if (team == null) continue;
                if (team.equals(winnerTeam) && location.getPvpWinPoints() > 0) {
                    location.addPoints(e.getKey(), location.getPvpWinPoints());
                    if (p != null) p.displayClientMessage(Component.translatable(
                        "wavedefense.msg.pvp_win_points", location.getPvpWinPoints()), true);
                } else if (!team.equals(winnerTeam) && location.getPvpLosePoints() > 0) {
                    location.addPoints(e.getKey(), location.getPvpLosePoints());
                    if (p != null) p.displayClientMessage(Component.translatable(
                        "wavedefense.msg.pvp_lose_points", location.getPvpLosePoints()), true);
                }
            }
        }

        if (state.isAllRoundsDone()) {
            endPvpMatch(wm, location, state);
            return;
        }

        // Наступний BUY раунд
        state.startBuyPhase();
        for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            wm.setSpectator(p, false);
            wm.removeWaitEffects(p);
            if (location.isPvpWaitEffect()) wm.applyWaitEffects(p);
            String team = location.getPlayerTeam(p.getUUID());
            if (team != null) {
                for (PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) {
                        wm.teleportToSpawnPoint(p, sp);
                        break;
                    }
                }
            }
            wm.syncPlayerData(p);
        }
        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.BUY_PHASE);
        wm.broadcastToLocation(location.getName(),
            Component.translatable("wavedefense.msg.pvp_buy_phase_start",
                location.getPvpBuyTime(), state.getCurrentRound()));
        broadcastPvpSync(wm, location);
    }

    private void endPvpMatch(WaveManager wm, Location location, PvpRoundState state) {
        state.setPhase(PvpRoundState.Phase.ENDED);

        String champion = state.getTeamWins().entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String championDisplay = champion != null ? champion
            : Component.translatable("wavedefense.msg.pvp_nobody").getString();

        wm.broadcastToLocation(location.getName(),
            Component.translatable("wavedefense.msg.pvp_match_ended", championDisplay));
        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.MATCH_END);
        broadcastPvpSync(wm, location);

        String winTeam = champion;

        // M1 fix: pvpWinPoints / pvpLosePoints are already distributed per-round
        // in endRound(). Repeating them here caused a double payout for the final
        // round. If a separate "match bonus" is ever needed, add dedicated fields.
        List<UUID> toRemove = new ArrayList<>(ctx.playerData.entrySet().stream()
            .filter(e -> e.getValue().getCurrentLocation() != null
                && e.getValue().getCurrentLocation().getName().equals(location.getName()))
            .map(Map.Entry::getKey).toList());

        net.minecraft.core.BlockPos victoryExit   = location.getVictoryExitPos();
        net.minecraft.core.BlockPos surrenderExit = location.getSurrenderExitPos();

        for (UUID pid : toRemove) {
            ServerPlayer p = WaveDefenseMod.getServer() != null
                ? WaveDefenseMod.getServer().getPlayerList().getPlayer(pid) : null;
            pvpPendingRespawn.remove(pid);
            if (p != null) {
                removeFromScoreboardTeam(p);
                wm.removeWaitEffects(p);
                if (p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR) {
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                }
                PlayerBackup backup = ctx.playerBackups.remove(pid);
                if (backup != null) backup.restore(p);
                String team = location.getPlayerTeam(pid);
                net.minecraft.core.BlockPos exitPos =
                    (team != null && team.equals(winTeam) && victoryExit != null) ? victoryExit
                    : (surrenderExit != null) ? surrenderExit : null;
                if (exitPos != null) {
                    p.teleportTo(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5);
                }
            }
            ctx.playerData.remove(pid);
            location.removePlayerTeam(pid);
            if (p != null) wm.syncPlayerData(p);
            if (p != null) wm.clearTeammatesForPlayer(p);
        }
        ctx.removeSession(location.getName());
        wm.brManager.clearLocation(location.getName());
        cleanupScoreboardTeams(location.getName());
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ════════════════════════════════════════════════════════════════════

    private void updatePvpEnemyCounts(Location location, PvpRoundState state) {
        Set<UUID> alive = state.getAliveThisRound();
        for (Map.Entry<UUID, PlayerWaveData> entry : ctx.playerData.entrySet()) {
            PlayerWaveData d = entry.getValue();
            if (d.getCurrentLocation() == null
                || !d.getCurrentLocation().getName().equals(location.getName())) continue;
            UUID pid = entry.getKey();
            String myTeam = location.getPlayerTeam(pid);
            int enemies = (int) alive.stream()
                .filter(id -> !id.equals(pid))
                .filter(id -> {
                    String t = location.getPlayerTeam(id);
                    return t != null && !t.equals(myTeam);
                })
                .count();
            d.setMobsRemaining(enemies);
        }
    }

    private void broadcastPvpSync(WaveManager wm, Location location) {
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state == null) return;

        List<SyncPvpStatePacket.PlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, PvpPlayerStats> e : state.getAllStats().entrySet()) {
            PvpPlayerStats ps = e.getValue();
            boolean alive = state.getAliveThisRound().contains(e.getKey());
            entries.add(new SyncPvpStatePacket.PlayerEntry(
                ps.getPlayerName(), ps.getTeamName(),
                ps.getKills(), ps.getDeaths(), ps.getAssists(), alive));
        }

        for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
            String myTeam = location.getPlayerTeam(p.getUUID());
            net.minecraft.nbt.CompoundTag tag = SyncPvpStatePacket.build(
                location.getName(), state.getPhase().name(),
                state.getCurrentRound(), state.getTotalRounds(), state.getTimerSeconds(),
                state.getTeamWins(), entries, myTeam);
            WaveDefenseMod.packetHandler.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new SyncPvpStatePacket(tag));
        }
    }

    private String formatTeamWins(PvpRoundState state) {
        StringBuilder sb = new StringBuilder();
        state.getTeamWins().forEach((t, w) ->
            sb.append("§e").append(t).append("§7:").append(w).append(" "));
        return sb.toString().trim();
    }

    // ── Scoreboard helpers (server-side nameplate hiding) ─────────────────

    private static final String WD_TEAM_PREFIX = "wd_";

    /**
     * Assigns the player to a Minecraft Scoreboard team so the server hides
     * their nameplate from players on other teams.
     */
    private void assignScoreboardTeam(ServerPlayer player, String locationName, String teamName) {
        net.minecraft.server.MinecraftServer server = WaveDefenseMod.getServer();
        if (server == null) return;
        try {
            net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
            String sbId = WD_TEAM_PREFIX + locationName + "_" + teamName;
            net.minecraft.world.scores.PlayerTeam sbTeam = sb.getPlayerTeam(sbId);
            if (sbTeam == null) {
                sbTeam = sb.addPlayerTeam(sbId);
                sbTeam.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.HIDE_FOR_OTHER_TEAMS);
            }
            // Remove from any previous WD team first
            net.minecraft.world.scores.PlayerTeam current = sb.getPlayersTeam(player.getScoreboardName());
            if (current != null) sb.removePlayerFromTeam(player.getScoreboardName(), current);
            sb.addPlayerToTeam(player.getScoreboardName(), sbTeam);
        } catch (Exception ignored) {}
    }

    /**
     * Removes the player from their current Scoreboard team (only if it was
     * assigned by Wave Defense, i.e. starts with the WD prefix).
     */
    private void removeFromScoreboardTeam(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = WaveDefenseMod.getServer();
        if (server == null) return;
        try {
            net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
            net.minecraft.world.scores.PlayerTeam current = sb.getPlayersTeam(player.getScoreboardName());
            if (current != null && current.getName().startsWith(WD_TEAM_PREFIX)) {
                sb.removePlayerFromTeam(player.getScoreboardName(), current);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Removes all Scoreboard teams created for the given location (called at session end
     * to keep the Scoreboard clean).
     */
    private void cleanupScoreboardTeams(String locationName) {
        net.minecraft.server.MinecraftServer server = WaveDefenseMod.getServer();
        if (server == null) return;
        try {
            net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
            String prefix = WD_TEAM_PREFIX + locationName + "_";
            new java.util.ArrayList<>(sb.getPlayerTeams()).stream()
                .filter(t -> t.getName().startsWith(prefix))
                .forEach(sb::removePlayerTeam);
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану PvP-менеджера. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // Pending respawns
        ListTag pendingList = new ListTag();
        for (UUID uuid : pvpPendingRespawn) {
            pendingList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("pvpPendingRespawn", pendingList);
        // Kill streaks
        CompoundTag streaksTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : pvpKillStreaks.entrySet()) {
            streaksTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("pvpKillStreaks", streaksTag);
        return tag;
    }

    /** Відновлення стану PvP-менеджера. */
    public void load(CompoundTag tag) {
        pvpPendingRespawn.clear();
        pvpKillStreaks.clear();
        if (tag.contains("pvpPendingRespawn")) {
            ListTag pendingList = tag.getList("pvpPendingRespawn", 8);
            for (int i = 0; i < pendingList.size(); i++) {
                pvpPendingRespawn.add(UUID.fromString(pendingList.getString(i)));
            }
        }
        if (tag.contains("pvpKillStreaks")) {
            CompoundTag streaksTag = tag.getCompound("pvpKillStreaks");
            for (String key : streaksTag.getAllKeys()) {
                pvpKillStreaks.put(UUID.fromString(key), streaksTag.getInt(key));
            }
        }
    }
}
