package com.wavedefense.compat;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional compatibility bridge for Mine and Slash (mod ID: {@code "mmorpg"}, v6.1.0+).
 *
 * <p>Uses reflection so <em>no compile-time dependency</em> on Mine and Slash classes is
 * required.  All public methods are no-ops when Mine and Slash is absent at runtime.
 *
 * <p>Per-location mob overrides (level, XP bonus, elemental resistances) are applied via
 * the MnS {@code EntityData} capability immediately before the mob is added to the world.
 *
 * <h3>API surface used</h3>
 * <ul>
 *   <li>{@code EntityData.get(LivingEntity)} — obtain capability</li>
 *   <li>{@code EntityData.setLevel(int)} — override level</li>
 *   <li>{@code EntityData.getCustomExactStats()} — custom stat container</li>
 *   <li>{@code <stats>.addExactStat(id, statId, value, ModType)} — add flat/% stat</li>
 *   <li>{@code EntityData.recalcStats_DONT_CALL()} — apply changes</li>
 * </ul>
 */
public final class MineAndSlashCompat {

    private MineAndSlashCompat() {}

    // ── Phase-1 state: EntityData class (resolved on first use) ──────────
    private static volatile boolean phase1Done = false;
    private static volatile boolean phase1Ok   = false;
    private static Method mGet;        // EntityData.get(LivingEntity)
    private static Method mSetLevel;   // EntityData.setLevel(int)
    private static Method mGetCustom;  // EntityData.getCustomExactStats()
    private static Method mRecalc;     // EntityData.recalcStats_DONT_CALL()

    // ── Phase-2 state: CustomExactStats methods (resolved on first mob) ──
    // Phase-2 requires a concrete object instance to navigate the class hierarchy.
    private static volatile boolean phase2Done = false;
    private static volatile boolean phase2Ok   = false;
    private static Method mAddExactStat; // addExactStat(String, String, int, ModType)
    private static Object MOD_FLAT;      // ModType.FLAT enum constant
    private static Object MOD_PERCENT;   // ModType.PERCENT enum constant

    // ─────────────────────────────────────────────────────────────────────

    /**
     * @return {@code true} if Mine and Slash is present in the current Forge session.
     */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("mmorpg");
    }

    /**
     * Applies Mine and Slash stat overrides from the location configuration to a
     * freshly created mob.
     *
     * <p>Must be called <em>after</em> {@code applyMobEquipment()} and
     * <em>before</em> {@code world.addFreshEntity()}.
     * Returns immediately (no-op) when Mine and Slash is absent or when no MnS
     * settings are configured for the location.
     *
     * @param mob      the mob about to be added to the world
     * @param location the Wave Defense location whose MnS settings to apply
     */
    public static void applyToMob(Mob mob, Location location) {
        if (!isLoaded()) return;
        if (location == null) return;
        if (!hasAnyConfig(location)) return;
        try {
            if (!initPhase1()) return;
            applyInternal(mob, location);
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.warn(
                "[WD/MnS] applyToMob failed for {} @ '{}': {}",
                mob.getType().getDescriptionId(), location.getName(), e.getMessage());
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private static boolean hasAnyConfig(Location loc) {
        return loc.getMasLevel()          > 0
            || loc.getMasXpBonus()        > 0
            || loc.getMasFireResist()     > 0
            || loc.getMasWaterResist()    > 0
            || loc.getMasLightningResist()> 0
            || loc.getMasChaosResist()    > 0
            || loc.getMasPhysicalResist() > 0;
    }

    /** Resolves the EntityData class and its key methods exactly once. */
    private static synchronized boolean initPhase1() {
        if (phase1Done) return phase1Ok;
        try {
            Class<?> cls = Class.forName(
                "com.robertx22.mine_and_slash.capability.entity.EntityData");
            mGet       = cls.getMethod("get", net.minecraft.world.entity.LivingEntity.class);
            mSetLevel  = cls.getMethod("setLevel", int.class);
            mGetCustom = cls.getMethod("getCustomExactStats");
            mRecalc    = cls.getMethod("recalcStats_DONT_CALL");
            phase1Ok   = true;
            WaveDefenseMod.LOGGER.info("[WD] Mine and Slash compat phase-1 OK");
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.warn("[WD] Mine and Slash compat phase-1 failed: {}", e.getMessage());
        }
        phase1Done = true;
        return phase1Ok;
    }

    /**
     * Resolves {@code addExactStat} and the {@code ModType} enum constants from the
     * first concrete {@code CustomExactStats} instance we encounter.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized boolean initPhase2(Object customStats) {
        if (phase2Done) return phase2Ok;
        try {
            for (Method m : customStats.getClass().getMethods()) {
                if ("addExactStat".equals(m.getName()) && m.getParameterCount() == 4) {
                    mAddExactStat = m;
                    Class<?> modTypeClass = m.getParameterTypes()[3];
                    MOD_FLAT    = Enum.valueOf((Class<Enum>) modTypeClass, "FLAT");
                    MOD_PERCENT = Enum.valueOf((Class<Enum>) modTypeClass, "PERCENT");
                    phase2Ok    = true;
                    WaveDefenseMod.LOGGER.info("[WD] Mine and Slash compat phase-2 OK");
                    break;
                }
            }
            if (!phase2Ok) {
                WaveDefenseMod.LOGGER.warn(
                    "[WD] Mine and Slash compat: addExactStat(4) not found on {}",
                    customStats.getClass().getName());
            }
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.warn("[WD] Mine and Slash compat phase-2 failed: {}", e.getMessage());
        }
        phase2Done = true;
        return phase2Ok;
    }

    private static void applyInternal(Mob mob, Location location) throws Exception {
        Object data = mGet.invoke(null, mob);
        if (data == null) return;

        // ── Level ────────────────────────────────────────────────────────
        int level = location.getMasLevel();
        if (level > 0) {
            mSetLevel.invoke(data, level);
        }

        // ── Custom stats (XP bonus + resistances) ────────────────────────
        Object customStats = mGetCustom.invoke(data);
        boolean dirty = false;

        if (customStats != null && initPhase2(customStats)) {
            // A short prefix derived from the mob UUID so each mob gets a unique stat ID.
            // 12 hex chars (6 bytes) is enough to avoid collisions in a typical wave.
            String uid = mob.getUUID().toString().replace("-", "").substring(0, 12);

            int xpBonus = location.getMasXpBonus();
            if (xpBonus > 0 && MOD_PERCENT != null) {
                mAddExactStat.invoke(customStats,
                    "wd_xp_" + uid, "bonus_exp", xpBonus, MOD_PERCENT);
                dirty = true;
            }
            dirty |= addResist(customStats, uid, "fire_resist",      location.getMasFireResist());
            dirty |= addResist(customStats, uid, "water_resist",     location.getMasWaterResist());
            dirty |= addResist(customStats, uid, "lightning_resist", location.getMasLightningResist());
            dirty |= addResist(customStats, uid, "chaos_resist",     location.getMasChaosResist());
            dirty |= addResist(customStats, uid, "physical_resist",  location.getMasPhysicalResist());
        }

        // Recalculate if anything changed
        if (level > 0 || dirty) {
            mRecalc.invoke(data);
        }
    }

    private static boolean addResist(Object customStats, String uid,
            String statId, int value) throws Exception {
        if (value <= 0 || MOD_FLAT == null) return false;
        mAddExactStat.invoke(customStats,
            "wd_" + statId + "_" + uid, statId, value, MOD_FLAT);
        return true;
    }
}
