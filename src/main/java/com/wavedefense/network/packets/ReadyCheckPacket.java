package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void encode(ReadyCheckPacket p, PacketBuffer buf) {
        buf.writeBoolean(p.ready);
    }

    public static ReadyCheckPacket decode(PacketBuffer buf) {
        return new ReadyCheckPacket(buf.readBoolean());
    }

    public static void handle(ReadyCheckPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || WaveDefenceMod.waveManager == null) return;
            PlayerWaveData pd = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
            if (pd == null || pd.getCurrentLocation() == null) return;
            String locName = pd.getCurrentLocation().getName();
            if (p.ready) {
                WaveDefenceMod.waveManager.pvpMgr.markPlayerReady(
                    WaveDefenceMod.waveManager, locName, player.getUUID());
            } else {
                WaveDefenceMod.waveManager.pvpMgr.unmarkPlayerReady(
                    WaveDefenceMod.waveManager, locName, player.getUUID());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
