package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class CreateLocationPacket {
    private final String locationName;

    public CreateLocationPacket(String name) {
        this.locationName = name;
    }

    public static void encode(CreateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
    }

    public static CreateLocationPacket decode(FriendlyByteBuf buf) {
        return new CreateLocationPacket(buf.readUtf());
    }

    public static void handle(CreateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Check for admin permissions
                WaveDefenseMod.locationManager.createLocation(packet.locationName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
