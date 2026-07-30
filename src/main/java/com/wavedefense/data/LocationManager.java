package com.wavedefense.data;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocationManager {
    private static final int DATA_VERSION = 1;

    /**
     * Locations keyed by name for O(1) lookup. {@link LinkedHashMap} preserves
     * insertion (creation) order so {@code /wd list} and the editor list show
     * locations in the same stable order as the legacy {@code ArrayList}.
     */
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final File dataFile;

    public LocationManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/wavedefense_locations.dat").toFile();
        load();
    }

    public void createLocation(String name) {
        if (!locations.containsKey(name)) {
            locations.put(name, new Location(name));
            saveToFile();
        }
    }

    public void addLocation(Location loc) {
        locations.put(loc.getName(), loc); // replace if exists
        saveToFile();
    }

    public void removeLocation(String name) {
        if (locations.remove(name) != null) {
            saveToFile();
        }
    }

    public void updateLocation(Location updatedLocation) {
        // Only persist if the location actually exists (mirrors legacy behaviour
        // which silently no-op'd updates to unknown names).
        if (locations.containsKey(updatedLocation.getName())) {
            locations.put(updatedLocation.getName(), updatedLocation);
            saveToFile();
        }
    }

    @Nullable
    public Location getLocation(String name) {
        return locations.get(name); // O(1)
    }

    /**
     * Returns a defensive copy of all locations in creation order.
     * Callers must not assume mutations propagate back — use the CRUD methods.
     */
    public List<Location> getAllLocations() {
        return new ArrayList<>(locations.values());
    }

    /**
     * Read-only view of all locations (creation order), backed by the live map —
     * <b>no per-call allocation</b>. Use this for per-tick iteration in server
     * managers where {@link #getAllLocations()}'s defensive copy would churn the
     * young generation 20× per second.
     *
     * <p>The returned collection is unmodifiable (mutating throws). It must only
     * be read; never add/remove locations <em>while iterating</em> it (all map
     * mutations happen elsewhere on the server thread, so tick iteration is safe).
     */
    public java.util.Collection<Location> getAllLocationsView() {
        return java.util.Collections.unmodifiableCollection(locations.values());
    }

    public boolean locationExists(String name) {
        return locations.containsKey(name);
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        data.putInt("version", DATA_VERSION);
        ListTag locationsList = new ListTag();
        for (Location loc : locations.values()) {
            locationsList.add(loc.save());
        }
        data.put("locations", locationsList);
        return data;
    }

    public void saveToFile() {
        // Async + debounced atomic write — collapses burst-saves into one disk hit
        // and moves I/O off the server thread. Server stop calls flushPendingWrites()
        // (see WaveDefenseMod.onServerStopping) so no data is lost on shutdown.
        NbtHelper.atomicWriteCompressedAsync(dataFile, save());
    }

    /** Synchronous save — used only on server stop or when caller must wait for disk. */
    public void saveToFileSync() {
        NbtHelper.atomicWriteCompressed(dataFile, save());
    }

    public void loadLocations() { load(); }

    /**
     * Load locations from a CompoundTag and immediately persist to disk.
     * Used by the backup-restore flow so the recovered data is saved right away.
     */
    public void loadFromTag(CompoundTag tag) {
        deserializeLocations(tag);
        saveToFile();
    }

    /**
     * Deserializes locations from a CompoundTag into the in-memory map.
     * Does NOT write to disk — callers that need persistence call {@link #saveToFile()} separately.
     * A location whose deserialization throws is skipped (logged) so one bad
     * entry never blocks the rest of the file from loading.
     */
    private void deserializeLocations(CompoundTag tag) {
        locations.clear();
        ListTag locationsList = tag.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            try {
                Location loc = Location.load(locationsList.getCompound(i));
                if (loc != null) locations.put(loc.getName(), loc);
            } catch (Exception ex) {
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Skipping malformed location entry #{}: {}",
                    i, ex.getMessage());
            }
        }
    }

    /**
     * Migration seam for forward-compatible schema changes. v0→v1 is a no-op
     * (the field layout never changed); future versions add transforms here.
     *
     * @param root        the raw root tag read from disk
     * @param fromVersion the {@code version} field found in the file (0 = legacy/unversioned)
     * @return the (possibly transformed) tag to deserialize
     */
    private CompoundTag migrate(CompoundTag root, int fromVersion) {
        if (fromVersion >= DATA_VERSION) return root;
        // No transforms needed for v0→v1 — layout is identical, the field was
        // simply added. Future schema bumps branch on fromVersion here.
        if (fromVersion < 1) {
            WaveDefenseMod.LOGGER.info("[WaveDefense] Migrating location data v{} → v{} (no-op).",
                fromVersion, DATA_VERSION);
        }
        return root;
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        try {
            CompoundTag data = NbtIo.readCompressed(dataFile);
            int fileVersion = data.contains("version") ? data.getInt("version") : 0;
            if (fileVersion > DATA_VERSION) {
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Location data version {} is newer than supported {}; loading anyway",
                    fileVersion, DATA_VERSION);
            } else if (fileVersion < DATA_VERSION) {
                data = migrate(data, fileVersion);
            }
            // Normal startup: just deserialize, do NOT rewrite the file needlessly
            deserializeLocations(data);
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Primary data file corrupt: {}", e.getMessage());
            // Спробуємо відновити з .bak копії
            File bakFile = new File(dataFile.getAbsolutePath() + ".bak");
            if (bakFile.exists()) {
                try {
                    WaveDefenseMod.LOGGER.warn("[WaveDefense] Attempting to restore from backup file...");
                    CompoundTag bakData = NbtIo.readCompressed(bakFile);
                    // Backup restore: deserialize AND save so the primary file is repaired
                    loadFromTag(bakData);
                    WaveDefenseMod.LOGGER.info("[WaveDefense] Successfully restored {} location(s) from backup.",
                        locations.size());
                } catch (IOException bakEx) {
                    WaveDefenseMod.LOGGER.error("[WaveDefense] Backup file also corrupt: {}. Starting with empty location list.", bakEx.getMessage());
                    // Перейменовуємо пошкоджений файл щоб не затирати нові дані
                    File corruptFile = new File(dataFile.getAbsolutePath() + ".corrupted");
                    //noinspection ResultOfMethodCallIgnored
                    dataFile.renameTo(corruptFile);
                }
            } else {
                WaveDefenseMod.LOGGER.error("[WaveDefense] No backup file found. Starting with empty location list.");
                File corruptFile = new File(dataFile.getAbsolutePath() + ".corrupted");
                //noinspection ResultOfMethodCallIgnored
                dataFile.renameTo(corruptFile);
            }
        }
    }
    /** Повертає поточну хвилю для вказаної локації (0 якщо неактивна). */
    public int getCurrentWaveForLocation(String locationName) {
        if (com.wavedefense.WaveDefenseMod.waveManager == null) return 0;
        return com.wavedefense.WaveDefenseMod.waveManager.getCurrentWaveForLocation(locationName);
    }
}
