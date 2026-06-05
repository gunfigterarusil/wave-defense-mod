package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SurrenderPacket {

    public SurrenderPacket() {
    }

    public static void encode(SurrenderPacket packet, PacketBuffer buf) {
    }

    public static SurrenderPacket decode(PacketBuffer buf) {
        return new SurrenderPacket();
    }

    public static void handle(SurrenderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit surrender to once per 10 s to prevent exploit/spam
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), SurrenderPacket.class, 10_000L)) return;
            WaveDefenceMod.waveManager.surrenderPlayer(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
