package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.function.Supplier;

public class RequestLocationDataPacket {
    public RequestLocationDataPacket() {}

    public static void encode(RequestLocationDataPacket packet, PacketBuffer buf) {}

    public static RequestLocationDataPacket decode(PacketBuffer buf) {
        return new RequestLocationDataPacket();
    }

    public static void handle(RequestLocationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                // Send the location data back to the client
                com.wavedefense.network.PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new SyncLocationDataPacket(WaveDefenceMod.locationManager.save()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
