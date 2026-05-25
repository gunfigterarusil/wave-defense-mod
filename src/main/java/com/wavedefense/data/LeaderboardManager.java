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
    public static final String MODE_STANDARD   = "PvP_STANDARD";
    public static final String MODE_DEATHMATCH = "PvP_DEATHMATCH";
    public static final String MODE_BATTLE_ROYALE = "PvP_BATTLE_ROYALE";
    public static final String MODE_CTP        = "PvP_CAPTURE_THE_POINT";
    public static final String MODE_KOTH       = "PvP_KING_OF_THE_HILL";

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
        list.sort((a, b) -> Integer.compare(b.primaryScore, a.primaryScore));
        if (list.size() > MAX_ENTRIES) list.subList(MAX_ENTRIES, list.size()).clear();
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

    public synchronized void saveToFile() {
        try {
            dataFile.getParentFile().mkdirs();
            CompoundTag root = serialize();
            File tmp = new File(dataFile.getAbsolutePath() + ".tmp");
            NbtIo.writeCompressed(root, tmp);

            // H-4 fix: check renameTo return values; on failure try java.nio.Files.move()
            File bak = new File(dataFile.getAbsolutePath() + ".bak");
            if (dataFile.exists()) {
                boolean moved = dataFile.renameTo(bak);
                if (!moved) {
                    try { java.nio.file.Files.move(dataFile.toPath(), bak.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ex) {
                        WaveDefenseMod.LOGGER.warn("[WaveDefense] Could not back up leaderboard file: {}", ex.getMessage());
                        // If we can't back up, don't risk overwriting with tmp
                        tmp.delete();
                        return;
                    }
                }
            }
            boolean committed = tmp.renameTo(dataFile);
            if (!committed) {
                try { java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    WaveDefenseMod.LOGGER.error("[WaveDefense] Could not commit leaderboard file — restoring backup: {}", ex.getMessage());
                    // Attempt to restore backup
                    if (bak.exists()) bak.renameTo(dataFile);
                }
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Could not save leaderboard data", e);
        }
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

    private void deserialize(CompoundTag root) {
        data.clear();
        for (String locName : root.getAllKeys()) {
            CompoundTag locTag = root.getCompound(locName);
            for (String modeKey : locTag.getAllKeys()) {
                ListTag listTag = locTag.getList(modeKey, 10);
                List<LeaderboardRecord> list = getOrCreate(locName, modeKey);
                for (int i = 0; i < listTag.size(); i++) {
                    list.add(LeaderboardRecord.load(listTag.getCompound(i)));
                }
                // L-5 fix: re-sort after load (guards against manual edits / version migration)
                list.sort((a, b) -> Integer.compare(b.primaryScore, a.primaryScore));
                if (list.size() > MAX_ENTRIES) list.subList(MAX_ENTRIES, list.size()).clear();
            }
        }
    }

    private List<LeaderboardRecord> getOrCreate(String loc, String mode) {
        return data.computeIfAbsent(loc, k -> new LinkedHashMap<>())
                   .computeIfAbsent(mode, k -> new ArrayList<>());
    }
}
