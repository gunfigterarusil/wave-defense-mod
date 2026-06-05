package com.wavedefense.gui;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helper for safely enumerating items in {@link ItemGroup}s on Forge 1.16.5.
 *
 * <p>1.16.5 does not have the "build contents on demand" event from 1.20.1.
 * Instead each {@code ItemGroup} fills items synchronously into a
 * {@link NonNullList} via {@link ItemGroup#fillItemList(NonNullList)}. That call
 * is cheap and idempotent, so we don't need an explicit cache or build phase.
 *
 * <p>All methods are safe to call on the client at any time.
 */
public final class CreativeTabHelper {

    private CreativeTabHelper() {}

    /** No-op on 1.16.5 — kept for API parity with the 1.20.1 helper. */
    public static void forceBuildAllTabs() { /* nothing to do */ }

    /** No-op on 1.16.5 — kept for API parity. */
    public static void invalidate() { /* nothing to do */ }

    /**
     * Reads a tab's items defensively. If the tab is null or throws, returns an
     * empty collection instead of propagating the exception.
     */
    public static Collection<ItemStack> safeGetItems(ItemGroup tab) {
        if (tab == null) return Collections.emptyList();
        try {
            NonNullList<ItemStack> items = NonNullList.create();
            tab.fillItemList(items);
            return items;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    /** Returns every loaded {@link ItemGroup} on 1.16.5 (creative search excluded by default). */
    public static List<ItemGroup> allTabs() {
        List<ItemGroup> out = new ArrayList<>();
        for (ItemGroup g : ItemGroup.TABS) {
            if (g == null) continue;
            out.add(g);
        }
        return out;
    }
}
