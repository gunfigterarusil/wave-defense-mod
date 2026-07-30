package com.wavedefense.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Partitions a {@link Location}'s NBT keys into editor sections so concurrent
 * edits by multiple admins can be merged at the section level instead of
 * clobbering the whole location (last-write-wins).
 *
 * <p>Each section owns a disjoint set of the keys written by
 * {@link LocationSerializer}. The {@link #RUNTIME} pseudo-section holds keys
 * that the editor must NEVER overwrite (live match state, lifetime stats, the
 * play-lock) — a merge from an open editor leaves these at their live server
 * values.
 *
 * <p><b>Safety invariant:</b> every key produced by {@code Location.save()} maps
 * to exactly one section. Keys not listed here fall through {@link #sectionOf}
 * to a conservative "always merge" result so an unmapped key is never silently
 * dropped (it just degrades to full-replace for that one key). The
 * {@code LocationSectionTest} coverage test asserts the mapping is exhaustive.
 */
public enum LocationSection {

    /** Identity + per-location behaviour flags. */
    GENERAL(
        "name", "mode", "playerSpawn", "playerSpawnRadius",
        "victoryExitPos", "surrenderExitPos",
        "enforceGameMode", "keepInventory", "keepLootOnExit", "hiddenFromPlayers",
        "locationLeaveTimerSec", "victoryLingerTimeSec", "victoryScreenEnabled",
        "reEntryCooldownSec"
    ),

    /** PvE waves + PvP rules (all sub-modes), spawns, capture points. */
    GAMEPLAY(
        // PvE
        "waves", "timeBetweenWaves", "totalWaves", "firstWaveDelaySec",
        "mobSpawns", "mobSpawnRadius",
        "locationTriggerEnabled", "locationTriggerType",
        "completionRewards", "completionPointsReward",
        // Endless / modifiers / difficulty
        "endlessMode", "endlessScalingPercent",
        "modifiersEnabled", "modifierInterval", "modifierPool",
        "difficultyPreset",
        // PvP common
        "pvpMode", "pvpMinPlayers", "pvpFriendlyFire", "pvpTeamAutoBalance",
        "pvpWaitEffect", "pvpReadyCheckTimeoutSec", "pvpTotalRounds", "pvpBuyTime",
        "pvpRoundStartDelay", "pvpRoundStartPoints", "pvpRoundTimeLimitSec",
        "pvpWinPoints", "pvpLosePoints", "pvpKillPoints", "pvpDeathPenalty",
        // Deathmatch
        "dmKillsToWin", "dmSpawnMode",
        // Capture the Point
        "ctpCaptureAllWin", "ctpFirstToScore", "ctpRoundDurationSec",
        "ctpScorePerSec", "ctpScoreToWin", "ctpSpeedMultiplier",
        // King of the Hill
        "kothFirstToScore", "kothHoldDurationSec", "kothHoldMode", "kothResetOnLoss",
        "kothRoundDurationSec", "kothScorePerSec", "kothScoreToWin",
        // Battle Royale
        "brBorderDamage", "brBorderDamageAmt", "brBorderParticle", "brBorderParticleCount",
        "brBorderRadius", "brFinalRadius", "brInitialWaitSec", "brShrinkAmountBlocks",
        "brShrinkIntervalSec",
        // Spawns + objectives
        "capturePoints", "pvpSpawnPoints"
    ),

    /** Bounding box, boundary, portal, auto-activation zone, particles. */
    AREA(
        "bboxMin", "bboxMax", "bboxOutlineEnabled", "minimapEnabled",
        "locationBoundaryEnabled", "locationBoundaryRadius", "boundaryConsequence",
        "boundaryDamagePerSec", "boundaryParticleCount", "boundaryParticleHeight",
        "boundaryParticleType", "boundaryParticlesEnabled",
        "portalDisappearsOnComplete", "portalEnabled", "portalOpenAfterStartSec",
        "portalPenaltyTimerSec", "portalPenaltyWave", "portalRespawnTimerSec",
        "autoActivate", "autoActivateEntryPos", "autoActivateRadius",
        "zoneActivationTimeSec", "zoneCenter", "zoneOpenAfterStartSec",
        "zoneParticleCount", "zoneParticleInterval", "zoneParticleSpeed",
        "zoneParticleType", "zoneUsesCustomCenter"
    ),

    /** Shop, loot, starting items/points. */
    ECONOMY(
        "shopItems", "shopPoints", "shopMode",
        "lootSpawns", "startingItems", "startingPoints"
    ),

    /** Info-panel display settings. */
    VISUAL(
        "infoPanel"
    ),

    /** Mine and Slash overrides. */
    COMPAT(
        "masChaosResist", "masFireResist", "masLevel", "masLightningResist",
        "masPhysicalResist", "masWaterResist", "masXpBonus"
    ),

    /**
     * Live state the editor must never overwrite: play-lock, lifetime stats,
     * in-session per-player points and team assignments. A section-merge from an
     * open editor preserves the server's live values for these keys.
     */
    RUNTIME(
        "locked", "totalMobsKilledAllTime", "totalSessionsCompleted",
        "playerPoints", "playerTeamMap"
    );

    private final Set<String> keys;

    LocationSection(String... keys) {
        this.keys = new HashSet<>(Arrays.asList(keys));
    }

    public Set<String> keys() {
        return keys;
    }

    public boolean owns(String key) {
        return keys.contains(key);
    }

    /** Editor-facing sections (excludes {@link #RUNTIME}). */
    public static LocationSection[] editorSections() {
        return new LocationSection[] { GENERAL, GAMEPLAY, AREA, ECONOMY, VISUAL, COMPAT };
    }

    /**
     * The section that owns {@code key}, or {@code null} if unmapped. Callers
     * treat {@code null} as "always merge this key" so unmapped keys are never
     * silently lost (they degrade to full-replace granularity).
     */
    public static LocationSection sectionOf(String key) {
        for (LocationSection s : values()) {
            if (s.owns(key)) return s;
        }
        return null;
    }

    /** True if a key belongs to {@link #RUNTIME} (editor must never write it). */
    public static boolean isRuntime(String key) {
        return RUNTIME.owns(key);
    }
}
