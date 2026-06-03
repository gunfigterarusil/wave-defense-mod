package com.wavedefense.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientTeammatesManager {

    public record PlayerEntry(String name, UUID uuid, int hp, int maxHp, boolean alive,
                               String team, double x, double y, double z, float yaw) {}

    private static String locationName = "";
    private static final List<PlayerEntry> players = new ArrayList<>();

    public static void update(CompoundTag data) {
        locationName = data.getString("location");
        players.clear();
        // Порожній location = сигнал очищення (сесія завершена / вихід)
        if (locationName.isEmpty()) return;

        ListTag list = data.getList("players", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            try {
                String team = p.contains("team") ? p.getString("team") : null;
                players.add(new PlayerEntry(
                    p.getString("name"),
                    UUID.fromString(p.getString("uuid")),
                    p.getInt("hp"),
                    p.getInt("maxHp"),
                    p.getBoolean("alive"),
                    team,
                    p.contains("x") ? p.getDouble("x") : 0d,
                    p.contains("y") ? p.getDouble("y") : 0d,
                    p.contains("z") ? p.getDouble("z") : 0d,
                    p.contains("yaw") ? p.getFloat("yaw") : 0f
                ));
            } catch (Exception ignored) {}
        }
    }

    public static void clear() {
        locationName = "";
        players.clear();
    }

    public static List<PlayerEntry> getPlayers() { return Collections.unmodifiableList(players); }
    public static String getLocationName() { return locationName; }
    public static int count() { return players.size(); }
}
