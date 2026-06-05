package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клієнт → Сервер: гравець хоче вийти з PvP локації (без штрафних очків).
 * Відрізняється від SurrenderPacket тим, що не нараховує пенальті —
 * це звичайний добровільний вихід ("Вийти з PvP").
 */
public class ExitPvpPacket {

    public static void encode(ExitPvpPacket p, PacketBuffer buf) {}

    public static ExitPvpPacket decode(PacketBuffer buf) {
        return new ExitPvpPacket();
    }

    public static void handle(ExitPvpPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit exit to once per 10 s
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), ExitPvpPacket.class, 10_000L)) return;
            WaveDefenceMod.waveManager.exitPvpLocation(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
