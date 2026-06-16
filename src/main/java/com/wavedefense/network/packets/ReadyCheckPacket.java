package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: toggle the sender's ready state during READY_CHECK phase.
 *
 * <p>The sender's UUID is read from {@code ctx.getSender()} (never trust the
 * payload's identity). The {@code ready} boolean is true to mark ready, false
 * to un-ready (player may change their mind before timeout).
 *
 * <p>Server-side guard: if the player isn't actually in a PvP location that's
 * in READY_CHECK phase, the packet is silently ignored. This keeps malicious
 * or stale packets from advancing other players' matches.
 */
public class ReadyCheckPacket {

    private final boolean ready;

    public ReadyCheckPacket(boolean ready) { this.ready = ready; }

    public static void encode(ReadyCheckPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.ready);
    }

    public static ReadyCheckPacket decode(FriendlyByteBuf buf) {
        return new ReadyCheckPacket(buf.readBoolean());
    }

    public static void handle(ReadyCheckPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || WaveDefenseMod.waveManager == null) return;
            // Rate-limit: 250 ms is enough for honest re-press; blocks key-spam macros.
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), ReadyCheckPacket.class, 250L)) return;
            PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
            if (pd == null || pd.getCurrentLocation() == null) return;
            String locName = pd.getCurrentLocation().getName();
            if (p.ready) {
                WaveDefenseMod.waveManager.pvpMgr.markPlayerReady(
                    WaveDefenseMod.waveManager, locName, player.getUUID());
            } else {
                WaveDefenseMod.waveManager.pvpMgr.unmarkPlayerReady(
                    WaveDefenseMod.waveManager, locName, player.getUUID());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
