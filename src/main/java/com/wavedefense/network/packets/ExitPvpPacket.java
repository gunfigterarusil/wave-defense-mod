package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт → Сервер: гравець хоче вийти з PvP локації (без штрафних очків).
 * Відрізняється від SurrenderPacket тим, що не нараховує пенальті —
 * це звичайний добровільний вихід ("Вийти з PvP").
 */
public class ExitPvpPacket {

    public static void encode(ExitPvpPacket p, FriendlyByteBuf buf) {}

    public static ExitPvpPacket decode(FriendlyByteBuf buf) {
        return new ExitPvpPacket();
    }

    public static void handle(ExitPvpPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit exit to once per 10 s
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), ExitPvpPacket.class, 10_000L)) return;
            WaveDefenseMod.waveManager.exitPvpLocation(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
