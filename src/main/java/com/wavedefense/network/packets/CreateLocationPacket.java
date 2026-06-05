package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import java.util.function.Supplier;

public class CreateLocationPacket {
    private final String locationName;

    public CreateLocationPacket(String name) {
        this.locationName = name;
    }

    public static void encode(CreateLocationPacket packet, PacketBuffer buf) {
        buf.writeUtf(packet.locationName);
    }

    public static CreateLocationPacket decode(PacketBuffer buf) {
        return new CreateLocationPacket(buf.readUtf());
    }

    public static void handle(CreateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Check for admin permissions
                WaveDefenceMod.locationManager.createLocation(packet.locationName);
                WaveDefenceMod.waveManager.broadcastLocationData();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
