package com.wavedefense.network.packets;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPlayerDataPacket {
    private final CompoundNBT data;

    public SyncPlayerDataPacket(PlayerWaveData playerData) {
        this.data = playerData.saveClientData();
    }

    private SyncPlayerDataPacket(CompoundNBT data) {
        this.data = data;
    }

    public static void encode(SyncPlayerDataPacket packet, PacketBuffer buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncPlayerDataPacket decode(PacketBuffer buf) {
        return new SyncPlayerDataPacket(buf.readNbt());
    }

    public static void handle(SyncPlayerDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncPlayerDataPacket packet) {
            com.wavedefense.gui.ClientPlayerDataManager.updateData(packet.data);
        }
    }
}
