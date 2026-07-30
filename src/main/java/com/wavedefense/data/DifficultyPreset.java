package com.wavedefense.data;

/**
 * A fixed difficulty tier chosen by the admin per location.
 *
 * <p>This is a <em>static</em> multiplier applied at spawn time and is distinct from
 * {@link com.wavedefense.wave.WaveAutoScaler}, which adapts to how the current squad
 * is actually performing. The two compose: the preset sets the baseline the scaler
 * then nudges around, so "Nightmare" stays harder than "Easy" even after the scaler
 * has settled.
 *
 * <p>Point rewards scale with difficulty so that a harder run is also a faster way to
 * earn shop currency — otherwise nobody would pick anything above Normal.
 */
public enum DifficultyPreset {

    EASY     ("easy",      0.75, 0.75, 0.75, 0.75),
    NORMAL   ("normal",    1.00, 1.00, 1.00, 1.00),
    HARD     ("hard",      1.50, 1.25, 1.25, 1.50),
    NIGHTMARE("nightmare", 2.00, 1.75, 1.50, 2.00);

    private final String key;
    private final double healthMult;
    private final double damageMult;
    private final double countMult;
    private final double pointsMult;

    DifficultyPreset(String key, double healthMult, double damageMult,
                     double countMult, double pointsMult) {
        this.key        = key;
        this.healthMult = healthMult;
        this.damageMult = damageMult;
        this.countMult  = countMult;
        this.pointsMult = pointsMult;
    }

    public String getKey()        { return key; }
    public double getHealthMult() { return healthMult; }
    public double getDamageMult() { return damageMult; }
    public double getCountMult()  { return countMult; }
    public double getPointsMult() { return pointsMult; }

    /** Translation key for the tier name, e.g. {@code wavedefense.difficulty.hard}. */
    public String getDisplayKey() { return "wavedefense.difficulty." + key; }

    /**
     * Suffix appended to the leaderboard mode key so each tier ranks separately —
     * a Nightmare run should never be compared against an Easy one. Normal is
     * unsuffixed so that existing records stay where they are.
     */
    public String getLeaderboardSuffix() { return this == NORMAL ? "" : "_" + key; }

    /** Lenient parse; unknown or missing values fall back to {@link #NORMAL}. */
    public static DifficultyPreset fromString(String s) {
        if (s == null || s.isEmpty()) return NORMAL;
        for (DifficultyPreset d : values()) {
            if (d.key.equalsIgnoreCase(s) || d.name().equalsIgnoreCase(s)) return d;
        }
        return NORMAL;
    }

    /** Next tier in the cycle — used by the editor's click-to-cycle button. */
    public DifficultyPreset next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
