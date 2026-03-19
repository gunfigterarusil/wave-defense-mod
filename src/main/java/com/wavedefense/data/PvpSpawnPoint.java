package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Точка спавну команди у PvP-локації.
 * Кожна точка має назву (наприклад "Команда Червоних") та позицію.
 * Гравці однієї точки не можуть нанести шкоду одне одному (якщо вимкнено friendly fire).
 */
public class PvpSpawnPoint {
    private String teamName;
    private BlockPos pos;
    /** Радіус розкиду гравців навколо точки спавну (0 = точно на блоці) */
    private int spawnRadius = 0;

    public PvpSpawnPoint(String teamName, BlockPos pos) {
        this.teamName = teamName;
        this.pos = pos;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String name) { this.teamName = name; }
    public BlockPos getPos() { return pos; }
    public void setPos(BlockPos pos) { this.pos = pos; }
    public int  getSpawnRadius()      { return spawnRadius; }
    public void setSpawnRadius(int r) { this.spawnRadius = Math.max(0, r); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("teamName", teamName);
        tag.putLong("pos", pos.asLong());
        if (spawnRadius > 0) tag.putInt("spawnRadius", spawnRadius);
        return tag;
    }

    public static PvpSpawnPoint load(CompoundTag tag) {
        PvpSpawnPoint sp = new PvpSpawnPoint(
                tag.getString("teamName"),
                BlockPos.of(tag.getLong("pos"))
        );
        if (tag.contains("spawnRadius")) sp.spawnRadius = tag.getInt("spawnRadius");
        return sp;
    }
}
