package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClientLocationManager {
    private static List<Location> locations = new ArrayList<>();

    /**
     * Replaces the cached location list.
     *
     * <p>The server strips shops from this broadcast — they are large and are delivered
     * separately by {@code SyncShopPacket}. Any shop already cached for a location is
     * therefore carried across, otherwise every broadcast would blank a shop the client
     * had legitimately fetched a moment earlier.
     */
    public static void updateLocations(CompoundTag data) {
        List<Location> incoming = new ArrayList<>();
        ListTag locationsList = data.getList("locations", 10);
        for (int i = 0; i < locationsList.size(); i++) {
            Location fresh = Location.load(locationsList.getCompound(i));
            if (fresh == null) continue;
            Location cached = getLocation(fresh.getName());
            if (cached != null) {
                if (fresh.getShopItems().isEmpty() && !cached.getShopItems().isEmpty()) {
                    fresh.getShopItems().addAll(cached.getShopItems());
                }
                if (fresh.getShopPoints().isEmpty() && !cached.getShopPoints().isEmpty()) {
                    fresh.getShopPoints().addAll(cached.getShopPoints());
                }
            }
            incoming.add(fresh);
        }
        locations = incoming;
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