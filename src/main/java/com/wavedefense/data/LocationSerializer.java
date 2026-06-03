package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/**
 * Stateless serializer for {@link Location}.
 *
 * Location.save() and Location.load() delegate here so Location.java
 * stays focused on its domain API and getters/setters.
 *
 * Fields are accessed directly (package-private) to mirror the original
 * load() implementation and avoid unnecessary setter side-effects during
 * deserialization.
 */
public final class LocationSerializer {
    private LocationSerializer() {}

    // ─── Save ─────────────────────────────────────────────────────────────

    public static CompoundTag save(Location loc) {
        CompoundTag tag = new CompoundTag();

        // ── Core ─────────────────────────────────────────────────────────
        tag.putString("name", loc.name);
        tag.putString("mode", loc.mode.name());
        tag.putInt("totalWaves",              loc.totalWaves);
        tag.putInt("timeBetweenWaves",        loc.timeBetweenWaves);
        tag.putInt("completionPointsReward",  loc.completionPointsReward);
        tag.putBoolean("keepInventory",       loc.keepInventory);
        tag.putBoolean("autoActivate",        loc.autoActivate);
        tag.putInt("autoActivateRadius",      loc.autoActivateRadius);
        tag.putInt("startingPoints",          loc.startingPoints);

        if (loc.playerSpawn   != null) tag.putLong("playerSpawn",   loc.playerSpawn.asLong());
        if (loc.playerSpawnRadius > 0) tag.putInt("playerSpawnRadius", loc.playerSpawnRadius);
        if (loc.mobSpawnRadius != 5)   tag.putInt("mobSpawnRadius",    loc.mobSpawnRadius);

        // ── Lists ─────────────────────────────────────────────────────────
        NbtHelper.saveList(tag, "mobSpawns",        loc.mobSpawns,        MobSpawnPoint::save);
        NbtHelper.saveList(tag, "pvpSpawnPoints",   loc.pvpSpawnPoints,   PvpSpawnPoint::save);
        NbtHelper.saveList(tag, "waves",            loc.waves,            WaveConfig::save);
        NbtHelper.saveList(tag, "lootSpawns",       loc.lootSpawns,       LootSpawn::save);
        NbtHelper.saveList(tag, "shopItems",        loc.shopItems,        ShopItem::save);
        NbtHelper.saveList(tag, "completionRewards",loc.completionRewards,ShopItem::save);

        // startingItems uses vanilla ItemStack serialization
        ListTag startingItemsList = new ListTag();
        for (ItemStack item : loc.startingItems) startingItemsList.add(item.save(new CompoundTag()));
        tag.put("startingItems", startingItemsList);

        // ── Shop mode ─────────────────────────────────────────────────────
        tag.putString("shopMode", loc.getShopMode().name());
        NbtHelper.saveList(tag, "shopPoints", loc.shopPoints, ShopPoint::save);

        // ── BBox area + minimap ──────────────────────────────────────────
        if (loc.bboxMin != null) tag.putLong("bboxMin", loc.bboxMin.asLong());
        if (loc.bboxMax != null) tag.putLong("bboxMax", loc.bboxMax.asLong());
        tag.putBoolean("minimapEnabled", loc.minimapEnabled);
        tag.putBoolean("bboxOutlineEnabled", loc.bboxOutlineEnabled);

        // ── Boundary ─────────────────────────────────────────────────────
        tag.putBoolean("locationBoundaryEnabled", loc.locationBoundaryEnabled);
        tag.putInt("locationBoundaryRadius",      loc.locationBoundaryRadius);
        tag.putString("boundaryConsequence",      loc.getBoundaryConsequence().name());
        tag.putFloat("boundaryDamagePerSec",      loc.boundaryDamagePerSec);
        tag.putBoolean("boundaryParticlesEnabled",loc.boundaryParticlesEnabled);
        tag.putString("boundaryParticleType",     loc.getBoundaryParticleType());
        tag.putInt("boundaryParticleCount",       loc.boundaryParticleCount);
        tag.putInt("boundaryParticleHeight",      loc.boundaryParticleHeight);
        tag.putInt("locationLeaveTimerSec",       loc.locationLeaveTimerSec);

        // ── Location trigger ──────────────────────────────────────────────
        tag.putBoolean("locationTriggerEnabled", loc.locationTriggerEnabled);
        tag.putString("locationTriggerType",     loc.locationTriggerType.name());

        // ── Portal ────────────────────────────────────────────────────────
        tag.putBoolean("portalEnabled",            loc.portalEnabled);
        tag.putInt("portalPenaltyWave",            loc.portalPenaltyWave);
        tag.putInt("portalPenaltyTimerSec",        loc.portalPenaltyTimerSec);
        tag.putBoolean("portalDisappearsOnComplete",loc.portalDisappearsOnComplete);
        tag.putInt("portalRespawnTimerSec",        loc.portalRespawnTimerSec);
        tag.putInt("portalOpenAfterStartSec",      loc.portalOpenAfterStartSec);

        // ── Misc ──────────────────────────────────────────────────────────
        tag.putBoolean("keepLootOnExit",     loc.keepLootOnExit);
        tag.putInt("reEntryCooldownSec",     loc.reEntryCooldownSec);
        tag.putLong("totalMobsKilledAllTime",loc.totalMobsKilledAllTime);
        tag.putInt("totalSessionsCompleted", loc.totalSessionsCompleted);

        if (loc.autoActivateEntryPos != null) tag.putLong("autoActivateEntryPos", loc.autoActivateEntryPos.asLong());

        // ── Victory ───────────────────────────────────────────────────────
        tag.putBoolean("victoryScreenEnabled",loc.victoryScreenEnabled);
        tag.putInt("victoryLingerTimeSec",   loc.victoryLingerTimeSec);

        // ── Zone activation extended ──────────────────────────────────────
        if (loc.zoneCenter != null) tag.putLong("zoneCenter", loc.zoneCenter.asLong());
        tag.putBoolean("zoneUsesCustomCenter",  loc.zoneUsesCustomCenter);
        tag.putInt("zoneActivationTimeSec",     loc.zoneActivationTimeSec);
        tag.putInt("zoneOpenAfterStartSec",     loc.zoneOpenAfterStartSec);

        // ── Exit points ───────────────────────────────────────────────────
        if (loc.victoryExitPos   != null) tag.putLong("victoryExitPos",   loc.victoryExitPos.asLong());
        if (loc.surrenderExitPos != null) tag.putLong("surrenderExitPos", loc.surrenderExitPos.asLong());

        // ── Visibility / misc v0.2.18+ ────────────────────────────────────
        tag.putBoolean("hiddenFromPlayers", loc.hiddenFromPlayers);
        tag.putInt("firstWaveDelaySec",     loc.firstWaveDelaySec);

        if (loc.zoneParticleType  != null) tag.putString("zoneParticleType", loc.zoneParticleType);
        if (loc.zoneParticleCount > 0)     tag.putInt("zoneParticleCount",   loc.zoneParticleCount);
        tag.putFloat("zoneParticleSpeed", loc.zoneParticleSpeed);
        if (loc.zoneParticleInterval != 1) tag.putInt("zoneParticleInterval", loc.zoneParticleInterval);

        if (loc.infoPanel != null) tag.put("infoPanel", loc.infoPanel.save());

        // ── PvP core ─────────────────────────────────────────────────────
        tag.putInt("pvpMinPlayers",   loc.pvpMinPlayers);
        tag.putBoolean("pvpFriendlyFire", loc.pvpFriendlyFire);
        tag.putInt("pvpKillPoints",   loc.pvpKillPoints);
        tag.putInt("pvpDeathPenalty", loc.pvpDeathPenalty);
        tag.putInt("pvpTotalRounds",  loc.pvpTotalRounds);
        tag.putInt("pvpBuyTime",      loc.pvpBuyTime);

        // ── PvP extended ─────────────────────────────────────────────────
        tag.putInt("pvpRoundStartDelay",  loc.pvpRoundStartDelay);
        tag.putInt("pvpReadyCheckTimeoutSec", loc.pvpReadyCheckTimeoutSec);
        tag.putInt("pvpRoundStartPoints", loc.pvpRoundStartPoints);
        tag.putInt("pvpWinPoints",        loc.pvpWinPoints);
        tag.putInt("pvpLosePoints",       loc.pvpLosePoints);
        tag.putInt("pvpRoundTimeLimitSec", loc.pvpRoundTimeLimitSec);
        tag.putInt("dmKillsToWin",        loc.dmKillsToWin);
        if (loc.dmSpawnMode != null) tag.putString("dmSpawnMode", loc.dmSpawnMode.name());
        tag.putBoolean("pvpWaitEffect",   loc.pvpWaitEffect);
        tag.putBoolean("pvpTeamAutoBalance", loc.pvpTeamAutoBalance);
        tag.putBoolean("enforceGameMode", loc.enforceGameMode);
        tag.putString("pvpMode",          loc.getPvpMode().name());

        // ── Capture the Point / King of the Hill ─────────────────────────
        NbtHelper.saveList(tag, "capturePoints", loc.capturePoints, CapturePoint::save);
        tag.putInt("ctpScoreToWin",       loc.ctpScoreToWin);
        tag.putInt("ctpScorePerSec",      loc.ctpScorePerSec);
        tag.putBoolean("ctpFirstToScore", loc.ctpFirstToScore);
        tag.putInt("ctpRoundDurationSec", loc.ctpRoundDurationSec);
        tag.putInt("kothScoreToWin",      loc.kothScoreToWin);
        tag.putInt("kothScorePerSec",     loc.kothScorePerSec);
        tag.putBoolean("kothFirstToScore",loc.kothFirstToScore);
        tag.putInt("kothRoundDurationSec",loc.kothRoundDurationSec);
        tag.putBoolean("kothHoldMode",    loc.kothHoldMode);
        tag.putInt("kothHoldDurationSec", loc.kothHoldDurationSec);
        tag.putBoolean("kothResetOnLoss", loc.kothResetOnLoss);
        tag.putBoolean("ctpSpeedMultiplier", loc.ctpSpeedMultiplier);
        tag.putBoolean("ctpCaptureAllWin",   loc.ctpCaptureAllWin);

        // ── Battle Royale ─────────────────────────────────────────────────
        tag.putInt("brBorderRadius",          loc.brBorderRadius);
        tag.putInt("brShrinkIntervalSec",     loc.brShrinkIntervalSec);
        tag.putInt("brShrinkAmountBlocks",    loc.brShrinkAmountBlocks);
        tag.putInt("brInitialWaitSec",        loc.brInitialWaitSec);
        tag.putInt("brFinalRadius",           loc.brFinalRadius);
        if (loc.brBorderParticle != null)     tag.putString("brBorderParticle", loc.brBorderParticle);
        tag.putInt("brBorderParticleCount",   loc.brBorderParticleCount);
        tag.putBoolean("brBorderDamage",      loc.brBorderDamage);
        tag.putFloat("brBorderDamageAmt",     loc.brBorderDamageAmt);

        // ── Lock state ────────────────────────────────────────────────────
        tag.putBoolean("locked", loc.locked);

        // ── Mine and Slash compat ─────────────────────────────────────────
        if (loc.masLevel          > 0) tag.putInt("masLevel",           loc.masLevel);
        if (loc.masXpBonus        > 0) tag.putInt("masXpBonus",         loc.masXpBonus);
        if (loc.masFireResist     > 0) tag.putInt("masFireResist",      loc.masFireResist);
        if (loc.masWaterResist    > 0) tag.putInt("masWaterResist",     loc.masWaterResist);
        if (loc.masLightningResist> 0) tag.putInt("masLightningResist", loc.masLightningResist);
        if (loc.masChaosResist    > 0) tag.putInt("masChaosResist",     loc.masChaosResist);
        if (loc.masPhysicalResist > 0) tag.putInt("masPhysicalResist",  loc.masPhysicalResist);

        // ── playerPoints ─────────────────────────────────────────────────
        CompoundTag pointsTag = new CompoundTag();
        for (java.util.Map.Entry<java.util.UUID, Integer> e : loc.playerPoints.entrySet())
            pointsTag.putInt(e.getKey().toString(), e.getValue());
        tag.put("playerPoints", pointsTag);

        // ── playerTeamMap ────────────────────────────────────────────────
        CompoundTag teamTag = new CompoundTag();
        for (java.util.Map.Entry<java.util.UUID, String> e : loc.playerTeamMap.entrySet())
            teamTag.putString(e.getKey().toString(), e.getValue());
        tag.put("playerTeamMap", teamTag);

        return tag;
    }

    // ─── Load ─────────────────────────────────────────────────────────────

    public static Location load(CompoundTag tag) {
        Location loc = new Location(tag.getString("name"));

        // ── Core ─────────────────────────────────────────────────────────
        loc.mode                 = LocationMode.fromString(tag.getString("mode"));
        loc.totalWaves           = tag.getInt("totalWaves");
        loc.timeBetweenWaves     = tag.getInt("timeBetweenWaves");
        loc.completionPointsReward = NbtHelper.getInt(tag,  "completionPointsReward", 0);
        loc.keepInventory        = NbtHelper.getBool(tag,   "keepInventory",          true);
        loc.autoActivate         = NbtHelper.getBool(tag,   "autoActivate",           false);
        loc.autoActivateRadius   = NbtHelper.getInt(tag,    "autoActivateRadius",     5);
        loc.startingPoints       = NbtHelper.getInt(tag,    "startingPoints",         0);
        loc.playerSpawnRadius    = NbtHelper.getInt(tag,    "playerSpawnRadius",      0);
        loc.mobSpawnRadius       = NbtHelper.getInt(tag,    "mobSpawnRadius",         5);

        if (tag.contains("playerSpawn")) loc.playerSpawn = net.minecraft.core.BlockPos.of(tag.getLong("playerSpawn"));

        // ── Lists ─────────────────────────────────────────────────────────
        // MobSpawnPoint uses backward-compat loading (preserve original logic)
        ListTag mobSpawnsList = tag.getList("mobSpawns", 10);
        for (int i = 0; i < mobSpawnsList.size(); i++) {
            CompoundTag spTag = mobSpawnsList.getCompound(i);
            if (spTag.contains("radius") || (spTag.contains("pos") && !spTag.contains("x"))) {
                if (spTag.contains("pos") && spTag.get("pos").getId() == 4) {
                    loc.mobSpawns.add(MobSpawnPoint.load(spTag));
                } else {
                    loc.mobSpawns.add(MobSpawnPoint.fromBlockPos(net.minecraft.core.BlockPos.of(spTag.getLong("pos"))));
                }
            } else {
                loc.mobSpawns.add(MobSpawnPoint.fromBlockPos(net.minecraft.core.BlockPos.of(spTag.getLong("pos"))));
            }
        }

        loc.pvpSpawnPoints  = NbtHelper.loadList(tag, "pvpSpawnPoints",   PvpSpawnPoint::load);
        loc.waves           = NbtHelper.loadList(tag, "waves",            WaveConfig::load);
        loc.lootSpawns      = NbtHelper.loadList(tag, "lootSpawns",       LootSpawn::load);
        loc.shopItems       = NbtHelper.loadList(tag, "shopItems",        ShopItem::load);
        loc.completionRewards = NbtHelper.loadList(tag, "completionRewards", ShopItem::load);

        // startingItems uses vanilla ItemStack deserialization
        ListTag sil = tag.getList("startingItems", 10);
        for (int i = 0; i < sil.size(); i++) loc.startingItems.add(ItemStack.of(sil.getCompound(i)));

        // ── Shop mode ─────────────────────────────────────────────────────
        loc.shopMode   = NbtHelper.loadEnum(tag, "shopMode",   Location.ShopMode.class, Location.ShopMode.GLOBAL);
        loc.shopPoints = NbtHelper.loadList(tag, "shopPoints", ShopPoint::load);

        // ── BBox area + minimap ──────────────────────────────────────────
        loc.bboxMin = tag.contains("bboxMin")
            ? net.minecraft.core.BlockPos.of(tag.getLong("bboxMin")) : null;
        loc.bboxMax = tag.contains("bboxMax")
            ? net.minecraft.core.BlockPos.of(tag.getLong("bboxMax")) : null;
        loc.minimapEnabled = NbtHelper.getBool(tag, "minimapEnabled", false);
        loc.bboxOutlineEnabled = NbtHelper.getBool(tag, "bboxOutlineEnabled", false);

        // ── Boundary ─────────────────────────────────────────────────────
        loc.locationBoundaryEnabled  = NbtHelper.getBool(tag,   "locationBoundaryEnabled",   false);
        loc.locationBoundaryRadius   = NbtHelper.getInt(tag,    "locationBoundaryRadius",    50);
        loc.locationLeaveTimerSec    = NbtHelper.getInt(tag,    "locationLeaveTimerSec",     30);
        loc.boundaryConsequence      = NbtHelper.loadEnum(tag,  "boundaryConsequence",       Location.BoundaryConsequence.class, Location.BoundaryConsequence.TIMER_SURRENDER);
        loc.boundaryDamagePerSec     = NbtHelper.getFloat(tag,  "boundaryDamagePerSec",      2.0f);
        loc.boundaryParticlesEnabled = NbtHelper.getBool(tag,   "boundaryParticlesEnabled",  false);
        loc.boundaryParticleType     = NbtHelper.getString(tag, "boundaryParticleType",      "minecraft:smoke");
        loc.boundaryParticleCount    = NbtHelper.getInt(tag,    "boundaryParticleCount",     4);
        loc.boundaryParticleHeight   = NbtHelper.getInt(tag,    "boundaryParticleHeight",    3);

        // ── Location trigger ──────────────────────────────────────────────
        loc.locationTriggerEnabled = NbtHelper.getBool(tag, "locationTriggerEnabled", false);
        loc.locationTriggerType    = NbtHelper.loadEnum(tag, "locationTriggerType",
                WaveTrigger.class, WaveTrigger.PLAYER_ENTER_ZONE);

        // ── Portal ────────────────────────────────────────────────────────
        loc.portalEnabled             = NbtHelper.getBool(tag, "portalEnabled",             false);
        loc.portalPenaltyWave         = NbtHelper.getInt(tag,  "portalPenaltyWave",         -1);
        loc.portalPenaltyTimerSec     = NbtHelper.getInt(tag,  "portalPenaltyTimerSec",     60);
        loc.portalDisappearsOnComplete= NbtHelper.getBool(tag, "portalDisappearsOnComplete",true);
        loc.portalRespawnTimerSec     = NbtHelper.getInt(tag,  "portalRespawnTimerSec",     300);
        loc.portalOpenAfterStartSec   = NbtHelper.getInt(tag,  "portalOpenAfterStartSec",   -1);

        // ── Misc ──────────────────────────────────────────────────────────
        loc.keepLootOnExit       = NbtHelper.getBool(tag, "keepLootOnExit",       false);
        loc.reEntryCooldownSec   = NbtHelper.getInt(tag,  "reEntryCooldownSec",   0);
        loc.totalMobsKilledAllTime = NbtHelper.getLong(tag,"totalMobsKilledAllTime",0L);
        loc.totalSessionsCompleted = NbtHelper.getInt(tag, "totalSessionsCompleted",0);

        if (tag.contains("autoActivateEntryPos"))
            loc.autoActivateEntryPos = net.minecraft.core.BlockPos.of(tag.getLong("autoActivateEntryPos"));

        loc.infoPanel = tag.contains("infoPanel")
                ? InfoPanelSettings.load(tag.getCompound("infoPanel"))
                : new InfoPanelSettings();

        // ── Victory ───────────────────────────────────────────────────────
        loc.victoryScreenEnabled = NbtHelper.getBool(tag, "victoryScreenEnabled", true);
        loc.victoryLingerTimeSec = NbtHelper.getInt(tag,  "victoryLingerTimeSec", 30);

        // ── Zone activation extended ──────────────────────────────────────
        if (tag.contains("zoneCenter")) loc.zoneCenter = net.minecraft.core.BlockPos.of(tag.getLong("zoneCenter"));
        loc.zoneUsesCustomCenter = NbtHelper.getBool(tag, "zoneUsesCustomCenter", false);
        loc.zoneActivationTimeSec = NbtHelper.getInt(tag, "zoneActivationTimeSec", 0);
        loc.zoneOpenAfterStartSec = NbtHelper.getInt(tag, "zoneOpenAfterStartSec", 0);

        // ── Exit points ───────────────────────────────────────────────────
        if (tag.contains("victoryExitPos"))  loc.victoryExitPos  = net.minecraft.core.BlockPos.of(tag.getLong("victoryExitPos"));
        if (tag.contains("surrenderExitPos"))loc.surrenderExitPos= net.minecraft.core.BlockPos.of(tag.getLong("surrenderExitPos"));

        // ── Visibility / misc v0.2.18+ ────────────────────────────────────
        loc.hiddenFromPlayers    = NbtHelper.getBool(tag,   "hiddenFromPlayers",    false);
        loc.firstWaveDelaySec    = NbtHelper.getInt(tag,    "firstWaveDelaySec",    0);
        loc.zoneParticleType     = tag.contains("zoneParticleType")  ? tag.getString("zoneParticleType") : null;
        loc.zoneParticleCount    = NbtHelper.getInt(tag,    "zoneParticleCount",    0);
        loc.zoneParticleSpeed    = NbtHelper.getFloat(tag,  "zoneParticleSpeed",    0.02f);
        loc.zoneParticleInterval = NbtHelper.getInt(tag,    "zoneParticleInterval", 1);

        // ── PvP core ─────────────────────────────────────────────────────
        loc.pvpMinPlayers    = NbtHelper.getInt(tag,  "pvpMinPlayers",    2);
        loc.pvpFriendlyFire  = NbtHelper.getBool(tag, "pvpFriendlyFire",  false);
        loc.pvpKillPoints    = NbtHelper.getInt(tag,  "pvpKillPoints",    100);
        loc.pvpDeathPenalty  = NbtHelper.getInt(tag,  "pvpDeathPenalty",  50);
        loc.pvpTotalRounds   = NbtHelper.getInt(tag,  "pvpTotalRounds",   10);
        loc.pvpBuyTime       = NbtHelper.getInt(tag,  "pvpBuyTime",       20);

        // ── PvP extended ─────────────────────────────────────────────────
        loc.pvpRoundStartDelay  = NbtHelper.getInt(tag,  "pvpRoundStartDelay",  5);
        loc.pvpReadyCheckTimeoutSec = NbtHelper.getInt(tag, "pvpReadyCheckTimeoutSec", 60);
        loc.pvpRoundStartPoints = NbtHelper.getInt(tag,  "pvpRoundStartPoints", 0);
        loc.pvpWinPoints        = NbtHelper.getInt(tag,  "pvpWinPoints",        0);
        loc.pvpLosePoints       = NbtHelper.getInt(tag,  "pvpLosePoints",       0);
        loc.pvpRoundTimeLimitSec = NbtHelper.getInt(tag, "pvpRoundTimeLimitSec", 0);
        loc.dmKillsToWin        = NbtHelper.getInt(tag,  "dmKillsToWin",        10);
        loc.dmSpawnMode         = NbtHelper.loadEnum(tag, "dmSpawnMode",
                                    com.wavedefense.data.Location.DmSpawnMode.class,
                                    com.wavedefense.data.Location.DmSpawnMode.TEAM_SPAWN);
        loc.pvpWaitEffect       = NbtHelper.getBool(tag, "pvpWaitEffect",       true);
        loc.pvpTeamAutoBalance  = NbtHelper.getBool(tag, "pvpTeamAutoBalance",  true);
        loc.enforceGameMode     = NbtHelper.getBool(tag, "enforceGameMode",     true);
        loc.pvpMode             = NbtHelper.loadEnum(tag, "pvpMode", Location.PvpMode.class, Location.PvpMode.STANDARD);

        // ── Capture the Point / King of the Hill ─────────────────────────
        loc.capturePoints        = NbtHelper.loadList(tag, "capturePoints", CapturePoint::load);
        loc.ctpScoreToWin        = NbtHelper.getInt(tag,    "ctpScoreToWin",       200);
        loc.ctpScorePerSec       = NbtHelper.getInt(tag,    "ctpScorePerSec",      1);
        loc.ctpFirstToScore      = NbtHelper.getBool(tag,   "ctpFirstToScore",     true);
        loc.ctpRoundDurationSec  = NbtHelper.getInt(tag,    "ctpRoundDurationSec", 300);
        loc.kothScoreToWin       = NbtHelper.getInt(tag,    "kothScoreToWin",      300);
        loc.kothScorePerSec      = NbtHelper.getInt(tag,    "kothScorePerSec",     1);
        loc.kothFirstToScore     = NbtHelper.getBool(tag,   "kothFirstToScore",    true);
        loc.kothRoundDurationSec = NbtHelper.getInt(tag,    "kothRoundDurationSec",300);
        loc.kothHoldMode         = NbtHelper.getBool(tag,   "kothHoldMode",        false);
        loc.kothHoldDurationSec  = NbtHelper.getInt(tag,    "kothHoldDurationSec", 180);
        loc.kothResetOnLoss      = NbtHelper.getBool(tag,   "kothResetOnLoss",     false);
        loc.ctpSpeedMultiplier   = NbtHelper.getBool(tag,   "ctpSpeedMultiplier",  false);
        loc.ctpCaptureAllWin     = NbtHelper.getBool(tag,   "ctpCaptureAllWin",    false);

        // ── Battle Royale ─────────────────────────────────────────────────
        loc.brBorderRadius        = NbtHelper.getInt(tag,    "brBorderRadius",        100);
        loc.brShrinkIntervalSec   = NbtHelper.getInt(tag,    "brShrinkIntervalSec",   5);
        loc.brShrinkAmountBlocks  = NbtHelper.getInt(tag,    "brShrinkAmountBlocks",  1);
        loc.brInitialWaitSec      = NbtHelper.getInt(tag,    "brInitialWaitSec",      30);
        loc.brFinalRadius         = NbtHelper.getInt(tag,    "brFinalRadius",         5);
        loc.brBorderParticle      = NbtHelper.getString(tag, "brBorderParticle",      "minecraft:flame");
        loc.brBorderParticleCount = NbtHelper.getInt(tag,    "brBorderParticleCount", 8);
        loc.brBorderDamage        = NbtHelper.getBool(tag,   "brBorderDamage",        true);
        loc.brBorderDamageAmt     = NbtHelper.getFloat(tag,  "brBorderDamageAmt",     1.0f);

        // ── Lock state ────────────────────────────────────────────────────
        if (tag.contains("locked")) loc.locked = tag.getBoolean("locked");

        // ── Mine and Slash compat ─────────────────────────────────────────
        loc.masLevel           = NbtHelper.getInt(tag, "masLevel",           0);
        loc.masXpBonus         = NbtHelper.getInt(tag, "masXpBonus",         0);
        loc.masFireResist      = NbtHelper.getInt(tag, "masFireResist",      0);
        loc.masWaterResist     = NbtHelper.getInt(tag, "masWaterResist",     0);
        loc.masLightningResist = NbtHelper.getInt(tag, "masLightningResist", 0);
        loc.masChaosResist     = NbtHelper.getInt(tag, "masChaosResist",     0);
        loc.masPhysicalResist  = NbtHelper.getInt(tag, "masPhysicalResist",  0);

        // ── playerPoints ─────────────────────────────────────────────────
        if (tag.contains("playerPoints")) {
            CompoundTag pointsTag = tag.getCompound("playerPoints");
            for (String key : pointsTag.getAllKeys()) {
                try { loc.playerPoints.put(java.util.UUID.fromString(key), pointsTag.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }

        // ── playerTeamMap ────────────────────────────────────────────────
        if (tag.contains("playerTeamMap")) {
            CompoundTag teamTag = tag.getCompound("playerTeamMap");
            for (String key : teamTag.getAllKeys()) {
                try { loc.playerTeamMap.put(java.util.UUID.fromString(key), teamTag.getString(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }

        return loc;
    }
}
