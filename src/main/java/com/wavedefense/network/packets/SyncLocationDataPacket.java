package com.wavedefense.network.packets;

import com.wavedefense.gui.ClientLocationManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncLocationDataPacket {
    private final CompoundTag data;

    public SyncLocationDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncLocationDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncLocationDataPacket decode(FriendlyByteBuf buf) {
        return new SyncLocationDataPacket(buf.readNbt());
    }

    public static void handle(SyncLocationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Updated to use ClientLocationManager
            ClientLocationManager.updateLocations(packet.getData());
        });
        ctx.get().setPacketHandled(true);
    }

    public CompoundTag getData() {
        return data;
    }
}
