package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class WaveConfigValidator {

    private final Location location;
    private final List<WaveConfig> waves;
    private final List<String> errors;
    private final List<String> warnings;

    private int maxMobsPerWave = 100;
    private int maxTotalMobsAcrossWaves = 500;
    private int maxBossMobsPerWave = 5;
    private int maxWaveTimeSeconds = 600;
    private int minWaveTimeSeconds = 5;
    private boolean allowTriggerWavesInNormalSequence = false;
    private boolean enforceUniqueWaveNumbers = true;

    private double playerScaleFactor = 1.0;
    private double performanceScaleFactor = 1.0;
    private boolean autoScaleEnabled = true;

    private int serverTickTimeThresholdMs = 50;
    private int recentTickSamples = 0;
    private long accumulatedTickTimeMs = 0;
    private boolean overloadMode = false;

    private final int configMaxMobTypes;
    private final int configMaxWaves;
    private final int configMaxMobSpawns;

    public WaveConfigValidator(Location location) {
        this.location = location;
        this.waves = new ArrayList<>(location.getWaves());
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.configMaxMobTypes = WaveDefenseConfig.MAX_MOB_TYPES.get();
        this.configMaxWaves = WaveDefenseConfig.MAX_WAVES.get();
        this.configMaxMobSpawns = WaveDefenseConfig.MAX_MOB_SPAWNS.get();
    }
    public WaveConfigValidator setMaxMobsPerWave(int max) {
        this.maxMobsPerWave = Math.max(1, Math.min(max, 1000));
        return this;
    }

    public WaveConfigValidator setMaxTotalMobsAcrossWaves(int max) {
        this.maxTotalMobsAcrossWaves = Math.max(1, Math.min(max, 5000));
        return this;
    }

    public WaveConfigValidator setMaxBossMobsPerWave(int max) {
        this.maxBossMobsPerWave = Math.max(0, Math.min(max, 50));
        return this;
    }

    public WaveConfigValidator setMaxWaveTimeSeconds(int max) {
        this.maxWaveTimeSeconds = Math.max(10, Math.min(max, 3600));
        return this;
    }

    public WaveConfigValidator setMinWaveTimeSeconds(int min) {
        this.minWaveTimeSeconds = Math.max(1, Math.min(min, 300));
        return this;
    }

    public WaveConfigValidator setAllowTriggerWavesInNormalSequence(boolean allow) {
        this.allowTriggerWavesInNormalSequence = allow;
        return this;
    }

    public WaveConfigValidator setEnforceUniqueWaveNumbers(boolean enforce) {
        this.enforceUniqueWaveNumbers = enforce;
        return this;
    }

    public WaveConfigValidator setAutoScaleEnabled(boolean enabled) {
        this.autoScaleEnabled = enabled;
        return this;
    }

    public void recordTickTime(long tickTimeMs) {
        accumulatedTickTimeMs += tickTimeMs;
        recentTickSamples++;
        if (recentTickSamples >= 20) {
            long avgTick = accumulatedTickTimeMs / recentTickSamples;
            overloadMode = avgTick > serverTickTimeThresholdMs;
            if (overloadMode) {
                performanceScaleFactor = Math.max(0.5, 1.0 - ((avgTick - serverTickTimeThresholdMs) / 100.0));
            } else {
                performanceScaleFactor = Math.min(1.0, performanceScaleFactor + 0.05);
            }
            accumulatedTickTimeMs = 0;
            recentTickSamples = 0;
        }
    }

    public void setPlayerScaleFactor(double factor) {
        this.playerScaleFactor = Math.max(0.25, Math.min(3.0, factor));
    }

    public void autoComputePlayerScale(int playerCount) {
        if (playerCount <= 1) {
            this.playerScaleFactor = 1.0;
        } else {
            this.playerScaleFactor = Math.max(0.5, Math.min(2.5, 1.0 + (playerCount - 1) * 0.15));
        }
    }

    // Н4: basic validation — was missing entirely; called from SessionManager.startSession()
    /**
     * Validates the location's wave configuration.
     * Populates {@link #getErrors()} and {@link #getWarnings()} and returns false if there are errors.
     */
    public boolean validate() {
        errors.clear();
        warnings.clear();

        if (waves == null || waves.isEmpty()) {
            errors.add("Location '" + location.getName() + "' has no waves configured.");
            return false;
        }
        if (waves.size() > configMaxWaves) {
            errors.add("Wave count " + waves.size() + " exceeds config maximum " + configMaxWaves + ".");
        }

        int totalMobs = 0;
        for (int wi = 0; wi < waves.size(); wi++) {
            WaveConfig wave = waves.get(wi);
            if (wave.getMobs() == null || wave.getMobs().isEmpty()) {
                warnings.add("Wave " + (wi + 1) + " has no mobs — it will complete instantly.");
            }
            if (wave.getTimeBetweenWaves() < minWaveTimeSeconds) {
                warnings.add("Wave " + (wi + 1) + " timer " + wave.getTimeBetweenWaves()
                    + "s is below minimum " + minWaveTimeSeconds + "s.");
            }
            if (wave.getTimeBetweenWaves() > maxWaveTimeSeconds) {
                warnings.add("Wave " + (wi + 1) + " timer " + wave.getTimeBetweenWaves()
                    + "s exceeds maximum " + maxWaveTimeSeconds + "s.");
            }
            int waveMobCount = 0;
            if (wave.getMobs() != null) {
                for (WaveMob mob : wave.getMobs()) {
                    if (mob.getMobType() == null || ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType()) == null) {
                        warnings.add("Wave " + (wi + 1) + " references unknown entity type: " + mob.getMobType());
                    }
                    waveMobCount += Math.max(0, mob.getCount());
                }
            }
            if (waveMobCount > maxMobsPerWave) {
                warnings.add("Wave " + (wi + 1) + " has " + waveMobCount
                    + " mobs which exceeds recommended maximum " + maxMobsPerWave + ".");
            }
            totalMobs += waveMobCount;
        }
        if (totalMobs > maxTotalMobsAcrossWaves) {
            warnings.add("Total mob count across all waves (" + totalMobs
                + ") exceeds recommended maximum " + maxTotalMobsAcrossWaves + ".");
        }

        return errors.isEmpty();
    }

    public List<String> getErrors()   { return Collections.unmodifiableList(errors); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public boolean isValid()          { return errors.isEmpty(); }
}