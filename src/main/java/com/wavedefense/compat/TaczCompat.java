package com.wavedefense.compat;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Optional compatibility for Timeless and Classics Zero (Tacz), modid {@code "tacz"}.
 *
 * <p>This bridge intentionally stays simple:
 *
 * <ol>
 *   <li><b>Source of truth</b> for stacks is the same one the regular item picker uses —
 *       {@link CreativeModeTabRegistry#getSortedCreativeModeTabs()}. Every Tacz-namespaced
 *       {@link ItemStack} with a {@code GunId} NBT tag becomes a gun entry; its NBT is
 *       preserved verbatim so any attachments / pre-fill the gunpack ships survive.</li>
 *   <li><b>Categorisation</b> uses our fixed 8-bucket scheme
 *       ({@link #CAT_PISTOL pistol} / {@link #CAT_RIFLE rifle} / shotgun / smg / sniper /
 *       rpg / mg / {@link #CAT_OTHER other}) determined by substring matching on the gun id.
 *       Unknown ids land in {@code other}; the {@link #CAT_ALL "all"} pseudo-tab shows
 *       everything regardless of bucket.</li>
 *   <li><b>Sorting</b> — guns within each category are sorted alphabetically by display name
 *       so the picker and bulk-add list are always in a predictable order.</li>
 * </ol>
 *
 * <p>Every public method is a no-op when Tacz is absent.
 */
public final class TaczCompat {

    private TaczCompat() {}

    // ── Mod-defined categories (stable order for UI) ─────────────────────
    public static final String CAT_PISTOL   = "pistol";
    public static final String CAT_RIFLE    = "rifle";
    public static final String CAT_SHOTGUN  = "shotgun";
    public static final String CAT_SMG      = "smg";
    public static final String CAT_SNIPER   = "sniper";
    public static final String CAT_RPG      = "rpg";
    public static final String CAT_MG       = "mg";
    public static final String CAT_OTHER    = "other";
    /** Pseudo-category meaning "no filter — show all guns". */
    public static final String CAT_ALL      = "all";

    private static final List<String> KNOWN_CATEGORIES = List.of(
        CAT_PISTOL, CAT_RIFLE, CAT_SHOTGUN, CAT_SMG,
        CAT_SNIPER, CAT_RPG, CAT_MG, CAT_OTHER
    );

    /** Tacz container item id. */
    private static final ResourceLocation GUN_ITEM_ID =
        new ResourceLocation("tacz", "modern_kinetic_gun");

    /** Cached guns; populated lazily on first call, cleared by invalidateCache(). */
    private static volatile List<TaczGunEntry> cachedEntries = null;

    /** Immutable per-gun record. {@link #template} carries the original creative-tab stack. */
    public static final class TaczGunEntry {
        public final String gunId;          // e.g. "tacz:ak47"
        public final String category;       // one of CAT_*
        public final String displayName;
        public final ItemStack template;
        public TaczGunEntry(String gunId, String category, String displayName, ItemStack template) {
            this.gunId       = gunId;
            this.category    = category != null ? category : CAT_OTHER;
            this.displayName = displayName != null ? displayName : gunId;
            this.template    = template != null ? template : ItemStack.EMPTY;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    public static List<String> getKnownCategories() {
        return KNOWN_CATEGORIES;
    }

    public static List<TaczGunEntry> getAllGuns() {
        if (!isLoaded()) return List.of();
        if (cachedEntries != null) return cachedEntries;
        synchronized (TaczCompat.class) {
            if (cachedEntries != null) return cachedEntries;
            List<TaczGunEntry> fresh = discoverGuns();
            // Don't cache an empty result — creative tabs may not be populated yet.
            // The next call will retry the scan after BuildCreativeModeTabContentsEvent
            // has had a chance to fire.
            if (fresh.isEmpty()) return fresh;
            cachedEntries = Collections.unmodifiableList(fresh);
            return cachedEntries;
        }
    }

    public static List<TaczGunEntry> getGunsByCategory(String category) {
        if (!isLoaded()) return List.of();
        if (category == null || category.isBlank() || CAT_ALL.equalsIgnoreCase(category)) {
            return getAllGuns();
        }
        String key = category.toLowerCase(Locale.ROOT);
        return getAllGuns().stream()
            .filter(e -> key.equals(e.category))
            .collect(Collectors.toList());
    }

    /** @return a fresh copy of the gun's creative-tab template, or EMPTY if unknown. */
    public static ItemStack buildGunStack(String gunId) {
        if (!isLoaded() || gunId == null || gunId.isBlank()) return ItemStack.EMPTY;
        for (TaczGunEntry e : getAllGuns()) {
            if (gunId.equals(e.gunId) && !e.template.isEmpty()) {
                return e.template.copy();
            }
        }
        // Fallback — bare container with just GunId NBT (works for any pack)
        Item container = ForgeRegistries.ITEMS.getValue(GUN_ITEM_ID);
        if (container == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(container);
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", gunId);
        stack.setTag(tag);
        return stack;
    }

    public static void invalidateCache() {
        synchronized (TaczCompat.class) {
            cachedEntries = null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private static List<TaczGunEntry> discoverGuns() {
        // Force-build creative tabs first — in Forge 1.20.1 tab.getDisplayItems()
        // returns an empty collection until BuildCreativeModeTabContentsEvent fires.
        try {
            com.wavedefense.gui.CreativeTabHelper.forceBuildAllTabs();
        } catch (Throwable ignored) {}

        // Dedupe by gunId — same gun can appear in multiple creative tabs
        Map<String, TaczGunEntry> byId = new LinkedHashMap<>();
        try {
            for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                for (ItemStack st : com.wavedefense.gui.CreativeTabHelper.safeGetItems(tab)) {
                    if (st == null || st.isEmpty()) continue;
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
                    if (key == null || !"tacz".equals(key.getNamespace())) continue;
                    String gunId = extractGunId(st);
                    if (gunId == null) continue;          // not a gun (ammo/attachment)
                    if (byId.containsKey(gunId)) continue;
                    String cat = categoriseById(gunId);
                    String name = displayNameOf(st, gunId);
                    byId.put(gunId, new TaczGunEntry(gunId, cat, name, st.copy()));
                }
            }
        } catch (Throwable t) {
            WaveDefenseMod.LOGGER.warn("[WD/Tacz] CreativeModeTab scan failed: {}", t.getMessage());
        }
        // Sort: category (in our fixed order) → displayName asc
        List<TaczGunEntry> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator
            .<TaczGunEntry, Integer>comparing(e -> {
                int idx = KNOWN_CATEGORIES.indexOf(e.category);
                return idx < 0 ? KNOWN_CATEGORIES.size() : idx;
            })
            .thenComparing(e -> e.displayName.toLowerCase(Locale.ROOT)));
        if (!sorted.isEmpty()) {
            WaveDefenseMod.LOGGER.info("[WD/Tacz] discovered {} guns via creative tabs", sorted.size());
        }
        return sorted;
    }

    /** Reads the {@code GunId} string from an ItemStack's NBT, or null if not present. */
    private static String extractGunId(ItemStack st) {
        if (!st.hasTag()) return null;
        CompoundTag tag = st.getTag();
        if (tag == null) return null;
        if (tag.contains("GunId", net.minecraft.nbt.Tag.TAG_STRING)) {
            String v = tag.getString("GunId");
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    /** Hover-name → registry-path-prettified → gunId fallback. */
    private static String displayNameOf(ItemStack st, String gunId) {
        try {
            String n = st.getHoverName().getString();
            if (n != null && !n.isBlank()) return n;
        } catch (Throwable ignored) {}
        return prettifyId(extractPath(gunId));
    }

    /**
     * Substring-based categorisation against our fixed bucket list.
     * Order matters — more specific terms are checked before general ones.
     * Unknown ids fall into {@link #CAT_OTHER}; admin can still reach them
     * via the "Tacz All" or "Other" tabs.
     */
    private static String categoriseById(String gunId) {
        if (gunId == null) return CAT_OTHER;
        String s = gunId.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);

        if (containsAny(s, "sniper", "awp", "barrett", "m24", "kar98", "mosin", "marksman", "dmr"))
            return CAT_SNIPER;
        if (containsAny(s, "shotgun", "m870", "spas", "saiga12", "ksg", "remington"))
            return CAT_SHOTGUN;
        if (containsAny(s, "smg", "mp5", "mp7", "mp9", "uzi", "vector", "p90", "ump", "thompson", "tommy"))
            return CAT_SMG;
        if (containsAny(s, "rpg", "launcher", "rocket", "bazooka", "panzerschreck", "rl_"))
            return CAT_RPG;
        if (containsAny(s, "_mg", "mg42", "minigun", "lmg", "hmg", "m249", "m60", "pkm", "saw"))
            return CAT_MG;
        if (containsAny(s, "pistol", "glock", "m1911", "deagle", "desert_eagle", "beretta",
                "usp", "p226", "p99", "p08", "luger", "revolver", "colt", "anaconda"))
            return CAT_PISTOL;
        if (containsAny(s, "rifle", "ak", "m4", "m16", "ar15", "ar_", "scar", "famas", "aug",
                "fal", "g3", "g36", "groza", "vss", "qbz", "stg"))
            return CAT_RIFLE;
        return CAT_OTHER;
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static String extractPath(String resourceLocation) {
        int colon = resourceLocation.indexOf(':');
        return colon >= 0 ? resourceLocation.substring(colon + 1) : resourceLocation;
    }

    private static String prettifyId(String s) {
        if (s == null || s.isEmpty()) return "?";
        String[] parts = s.replace('_', ' ').split(" ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(' ');
            if (parts[i].isEmpty()) continue;
            b.append(Character.toUpperCase(parts[i].charAt(0)))
             .append(parts[i].substring(1));
        }
        return b.toString();
    }
}
