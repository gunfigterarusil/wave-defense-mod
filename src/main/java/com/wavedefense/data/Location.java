package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class Location {
    private String name;
    private BlockPos playerSpawn;
    private List<BlockPos> mobSpawns;
    private List<WaveConfig> waves;
    private int totalWaves;
    private int timeBetweenWaves; // in seconds
    private Map<UUID, Integer> playerPoints;
    private boolean keepInventory;
    private List<ItemStack> startingItems;
    private List<ShopItem> shopItems;

    public Location(String name) {
        this.name = name;
        this.mobSpawns = new ArrayList<>();
        this.waves = new ArrayList<>();
        this.totalWaves = 10;
        this.timeBetweenWaves = 30;
        this.playerPoints = new HashMap<>();
        this.startingItems = new ArrayList<>();
        this.shopItems = new ArrayList<>();
        this.keepInventory = true;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BlockPos getPlayerSpawn() { return playerSpawn; }
    public void setPlayerSpawn(BlockPos pos) { this.playerSpawn = pos; }

    public List<BlockPos> getMobSpawns() { return mobSpawns; }
    public void addMobSpawn(BlockPos pos) {
        if (mobSpawns.size() < 10) mobSpawns.add(pos);
    }
    public void removeMobSpawn(int index) {
        if (index >= 0 && index < mobSpawns.size()) mobSpawns.remove(index);
    }

    public List<WaveConfig> getWaves() { return waves; }
    public void addWave(WaveConfig wave) { waves.add(wave); }

    public int getTotalWaves() { return totalWaves; }
    public void setTotalWaves(int count) { this.totalWaves = count; }

    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int seconds) { this.timeBetweenWaves = seconds; }

    public boolean isKeepInventory() { return keepInventory; }
    public void setKeepInventory(boolean keep) { this.keepInventory = keep; }

    public List<ItemStack> getStartingItems() { return startingItems; }
    public void addStartingItem(ItemStack item) { startingItems.add(item.copy()); }

    public List<ShopItem> getShopItems() { return shopItems; }
    public void addShopItem(ShopItem item) { shopItems.add(item); }
    public void removeShopItem(int index) {
        if (index >= 0 && index < shopItems.size()) shopItems.remove(index);
    }

    public int getPlayerPoints(UUID playerId) {
        return playerPoints.getOrDefault(playerId, 0);
    }

    public void addPoints(UUID playerId, int points) {
        playerPoints.put(playerId, getPlayerPoints(playerId) + points);
    }

    public void resetPoints(UUID playerId) {
        playerPoints.put(playerId, 0);
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("totalWaves", totalWaves);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);

        if (playerSpawn != null) {
            tag.putLong("playerSpawn", playerSpawn.asLong());
        }

        ListTag mobSpawnsList = new ListTag();
        for (BlockPos pos : mobSpawns) {
            CompoundTag posTag = new CompoundTag();
            posTag.putLong("pos", pos.asLong());
            mobSpawnsList.add(posTag);
        }
        tag.put("mobSpawns", mobSpawnsList);

        ListTag wavesList = new ListTag();
        for (WaveConfig wave : waves) {
            wavesList.add(wave.save());
        }
        tag.put("waves", wavesList);

        tag.putBoolean("keepInventory", keepInventory);

        ListTag startingItemsList = new ListTag();
        for (ItemStack item : startingItems) {
            startingItemsList.add(item.save(new CompoundTag()));
        }
        tag.put("startingItems", startingItemsList);

        ListTag shopItemsList = new ListTag();
        for (ShopItem item : shopItems) {
            shopItemsList.add(item.save());
        }
        tag.put("shopItems", shopItemsList);

        return tag;
    }

    public static Location load(CompoundTag tag) {
        Location location = new Location(tag.getString("name"));
        location.totalWaves = tag.getInt("totalWaves");
        location.timeBetweenWaves = tag.getInt("timeBetweenWaves");

        if (tag.contains("playerSpawn")) {
            location.playerSpawn = BlockPos.of(tag.getLong("playerSpawn"));
        }

        ListTag mobSpawnsList = tag.getList("mobSpawns", 10);
        for (int i = 0; i < mobSpawnsList.size(); i++) {
            CompoundTag posTag = mobSpawnsList.getCompound(i);
            location.mobSpawns.add(BlockPos.of(posTag.getLong("pos")));
        }

        ListTag wavesList = tag.getList("waves", 10);
        for (int i = 0; i < wavesList.size(); i++) {
            location.waves.add(WaveConfig.load(wavesList.getCompound(i)));
        }

        location.keepInventory = tag.getBoolean("keepInventory");

        ListTag startingItemsList = tag.getList("startingItems", 10);
        for (int i = 0; i < startingItemsList.size(); i++) {
            location.startingItems.add(ItemStack.of(startingItemsList.getCompound(i)));
        }

        ListTag shopItemsList = tag.getList("shopItems", 10);
        for (int i = 0; i < shopItemsList.size(); i++) {
            location.shopItems.add(ShopItem.load(shopItemsList.getCompound(i)));
        }

        return location;
    }
}
