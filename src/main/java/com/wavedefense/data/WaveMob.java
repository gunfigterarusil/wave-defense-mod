package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфігурація моба у хвилі.
 * Тепер підтримує: броню (4 слоти), зброю (в руці), ефекти.
 */
public class WaveMob {
    private ResourceLocation mobType;
    private int count;
    private int growthPerWave;
    private int spawnChance;
    private int pointsPerKill;

    // Спорядження (можуть бути empty)
    private ItemStack helmet     = ItemStack.EMPTY;
    private ItemStack chestplate = ItemStack.EMPTY;
    private ItemStack leggings   = ItemStack.EMPTY;
    private ItemStack boots      = ItemStack.EMPTY;
    private ItemStack mainHand   = ItemStack.EMPTY;
    private ItemStack offHand    = ItemStack.EMPTY;

    // Ефекти: список "effectId:amplifier:durationTicks" рядків
    private List<String> effects = new ArrayList<>();

    public WaveMob(ResourceLocation mobType, int count, int growthPerWave, int spawnChance, int pointsPerKill) {
        this.mobType = mobType;
        this.count = count;
        this.growthPerWave = growthPerWave;
        this.spawnChance = spawnChance;
        this.pointsPerKill = pointsPerKill;
    }

    public ResourceLocation getMobType()            { return mobType; }
    public void setMobType(ResourceLocation t)      { this.mobType = t; }
    public int getCount()                           { return count; }
    public void setCount(int c)                     { this.count = c; }
    public int getGrowthPerWave()                   { return growthPerWave; }
    public void setGrowthPerWave(int g)             { this.growthPerWave = g; }
    public int getSpawnChance()                     { return spawnChance; }
    public void setSpawnChance(int s)               { this.spawnChance = s; }
    public int getPointsPerKill()                   { return pointsPerKill; }
    public void setPointsPerKill(int p)             { this.pointsPerKill = p; }

    public ItemStack getHelmet()      { return helmet; }
    public void setHelmet(ItemStack i) { this.helmet = i.copy(); }
    public ItemStack getChestplate()  { return chestplate; }
    public void setChestplate(ItemStack i) { this.chestplate = i.copy(); }
    public ItemStack getLeggings()    { return leggings; }
    public void setLeggings(ItemStack i) { this.leggings = i.copy(); }
    public ItemStack getBoots()       { return boots; }
    public void setBoots(ItemStack i) { this.boots = i.copy(); }
    public ItemStack getMainHand()    { return mainHand; }
    public void setMainHand(ItemStack i) { this.mainHand = i.copy(); }
    public ItemStack getOffHand()     { return offHand; }
    public void setOffHand(ItemStack i) { this.offHand = i.copy(); }
    public List<String> getEffects()  { return effects; }
    public void setEffects(List<String> e) { this.effects = new ArrayList<>(e); }

    public boolean hasEquipment() {
        return !helmet.isEmpty() || !chestplate.isEmpty() || !leggings.isEmpty()
            || !boots.isEmpty() || !mainHand.isEmpty() || !offHand.isEmpty();
    }

    public boolean hasEffects() { return !effects.isEmpty(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mobType", mobType.toString());
        tag.putInt("count", count);
        tag.putInt("growthPerWave", growthPerWave);
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("pointsPerKill", pointsPerKill);

        saveItem(tag, "helmet",     helmet);
        saveItem(tag, "chestplate", chestplate);
        saveItem(tag, "leggings",   leggings);
        saveItem(tag, "boots",      boots);
        saveItem(tag, "mainHand",   mainHand);
        saveItem(tag, "offHand",    offHand);

        if (!effects.isEmpty()) {
            ListTag efList = new ListTag();
            for (String e : effects) {
                net.minecraft.nbt.StringTag st = net.minecraft.nbt.StringTag.valueOf(e);
                efList.add(st);
            }
            tag.put("effects", efList);
        }
        return tag;
    }

    private static void saveItem(CompoundTag parent, String key, ItemStack item) {
        if (!item.isEmpty()) parent.put(key, item.save(new CompoundTag()));
    }

    public static WaveMob load(CompoundTag tag) {
        WaveMob mob = new WaveMob(
                new ResourceLocation(tag.getString("mobType")),
                tag.getInt("count"),
                tag.getInt("growthPerWave"),
                tag.getInt("spawnChance"),
                tag.getInt("pointsPerKill")
        );
        if (tag.contains("helmet"))     mob.helmet     = ItemStack.of(tag.getCompound("helmet"));
        if (tag.contains("chestplate")) mob.chestplate = ItemStack.of(tag.getCompound("chestplate"));
        if (tag.contains("leggings"))   mob.leggings   = ItemStack.of(tag.getCompound("leggings"));
        if (tag.contains("boots"))      mob.boots      = ItemStack.of(tag.getCompound("boots"));
        if (tag.contains("mainHand"))   mob.mainHand   = ItemStack.of(tag.getCompound("mainHand"));
        if (tag.contains("offHand"))    mob.offHand    = ItemStack.of(tag.getCompound("offHand"));
        if (tag.contains("effects")) {
            ListTag efList = tag.getList("effects", 8);
            for (int i = 0; i < efList.size(); i++) mob.effects.add(efList.getString(i));
        }
        return mob;
    }
}
