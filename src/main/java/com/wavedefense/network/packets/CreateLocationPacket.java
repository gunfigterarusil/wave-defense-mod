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
            if (player == null || !player.hasPermissions(2)) return; // admin only
            // The name becomes part of export filenames later, so reject anything that
            // could carry a separator or ".." before it ever reaches the data layer.
            if (!com.wavedefense.data.LocationManager.isValidName(packet.locationName)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.msg.invalid_location_name",
                    com.wavedefense.data.LocationManager.MAX_NAME_LENGTH), false);
                return;
            }
            if (WaveDefenseMod.locationManager.createLocation(packet.locationName)) {
                WaveDefenseMod.waveManager.broadcastLocationData();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
