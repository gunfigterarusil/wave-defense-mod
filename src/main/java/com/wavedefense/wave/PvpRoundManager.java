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

    /**
     * Returns the {@link PvpSpawnPoint} a Deathmatch player should spawn at, honouring the
     * location's {@link Location.DmSpawnMode}. Used by both initial round spawn and respawn.
     *
     * @param location  the PvP location (must be DM mode)
     * @param player    the player about to (re)spawn — used for SMART_SPAWN distance check
     * @param teamSpawn fallback spawn (player's team spawn point) — used for TEAM_SPAWN
     * @return the chosen spawn point, or {@code teamSpawn} when no other candidate exists
     */
    public PvpSpawnPoint pickDmSpawn(Location location, ServerPlayer player, PvpSpawnPoint teamSpawn) {
        if (location == null) return teamSpawn;
        Location.DmSpawnMode mode = location.getDmSpawnMode();
        java.util.List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        if (spawns.isEmpty()) return teamSpawn;
        if (mode == Location.DmSpawnMode.TEAM_SPAWN) return teamSpawn;

        java.util.Random rng = new java.util.Random();
        if (mode == Location.DmSpawnMode.RANDOM_SPAWN) {
            return spawns.get(rng.nextInt(spawns.size()));
        }

        // SMART_SPAWN: pick the candidate with the largest minimum-distance to any living enemy.
        // Try up to 10 random candidates; settle on the best so far.
        java.util.List<ServerPlayer> enemies = new java.util.ArrayList<>();
        if (player != null) {
            for (PlayerWaveData d : ctx.playerData.values()) {
                if (d.getPlayerUUID() == null) continue;
                if (d.getPlayerUUID().equals(player.getUUID())) continue;
                if (d.getCurrentLocation() == null
                        || !d.getCurrentLocation().getName().equals(location.getName())) continue;
                ServerPlayer ep = WaveDefenseMod.getServer() == null ? null
                        : WaveDefenseMod.getServer().getPlayerList().getPlayer(d.getPlayerUUID());
                if (ep == null || !ep.isAlive()) continue;
                // In DM, anyone not on my team counts as enemy
                String myTeam    = location.getPlayerTeam(player.getUUID());
                String theirTeam = location.getPlayerTeam(d.getPlayerUUID());
                if (myTeam != null && myTeam.equals(theirTeam)) continue;
                enemies.add(ep);
            }
        }
        if (enemies.isEmpty()) {
            // No enemies tracked — fall back to random
            return spawns.get(rng.nextInt(spawns.size()));
        }
        PvpSpawnPoint best = null;
        double bestMinDist = -1;
        int tries = Math.min(10, spawns.size());
        java.util.Set<Integer> tried = new java.util.HashSet<>();
        for (int i = 0; i < tries; i++) {
            int idx;
            int guard = 0;
            do { idx = rng.nextInt(spawns.size()); guard++; }
            while (tried.contains(idx) && guard < 20);
            tried.add(idx);
            PvpSpawnPoint cand = spawns.get(idx);
            if (cand.getPos() == null) continue;
            double minDist = Double.MAX_VALUE;
            for (ServerPlayer enemy : enemies) {
                double d = enemy.blockPosition().distSqr(cand.getPos());
                if (d < minDist) minDist = d;
            }
            if (minDist > bestMinDist) { bestMinDist = minDist; best = cand; }
            // Early exit if we already cleared the safe-distance threshold (10 blocks = 100)
            if (bestMinDist >= 100.0) break;
        }
        return best != null ? best : (teamSpawn != null ? teamSpawn : spawns.get(0));
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

        // v0.2.61: BR late-join lock — once the match is past READY_CHECK,
        // new joiners would be inserted as already-eliminated (their team's
        // spawn is overrun). Reject with a clear message.
        if (location.isBattleRoyale()) {
            LocationSession existing = ctx.sessions.get(location.getName());
            if (existing != null && existing.pvpState != null) {
                PvpRoundState.Phase ph = existing.pvpState.getPhase();
                if (ph != PvpRoundState.Phase.WAITING
                 && ph != PvpRoundState.Phase.READY_CHECK
                 && ph != PvpRoundState.Phase.ENDED) {
                    player.displayClientMessage(
                        Component.translatable("wavedefense.msg.br_match_locked"), false);
                    return;
                }
            }
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

        // Bounds-check the spawn index — protects against stale packets / admin
        // mid-session removing a spawn point. Falls back to 0 with a log warning.
        java.util.List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        if (spawns.isEmpty()) {
            WaveDefenseMod.LOGGER.warn("[WD] PvP join rejected — location '{}' has no spawn points",
                location.getName());
            player.displayClientMessage(
                Component.translatable("wavedefense.msg.pvp_no_spawn_points"), false);
            ctx.playerBackups.remove(playerId);
            return;
        }
        if (spawnIndex < 0 || spawnIndex >= spawns.size()) {
            WaveDefenseMod.LOGGER.warn(
                "[WD] PvP join: spawnIndex {} out of range for '{}' ({} points) — using 0",
                spawnIndex, location.getName(), spawns.size());
            spawnIndex = 0;
        }
        PvpSpawnPoint spawnPoint = spawns.get(spawnIndex);
        location.setPlayerTeam(playerId, spawnPoint.getTeamName());
        assignScoreboardTeam(player, location.getName(), spawnPoint.getTeamName());

        // v0.2.64: per-team starting items are ADDITIVE on top of the location-
        // global ones that were applied in the !keepInventory block above. Admin
        // can leave global empty and put items only on a specific team, or vice
        // versa. Inventory was cleared earlier so duplicates aren't a concern.
        if (!location.isKeepInventory() && !spawnPoint.getStartingItems().isEmpty()) {
            for (net.minecraft.world.item.ItemStack item : spawnPoint.getStartingItems()) {
                if (item != null && !item.isEmpty()) player.getInventory().add(item.copy());
            }
        }

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
            // DM / BR redesign: both are single-match modes — force totalRounds=1.
            //   DM: kills-to-win ends the match → endRound() → endPvpMatch().
            //   BR: last man standing ends the match.
            int rounds = (location.isDeathmatch() || location.isBattleRoyale())
                ? 1 : location.getPvpTotalRounds();
            // DM and BR have no shop/buy phase — pass buyTime=0 so BUY expires instantly
            // (also bypassed in checkPvpStart, but keeps state consistent).
            int buy = (location.isDeathmatch() || location.isBattleRoyale())
                ? 0 : location.getPvpBuyTime();
            _s.pvpState = new PvpRoundState(rounds, buy);
            _s.pvpState.setDmKillsToWin(location.getDmKillsToWin());
            // Common admin pitfall: Standard with totalRounds=1 ends the match on the
            // very first round result (including draws) — players are kicked out
            // immediately after the BUY phase if nobody scores. Warn loudly.
            if (!location.isDeathmatch() && !location.isBattleRoyale()
                    && !location.isObjectiveMode() && rounds == 1) {
                WaveDefenseMod.LOGGER.warn(
                    "[WD/PvP] Location '{}' is Standard mode with totalRounds=1. "
                    + "The match will end after a single round — first draw or kill "
                    + "ends the entire match. Set totalRounds >= 3 for typical play.",
                    location.getName());
            }
        }
        PvpRoundState state = _s.pvpState;
        state.registerPlayer(playerId, player.getName().getString(), spawnPoint.getTeamName());

        // Mid-round join — if a round is already ACTIVE, add the new player to
        // aliveThisRound so checkRoundWinner() considers them. Without this they
        // would be invisible to the round-winner predicate even though they're
        // alive and on a real team — leading to premature round-end calls.
        // BR is excluded: respawn-as-spectator is by design (eliminated player).
        if (state.getPhase() == PvpRoundState.Phase.ACTIVE
                && !location.isBattleRoyale()) {
            state.getAliveThisRound().add(playerId);
        }

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

            // v0.2.61: READY_CHECK tick — count down timeout; advance when all alive ready
            //   OR when timer expires (force-start with current ready set, AFK drops out).
            if (state.getPhase() == PvpRoundState.Phase.READY_CHECK) {
                state.tickDown();
                if (state.getTimerTicks() % 20 == 0) broadcastPvpSync(wm, location);
                // Count alive players (anyone in this location) and ready ones
                long inLoc = ctx.playerData.values().stream()
                    .filter(d -> d.getCurrentLocation() != null
                        && d.getCurrentLocation().getName().equals(locName))
                    .count();
                boolean allReady = inLoc > 0 && state.getReadyCount() >= inLoc;
                boolean timeoutExpired = state.getTimerTicks() <= 0
                    && location.getPvpReadyCheckTimeoutSec() > 0;
                if (allReady || timeoutExpired) {
                    long readyCount = state.getReadyCount();
                    // Force-start guard: at least minPlayers must be ready or in location
                    long startCount = timeoutExpired ? readyCount : inLoc;
                    if (startCount >= location.getPvpMinPlayers()) {
                        state.clearReadyPlayers();
                        directStartFromWaitingDeprecated(wm, location, state, inLoc);
                    } else {
                        // Not enough ready by timeout — return to WAITING for more joiners
                        state.setPhase(PvpRoundState.Phase.WAITING);
                        state.clearReadyPlayers();
                        wm.broadcastToLocation(location.getName(),
                            Component.translatable("wavedefense.msg.pvp_ready_check_failed"));
                        broadcastPvpSync(wm, location);
                    }
                }
                continue;
            }
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
                // CtP/KotH win condition is handled by CapturePointManager — skip alive-check
                if (!location.isObjectiveMode()) {
                    String winner = state.checkRoundWinner();
                    if (winner != null) {
                        state.setPendingWinner(winner);
                        state.startRoundEndDelay(5);
                        wm.broadcastToLocation(location.getName(),
                            Component.translatable("wavedefense.msg.pvp_team_wins_round", winner));
                        broadcastPvpSync(wm, location);
                    } else if (state.getAliveThisRound().isEmpty()) {
                        // X2 fix: covers both Standard mode (previously missed) and BR (H3).
                        // If all players are dead simultaneously (e.g. mutual kill, fall/fire),
                        // checkRoundWinner() returns null and we must still end the round as draw
                        // or the ACTIVE phase runs forever with zero alive players.
                        state.setPendingWinner(null);
                        state.startRoundEndDelay(5);
                        Component drawMsg = location.isBattleRoyale()
                            ? Component.translatable("wavedefense.msg.pvp_br_draw")
                            : Component.translatable("wavedefense.msg.pvp_draw");
                        wm.broadcastToLocation(location.getName(), drawMsg);
                        broadcastPvpSync(wm, location);
                    } else if (location.getPvpRoundTimeLimitSec() > 0
                            && !location.isBattleRoyale()) {
                        // Round/match time limit timer (Standard + DM).
                        state.tickDown();
                        int t = state.getTimerTicks();
                        // Broadcast countdown at key intervals
                        if (t > 0 && t % 20 == 0) {
                            int secsLeft = t / 20;
                            if (secsLeft == 60 || secsLeft == 30 || secsLeft == 10
                                    || (secsLeft <= 5 && secsLeft > 0)) {
                                wm.broadcastToLocation(location.getName(),
                                    Component.translatable("wavedefense.msg.pvp_time_left", secsLeft));
                            }
                            broadcastPvpSync(wm, location);
                        }
                        if (t <= 0) {
                            // Timer expired — decide winner.
                            String timeoutWinner = decideTimeoutWinner(location, state);
                            state.setPendingWinner(timeoutWinner);
                            state.startRoundEndDelay(5);
                            if (timeoutWinner != null) {
                                wm.broadcastToLocation(location.getName(),
                                    Component.translatable(
                                        location.isDeathmatch()
                                            ? "wavedefense.msg.pvp_timeout_dm_winner"
                                            : "wavedefense.msg.pvp_timeout_winner",
                                        timeoutWinner));
                            } else {
                                wm.broadcastToLocation(location.getName(),
                                    Component.translatable("wavedefense.msg.pvp_timeout_draw"));
                            }
                            broadcastPvpSync(wm, location);
                        }
                    }
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

        // Kill streak — use Component args so clients receive a translatable component
        int streak = pvpKillStreaks.merge(killer.getUUID(), 1, Integer::sum);
        pvpKillStreaks.remove(victim.getUUID());
        net.minecraft.network.chat.Component streakSuffix = streak >= 3
            ? Component.translatable("wavedefense.msg.pvp_kill_streak", streak)
            : Component.empty();
        killer.displayClientMessage(
            Component.translatable("wavedefense.msg.pvp_kill",
                kill, victim.getName(), streakSuffix), true);

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
                // Player names don't need translation; "unknown" key does
                roundWinner = brWinner != null ? brWinner.getName().getString() : "?";
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
                    victim.getName(), alive));
        } else {
            roundWinner = state.recordDeath(victim.getUUID(), killer.getUUID());
            pvpPendingRespawn.add(victim.getUUID());
        }

         if (roundWinner != null) {
            state.setPendingWinner(roundWinner);
            state.startRoundEndDelay(5);
            net.minecraft.network.chat.Component winnerComp = "?".equals(roundWinner)
                ? Component.translatable("wavedefense.msg.pvp_unknown")
                : Component.literal(roundWinner);
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_team_wins_round", winnerComp));
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
                    String winName = brWinner != null ? brWinner.getName().getString() : "?";
                    state.setPendingWinner(winName);
                    state.startRoundEndDelay(5);
                    net.minecraft.network.chat.Component winComp = "?".equals(winName)
                        ? Component.translatable("wavedefense.msg.pvp_unknown")
                        : Component.literal(winName);
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.pvp_last_survivor", winComp));
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
                            player.getName(), alive));
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
            if (leavingPlayer != null) {
                removeFromScoreboardTeam(leavingPlayer);
                // M-1 fix: clear the CtP/KotH HUD overlay for the player who just left.
                // endPvpMatch() already sends a clear packet to all remaining players, but
                // when a single player surrenders mid-match the match doesn't end — only
                // that specific player needs their overlay cleared.
                if (locRef.isObjectiveMode()) {
                    com.wavedefense.network.PacketHandler.sendToPlayer(leavingPlayer,
                        new com.wavedefense.network.packets.SyncCtpStatePacket(
                            "", new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
                            new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
                            new java.util.LinkedHashMap<>(), 0, 0));
                }
            }
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
        // Update scoreboard team so nametag visibility (HIDE_FOR_OTHER_TEAMS) reflects
        // the new team. Without this the rebalanced player would appear as ally to the
        // OLD team and as enemy-with-hidden-name to the NEW team.
        removeFromScoreboardTeam(sp);
        assignScoreboardTeam(sp, location.getName(), finalSmallTeam);
        wm.teleportToSpawnPoint(sp, newSpawn);
        sp.displayClientMessage(Component.translatable(
            "wavedefense.msg.team_rebalanced", finalSmallTeam), false);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — state machine transitions
    // ════════════════════════════════════════════════════════════════════

    /**
     * Decide the winner when the round / match time limit expires.
     * <p>
     * Standard: team with the most alive players wins (draw if tied).
     * Deathmatch: team with the most kills wins (draw if tied).
     * Returns null = draw.
     */
    private String decideTimeoutWinner(Location location, PvpRoundState state) {
        if (location.isDeathmatch()) {
            // Leading team by kills wins
            return leadingTeam(state.getDmTeamKills());
        }
        // Standard: count alive players per team
        Map<String, Integer> aliveByTeam = new java.util.LinkedHashMap<>();
        for (UUID id : state.getAliveThisRound()) {
            PvpPlayerStats ps = state.getStats(id);
            if (ps == null || ps.getTeamName() == null) continue;
            aliveByTeam.merge(ps.getTeamName(), 1, Integer::sum);
        }
        return leadingTeam(aliveByTeam);
    }

    /** Returns the single highest-valued key in the map, or null on tie / empty. */
    private static String leadingTeam(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) return null;
        String leader = null;
        int best = Integer.MIN_VALUE;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); leader = e.getKey(); tie = false; }
            else if (e.getValue() == best) { tie = true; }
        }
        return tie ? null : leader;
    }

    private void checkPvpStart(WaveManager wm, Location location) {
        LocationSession _s = ctx.getSession(location.getName());
        PvpRoundState state = _s != null ? _s.pvpState : null;
        if (state == null || state.getPhase() != PvpRoundState.Phase.WAITING) return;

        long count = ctx.playerData.values().stream()
            .filter(d -> d.getCurrentLocation() != null
                && d.getCurrentLocation().getName().equals(location.getName()))
            .count();

        if (count >= location.getPvpMinPlayers()) {
            // v0.2.61: Transition WAITING → READY_CHECK instead of straight to BUY/ACTIVE.
            // Players are now spawned at their team points but wait-effects keep them
            // frozen until either everyone presses ready or the timeout expires.
            // The actual start (BUY for Standard, ACTIVE for DM/BR/CtP/KotH) is
            // deferred to advanceFromReadyCheck() below.
            int timeoutSec = location.getPvpReadyCheckTimeoutSec();
            state.startReadyCheck(timeoutSec);
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_ready_check_started", timeoutSec));
            return;
        }
        // ── Legacy direct-start kept as helper, called only when ready-check
        //    times out (advanceFromReadyCheck) — see helper at end of class. ──
        directStartFromWaitingDeprecated(wm, location, state, count);
    }

    // ════════════════════════════════════════════════════════════════════
    //  v0.2.61: Ready-check public API
    // ════════════════════════════════════════════════════════════════════

    /** Marks a player as ready. If all in-location players are ready, advance
     *  immediately to BUY/ACTIVE without waiting for the timeout. Called from
     *  ReadyCheckPacket (future UI hotkey) or admin command. */
    public void markPlayerReady(WaveManager wm, String locationName, UUID playerId) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return;
        if (s.pvpState.getPhase() != PvpRoundState.Phase.READY_CHECK) return;
        s.pvpState.markPlayerReady(playerId);
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location != null) broadcastPvpSync(wm, location);
    }

    /** Unmarks a player as ready (toggle off). Called from ReadyCheckPacket when
     *  the player presses R again before timeout. */
    public void unmarkPlayerReady(WaveManager wm, String locationName, UUID playerId) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return;
        if (s.pvpState.getPhase() != PvpRoundState.Phase.READY_CHECK) return;
        s.pvpState.unmarkPlayerReady(playerId);
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location != null) broadcastPvpSync(wm, location);
    }

    /** Admin force-end an active PvP match (called from /wda match stop|restart).
     *  Wipes the session — players need to rejoin. Returns true if a session was ended. */
    public boolean forceEndPvpLocation(WaveManager wm, String locationName) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return false;
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location != null) endPvpMatch(wm, location, s.pvpState);
        return true;
    }

    /** v0.2.65: lists names of ready players for /wda who-ready. */
    public String debugDumpReadySet(String locationName) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return "no active PvP session for '" + locationName + "'";
        PvpRoundState st = s.pvpState;
        if (st.getPhase() != PvpRoundState.Phase.READY_CHECK)
            return "Phase=" + st.getPhase() + " (not READY_CHECK) — ready set empty by definition";
        java.util.Set<UUID> ready = st.getReadyPlayers();
        if (ready.isEmpty()) return "No players ready yet (timer " + st.getTimerSeconds() + "s)";
        StringBuilder sb = new StringBuilder();
        sb.append("Ready (").append(ready.size()).append("): ");
        boolean first = true;
        for (UUID id : ready) {
            PvpPlayerStats ps = st.getStats(id);
            if (!first) sb.append(", ");
            sb.append(ps != null ? ps.getPlayerName() : id.toString());
            first = false;
        }
        sb.append("  (timer ").append(st.getTimerSeconds()).append("s)");
        return sb.toString();
    }

    /** Multi-line string dump of current PvP state for /wda debug state. */
    public String debugDumpPvpState(String locationName) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return "no active PvP session for '" + locationName + "'";
        PvpRoundState st = s.pvpState;
        long count = ctx.playerData.values().stream()
            .filter(d -> d.getCurrentLocation() != null
                && d.getCurrentLocation().getName().equals(locationName)).count();
        StringBuilder sb = new StringBuilder();
        sb.append("PvP[").append(locationName).append("] ");
        sb.append("phase=").append(st.getPhase()).append(' ');
        sb.append("round=").append(st.getCurrentRound()).append('/').append(st.getTotalRounds()).append(' ');
        sb.append("timer=").append(st.getTimerSeconds()).append("s ");
        sb.append("inLoc=").append(count).append(' ');
        sb.append("ready=").append(st.getReadyCount()).append(' ');
        sb.append("alive=").append(st.getAliveThisRound().size());
        return sb.toString();
    }

    /** Admin force-skip of the READY_CHECK phase regardless of who pressed ready.
     *  Goes directly to BUY/ACTIVE as if everyone confirmed. */
    public void skipReadyCheck(WaveManager wm, String locationName) {
        LocationSession s = ctx.sessions.get(locationName);
        if (s == null || s.pvpState == null) return;
        if (s.pvpState.getPhase() != PvpRoundState.Phase.READY_CHECK) return;
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return;
        long inLoc = ctx.playerData.values().stream()
            .filter(d -> d.getCurrentLocation() != null
                && d.getCurrentLocation().getName().equals(locationName))
            .count();
        s.pvpState.clearReadyPlayers();
        directStartFromWaitingDeprecated(wm, location, s.pvpState, inLoc);
    }

    /** Extracted legacy direct-start so it can be reused from the ready-check
     *  timeout handler. Original WAITING→BUY/ACTIVE logic unchanged. */
    private void directStartFromWaitingDeprecated(WaveManager wm, Location location, PvpRoundState state, long count) {
        if (count >= location.getPvpMinPlayers()) {
            if (location.isDeathmatch() || location.isBattleRoyale()) {
                // DM / BR: no BUY phase — go directly to ACTIVE (or countdown).
                state.markFirstRound();
                int delay = location.getPvpRoundStartDelay();
                if (delay > 0) {
                    state.startCountdown(delay);
                    Component msg = location.isBattleRoyale()
                        ? Component.translatable("wavedefense.msg.pvp_br_starting", delay)
                        : Component.translatable("wavedefense.msg.pvp_dm_starting", delay);
                    wm.broadcastToLocation(location.getName(), msg);
                } else {
                    startActiveRound(wm, location, state);
                }
            } else {
                state.startBuyPhase();
                wm.broadcastToLocation(location.getName(),
                    Component.translatable("wavedefense.msg.pvp_enough_players",
                        location.getPvpBuyTime()));
            }
            broadcastPvpSync(wm, location);
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
        pvpPenaltyDeducted.clear(); // скидаємо деdup-сет щоб штрафи наступного раунду рахувались знову

        // Insufficient-teams check — applies to Standard only.
        // DM uses dmTeamKills, BR uses last-alive, CtP/KotH use score; those modes
        // don't suffer from the "round ends instantly because everyone is on one team" bug.
        if (!location.isDeathmatch() && !location.isBattleRoyale() && !location.isObjectiveMode()) {
            java.util.Set<String> teamsPresent = new java.util.HashSet<>();
            for (UUID pid : allInLoc) {
                String t = location.getPlayerTeam(pid);
                if (t != null && !t.isBlank()) teamsPresent.add(t);
            }
            if (teamsPresent.size() < 2) {
                wm.broadcastToLocation(location.getName(),
                    Component.translatable("wavedefense.msg.pvp_insufficient_teams"));
                WaveDefenseMod.LOGGER.warn(
                    "[WD] Round at '{}' starts with only {} team(s) — checkRoundWinner() will be inert until opponents join",
                    location.getName(), teamsPresent.size());
            }
        }

        // CtP/KotH: initialise capture point tracking
        if (location.isObjectiveMode()) {
            if (location.getCapturePoints().isEmpty()) {
                // H-2 fix: no capture points configured → abort immediately with a warning
                wm.broadcastToLocation(location.getName(),
                    Component.translatable("wavedefense.msg.ctp_no_points"));
                state.setPendingWinner(null);
                state.startRoundEndDelay(3);
                return;
            }
            state.initCapturePoints(location.getCapturePoints(), location.getObjectiveRoundDurationSec());
        }

        for (UUID pid : allInLoc) {
            ServerPlayer p = WaveDefenseMod.getServer().getPlayerList().getPlayer(pid);
            if (p == null) continue;
            wm.removeWaitEffects(p);
            wm.setSpectator(p, false);
            String team = location.getPlayerTeam(pid);
            PvpSpawnPoint teamSpawn = null;
            if (team != null) {
                for (PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                    if (sp.getTeamName().equals(team)) { teamSpawn = sp; break; }
                }
            }
            // B1: DM honours dmSpawnMode (TEAM / RANDOM / SMART). Other modes always use team spawn.
            PvpSpawnPoint chosen = location.isDeathmatch()
                ? pickDmSpawn(location, p, teamSpawn)
                : teamSpawn;
            if (chosen != null) wm.teleportToSpawnPoint(p, chosen);
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
        // Round / match time limit (Standard + DM only — objective modes use their own timer).
        // 0 = no limit. Sets ACTIVE-phase timer that counts down in tick().
        if (location.getPvpRoundTimeLimitSec() > 0
                && !location.isObjectiveMode() && !location.isBattleRoyale()) {
            state.setTimerTicks(location.getPvpRoundTimeLimitSec() * 20);
        }
        // DM redesign: no round concept in DM — broadcast a single "match started" message.
        if (location.isDeathmatch()) {
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_dm_match_started",
                    state.getDmKillsToWin()));
        } else {
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_round_started",
                    state.getCurrentRound(), state.getTotalRounds()));
        }
        broadcastPvpSync(wm, location);
    }

    void endRound(WaveManager wm, Location location, PvpRoundState state, String winnerTeam) {
        // winnerTeam == null means the round ended in a draw (H3 / H4 fix).
        // recordTeamWin() is null-safe and won't record anything for draws.
        WaveDefenseMod.LOGGER.info(
            "[WD/PvP] endRound @ '{}' — round {}/{}, winner={}, teamWins={}",
            location.getName(), state.getCurrentRound(), state.getTotalRounds(),
            winnerTeam == null ? "(draw)" : winnerTeam, state.getTeamWins());
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

        // DM redesign: DM is a single match — always end the match when the kill
        // target is reached, regardless of totalRounds.
        // For all other modes isAllRoundsDone() (currentRound >= totalRounds) applies.
        if (location.isDeathmatch() || state.isAllRoundsDone()) {
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

    /**
     * Called by {@link CapturePointManager} when a CtP/KotH win condition is met.
     * Sets the pending winner and starts the round-end delay — identical to the normal path.
     *
     * @param timerEnd true = round ended because time ran out (show timer-win message)
     */
    void declareObjectiveWinner(WaveManager wm, Location location, PvpRoundState state,
                                String winnerTeam, boolean timerEnd) {
        if (state.getPhase() != PvpRoundState.Phase.ACTIVE) return;
        state.setPendingWinner(winnerTeam);
        state.startRoundEndDelay(5);
        if (winnerTeam != null) {
            Component msg = timerEnd
                ? Component.translatable("wavedefense.msg.ctp_timer_wins", winnerTeam)
                : Component.translatable("wavedefense.msg.ctp_wins", winnerTeam);
            wm.broadcastToLocation(location.getName(), msg);
        } else {
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_br_draw"));
        }
        broadcastPvpSync(wm, location);
    }

    private void endPvpMatch(WaveManager wm, Location location, PvpRoundState state) {
        // Log a clear reason for the match ending so admins can debug premature
        // exits ("we just joined, BUY ended, suddenly we're out" complaints).
        String reason = location.isDeathmatch() ? "DM kill target reached"
            : "all rounds played (" + state.getCurrentRound() + "/" + state.getTotalRounds() + ")";
        WaveDefenseMod.LOGGER.info(
            "[WD/PvP] endPvpMatch @ '{}' — reason: {} — teamWins={}",
            location.getName(), reason, state.getTeamWins());
        state.setPhase(PvpRoundState.Phase.ENDED);

        // H-1 fix: detect ties before picking a champion
        int maxWins = state.getTeamWins().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long teamsAtMax = state.getTeamWins().values().stream().filter(v -> v == maxWins).count();
        boolean isDraw = teamsAtMax > 1;

        String champion = isDraw ? null
            : state.getTeamWins().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        // Pass Component as arg so clients render pvp_nobody in their own language
        net.minecraft.network.chat.Component championComp = champion != null
            ? net.minecraft.network.chat.Component.literal(champion)
            : Component.translatable("wavedefense.msg.pvp_nobody");

        if (isDraw) {
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.pvp_draw"));
        }
        wm.broadcastToLocation(location.getName(),
            Component.translatable("wavedefense.msg.pvp_match_ended", championComp));
        wm.fireLootTriggerByName(location.getName(), LootSpawn.Trigger.MATCH_END);
        broadcastPvpSync(wm, location);

        // H-6 fix: clear CtP/KotH overlay on clients when the match ends
        if (location.isObjectiveMode()) {
            com.wavedefense.network.packets.SyncCtpStatePacket clearPkt =
                new com.wavedefense.network.packets.SyncCtpStatePacket(
                    "", new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
                    new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
                    new java.util.LinkedHashMap<>(), 0, 0);
            for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
                com.wavedefense.network.PacketHandler.sendToPlayer(p, clearPkt);
            }
        }

        String winTeam = champion;

        // B5: build & send post-match scoreboard packet to every player in the location.
        // Done BEFORE removing players so PacketHandler.sendToPlayer still finds them online.
        {
            String modeLabel = location.isDeathmatch() ? "DM"
                : location.isBattleRoyale()        ? "BR"
                : location.isCtpMode()             ? "CTP"
                : location.isKothMode()            ? "KOTH"
                : "STANDARD";
            java.util.List<com.wavedefense.network.packets.OpenPostMatchScoreboardPacket.PlayerRow> rowList =
                new java.util.ArrayList<>();
            for (Map.Entry<UUID, PvpPlayerStats> e : state.getAllStats().entrySet()) {
                PvpPlayerStats st = e.getValue();
                int playerPts = location.getPlayerPoints(e.getKey());
                rowList.add(new com.wavedefense.network.packets.OpenPostMatchScoreboardPacket.PlayerRow(
                    st.getPlayerName(), st.getTeamName(),
                    st.getKills(), st.getDeaths(), st.getAssists(), playerPts));
            }
            com.wavedefense.network.packets.OpenPostMatchScoreboardPacket pkt =
                new com.wavedefense.network.packets.OpenPostMatchScoreboardPacket(
                    modeLabel,
                    champion == null ? "" : champion,
                    rowList,
                    new java.util.LinkedHashMap<>(state.getTeamWins()));
            for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
                com.wavedefense.network.PacketHandler.sendToPlayer(p, pkt);
            }
        }

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
        // E1 fix: clear dedup-set so penalties in a future session are counted correctly.
        pvpPenaltyDeducted.clear();
        // Same contract as the PvE path: anything the session spawned (trigger waves,
        // portal mobs) is persistenceRequired, so it has to leave the world before the
        // tracking set is dropped.
        wm.sessionMgr.despawnSessionMobs(location.getName());
        ctx.removeSession(location.getName());
        wm.brManager.clearLocation(location.getName());
        cleanupScoreboardTeams(location.getName());

        // ── Leaderboard: record top player per match ──────────────────────
        if (WaveDefenseMod.leaderboardManager != null) {
            String modeKey = switch (location.getPvpMode()) {
                case DEATHMATCH      -> com.wavedefense.data.LeaderboardManager.MODE_DEATHMATCH;
                case BATTLE_ROYALE   -> com.wavedefense.data.LeaderboardManager.MODE_BATTLE_ROYALE;
                case CAPTURE_THE_POINT -> com.wavedefense.data.LeaderboardManager.MODE_CTP;
                case KING_OF_THE_HILL  -> com.wavedefense.data.LeaderboardManager.MODE_KOTH;
                default              -> com.wavedefense.data.LeaderboardManager.MODE_STANDARD;
            };
            // C-1 fix: matchStartMs is recorded in PvpRoundState.startActiveRound()
            long matchStartMs = state.getMatchStartMs();
            int matchDurationSec = matchStartMs > 0
                ? Math.max(0, (int)((System.currentTimeMillis() - matchStartMs) / 1000)) : 0;
            for (Map.Entry<UUID, com.wavedefense.data.PvpPlayerStats> e : state.getAllStats().entrySet()) {
                com.wavedefense.data.PvpPlayerStats ps = e.getValue();
                int primary = location.isObjectiveMode()
                    ? state.getObjectiveScore(ps.getTeamName())
                    : location.getPlayerPoints(e.getKey());
                int secondary = ps.getKills();
                com.wavedefense.data.LeaderboardRecord rec =
                    new com.wavedefense.data.LeaderboardRecord(
                        e.getKey(), ps.getPlayerName(), primary, secondary,
                        matchDurationSec);
                WaveDefenseMod.leaderboardManager.addRecord(location.getName(), modeKey, rec);
            }
            WaveDefenseMod.leaderboardManager.saveToFile();
        }
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

        // v0.2.62: collect ready-player NAMES (not UUIDs) for client rendering
        java.util.Set<String> readyNames = new java.util.HashSet<>();
        for (UUID rid : state.getReadyPlayers()) {
            PvpPlayerStats ps = state.getStats(rid);
            if (ps != null) readyNames.add(ps.getPlayerName());
        }
        for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
            String myTeam = location.getPlayerTeam(p.getUUID());
            net.minecraft.nbt.CompoundTag tag = SyncPvpStatePacket.build(
                location.getName(), state.getPhase().name(),
                state.getCurrentRound(), state.getTotalRounds(), state.getTimerSeconds(),
                state.getTeamWins(), entries, myTeam, readyNames);
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
            }
            // Honour the config toggle. Hiding used to be hard-coded on team creation,
            // so the "hide enemy nametags" option did nothing — and, because it only ran
            // when the team was first created, flipping it later had no effect either.
            sbTeam.setNameTagVisibility(
                com.wavedefense.config.WaveDefenseConfig.PVP_HIDE_ENEMY_NAMETAGS.get()
                    ? net.minecraft.world.scores.Team.Visibility.HIDE_FOR_OTHER_TEAMS
                    : net.minecraft.world.scores.Team.Visibility.ALWAYS);
            // Remove from any previous WD team first
            net.minecraft.world.scores.PlayerTeam current = sb.getPlayersTeam(player.getScoreboardName());
            if (current != null) sb.removePlayerFromTeam(player.getScoreboardName(), current);
            sb.addPlayerToTeam(player.getScoreboardName(), sbTeam);
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.debug("[WaveDefense] Scoreboard team assign failed for {}: {}",
                player.getScoreboardName(), e.getMessage());
        }
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
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.debug("[WaveDefense] Scoreboard team removal failed for {}: {}",
                player.getScoreboardName(), e.getMessage());
        }
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
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.debug("[WaveDefense] Scoreboard cleanup failed for {}: {}",
                locationName, e.getMessage());
        }
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
