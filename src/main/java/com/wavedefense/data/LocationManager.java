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
import java.util.List;

public class LocationManager {
    private static final int DATA_VERSION = 1;

    private final List<Location> locations = new ArrayList<>();
    private final File dataFile;

    public LocationManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/wavedefense_locations.dat").toFile();
        load();
    }

    public void createLocation(String name) {
        if (getLocation(name) == null) {
            locations.add(new Location(name));
            saveToFile();
        }
    }

    public void addLocation(Location loc) {
        locations.removeIf(l -> l.getName().equals(loc.getName())); // replace if exists
        locations.add(loc);
        saveToFile();
    }

    public void removeLocation(String name) {
        locations.removeIf(loc -> loc.getName().equals(name));
        saveToFile();
    }

    public void updateLocation(Location updatedLocation) {
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getName().equals(updatedLocation.getName())) {
                locations.set(i, updatedLocation);
                saveToFile();
                return;
            }
        }
    }

    @Nullable
    public Location getLocation(String name) {
        return locations.stream()
                .filter(loc -> loc.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<Location> getAllLocations() {
        return locations;
    }

    public boolean locationExists(String name) {
        return locations.stream().anyMatch(loc -> loc.getName().equals(name));
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        data.putInt("version", DATA_VERSION);
        ListTag locationsList = new ListTag();
        for (Location loc : locations) {
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
     * Deserializes locations from a CompoundTag into the in-memory list.
     * Does NOT write to disk — callers that need persistence call {@link #saveToFile()} separately.
     */
    private void deserializeLocations(CompoundTag tag) {
        locations.clear();
        ListTag locationsList = tag.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            locations.add(Location.load(locationsList.getCompound(i)));
        }
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
