package com.wavedefense.network.packets;

import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SyncPlayerDataPacket {
    private final CompoundTag data;

    public SyncPlayerDataPacket(PlayerWaveData playerData) {
        this.data = playerData.saveClientData();
    }

    private SyncPlayerDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncPlayerDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncPlayerDataPacket decode(FriendlyByteBuf buf) {
        return new SyncPlayerDataPacket(buf.readNbt());
    }

    public static void handle(SyncPlayerDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPlayerDataManager.updateData(packet.data);
        });
        ctx.get().setPacketHandled(true);
    }
}
