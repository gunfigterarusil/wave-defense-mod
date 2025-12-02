package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class DeleteLocationPacket {
    private final String locationName;

    public DeleteLocationPacket(String name) {
        this.locationName = name;
    }

    public static void encode(DeleteLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
    }

    public static DeleteLocationPacket decode(FriendlyByteBuf buf) {
        return new DeleteLocationPacket(buf.readUtf());
    }

    public static void handle(DeleteLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Check for admin permissions
                WaveDefenseMod.locationManager.removeLocation(packet.locationName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
