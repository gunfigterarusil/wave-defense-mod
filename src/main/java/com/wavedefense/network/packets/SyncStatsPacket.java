package com.wavedefense.network.packets;

import com.wavedefense.data.GameStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncStatsPacket {
    private final CompoundTag statsData;

    public SyncStatsPacket(GameStats stats) {
        this.statsData = stats.save();
    }

    private SyncStatsPacket(CompoundTag tag) {
        this.statsData = tag;
    }

    public static void encode(SyncStatsPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.statsData);
    }

    public static SyncStatsPacket decode(FriendlyByteBuf buf) {
        return new SyncStatsPacket(buf.readNbt());
    }

    public static void handle(SyncStatsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncStatsPacket packet) {
            com.wavedefense.gui.ClientStatsManager.updateStats(packet.statsData);
        }
    }
}
