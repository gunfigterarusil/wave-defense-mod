package com.wavedefense.wave;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Автоматичне масштабування складності хвиль на основі продуктивності гравців.
 * <p>Клас відповідає за динамічну зміну складності гри, щоб зробити її цікавою
 * як для нових, так і для досвідчених гравців.
 */
public class WaveAutoScaler {

    private static final int PERFORMANCE_WINDOW_SIZE = 5;
    private static final int LONG_TERM_WINDOW_SIZE = 20;
    private static final double MIN_DIFFICULTY = 0.1;
    private static final double MAX_DIFFICULTY = 5.0;
    private static final double BASE_DIFFICULTY = 1.0;
    private static final double SHORT_TERM_ALPHA = 0.3;
    private static final double TARGET_PERFORMANCE_SCORE = 100.0;
    private static final double PERFECT_PERFORMANCE_THRESHOLD = 150.0;
    private static final double CRITICAL_THRESHOLD = 40.0;
    private static final int MAX_CONSECUTIVE_DIFFICULTY_INCREASES = 3;
    private static final int MAX_CONSECUTIVE_DIFFICULTY_DECREASES = 2;
    private static final double MAX_SINGLE_ADJUSTMENT = 0.25;
    private static final double MIN_SINGLE_ADJUSTMENT = 0.02;

    private final Deque<WavePerformance> recentPerformances;
    private final Deque<WavePerformance> longTermPerformances;
    private double currentDifficulty;
    private double smoothedDifficulty;
    private int consecutiveIncreases;
    private int consecutiveDecreases;
    private int currentWaveNumber;
    private WaveMetrics currentWaveMetrics;

    public static class WavePerformance {
        public final int waveNumber;
        public final double performanceScore;
        public final double accuracy;
        public final double damageTaken;
        public final double healthRemaining;
        public final double timeSurvived;
        public final double enemiesKilled;
        public final double totalEnemies;
        public final double difficultyMultiplier;

        public WavePerformance(int waveNumber, double performanceScore, double accuracy,
                               double damageTaken, double healthRemaining, double timeSurvived,
                               double enemiesKilled, double totalEnemies, double difficultyMultiplier) {
            this.waveNumber = waveNumber;
            this.performanceScore = performanceScore;
            this.accuracy = accuracy;
            this.damageTaken = damageTaken;
            this.healthRemaining = healthRemaining;
            this.timeSurvived = timeSurvived;
            this.enemiesKilled = enemiesKilled;
            this.totalEnemies = totalEnemies;
            this.difficultyMultiplier = difficultyMultiplier;
        }
    }

    public static class WaveMetrics {
        public int enemiesKilled;
        public int totalEnemies;
        public double damageTaken;
        public double healthRemaining;
        public double startTime;
        public int shotsFired;
        public int shotsHit;
        public double score;

        public WaveMetrics(double health) {
            this.enemiesKilled = 0;
            this.totalEnemies = 0;
            this.damageTaken = 0.0;
            this.healthRemaining = health;
            this.startTime = System.currentTimeMillis() / 1000.0;
            this.shotsFired = 0;
            this.shotsHit = 0;
            this.score = 0.0;
        }

        public double getAccuracy() {
            return shotsFired > 0 ? (double) shotsHit / shotsFired : 0.0;
        }

        public double getTimeSurvived() {
            return System.currentTimeMillis() / 1000.0 - startTime;
        }

        public void recordShot(boolean hit) {
            shotsFired++;
            if (hit) shotsHit++;
        }

        public void addDamage(double damage) {
            damageTaken += damage;
            healthRemaining -= damage;
            if (healthRemaining < 0) healthRemaining = 0;
        }

        public void addScore(double points) {
            score += points;
        }
    }

    public static class DifficultyAdjustment {
        public final double newDifficulty;
        public final double oldDifficulty;
        public final double adjustmentFactor;
        public final String reason;
        public final double performanceScore;
        public final boolean isAntiFrustration;
        public final boolean isAntiBoredom;

        public DifficultyAdjustment(double oldDifficulty, double newDifficulty,
                                    double adjustmentFactor, String reason,
                                    double performanceScore, boolean isAntiFrustration,
                                    boolean isAntiBoredom) {
            this.oldDifficulty = oldDifficulty;
            this.newDifficulty = newDifficulty;
            this.adjustmentFactor = adjustmentFactor;
            this.reason = reason;
            this.performanceScore = performanceScore;
            this.isAntiFrustration = isAntiFrustration;
            this.isAntiBoredom = isAntiBoredom;
        }

        public String toString() {
            return String.format(
                "DifficultyAdjustment{old=%.2f, new=%.2f, factor=%.3f, reason='%s', performance=%.1f, antiFrustration=%b, antiBoredom=%b}",
                oldDifficulty, newDifficulty, adjustmentFactor, reason, performanceScore, isAntiFrustration, isAntiBoredom
            );
        }
    }

    public WaveAutoScaler() {
        this(BASE_DIFFICULTY);
    }

    public WaveAutoScaler(double initialDifficulty) {
        this.currentDifficulty = clamp(initialDifficulty, MIN_DIFFICULTY, MAX_DIFFICULTY);
        this.smoothedDifficulty = this.currentDifficulty;
        this.recentPerformances = new ArrayDeque<>();
        this.longTermPerformances = new ArrayDeque<>();
        this.consecutiveIncreases = 0;
        this.consecutiveDecreases = 0;
        this.currentWaveNumber = 0;
        this.currentWaveMetrics = null;
    }

    public WaveMetrics startWave(int waveNumber, double playerHealth) {
        this.currentWaveNumber = waveNumber;
        this.currentWaveMetrics = new WaveMetrics(playerHealth);
        return this.currentWaveMetrics;
    }

    public DifficultyAdjustment recordWaveCompletion(WaveMetrics waveMetrics) {
        if (waveMetrics == null) {
            throw new IllegalArgumentException("Wave metrics cannot be null");
        }

        double performanceScore = calculatePerformanceScore(waveMetrics);

        WavePerformance performance = new WavePerformance(
            currentWaveNumber, performanceScore, waveMetrics.getAccuracy(),
            waveMetrics.damageTaken, waveMetrics.healthRemaining,
            waveMetrics.getTimeSurvived(), waveMetrics.enemiesKilled,
            waveMetrics.totalEnemies, currentDifficulty
        );

        addPerformanceRecord(performance);
        DifficultyAdjustment adjustment = calculateDifficultyAdjustment(performance);

        currentDifficulty = adjustment.newDifficulty;
        smoothedDifficulty = applySmoothing(adjustment.newDifficulty);
        updateConsecutiveCounters(adjustment.adjustmentFactor);

        return adjustment;
    }

    // ---- internal calculation methods ----

    private double calculatePerformanceScore(WaveMetrics metrics) {
        double killRatio = metrics.totalEnemies > 0 ? (double) metrics.enemiesKilled / metrics.totalEnemies : 0.0;
        double totalHealth = metrics.damageTaken + metrics.healthRemaining;
        double healthRatio = totalHealth > 0 ? metrics.healthRemaining / totalHealth : 0.0;
        double accuracy = metrics.getAccuracy();
        double timeSurvived = metrics.getTimeSurvived();
        double timeScore = Math.max(0, 1.0 - (timeSurvived / 120.0));

        double score = (killRatio * 50.0) + (healthRatio * 30.0) + (accuracy * 20.0) + (timeScore * 10.0);
        return Math.max(0, Math.min(score * 100, 200));
    }

    private void addPerformanceRecord(WavePerformance performance) {
        recentPerformances.addLast(performance);
        longTermPerformances.addLast(performance);
        while (recentPerformances.size() > PERFORMANCE_WINDOW_SIZE) {
            recentPerformances.pollFirst();
        }
        while (longTermPerformances.size() > LONG_TERM_WINDOW_SIZE) {
            longTermPerformances.pollFirst();
        }
    }

    private DifficultyAdjustment calculateDifficultyAdjustment(WavePerformance performance) {
        double performanceScore = performance.performanceScore;
        double oldDifficulty = currentDifficulty;

        double adjustmentFactor = 0.0;
        boolean isAntiFrustration = false;
        boolean isAntiBoredom = false;

        if (performanceScore < CRITICAL_THRESHOLD) {
            adjustmentFactor = -MAX_SINGLE_ADJUSTMENT;
            isAntiFrustration = true;
        } else if (performanceScore > PERFECT_PERFORMANCE_THRESHOLD) {
            adjustmentFactor = MAX_SINGLE_ADJUSTMENT;
            isAntiBoredom = true;
        } else {
            double error = (performanceScore - TARGET_PERFORMANCE_SCORE) / TARGET_PERFORMANCE_SCORE;
            adjustmentFactor = -error * MAX_SINGLE_ADJUSTMENT;
            adjustmentFactor = Math.max(-MAX_SINGLE_ADJUSTMENT, Math.min(MAX_SINGLE_ADJUSTMENT, adjustmentFactor));
        }

        if (adjustmentFactor > 0 && consecutiveIncreases >= MAX_CONSECUTIVE_DIFFICULTY_INCREASES) {
            adjustmentFactor = 0;
        } else if (adjustmentFactor < 0 && consecutiveDecreases >= MAX_CONSECUTIVE_DIFFICULTY_DECREASES) {
            adjustmentFactor = 0;
        }

        if (Math.abs(adjustmentFactor) < MIN_SINGLE_ADJUSTMENT && adjustmentFactor != 0) {
            adjustmentFactor = adjustmentFactor > 0 ? MIN_SINGLE_ADJUSTMENT : -MIN_SINGLE_ADJUSTMENT;
        }

        double newDifficulty = clamp(currentDifficulty * (1.0 + adjustmentFactor), MIN_DIFFICULTY, MAX_DIFFICULTY);
        String reason = "Performance score: " + String.format("%.1f", performanceScore);

        return new DifficultyAdjustment(oldDifficulty, newDifficulty, adjustmentFactor, reason, performanceScore, isAntiFrustration, isAntiBoredom);
    }

    private double applySmoothing(double newDifficulty) {
        return smoothedDifficulty * (1.0 - SHORT_TERM_ALPHA) + newDifficulty * SHORT_TERM_ALPHA;
    }

    private void updateConsecutiveCounters(double adjustmentFactor) {
        if (adjustmentFactor > 0) {
            consecutiveIncreases++;
            consecutiveDecreases = 0;
        } else if (adjustmentFactor < 0) {
            consecutiveDecreases++;
            consecutiveIncreases = 0;
        } else {
            consecutiveIncreases = 0;
            consecutiveDecreases = 0;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- NBT serialization ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("currentDifficulty", this.currentDifficulty);
        tag.putDouble("smoothedDifficulty", this.smoothedDifficulty);
        tag.putInt("consecutiveIncreases", this.consecutiveIncreases);
        tag.putInt("consecutiveDecreases", this.consecutiveDecreases);
        tag.putInt("currentWaveNumber", this.currentWaveNumber);
        return tag;
    }

    public static WaveAutoScaler load(CompoundTag tag) {
        double initialDifficulty = tag.contains("currentDifficulty") ? tag.getDouble("currentDifficulty") : BASE_DIFFICULTY;
        WaveAutoScaler scaler = new WaveAutoScaler(initialDifficulty);
        scaler.smoothedDifficulty = tag.getDouble("smoothedDifficulty");
        scaler.consecutiveIncreases = tag.getInt("consecutiveIncreases");
        scaler.consecutiveDecreases = tag.getInt("consecutiveDecreases");
        scaler.currentWaveNumber = tag.getInt("currentWaveNumber");
        return scaler;
    }

    // ---- getters ----

    public double getCurrentDifficulty() {
        return currentDifficulty;
    }

    public double getSmoothedDifficulty() {
        return smoothedDifficulty;
    }

    public WaveMetrics getCurrentWaveMetrics() {
        return currentWaveMetrics;
    }
}
