package com.wavedefense.compat;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * <p>All Tacz guns are instances of a single container item
 * (registry id {@code tacz:modern_kinetic_gun}) whose actual identity is
 * encoded in an NBT tag {@code GunId} (a {@link ResourceLocation} string).
 * Datapacks add new guns at runtime; they are not registry-registered.
 *
 * <p>This class uses <strong>pure reflection</strong> — no compile-time
 * dependency on Tacz exists. When Tacz is not loaded, every public
 * method returns an empty / default value with zero log noise.
 *
 * <p>When Tacz <em>is</em> loaded but its internal data map cannot be
 * reflected (incompatible version), {@link #getAllGuns()} falls back to
 * enumerating every item in the {@code tacz} namespace via
 * {@link ForgeRegistries#ITEMS}, all grouped under category
 * {@link #CAT_OTHER}.  The degraded path is logged once on first use.
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

    /** Tacz container item id (the only real registry item — all guns are NBT-distinguished). */
    private static final ResourceLocation GUN_ITEM_ID =
        new ResourceLocation("tacz", "modern_kinetic_gun");

    // ── Reflection state ──────────────────────────────────────────────────
    private static volatile boolean phase1Done = false;
    private static volatile boolean phase1Ok   = false;
    /** Cache of the resolved {@code GunData} class (or null). */
    private static Class<?> gunDataClass = null;
    /** Cached accessor for the gun "type" field on GunData. */
    private static Method gunTypeAccessor = null;

    /** Cached enumeration of all guns + categories, refreshed on first call after a reload. */
    private static volatile List<TaczGunEntry> cachedEntries = null;
    private static volatile boolean fallbackLogged = false;

    /** Immutable per-gun record returned to callers. */
    public static final class TaczGunEntry {
        public final String gunId;        // e.g. "tacz:ak47"
        public final String category;     // one of CAT_*
        public final String displayName;  // human-readable (defaults to gunId tail-cased)
        public TaczGunEntry(String gunId, String category, String displayName) {
            this.gunId       = gunId;
            this.category    = category != null ? category : CAT_OTHER;
            this.displayName = displayName != null ? displayName : gunId;
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

    /**
     * @return all Tacz guns currently loaded (datapack + built-in).
     *         Empty list when Tacz is absent.
     */
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
     * Builds a Minecraft {@link ItemStack} for the given Tacz gun id.
     * Returns {@link ItemStack#EMPTY} when Tacz is absent or the container
     * item is missing.
     *
     * @param gunId resource-location string, e.g. {@code "tacz:ak47"}
     */
    public static ItemStack buildGunStack(String gunId) {
        if (!isLoaded() || gunId == null || gunId.isBlank()) return ItemStack.EMPTY;
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
            cachedEntries = null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private static List<TaczGunEntry> discoverGuns() {
        // Try the reflection path first
        if (initPhase1()) {
            List<TaczGunEntry> reflected = scanGunDataMaps();
            if (!reflected.isEmpty()) return reflected;
        }
        // Fallback — enumerate any tacz-namespaced items as untyped guns
        if (!fallbackLogged) {
            WaveDefenseMod.LOGGER.warn(
                "[WD/Tacz] Internal gun data map not accessible — falling back to registry scan");
            fallbackLogged = true;
        }
        List<TaczGunEntry> fallback = new ArrayList<>();
        for (ResourceLocation rl : ForgeRegistries.ITEMS.getKeys()) {
            if (!"tacz".equals(rl.getNamespace())) continue;
            // Treat each registry item itself as a "gun id" — admin will see them under "other"
            fallback.add(new TaczGunEntry(rl.toString(), CAT_OTHER, prettifyId(rl.getPath())));
        }
        return fallback;
    }

    /** Resolves the GunData class + type accessor on first call. */
    private static synchronized boolean initPhase1() {
        if (phase1Done) return phase1Ok;
        phase1Done = true;
        try {
            gunDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            gunTypeAccessor = findTypeAccessor(gunDataClass);
            phase1Ok = true;
            WaveDefenseMod.LOGGER.info("[WD/Tacz] compat phase-1 OK (GunData accessor: {})",
                gunTypeAccessor == null ? "none" : gunTypeAccessor.getName());
        } catch (ClassNotFoundException e) {
            WaveDefenseMod.LOGGER.warn("[WD/Tacz] GunData class not found — falling back to registry scan");
        } catch (Throwable e) {
            WaveDefenseMod.LOGGER.warn("[WD/Tacz] phase-1 failed: {}", e.getMessage());
        }
        return phase1Ok;
    }

    /**
     * Locates the runtime gun-data map. Tries a few known classes and field shapes
     * (any {@code Map<ResourceLocation, GunData>}).
     */
    @SuppressWarnings("unchecked")
    private static List<TaczGunEntry> scanGunDataMaps() {
        if (gunDataClass == null) return List.of();
        // Candidate classes — in order of likelihood. Add more here as needed.
        String[] candidates = {
            "com.tacz.guns.resource.CommonAssetManager",
            "com.tacz.guns.resource.manager.CommonGunPackLoader",
            "com.tacz.guns.resource.manager.CommonAssetsManager",
            "com.tacz.guns.resource.CommonGunPackLoader"
        };
        List<TaczGunEntry> out = new ArrayList<>();
        for (String fqn : candidates) {
            try {
                Class<?> cls = Class.forName(fqn);
                Object instance = findInstance(cls);
                Map<?, ?> map = findGunDataMap(cls, instance);
                if (map == null) continue;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Object k = e.getKey();
                    Object v = e.getValue();
                    if (k == null || v == null) continue;
                    String gunId = k.toString();
                    String category = extractCategory(v);
                    String display = prettifyId(extractPath(gunId));
                    out.add(new TaczGunEntry(gunId, category, display));
                }
                if (!out.isEmpty()) return out;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                WaveDefenseMod.LOGGER.debug("[WD/Tacz] scan {} failed: {}", fqn, t.getMessage());
            }
        }
        return out;
    }

    /** Try to find a static {@code INSTANCE} field, otherwise null (use class for static access). */
    private static Object findInstance(Class<?> cls) {
        try {
            Field f = cls.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Walks all (static or instance) Map fields and returns the first whose values are GunData. */
    private static Map<?, ?> findGunDataMap(Class<?> cls, Object instance) {
        for (Field f : cls.getDeclaredFields()) {
            try {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object raw = f.get(java.lang.reflect.Modifier.isStatic(f.getModifiers()) ? null : instance);
                if (!(raw instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object sampleValue = m.values().iterator().next();
                if (sampleValue != null && gunDataClass.isAssignableFrom(sampleValue.getClass())) {
                    return m;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Tries {@code getType()}, {@code getCategory()}, or a field of either name. */
    private static Method findTypeAccessor(Class<?> cls) {
        for (String name : new String[]{"getType", "getCategory", "getGunType"}) {
            try {
                Method m = cls.getMethod(name);
                if (m.getParameterCount() == 0) return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    /** Extracts the category for a single GunData instance via the cached accessor. */
    private static String extractCategory(Object gunData) {
        if (gunTypeAccessor != null) {
            try {
                Object v = gunTypeAccessor.invoke(gunData);
                if (v != null) return normaliseCategory(v.toString());
            } catch (Throwable ignored) {}
        }
        // Field-based fallback
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

    /** Normalises a raw category string ("PISTOL", "Pistol", "tacz:pistol") to lowercase bare form. */
    private static String normaliseCategory(String raw) {
        if (raw == null) return CAT_OTHER;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) s = s.substring(colon + 1);
        // Map common synonyms
        return switch (s) {
            case "pistol", "handgun"          -> CAT_PISTOL;
            case "rifle", "assault_rifle", "ar" -> CAT_RIFLE;
            case "shotgun"                    -> CAT_SHOTGUN;
            case "smg", "sub_machine_gun"     -> CAT_SMG;
            case "sniper", "sniper_rifle", "marksman" -> CAT_SNIPER;
            case "rpg", "rocket", "launcher"  -> CAT_RPG;
            case "mg", "machine_gun", "lmg", "hmg" -> CAT_MG;
            default -> KNOWN_CATEGORIES.contains(s) ? s : CAT_OTHER;
        };
    }

    private static String extractPath(String resourceLocation) {
        int colon = resourceLocation.indexOf(':');
        return colon >= 0 ? resourceLocation.substring(colon + 1) : resourceLocation;
    }

    /** {@code ak_47} → {@code Ak 47}, very simple. */
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
