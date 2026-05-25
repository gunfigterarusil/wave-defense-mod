package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: Гравець виходить з поточної локації (PvE або PvP) без штрафу.
 * Спрацьовує по keybind L — "Вийти з локації".
 * Поведінка аналогічна surrenderPlayer але без death-penalty.
 */
public class LeaveLocationPacket {

    public static void encode(LeaveLocationPacket p, FriendlyByteBuf buf) {}
    public static LeaveLocationPacket decode(FriendlyByteBuf buf) { return new LeaveLocationPacket(); }

    public static void handle(LeaveLocationPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit leave to once per 5 s (keybind can fire rapidly)
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), LeaveLocationPacket.class, 5_000L)) return;
            com.wavedefense.wave.PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
            if (data == null || data.getCurrentLocation() == null) return;
            // exitPvpLocation вже є — він universally виконує surrender без пенальті
            WaveDefenseMod.waveManager.exitPvpLocation(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
