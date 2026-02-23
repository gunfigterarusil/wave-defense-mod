package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClientLocationManager {
    private static List<Location> locations = new ArrayList<>();

    public static void updateLocations(CompoundTag data) {
        locations.clear();
        ListTag locationsList = data.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            locations.add(Location.load(locationsList.getCompound(i)));
        }
    }

    public static List<Location> getAllLocations() {
        return new ArrayList<>(locations);
    }

    public static List<String> getAllLocationNames() {
        return locations.stream().map(Location::getName).collect(Collectors.toList());
    }

    public static Location getLocation(String name) {
        return locations.stream()
                .filter(loc -> loc.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
