package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import java.util.function.Supplier;

public class DeleteLocationPacket {
    private final String locationName;

    public DeleteLocationPacket(String name) {
        this.locationName = name;
    }

    public static void encode(DeleteLocationPacket packet, PacketBuffer buf) {
        buf.writeUtf(packet.locationName);
    }

    public static DeleteLocationPacket decode(PacketBuffer buf) {
        return new DeleteLocationPacket(buf.readUtf());
    }

    public static void handle(DeleteLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Check for admin permissions
                // C1 fix: end any active session before removing the location so players
                // are properly restored and the session map doesn't contain orphans.
                WaveDefenceMod.waveManager.endSessionForLocation(packet.locationName);
                WaveDefenceMod.locationManager.removeLocation(packet.locationName);
                WaveDefenceMod.waveManager.broadcastLocationData();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
