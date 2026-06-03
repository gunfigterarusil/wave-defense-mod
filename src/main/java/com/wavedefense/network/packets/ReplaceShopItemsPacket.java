package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client → server: REPLACE a location's shop items list, transferred in chunks.
 *
 * <p>Different from {@link BulkAddShopItemsPacket} (which appends): this packet
 * is the chunked equivalent of sending the full shop in an {@link UpdateLocationPacket},
 * used by {@link com.wavedefense.gui.ShopEditorScreen} when the shop has more
 * items than safely fit into one Forge channel payload (~32 KB).
 *
 * <p>Protocol:
 * <ul>
 *   <li>Client sends N packets each with: locationName, chunkIndex (0-based),
 *       totalChunks, items[].</li>
 *   <li>Server accumulates into a per-(player, location) buffer keyed by chunkIndex.</li>
 *   <li>When all chunks (count == totalChunks) have arrived, the buffer is
 *       flushed: location.shopItems is REPLACED (not appended) with the merged
 *       list, then broadcast.</li>
 *   <li>Stale buffers (waiting for missing chunks) are cleaned up after 30 s.</li>
 * </ul>
 *
 * <p>v0.2.64.
 */
public class ReplaceShopItemsPacket {

    // ── Per-(player, location) accumulator on the server ─────────────────
    private static final Map<String, PendingReplace> PENDING = new ConcurrentHashMap<>();
    private static final long STALE_AFTER_MS = 30_000L;

    private static String key(UUID player, String loc) { return player + "|" + loc; }

    private static class PendingReplace {
        final int totalChunks;
        final Map<Integer, List<ShopItem>> chunks = new HashMap<>();
        final long createdAt = System.currentTimeMillis();
        PendingReplace(int total) { this.totalChunks = total; }
    }

    private final String locationName;
    private final int chunkIndex;
    private final int totalChunks;
    private final List<ShopItem> items;

    public ReplaceShopItemsPacket(String locationName, int chunkIndex, int totalChunks, List<ShopItem> items) {
        this.locationName = locationName;
        this.chunkIndex   = chunkIndex;
        this.totalChunks  = totalChunks;
        this.items        = items != null ? items : new ArrayList<>();
    }

    public static void encode(ReplaceShopItemsPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName == null ? "" : p.locationName, 64);
        buf.writeVarInt(p.chunkIndex);
        buf.writeVarInt(p.totalChunks);
        ListTag list = new ListTag();
        for (ShopItem si : p.items) if (si != null) list.add(si.save());
        CompoundTag tag = new CompoundTag();
        tag.put("items", list);
        buf.writeNbt(tag);
    }

    public static ReplaceShopItemsPacket decode(FriendlyByteBuf buf) {
        String loc   = buf.readUtf(64);
        int chunkIx  = buf.readVarInt();
        int total    = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        List<ShopItem> items = new ArrayList<>();
        if (tag != null && tag.contains("items")) {
            ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                try { items.add(ShopItem.load(list.getCompound(i))); }
                catch (Throwable ignored) {}
            }
        }
        return new ReplaceShopItemsPacket(loc, chunkIx, total, items);
    }

    public static void handle(ReplaceShopItemsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (p.locationName == null || p.locationName.isBlank()) return;
            if (p.totalChunks < 1 || p.chunkIndex < 0 || p.chunkIndex >= p.totalChunks) return;

            Location loc = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            // Periodic stale cleanup (cheap — runs only on packet arrival)
            cleanupStale();

            String k = key(player.getUUID(), p.locationName);
            PendingReplace acc = PENDING.computeIfAbsent(k, kk -> new PendingReplace(p.totalChunks));

            // Total-chunks mismatch → restart the buffer with the new value (client retry?)
            if (acc.totalChunks != p.totalChunks) {
                PENDING.put(k, acc = new PendingReplace(p.totalChunks));
            }
            acc.chunks.put(p.chunkIndex, p.items);

            // All chunks received? Replace shop items and broadcast.
            if (acc.chunks.size() >= acc.totalChunks) {
                List<ShopItem> merged = new ArrayList<>();
                for (int i = 0; i < acc.totalChunks; i++) {
                    List<ShopItem> chunk = acc.chunks.get(i);
                    if (chunk != null) merged.addAll(chunk);
                }
                loc.getShopItems().clear();
                loc.getShopItems().addAll(merged);
                PENDING.remove(k);
                // Persist + sync the rest of the location to clients (same as
                // existing UpdateLocationPacket flow does, just without the
                // shop-items payload that's already on the server).
                WaveDefenseMod.locationManager.save();
                WaveDefenseMod.waveManager.broadcastLocationData();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void cleanupStale() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> (now - e.getValue().createdAt) > STALE_AFTER_MS);
    }
}
