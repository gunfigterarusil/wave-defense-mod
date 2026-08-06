package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: send me the shop for one location.
 *
 * <p>The location broadcast deliberately omits shops — a generated pack is megabytes and
 * every player would receive it on login and on every change. Screens that actually need
 * the shop ask for it here, and the server replies with a {@link SyncShopPacket} carrying
 * that one location.
 *
 * <p>Readable by any player: a shop's contents and prices are shown in the buy screen
 * anyway. Rate-limited so it cannot be used to make the server serialize a large shop in
 * a loop.
 */
public class RequestShopDataPacket {

    private final String locationName;

    public RequestShopDataPacket(String locationName) {
        this.locationName = locationName;
    }

    public static void encode(RequestShopDataPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName == null ? "" : p.locationName, 256);
    }

    public static RequestShopDataPacket decode(FriendlyByteBuf buf) {
        return new RequestShopDataPacket(buf.readUtf(256));
    }

    public static void handle(RequestShopDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Serializing a large shop is not free; one request per 250 ms is plenty for
            // opening a screen and stops a client from looping it.
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), RequestShopDataPacket.class, 250L)) return;
            if (p.locationName == null || p.locationName.isBlank()) return;

            Location loc = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            PacketHandler.sendToPlayer(player, new SyncShopPacket(p.locationName, loc.save()));
        });
        ctx.get().setPacketHandled(true);
    }
}
