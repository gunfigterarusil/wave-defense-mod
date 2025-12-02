package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class UpdateLocationPacket {
    private final Location location;

    public UpdateLocationPacket(Location location) {
        this.location = location;
    }

    public static void encode(UpdateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.location.save());
    }

    public static UpdateLocationPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) return null;
        return new UpdateLocationPacket(Location.load(tag));
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                if (packet.location != null) {
                    WaveDefenseMod.locationManager.updateLocation(packet.location);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
