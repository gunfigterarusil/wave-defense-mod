package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import java.util.function.Supplier;

public class UpdatePlayerSettingsPacket {
    private final boolean showTimer;
    private final boolean showNotifications;
    private final boolean showTeammates;

    public UpdatePlayerSettingsPacket(boolean showTimer, boolean showNotifications, boolean showTeammates) {
        this.showTimer = showTimer;
        this.showNotifications = showNotifications;
        this.showTeammates = showTeammates;
    }

    public static void encode(UpdatePlayerSettingsPacket p, PacketBuffer buf) {
        buf.writeBoolean(p.showTimer);
        buf.writeBoolean(p.showNotifications);
        buf.writeBoolean(p.showTeammates);
    }

    public static UpdatePlayerSettingsPacket decode(PacketBuffer buf) {
        return new UpdatePlayerSettingsPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(UpdatePlayerSettingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
                if (data != null) {
                    data.setShowTimer(p.showTimer);
                    data.setShowNotifications(p.showNotifications);
                    data.setShowTeammates(p.showTeammates);
                    // Надсилаємо оновлені дані назад клієнту (щоб HUD одразу відреагував)
                    WaveDefenceMod.waveManager.syncPlayerData(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
