package com.wavedefense.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.item.ItemStack;

import java.util.*;

public class Location {
    // Fields are package-private so LocationSerializer (same package) can access them
    // directly — avoids needing setters for every deserialized value.
    String name;
    LocationMode mode;
    BlockPos playerSpawn;
    /** Scatter radius for PvE players around playerSpawn (0 = exact block). */
    int playerSpawnRadius = 0;
    List<MobSpawnPoint> mobSpawns;
    /** Scatter radius for mobs around their spawn point (0 = default 5 blocks). */
    int mobSpawnRadius = 5;
    List<WaveConfig> waves;
    int totalWaves;
    int timeBetweenWaves;
    Map<UUID, Integer> playerPoints;
    boolean keepInventory;
    // Auto-activation zone (PvE only)
    boolean autoActivate = false;
    int autoActivateRadius = 5; // blocks
    List<ItemStack> startingItems;
    List<ShopItem> shopItems;
    List<ShopItem> completionRewards;
    // ── Shop mode ─────────────────────────────────────────────────────
    public enum ShopMode { GLOBAL, POINT }
    ShopMode        shopMode   = ShopMode.GLOBAL;   // GLOBAL = standard, POINT = multi-point
    List<ShopPoint> shopPoints = new ArrayList<>();  // point-based shop entries
    int completionPointsReward;
    List<LootSpawn> lootSpawns;

    // PvP fields
    List<PvpSpawnPoint> pvpSpawnPoints;
    int pvpMinPlayers;
    boolean pvpFriendlyFire;
    int pvpKillPoints;
    int pvpDeathPenalty;
    int pvpTotalRounds;   // number of rounds (0 = infinite)
    int pvpBuyTime;        // buy-phase duration between rounds (seconds)
    int pvpRoundStartDelay = 5;   // seconds from BUY→ACTIVE (countdown)
    int pvpReadyCheckTimeoutSec = 60; // v0.2.61: READY_CHECK timeout before force-start (0 = wait forever)
    int pvpRoundStartPoints = 0;  // points awarded to every player at round start
    int pvpWinPoints    = 0;      // points awarded to the winning team per round
    int pvpLosePoints   = 0;      // points awarded to the losing team per round
    // Round / match time limit (sec, 0 = no limit).
    // Standard: defender wins on timeout → team with more alive players wins (draw if equal).
    // Deathmatch: leading team wins on timeout (draw if tied).
    int pvpRoundTimeLimitSec = 0;
    // ── PvP sub-mode ─────────────────────────────────────────────────────
    public enum PvpMode { STANDARD, DEATHMATCH, BATTLE_ROYALE, CAPTURE_THE_POINT, KING_OF_THE_HILL }
    PvpMode pvpMode = PvpMode.STANDARD;

    // ── Battle Royale settings ────────────────────────────────────────
    int     brBorderRadius        = 100;  // starting border radius (blocks)
    int     brShrinkIntervalSec   = 5;    // border shrinks every N seconds (default 5 — was 30, too slow)
    int     brShrinkAmountBlocks  = 1;    // how many blocks to shrink per step
    int     brInitialWaitSec      = 30;   // initial wait before the border starts shrinking
    int     brFinalRadius         = 5;    // border stops shrinking at this radius
    String  brBorderParticle      = "minecraft:flame";
    int     brBorderParticleCount = 8;
    boolean brBorderDamage        = true;
    float   brBorderDamageAmt     = 1.0f;

    int     dmKillsToWin  = 10;          // Deathmatch: kills to win a round
    /**
     * Deathmatch spawn mode (where players appear after death).
     *   TEAM_SPAWN    — always spawn at the player's team spawn point (legacy default; camper-prone)
     *   RANDOM_SPAWN  — random PvpSpawnPoint each respawn
     *   SMART_SPAWN   — random from up to 10 candidates; pick one ≥10 blocks from nearest enemy
     */
    public enum DmSpawnMode { TEAM_SPAWN, RANDOM_SPAWN, SMART_SPAWN }
    DmSpawnMode dmSpawnMode = DmSpawnMode.TEAM_SPAWN;

    // ── Capture the Point / King of the Hill ─────────────────────────────
    List<CapturePoint> capturePoints    = new ArrayList<>();
    int  ctpScoreToWin      = 200;
    int  ctpScorePerSec     = 1;
    boolean ctpFirstToScore = true;    // true = first team to scoreToWin wins; false = timer
    int  ctpRoundDurationSec = 300;
    int  kothScoreToWin     = 300;
    int  kothScorePerSec    = 1;
    boolean kothFirstToScore = true;
    int  kothRoundDurationSec = 300;
    // B3: CtP — multiply capture progress by team count standing on point (capped at 4×).
    boolean ctpSpeedMultiplier = false;
    // B4: CtP — additional win condition: one team owns ALL capture points simultaneously.
    boolean ctpCaptureAllWin   = false;
    // KotH true "hold timer" mode (TF2/Halo style).
    // When kothHoldMode=true the win condition is "hold the hill for kothHoldDurationSec
    // total/consecutive seconds" — score-based settings above are ignored.
    boolean kothHoldMode        = false;
    int     kothHoldDurationSec = 180;  // 3 minutes (TF2 default)
    boolean kothResetOnLoss     = false; // false = Halo style (timer freezes); true = TF2 style (timer resets)
    boolean pvpWaitEffect = true;       // apply slowness+blindness while waiting (instead of spectator)
    boolean pvpTeamAutoBalance = true;  // auto-balance teams on join/leave
    boolean enforceGameMode = true;     // force the configured game mode in the location
    int startingPoints = 0; // points awarded to each player when joining the location
    Map<UUID, String> playerTeamMap;

    // ── Location bbox (optional area) ────────────────────────────────
    // When set, the location has a defined 3D box area (e.g. for tactical PvP minimap).
    // Both corners must be non-null for bbox-dependent features to activate.
    // bbox is independent of the (radius-based) boundary system — admin can use either or both.
    net.minecraft.util.math.BlockPos bboxMin = null;
    net.minecraft.util.math.BlockPos bboxMax = null;
    boolean minimapEnabled = false;     // show tactical minimap in PvP
    boolean bboxOutlineEnabled = false; // show particle outline of bbox top edges in-world

    // ── Location boundary and leave timer ────────────────────────────
    // When locationBoundaryEnabled=true, players who leave the radius face a consequence.
    boolean locationBoundaryEnabled = false;
    int     locationBoundaryRadius  = 50;   // blocks, 1-9999
    int     locationLeaveTimerSec   = 30;   // seconds to return (for TIMER mode)
    // ── Boundary consequence ──────────────────────────────────────────
    public enum BoundaryConsequence { TIMER_SURRENDER, DAMAGE, TELEPORT_BACK, INSTANT_SURRENDER }
    BoundaryConsequence boundaryConsequence = BoundaryConsequence.TIMER_SURRENDER;
    float   boundaryDamagePerSec  = 2.0f;  // damage per second (for DAMAGE mode)
    // ── Boundary visual ───────────────────────────────────────────────
    boolean boundaryParticlesEnabled = false;
    String  boundaryParticleType  = "minecraft:smoke"; // particle type
    int     boundaryParticleCount = 4;    // particles per point
    int     boundaryParticleHeight = 3;   // ring height (blocks)

    // ── Location activation trigger ───────────────────────────────────
    boolean locationTriggerEnabled  = false;
    com.wavedefense.data.WaveTrigger locationTriggerType = com.wavedefense.data.WaveTrigger.PLAYER_ENTER_ZONE;

    // ── Portal ────────────────────────────────────────────────────────
    boolean portalEnabled       = false;
    // Penalty wave: -1 = all waves in order, 0+ = index of a specific wave
    int     portalPenaltyWave   = -1;
    // Delay before the penalty wave spawns (seconds, 0 = immediately)
    int     portalPenaltyTimerSec = 60;
    // Whether the portal disappears after the location is completed
    boolean portalDisappearsOnComplete = true;
    // If it disappears — how many seconds until it reappears elsewhere
    int     portalRespawnTimerSec = 300;

    // Keep loot picked up in the location after leaving
    boolean keepLootOnExit = false;

    // ── Victory — screen and linger delay ─────────────────────────────
    boolean victoryScreenEnabled = true;     // show the "Victory" screen
    int     victoryLingerTimeSec  = 30;       // how many seconds players remain after victory

    // ── Auto-activation zone (extended) ───────────────────────────────
    // Zone centre: if null, playerSpawn is used
    net.minecraft.util.math.BlockPos zoneCenter   = null;
    int  zoneActivationTimeSec  = 0;    // 0 = instant; >0 = timer after first player enters zone
    // How long (sec) the zone stays open AFTER the location starts (0 = close immediately)
    int  zoneOpenAfterStartSec  = 0;    // 0 = close immediately after teleport
    // false = use playerSpawn as zone centre; true = use zoneCenter
    boolean zoneUsesCustomCenter = false;

    // ── Portal: open duration after location starts ───────────────────
    // 0 = close portal immediately after location starts
    // >0 = portal stays open N more seconds after start (for late joiners)
    // -1 = do not close automatically (legacy grace-period behaviour)
    int portalOpenAfterStartSec = -1;

    // Re-entry cooldown after completion/surrender
    int     reEntryCooldownSec = 0;  // 0 = disabled

    // Cumulative stats (persisted across sessions)
    long    totalMobsKilledAllTime = 0L;
    int     totalSessionsCompleted  = 0;

    // ── Info panels (TextDisplay entities in-game) ────────────────────
    InfoPanelSettings infoPanel = new InfoPanelSettings();

    // Portal entry point (server-side; not serialized — stored in WaveManager.portalEntryPositions)
    // Auto-activation: separate entry point (if null, playerSpawn is used)
    net.minecraft.util.math.BlockPos autoActivateEntryPos = null;
    // Exit points after completion and surrender (null = return to previous position)
    net.minecraft.util.math.BlockPos victoryExitPos  = null;  // after victory
    net.minecraft.util.math.BlockPos surrenderExitPos = null; // after surrender
    // Hidden from players (admins always see it)
    boolean hiddenFromPlayers = false;
    // Delay before the first wave starts after the lobby ends (seconds)
    int firstWaveDelaySec = 0;
    // Particles around the entry zone (null = SQUID_INK default)
    String zoneParticleType  = null; // registry id, e.g. "minecraft:flame"
    int    zoneParticleCount = 0;   // 0 = auto (radius*2, min 6, max 12)
    float  zoneParticleSpeed = 0.02f; // particle speed (delta movement)
    int    zoneParticleInterval = 1;  // spawn particles every N ticks (1=every tick, 20=once/sec)

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

    // Lobby timer (shown on the entry-zone info panel) — already stored in zoneActivationTimeSec.

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

    // ── BBox area (optional, for tactical minimap) ───────────────────
    public net.minecraft.util.math.BlockPos getBboxMin() { return bboxMin; }
    public void setBboxMin(net.minecraft.util.math.BlockPos p) { this.bboxMin = p; }
    public net.minecraft.util.math.BlockPos getBboxMax() { return bboxMax; }
    public void setBboxMax(net.minecraft.util.math.BlockPos p) { this.bboxMax = p; }
    public boolean hasBbox() { return bboxMin != null && bboxMax != null; }
    public boolean isMinimapEnabled() { return minimapEnabled && hasBbox(); }
    public void setMinimapEnabled(boolean v) { this.minimapEnabled = v; }
    public boolean isBboxOutlineEnabled() { return bboxOutlineEnabled && hasBbox(); }
    public void setBboxOutlineEnabled(boolean v) { this.bboxOutlineEnabled = v; }
    /** @return min-corner of the bbox with components min()-ed pairwise (handles arbitrary corner pairs) */
    public net.minecraft.util.math.BlockPos getBboxLowCorner() {
        if (!hasBbox()) return null;
        return new net.minecraft.util.math.BlockPos(
            Math.min(bboxMin.getX(), bboxMax.getX()),
            Math.min(bboxMin.getY(), bboxMax.getY()),
            Math.min(bboxMin.getZ(), bboxMax.getZ()));
    }
    /** @return max-corner of the bbox with components max()-ed pairwise */
    public net.minecraft.util.math.BlockPos getBboxHighCorner() {
        if (!hasBbox()) return null;
        return new net.minecraft.util.math.BlockPos(
            Math.max(bboxMin.getX(), bboxMax.getX()),
            Math.max(bboxMin.getY(), bboxMax.getY()),
            Math.max(bboxMin.getZ(), bboxMax.getZ()));
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

    public net.minecraft.util.math.BlockPos getAutoActivateEntryPos() { return autoActivateEntryPos; }
    public void setAutoActivateEntryPos(net.minecraft.util.math.BlockPos pos) { this.autoActivateEntryPos = pos; }

    // ── Victory screen ────────────────────────────────────────────────
    public boolean isVictoryScreenEnabled()         { return victoryScreenEnabled; }
    public void    setVictoryScreenEnabled(boolean v){ this.victoryScreenEnabled = v; }
    public int     getVictoryLingerTimeSec()         { return victoryLingerTimeSec; }
    public void    setVictoryLingerTimeSec(int s)    { this.victoryLingerTimeSec = Math.max(0, s); }

    // ── Zone activation extended ──────────────────────────────────────
    public net.minecraft.util.math.BlockPos getZoneCenter() { return zoneCenter; }
    public void    setZoneCenter(net.minecraft.util.math.BlockPos p) { this.zoneCenter = p; }
    public boolean isZoneUsesCustomCenter()          { return zoneUsesCustomCenter; }
    public void    setZoneUsesCustomCenter(boolean v){ this.zoneUsesCustomCenter = v; }
    public int     getZoneActivationTimeSec()        { return zoneActivationTimeSec; }
    public void    setZoneActivationTimeSec(int s)   { this.zoneActivationTimeSec = Math.max(0, s); }
    public int     getZoneOpenAfterStartSec()        { return zoneOpenAfterStartSec; }
    public void    setZoneOpenAfterStartSec(int s)   { this.zoneOpenAfterStartSec = Math.max(0, s); }
    /** Центр зони активації: якщо кастомний — zoneCenter, інакше playerSpawn */
    public net.minecraft.util.math.BlockPos getEffectiveZoneCenter() {
        if (zoneUsesCustomCenter && zoneCenter != null) return zoneCenter;
        return playerSpawn; // fallback to playerSpawn
    }

    // ── Portal open-after-start ───────────────────────────────────────
    public int  getPortalOpenAfterStartSec()         { return portalOpenAfterStartSec; }
    public void setPortalOpenAfterStartSec(int s)    { this.portalOpenAfterStartSec = s; }

    // ── Exit points ───────────────────────────────────────────────────
    public net.minecraft.util.math.BlockPos getVictoryExitPos()              { return victoryExitPos; }
    public void setVictoryExitPos(net.minecraft.util.math.BlockPos p)        { this.victoryExitPos = p; }
    public net.minecraft.util.math.BlockPos getSurrenderExitPos()            { return surrenderExitPos; }
    public void setSurrenderExitPos(net.minecraft.util.math.BlockPos p)      { this.surrenderExitPos = p; }

    // ── Visibility ────────────────────────────────────────────────────
    public boolean isHiddenFromPlayers()                                { return hiddenFromPlayers; }
    public void    setHiddenFromPlayers(boolean v)                      { this.hiddenFromPlayers = v; }

    // ── First wave delay ──────────────────────────────────────────────
    public int  getFirstWaveDelaySec()                                  { return firstWaveDelaySec; }
    public void setFirstWaveDelaySec(int s)                             { this.firstWaveDelaySec = Math.max(0, s); }

    // ── Zone particles ────────────────────────────────────────────────
    public String getZoneParticleType()                                 { return zoneParticleType != null ? zoneParticleType : "minecraft:squid_ink"; }
    public void   setZoneParticleType(String t)                         { this.zoneParticleType = (t == null || t.trim().isEmpty()) ? null : t; }
    public int    getZoneParticleCount()                                { return zoneParticleCount; }
    public void   setZoneParticleCount(int n)                          { this.zoneParticleCount = Math.max(0, Math.min(64, n)); }
    public float  getZoneParticleSpeed()                               { return zoneParticleSpeed; }
    public void   setZoneParticleSpeed(float s)                        { this.zoneParticleSpeed = Math.max(0f, Math.min(5f, s)); }
    public int    getZoneParticleInterval()                            { return zoneParticleInterval; }
    public void   setZoneParticleInterval(int t)                      { this.zoneParticleInterval = Math.max(1, Math.min(200, t)); }

    // ── PvP extended settings ─────────────────────────────────────────
    public int  getPvpRoundStartDelay()          { return pvpRoundStartDelay; }
    public void setPvpRoundStartDelay(int s)     { this.pvpRoundStartDelay = Math.max(0, s); }
    public int  getPvpReadyCheckTimeoutSec()         { return pvpReadyCheckTimeoutSec; }
    public void setPvpReadyCheckTimeoutSec(int s)    { this.pvpReadyCheckTimeoutSec = Math.max(0, s); }
    public int  getPvpRoundStartPoints()         { return pvpRoundStartPoints; }
    public void setPvpRoundStartPoints(int p)    { this.pvpRoundStartPoints = Math.max(0, p); }
    public int  getPvpWinPoints()                { return pvpWinPoints; }
    public void setPvpWinPoints(int p)           { this.pvpWinPoints = Math.max(0, p); }
    public int  getPvpLosePoints()               { return pvpLosePoints; }
    public void setPvpLosePoints(int p)          { this.pvpLosePoints = Math.max(0, p); }
    public int  getPvpRoundTimeLimitSec()        { return pvpRoundTimeLimitSec; }
    public void setPvpRoundTimeLimitSec(int s)   { this.pvpRoundTimeLimitSec = Math.max(0, s); }
    public int  getDmKillsToWin()                { return dmKillsToWin; }
    public void setDmKillsToWin(int k)           { this.dmKillsToWin = Math.max(1, k); }
    public DmSpawnMode getDmSpawnMode()          { return dmSpawnMode != null ? dmSpawnMode : DmSpawnMode.TEAM_SPAWN; }
    public void setDmSpawnMode(DmSpawnMode m)    { this.dmSpawnMode = m != null ? m : DmSpawnMode.TEAM_SPAWN; }
    public boolean isPvpWaitEffect()             { return pvpWaitEffect; }
    public void    setPvpWaitEffect(boolean v)   { this.pvpWaitEffect = v; }

    public PvpMode getPvpMode()                  { return pvpMode != null ? pvpMode : PvpMode.STANDARD; }
    public void    setPvpMode(PvpMode m)         { this.pvpMode = m != null ? m : PvpMode.STANDARD; }
    public boolean isDeathmatch()                { return getPvpMode() == PvpMode.DEATHMATCH; }
    public boolean isBattleRoyale()              { return getPvpMode() == PvpMode.BATTLE_ROYALE; }
    public boolean isCtpMode()                   { return getPvpMode() == PvpMode.CAPTURE_THE_POINT; }
    public boolean isKothMode()                  { return getPvpMode() == PvpMode.KING_OF_THE_HILL; }
    public boolean isObjectiveMode()             { return isCtpMode() || isKothMode(); }

    // ── Capture the Point / King of the Hill ─────────────────────────────
    public List<CapturePoint> getCapturePoints()              { return capturePoints; }
    public void addCapturePoint(CapturePoint cp)              { capturePoints.add(cp); }
    public void removeCapturePoint(int i)                     { if (i >= 0 && i < capturePoints.size()) capturePoints.remove(i); }

    public int  getCtpScoreToWin()                            { return ctpScoreToWin; }
    public void setCtpScoreToWin(int v)                       { this.ctpScoreToWin = Math.max(1, v); }
    public int  getCtpScorePerSec()                           { return ctpScorePerSec; }
    public void setCtpScorePerSec(int v)                      { this.ctpScorePerSec = Math.max(1, v); }
    public boolean isCtpFirstToScore()                        { return ctpFirstToScore; }
    public void setCtpFirstToScore(boolean v)                 { this.ctpFirstToScore = v; }
    public int  getCtpRoundDurationSec()                      { return ctpRoundDurationSec; }
    public void setCtpRoundDurationSec(int v)                 { this.ctpRoundDurationSec = Math.max(30, v); }
    public boolean isCtpSpeedMultiplier()                     { return ctpSpeedMultiplier; }
    public void setCtpSpeedMultiplier(boolean v)              { this.ctpSpeedMultiplier = v; }
    public boolean isCtpCaptureAllWin()                       { return ctpCaptureAllWin; }
    public void setCtpCaptureAllWin(boolean v)                { this.ctpCaptureAllWin = v; }

    public int  getKothScoreToWin()                           { return kothScoreToWin; }
    public void setKothScoreToWin(int v)                      { this.kothScoreToWin = Math.max(1, v); }
    public int  getKothScorePerSec()                          { return kothScorePerSec; }
    public void setKothScorePerSec(int v)                     { this.kothScorePerSec = Math.max(1, v); }
    public boolean isKothFirstToScore()                       { return kothFirstToScore; }
    public void setKothFirstToScore(boolean v)                { this.kothFirstToScore = v; }
    public int  getKothRoundDurationSec()                     { return kothRoundDurationSec; }
    public void setKothRoundDurationSec(int v)                { this.kothRoundDurationSec = Math.max(30, v); }
    public boolean isKothHoldMode()                           { return kothHoldMode; }
    public void setKothHoldMode(boolean v)                    { this.kothHoldMode = v; }
    public int  getKothHoldDurationSec()                      { return kothHoldDurationSec; }
    public void setKothHoldDurationSec(int v)                 { this.kothHoldDurationSec = Math.max(10, v); }
    public boolean isKothResetOnLoss()                        { return kothResetOnLoss; }
    public void setKothResetOnLoss(boolean v)                 { this.kothResetOnLoss = v; }

    /** Returns the effective score-to-win for the current objective mode. */
    public int getObjectiveScoreToWin() {
        return isKothMode() ? kothScoreToWin : ctpScoreToWin;
    }
    /** Returns the effective score-per-second for the current objective mode. */
    public int getObjectiveScorePerSec() {
        return isKothMode() ? kothScorePerSec : ctpScorePerSec;
    }
    /** Returns true if win condition is "first to score" (not timer) for current mode. */
    public boolean isObjectiveFirstToScore() {
        return isKothMode() ? kothFirstToScore : ctpFirstToScore;
    }
    /** Returns the effective round duration (seconds) for timer-based objective mode. */
    public int getObjectiveRoundDurationSec() {
        return isKothMode() ? kothRoundDurationSec : ctpRoundDurationSec;
    }

    public int     getBrBorderRadius()           { return brBorderRadius; }
    public void    setBrBorderRadius(int r)      { this.brBorderRadius = Math.max(10, r); }
    public int     getBrShrinkIntervalSec()      { return brShrinkIntervalSec; }
    public void    setBrShrinkIntervalSec(int s) { this.brShrinkIntervalSec = Math.max(1, s); }
    public int     getBrShrinkAmountBlocks()     { return Math.max(1, brShrinkAmountBlocks); }
    public void    setBrShrinkAmountBlocks(int b){ this.brShrinkAmountBlocks = Math.max(1, b); }
    public int     getBrInitialWaitSec()         { return Math.max(0, brInitialWaitSec); }
    public void    setBrInitialWaitSec(int s)    { this.brInitialWaitSec = Math.max(0, s); }
    public int     getBrFinalRadius()            { return Math.max(3, brFinalRadius); }
    public void    setBrFinalRadius(int r)       { this.brFinalRadius = Math.max(3, r); }
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
    public CompoundNBT save() {
        return LocationSerializer.save(this);
    }

    /** Deserializes a location from NBT. Delegated to {@link LocationSerializer}. */
    public static Location load(CompoundNBT tag) {
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
