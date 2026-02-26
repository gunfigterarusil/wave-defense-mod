package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class Location {
    private String name;
    private LocationMode mode;
    private BlockPos playerSpawn;
    private List<BlockPos> mobSpawns;
    private List<WaveConfig> waves;
    private int totalWaves;
    private int timeBetweenWaves;
    private Map<UUID, Integer> playerPoints;
    private boolean keepInventory;
    // Авто-активація зони (тільки PvE)
    private boolean autoActivate = false;
    private int autoActivateRadius = 5; // блоків
    private List<ItemStack> startingItems;
    private List<ShopItem> shopItems;
    private List<ShopItem> completionRewards;
    private int completionPointsReward;
    private List<LootSpawn> lootSpawns;

    // PvP поля
    private List<PvpSpawnPoint> pvpSpawnPoints;
    private int pvpMinPlayers;
    private boolean pvpFriendlyFire;
    private int pvpKillPoints;
    private int pvpDeathPenalty;
    private int pvpTotalRounds;   // кількість раундів (0 = нескінченно)
    private int pvpBuyTime;        // час на покупки між раундами (секунди)
    private int startingPoints = 0; // стартові поінти при вході на локацію
    private Map<UUID, String> playerTeamMap;

    public Location(String name) {
        this.name = name;
        this.mode = LocationMode.PVE;
        this.mobSpawns = new ArrayList<>();
        this.waves = new ArrayList<>();
        this.totalWaves = 10;
        this.timeBetweenWaves = 30;
        this.playerPoints = new HashMap<>();
        this.startingItems = new ArrayList<>();
        this.shopItems = new ArrayList<>();
        this.completionRewards = new ArrayList<>();
        this.completionPointsReward = 0;
        this.lootSpawns = new ArrayList<>();
        this.keepInventory = true;
        this.pvpSpawnPoints = new ArrayList<>();
        this.pvpMinPlayers = 2;
        this.pvpFriendlyFire = false;
        this.pvpKillPoints = 100;
        this.pvpDeathPenalty = 50;
        this.pvpTotalRounds = 10;
        this.pvpBuyTime = 20;
        this.playerTeamMap = new HashMap<>();
    }

    // --- Загальні ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocationMode getMode() { return mode; }
    public void setMode(LocationMode mode) { this.mode = mode; }
    public boolean isPvp() { return mode == LocationMode.PVP; }

    public BlockPos getPlayerSpawn() { return playerSpawn; }
    public void setPlayerSpawn(BlockPos pos) { this.playerSpawn = pos; }

    public List<BlockPos> getMobSpawns() { return mobSpawns; }
    public void addMobSpawn(BlockPos pos) { if (mobSpawns.size() < com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get()) mobSpawns.add(pos); }
    public void removeMobSpawn(int index) { if (index >= 0 && index < mobSpawns.size()) mobSpawns.remove(index); }

    public List<WaveConfig> getWaves() { return waves; }
    public void addWave(WaveConfig wave) { waves.add(wave); }

    public int getTotalWaves() { return totalWaves; }
    public void setTotalWaves(int count) { this.totalWaves = count; }

    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int seconds) { this.timeBetweenWaves = seconds; }

    public boolean isKeepInventory() { return keepInventory; }
    public void setKeepInventory(boolean keep) { this.keepInventory = keep; }

    public boolean isAutoActivate() { return autoActivate; }
    public void setAutoActivate(boolean v) { this.autoActivate = v; }
    public int getAutoActivateRadius() { return autoActivateRadius; }
    public void setAutoActivateRadius(int r) { this.autoActivateRadius = Math.max(1, Math.min(20, r)); }

    public List<ItemStack> getStartingItems() { return startingItems; }
    public void addStartingItem(ItemStack item) { startingItems.add(item.copy()); }

    public List<ShopItem> getShopItems() { return shopItems; }
    public void addShopItem(ShopItem item) { shopItems.add(item); }
    public void removeShopItem(int index) { if (index >= 0 && index < shopItems.size()) shopItems.remove(index); }

    public List<ShopItem> getCompletionRewards() { return completionRewards; }
    public void addCompletionReward(ShopItem item) { completionRewards.add(item); }
    public void removeCompletionReward(int index) { if (index >= 0 && index < completionRewards.size()) completionRewards.remove(index); }
    public int getCompletionPointsReward() { return completionPointsReward; }
    public void setCompletionPointsReward(int points) { this.completionPointsReward = points; }

    public List<LootSpawn> getLootSpawns() { return lootSpawns; }
    public void addLootSpawn(LootSpawn ls) { lootSpawns.add(ls); }
    public void removeLootSpawn(int index) { if (index >= 0 && index < lootSpawns.size()) lootSpawns.remove(index); }

    public int getPlayerPoints(UUID playerId) { return playerPoints.getOrDefault(playerId, 0); }
    public void addPoints(UUID playerId, int points) { playerPoints.put(playerId, getPlayerPoints(playerId) + points); }
    public void removePoints(UUID playerId, int points) {
        playerPoints.put(playerId, Math.max(0, getPlayerPoints(playerId) - points));
    }
    public void resetPoints(UUID playerId) { playerPoints.put(playerId, 0); }

    // --- PvP ---
    public List<PvpSpawnPoint> getPvpSpawnPoints() { return pvpSpawnPoints; }
    public void addPvpSpawnPoint(PvpSpawnPoint sp) { pvpSpawnPoints.add(sp); }
    public void removePvpSpawnPoint(int index) { if (index >= 0 && index < pvpSpawnPoints.size()) pvpSpawnPoints.remove(index); }

    public int getPvpMinPlayers() { return pvpMinPlayers; }
    public void setPvpMinPlayers(int n) { this.pvpMinPlayers = Math.max(2, n); }

    public boolean isPvpFriendlyFire() { return pvpFriendlyFire; }
    public void setPvpFriendlyFire(boolean ff) { this.pvpFriendlyFire = ff; }

    public int getPvpKillPoints() { return pvpKillPoints; }
    public void setPvpKillPoints(int pts) { this.pvpKillPoints = pts; }

    public int getPvpDeathPenalty() { return pvpDeathPenalty; }
    public void setPvpDeathPenalty(int pts) { this.pvpDeathPenalty = pts; }

    public int getPvpTotalRounds() { return pvpTotalRounds; }
    public void setPvpTotalRounds(int r) { this.pvpTotalRounds = Math.max(1, r); }

    public int getPvpBuyTime() { return pvpBuyTime; }
    public void setPvpBuyTime(int sec) { this.pvpBuyTime = Math.max(5, sec); }

    public int getStartingPoints() { return startingPoints; }
    public void setStartingPoints(int pts) { this.startingPoints = Math.max(0, pts); }

    public Map<UUID, String> getPlayerTeamMap() { return playerTeamMap; }
    public String getPlayerTeam(UUID playerId) { return playerTeamMap.get(playerId); }
    public void setPlayerTeam(UUID playerId, String teamName) { playerTeamMap.put(playerId, teamName); }
    public void removePlayerTeam(UUID playerId) { playerTeamMap.remove(playerId); }

    /** Повертає кількість живих гравців у ворожих командах відносно даного гравця */
    public int getEnemyCount(UUID playerId, java.util.function.Predicate<UUID> isOnline) {
        String myTeam = playerTeamMap.get(playerId);
        if (myTeam == null) return 0;
        int count = 0;
        for (Map.Entry<UUID, String> e : playerTeamMap.entrySet()) {
            if (!e.getKey().equals(playerId) && !e.getValue().equals(myTeam) && isOnline.test(e.getKey())) {
                count++;
            }
        }
        return count;
    }

    /** Чи обидва в одній команді */
    public boolean isSameTeam(UUID a, UUID b) {
        String ta = playerTeamMap.get(a);
        String tb = playerTeamMap.get(b);
        return ta != null && ta.equals(tb);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("mode", mode.name());
        tag.putInt("totalWaves", totalWaves);
        tag.putInt("timeBetweenWaves", timeBetweenWaves);
        tag.putInt("completionPointsReward", completionPointsReward);
        tag.putBoolean("keepInventory", keepInventory);
        tag.putBoolean("autoActivate", autoActivate);
        tag.putInt("autoActivateRadius", autoActivateRadius);
        tag.putInt("pvpMinPlayers", pvpMinPlayers);
        tag.putBoolean("pvpFriendlyFire", pvpFriendlyFire);
        tag.putInt("pvpKillPoints", pvpKillPoints);
        tag.putInt("pvpDeathPenalty", pvpDeathPenalty);
        tag.putInt("pvpTotalRounds", pvpTotalRounds);
        tag.putInt("pvpBuyTime", pvpBuyTime);
        tag.putInt("startingPoints", startingPoints);

        if (playerSpawn != null) tag.putLong("playerSpawn", playerSpawn.asLong());

        ListTag mobSpawnsList = new ListTag();
        for (BlockPos pos : mobSpawns) {
            CompoundTag posTag = new CompoundTag();
            posTag.putLong("pos", pos.asLong());
            mobSpawnsList.add(posTag);
        }
        tag.put("mobSpawns", mobSpawnsList);

        ListTag pvpSpawnsList = new ListTag();
        for (PvpSpawnPoint sp : pvpSpawnPoints) pvpSpawnsList.add(sp.save());
        tag.put("pvpSpawnPoints", pvpSpawnsList);

        ListTag wavesList = new ListTag();
        for (WaveConfig wave : waves) wavesList.add(wave.save());
        tag.put("waves", wavesList);

        ListTag startingItemsList = new ListTag();
        for (ItemStack item : startingItems) startingItemsList.add(item.save(new CompoundTag()));
        tag.put("startingItems", startingItemsList);

        ListTag shopItemsList = new ListTag();
        for (ShopItem item : shopItems) shopItemsList.add(item.save());
        tag.put("shopItems", shopItemsList);

        ListTag completionRewardsList = new ListTag();
        for (ShopItem item : completionRewards) completionRewardsList.add(item.save());
        tag.put("completionRewards", completionRewardsList);

        ListTag lootSpawnsList = new ListTag();
        for (LootSpawn ls : lootSpawns) lootSpawnsList.add(ls.save());
        tag.put("lootSpawns", lootSpawnsList);

        return tag;
    }

    public static Location load(CompoundTag tag) {
        Location location = new Location(tag.getString("name"));
        location.mode = LocationMode.fromString(tag.getString("mode"));
        location.totalWaves = tag.getInt("totalWaves");
        location.timeBetweenWaves = tag.getInt("timeBetweenWaves");
        location.completionPointsReward = tag.contains("completionPointsReward") ? tag.getInt("completionPointsReward") : 0;
        location.keepInventory = !tag.contains("keepInventory") || tag.getBoolean("keepInventory");
        location.autoActivate = tag.contains("autoActivate") && tag.getBoolean("autoActivate");
        location.autoActivateRadius = tag.contains("autoActivateRadius") ? tag.getInt("autoActivateRadius") : 5;
        location.pvpMinPlayers = tag.contains("pvpMinPlayers") ? tag.getInt("pvpMinPlayers") : 2;
        location.pvpFriendlyFire = tag.contains("pvpFriendlyFire") && tag.getBoolean("pvpFriendlyFire");
        location.pvpKillPoints = tag.contains("pvpKillPoints") ? tag.getInt("pvpKillPoints") : 100;
        location.pvpDeathPenalty = tag.contains("pvpDeathPenalty") ? tag.getInt("pvpDeathPenalty") : 50;
        location.pvpTotalRounds = tag.contains("pvpTotalRounds") ? tag.getInt("pvpTotalRounds") : 10;
        location.pvpBuyTime = tag.contains("pvpBuyTime") ? tag.getInt("pvpBuyTime") : 20;
        location.startingPoints = tag.contains("startingPoints") ? tag.getInt("startingPoints") : 0;

        if (tag.contains("playerSpawn")) location.playerSpawn = BlockPos.of(tag.getLong("playerSpawn"));

        ListTag mobSpawnsList = tag.getList("mobSpawns", 10);
        for (int i = 0; i < mobSpawnsList.size(); i++)
            location.mobSpawns.add(BlockPos.of(mobSpawnsList.getCompound(i).getLong("pos")));

        if (tag.contains("pvpSpawnPoints")) {
            ListTag pvpSp = tag.getList("pvpSpawnPoints", 10);
            for (int i = 0; i < pvpSp.size(); i++) location.pvpSpawnPoints.add(PvpSpawnPoint.load(pvpSp.getCompound(i)));
        }

        ListTag wavesList = tag.getList("waves", 10);
        for (int i = 0; i < wavesList.size(); i++) location.waves.add(WaveConfig.load(wavesList.getCompound(i)));

        ListTag sil = tag.getList("startingItems", 10);
        for (int i = 0; i < sil.size(); i++) location.startingItems.add(ItemStack.of(sil.getCompound(i)));

        ListTag shopList = tag.getList("shopItems", 10);
        for (int i = 0; i < shopList.size(); i++) location.shopItems.add(ShopItem.load(shopList.getCompound(i)));

        ListTag crList = tag.getList("completionRewards", 10);
        for (int i = 0; i < crList.size(); i++) location.completionRewards.add(ShopItem.load(crList.getCompound(i)));

        if (tag.contains("lootSpawns")) {
            ListTag lsList = tag.getList("lootSpawns", 10);
            for (int i = 0; i < lsList.size(); i++) location.lootSpawns.add(LootSpawn.load(lsList.getCompound(i)));
        }

        return location;
    }
}
