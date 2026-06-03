package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Точка спавну команди у PvP-локації.
 * Кожна точка має назву (наприклад "Команда Червоних") та позицію.
 * Гравці однієї точки не можуть нанести шкоду одне одному (якщо вимкнено friendly fire).
 *
 * <p>v0.2.64: per-team {@link #startingItems} list. When non-empty, players
 * joining this team receive these items on spawn instead of (or in addition to)
 * the location-global {@link Location#getStartingItems()}.
 */
public class PvpSpawnPoint {
    private String teamName;
    private BlockPos pos;
    /** Радіус розкиду гравців навколо точки спавну (0 = точно на блоці) */
    private int spawnRadius = 0;
    /** v0.2.64: per-team starting items — applied to team members on spawn.
     *  Empty list = fall back to {@link Location#getStartingItems()}. */
    private final List<ItemStack> startingItems = new ArrayList<>();

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
    /** v0.2.64: mutable list of per-team starting items. */
    public List<ItemStack> getStartingItems() { return startingItems; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("teamName", teamName);
        tag.putLong("pos", pos.asLong());
        if (spawnRadius > 0) tag.putInt("spawnRadius", spawnRadius);
        // v0.2.64: only persist startingItems when non-empty to keep NBT compact
        if (!startingItems.isEmpty()) {
            ListTag items = new ListTag();
            for (ItemStack is : startingItems) {
                if (is != null && !is.isEmpty()) {
                    CompoundTag iTag = new CompoundTag();
                    is.save(iTag);
                    items.add(iTag);
                }
            }
            if (!items.isEmpty()) tag.put("startingItems", items);
        }
        return tag;
    }

    public static PvpSpawnPoint load(CompoundTag tag) {
        PvpSpawnPoint sp = new PvpSpawnPoint(
                tag.getString("teamName"),
                BlockPos.of(tag.getLong("pos"))
        );
        if (tag.contains("spawnRadius")) sp.spawnRadius = tag.getInt("spawnRadius");
        // v0.2.64: load per-team starting items
        if (tag.contains("startingItems", Tag.TAG_LIST)) {
            ListTag items = tag.getList("startingItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                try {
                    ItemStack is = ItemStack.of(items.getCompound(i));
                    if (!is.isEmpty()) sp.startingItems.add(is);
                } catch (Throwable ignored) {}
            }
        }
        return sp;
    }
}
