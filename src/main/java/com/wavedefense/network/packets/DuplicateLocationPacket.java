package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: duplicate an existing location under a new name.
 *
 * <p>The server clones the source Location via the canonical NBT roundtrip
 * ({@code Location.load(src.save())}) — same path used by editor deep-copy,
 * so every persisted field is carried over. The clone has its own copy of
 * waves, shop items, spawn points, capture points, etc. (no shared mutable
 * state with the source).
 *
 * <p>Validation:
 * <ul>
 *   <li>Sender must have permission level &gt;= 2.</li>
 *   <li>Source location must exist.</li>
 *   <li>Target name must not already exist (the client should check too, but
 *       this is the authoritative check).</li>
 *   <li>Target name must match the same regex as new-location names
 *       ({@code [a-zA-Z0-9_-]+}).</li>
 * </ul>
 *
 * <p>v0.2.63.
 */
public class DuplicateLocationPacket {

    private final String sourceName;
    private final String targetName;

    public DuplicateLocationPacket(String sourceName, String targetName) {
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    public static void encode(DuplicateLocationPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.sourceName == null ? "" : p.sourceName, 64);
        buf.writeUtf(p.targetName == null ? "" : p.targetName, 64);
    }

    public static DuplicateLocationPacket decode(FriendlyByteBuf buf) {
        return new DuplicateLocationPacket(buf.readUtf(64), buf.readUtf(64));
    }

    public static void handle(DuplicateLocationPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (p.sourceName == null || p.sourceName.isBlank()) return;
            // Shared rule — see LocationManager.isValidName for why names are restricted.
            if (!com.wavedefense.data.LocationManager.isValidName(p.targetName)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.msg.invalid_location_name",
                    com.wavedefense.data.LocationManager.MAX_NAME_LENGTH), false);
                return;
            }

            Location src = WaveDefenseMod.locationManager.getLocation(p.sourceName);
            if (src == null) return;
            if (WaveDefenseMod.locationManager.getLocation(p.targetName) != null) return; // collision

            // Deep copy via NBT roundtrip — same path as editor's Cancel deep-copy
            Location clone = Location.load(src.save());
            clone.setName(p.targetName);
            WaveDefenseMod.locationManager.addLocation(clone);
            WaveDefenseMod.waveManager.broadcastLocationData();
        });
        ctx.get().setPacketHandled(true);
    }
}
