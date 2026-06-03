package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * S→C: Синхронізує список тімейтів гравця на локації.
 * PvE: всі гравці на локації.
 * PvP: лише гравці тієї ж команди.
 * Порожній список = гравець вийшов / сесія завершена → HUD обнуляється.
 */
public class SyncTeammatesPacket {

    private final CompoundTag data;

    private SyncTeammatesPacket(CompoundTag data) { this.data = data; }

    public static SyncTeammatesPacket build(String locationName, List<PlayerEntry> players) {
        CompoundTag tag = new CompoundTag();
        tag.putString("location", locationName);
        ListTag list = new ListTag();
        for (PlayerEntry e : players) {
            CompoundTag p = new CompoundTag();
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

    public static void encode(SyncTeammatesPacket p, FriendlyByteBuf buf) { buf.writeNbt(p.data); }
    public static SyncTeammatesPacket decode(FriendlyByteBuf buf) { return new SyncTeammatesPacket(buf.readNbt()); }

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

    public record PlayerEntry(String name, UUID uuid, int hp, int maxHp, boolean alive,
                              String team, double x, double y, double z, float yaw) {
        // Convenience overload for callers that don't need positions
        public PlayerEntry(String name, UUID uuid, int hp, int maxHp, boolean alive, String team) {
            this(name, uuid, hp, maxHp, alive, team, 0d, 0d, 0d, 0f);
        }
    }
}
