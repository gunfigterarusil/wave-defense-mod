package com.wavedefense.gui;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientTeammatesManager {

    /** 1.16.5 port: record → plain final class with accessor methods. */
    public static final class PlayerEntry {
        public final String name;
        public final UUID uuid;
        public final int hp, maxHp;
        public final boolean alive;
        public final String team;
        public final double x, y, z;
        public final float yaw;
        public PlayerEntry(String name, UUID uuid, int hp, int maxHp, boolean alive,
                           String team, double x, double y, double z, float yaw) {
            this.name = name; this.uuid = uuid; this.hp = hp; this.maxHp = maxHp;
            this.alive = alive; this.team = team;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw;
        }
        public String  name()  { return name; }
        public UUID    uuid()  { return uuid; }
        public int     hp()    { return hp; }
        public int     maxHp() { return maxHp; }
        public boolean alive() { return alive; }
        public String  team()  { return team; }
        public double  x()     { return x; }
        public double  y()     { return y; }
        public double  z()     { return z; }
        public float   yaw()   { return yaw; }
    }

    private static String locationName = "";
    private static final List<PlayerEntry> players = new ArrayList<>();

    public static void update(CompoundNBT data) {
        locationName = data.getString("location");
        players.clear();
        // Порожній location = сигнал очищення (сесія завершена / вихід)
        if (locationName.isEmpty()) return;

        ListNBT list = data.getList("players", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT p = list.getCompound(i);
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
