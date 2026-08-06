package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: replace a location wholesale.
 *
 * <p><b>Every content-sized list is deliberately excluded.</b> Shops, waves, loot spawns,
 * reward tables and per-team kits all hold modded items whose NBT can be enormous — a
 * generated TACZ pack alone runs to several megabytes. Shipping any of them here meant
 * that editing <em>anything</em> on such a location exceeded the 32767-byte serverbound
 * payload limit and dropped the connection. Renaming an arena should not have to send its
 * entire weapon catalogue.
 *
 * <p>Senders build the payload through {@link #withoutBigLists(Location)}; when a
 * preserved list is missing from the incoming tag the handler keeps whatever the server
 * already holds. Those lists travel on their own channels instead:
 * {@link ShopItemOpPacket} for a single shop entry, {@link BulkAddShopItemsPacket} for a
 * bulk upload, and {@link ReplaceLocationListPacket} for a chunked full replace of any
 * one of them.
 *
 * <p><b>Adding a list to {@link com.wavedefense.data.LocationSection#isContentSizedList} without giving its editor a transport
 * silently discards that editor's changes</b> — which is exactly what happened to shop
 * points the first time round. Keep the two in step.
 */
public class UpdateLocationPacket {

    /**
     * Lists held back from this packet and preserved from live server state on arrival.
     *
     * <p>These are the entries whose size is driven by content rather than by a fixed
     * schema: a generated shop reaches thousands of items, and a wave list can hold 100
     * waves × 20 mob types × six NBT-carrying equipment slots. Everything else in a
     * location is scalars and short lists, which stay comfortably inside the payload
     * limit. Each has its own per-entry packet.
     */
    private static java.util.Set<String> preservedLists() {
        return com.wavedefense.data.LocationSection.contentSizedLists();
    }

    private final CompoundTag locationNbt;

    /** Builds a packet from an already-prepared tag. */
    private UpdateLocationPacket(CompoundTag locationNbt) {
        this.locationNbt = locationNbt;
    }

    /**
     * Standard constructor — strips the shop automatically, so no caller can
     * accidentally reintroduce the oversized payload.
     */
    public UpdateLocationPacket(Location location) {
        this(withoutBigLists(location));
    }

    /** Serializes {@code location} without the content-sized lists. */
    public static CompoundTag withoutBigLists(Location location) {
        CompoundTag tag = location.save();
        for (String key : preservedLists()) tag.remove(key);
        return tag;
    }

    public static void encode(UpdateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.locationNbt);
    }

    public static UpdateLocationPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) return null;
        return new UpdateLocationPacket(tag);
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2) || packet.locationNbt == null) return;
            // Rate-limit: full-location save is expensive (NBT decode + write + broadcast).
            // 500 ms blocks accidental double-Save clicks without hurting fast admin work.
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), UpdateLocationPacket.class, 500L)) return;

            String locName = packet.locationNbt.getString("name");
            if (locName.isEmpty()) return;

            // Carry the live shop across: the client never sends it, so rebuilding the
            // location from this tag alone would wipe it.
            Location live = WaveDefenseMod.locationManager.getLocation(locName);
            CompoundTag merged = packet.locationNbt.copy();
            if (live != null) {
                CompoundTag liveNbt = live.save();
                for (String key : preservedLists()) {
                    if (!merged.contains(key) && liveNbt.contains(key)) {
                        merged.put(key, liveNbt.get(key).copy());
                    }
                }
            }

            Location updated = Location.load(merged);
            if (updated == null) return;
            WaveDefenseMod.locationManager.updateLocation(updated);

            // Broadcastуємо оновлені дані локацій всім гравцям на сервері
            WaveDefenseMod.waveManager.broadcastLocationData();

            // Синхронізуємо магазин з гравцями що зараз на цій локації
            CompoundTag locNbt = updated.save();
            SyncShopPacket syncPkt = new SyncShopPacket(locName, locNbt);
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(p.getUUID());
                if (pd != null && pd.getCurrentLocation() != null
                        && pd.getCurrentLocation().getName().equals(locName)) {
                    PacketHandler.sendToPlayer(p, syncPkt);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
