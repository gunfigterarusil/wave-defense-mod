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

    public static void encode(UpdateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.locationData);
    }

    public static UpdateLocationPacket decode(FriendlyByteBuf buf) {
        return new UpdateLocationPacket(Location.load(buf.readNbt()));
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                Location updatedLocation = Location.load(packet.locationData);
                WaveDefenseMod.locationManager.updateLocation(updatedLocation);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
