package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SurrenderPacket {

    public SurrenderPacket() {
    }

    public static void encode(SurrenderPacket packet, FriendlyByteBuf buf) {
    }

    public static SurrenderPacket decode(FriendlyByteBuf buf) {
        return new SurrenderPacket();
    }

    public static void handle(SurrenderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit surrender to once per 10 s to prevent exploit/spam
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), SurrenderPacket.class, 10_000L)) return;
            WaveDefenseMod.waveManager.surrenderPlayer(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
