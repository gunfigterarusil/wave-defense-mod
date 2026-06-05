package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;

import java.util.UUID;

/**
 * A single leaderboard entry.
 *
 * <ul>
 *   <li>PvE: primaryScore = points earned, secondaryScore = waves completed</li>
 *   <li>PvP Standard/DM: primaryScore = points, secondaryScore = kills</li>
 *   <li>PvP CtP/KotH: primaryScore = objective score, secondaryScore = kills</li>
 *   <li>PvP Battle Royale: primaryScore = kills, secondaryScore = survival position (1 = winner)</li>
 * </ul>
 */
public class LeaderboardRecord {

    public UUID   playerUuid;
    public String playerName;
    public int    primaryScore;    // main ranking metric (higher = better)
    public int    secondaryScore;  // supplementary stat
    public int    durationSec;     // session duration in seconds
    public long   timestamp;       // System.currentTimeMillis()

    public LeaderboardRecord(UUID uuid, String name, int primary, int secondary, int durationSec) {
        this.playerUuid     = uuid;
        this.playerName     = name != null ? name : "Unknown";
        this.primaryScore   = primary;
        this.secondaryScore = secondary;
        this.durationSec    = durationSec;
        this.timestamp      = System.currentTimeMillis();
    }

    /** For deserialization. */
    private LeaderboardRecord() {}

    // ── NBT ───────────────────────────────────────────────────────────────

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putUUID("uuid",           playerUuid);
        tag.putString("name",         playerName);
        tag.putInt("primaryScore",    primaryScore);
        tag.putInt("secondaryScore",  secondaryScore);
        tag.putInt("durationSec",     durationSec);
        tag.putLong("timestamp",      timestamp);
        return tag;
    }

    public static LeaderboardRecord load(CompoundNBT tag) {
        LeaderboardRecord r = new LeaderboardRecord();
        r.playerUuid    = tag.hasUUID("uuid") ? tag.getUUID("uuid") : UUID.randomUUID();
        r.playerName    = NbtHelper.getString(tag, "name",          "Unknown");
        r.primaryScore  = NbtHelper.getInt(tag,    "primaryScore",  0);
        r.secondaryScore= NbtHelper.getInt(tag,    "secondaryScore",0);
        r.durationSec   = NbtHelper.getInt(tag,    "durationSec",   0);
        r.timestamp     = tag.contains("timestamp") ? tag.getLong("timestamp") : 0L;
        return r;
    }
}
