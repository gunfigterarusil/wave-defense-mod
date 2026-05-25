package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.LeaderboardRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.wavedefense.network.PacketHandler;

import java.util.List;
import java.util.function.Supplier;

/**
 * C→S: client requests leaderboard data for a given location + mode.
 * Server responds with {@link LeaderboardDataPacket}.
 */
public class RequestLeaderboardPacket {

    private final String locationName;
    private final String modeKey;

    public RequestLeaderboardPacket(String locationName, String modeKey) {
        this.locationName = locationName != null ? locationName : "";
        this.modeKey      = modeKey      != null ? modeKey      : "";
    }

    public static void encode(RequestLeaderboardPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName, 256);
        buf.writeUtf(p.modeKey, 64);
    }

    public static RequestLeaderboardPacket decode(FriendlyByteBuf buf) {
        return new RequestLeaderboardPacket(buf.readUtf(256), buf.readUtf(64));
    }

    public static void handle(RequestLeaderboardPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || WaveDefenseMod.leaderboardManager == null) return;
            List<LeaderboardRecord> records = WaveDefenseMod.leaderboardManager.getTop10(p.locationName, p.modeKey);
            PacketHandler.sendToPlayer(sender, new LeaderboardDataPacket(p.locationName, p.modeKey, records));
        });
        ctx.get().setPacketHandled(true);
    }
}
