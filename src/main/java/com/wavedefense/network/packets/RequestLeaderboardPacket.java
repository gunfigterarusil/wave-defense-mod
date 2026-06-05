package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.LeaderboardRecord;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
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

    public static void encode(RequestLeaderboardPacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName, 256);
        buf.writeUtf(p.modeKey, 64);
    }

    public static RequestLeaderboardPacket decode(PacketBuffer buf) {
        return new RequestLeaderboardPacket(buf.readUtf(256), buf.readUtf(64));
    }

    public static void handle(RequestLeaderboardPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender == null || WaveDefenceMod.leaderboardManager == null) return;
            // G1 fix: rate-limit leaderboard requests to once per 2 s
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    sender.getUUID(), RequestLeaderboardPacket.class, 2_000L)) return;
            List<LeaderboardRecord> records = WaveDefenceMod.leaderboardManager.getTop10(p.locationName, p.modeKey);
            PacketHandler.sendToPlayer(sender, new LeaderboardDataPacket(p.locationName, p.modeKey, records));
        });
        ctx.get().setPacketHandled(true);
    }
}
