package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Точка появи луту на карті.
 * Зберігає позицію, список предметів (до 4), шанс появи та кількість.
 */
public class LootSpawn {
    private BlockPos pos;
    private List<ItemStack> items;
    private int spawnChance; // 1–100
    private int count;       // скільки штук кожного предмета

    public LootSpawn(BlockPos pos, List<ItemStack> items, int spawnChance, int count) {
        this.pos = pos;
        this.items = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
        this.spawnChance = Math.max(1, Math.min(100, spawnChance));
        this.count = Math.max(1, count);
    }

    public BlockPos getPos() { return pos; }
    public void setPos(BlockPos pos) { this.pos = pos; }

    public List<ItemStack> getItems() { return items; }
    public void setItems(List<ItemStack> items) {
        this.items = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
    }

    public int getSpawnChance() { return spawnChance; }
    public void setSpawnChance(int spawnChance) { this.spawnChance = Math.max(1, Math.min(100, spawnChance)); }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = Math.max(1, count); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("count", count);
        ListTag itemsList = new ListTag();
        for (ItemStack item : items) {
            if (!item.isEmpty()) itemsList.add(item.save(new CompoundTag()));
        }
        tag.put("items", itemsList);
        return tag;
    }

    public static LootSpawn load(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        int spawnChance = tag.getInt("spawnChance");
        int count = tag.contains("count") ? tag.getInt("count") : 1;
        List<ItemStack> items = new ArrayList<>();
        ListTag itemsList = tag.getList("items", 10);
        for (int i = 0; i < itemsList.size(); i++) {
            items.add(ItemStack.of(itemsList.getCompound(i)));
        }
        return new LootSpawn(pos, items, spawnChance, count);
    }
}
