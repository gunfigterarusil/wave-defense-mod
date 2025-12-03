package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class UpdateLocationPacket {
    private final CompoundTag locationData;

    public UpdateLocationPacket(Location location) {
        this.locationData = location.save();
    }

    private UpdateLocationPacket(CompoundTag data) {
        this.locationData = data;
    }

    public static void encode(UpdateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.locationData);
    }

    public static UpdateLocationPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new UpdateLocationPacket(tag != null ? tag : new CompoundTag());
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                if (packet.locationData != null && !packet.locationData.isEmpty()) {
                    try {
                        Location updatedLocation = Location.load(packet.locationData);
                        WaveDefenseMod.locationManager.updateLocation(updatedLocation);
                        WaveDefenseMod.LOGGER.info("Location '{}' updated by {}", 
                            updatedLocation.getName(), player.getName().getString());
                    } catch (Exception e) {
                        WaveDefenseMod.LOGGER.error("Failed to update location", e);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
