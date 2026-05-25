package com.wavedefense.gui;

import com.wavedefense.data.LeaderboardRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for the last leaderboard response received from the server.
 * Updated by {@link com.wavedefense.network.packets.LeaderboardDataPacket}.
 */
public final class ClientLeaderboardCache {

    private static String              cachedLocation = "";
    private static String              cachedMode     = "";
    private static List<LeaderboardRecord> records    = new ArrayList<>();
    private static boolean             loading        = false;

    private ClientLeaderboardCache() {}

    /** Called by the packet handler when the server sends new records. */
    public static void update(String location, String mode, List<LeaderboardRecord> newRecords) {
        cachedLocation = location != null ? location : "";
        cachedMode     = mode     != null ? mode     : "";
        records        = newRecords != null ? new ArrayList<>(newRecords) : new ArrayList<>();
        loading        = false;
    }

    /** Mark that a request has been sent and we are waiting for a response. */
    public static void setLoading() {
        loading = true;
        records = new ArrayList<>();
    }

    public static boolean isLoading()              { return loading; }
    public static String  getCachedLocation()      { return cachedLocation; }
    public static String  getCachedMode()          { return cachedMode; }
    public static List<LeaderboardRecord> getRecords() { return records; }

    /** Resets the cache (e.g. on disconnect). */
    public static void reset() {
        cachedLocation = "";
        cachedMode     = "";
        records        = new ArrayList<>();
        loading        = false;
    }
}
