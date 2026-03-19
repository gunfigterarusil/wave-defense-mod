package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import com.wavedefense.data.MobSpawnPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class Location {
    private String name;
    private LocationMode mode;
    private BlockPos playerSpawn;
    /** Радіус розкиду PvE гравців навколо playerSpawn (0 = точно на блоці) */
    private int playerSpawnRadius = 0;
    private List<MobSpawnPoint> mobSpawns;
    /** Радіус розкиду мобів навколо точки спавну (0 = за замовчуванням 5 блоків) */
    private int mobSpawnRadius = 5;
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
    // ── Режим магазину ────────────────────────────────────────────────
    public enum ShopMode { GLOBAL, POINT }
    private ShopMode        shopMode   = ShopMode.GLOBAL;   // GLOBAL = звичайний, POINT = точковий
    private List<ShopPoint> shopPoints = new ArrayList<>();  // точки точкового магазину
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
    private int pvpRoundStartDelay = 5;   // секунд від BUY→ACTIVE (підрахунок)
    private int pvpRoundStartPoints = 0;  // поінти кожному гравцю на початку раунду
    private int pvpWinPoints    = 0;      // поінти переможній команді за раунд
    private int pvpLosePoints   = 0;      // поінти команді що програла за раунд
    // ── PvP підрежим ─────────────────────────────────────────────────────
    public enum PvpMode { STANDARD, DEATHMATCH, BATTLE_ROYALE }
    private PvpMode pvpMode = PvpMode.STANDARD;

    // ── Battle Royale налаштування ────────────────────────────────────
    private int     brBorderRadius        = 100;  // початковий радіус кордону (блоків)
    private int     brShrinkIntervalSec   = 30;   // кожні N сек кордон зменшується на 1 блок
    private String  brBorderParticle      = "minecraft:flame";
    private int     brBorderParticleCount = 8;
    private boolean brBorderDamage        = true;
    private float   brBorderDamageAmt     = 1.0f;

    private int     dmKillsToWin  = 10;          // Deathmatch: вбивств для перемоги у раунді
    private boolean pvpWaitEffect = true;       // ефекти slowness+blindness на очікування (замість spectator)
    private boolean pvpTeamAutoBalance = true;  // автобаланс команд при вході/виході
    private boolean enforceGameMode = true;     // примусовий gamemode в локації
    private int startingPoints = 0; // стартові поінти при вході на локацію
    private Map<UUID, String> playerTeamMap;

    // ── Радіус локації та таймер виходу ─────────────────────────────
    // Якщо locationBoundaryEnabled=true — гравець що вийшов за radius отримує наслідки
    private boolean locationBoundaryEnabled = false;
    private int     locationBoundaryRadius  = 50;   // блоків, 1-9999
    private int     locationLeaveTimerSec   = 30;   // секунд на повернення (для TIMER режиму)
    // ── Наслідки перетину кордону ─────────────────────────────────────
    public enum BoundaryConsequence { TIMER_SURRENDER, DAMAGE, TELEPORT_BACK, INSTANT_SURRENDER }
    private BoundaryConsequence boundaryConsequence = BoundaryConsequence.TIMER_SURRENDER;
    private float   boundaryDamagePerSec  = 2.0f;  // шкода за секунду (для DAMAGE режиму)
    // ── Візуал кордону ───────────────────────────────────────────────
    private boolean boundaryParticlesEnabled = false;
    private String  boundaryParticleType  = "minecraft:smoke"; // тип частинок
    private int     boundaryParticleCount = 4;    // частинок на точку
    private int     boundaryParticleHeight = 3;   // висота кільця (блоків)

    // ── Тригер запуску локації ────────────────────────────────────────
    private boolean locationTriggerEnabled  = false;
    private com.wavedefense.data.WaveTrigger locationTriggerType = com.wavedefense.data.WaveTrigger.PLAYER_ENTER_ZONE;

    // ── Портал ────────────────────────────────────────────────────────
    private boolean portalEnabled       = false;
    // Штрафна хвиля: -1 = всі хвилі по порядку, 0+ = індекс конкретної хвилі
    private int     portalPenaltyWave   = -1;
    // Час очікування до штрафної хвилі (тіки, 0 = відразу)
    private int     portalPenaltyTimerSec = 60;
    // Чи зникає портал після проходження локації?
    private boolean portalDisappearsOnComplete = true;
    // Якщо зникає — через скільки секунд зʼявляється знову в іншому місці
    private int     portalRespawnTimerSec = 300;

    // Зберігати лут підібраний в локації після виходу
    private boolean keepLootOnExit = false;

    // ── Перемога — екран та затримка виходу ───────────────────────────
    private boolean victoryScreenEnabled = true;     // показувати екран "Перемога"
    private int     victoryLingerTimeSec  = 30;       // скільки секунд гравці залишаються після перемоги

    // ── Авто-активація зони (розширена) ───────────────────────────────
    // Центр зони: якщо null — використовується playerSpawn
    private net.minecraft.core.BlockPos zoneCenter   = null;
    private int  zoneActivationTimeSec  = 0;    // 0 = миттєво; >0 = таймер після першого гравця в зоні
    // Час (сек) скільки зона залишається відкритою ПІСЛЯ запуску локації (0 = закрити одразу)
    private int  zoneOpenAfterStartSec  = 0;    // 0 = закрити одразу після телепорту
    // useZoneCenter: false = використовуємо playerSpawn як центр, true = використовуємо zoneCenter
    private boolean zoneUsesCustomCenter = false;

    // ── Портал: час відкритості після старту локації ──────────────────
    // 0 = закрити портал одразу після запуску локації
    // >0 = портал залишається відкритим ще N секунд після старту (для запізнілих гравців)
    // -1 = не закривати автоматично (стара поведінка grace period 30 сек)
    private int portalOpenAfterStartSec = -1;

    // КД повторного входу після завершення/здачі
    private int     reEntryCooldownSec = 0;  // 0 = вимкнено

    // Загальна статистика (зберігається між сесіями)
    private long    totalMobsKilledAllTime = 0L;
    private int     totalSessionsCompleted  = 0;

    // ── Інфо-панелі (TextDisplay entities у грі) ──────────────────────
    private InfoPanelSettings infoPanel = new InfoPanelSettings();

    // Точка входу в портал (server-side, не serialized — зберігається у WaveManager.portalEntryPositions)
    // Авто-активація: окрема точка входу (якщо null — використовується playerSpawn)
    private net.minecraft.core.BlockPos autoActivateEntryPos = null;
    // Точки виходу після проходження та здачі (null = повернутись на попереднє місце)
    private net.minecraft.core.BlockPos victoryExitPos  = null;  // після перемоги
    private net.minecraft.core.BlockPos surrenderExitPos = null; // після здачі
    // Прихована від гравців (адміни завжди бачать)
    private boolean hiddenFromPlayers = false;
    // Час до початку першої хвилі після запуску (секунди)
    private int firstWaveDelaySec = 0;
    // Частинки навколо зони входу (null = SQUID_INK за замовчуванням)
    private String zoneParticleType  = null; // registry id, напр. "minecraft:flame"
    private int    zoneParticleCount = 0;   // 0 = авто (radius*2, min 6, max 12)
    private float  zoneParticleSpeed = 0.02f; // швидкість частинок (delta movement)
    private int    zoneParticleInterval = 1;  // кожні N тіків спавнити частинки (1=кожен тік, 20=раз/сек)
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
        if (playerSpawnRadius > 0) tag.putInt("playerSpawnRadius", playerSpawnRadius);
        if (mobSpawnRadius != 5)   tag.putInt("mobSpawnRadius", mobSpawnRadius);

        ListTag mobSpawnsList = new ListTag();
        for (MobSpawnPoint sp : mobSpawns) {
            mobSpawnsList.add(sp.save());
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

        // Режим магазину + точки
        tag.putString("shopMode", getShopMode().name());
        ListTag shopPointsList = new ListTag();
        for (ShopPoint sp : shopPoints) shopPointsList.add(sp.save());
        tag.put("shopPoints", shopPointsList);

        ListTag completionRewardsList = new ListTag();
        for (ShopItem item : completionRewards) completionRewardsList.add(item.save());
        tag.put("completionRewards", completionRewardsList);

        ListTag lootSpawnsList = new ListTag();
        for (LootSpawn ls : lootSpawns) lootSpawnsList.add(ls.save());
        tag.put("lootSpawns", lootSpawnsList);

        // Boundary
        tag.putBoolean("locationBoundaryEnabled", locationBoundaryEnabled);
        tag.putInt("locationBoundaryRadius", locationBoundaryRadius);
        tag.putString("boundaryConsequence", getBoundaryConsequence().name());
        tag.putFloat("boundaryDamagePerSec", boundaryDamagePerSec);
        tag.putBoolean("boundaryParticlesEnabled", boundaryParticlesEnabled);
        tag.putString("boundaryParticleType", getBoundaryParticleType());
        tag.putInt("boundaryParticleCount", boundaryParticleCount);
        tag.putInt("boundaryParticleHeight", boundaryParticleHeight);
        tag.putInt("locationLeaveTimerSec", locationLeaveTimerSec);
        // Location trigger
        tag.putBoolean("locationTriggerEnabled", locationTriggerEnabled);
        tag.putString("locationTriggerType", locationTriggerType.name());
        // Portal
        tag.putBoolean("portalEnabled", portalEnabled);
        tag.putInt("portalPenaltyWave", portalPenaltyWave);
        tag.putInt("portalPenaltyTimerSec", portalPenaltyTimerSec);
        tag.putBoolean("portalDisappearsOnComplete", portalDisappearsOnComplete);
        tag.putBoolean("keepLootOnExit", keepLootOnExit);
        if (autoActivateEntryPos != null) tag.putLong("autoActivateEntryPos", autoActivateEntryPos.asLong());
        tag.putInt("portalRespawnTimerSec", portalRespawnTimerSec);
        tag.putInt("reEntryCooldownSec", reEntryCooldownSec);
        tag.putLong("totalMobsKilledAllTime", totalMobsKilledAllTime);
        tag.putInt("totalSessionsCompleted", totalSessionsCompleted);
        // Victory
        tag.putBoolean("victoryScreenEnabled", victoryScreenEnabled);
        tag.putInt("victoryLingerTimeSec", victoryLingerTimeSec);
        // Zone activation extended
        if (zoneCenter != null) tag.putLong("zoneCenter", zoneCenter.asLong());
        tag.putBoolean("zoneUsesCustomCenter", zoneUsesCustomCenter);
        tag.putInt("zoneActivationTimeSec", zoneActivationTimeSec);
        tag.putInt("zoneOpenAfterStartSec", zoneOpenAfterStartSec);
        // Portal open-after-start
        tag.putInt("portalOpenAfterStartSec", portalOpenAfterStartSec);
        // ── New fields v0.2.18 ────────────────────────────────────────
        if (victoryExitPos  != null) tag.putLong("victoryExitPos",  victoryExitPos.asLong());
        if (surrenderExitPos != null) tag.putLong("surrenderExitPos", surrenderExitPos.asLong());
        tag.putBoolean("hiddenFromPlayers", hiddenFromPlayers);
        tag.putInt("firstWaveDelaySec", firstWaveDelaySec);
        if (zoneParticleType != null) tag.putString("zoneParticleType", zoneParticleType);
        if (zoneParticleCount > 0) tag.putInt("zoneParticleCount", zoneParticleCount);
        tag.putFloat("zoneParticleSpeed", zoneParticleSpeed);  // завжди зберігаємо
        if (zoneParticleInterval != 1) tag.putInt("zoneParticleInterval", zoneParticleInterval);
        if (infoPanel != null) tag.put("infoPanel", infoPanel.save());
        // ── PvP extended ──────────────────────────────────────────────
        tag.putInt("pvpRoundStartDelay",  pvpRoundStartDelay);
        tag.putInt("pvpRoundStartPoints", pvpRoundStartPoints);
        tag.putInt("pvpWinPoints",        pvpWinPoints);
        tag.putInt("pvpLosePoints",       pvpLosePoints);
        tag.putInt("dmKillsToWin",            dmKillsToWin);
        tag.putBoolean("pvpWaitEffect",       pvpWaitEffect);
        tag.putString("pvpMode",              getPvpMode().name());
        tag.putInt("brBorderRadius",          brBorderRadius);
        tag.putInt("brShrinkIntervalSec",     brShrinkIntervalSec);
        if (brBorderParticle != null) tag.putString("brBorderParticle", brBorderParticle);
        tag.putInt("brBorderParticleCount",   brBorderParticleCount);
        tag.putBoolean("brBorderDamage",      brBorderDamage);
        tag.putFloat("brBorderDamageAmt",     brBorderDamageAmt);
        tag.putBoolean("pvpTeamAutoBalance",  pvpTeamAutoBalance);
        tag.putBoolean("enforceGameMode",     enforceGameMode);
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
        location.playerSpawnRadius = tag.contains("playerSpawnRadius") ? tag.getInt("playerSpawnRadius") : 0;
        location.mobSpawnRadius    = tag.contains("mobSpawnRadius")    ? tag.getInt("mobSpawnRadius")    : 5;

        ListTag mobSpawnsList = tag.getList("mobSpawns", 10);
        for (int i = 0; i < mobSpawnsList.size(); i++) {
            CompoundTag spTag = mobSpawnsList.getCompound(i);
            // backward compat: old format saved "pos" as asLong in nested tag, new saves full MobSpawnPoint
            if (spTag.contains("radius") || (spTag.contains("pos") && !spTag.contains("x"))) {
                if (spTag.contains("pos") && spTag.get("pos").getId() == 4) {
                    // new format: pos stored as long
                    location.mobSpawns.add(MobSpawnPoint.load(spTag));
                } else {
                    location.mobSpawns.add(MobSpawnPoint.fromBlockPos(BlockPos.of(spTag.getLong("pos"))));
                }
            } else {
                location.mobSpawns.add(MobSpawnPoint.fromBlockPos(BlockPos.of(spTag.getLong("pos"))));
            }
        }

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

        // Режим магазину + точки
        if (tag.contains("shopMode")) {
            try { location.shopMode = ShopMode.valueOf(tag.getString("shopMode")); }
            catch (Exception e) { location.shopMode = ShopMode.GLOBAL; }
        }
        if (tag.contains("shopPoints")) {
            ListTag spList = tag.getList("shopPoints", 10);
            for (int i = 0; i < spList.size(); i++) location.shopPoints.add(ShopPoint.load(spList.getCompound(i)));
        }

        ListTag crList = tag.getList("completionRewards", 10);
        for (int i = 0; i < crList.size(); i++) location.completionRewards.add(ShopItem.load(crList.getCompound(i)));

        // Boundary
        location.locationBoundaryEnabled = tag.contains("locationBoundaryEnabled") && tag.getBoolean("locationBoundaryEnabled");
        if (tag.contains("boundaryConsequence")) {
            try { location.boundaryConsequence = BoundaryConsequence.valueOf(tag.getString("boundaryConsequence")); }
            catch (Exception ignored) {}
        }
        location.boundaryDamagePerSec    = tag.contains("boundaryDamagePerSec")    ? tag.getFloat("boundaryDamagePerSec")    : 2.0f;
        location.boundaryParticlesEnabled= tag.contains("boundaryParticlesEnabled") && tag.getBoolean("boundaryParticlesEnabled");
        location.boundaryParticleType    = tag.contains("boundaryParticleType")    ? tag.getString("boundaryParticleType")   : "minecraft:smoke";
        location.boundaryParticleCount   = tag.contains("boundaryParticleCount")   ? tag.getInt("boundaryParticleCount")     : 4;
        location.boundaryParticleHeight  = tag.contains("boundaryParticleHeight")  ? tag.getInt("boundaryParticleHeight")    : 3;
        location.locationBoundaryRadius  = tag.contains("locationBoundaryRadius")  ? tag.getInt("locationBoundaryRadius")  : 50;
        location.locationLeaveTimerSec   = tag.contains("locationLeaveTimerSec")   ? tag.getInt("locationLeaveTimerSec")   : 30;
        // Location trigger
        location.locationTriggerEnabled  = tag.contains("locationTriggerEnabled") && tag.getBoolean("locationTriggerEnabled");
        if (tag.contains("locationTriggerType")) {
            try { location.locationTriggerType = com.wavedefense.data.WaveTrigger.valueOf(tag.getString("locationTriggerType")); }
            catch (Exception ignored) {}
        }
        // Portal
        location.portalEnabled            = tag.contains("portalEnabled") && tag.getBoolean("portalEnabled");
        location.portalPenaltyWave        = tag.contains("portalPenaltyWave")        ? tag.getInt("portalPenaltyWave")        : -1;
        location.portalPenaltyTimerSec    = tag.contains("portalPenaltyTimerSec")    ? tag.getInt("portalPenaltyTimerSec")    : 60;
        location.portalDisappearsOnComplete= tag.contains("portalDisappearsOnComplete") ? tag.getBoolean("portalDisappearsOnComplete") : true;
        location.portalRespawnTimerSec    = tag.contains("portalRespawnTimerSec")    ? tag.getInt("portalRespawnTimerSec")    : 300;
        location.keepLootOnExit           = tag.contains("keepLootOnExit") && tag.getBoolean("keepLootOnExit");
        if (tag.contains("autoActivateEntryPos")) location.autoActivateEntryPos = net.minecraft.core.BlockPos.of(tag.getLong("autoActivateEntryPos"));
        location.reEntryCooldownSec = tag.contains("reEntryCooldownSec") ? tag.getInt("reEntryCooldownSec") : 0;
        location.totalMobsKilledAllTime = tag.contains("totalMobsKilledAllTime") ? tag.getLong("totalMobsKilledAllTime") : 0L;
        location.totalSessionsCompleted = tag.contains("totalSessionsCompleted") ? tag.getInt("totalSessionsCompleted") : 0;
        if (tag.contains("infoPanel")) location.infoPanel = InfoPanelSettings.load(tag.getCompound("infoPanel"));
        else location.infoPanel = new InfoPanelSettings();
        // Victory
        location.victoryScreenEnabled = !tag.contains("victoryScreenEnabled") || tag.getBoolean("victoryScreenEnabled");
        location.victoryLingerTimeSec = tag.contains("victoryLingerTimeSec") ? tag.getInt("victoryLingerTimeSec") : 30;
        // Zone extended
        if (tag.contains("zoneCenter")) location.zoneCenter = net.minecraft.core.BlockPos.of(tag.getLong("zoneCenter"));
        location.zoneUsesCustomCenter = tag.contains("zoneUsesCustomCenter") && tag.getBoolean("zoneUsesCustomCenter");
        location.zoneActivationTimeSec = tag.contains("zoneActivationTimeSec") ? tag.getInt("zoneActivationTimeSec") : 0;
        location.zoneOpenAfterStartSec = tag.contains("zoneOpenAfterStartSec") ? tag.getInt("zoneOpenAfterStartSec") : 0;
        // Portal open-after-start
        location.portalOpenAfterStartSec = tag.contains("portalOpenAfterStartSec") ? tag.getInt("portalOpenAfterStartSec") : -1;
        // ── New fields v0.2.18 ────────────────────────────────────────
        if (tag.contains("victoryExitPos"))  location.victoryExitPos  = net.minecraft.core.BlockPos.of(tag.getLong("victoryExitPos"));
        if (tag.contains("surrenderExitPos")) location.surrenderExitPos = net.minecraft.core.BlockPos.of(tag.getLong("surrenderExitPos"));
        location.hiddenFromPlayers = tag.contains("hiddenFromPlayers") && tag.getBoolean("hiddenFromPlayers");
        location.firstWaveDelaySec = tag.contains("firstWaveDelaySec") ? tag.getInt("firstWaveDelaySec") : 0;
        location.zoneParticleType  = tag.contains("zoneParticleType")  ? tag.getString("zoneParticleType") : null;
        location.zoneParticleCount = tag.contains("zoneParticleCount") ? tag.getInt("zoneParticleCount") : 0;
        location.zoneParticleSpeed = tag.contains("zoneParticleSpeed") ? tag.getFloat("zoneParticleSpeed") : 0.02f;
        location.zoneParticleInterval = tag.contains("zoneParticleInterval") ? tag.getInt("zoneParticleInterval") : 1;
        // ── PvP extended ──────────────────────────────────────────────
        location.pvpRoundStartDelay  = tag.contains("pvpRoundStartDelay")  ? tag.getInt("pvpRoundStartDelay")  : 5;
        location.pvpRoundStartPoints = tag.contains("pvpRoundStartPoints") ? tag.getInt("pvpRoundStartPoints") : 0;
        location.pvpWinPoints        = tag.contains("pvpWinPoints")        ? tag.getInt("pvpWinPoints")        : 0;
        location.pvpLosePoints       = tag.contains("pvpLosePoints")       ? tag.getInt("pvpLosePoints")       : 0;
        location.dmKillsToWin        = tag.contains("dmKillsToWin")         ? tag.getInt("dmKillsToWin")         : 10;
        location.pvpWaitEffect       = !tag.contains("pvpWaitEffect")      || tag.getBoolean("pvpWaitEffect");
        if (tag.contains("pvpMode")) {
            try { location.pvpMode = PvpMode.valueOf(tag.getString("pvpMode")); }
            catch (Exception ignored) { location.pvpMode = PvpMode.STANDARD; }
        }
        location.brBorderRadius        = tag.contains("brBorderRadius")        ? tag.getInt("brBorderRadius")        : 100;
        location.brShrinkIntervalSec   = tag.contains("brShrinkIntervalSec")   ? tag.getInt("brShrinkIntervalSec")   : 30;
        location.brBorderParticle      = tag.contains("brBorderParticle")      ? tag.getString("brBorderParticle")   : "minecraft:flame";
        location.brBorderParticleCount = tag.contains("brBorderParticleCount") ? tag.getInt("brBorderParticleCount") : 8;
        location.brBorderDamage        = !tag.contains("brBorderDamage")       || tag.getBoolean("brBorderDamage");
        location.brBorderDamageAmt     = tag.contains("brBorderDamageAmt")     ? tag.getFloat("brBorderDamageAmt")   : 1.0f;
        location.pvpTeamAutoBalance  = !tag.contains("pvpTeamAutoBalance") || tag.getBoolean("pvpTeamAutoBalance");
        location.enforceGameMode     = !tag.contains("enforceGameMode")    || tag.getBoolean("enforceGameMode");

        if (tag.contains("lootSpawns")) {
            ListTag lsList = tag.getList("lootSpawns", 10);
            for (int i = 0; i < lsList.size(); i++) location.lootSpawns.add(LootSpawn.load(lsList.getCompound(i)));
        }

        return location;
    }
}
