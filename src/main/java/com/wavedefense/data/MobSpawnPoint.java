package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A mob spawn point with an individual scatter radius.
 * radius=0 — mobs spawn exactly at pos.
 * radius>0 — within a square [-radius, +radius] on X and Z.
 */
public class MobSpawnPoint {
    private BlockPos pos;
    private int radius = 0; // 0 = exact spawn

    public MobSpawnPoint(BlockPos pos) {
        this.pos = pos;
    }

    public MobSpawnPoint(BlockPos pos, int radius) {
        this.pos = pos;
        this.radius = Math.max(0, radius);
    }

    public BlockPos getPos()            { return pos; }
    public void     setPos(BlockPos p)  { this.pos = p; }
    public int      getRadius()         { return radius; }
    public void     setRadius(int r)    { this.radius = Math.max(0, r); }

    /** Повертає випадкову позицію в межах радіусу навколо pos */
    public BlockPos randomPos(java.util.Random rng) {
        if (radius <= 0) return pos;
        int dx = rng.nextInt(radius * 2 + 1) - radius;
        int dz = rng.nextInt(radius * 2 + 1) - radius;
        return pos.offset(dx, 0, dz);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        if (radius > 0) tag.putInt("radius", radius);
        return tag;
    }

    public static MobSpawnPoint load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        int radius = tag.contains("radius") ? tag.getInt("radius") : 0;
        return new MobSpawnPoint(pos, radius);
    }

    /** Backward-compat: create from plain BlockPos (radius=0) */
    public static MobSpawnPoint fromBlockPos(BlockPos pos) {
        return new MobSpawnPoint(pos, 0);
    }
}
