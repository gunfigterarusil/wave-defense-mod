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

    public PvpSpawnPoint(String teamName, BlockPos pos) {
        this.teamName = teamName;
        this.pos = pos;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String name) { this.teamName = name; }
    public BlockPos getPos() { return pos; }
    public void setPos(BlockPos pos) { this.pos = pos; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("teamName", teamName);
        tag.putLong("pos", pos.asLong());
        return tag;
    }

    public static PvpSpawnPoint load(CompoundTag tag) {
        return new PvpSpawnPoint(
                tag.getString("teamName"),
                BlockPos.of(tag.getLong("pos"))
        );
    }
}
