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
import java.util.Optional;

public class LocationManager {
    private final List<Location> locations = new ArrayList<>();
    private final File dataFile;

    public LocationManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/wavedefense_locations.dat").toFile();
        load();
    }

    public void createLocation(String name) {
        if (getLocation(name) == null) {
            locations.add(new Location(name));
            save();
        }
    }

    public void removeLocation(String name) {
        locations.removeIf(loc -> loc.getName().equals(name));
        save();
    }

    public void updateLocation(Location updatedLocation) {
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getName().equals(updatedLocation.getName())) {
                locations.set(i, updatedLocation);
                save();
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

    public void save() {
        CompoundTag data = new CompoundTag();
        ListTag locationsList = new ListTag();
        for (Location loc : locations) {
            locationsList.add(loc.save());
        }
        data.put("locations", locationsList);

        try {
            dataFile.getParentFile().mkdirs();
            NbtIo.writeCompressed(data, dataFile);
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Could not save location data", e);
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        try {
            CompoundTag data = NbtIo.readCompressed(dataFile);
            ListTag locationsList = data.getList("locations", 10);
            for (int i = 0; i < locationsList.size(); i++) {
                locations.add(Location.load(locationsList.getCompound(i)));
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Could not load location data", e);
        }
    }
}
