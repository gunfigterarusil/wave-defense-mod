package com.wavedefense.data;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the section-merge feature's core safety invariant: every NBT key a
 * {@link Location} can persist must be owned by exactly one {@link LocationSection}.
 *
 * <p>If a key is unmapped, a concurrent section-merge would silently drop an
 * admin's edit to it. This test fails loudly the moment a new persisted key is
 * added to {@code Location}/{@code LocationSerializer} without being assigned to
 * a section — forcing the author to place it.
 *
 * <p>The expected key list mirrors {@code LocationSerializer.serialize}. Keep it
 * in sync when adding a persisted field.
 */
class LocationSectionTest {

    /** Every key Location.save() writes (mirror of LocationSerializer). */
    private static final String[] ALL_KEYS = {
        // GENERAL
        "name", "mode", "playerSpawn", "playerSpawnRadius", "victoryExitPos",
        "surrenderExitPos", "enforceGameMode", "keepInventory", "keepLootOnExit",
        "hiddenFromPlayers", "locationLeaveTimerSec", "victoryLingerTimeSec",
        "victoryScreenEnabled", "reEntryCooldownSec",
        // GAMEPLAY
        "waves", "timeBetweenWaves", "totalWaves", "firstWaveDelaySec", "mobSpawns",
        "mobSpawnRadius", "locationTriggerEnabled", "locationTriggerType",
        "completionRewards", "completionPointsReward", "pvpMode", "pvpMinPlayers",
        "pvpFriendlyFire", "pvpTeamAutoBalance", "pvpWaitEffect",
        "pvpReadyCheckTimeoutSec", "pvpTotalRounds", "pvpBuyTime", "pvpRoundStartDelay",
        "pvpRoundStartPoints", "pvpRoundTimeLimitSec", "pvpWinPoints", "pvpLosePoints",
        "pvpKillPoints", "pvpDeathPenalty", "dmKillsToWin", "dmSpawnMode",
        "ctpCaptureAllWin", "ctpFirstToScore", "ctpRoundDurationSec", "ctpScorePerSec",
        "ctpScoreToWin", "ctpSpeedMultiplier", "kothFirstToScore", "kothHoldDurationSec",
        "kothHoldMode", "kothResetOnLoss", "kothRoundDurationSec", "kothScorePerSec",
        "kothScoreToWin", "brBorderDamage", "brBorderDamageAmt", "brBorderParticle",
        "brBorderParticleCount", "brBorderRadius", "brFinalRadius", "brInitialWaitSec",
        "brShrinkAmountBlocks", "brShrinkIntervalSec", "capturePoints", "pvpSpawnPoints",
        // AREA
        "bboxMin", "bboxMax", "bboxOutlineEnabled", "minimapEnabled",
        "locationBoundaryEnabled", "locationBoundaryRadius", "boundaryConsequence",
        "boundaryDamagePerSec", "boundaryParticleCount", "boundaryParticleHeight",
        "boundaryParticleType", "boundaryParticlesEnabled", "portalDisappearsOnComplete",
        "portalEnabled", "portalOpenAfterStartSec", "portalPenaltyTimerSec",
        "portalPenaltyWave", "portalRespawnTimerSec", "autoActivate",
        "autoActivateEntryPos", "autoActivateRadius", "zoneActivationTimeSec",
        "zoneCenter", "zoneOpenAfterStartSec", "zoneParticleCount", "zoneParticleInterval",
        "zoneParticleSpeed", "zoneParticleType", "zoneUsesCustomCenter",
        // ECONOMY
        "shopItems", "shopPoints", "shopMode", "lootSpawns", "startingItems", "startingPoints",
        // VISUAL
        "infoPanel",
        // COMPAT
        "masChaosResist", "masFireResist", "masLevel", "masLightningResist",
        "masPhysicalResist", "masWaterResist", "masXpBonus",
        // RUNTIME
        "locked", "totalMobsKilledAllTime", "totalSessionsCompleted",
        "playerPoints", "playerTeamMap"
    };

    @Test
    void everyKeyIsMappedToExactlyOneSection() {
        for (String key : ALL_KEYS) {
            LocationSection sec = LocationSection.sectionOf(key);
            assertNotNull(sec, "Key '" + key + "' is not assigned to any LocationSection — "
                + "a section-merge would silently drop edits to it.");
            int owners = 0;
            for (LocationSection s : LocationSection.values()) {
                if (s.owns(key)) owners++;
            }
            assertEquals(1, owners, "Key '" + key + "' is owned by " + owners
                + " sections (must be exactly 1).");
        }
    }

    @Test
    void noSectionDeclaresAnUnknownKey() {
        Set<String> known = new HashSet<>();
        for (String k : ALL_KEYS) known.add(k);
        for (LocationSection s : LocationSection.values()) {
            for (String key : s.keys()) {
                assertTrue(known.contains(key),
                    "Section " + s + " declares key '" + key + "' that Location never persists "
                    + "(stale mapping — remove it or add the field).");
            }
        }
    }

    @Test
    void runtimeKeysAreNeverEditorOwned() {
        for (LocationSection editor : LocationSection.editorSections()) {
            for (String key : editor.keys()) {
                assertFalse(LocationSection.isRuntime(key),
                    "Editor section " + editor + " must not own runtime key '" + key + "'.");
            }
        }
        // RUNTIME is excluded from editorSections()
        for (LocationSection s : LocationSection.editorSections()) {
            assertNotEquals(LocationSection.RUNTIME, s);
        }
    }
}
