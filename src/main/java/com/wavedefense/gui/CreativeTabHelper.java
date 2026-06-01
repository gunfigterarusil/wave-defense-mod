package com.wavedefense.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.CreativeModeTabRegistry;

import java.util.Collection;
import java.util.Collections;

/**
 * Helper for safely enumerating items in {@link CreativeModeTab}s on Forge 1.20.1.
 *
 * <p>In 1.20.1 a tab's display contents are only populated once {@code buildContents()}
 * fires (via {@code BuildCreativeModeTabContentsEvent}). Before that event a fresh
 * {@code tab.getDisplayItems()} call returns an empty collection.
 *
 * <p>This helper proactively triggers the build for every loaded tab using the
 * current world's feature flags / registry access, then safely reads the items.
 * Repeated invocations are idempotent and very cheap (the build event itself is
 * the costly part, and Forge caches the result on the tab instance).
 *
 * <p>All methods are client-only — they require {@link Minecraft#level} to be
 * non-null. Calling them on the server side is a no-op.
 */
public final class CreativeTabHelper {

    private CreativeTabHelper() {}

    /** Last successful build pass — used to skip redundant rebuilds within one session. */
    private static volatile boolean built = false;

    /**
     * Forces every loaded {@link CreativeModeTab} to populate its display items.
     * Safe to call multiple times; after the first call further invocations are no-ops.
     */
    public static void forceBuildAllTabs() {
        if (built) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        try {
            FeatureFlagSet features = mc.level.enabledFeatures();
            RegistryAccess registries = mc.level.registryAccess();
            boolean opMaster = mc.player != null && mc.player.canUseGameMasterBlocks();
            CreativeModeTab.ItemDisplayParameters params =
                new CreativeModeTab.ItemDisplayParameters(features, opMaster, registries);
            for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                try {
                    tab.buildContents(params);
                } catch (Throwable ignored) {
                    // One bad tab shouldn't break the entire build pass
                }
            }
            built = true;
        } catch (Throwable ignored) {
            // Forge internals changed — give up silently; callers will fall back
        }
    }

    /** Drops the "already built" flag so the next {@link #forceBuildAllTabs()} call re-runs. */
    public static void invalidate() {
        built = false;
    }

    /**
     * Reads a tab's display items defensively. If the tab is uninitialised or throws,
     * returns an empty collection instead of propagating the exception.
     */
    public static Collection<ItemStack> safeGetItems(CreativeModeTab tab) {
        if (tab == null) return Collections.emptyList();
        try {
            Collection<ItemStack> items = tab.getDisplayItems();
            return items != null ? items : Collections.emptyList();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }
}
