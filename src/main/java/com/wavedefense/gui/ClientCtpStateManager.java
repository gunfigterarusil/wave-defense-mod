package com.wavedefense.gui;

import com.wavedefense.network.packets.SyncCtpStatePacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side mirror of the active Capture the Point / King of the Hill state.
 * Updated every second via {@link SyncCtpStatePacket}.
 */
public final class ClientCtpStateManager {

    private static String              locationName     = "";
    private static Map<String, String>  pointOwners    = new LinkedHashMap<>();
    private static Map<String, String>  pointNames     = new LinkedHashMap<>();
    private static Map<String, Integer> captureTimeTicks= new LinkedHashMap<>(); // H-3: denominator for progress bar
    private static Map<String, Integer> captureProgress = new LinkedHashMap<>();
    private static Map<String, Integer> objectiveScore  = new LinkedHashMap<>();
    private static int scoreToWin    = 0;
    private static int roundTicksLeft= 0;

    private ClientCtpStateManager() {}

    /** Called by the packet handler on the client thread. */
    public static void update(SyncCtpStatePacket pkt) {
        locationName     = pkt.locationName      != null ? pkt.locationName                       : "";
        pointOwners      = pkt.pointOwners       != null ? new LinkedHashMap<>(pkt.pointOwners)      : new LinkedHashMap<>();
        pointNames       = pkt.pointNames        != null ? new LinkedHashMap<>(pkt.pointNames)       : new LinkedHashMap<>();
        captureTimeTicks = pkt.captureTimeTicks  != null ? new LinkedHashMap<>(pkt.captureTimeTicks) : new LinkedHashMap<>();
        captureProgress  = pkt.captureProgress   != null ? new LinkedHashMap<>(pkt.captureProgress)  : new LinkedHashMap<>();
        objectiveScore   = pkt.objectiveScore    != null ? new LinkedHashMap<>(pkt.objectiveScore)   : new LinkedHashMap<>();
        scoreToWin       = pkt.scoreToWin;
        roundTicksLeft   = pkt.roundTicksLeft;
    }

    /** Returns true when the client has live CtP/KotH data for the current session. */
    public static boolean isActive() {
        return !locationName.isEmpty() && !pointOwners.isEmpty();
    }

    public static String              getLocationName()     { return locationName; }
    public static Map<String, String>  getPointOwners()    { return pointOwners; }
    public static Map<String, String>  getPointNames()     { return pointNames; }
    public static Map<String, Integer> getCaptureTimeTicks(){ return captureTimeTicks; } // H-3
    public static Map<String, Integer> getCaptureProgress() { return captureProgress; }
    public static Map<String, Integer> getObjectiveScore()  { return objectiveScore; }
    public static int  getScoreToWin()    { return scoreToWin; }
    public static int  getRoundTicksLeft(){ return roundTicksLeft; }

    /** Whether the round is in timer mode (scoreToWin == 0 means timer mode). */
    public static boolean isTimerMode()   { return scoreToWin <= 0 && roundTicksLeft > 0; }

    /** Resets all state (e.g. on session end / disconnect). */
    public static void reset() {
        locationName    = "";
        pointOwners.clear();
        pointNames.clear();
        captureTimeTicks.clear();
        captureProgress.clear();
        objectiveScore.clear();
        scoreToWin     = 0;
        roundTicksLeft = 0;
    }
}
