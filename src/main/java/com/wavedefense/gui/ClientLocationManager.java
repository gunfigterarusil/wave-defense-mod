package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClientLocationManager {
    private static List<Location> locations = new ArrayList<>();

    public static void updateLocations(CompoundNBT data) {
        locations.clear();
        ListNBT locationsList = data.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            locations.add(Location.load(locationsList.getCompound(i)));
        }
    }

    public static List<Location> getAllLocations() {
        return new ArrayList<>(locations);
    }

    public static List<String> getAllLocationNames() {
        return locations.stream().map(Location::getName).collect(java.util.stream.Collectors.toList());
    }

    public static Location getLocation(String name) {
        return locations.stream()
                .filter(loc -> loc.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** Оновлює одну локацію в кеші (для sync магазину). */
    public static void updateSingleLocation(Location updated) {
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getName().equals(updated.getName())) {
                locations.set(i, updated);
                return;
            }
        }
        locations.add(updated); // not found — add it
    }

}