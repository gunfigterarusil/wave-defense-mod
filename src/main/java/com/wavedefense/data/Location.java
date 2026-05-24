package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class Location {
    // Fields are package-private so LocationSerializer (same package) can access them
    // directly — avoids needing setters for every deserialized value.
    String name;
    LocationMode mode;
    BlockPos playerSpawn;
    /** Радіус розкиду PvE гравців навколо playerSpawn (0 = точно на блоці) */
    int playerSpawnRadius = 0;
    List<MobSpawnPoint> mobSpawns;
    /** Радіус розкиду мобів навколо точки спавну (0 = за замовчуванням 5 блоків) */
    int mobSpawnRadius = 5;
    List<WaveConfig> waves;
    int totalWaves;
    int timeBetweenWaves;
    Map<UUID, Integer> playerPoints;
    boolean keepInventory;
    // Авто-активація зони (тільки PvE)
    boolean autoActivate = false;
    int autoActivateRadius = 5; // блоків
    List<ItemStack> startingItems;
    List<ShopItem> shopItems;
    List<ShopItem> completionRewards;
    // ── Режим магазину ────────────────────────────────────────────────
    public enum ShopMode { GLOBAL, POINT }
    ShopMode        shopMode   = ShopMode.GLOBAL;   // GLOBAL = звичайний, POINT = точковий
    List<ShopPoint> shopPoints = new ArrayList<>();  // точки точкового магазину
    int completionPointsReward;
    List<LootSpawn> lootSpawns;

    // PvP поля
    List<PvpSpawnPoint> pvpSpawnPoints;
    int pvpMinPlayers;
    boolean pvpFriendlyFire;
    int pvpKillPoints;
    int pvpDeathPenalty;
    int pvpTotalRounds;   // кількість раундів (0 = нескінченно)
    int pvpBuyTime;        // час на покупки між раундами (секунди)
    int pvpRoundStartDelay = 5;   // секунд від BUY→ACTIVE (підрахунок)
    int pvpRoundStartPoints = 0;  // поінти кожному гравцю на початку раунду
    int pvpWinPoints    = 0;      // поінти переможній команді за раунд
    int pvpLosePoints   = 0;      // поінти команді що програла за раунд
    // ── PvP підрежим ─────────────────────────────────────────────────────
    public enum PvpMode { STANDARD, DEATHMATCH, BATTLE_ROYALE }
    PvpMode pvpMode = PvpMode.STANDARD;

    // ── Battle Royale налаштування ────────────────────────────────────
    int     brBorderRadius        = 100;  // початковий радіус кордону (блоків)
    int     brShrinkIntervalSec   = 30;   // кожні N сек кордон зменшується на 1 блок
    String  brBorderParticle      = "minecraft:flame";
    int     brBorderParticleCount = 8;
    boolean brBorderDamage        = true;
    float   brBorderDamageAmt     = 1.0f;

    int     dmKillsToWin  = 10;          // Deathmatch: вбивств для перемоги у раунді
    boolean pvpWaitEffect = true;       // ефекти slowness+blindness на очікування (замість spectator)
    boolean pvpTeamAutoBalance = true;  // автобаланс команд при вході/виході
    boolean enforceGameMode = true;     // примусовий gamemode в локації
    int startingPoints = 0; // стартові поінти при вході на локацію
    Map<UUID, String> playerTeamMap;

    // ── Радіус локації та таймер виходу ─────────────────────────────
    // Якщо locationBoundaryEnabled=true — гравець що вийшов за radius отримує наслідки
    boolean locationBoundaryEnabled = false;
    int     locationBoundaryRadius  = 50;   // блоків, 1-9999
    int     locationLeaveTimerSec   = 30;   // секунд на повернення (для TIMER режиму)
    // ── Наслідки перетину кордону ─────────────────────────────────────
    public enum BoundaryConsequence { TIMER_SURRENDER, DAMAGE, TELEPORT_BACK, INSTANT_SURRENDER }
    BoundaryConsequence boundaryConsequence = BoundaryConsequence.TIMER_SURRENDER;
    float   boundaryDamagePerSec  = 2.0f;  // шкода за секунду (для DAMAGE режиму)
    // ── Візуал кордону ───────────────────────────────────────────────
    boolean boundaryParticlesEnabled = false;
    String  boundaryParticleType  = "minecraft:smoke"; // тип частинок
    int     boundaryParticleCount = 4;    // частинок на точку
    int     boundaryParticleHeight = 3;   // висота кільця (блоків)

    // ── Тригер запуску локації ────────────────────────────────────────
    boolean locationTriggerEnabled  = false;
    com.wavedefense.data.WaveTrigger locationTriggerType = com.wavedefense.data.WaveTrigger.PLAYER_ENTER_ZONE;

    // ── Портал ────────────────────────────────────────────────────────
    boolean portalEnabled       = false;
    // Штрафна хвиля: -1 = всі хвилі по порядку, 0+ = індекс конкретної хвилі
    int     portalPenaltyWave   = -1;
    // Час очікування до штрафної хвилі (тіки, 0 = відразу)
    int     portalPenaltyTimerSec = 60;
    // Чи зникає портал після проходження локації?
    boolean portalDisappearsOnComplete = true;
    // Якщо зникає — через скільки секунд зʼявляється знову в іншому місці
    int     portalRespawnTimerSec = 300;

    // Зберігати лут підібраний в локації після виходу
    boolean keepLootOnExit = false;

    // ── Перемога — екран та затримка виходу ───────────────────────────
    boolean victoryScreenEnabled = true;     // показувати екран "Перемога"
    int     victoryLingerTimeSec  = 30;       // скільки секунд гравці залишаються після перемоги

    // ── Авто-активація зони (розширена) ───────────────────────────────
    // Центр зони: якщо null — використовується playerSpawn
    net.minecraft.core.BlockPos zoneCenter   = null;
    int  zoneActivationTimeSec  = 0;    // 0 = миттєво; >0 = таймер після першого гравця в зоні
    // Час (сек) скільки зона залишається відкритою ПІСЛЯ запуску локації (0 = закрити одразу)
    int  zoneOpenAfterStartSec  = 0;    // 0 = закрити одразу після телепорту
    // useZoneCenter: false = використовуємо playerSpawn як центр, true = використовуємо zoneCenter
    boolean zoneUsesCustomCenter = false;

    // ── Портал: час відкритості після старту локації ──────────────────
    // 0 = закрити портал одразу після запуску локації
    // >0 = портал залишається відкритим ще N секунд після старту (для запізнілих гравців)
    // -1 = не закривати автоматично (стара поведінка grace period 30 сек)
    int portalOpenAfterStartSec = -1;

    // КД повторного входу після завершення/здачі
    int     reEntryCooldownSec = 0;  // 0 = вимкнено

    // Загальна статистика (зберігається між сесіями)
    long    totalMobsKilledAllTime = 0L;
    int     totalSessionsCompleted  = 0;

    // ── Інфо-панелі (TextDisplay entities у грі) ──────────────────────
    InfoPanelSettings infoPanel = new InfoPanelSettings();

    // Точка входу в портал (server-side, не serialized — зберігається у WaveManager.portalEntryPositions)
    // Авто-активація: окрема точка входу (якщо null — використовується playerSpawn)
    net.minecraft.core.BlockPos autoActivateEntryPos = null;
    // Точки виходу після проходження та здачі (null = повернутись на попереднє місце)
    net.minecraft.core.BlockPos victoryExitPos  = null;  // після перемоги
    net.minecraft.core.BlockPos surrenderExitPos = null; // після здачі
    // Прихована від гравців (адміни завжди бачать)
    boolean hiddenFromPlayers = false;
    // Час до початку першої хвилі після запуску (секунди)
    int firstWaveDelaySec = 0;
    // Частинки навколо зони входу (null = SQUID_INK за замовчуванням)
    String zoneParticleType  = null; // registry id, напр. "minecraft:flame"
    int    zoneParticleCount = 0;   // 0 = авто (radius*2, min 6, max 12)
    float  zoneParticleSpeed = 0.02f; // швидкість частинок (delta movement)
    int    zoneParticleInterval = 1;  // кожні N тіків спавнити частинки (1=кожен тік, 20=раз/сек)

    // ── Lock state ──────────────────────────────────────────────────────
    boolean locked = false;  // persistent: prevents new players from entering when true

    // ── Mine and Slash optional compatibility (mmorpg mod) ─────────────
    // All values default to 0 = "do not override; use MnS defaults"
    int masLevel          = 0;  // mob level in MnS system (0 = MnS default)
    int masXpBonus        = 0;  // bonus_exp PERCENT modifier added to mob (0 = no bonus)
    int masFireResist     = 0;  // fire_resist FLAT modifier (0 = no override)
    int masWaterResist    = 0;  // water_resist FLAT modifier
    int masLightningResist= 0;  // lightning_resist FLAT modifier
    int masChaosResist    = 0;  // chaos_resist FLAT modifier
    int masPhysicalResist = 0;  // physical_resist FLAT modifier

    // Таймер до запуску локації (для інфо-панелі над зоною входу, відображається в InfoPanel)
    // — це вже є як zoneActivationTimeSec, використовуємо його для відображення

    public Location(String name) {
        this.name = name;
        this.mode = LocationMode.PVE;
        this.mobSpawns = new ArrayList<>(); // List<MobSpawnPoint>
        this.waves = new ArrayList<>();
        this.totalWaves = 10;
        this.timeBetweenWaves = 30;
        this.playerPoints = new HashMap<>();
        this.startingItems = new ArrayList<>();
        this.shopItems = new ArrayList<>();
        this.completionRewards = new ArrayList<>();
        this.shopPoints = new ArrayList<>();
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
    public int  getPlayerSpawnRadius()      { return playerSpawnRadius; }
    public void setPlayerSpawnRadius(int r) { this.playerSpawnRadius = Math.max(0, r); }
    public int  getMobSpawnRadius()         { return mobSpawnRadius; }
    public void setMobSpawnRadius(int r)    { this.mobSpawnRadius = Math.max(0, r); }

    public List<MobSpawnPoint> getMobSpawns() { return mobSpawns; }
    public void addMobSpawn(BlockPos pos)            { if (mobSpawns.size() < com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get()) mobSpawns.add(new MobSpawnPoint(pos)); }
    public void addMobSpawnPoint(MobSpawnPoint sp)   { if (mobSpawns.size() < com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get()) mobSpawns.add(sp); }
    public void removeMobSpawn(int index) { if (index >= 0 && index < mobSpawns.size()) mobSpawns.remove(index); }

    public List<WaveConfig> getWaves() { return waves; }
    public void addWave(WaveConfig wave) { if (waves.size() < com.wavedefense.config.WaveDefenseConfig.MAX_WAVES.get()) waves.add(wave); }

    public int getTotalWaves() { return totalWaves; }
    public void setTotalWaves(int count) { this.totalWaves = count; }

    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int seconds) { this.timeBetweenWaves = seconds; }

    public boolean isKeepInventory() { return keepInventory; }
    public void setKeepInventory(boolean keep) { this.keepInventory = keep; }

    public boolean isAutoActivate() { return autoActivate; }
    public void setAutoActivate(boolean v) { this.autoActivate = v; }
    public int getAutoActivateRadius() { return autoActivateRadius; }
    public void setAutoActivateRadius(int r) { this.autoActivateRadius = Math.max(5, Math.min(9999, r)); }

    public List<ItemStack> getStartingItems() { return startingItems; }
    public void addStartingItem(ItemStack item) { startingItems.add(item.copy()); }

    public List<ShopItem> getShopItems() { return shopItems; }
    public void addShopItem(ShopItem item) { shopItems.add(item); }
    public void removeShopItem(int index) { if (index >= 0 && index < shopItems.size()) shopItems.remove(index); }

    // ── Режим магазину ────────────────────────────────────────────────
    public ShopMode         getShopMode()                  { return shopMode == null ? ShopMode.GLOBAL : shopMode; }
    public void             setShopMode(ShopMode m)        { this.shopMode = m == null ? ShopMode.GLOBAL : m; }
    public boolean          isPointShopMode()              { return shopMode == ShopMode.POINT; }
    public List<ShopPoint>  getShopPoints()                { return shopPoints; }
    public void             addShopPoint(ShopPoint sp)     { shopPoints.add(sp); }
    public void             removeShopPoint(int i)         { if (i >= 0 && i < shopPoints.size()) shopPoints.remove(i); }

    /**
     * Знаходить точку магазину в радіусі від гравця (або null якщо не знайдено / режим GLOBAL).
     */
    public ShopPoint findNearestShopPoint(double px, double py, double pz) {
        if (shopMode != ShopMode.POINT) return null;
        for (ShopPoint sp : shopPoints) {
            if (sp.isPlayerInRange(px, py, pz)) return sp;
        }
        return null;
    }

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

    // ── Boundary / Leave timer ───────────────────────────────────────
    public boolean isLocationBoundaryEnabled()             { return locationBoundaryEnabled; }
    public void    setLocationBoundaryEnabled(boolean v)   { this.locationBoundaryEnabled = v; }
    public int     getLocationBoundaryRadius()              { return locationBoundaryRadius; }
    public void    setLocationBoundaryRadius(int r)         { this.locationBoundaryRadius = Math.max(1, Math.min(9999, r)); }
    public BoundaryConsequence getBoundaryConsequence()     { return boundaryConsequence != null ? boundaryConsequence : BoundaryConsequence.TIMER_SURRENDER; }
    public void    setBoundaryConsequence(BoundaryConsequence v){ this.boundaryConsequence = v; }
    public float   getBoundaryDamagePerSec()               { return boundaryDamagePerSec; }
    public void    setBoundaryDamagePerSec(float v)        { this.boundaryDamagePerSec = Math.max(0f, v); }
    public boolean isBoundaryParticlesEnabled()            { return boundaryParticlesEnabled; }
    public void    setBoundaryParticlesEnabled(boolean v)  { this.boundaryParticlesEnabled = v; }
    public String  getBoundaryParticleType()               { return boundaryParticleType != null ? boundaryParticleType : "minecraft:smoke"; }
    public void    setBoundaryParticleType(String v)       { this.boundaryParticleType = v; }
    public int     getBoundaryParticleCount()              { return boundaryParticleCount; }
    public void    setBoundaryParticleCount(int v)         { this.boundaryParticleCount = Math.max(1, Math.min(32, v)); }
    public int     getBoundaryParticleHeight()             { return boundaryParticleHeight; }
    public void    setBoundaryParticleHeight(int v)        { this.boundaryParticleHeight = Math.max(1, Math.min(20, v)); }
    public int     getLocationLeaveTimerSec()           { return locationLeaveTimerSec; }
    public void    setLocationLeaveTimerSec(int s)      { this.locationLeaveTimerSec = Math.max(5, s); }

    // ── Location trigger ─────────────────────────────────────────────
    public boolean isLocationTriggerEnabled()          { return locationTriggerEnabled; }
    public void    setLocationTriggerEnabled(boolean v){ this.locationTriggerEnabled = v; }
    public com.wavedefense.data.WaveTrigger getLocationTriggerType() { return locationTriggerType; }
    public void setLocationTriggerType(com.wavedefense.data.WaveTrigger t) { this.locationTriggerType = t; }

    // ── Portal ───────────────────────────────────────────────────────
    public boolean isPortalEnabled()                  { return portalEnabled; }
    public void    setPortalEnabled(boolean v)        { this.portalEnabled = v; }
    public int     getPortalPenaltyWave()             { return portalPenaltyWave; }
    public void    setPortalPenaltyWave(int w)        { this.portalPenaltyWave = w; }
    public int     getPortalPenaltyTimerSec()         { return portalPenaltyTimerSec; }
    public void    setPortalPenaltyTimerSec(int s)    { this.portalPenaltyTimerSec = Math.max(0, s); }
    public boolean isPortalDisappearsOnComplete()     { return portalDisappearsOnComplete; }
    public void    setPortalDisappearsOnComplete(boolean v){ this.portalDisappearsOnComplete = v; }
    public int     getPortalRespawnTimerSec()         { return portalRespawnTimerSec; }
    public void    setPortalRespawnTimerSec(int s)    { this.portalRespawnTimerSec = Math.max(30, s); }

    public boolean isKeepLootOnExit()              { return keepLootOnExit; }
    public void    setKeepLootOnExit(boolean v)    { this.keepLootOnExit = v; }
    public int  getReEntryCooldownSec() { return reEntryCooldownSec; }
    public void setReEntryCooldownSec(int v) { this.reEntryCooldownSec = Math.max(0, v); }
    public long getTotalMobsKilledAllTime() { return totalMobsKilledAllTime; }
    public void addTotalMobsKilled(long n) { this.totalMobsKilledAllTime += n; }
    public int  getTotalSessionsCompleted() { return totalSessionsCompleted; }
    public void incrementSessionsCompleted() { this.totalSessionsCompleted++; }

    public InfoPanelSettings getInfoPanel() {
        if (infoPanel == null) infoPanel = new InfoPanelSettings();
        return infoPanel;
    }

    public net.minecraft.core.BlockPos getAutoActivateEntryPos() { return autoActivateEntryPos; }
    public void setAutoActivateEntryPos(net.minecraft.core.BlockPos pos) { this.autoActivateEntryPos = pos; }

    // ── Victory screen ────────────────────────────────────────────────
    public boolean isVictoryScreenEnabled()         { return victoryScreenEnabled; }
    public void    setVictoryScreenEnabled(boolean v){ this.victoryScreenEnabled = v; }
    public int     getVictoryLingerTimeSec()         { return victoryLingerTimeSec; }
    public void    setVictoryLingerTimeSec(int s)    { this.victoryLingerTimeSec = Math.max(0, s); }

    // ── Zone activation extended ──────────────────────────────────────
    public net.minecraft.core.BlockPos getZoneCenter() { return zoneCenter; }
    public void    setZoneCenter(net.minecraft.core.BlockPos p) { this.zoneCenter = p; }
    public boolean isZoneUsesCustomCenter()          { return zoneUsesCustomCenter; }
    public void    setZoneUsesCustomCenter(boolean v){ this.zoneUsesCustomCenter = v; }
    public int     getZoneActivationTimeSec()        { return zoneActivationTimeSec; }
    public void    setZoneActivationTimeSec(int s)   { this.zoneActivationTimeSec = Math.max(0, s); }
    public int     getZoneOpenAfterStartSec()        { return zoneOpenAfterStartSec; }
    public void    setZoneOpenAfterStartSec(int s)   { this.zoneOpenAfterStartSec = Math.max(0, s); }
    /** Центр зони активації: якщо кастомний — zoneCenter, інакше playerSpawn */
    public net.minecraft.core.BlockPos getEffectiveZoneCenter() {
        if (zoneUsesCustomCenter && zoneCenter != null) return zoneCenter;
        return playerSpawn; // fallback to playerSpawn
    }

    // ── Portal open-after-start ───────────────────────────────────────
    public int  getPortalOpenAfterStartSec()         { return portalOpenAfterStartSec; }
    public void setPortalOpenAfterStartSec(int s)    { this.portalOpenAfterStartSec = s; }

    // ── Exit points ───────────────────────────────────────────────────
    public net.minecraft.core.BlockPos getVictoryExitPos()              { return victoryExitPos; }
    public void setVictoryExitPos(net.minecraft.core.BlockPos p)        { this.victoryExitPos = p; }
    public net.minecraft.core.BlockPos getSurrenderExitPos()            { return surrenderExitPos; }
    public void setSurrenderExitPos(net.minecraft.core.BlockPos p)      { this.surrenderExitPos = p; }

    // ── Visibility ────────────────────────────────────────────────────
    public boolean isHiddenFromPlayers()                                { return hiddenFromPlayers; }
    public void    setHiddenFromPlayers(boolean v)                      { this.hiddenFromPlayers = v; }

    // ── First wave delay ──────────────────────────────────────────────
    public int  getFirstWaveDelaySec()                                  { return firstWaveDelaySec; }
    public void setFirstWaveDelaySec(int s)                             { this.firstWaveDelaySec = Math.max(0, s); }

    // ── Zone particles ────────────────────────────────────────────────
    public String getZoneParticleType()                                 { return zoneParticleType != null ? zoneParticleType : "minecraft:squid_ink"; }
    public void   setZoneParticleType(String t)                         { this.zoneParticleType = (t == null || t.isBlank()) ? null : t; }
    public int    getZoneParticleCount()                                { return zoneParticleCount; }
    public void   setZoneParticleCount(int n)                          { this.zoneParticleCount = Math.max(0, Math.min(64, n)); }
    public float  getZoneParticleSpeed()                               { return zoneParticleSpeed; }
    public void   setZoneParticleSpeed(float s)                        { this.zoneParticleSpeed = Math.max(0f, Math.min(5f, s)); }
    public int    getZoneParticleInterval()                            { return zoneParticleInterval; }
    public void   setZoneParticleInterval(int t)                      { this.zoneParticleInterval = Math.max(1, Math.min(200, t)); }

    // ── PvP extended settings ─────────────────────────────────────────
    public int  getPvpRoundStartDelay()          { return pvpRoundStartDelay; }
    public void setPvpRoundStartDelay(int s)     { this.pvpRoundStartDelay = Math.max(0, s); }
    public int  getPvpRoundStartPoints()         { return pvpRoundStartPoints; }
    public void setPvpRoundStartPoints(int p)    { this.pvpRoundStartPoints = Math.max(0, p); }
    public int  getPvpWinPoints()                { return pvpWinPoints; }
    public void setPvpWinPoints(int p)           { this.pvpWinPoints = Math.max(0, p); }
    public int  getPvpLosePoints()               { return pvpLosePoints; }
    public void setPvpLosePoints(int p)          { this.pvpLosePoints = Math.max(0, p); }
    public int  getDmKillsToWin()                { return dmKillsToWin; }
    public void setDmKillsToWin(int k)           { this.dmKillsToWin = Math.max(1, k); }
    public boolean isPvpWaitEffect()             { return pvpWaitEffect; }
    public void    setPvpWaitEffect(boolean v)   { this.pvpWaitEffect = v; }

    public PvpMode getPvpMode()                  { return pvpMode != null ? pvpMode : PvpMode.STANDARD; }
    public void    setPvpMode(PvpMode m)         { this.pvpMode = m != null ? m : PvpMode.STANDARD; }
    public boolean isDeathmatch()                { return getPvpMode() == PvpMode.DEATHMATCH; }
    public boolean isBattleRoyale()              { return getPvpMode() == PvpMode.BATTLE_ROYALE; }

    public int     getBrBorderRadius()           { return brBorderRadius; }
    public void    setBrBorderRadius(int r)      { this.brBorderRadius = Math.max(10, r); }
    public int     getBrShrinkIntervalSec()      { return brShrinkIntervalSec; }
    public void    setBrShrinkIntervalSec(int s) { this.brShrinkIntervalSec = Math.max(1, s); }
    public String  getBrBorderParticle()         { return brBorderParticle != null ? brBorderParticle : "minecraft:flame"; }
    public void    setBrBorderParticle(String p) { this.brBorderParticle = p; }
    public int     getBrBorderParticleCount()    { return brBorderParticleCount; }
    public void    setBrBorderParticleCount(int n){ this.brBorderParticleCount = Math.max(1, Math.min(32, n)); }
    public boolean isBrBorderDamage()            { return brBorderDamage; }
    public void    setBrBorderDamage(boolean v)  { this.brBorderDamage = v; }
    public float   getBrBorderDamageAmt()        { return brBorderDamageAmt; }
    public void    setBrBorderDamageAmt(float v) { this.brBorderDamageAmt = Math.max(0f, v); }
    public boolean isPvpTeamAutoBalance()        { return pvpTeamAutoBalance; }
    public void    setPvpTeamAutoBalance(boolean v) { this.pvpTeamAutoBalance = v; }

    // ── Enforce gamemode (option) ─────────────────────────────────────
    public boolean isEnforceGameMode()           { return enforceGameMode; }
    public void    setEnforceGameMode(boolean v) { this.enforceGameMode = v; }

    /** Serializes this location to NBT. Delegated to {@link LocationSerializer}. */
    public CompoundTag save() {
        return LocationSerializer.save(this);
    }

    /** Deserializes a location from NBT. Delegated to {@link LocationSerializer}. */
    public static Location load(CompoundTag tag) {
        return LocationSerializer.load(tag);
    }

    /**
     * Resets all player progress in this location.
     * Clears player points and team mappings, but keeps configuration intact.
     */
    public void resetProgress() {
        if (playerPoints != null) {
            playerPoints.clear();
        }
        if (playerTeamMap != null) {
            playerTeamMap.clear();
        }
    }

    /**
     * Checks if this location is currently locked.
     * A locked location prevents new players from entering.
     */
    public boolean isLocked() { return locked; }

    /**
     * Sets the locked state of this location.
     * Persisted via LocationSerializer — survives server restarts.
     * @param locked true to lock, false to unlock
     */
    public void setLocked(boolean locked) { this.locked = locked; }

    /**
     * Gets the wave configuration for this location.
     * Returns the first wave config if multiple exist, or null if none.
     */
    public WaveConfig getWaveConfig() {
        if (waves != null && !waves.isEmpty()) {
            return waves.get(0);
        }
        return null;
    }

    /**
     * Gets the game mode for this location.
     * Defaults to SURVIVAL if not configured.
     */
    public String getGameMode() {
        return enforceGameMode ? "SURVIVAL" : "DEFAULT";
    }

    // ── Mine and Slash compat ──────────────────────────────────────────
    public int  getMasLevel()                { return masLevel; }
    public void setMasLevel(int v)           { this.masLevel = Math.max(0, v); }
    public int  getMasXpBonus()              { return masXpBonus; }
    public void setMasXpBonus(int v)         { this.masXpBonus = Math.max(0, v); }
    public int  getMasFireResist()           { return masFireResist; }
    public void setMasFireResist(int v)      { this.masFireResist = Math.max(0, v); }
    public int  getMasWaterResist()          { return masWaterResist; }
    public void setMasWaterResist(int v)     { this.masWaterResist = Math.max(0, v); }
    public int  getMasLightningResist()      { return masLightningResist; }
    public void setMasLightningResist(int v) { this.masLightningResist = Math.max(0, v); }
    public int  getMasChaosResist()          { return masChaosResist; }
    public void setMasChaosResist(int v)     { this.masChaosResist = Math.max(0, v); }
    public int  getMasPhysicalResist()       { return masPhysicalResist; }
    public void setMasPhysicalResist(int v)  { this.masPhysicalResist = Math.max(0, v); }
}
