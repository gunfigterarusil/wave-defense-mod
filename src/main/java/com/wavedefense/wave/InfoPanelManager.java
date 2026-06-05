package com.wavedefense.wave;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.nbt.CompoundNBT;

/**
 * 1.16.5 STUB — InfoPanelManager is not available on this version.
 *
 * <p>The 1.20.1 implementation renders floating text labels above PvE mob
 * spawn points and player areas using {@code Display.TextDisplay} entities.
 * That entity type is a 1.19.4+ feature — there is no equivalent in 1.16.5.
 *
 * <p>This stub preserves the public API (constructor + {@link #tick()},
 * {@link #removeInfoPanelEntities(String)}, {@link #save()}, {@link #load(CompoundNBT)})
 * so callers in {@code WaveManager} keep compiling, but every method is a no-op.
 *
 * <p>If the feature is needed on 1.16.5, a future revision can re-implement it
 * using invisible ArmorStands with custom name (single line, no rich
 * formatting, ~150 LOC rewrite). See {@code PORT_STATUS.md} for context.
 */
public class InfoPanelManager {

    private final WaveContext ctx;
    private static boolean warnedOnce = false;

    public InfoPanelManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    /** No-op on 1.16.5. Logs a warning on the first tick. */
    public void tick() {
        if (!warnedOnce) {
            warnedOnce = true;
            WaveDefenceMod.LOGGER.info(
                "InfoPanelManager: feature not available on 1.16.5 "
              + "(requires Display.TextDisplay from 1.19.4+). Ticks are no-ops.");
        }
    }

    /** No-op on 1.16.5. */
    public void removeInfoPanelEntities(String locationName) {
        // nothing to do — no entities to remove
    }

    /** Returns an empty tag — no state to persist. */
    public CompoundNBT save() {
        return new CompoundNBT();
    }

    /** No-op on 1.16.5. */
    public void load(CompoundNBT tag) {
        // nothing to do — feature disabled
    }
}
