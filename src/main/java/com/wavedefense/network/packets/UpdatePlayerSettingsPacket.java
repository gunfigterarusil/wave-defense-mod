package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class UpdatePlayerSettingsPacket {
    private final boolean showTimer;
    private final boolean showNotifications;
    private final boolean showTeammates;
    private final boolean boundaryParticles;

    public UpdatePlayerSettingsPacket(boolean showTimer, boolean showNotifications,
                                      boolean showTeammates, boolean boundaryParticles) {
        this.showTimer = showTimer;
        this.showNotifications = showNotifications;
        this.showTeammates = showTeammates;
        this.boundaryParticles = boundaryParticles;
    }

    public static void encode(UpdatePlayerSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.showTimer);
        buf.writeBoolean(p.showNotifications);
        buf.writeBoolean(p.showTeammates);
        buf.writeBoolean(p.boundaryParticles);
    }

    public static UpdatePlayerSettingsPacket decode(FriendlyByteBuf buf) {
        return new UpdatePlayerSettingsPacket(
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(UpdatePlayerSettingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Rate-limit: settings rarely change >once/sec under honest use
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), UpdatePlayerSettingsPacket.class, 500L)) return;
            PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
            if (data != null) {
                data.setShowTimer(p.showTimer);
                data.setShowNotifications(p.showNotifications);
                data.setShowTeammates(p.showTeammates);
                data.setBoundaryParticlesVisible(p.boundaryParticles);
                // Надсилаємо оновлені дані назад клієнту (щоб HUD одразу відреагував)
                WaveDefenseMod.waveManager.syncPlayerData(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
