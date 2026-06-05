package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * S→C: Синхронізує список тімейтів гравця на локації.
 * PvE: всі гравці на локації.
 * PvP: лише гравці тієї ж команди.
 * Порожній список = гравець вийшов / сесія завершена → HUD обнуляється.
 */
public class SyncTeammatesPacket {

    private final CompoundNBT data;

    private SyncTeammatesPacket(CompoundNBT data) { this.data = data; }

    public static SyncTeammatesPacket build(String locationName, List<PlayerEntry> players) {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("location", locationName);
        ListNBT list = new ListNBT();
        for (PlayerEntry e : players) {
            CompoundNBT p = new CompoundNBT();
            p.putString("name", e.name());
            p.putString("uuid", e.uuid().toString());
            p.putInt("hp", e.hp());
            p.putInt("maxHp", e.maxHp());
            p.putBoolean("alive", e.alive());
            if (e.team() != null) p.putString("team", e.team());
            // Minimap positions — clients without minimap simply ignore these fields.
            p.putDouble("x", e.x());
            p.putDouble("y", e.y());
            p.putDouble("z", e.z());
            p.putFloat("yaw", e.yaw());
            list.add(p);
        }
        tag.put("players", list);
        return new SyncTeammatesPacket(tag);
    }

    public static void encode(SyncTeammatesPacket p, PacketBuffer buf) { buf.writeNbt(p.data); }
    public static SyncTeammatesPacket decode(PacketBuffer buf) { return new SyncTeammatesPacket(buf.readNbt()); }

    public static void handle(SyncTeammatesPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncTeammatesPacket p) {
            com.wavedefense.gui.ClientTeammatesManager.update(p.data);
        }
    }

    /** 1.16.5 port: Java 14+ record → plain final class with public final fields
     *  + accessor methods (name(), uuid(), …) matching record-style API used by
     *  ClientTeammatesManager + MinimapRenderer. */
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

        public PlayerEntry(String name, UUID uuid, int hp, int maxHp, boolean alive, String team) {
            this(name, uuid, hp, maxHp, alive, team, 0d, 0d, 0d, 0f);
        }

        // Record-style accessors so existing call sites (foo.name(), foo.uuid()) keep working
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
}
