package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TeleportPacket {
    private final String locationName;

    public TeleportPacket(String locationName) {
        this.locationName = locationName;
    }

    public static void encode(TeleportPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
    }

    public static TeleportPacket decode(FriendlyByteBuf buf) {
        return new TeleportPacket(buf.readUtf());
    }

    public static void handle(TeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (packet.locationName.equals("surrender")) {
                WaveDefenseMod.waveManager.surrenderPlayer(player);
                return;
            }

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null || location.getPlayerSpawn() == null) {
                WaveDefenseMod.LOGGER.warn("Invalid location: " + packet.locationName);
                return;
            }

            WaveDefenseMod.waveManager.addPlayerToLocation(player, location);
        });
        ctx.get().setPacketHandled(true);
    }
}
