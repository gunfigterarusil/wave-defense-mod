package com.wavedefense.network.packets;

import com.wavedefense.data.GameStats;
import com.wavedefense.gui.ClientStatsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
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
        ctx.get().enqueueWork(() -> {
            ClientStatsManager.updateStats(packet.statsData);
        });
        ctx.get().setPacketHandled(true);
    }
}
