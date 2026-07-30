package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A player's lifetime record across every location on the server.
 *
 * <p>Distinct from {@link PlayerStats}, which is scoped to a single session and dies
 * with it. This is the thing that makes a second run feel like it builds on the first:
 * it survives restarts, accumulates across locations, and drives the displayed level.
 *
 * <p>Levelling is deliberately quadratic — {@code level = 1 + floor(sqrt(xp / 100))} —
 * so early levels arrive quickly enough to notice while later ones stay meaningful.
 * XP is awarded for the things the mod actually wants to reward: surviving waves,
 * killing things, and finishing runs.
 */
public class PlayerProfile {

    /** XP granted per wave survived. */
    public static final int XP_PER_WAVE    = 10;
    /** XP granted per mob killed. */
    public static final int XP_PER_KILL    = 1;
    /** Bonus XP for completing a location. */
    public static final int XP_PER_VICTORY = 100;

    /** XP required for level 2; the curve is this value times {@code (level-1)^2}. */
    private static final int XP_CURVE_BASE = 100;

    private final UUID uuid;
    private String playerName;

    private int  totalWaves;      // waves survived, all runs
    private int  bestWave;        // furthest wave reached in a single run
    private int  totalKills;
    private int  totalPoints;
    private int  matchesPlayed;
    private int  matchesWon;
    private int  deaths;
    private long playtimeSec;
    private int  xp;

    public PlayerProfile(UUID uuid, String playerName) {
        this.uuid       = uuid;
        this.playerName = playerName != null ? playerName : "Unknown";
    }

    // ── Accumulation ───────────────────────────────────────────────────────

    /** Records one completed wave, tracking the personal best along the way. */
    public void recordWaveCompleted(int waveNumber) {
        totalWaves++;
        if (waveNumber > bestWave) bestWave = waveNumber;
        xp += XP_PER_WAVE;
    }

    public void recordKills(int count) {
        if (count <= 0) return;
        totalKills += count;
        xp += count * XP_PER_KILL;
    }

    public void recordPoints(int points) {
        if (points > 0) totalPoints += points;
    }

    /**
     * Closes out a run.
     *
     * @param won      whether the player reached victory rather than dying or leaving
     * @param duration how long the run lasted, in seconds
     */
    public void recordMatchEnd(boolean won, long duration) {
        matchesPlayed++;
        if (won) {
            matchesWon++;
            xp += XP_PER_VICTORY;
        }
        if (duration > 0) playtimeSec += duration;
    }

    public void recordDeath() { deaths++; }

    // ── Derived ────────────────────────────────────────────────────────────

    /** Current level, starting at 1. */
    public int getLevel() {
        return 1 + (int) Math.floor(Math.sqrt((double) xp / XP_CURVE_BASE));
    }

    /** Total XP needed to have reached {@code level}. */
    public static int xpForLevel(int level) {
        if (level <= 1) return 0;
        return (level - 1) * (level - 1) * XP_CURVE_BASE;
    }

    /** XP still needed to reach the next level. */
    public int getXpToNextLevel() {
        return Math.max(0, xpForLevel(getLevel() + 1) - xp);
    }

    /** Progress through the current level, 0.0–1.0 — for rendering a progress bar. */
    public float getLevelProgress() {
        int cur  = xpForLevel(getLevel());
        int next = xpForLevel(getLevel() + 1);
        if (next <= cur) return 0f;
        return Math.max(0f, Math.min(1f, (float) (xp - cur) / (next - cur)));
    }

    /** Win rate as a percentage, 0 when nothing has been played yet. */
    public int getWinRatePercent() {
        return matchesPlayed == 0 ? 0 : (int) Math.round(100.0 * matchesWon / matchesPlayed);
    }

    // ── Getters / setters ──────────────────────────────────────────────────

    public UUID   getUuid()          { return uuid; }
    public String getPlayerName()    { return playerName; }
    public void   setPlayerName(String n) { if (n != null && !n.isEmpty()) this.playerName = n; }
    public int    getTotalWaves()    { return totalWaves; }
    public int    getBestWave()      { return bestWave; }
    public int    getTotalKills()    { return totalKills; }
    public int    getTotalPoints()   { return totalPoints; }
    public int    getMatchesPlayed() { return matchesPlayed; }
    public int    getMatchesWon()    { return matchesWon; }
    public int    getDeaths()        { return deaths; }
    public long   getPlaytimeSec()   { return playtimeSec; }
    public int    getXp()            { return xp; }

    // ── NBT ────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid",           uuid);
        tag.putString("name",         playerName);
        tag.putInt("totalWaves",      totalWaves);
        tag.putInt("bestWave",        bestWave);
        tag.putInt("totalKills",      totalKills);
        tag.putInt("totalPoints",     totalPoints);
        tag.putInt("matchesPlayed",   matchesPlayed);
        tag.putInt("matchesWon",      matchesWon);
        tag.putInt("deaths",          deaths);
        tag.putLong("playtimeSec",    playtimeSec);
        tag.putInt("xp",              xp);
        return tag;
    }

    public static PlayerProfile load(CompoundTag tag) {
        if (!tag.hasUUID("uuid")) return null;
        PlayerProfile p = new PlayerProfile(tag.getUUID("uuid"), tag.getString("name"));
        p.totalWaves    = tag.getInt("totalWaves");
        p.bestWave      = tag.getInt("bestWave");
        p.totalKills    = tag.getInt("totalKills");
        p.totalPoints   = tag.getInt("totalPoints");
        p.matchesPlayed = tag.getInt("matchesPlayed");
        p.matchesWon    = tag.getInt("matchesWon");
        p.deaths        = tag.getInt("deaths");
        p.playtimeSec   = tag.getLong("playtimeSec");
        p.xp            = tag.getInt("xp");
        return p;
    }
}
