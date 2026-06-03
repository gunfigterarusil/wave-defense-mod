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
    /** v0.2.65: ChatFormatting color name (e.g. "RED", "BLUE"). null/empty =
     *  auto-pick from team-name hash (legacy behaviour). */
    private String colorName = "";
    /** v0.2.65: custom display name shown in HUD, scoreboard, minimap legend.
     *  Empty = use {@link #teamName} as display (legacy behaviour). */
    private String customDisplayName = "";

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
    /** v0.2.65: explicit team color name (empty = auto from teamName hash). */
    public String getColorName()              { return colorName == null ? "" : colorName; }
    public void   setColorName(String c)      { this.colorName = c == null ? "" : c; }
    /** v0.2.65: custom display name (empty = falls back to teamName). */
    public String getCustomDisplayName()      { return customDisplayName == null ? "" : customDisplayName; }
    public void   setCustomDisplayName(String n) { this.customDisplayName = n == null ? "" : n; }
    /** v0.2.65: convenience — returns custom name if set, else teamName. */
    public String getDisplayName() {
        String c = getCustomDisplayName();
        return c.isEmpty() ? teamName : c;
    }
    /** v0.2.65: resolves to ChatFormatting; falls back to hash-derived palette
     *  if no explicit color is set or the name is invalid. */
    public net.minecraft.ChatFormatting resolveChatColor() {
        String c = getColorName();
        if (!c.isEmpty()) {
            try {
                net.minecraft.ChatFormatting cf = net.minecraft.ChatFormatting.valueOf(c.toUpperCase(java.util.Locale.ROOT));
                if (cf.isColor()) return cf;
            } catch (IllegalArgumentException ignored) {}
        }
        // Legacy hash-based fallback
        net.minecraft.ChatFormatting[] palette = {
            net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BLUE,
            net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.YELLOW,
            net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.AQUA,
            net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.WHITE
        };
        int hash = teamName == null ? 0 : Math.abs(teamName.hashCode());
        return palette[hash % palette.length];
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("teamName", teamName);
        tag.putLong("pos", pos.asLong());
        if (spawnRadius > 0) tag.putInt("spawnRadius", spawnRadius);
        // v0.2.65: only persist color/displayName when set (compact NBT)
        if (colorName != null && !colorName.isEmpty()) tag.putString("colorName", colorName);
        if (customDisplayName != null && !customDisplayName.isEmpty())
            tag.putString("customDisplayName", customDisplayName);
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
        // v0.2.65
        if (tag.contains("colorName")) sp.colorName = tag.getString("colorName");
        if (tag.contains("customDisplayName")) sp.customDisplayName = tag.getString("customDisplayName");
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
