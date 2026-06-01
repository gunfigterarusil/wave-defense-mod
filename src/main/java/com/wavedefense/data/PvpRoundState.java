package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PvP session state for a single location.
 *
 * Phases:
 *   WAITING         — waiting for the minimum number of players
 *   BUY             — buy phase between rounds
 *   COUNTDOWN       — countdown before round start (BUY → ACTIVE)
 *   ACTIVE          — active round
 *   ROUND_END_DELAY — brief delay at round end before the next phase
 *   ENDED           — all rounds completed
 */
public class PvpRoundState {

    public enum Phase { WAITING, BUY, COUNTDOWN, ACTIVE, ROUND_END_DELAY, ENDED }

    private Phase phase      = Phase.WAITING;
    private int currentRound = 0;
    private int totalRounds;
    private int buyTime;
    // B1 fix: roundStartDelay was a dead field (never set from Location, never read at runtime).
    // The delay is always read directly from Location.getPvpRoundStartDelay() in PvpRoundManager.

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

    // ── Capture the Point / King of the Hill ────────────────────────────
    /** pointId → owning teamName (null = neutral) */
    private final Map<String, String>  pointOwners       = new LinkedHashMap<>();
    /** pointId → signed capture-tick progress (magnitude 0..capTicks).
     *  The sign convention is determined by {@code pointCapturingTeam}:
     *  positive = {@code pointCapturingTeam[id]} is advancing,
     *  negative = opponent is neutralising that team's progress. */
    private final Map<String, Integer> captureProgress   = new LinkedHashMap<>();
    /** pointId → which team "owns" the positive direction on this point.
     *  Set the first time a team starts capturing from neutral; cleared
     *  when the point is captured or progress returns to 0. */
    private final Map<String, String>  pointCapturingTeam = new LinkedHashMap<>();
    /** teamName → accumulated objective score */
    private final Map<String, Integer> objectiveScore    = new LinkedHashMap<>();
    /** Ticks remaining in timer-mode round (0 when over) */
    private int roundDurationTicks = 0;
    /** KotH hold-timer mode: teamName → accumulated ticks held on the hill */
    private final Map<String, Integer> kothHoldTicks = new LinkedHashMap<>();
    public Map<String, Integer> getKothHoldTicks()              { return kothHoldTicks; }
    public int getKothHoldTicks(String team)                    { return team == null ? 0 : kothHoldTicks.getOrDefault(team, 0); }
    public void addKothHoldTicks(String team, int delta)        { if (team != null) kothHoldTicks.merge(team, delta, Integer::sum); }
    public void resetKothHoldTicks(String team)                 { if (team != null) kothHoldTicks.put(team, 0); }
    /** epoch-ms when the first ACTIVE round of this match began (for leaderboard duration). */
    private long matchStartMs = 0L;

    /**
     * Initialises capture-point tracking for a new active round.
     * @param points         the capture point definitions from the location
     * @param roundDurationSec  round duration in seconds (ignored in first-to-score mode; still stored)
     */
    public void initCapturePoints(List<CapturePoint> points, int roundDurationSec) {
        pointOwners.clear();
        captureProgress.clear();
        pointCapturingTeam.clear();
        objectiveScore.clear();
        kothHoldTicks.clear();
        for (CapturePoint cp : points) {
            pointOwners.put(cp.getId(), null);     // neutral
            captureProgress.put(cp.getId(), 0);
        }
        roundDurationTicks = roundDurationSec * 20;
    }

    public Map<String, String>  getPointOwners()       { return pointOwners; }
    public Map<String, Integer> getCaptureProgress()   { return captureProgress; }
    public Map<String, String>  getPointCapturingTeam(){ return pointCapturingTeam; }
    public Map<String, Integer> getObjectiveScore()    { return objectiveScore; }
    public int  getRoundDurationTicks()                { return roundDurationTicks; }
    public void setRoundDurationTicks(int t)           { this.roundDurationTicks = t; }
    public long getMatchStartMs()                      { return matchStartMs; }
    public void setMatchStartMs(long t)                { this.matchStartMs = t; }

    public String getPointOwner(String pointId) { return pointOwners.get(pointId); }

    /**
     * Advances capture progress for the given point toward {@code capTicks} for {@code team}.
     * Progress is signed: positive = capturer is team "team1", negative = team "team2".
     * When the magnitude reaches {@code capTicks} the point flips ownership.
     *
     * @param pointId   capture point id
     * @param delta     +1 or -1 per tick (sign encodes which side is advancing)
     * @param capTicks  captureTimeSec * 20
     * @param team      the team currently standing on the point uncontested
     * @return the new owner team name if the point just flipped, null otherwise
     */
    public String advanceCaptureProgress(String pointId, int delta, int capTicks, String team) {
        int current = captureProgress.getOrDefault(pointId, 0);
        current += delta;
        if (Math.abs(current) >= capTicks) {
            pointOwners.put(pointId, team);
            captureProgress.put(pointId, 0);
            return team;
        }
        captureProgress.put(pointId, current);
        return null;
    }

    public void addObjectiveScore(String teamName, int amount) {
        if (teamName == null) return;
        objectiveScore.merge(teamName, amount, Integer::sum);
    }

    public int getObjectiveScore(String teamName) {
        return teamName == null ? 0 : objectiveScore.getOrDefault(teamName, 0);
    }

    /** Returns the winning team if any team has reached {@code scoreToWin}, else null. */
    public String checkObjectiveWinner(int scoreToWin) {
        for (Map.Entry<String, Integer> e : objectiveScore.entrySet()) {
            if (e.getValue() >= scoreToWin) return e.getKey();
        }
        return null;
    }

    /** Returns the team with the highest objective score (for timer-end mode). null if tie/empty. */
    public String getLeadingTeam() {
        if (objectiveScore.isEmpty()) return null;
        String leader = null;
        int best = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : objectiveScore.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); leader = e.getKey(); tie = false; }
            else if (e.getValue() == best) { tie = true; }
        }
        return tie ? null : leader;
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

    // ── Deathmatch helpers ──────────────────────────────────────────────
    /**
     * DM: advance to round 1 without going through the BUY phase.
     * Called from checkPvpStart() when the mode is DEATHMATCH so the match
     * starts immediately (the BUY / shop phase is skipped entirely).
     */
    public void markFirstRound() { this.currentRound = 1; }

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
        // H1 fix: reset DM kill counters so they don't carry over from previous rounds
        dmTeamKills.clear();
        // C-1 fix: record match start time on the very first round only
        if (matchStartMs == 0L) matchStartMs = System.currentTimeMillis();
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
        // C1 fix: do NOT set phase = ENDED here — endRound() / endPvpMatch() owns
        // the phase transition. Setting ENDED prematurely creates a 1-tick window
        // where the state machine is in ENDED but startBuyPhase() hasn't run yet.
        // Null = draw (no team recorded as winner).
        if (teamName != null && !teamName.isBlank()) {
            teamWins.merge(teamName, 1, Integer::sum);
        }
    }

    public Map<String, Integer> getTeamWins()  { return teamWins; }
    public Set<UUID> getAliveThisRound()        { return aliveThisRound; }

    // ──────────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ──────────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putInt("currentRound", currentRound);
        tag.putInt("totalRounds", totalRounds);
        tag.putInt("buyTime", buyTime);
        tag.putInt("timerTicks", timerTicks);
        tag.putInt("dmKillsToWin", dmKillsToWin);
        if (pendingWinner != null) {
            tag.putString("pendingWinner", pendingWinner);
        }
        // Save stats
        ListTag statsList = new ListTag();
        for (Map.Entry<UUID, PvpPlayerStats> entry : stats.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("uuid", entry.getKey());
            entryTag.put("stats", entry.getValue().save());
            statsList.add(entryTag);
        }
        tag.put("stats", statsList);
        // Save team wins
        CompoundTag teamWinsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : teamWins.entrySet()) {
            teamWinsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("teamWins", teamWinsTag);
        // Save alive this round
        ListTag aliveList = new ListTag();
        for (UUID uuid : aliveThisRound) {
            aliveList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("aliveThisRound", aliveList);
        // Save DM team kills
        CompoundTag dmKillsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : dmTeamKills.entrySet()) {
            dmKillsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("dmTeamKills", dmKillsTag);
        // Save CtP/KotH state
        tag.putLong("matchStartMs", matchStartMs);
        if (!pointCapturingTeam.isEmpty()) {
            CompoundTag capTeamTag = new CompoundTag();
            for (Map.Entry<String, String> e : pointCapturingTeam.entrySet()) {
                capTeamTag.putString(e.getKey(), e.getValue());
            }
            tag.put("pointCapturingTeam", capTeamTag);
        }
        if (!objectiveScore.isEmpty()) {
            CompoundTag objTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : objectiveScore.entrySet()) objTag.putInt(e.getKey(), e.getValue());
            tag.put("objectiveScore", objTag);
        }
        if (!pointOwners.isEmpty()) {
            CompoundTag ownTag = new CompoundTag();
            for (Map.Entry<String, String> e : pointOwners.entrySet()) {
                ownTag.putString(e.getKey(), e.getValue() != null ? e.getValue() : "");
            }
            tag.put("pointOwners", ownTag);
        }
        if (!captureProgress.isEmpty()) {
            CompoundTag progTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : captureProgress.entrySet()) progTag.putInt(e.getKey(), e.getValue());
            tag.put("captureProgress", progTag);
        }
        tag.putInt("roundDurationTicks", roundDurationTicks);
        if (!kothHoldTicks.isEmpty()) {
            CompoundTag kothTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : kothHoldTicks.entrySet()) kothTag.putInt(e.getKey(), e.getValue());
            tag.put("kothHoldTicks", kothTag);
        }
        return tag;
    }

    /**
     * Статичний метод для завантаження стану з NBT.
     */
    public static PvpRoundState load(CompoundTag tag) {
        PvpRoundState state = new PvpRoundState(
            tag.getInt("totalRounds"),
            tag.getInt("buyTime")
        );
        try { state.phase = Phase.valueOf(tag.getString("phase")); }
        catch (IllegalArgumentException ignored) { state.phase = Phase.WAITING; }
        state.currentRound = tag.getInt("currentRound");
        state.timerTicks = tag.getInt("timerTicks");
        state.dmKillsToWin = tag.getInt("dmKillsToWin");
        state.pendingWinner = tag.contains("pendingWinner") ? tag.getString("pendingWinner") : null;
        // Load stats
        if (tag.contains("stats")) {
            ListTag statsList = tag.getList("stats", 10);
            for (int i = 0; i < statsList.size(); i++) {
                CompoundTag entryTag = statsList.getCompound(i);
                UUID uuid = entryTag.getUUID("uuid");
                PvpPlayerStats pps = new PvpPlayerStats();
                pps.load(entryTag.getCompound("stats"));
                state.stats.put(uuid, pps);
            }
        }
        // Load team wins
        if (tag.contains("teamWins")) {
            CompoundTag teamWinsTag = tag.getCompound("teamWins");
            for (String key : teamWinsTag.getAllKeys()) {
                state.teamWins.put(key, teamWinsTag.getInt(key));
            }
        }
        // Load alive this round
        if (tag.contains("aliveThisRound")) {
            ListTag aliveList = tag.getList("aliveThisRound", 8);
            for (int i = 0; i < aliveList.size(); i++) {
                state.aliveThisRound.add(UUID.fromString(aliveList.getString(i)));
            }
        }
        // Load DM team kills
        if (tag.contains("dmTeamKills")) {
            CompoundTag dmKillsTag = tag.getCompound("dmTeamKills");
            for (String key : dmKillsTag.getAllKeys()) {
                state.dmTeamKills.put(key, dmKillsTag.getInt(key));
            }
        }
        // Load CtP/KotH state
        state.matchStartMs = NbtHelper.getLong(tag, "matchStartMs", 0L);
        if (tag.contains("pointCapturingTeam")) {
            CompoundTag capTeamTag = tag.getCompound("pointCapturingTeam");
            for (String key : capTeamTag.getAllKeys()) {
                state.pointCapturingTeam.put(key, capTeamTag.getString(key));
            }
        }
        if (tag.contains("objectiveScore")) {
            CompoundTag objTag = tag.getCompound("objectiveScore");
            for (String key : objTag.getAllKeys()) state.objectiveScore.put(key, objTag.getInt(key));
        }
        if (tag.contains("pointOwners")) {
            CompoundTag ownTag = tag.getCompound("pointOwners");
            for (String key : ownTag.getAllKeys()) {
                String val = ownTag.getString(key);
                state.pointOwners.put(key, val.isEmpty() ? null : val);
            }
        }
        if (tag.contains("captureProgress")) {
            CompoundTag progTag = tag.getCompound("captureProgress");
            for (String key : progTag.getAllKeys()) state.captureProgress.put(key, progTag.getInt(key));
        }
        state.roundDurationTicks = NbtHelper.getInt(tag, "roundDurationTicks", 0);
        if (tag.contains("kothHoldTicks")) {
            CompoundTag kothTag = tag.getCompound("kothHoldTicks");
            for (String key : kothTag.getAllKeys()) state.kothHoldTicks.put(key, kothTag.getInt(key));
        }
        return state;
    }
}
