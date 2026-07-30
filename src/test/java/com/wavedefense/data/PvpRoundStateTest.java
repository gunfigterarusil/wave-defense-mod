package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PvP round state machine.
 *
 * <p>{@link PvpRoundState} drives every PvP mode (Standard / Deathmatch / Battle
 * Royale / Capture-the-Point / King-of-the-Hill) and is the single highest-risk
 * class for regressions — a wrong phase transition or win check silently breaks
 * a whole game mode. These tests pin the behaviour that the managers rely on.
 */
class PvpRoundStateTest {

    private PvpRoundState state;

    @BeforeEach
    void setUp() {
        // 3 rounds, 20-second buy phase
        state = new PvpRoundState(3, 20);
    }

    // ── Phase transitions ───────────────────────────────────────────────────

    @Test
    void startsInWaitingPhase() {
        assertEquals(PvpRoundState.Phase.WAITING, state.getPhase());
        assertEquals(3, state.getTotalRounds());
    }

    @Test
    void buyPhaseAdvancesRoundAndSetsTimer() {
        int before = state.getCurrentRound();

        state.startBuyPhase();

        assertEquals(PvpRoundState.Phase.BUY, state.getPhase());
        assertEquals(before + 1, state.getCurrentRound(), "buy phase should open the next round");
        assertEquals(20 * 20, state.getTimerTicks(), "20 s buy time = 400 ticks");
    }

    @Test
    void readyCheckResetsReadyPlayersAndArmsTimeout() {
        state.markPlayerReady(UUID.randomUUID());
        assertEquals(1, state.getReadyCount());

        state.startReadyCheck(60);

        assertEquals(PvpRoundState.Phase.READY_CHECK, state.getPhase());
        assertEquals(60 * 20, state.getTimerTicks());
        assertEquals(0, state.getReadyCount(), "a fresh ready-check must start with nobody ready");
    }

    @Test
    void countdownAndRoundEndDelaySetTheirPhases() {
        state.startCountdown(5);
        assertEquals(PvpRoundState.Phase.COUNTDOWN, state.getPhase());
        assertEquals(100, state.getTimerTicks());

        state.startRoundEndDelay(3);
        assertEquals(PvpRoundState.Phase.ROUND_END_DELAY, state.getPhase());
        assertEquals(60, state.getTimerTicks());
    }

    @Test
    void tickDownNeverGoesNegative() {
        state.setTimerTicks(2);

        state.tickDown();
        state.tickDown();
        state.tickDown(); // one extra past zero

        assertEquals(0, state.getTimerTicks(), "timer must clamp at zero, not go negative");
    }

    // ── Ready-check bookkeeping ─────────────────────────────────────────────

    @Test
    void readyPlayersAreTrackedPerPlayerAndIdempotent() {
        UUID alice = UUID.randomUUID();
        UUID bob   = UUID.randomUUID();

        state.markPlayerReady(alice);
        state.markPlayerReady(alice); // double-press must not double-count

        assertTrue(state.isPlayerReady(alice));
        assertFalse(state.isPlayerReady(bob));
        assertEquals(1, state.getReadyCount());

        state.markPlayerReady(bob);
        assertEquals(2, state.getReadyCount());

        state.unmarkPlayerReady(alice);
        assertFalse(state.isPlayerReady(alice));
        assertEquals(1, state.getReadyCount());

        state.clearReadyPlayers();
        assertEquals(0, state.getReadyCount());
    }

    // ── Deathmatch win condition ────────────────────────────────────────────

    @Test
    void deathmatchWinnerOnlyOnceThresholdIsReached() {
        state.setDmKillsToWin(3);

        state.recordDmKill("red");
        state.recordDmKill("red");
        assertNull(state.checkDmWinner(), "2 of 3 kills is not a win yet");

        state.recordDmKill("red");
        assertEquals("red", state.checkDmWinner());
    }

    @Test
    void deathmatchIgnoresNullKillerTeam() {
        state.setDmKillsToWin(1);

        state.recordDmKill(null); // environment kill — no team credited

        assertNull(state.checkDmWinner());
    }

    @Test
    void resetDmKillsClearsProgress() {
        state.setDmKillsToWin(2);
        state.recordDmKill("blue");
        state.recordDmKill("blue");
        assertEquals("blue", state.checkDmWinner());

        state.resetDmKills();

        assertNull(state.checkDmWinner(), "kills should be cleared between matches");
    }

    // ── Objective (CtP / KotH) scoring ──────────────────────────────────────

    @Test
    void objectiveScoreAccumulatesAndDeclaresWinnerAtThreshold() {
        state.addObjectiveScore("red", 40);
        state.addObjectiveScore("red", 40);
        state.addObjectiveScore("blue", 10);

        assertEquals(80, state.getObjectiveScore("red"));
        assertEquals(10, state.getObjectiveScore("blue"));

        assertNull(state.checkObjectiveWinner(100), "80 < 100, nobody wins yet");
        assertEquals("red", state.checkObjectiveWinner(80));
    }

    @Test
    void leadingTeamIsNullWhileTiedAndNullWithNoScores() {
        assertNull(state.getLeadingTeam(), "no scores at all → no leader");

        state.addObjectiveScore("red", 50);
        state.addObjectiveScore("blue", 50);
        assertNull(state.getLeadingTeam(), "an exact tie has no leader (used for draw handling)");

        state.addObjectiveScore("red", 1);
        assertEquals("red", state.getLeadingTeam());
    }

    // ── KotH hold timer ─────────────────────────────────────────────────────

    @Test
    void kothHoldTicksAccumulatePerTeamAndReset() {
        state.addKothHoldTicks("red", 20);
        state.addKothHoldTicks("red", 20);
        state.addKothHoldTicks("blue", 5);

        assertEquals(40, state.getKothHoldTicks("red"));
        assertEquals(5, state.getKothHoldTicks("blue"));

        state.resetKothHoldTicks("red");
        assertEquals(0, state.getKothHoldTicks("red"));
        assertEquals(5, state.getKothHoldTicks("blue"), "resetting one team must not touch another");
    }

    // ── Battle Royale survivor check ────────────────────────────────────────

    @Test
    void battleRoyaleWinnerRequiresExactlyOneSurvivor() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        state.getAliveThisRound().addAll(java.util.Arrays.asList(a, b));

        assertNull(state.checkBrWinner(), "two alive → no winner");

        state.recordDeath(b, a);
        assertEquals(a, state.checkBrWinner(), "last one standing wins");

        state.recordDeath(a, null);
        assertNull(state.checkBrWinner(), "nobody alive → draw, not a winner");
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    @Test
    void nbtRoundTripPreservesMatchState() {
        state.startBuyPhase();               // round 1, BUY
        state.setDmKillsToWin(5);
        state.recordDmKill("red");
        state.addObjectiveScore("blue", 30);
        state.addKothHoldTicks("red", 60);

        CompoundTag tag = state.save();
        PvpRoundState restored = PvpRoundState.load(tag);

        assertEquals(state.getPhase(), restored.getPhase());
        assertEquals(state.getCurrentRound(), restored.getCurrentRound());
        assertEquals(state.getTotalRounds(), restored.getTotalRounds());
        assertEquals(state.getTimerTicks(), restored.getTimerTicks());
        assertEquals(state.getDmKillsToWin(), restored.getDmKillsToWin());
        assertEquals(state.getObjectiveScore("blue"), restored.getObjectiveScore("blue"));
        assertEquals(state.getKothHoldTicks("red"), restored.getKothHoldTicks("red"));
    }

    /**
     * Documents current behaviour: the ready-check set is deliberately transient
     * and is <em>not</em> written by {@link PvpRoundState#save()}.
     *
     * <p>That is harmless today — a restart discards live sessions entirely, so a
     * half-finished ready-check never resumes. If sessions ever become resumable,
     * this test will need to flip to asserting the set survives.
     */
    @Test
    void readyCheckStateIsTransientAcrossSaveLoad() {
        state.startReadyCheck(60);
        UUID ready = UUID.randomUUID();
        state.markPlayerReady(ready);
        assertEquals(1, state.getReadyCount());

        PvpRoundState restored = PvpRoundState.load(state.save());

        assertEquals(0, restored.getReadyCount(),
            "ready set is not persisted — see javadoc if this behaviour changes");
    }
}
