package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the v0.4 progression features: difficulty presets, endless-mode
 * scaling, wave-modifier rolling, and the lifetime player profile.
 *
 * <p>These are all pure arithmetic and bookkeeping — exactly the kind of logic that
 * breaks silently in a live server and is invisible until someone's leaderboard entry
 * looks wrong, so it is worth pinning here rather than discovering in production.
 */
class ProgressionTest {

    // ── Difficulty presets ──────────────────────────────────────────────────

    @Test
    void difficultyParsesLenientlyAndDefaultsToNormal() {
        assertEquals(DifficultyPreset.HARD,      DifficultyPreset.fromString("hard"));
        assertEquals(DifficultyPreset.HARD,      DifficultyPreset.fromString("HARD"));
        assertEquals(DifficultyPreset.NIGHTMARE, DifficultyPreset.fromString("NIGHTMARE"));
        assertEquals(DifficultyPreset.NORMAL,    DifficultyPreset.fromString("nonsense"));
        assertEquals(DifficultyPreset.NORMAL,    DifficultyPreset.fromString(""));
        assertEquals(DifficultyPreset.NORMAL,    DifficultyPreset.fromString(null));
    }

    @Test
    void difficultyMultipliersIncreaseMonotonically() {
        DifficultyPreset[] ordered = {
            DifficultyPreset.EASY, DifficultyPreset.NORMAL,
            DifficultyPreset.HARD, DifficultyPreset.NIGHTMARE
        };
        for (int i = 1; i < ordered.length; i++) {
            assertTrue(ordered[i].getHealthMult() > ordered[i - 1].getHealthMult(),
                ordered[i] + " must be tougher than " + ordered[i - 1]);
            // Rewards must rise with difficulty, or nobody picks the hard tiers.
            assertTrue(ordered[i].getPointsMult() > ordered[i - 1].getPointsMult(),
                ordered[i] + " must pay better than " + ordered[i - 1]);
        }
    }

    @Test
    void normalTierKeepsTheUnsuffixedLeaderboardKey() {
        // Existing records live under the bare mode key; changing that would orphan them.
        assertEquals("", DifficultyPreset.NORMAL.getLeaderboardSuffix());
        assertEquals("_hard", DifficultyPreset.HARD.getLeaderboardSuffix());
        assertNotEquals(DifficultyPreset.HARD.getLeaderboardSuffix(),
                        DifficultyPreset.NIGHTMARE.getLeaderboardSuffix());
    }

    @Test
    void difficultyCyclesThroughEveryTierAndWrapsAround() {
        DifficultyPreset d = DifficultyPreset.EASY;
        for (int i = 0; i < DifficultyPreset.values().length; i++) d = d.next();
        assertEquals(DifficultyPreset.EASY, d, "cycling once through every tier returns to the start");
    }

    // ── Endless mode ────────────────────────────────────────────────────────

    private static Location endlessLocation(int totalWaves, int scalingPercent) {
        Location loc = new Location("test");
        loc.setTotalWaves(totalWaves);
        loc.setEndlessMode(true);
        loc.setEndlessScalingPercent(scalingPercent);
        return loc;
    }

    @Test
    void endlessLoopCountsCompletedPassesThroughTheWaveList() {
        Location loc = endlessLocation(10, 10);

        assertEquals(0, loc.getEndlessLoop(1));
        assertEquals(0, loc.getEndlessLoop(10), "wave 10 is still the first pass");
        assertEquals(1, loc.getEndlessLoop(11), "wave 11 opens the second pass");
        assertEquals(1, loc.getEndlessLoop(20));
        assertEquals(2, loc.getEndlessLoop(21));
    }

    @Test
    void endlessMultiplierGrowsLinearlyNotExponentially() {
        Location loc = endlessLocation(10, 10);

        assertEquals(1.0, loc.getEndlessMultiplier(1),  1e-9, "first loop is unscaled");
        assertEquals(1.1, loc.getEndlessMultiplier(11), 1e-9);
        assertEquals(1.2, loc.getEndlessMultiplier(21), 1e-9);
        // Compounding would give 1.1^3 = 1.331 here; linear gives 1.3.
        assertEquals(1.3, loc.getEndlessMultiplier(31), 1e-9);
    }

    @Test
    void nonEndlessLocationNeverScales() {
        Location loc = new Location("test");
        loc.setTotalWaves(10);
        loc.setEndlessScalingPercent(50);

        assertEquals(1.0, loc.getEndlessMultiplier(99), 1e-9);
        assertEquals(0, loc.getEndlessLoop(99));
    }

    @Test
    void endlessScalingPercentIsClamped() {
        Location loc = new Location("test");
        loc.setEndlessScalingPercent(-5);
        assertEquals(0, loc.getEndlessScalingPercent());
        loc.setEndlessScalingPercent(500);
        assertEquals(100, loc.getEndlessScalingPercent());
    }

    // ── Wave modifiers ──────────────────────────────────────────────────────

    @Test
    void modifierWavesFireOnTheConfiguredInterval() {
        Location loc = new Location("test");
        loc.setModifiersEnabled(true);
        loc.setModifierInterval(3);

        assertFalse(loc.isModifierWave(1));
        assertFalse(loc.isModifierWave(2));
        assertTrue(loc.isModifierWave(3));
        assertTrue(loc.isModifierWave(6));
        assertFalse(loc.isModifierWave(7));
    }

    @Test
    void modifiersDisabledMeansNoModifierWaveEver() {
        Location loc = new Location("test");
        loc.setModifierInterval(1);
        assertFalse(loc.isModifiersEnabled());
        assertFalse(loc.isModifierWave(1));
        assertFalse(loc.isModifierWave(50));
    }

    @Test
    void modifierIntervalNeverDropsBelowOne() {
        // A zero interval would make `wave % interval` divide by zero.
        Location loc = new Location("test");
        loc.setModifiersEnabled(true);
        loc.setModifierInterval(0);
        assertEquals(1, loc.getModifierInterval());
        assertTrue(loc.isModifierWave(1));
    }

    @Test
    void rollRespectsThePoolWhenOneIsConfigured() {
        Random rng = new Random(1234);
        List<String> pool = List.of("swift", "venomous");
        for (int i = 0; i < 50; i++) {
            WaveModifier m = WaveModifier.roll(pool, rng);
            assertTrue(m == WaveModifier.SWIFT || m == WaveModifier.VENOMOUS,
                "rolled " + m + " which is outside the configured pool");
        }
    }

    @Test
    void rollTreatsAnEmptyOrUnknownPoolAsEveryModifier() {
        Random rng = new Random(99);
        assertNotNull(WaveModifier.roll(List.of(), rng));
        assertNotNull(WaveModifier.roll(null, rng));
        // A pool of names that match nothing must not produce an empty candidate list.
        assertNotNull(WaveModifier.roll(List.of("not_a_modifier"), rng));
    }

    @Test
    void modifierParsesLenientlyAndRejectsUnknownNames() {
        assertEquals(WaveModifier.VOLATILE, WaveModifier.fromString("volatile"));
        assertEquals(WaveModifier.VOLATILE, WaveModifier.fromString("VOLATILE"));
        assertNull(WaveModifier.fromString("explodey"));
        assertNull(WaveModifier.fromString(null));
        assertNull(WaveModifier.fromString(""));
    }

    // ── Player profile ──────────────────────────────────────────────────────

    @Test
    void levelFollowsTheQuadraticCurve() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "Tester");
        assertEquals(1, p.getLevel(), "a fresh profile starts at level 1");

        assertEquals(0,   PlayerProfile.xpForLevel(1));
        assertEquals(100, PlayerProfile.xpForLevel(2));
        assertEquals(400, PlayerProfile.xpForLevel(3));
        assertEquals(900, PlayerProfile.xpForLevel(4));

        // 10 XP per wave, so 10 waves is exactly the level-2 threshold.
        for (int i = 1; i <= 10; i++) p.recordWaveCompleted(i);
        assertEquals(100, p.getXp());
        assertEquals(2, p.getLevel());
    }

    @Test
    void profileTracksBestWaveAsAHighWaterMark() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "Tester");
        p.recordWaveCompleted(5);
        p.recordWaveCompleted(12);
        p.recordWaveCompleted(3); // a worse later run must not lower the record

        assertEquals(12, p.getBestWave());
        assertEquals(3,  p.getTotalWaves());
    }

    @Test
    void winRateAndMatchCountsAddUp() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "Tester");
        assertEquals(0, p.getWinRatePercent(), "no matches played → no division by zero");

        p.recordMatchEnd(true,  120);
        p.recordMatchEnd(false,  60);
        p.recordMatchEnd(false,  60);

        assertEquals(3, p.getMatchesPlayed());
        assertEquals(1, p.getMatchesWon());
        assertEquals(33, p.getWinRatePercent());
        assertEquals(240, p.getPlaytimeSec());
    }

    @Test
    void levelProgressStaysWithinBounds() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "Tester");
        assertEquals(0f, p.getLevelProgress(), 1e-6);

        p.recordKills(50); // 50 XP — halfway to level 2
        assertTrue(p.getLevelProgress() > 0f && p.getLevelProgress() < 1f);
        assertTrue(p.getXpToNextLevel() > 0);
    }

    @Test
    void negativeOrZeroGainsAreIgnored() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "Tester");
        p.recordKills(0);
        p.recordKills(-5);
        p.recordPoints(-100);

        assertEquals(0, p.getTotalKills());
        assertEquals(0, p.getTotalPoints());
        assertEquals(0, p.getXp());
    }

    @Test
    void nbtRoundTripPreservesTheWholeProfile() {
        UUID id = UUID.randomUUID();
        PlayerProfile p = new PlayerProfile(id, "Tester");
        p.recordWaveCompleted(17);
        p.recordKills(42);
        p.recordPoints(500);
        p.recordDeath();
        p.recordMatchEnd(true, 300);

        CompoundTag tag = p.save();
        PlayerProfile restored = PlayerProfile.load(tag);

        assertNotNull(restored);
        assertEquals(id,                  restored.getUuid());
        assertEquals("Tester",            restored.getPlayerName());
        assertEquals(p.getTotalWaves(),   restored.getTotalWaves());
        assertEquals(p.getBestWave(),     restored.getBestWave());
        assertEquals(p.getTotalKills(),   restored.getTotalKills());
        assertEquals(p.getTotalPoints(),  restored.getTotalPoints());
        assertEquals(p.getMatchesPlayed(),restored.getMatchesPlayed());
        assertEquals(p.getMatchesWon(),   restored.getMatchesWon());
        assertEquals(p.getDeaths(),       restored.getDeaths());
        assertEquals(p.getPlaytimeSec(),  restored.getPlaytimeSec());
        assertEquals(p.getXp(),           restored.getXp());
        assertEquals(p.getLevel(),        restored.getLevel());
    }

    @Test
    void loadRejectsATagWithoutAUuid() {
        assertNull(PlayerProfile.load(new CompoundTag()),
            "a malformed entry must be skipped, not resurrected with a null id");
    }
}
