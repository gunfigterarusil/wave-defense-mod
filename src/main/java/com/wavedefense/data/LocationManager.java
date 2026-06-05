package com.wavedefense.data;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocationManager {
    private static final int DATA_VERSION = 1;

    private final List<Location> locations = new ArrayList<>();
    private final File dataFile;

    public LocationManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.storage.FolderName.ROOT).resolve("data/wavedefense_locations.dat").toFile();
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

    /** v1.16.5 port — convenience for command/network code that only needs names. */
    public List<String> getAllLocationNames() {
        List<String> names = new ArrayList<>();
        for (Location l : locations) names.add(l.getName());
        return names;
    }

    public boolean locationExists(String name) {
        return locations.stream().anyMatch(loc -> loc.getName().equals(name));
    }

    public CompoundNBT save() {
        CompoundNBT data = new CompoundNBT();
        data.putInt("version", DATA_VERSION);
        ListNBT locationsList = new ListNBT();
        for (Location loc : locations) {
            locationsList.add(loc.save());
        }
        data.put("locations", locationsList);
        return data;
    }

    public void saveToFile() {
        try {
            dataFile.getParentFile().mkdirs();
            // Serialize once, then write atomically: .tmp → main, current main → .bak
            CompoundNBT data = save();
            File tmpFile = new File(dataFile.getAbsolutePath() + ".tmp");
            CompressedStreamTools.writeCompressed(data, tmpFile);
            File bakFile = new File(dataFile.getAbsolutePath() + ".bak");
            if (dataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dataFile.renameTo(bakFile);
            }
            //noinspection ResultOfMethodCallIgnored
            tmpFile.renameTo(dataFile);
        } catch (IOException e) {
            WaveDefenceMod.LOGGER.error("Could not save location data", e);
        }
    }

    public void loadLocations() { load(); }

    /**
     * Load locations from a CompoundNBT and immediately persist to disk.
     * Used by the backup-restore flow so the recovered data is saved right away.
     */
    public void loadFromTag(CompoundNBT tag) {
        deserializeLocations(tag);
        saveToFile();
    }

    /**
     * Deserializes locations from a CompoundNBT into the in-memory list.
     * Does NOT write to disk — callers that need persistence call {@link #saveToFile()} separately.
     */
    private void deserializeLocations(CompoundNBT tag) {
        locations.clear();
        ListNBT locationsList = tag.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            locations.add(Location.load(locationsList.getCompound(i)));
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        try {
            CompoundNBT data = CompressedStreamTools.readCompressed(dataFile);
            int fileVersion = data.contains("version") ? data.getInt("version") : 0;
            if (fileVersion > DATA_VERSION) {
                WaveDefenceMod.LOGGER.warn("[WaveDefense] Location data version {} is newer than supported {}; loading anyway",
                    fileVersion, DATA_VERSION);
            }
            // Normal startup: just deserialize, do NOT rewrite the file needlessly
            deserializeLocations(data);
        } catch (IOException e) {
            WaveDefenceMod.LOGGER.error("[WaveDefense] Primary data file corrupt: {}", e.getMessage());
            // Спробуємо відновити з .bak копії
            File bakFile = new File(dataFile.getAbsolutePath() + ".bak");
            if (bakFile.exists()) {
                try {
                    WaveDefenceMod.LOGGER.warn("[WaveDefense] Attempting to restore from backup file...");
                    CompoundNBT bakData = CompressedStreamTools.readCompressed(bakFile);
                    // Backup restore: deserialize AND save so the primary file is repaired
                    loadFromTag(bakData);
                    WaveDefenceMod.LOGGER.info("[WaveDefense] Successfully restored {} location(s) from backup.",
                        locations.size());
                } catch (IOException bakEx) {
                    WaveDefenceMod.LOGGER.error("[WaveDefense] Backup file also corrupt: {}. Starting with empty location list.", bakEx.getMessage());
                    // Перейменовуємо пошкоджений файл щоб не затирати нові дані
                    File corruptFile = new File(dataFile.getAbsolutePath() + ".corrupted");
                    //noinspection ResultOfMethodCallIgnored
                    dataFile.renameTo(corruptFile);
                }
            } else {
                WaveDefenceMod.LOGGER.error("[WaveDefense] No backup file found. Starting with empty location list.");
                File corruptFile = new File(dataFile.getAbsolutePath() + ".corrupted");
                //noinspection ResultOfMethodCallIgnored
                dataFile.renameTo(corruptFile);
            }
        }
    }
    /** Current wave number for the given location (0 if inactive).
     *  Phase 1 v0.0.1 stub: always returns 0 — WaveManager arrives in Phase 2. */
    public int getCurrentWaveForLocation(String locationName) {
        return 0;
    }
}
