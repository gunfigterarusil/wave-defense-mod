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

    public UpdatePlayerSettingsPacket(boolean showTimer, boolean showNotifications) {
        this.showTimer = showTimer;
        this.showNotifications = showNotifications;
    }

    public static void encode(UpdatePlayerSettingsPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.showTimer);
        buf.writeBoolean(packet.showNotifications);
    }

    public static UpdatePlayerSettingsPacket decode(FriendlyByteBuf buf) {
        return new UpdatePlayerSettingsPacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(UpdatePlayerSettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
                if (data != null) {
                    data.setShowTimer(packet.showTimer);
                    data.setShowNotifications(packet.showNotifications);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
