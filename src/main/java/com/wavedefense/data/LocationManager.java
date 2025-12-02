package com.wavedefense.data;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocationManager {
    private static final String SAVE_FILE = "locations.dat";
    private final Map<String, Location> locations = new ConcurrentHashMap<>();
    private final File dataFile;

    public LocationManager(MinecraftServer server) {
        Path dataPath = server.getWorldPath(LevelResource.ROOT).resolve("data");
        this.dataFile = new File(dataPath.toFile(), SAVE_FILE);
        load();
    }

    public void addLocation(Location location) {
        if (location != null && location.getName() != null) {
            locations.put(location.getName(), location);
            save();
        }
    }

    public boolean locationExists(String name) {
        return locations.containsKey(name);
    }

    public void createLocation(String name) {
        if (!locationExists(name)) {
            addLocation(new Location(name));
        }
    }

    public void removeLocation(String name) {
        if (locations.remove(name) != null) {
            save();
        }
    }

    @Nullable
    public Location getLocation(String name) {
        return locations.get(name);
    }

    public Collection<Location> getAllLocations() {
        return locations.values();
    }

    public void save() {
        CompoundTag rootTag = new CompoundTag();
        ListTag locationsList = new ListTag();

        for (Location location : locations.values()) {
            locationsList.add(location.save());
        }

        rootTag.put("locations", locationsList);

        try {
            net.minecraft.nbt.NbtIo.writeCompressed(rootTag, dataFile);
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Could not save locations to " + dataFile.getAbsolutePath(), e);
        }
    }

    public void load() {
        if (!dataFile.exists()) {
            return;
        }

        try {
            CompoundTag rootTag = net.minecraft.nbt.NbtIo.readCompressed(dataFile);
            if (rootTag != null && rootTag.contains("locations", 9)) { // 9 = ListTag
                ListTag locationsList = rootTag.getList("locations", 10); // 10 = CompoundTag
                locations.clear();
                for (int i = 0; i < locationsList.size(); i++) {
                    CompoundTag locationTag = locationsList.getCompound(i);
                    Location location = Location.load(locationTag);
                    locations.put(location.getName(), location);
                }
                WaveDefenseMod.LOGGER.info("Loaded {} locations.", locations.size());
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Could not load locations from " + dataFile.getAbsolutePath(), e);
        }
    }
}
