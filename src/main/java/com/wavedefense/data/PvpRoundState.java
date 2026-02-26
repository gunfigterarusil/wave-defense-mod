package com.wavedefense.data;

import java.util.*;

/**
 * Стан PvP сесії для однієї локації.
 * Зберігається в WaveManager за назвою локації.
 *
 * Фази:
 *   WAITING   — чекаємо мінімальну кількість гравців
 *   BUY       — час купівлі між раундами (гравці-спектатори відроджуються)
 *   ACTIVE    — активний раунд (гравці б'ються)
 *   ENDED     — всі раунди завершені
 */
public class PvpRoundState {

    public enum Phase { WAITING, BUY, ACTIVE, ENDED }

    // Основний стан
    private Phase phase       = Phase.WAITING;
    private int currentRound  = 0;   // 1-based
    private int totalRounds;
    private int buyTime;             // секунд

    // Таймер (у тіках сервера, 20 = 1 сек)
    private int timerTicks    = 0;   // відраховує вниз

    // Статистика гравців (UUID → stats)
    private final Map<UUID, PvpPlayerStats> stats = new LinkedHashMap<>();

    // Перемоги команд: teamName → wins
    private final Map<String, Integer> teamWins = new LinkedHashMap<>();

    // Живі гравці поточного раунду (UUID → teamName); при смерті видаляється
    private final Set<UUID> aliveThisRound = new HashSet<>();

    // Останні атакуючі (для асистів): victimUUID → set of attackers
    private final Map<UUID, Set<UUID>> recentAttackers = new HashMap<>();

    public PvpRoundState(int totalRounds, int buyTime) {
        this.totalRounds = totalRounds;
        this.buyTime     = buyTime;
    }

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

    public PvpPlayerStats getStats(UUID id) { return stats.get(id); }
    public Map<UUID, PvpPlayerStats> getAllStats() { return stats; }

    // ── Раунди ──────────────────────────────────────────────────────────

    public Phase getPhase() { return phase; }
    public void setPhase(Phase p) { this.phase = p; }

    public int getCurrentRound() { return currentRound; }
    public int getTotalRounds()  { return totalRounds; }

    public int getTimerTicks()   { return timerTicks; }
    public int getTimerSeconds() { return timerTicks / 20; }
    public void setTimerTicks(int t) { this.timerTicks = t; }
    public void tickDown() { if (timerTicks > 0) timerTicks--; }

    /** Починаємо нову фазу BUY перед раундом */
    public void startBuyPhase() {
        currentRound++;
        phase    = Phase.BUY;
        timerTicks = buyTime * 20;
    }

    /** Починаємо ACTIVE раунд (відновлюємо aliveThisRound) */
    public void startActiveRound(Set<UUID> allPlayers) {
        phase = Phase.ACTIVE;
        aliveThisRound.clear();
        aliveThisRound.addAll(allPlayers);
        recentAttackers.clear();
    }

    public boolean isAllRoundsDone() { return currentRound >= totalRounds; }

    // ── Смерті у раунді ─────────────────────────────────────────────────

    /**
     * Реєструє смерть гравця. Повертає teamName переможця якщо раунд завершено, інакше null.
     */
    public String recordDeath(UUID victimId, UUID killerId) {
        aliveThisRound.remove(victimId);

        // Асисти: всі недавні атакуючі крім вбивці
        Set<UUID> attackers = recentAttackers.getOrDefault(victimId, Collections.emptySet());
        for (UUID a : attackers) {
            if (!a.equals(killerId)) {
                PvpPlayerStats as = stats.get(a);
                if (as != null) as.addAssist();
            }
        }
        recentAttackers.remove(victimId);

        // Кіл вбивці
        if (killerId != null) {
            PvpPlayerStats ks = stats.get(killerId);
            if (ks != null) ks.addKill();
        }
        // Смерть жертви
        PvpPlayerStats vs = stats.get(victimId);
        if (vs != null) vs.addDeath();

        return checkRoundWinner();
    }

    /** Реєструє удар для відслідковування асистів */
    public void recordHit(UUID attacker, UUID victim) {
        recentAttackers.computeIfAbsent(victim, k -> new HashSet<>()).add(attacker);
    }

    /**
     * Перевіряє чи залишилась одна команда живою.
     * @return teamName переможця або null якщо раунд ще триває
     */
    public String checkRoundWinner() {
        if (aliveThisRound.isEmpty()) return null;

        String firstTeam = null;
        for (UUID id : aliveThisRound) {
            PvpPlayerStats ps = stats.get(id);
            if (ps == null) continue;
            if (firstTeam == null) {
                firstTeam = ps.getTeamName();
            } else if (!firstTeam.equals(ps.getTeamName())) {
                return null; // ще є гравці з різних команд
            }
        }
        return firstTeam; // всі живі — з однієї команди → вона виграла
    }

    public void recordTeamWin(String teamName) {
        teamWins.merge(teamName, 1, Integer::sum);
        phase = Phase.ENDED; // тимчасово, до наступного BUY
    }

    public Map<String, Integer> getTeamWins() { return teamWins; }
    public Set<UUID> getAliveThisRound() { return aliveThisRound; }
}
