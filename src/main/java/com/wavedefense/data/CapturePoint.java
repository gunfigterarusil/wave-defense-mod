package com.wavedefense.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;

import java.util.UUID;

/**
 * Represents a single capture/control point used in Capture the Point and King of the Hill PvP modes.
 * The point has a position, capture radius, time to capture, and visual particle settings.
 */
public class CapturePoint {

    private String id;           // unique identifier (UUID string)
    private String name;         // display name, e.g. "Alpha", "Hill"
    private BlockPos pos;
    private int captureRadius   = 5;   // block radius for player detection
    private int captureTimeSec  = 10;  // seconds of uncontested presence to flip ownership
    private String particleType = "minecraft:smoke";
    private int particleCount   = 4;

    /** Creates a new capture point with auto-generated ID. */
    public CapturePoint(String name, BlockPos pos) {
        this.id   = UUID.randomUUID().toString();
        this.name = name != null ? name : "Point";
        this.pos  = pos;
    }

    /** For deserialization only. */
    private CapturePoint() {}

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String   getId()                         { return id; }

    public String   getName()                       { return name; }
    public void     setName(String name)            { this.name = name != null ? name : "Point"; }

    public BlockPos getPos()                        { return pos; }
    public void     setPos(BlockPos pos)            { this.pos = pos; }

    public int      getCaptureRadius()              { return captureRadius; }
    public void     setCaptureRadius(int r)         { this.captureRadius = Math.max(1, Math.min(30, r)); }

    public int      getCaptureTimeSec()             { return captureTimeSec; }
    public void     setCaptureTimeSec(int s)        { this.captureTimeSec = Math.max(1, Math.min(120, s)); }

    public String   getParticleType()               { return particleType != null ? particleType : "minecraft:smoke"; }
    public void     setParticleType(String p)       { this.particleType = p; }

    public int      getParticleCount()              { return particleCount; }
    public void     setParticleCount(int c)         { this.particleCount = Math.max(1, Math.min(32, c)); }

    // ── NBT ───────────────────────────────────────────────────────────────

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("id",            id);
        tag.putString("name",          name);
        if (pos != null) tag.putLong("pos", pos.asLong());
        tag.putInt("captureRadius",    captureRadius);
        tag.putInt("captureTimeSec",   captureTimeSec);
        tag.putString("particleType",  particleType);
        tag.putInt("particleCount",    particleCount);
        return tag;
    }

    public static CapturePoint load(CompoundNBT tag) {
        CapturePoint cp = new CapturePoint();
        cp.id            = tag.contains("id")   ? tag.getString("id")   : UUID.randomUUID().toString();
        cp.name          = tag.contains("name") ? tag.getString("name") : "Point";
        cp.pos           = tag.contains("pos")  ? BlockPos.of(tag.getLong("pos")) : BlockPos.ZERO;
        cp.captureRadius = NbtHelper.getInt(tag,    "captureRadius",   5);
        cp.captureTimeSec= NbtHelper.getInt(tag,    "captureTimeSec",  10);
        cp.particleType  = NbtHelper.getString(tag, "particleType",    "minecraft:smoke");
        cp.particleCount = NbtHelper.getInt(tag,    "particleCount",   4);
        return cp;
    }
}
