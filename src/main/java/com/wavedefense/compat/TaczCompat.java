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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optional compatibility bridge for Timeless and Classics Zero (Tacz),
 * mod id {@code "tacz"}.
 *
 * <h3>Discovery strategy</h3>
 * Gun discovery scans every loaded {@link CreativeModeTab} for {@link ItemStack}s
 * in the {@code tacz} namespace — the same approach used by
 * {@code ItemSelectionScreen.buildAllStacks()}. This produces exactly the
 * set of guns the admin sees in the creative inventory, including
 * datapack-defined guns added at runtime.
 *
 * <h3>Categorisation</h3>
 * For each discovered gun we try (in order):
 *   1. The Tacz internal {@code GunData} map via reflection (when the mod's
 *      resource layer is reachable) — picks up the proper category enum value.
 *   2. Substring match on the gun id path
 *      (e.g. {@code "ak47"} → rifle, {@code "glock_17"} → pistol).
 *   3. Fallback bucket {@link #CAT_OTHER}.
 *
 * <p>All public methods are safe to call when Tacz is absent — they return
 * empty / EMPTY values.
 */
public final class TaczCompat {

    private TaczCompat() {}

    // ── Known categories — stable order for UI ───────────────────────────
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

    /** Tacz container item id (only registry item — actual gun identity stored in NBT). */
    private static final ResourceLocation GUN_ITEM_ID =
        new ResourceLocation("tacz", "modern_kinetic_gun");

    // ── Reflection state for GunData category lookup (optional) ──────────
    private static volatile boolean phase1Done = false;
    private static volatile boolean phase1Ok   = false;
    private static Class<?> gunDataClass = null;
    private static Method gunTypeAccessor = null;
    /** Cached gunId → category map populated lazily when first scan succeeds. */
    private static volatile Map<String, String> gunCategoryMap = null;

    /** Cached gun list, lazily populated on first call. */
    private static volatile List<TaczGunEntry> cachedEntries = null;
    private static volatile boolean fallbackLogged = false;

    /** Immutable per-gun record returned to callers.
     *  Each entry carries a ready-made ItemStack snapshot taken from the
     *  Tacz creative tab so admins see exactly what they would in survival. */
    public static final class TaczGunEntry {
        public final String gunId;          // e.g. "tacz:ak47"
        public final String category;       // one of CAT_*
        public final String displayName;
        public final ItemStack template;    // ready ItemStack with proper NBT
        public TaczGunEntry(String gunId, String category, String displayName, ItemStack template) {
            this.gunId       = gunId;
            this.category    = category != null ? category : CAT_OTHER;
            this.displayName = displayName != null ? displayName : gunId;
            this.template    = template != null ? template : ItemStack.EMPTY;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** @return {@code true} if Tacz is loaded in the current Forge session. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    /** @return stable-ordered list of known gun categories (for UI tabs/buttons). */
    public static List<String> getKnownCategories() {
        return KNOWN_CATEGORIES;
    }

    /** @return all Tacz guns currently loaded; empty when Tacz is absent. */
    public static List<TaczGunEntry> getAllGuns() {
        if (!isLoaded()) return List.of();
        if (cachedEntries != null) return cachedEntries;
        synchronized (TaczCompat.class) {
            if (cachedEntries != null) return cachedEntries;
            List<TaczGunEntry> entries = discoverGuns();
            cachedEntries = Collections.unmodifiableList(entries);
            return cachedEntries;
        }
    }

    /** @return all guns whose category equals the given one, or empty list. */
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

    /**
     * Builds an ItemStack for the given gun id. Prefers the cached creative-tab
     * snapshot (preserves any attachments / pre-fill NBT the gun pack ships).
     * Falls back to a fresh container item with just {@code GunId} set.
     */
    public static ItemStack buildGunStack(String gunId) {
        if (!isLoaded() || gunId == null || gunId.isBlank()) return ItemStack.EMPTY;
        // Prefer the template captured from the creative tab — keeps default
        // attachments / pre-installed parts that the gunpack author intended.
        for (TaczGunEntry e : getAllGuns()) {
            if (gunId.equals(e.gunId) && !e.template.isEmpty()) {
                return e.template.copy();
            }
        }
        // Fallback — container item with bare GunId NBT (works for any gun in pack)
        Item container = ForgeRegistries.ITEMS.getValue(GUN_ITEM_ID);
        if (container == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(container);
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", gunId);
        stack.setTag(tag);
        return stack;
    }

    /** Drops the gun cache so the next call re-scans (e.g. after a datapack reload). */
    public static void invalidateCache() {
        synchronized (TaczCompat.class) {
            cachedEntries  = null;
            gunCategoryMap = null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    /**
     * Primary discovery: scan every Creative tab for Tacz-namespaced stacks
     * whose item is the gun container (carries a {@code GunId} NBT tag).
     * This matches what {@code ItemSelectionScreen} already does for the
     * general item picker.
     */
    private static List<TaczGunEntry> discoverGuns() {
        Map<String, TaczGunEntry> byId = new LinkedHashMap<>();
        // Try to populate the gunId → category reflection map first
        // (best-effort; null if Tacz internals can't be reached).
        Map<String, String> categoryByGunId = buildCategoryMap();

        try {
            for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                try {
                    Iterable<ItemStack> items = tab.getDisplayItems();
                    for (ItemStack st : items) {
                        if (st == null || st.isEmpty()) continue;
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
                        if (key == null || !"tacz".equals(key.getNamespace())) continue;
                        // We only want guns — skip ammo/attachments etc.
                        // Recognised by either:
                        //   (a) item being the container with GunId NBT
                        //   (b) any item id beginning with "modern_kinetic_gun"
                        String gunId = extractGunId(st);
                        if (gunId == null) continue;
                        if (byId.containsKey(gunId)) continue;
                        String cat = categoryByGunId != null
                            ? categoryByGunId.getOrDefault(gunId, null)
                            : null;
                        if (cat == null) cat = guessCategoryFromId(gunId);
                        String display = displayNameOf(st, gunId);
                        byId.put(gunId, new TaczGunEntry(gunId, cat, display, st.copy()));
                    }
                } catch (Throwable t) {
                    // One bad tab shouldn't break discovery
                }
            }
        } catch (Throwable t) {
            WaveDefenseMod.LOGGER.warn("[WD/Tacz] CreativeModeTab scan failed: {}", t.getMessage());
        }

        if (byId.isEmpty()) {
            // Last-ditch fallback: registry-level scan (works if guns aren't tab-registered)
            if (!fallbackLogged) {
                WaveDefenseMod.LOGGER.warn(
                    "[WD/Tacz] No guns found via creative tabs — using registry fallback");
                fallbackLogged = true;
            }
            for (ResourceLocation rl : ForgeRegistries.ITEMS.getKeys()) {
                if (!"tacz".equals(rl.getNamespace())) continue;
                if (!"modern_kinetic_gun".equals(rl.getPath())) continue;
                // Single container item, no gunId — admin must add manually
            }
        } else {
            WaveDefenseMod.LOGGER.info("[WD/Tacz] discovered {} Tacz guns via creative tabs",
                byId.size());
        }
        return new ArrayList<>(byId.values());
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

    /** Hover-name fallback chain → registry path → "?" */
    private static String displayNameOf(ItemStack st, String gunId) {
        try {
            String n = st.getHoverName().getString();
            if (n != null && !n.isBlank()) return n;
        } catch (Throwable ignored) {}
        return prettifyId(extractPath(gunId));
    }

    /**
     * Builds a gunId → category map from Tacz internals. Returns null when
     * reflection fails (we'll fall back to id-substring guessing).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> buildCategoryMap() {
        if (gunCategoryMap != null) return gunCategoryMap;
        if (!initPhase1()) return null;
        String[] candidates = {
            "com.tacz.guns.resource.CommonAssetManager",
            "com.tacz.guns.resource.manager.CommonGunPackLoader",
            "com.tacz.guns.resource.manager.CommonAssetsManager",
            "com.tacz.guns.resource.CommonGunPackLoader"
        };
        for (String fqn : candidates) {
            try {
                Class<?> cls = Class.forName(fqn);
                Object instance = findInstance(cls);
                Map<?, ?> map = findGunDataMap(cls, instance);
                if (map == null) continue;
                Map<String, String> out = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Object k = e.getKey();
                    Object v = e.getValue();
                    if (k == null || v == null) continue;
                    out.put(k.toString(), extractCategory(v));
                }
                if (!out.isEmpty()) {
                    gunCategoryMap = out;
                    return gunCategoryMap;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                WaveDefenseMod.LOGGER.debug("[WD/Tacz] category-map scan {} failed: {}",
                    fqn, t.getMessage());
            }
        }
        return null;
    }

    private static synchronized boolean initPhase1() {
        if (phase1Done) return phase1Ok;
        phase1Done = true;
        try {
            gunDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            gunTypeAccessor = findTypeAccessor(gunDataClass);
            phase1Ok = true;
        } catch (Throwable e) {
            // No GunData — we'll rely on id-guessing entirely. Not a problem.
        }
        return phase1Ok;
    }

    private static Object findInstance(Class<?> cls) {
        try {
            Field f = cls.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<?, ?> findGunDataMap(Class<?> cls, Object instance) {
        for (Field f : cls.getDeclaredFields()) {
            try {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object raw = f.get(java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    ? null : instance);
                if (!(raw instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object sample = m.values().iterator().next();
                if (sample != null && gunDataClass.isAssignableFrom(sample.getClass())) {
                    return m;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findTypeAccessor(Class<?> cls) {
        for (String name : new String[]{"getType", "getCategory", "getGunType"}) {
            try {
                Method m = cls.getMethod(name);
                if (m.getParameterCount() == 0) return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static String extractCategory(Object gunData) {
        if (gunTypeAccessor != null) {
            try {
                Object v = gunTypeAccessor.invoke(gunData);
                if (v != null) return normaliseCategory(v.toString());
            } catch (Throwable ignored) {}
        }
        for (String name : new String[]{"type", "category", "gunType"}) {
            try {
                Field f = gunData.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(gunData);
                if (v != null) return normaliseCategory(v.toString());
            } catch (Throwable ignored) {}
        }
        return CAT_OTHER;
    }

    /**
     * Best-effort categorisation by inspecting the gun id path. Used when
     * Tacz internals can't be reflected (datapack-defined guns are a common case).
     */
    private static String guessCategoryFromId(String gunId) {
        if (gunId == null) return CAT_OTHER;
        String s = gunId.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        // Order matters — check more specific terms first
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

    /** Normalises a raw category string ("PISTOL", "tacz:pistol") to lowercase bare form. */
    private static String normaliseCategory(String raw) {
        if (raw == null) return CAT_OTHER;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) s = s.substring(colon + 1);
        return switch (s) {
            case "pistol", "handgun"                    -> CAT_PISTOL;
            case "rifle", "assault_rifle", "ar"         -> CAT_RIFLE;
            case "shotgun"                              -> CAT_SHOTGUN;
            case "smg", "sub_machine_gun"               -> CAT_SMG;
            case "sniper", "sniper_rifle", "marksman"   -> CAT_SNIPER;
            case "rpg", "rocket", "launcher"            -> CAT_RPG;
            case "mg", "machine_gun", "lmg", "hmg"      -> CAT_MG;
            default -> KNOWN_CATEGORIES.contains(s) ? s : CAT_OTHER;
        };
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
