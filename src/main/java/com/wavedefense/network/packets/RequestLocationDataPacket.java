package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestLocationDataPacket {
    public RequestLocationDataPacket() {}

    public static void encode(RequestLocationDataPacket packet, FriendlyByteBuf buf) {}

    public static RequestLocationDataPacket decode(FriendlyByteBuf buf) {
        return new RequestLocationDataPacket();
    }

    public static void handle(RequestLocationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Rate-limit: full location-tree sync is expensive (NBT serialization + network);
            // 1 s is enough for editor reload, blocks spam-refresh DoS.
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), RequestLocationDataPacket.class, 1000L)) return;
            WaveDefenseMod.packetHandler.send(PacketDistributor.PLAYER.with(() -> player),
                    new SyncLocationDataPacket(WaveDefenseMod.locationManager.save()));
        });
        ctx.get().setPacketHandled(true);
    }
}
