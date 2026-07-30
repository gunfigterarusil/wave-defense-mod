package com.wavedefense.data;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persists per-location per-mode leaderboard top-10 lists.
 *
 * Mode keys:
 *   "PvE", "PvP_STANDARD", "PvP_DEATHMATCH", "PvP_BATTLE_ROYALE",
 *   "PvP_CAPTURE_THE_POINT", "PvP_KING_OF_THE_HILL"
 *
 * Storage: {@code <world>/data/wavedefense_leaderboards.dat}
 */
public class LeaderboardManager {

    public static final String MODE_PVE        = "PvE";
    /**
     * Endless runs rank separately and by a different metric: primaryScore is the wave
     * reached, not points. Mixing them into MODE_PVE would compare unlike numbers.
     */
    public static final String MODE_PVE_ENDLESS = "PvE_ENDLESS";
    public static final String MODE_STANDARD   = "PvP_STANDARD";
    public static final String MODE_DEATHMATCH = "PvP_DEATHMATCH";
    public static final String MODE_BATTLE_ROYALE = "PvP_BATTLE_ROYALE";
    public static final String MODE_CTP        = "PvP_CAPTURE_THE_POINT";
    public static final String MODE_KOTH       = "PvP_KING_OF_THE_HILL";

    private static final int DATA_VERSION = 1;
    private static final int MAX_ENTRIES = 10;

    /** location name → mode key → sorted list (index 0 = highest primaryScore) */
    private final Map<String, Map<String, List<LeaderboardRecord>>> data = new LinkedHashMap<>();
    private final File dataFile;

    public LeaderboardManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/wavedefense_leaderboards.dat").toFile();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Inserts a new record for the given location and mode, keeps top-{@value MAX_ENTRIES}
     * entries sorted by primaryScore descending.
     */
    public synchronized void addRecord(String locationName, String modeKey, LeaderboardRecord record) {
        if (locationName == null || modeKey == null || record == null) return;
        List<LeaderboardRecord> list = getOrCreate(locationName, modeKey);
        list.add(record);
        sortRecords(list);
        if (list.size() > MAX_ENTRIES) list.subList(MAX_ENTRIES, list.size()).clear();
    }

    /**
     * M-3 fix: stable sort with three-level tie-breaking.
     * 1) primaryScore descending (higher is better)
     * 2) durationSec ascending   (faster completion is better)
     * 3) timestamp descending    (more recent record wins ties)
     */
    private static void sortRecords(List<LeaderboardRecord> list) {
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.primaryScore, a.primaryScore);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.durationSec, b.durationSec);
            if (cmp != 0) return cmp;
            return Long.compare(b.timestamp, a.timestamp);
        });
    }

    /** Returns the top-10 list for the given location/mode (may be empty, never null). */
    public synchronized List<LeaderboardRecord> getTop10(String locationName, String modeKey) {
        if (locationName == null || modeKey == null) return List.of();
        Map<String, List<LeaderboardRecord>> byMode = data.get(locationName);
        if (byMode == null) return List.of();
        List<LeaderboardRecord> list = byMode.get(modeKey);
        return list != null ? Collections.unmodifiableList(new ArrayList<>(list)) : List.of();
    }

    /** Returns all mode keys that have at least one record for this location. */
    public synchronized List<String> getModesWithRecords(String locationName) {
        Map<String, List<LeaderboardRecord>> byMode = data.get(locationName);
        if (byMode == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<LeaderboardRecord>> e : byMode.entrySet()) {
            if (!e.getValue().isEmpty()) result.add(e.getKey());
        }
        return result;
    }

    // ── Persistence ────────────────────────────────────────────────────────

    /** v0.2.61: Wipes all leaderboard records (every location, every mode). Persists immediately. */
    public synchronized void clearAll() {
        data.clear();
        saveToFile();
    }

    public synchronized void saveToFile() {
        // Async + debounced — leaderboard updates are frequent during PvP rounds;
        // burst of 5 round-end writes collapses into 1 disk hit.
        NbtHelper.atomicWriteCompressedAsync(dataFile, serialize());
    }

    /** Synchronous save — used only on server stop. */
    public synchronized void saveToFileSync() {
        NbtHelper.atomicWriteCompressed(dataFile, serialize());
    }

    public synchronized void loadFromFile() {
        if (!dataFile.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile);
            deserialize(root);
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Could not load leaderboard data", e);
        }
    }

    // ── Serialization ──────────────────────────────────────────────────────

    private CompoundTag serialize() {
        CompoundTag root = new CompoundTag();
        root.putInt("__version__", DATA_VERSION);
        for (Map.Entry<String, Map<String, List<LeaderboardRecord>>> locEntry : data.entrySet()) {
            CompoundTag locTag = new CompoundTag();
            for (Map.Entry<String, List<LeaderboardRecord>> modeEntry : locEntry.getValue().entrySet()) {
                ListTag listTag = new ListTag();
                for (LeaderboardRecord rec : modeEntry.getValue()) listTag.add(rec.save());
                locTag.put(modeEntry.getKey(), listTag);
            }
            root.put(locEntry.getKey(), locTag);
        }
        return root;
    }

    /**
     * Migration seam for future leaderboard schema changes. v0→v1 is a no-op.
     */
    private CompoundTag migrate(CompoundTag root, int fromVersion) {
        if (fromVersion >= DATA_VERSION) return root;
        WaveDefenseMod.LOGGER.info("[WaveDefense] Migrating leaderboard data v{} → v{} (no-op).",
            fromVersion, DATA_VERSION);
        return root;
    }

    private void deserialize(CompoundTag root) {
        int fileVersion = root.contains("__version__") ? root.getInt("__version__") : 0;
        if (fileVersion > DATA_VERSION) {
            WaveDefenseMod.LOGGER.warn("[WaveDefense] Leaderboard data version {} is newer than supported {}; loading anyway",
                fileVersion, DATA_VERSION);
        } else if (fileVersion < DATA_VERSION) {
            root = migrate(root, fileVersion);
        }
        data.clear();
        for (String locName : root.getAllKeys()) {
            if ("__version__".equals(locName)) continue;
            CompoundTag locTag = root.getCompound(locName);
            for (String modeKey : locTag.getAllKeys()) {
                ListTag listTag = locTag.getList(modeKey, 10);
                List<LeaderboardRecord> list = getOrCreate(locName, modeKey);
                for (int i = 0; i < listTag.size(); i++) {
                    list.add(LeaderboardRecord.load(listTag.getCompound(i)));
                }
                // M-3 fix: re-sort with stable comparator after load
                sortRecords(list);
                if (list.size() > MAX_ENTRIES) list.subList(MAX_ENTRIES, list.size()).clear();
            }
        }
    }

    private List<LeaderboardRecord> getOrCreate(String loc, String mode) {
        return data.computeIfAbsent(loc, k -> new LinkedHashMap<>())
                   .computeIfAbsent(mode, k -> new ArrayList<>());
    }
}
