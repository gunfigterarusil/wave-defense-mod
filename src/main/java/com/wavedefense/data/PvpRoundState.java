package com.wavedefense.data;

import java.util.*;

/**
 * Стан PvP сесії для однієї локації.
 *
 * Фази:
 *   WAITING   — чекаємо мінімальну кількість гравців
 *   BUY       — час купівлі між раундами
 *   COUNTDOWN — підрахунок перед стартом раунду (BUY→ACTIVE)
 *   ACTIVE    — активний раунд
 *   ENDED     — всі раунди завершені
 */
public class PvpRoundState {

    public enum Phase { WAITING, BUY, COUNTDOWN, ACTIVE, ROUND_END_DELAY, ENDED }

    private Phase phase      = Phase.WAITING;
    private int currentRound = 0;
    private int totalRounds;
    private int buyTime;
    private int roundStartDelay = 5; // секунд між BUY→ACTIVE

    private int timerTicks = 0;

    private final Map<UUID, PvpPlayerStats>  stats          = new LinkedHashMap<>();
    private final Map<String, Integer>       teamWins       = new LinkedHashMap<>();
    private final Set<UUID>                  aliveThisRound = new HashSet<>();

    // Deathmatch: рахунок вбивств по командам за поточний раунд
    private final Map<String, Integer> dmTeamKills = new LinkedHashMap<>();
    private int dmKillsToWin = 10;

    public int  getDmKillsToWin()        { return dmKillsToWin; }
    public void setDmKillsToWin(int k)   { this.dmKillsToWin = Math.max(1, k); }
    public Map<String, Integer> getDmTeamKills() { return dmTeamKills; }
    public void recordDmKill(String killerTeam) { if (killerTeam != null) dmTeamKills.merge(killerTeam, 1, Integer::sum); }
    public String checkDmWinner() {
        if (dmKillsToWin <= 0) return null;
        return dmTeamKills.entrySet().stream()
            .filter(e -> e.getValue() >= dmKillsToWin).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }
    public void resetDmKills() { dmTeamKills.clear(); }

    /**
     * Battle Royale: перевіряємо чи залишився лише один живий гравець.
     * @return UUID останнього живого або null якщо ще більше одного
     */
    public java.util.UUID checkBrWinner() {
        if (aliveThisRound.size() == 1) return aliveThisRound.iterator().next();
        if (aliveThisRound.isEmpty()) return null;
        return null;
    }

    // Переможець очікує підтвердження (під час ROUND_END_DELAY)
    private String pendingWinner = null;

    public String getPendingWinner()           { return pendingWinner; }
    public void   setPendingWinner(String w)   { this.pendingWinner = w; }
    public void   clearPendingWinner()         { this.pendingWinner = null; }
    private final Map<UUID, Set<UUID>>       recentAttackers = new HashMap<>();

    public PvpRoundState(int totalRounds, int buyTime) {
        this.totalRounds = totalRounds;
        this.buyTime     = buyTime;
    }

    // ── Налаштування ────────────────────────────────────────────────────
    public int  getRoundStartDelay()      { return roundStartDelay; }
    public void setRoundStartDelay(int s) { this.roundStartDelay = Math.max(0, s); }

    // ── Гравці ──────────────────────────────────────────────────────────
    public void registerPlayer(UUID id, String name, String team) {
        stats.putIfAbsent(id, new PvpPlayerStats(name, team));
        teamWins.putIfAbsent(team, 0);
    }

    public void removePlayer(UUID id) {
        stats.remove(id);
        aliveThisRound.remove(id);
        recentAttackers.values().forEach(s -> s.remove(id));
    }

    public PvpPlayerStats getStats(UUID id)         { return stats.get(id); }
    public Map<UUID, PvpPlayerStats> getAllStats()   { return stats; }

    // ── Фаза і таймер ───────────────────────────────────────────────────
    public Phase getPhase()              { return phase; }
    public void  setPhase(Phase p)       { this.phase = p; }

    public int getCurrentRound()         { return currentRound; }
    public int getTotalRounds()          { return totalRounds; }

    public int  getTimerTicks()          { return timerTicks; }
    public int  getTimerSeconds()        { return timerTicks / 20; }
    public void setTimerTicks(int t)     { this.timerTicks = t; }
    public void tickDown()               { if (timerTicks > 0) timerTicks--; }

    /** BUY фаза перед раундом */
    public void startBuyPhase() {
        currentRound++;
        phase      = Phase.BUY;
        timerTicks = buyTime * 20;
    }

    /** ROUND_END_DELAY — пауза після перемоги команди перед BUY (щоб гравці встигли відродитись) */
    public void startRoundEndDelay(int delaySec) {
        phase      = Phase.ROUND_END_DELAY;
        timerTicks = delaySec * 20;
    }

    /** COUNTDOWN фаза (підрахунок між BUY і ACTIVE) */
    public void startCountdown(int delaySec) {
        phase      = Phase.COUNTDOWN;
        timerTicks = delaySec * 20;
    }

    /** ACTIVE раунд — відновлюємо список живих */
    public void startActiveRound(Set<UUID> allPlayers) {
        phase = Phase.ACTIVE;
        aliveThisRound.clear();
        aliveThisRound.addAll(allPlayers);
        recentAttackers.clear();
    }

    public boolean isAllRoundsDone() { return currentRound >= totalRounds; }

    // ── Смерті та асисти ────────────────────────────────────────────────
    public String recordDeath(UUID victimId, UUID killerId) {
        aliveThisRound.remove(victimId);

        Set<UUID> attackers = recentAttackers.getOrDefault(victimId, Collections.emptySet());
        for (UUID a : attackers) {
            if (!a.equals(killerId)) {
                PvpPlayerStats as = stats.get(a);
                if (as != null) as.addAssist();
            }
        }
        recentAttackers.remove(victimId);

        if (killerId != null) {
            PvpPlayerStats ks = stats.get(killerId);
            if (ks != null) ks.addKill();
        }
        PvpPlayerStats vs = stats.get(victimId);
        if (vs != null) vs.addDeath();

        return checkRoundWinner();
    }

    public void recordHit(UUID attacker, UUID victim) {
        recentAttackers.computeIfAbsent(victim, k -> new HashSet<>()).add(attacker);
    }

    public String checkRoundWinner() {
        if (aliveThisRound.isEmpty()) return null;
        String firstTeam = null;
        for (UUID id : aliveThisRound) {
            PvpPlayerStats ps = stats.get(id);
            if (ps == null) continue;
            if (firstTeam == null) {
                firstTeam = ps.getTeamName();
            } else if (!firstTeam.equals(ps.getTeamName())) {
                return null;
            }
        }
        return firstTeam;
    }

    public void recordTeamWin(String teamName) {
        teamWins.merge(teamName, 1, Integer::sum);
        phase = Phase.ENDED;
    }

    public Map<String, Integer> getTeamWins()  { return teamWins; }
    public Set<UUID> getAliveThisRound()        { return aliveThisRound; }
}
