package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client → server: replace one named list inside a location, streamed in chunks.
 *
 * <p>A location has several lists whose serialized size follows their contents rather
 * than a fixed schema — shops, waves, loot spawns, reward tables, per-team kits. Each one
 * can individually outgrow the 32767-byte serverbound payload limit once an admin fills
 * it with NBT-heavy modded items, and sending any of them inside a whole-location packet
 * takes the rest of the editor down with it.
 *
 * <p>Handling them one at a time proved fragile: excluding shops from
 * {@link UpdateLocationPacket} silently broke shop-point editing, because that list was
 * missed. This packet is deliberately generic — it addresses a list by its NBT key, so a
 * list added later is covered by registering its key rather than by writing a new packet.
 *
 * <p>Chunking is by <b>encoded size</b>, not element count. Element weight varies by
 * orders of magnitude — a plain iron sword against a TACZ rifle with attachments — so a
 * fixed "N per packet" is either wasteful or unsafe depending on the content.
 *
 * <p>Chunks accumulate per (player, location, list) and are applied together once the
 * final one arrives, so a half-delivered edit never overwrites a good list.
 */
public class ReplaceLocationListPacket {

    /**
     * Lists this packet may replace.
     *
     * <p>An allowlist rather than "any key": the payload is applied straight onto the
     * location's NBT, so without it a client could overwrite scalars — or the location's
     * own name — with arbitrary content.
     */
    private static boolean replaceable(String listKey) {
        return com.wavedefense.data.LocationSection.isContentSizedList(listKey);
    }

    /**
     * Byte budget per packet. Well under the 32767-byte hard limit, leaving room for the
     * location name, the list key and the NBT framing around the payload.
     */
    private static final int CHUNK_BUDGET_BYTES = 20_000;

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    private static final long STALE_AFTER_MS = 30_000L;

    private static String key(UUID player, String loc, String list) {
        return player + "|" + loc + "|" + list;
    }

    private static class Pending {
        final int totalChunks;
        final Map<Integer, ListTag> chunks = new HashMap<>();
        final long createdAt = System.currentTimeMillis();
        Pending(int total) { this.totalChunks = total; }
    }

    private final String locationName;
    private final String listKey;
    private final int chunkIndex;
    private final int totalChunks;
    private final ListTag chunk;

    public ReplaceLocationListPacket(String locationName, String listKey,
                                     int chunkIndex, int totalChunks, ListTag chunk) {
        this.locationName = locationName;
        this.listKey      = listKey;
        this.chunkIndex   = chunkIndex;
        this.totalChunks  = totalChunks;
        this.chunk        = chunk != null ? chunk : new ListTag();
    }

    // ── Client helper ────────────────────────────────────────────────────

    /**
     * Splits {@code full} by encoded size and sends every chunk.
     *
     * <p>An empty list still sends one empty chunk — that is how "the admin deleted
     * everything" is expressed; sending nothing would leave the old list in place.
     *
     * @param full the complete new contents of {@code listKey}
     */
    public static void sendList(String locationName, String listKey, ListTag full) {
        List<ListTag> chunks = new ArrayList<>();
        ListTag current = new ListTag();
        int currentBytes = 0;

        for (Tag element : full) {
            int size = encodedSize(element);
            // An element bigger than the whole budget still has to travel: give it a
            // chunk to itself rather than dropping it or stalling.
            if (currentBytes > 0 && currentBytes + size > CHUNK_BUDGET_BYTES) {
                chunks.add(current);
                current = new ListTag();
                currentBytes = 0;
            }
            current.add(element.copy());
            currentBytes += size;
        }
        chunks.add(current);   // always at least one, possibly empty

        for (int i = 0; i < chunks.size(); i++) {
            PacketHandler.sendToServer(new ReplaceLocationListPacket(
                locationName, listKey, i, chunks.size(), chunks.get(i)));
        }
    }

    /** Serialized size of one NBT element, used to pack chunks to a byte budget. */
    private static int encodedSize(Tag tag) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(out)) {
            tag.write(data);
            return out.size();
        } catch (Exception e) {
            // Unmeasurable element: assume it is expensive so it lands in its own chunk.
            return CHUNK_BUDGET_BYTES;
        }
    }

    // ── Wire format ──────────────────────────────────────────────────────

    public static void encode(ReplaceLocationListPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName == null ? "" : p.locationName, 256);
        buf.writeUtf(p.listKey == null ? "" : p.listKey, 64);
        buf.writeVarInt(p.chunkIndex);
        buf.writeVarInt(p.totalChunks);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("c", p.chunk);
        buf.writeNbt(wrapper);
    }

    public static ReplaceLocationListPacket decode(FriendlyByteBuf buf) {
        String loc  = buf.readUtf(256);
        String list = buf.readUtf(64);
        int idx     = buf.readVarInt();
        int total   = buf.readVarInt();
        CompoundTag wrapper = buf.readNbt();
        ListTag chunk = (wrapper != null) ? wrapper.getList("c", listElementType(list)) : new ListTag();
        return new ReplaceLocationListPacket(loc, list, idx, total, chunk);
    }

    /**
     * NBT element type for a list key. All of these are lists of compounds except
     * {@code startingItems}, which stores raw {@code ItemStack} tags — also compounds —
     * so the answer is uniform today, but the mapping is explicit so a future list of a
     * different type does not silently decode as empty.
     */
    private static byte listElementType(String listKey) {
        return Tag.TAG_COMPOUND;
    }

    // ── Server handling ──────────────────────────────────────────────────

    public static void handle(ReplaceLocationListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), ReplaceLocationListPacket.class, 50L)) return;
            if (p.locationName == null || p.locationName.isBlank()) return;
            if (p.listKey == null || !replaceable(p.listKey)) {
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Rejected list replace for '{}' by {}: not replaceable",
                    p.listKey, player.getName().getString());
                return;
            }
            if (p.totalChunks < 1 || p.chunkIndex < 0 || p.chunkIndex >= p.totalChunks) return;

            Location live = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (live == null) return;

            cleanupStale();

            String k = key(player.getUUID(), p.locationName, p.listKey);
            Pending acc = PENDING.computeIfAbsent(k, kk -> new Pending(p.totalChunks));
            if (acc.totalChunks != p.totalChunks) {
                PENDING.put(k, acc = new Pending(p.totalChunks));
            }
            acc.chunks.put(p.chunkIndex, p.chunk);

            if (acc.chunks.size() < acc.totalChunks) return;   // still streaming

            ListTag merged = new ListTag();
            for (int i = 0; i < acc.totalChunks; i++) {
                ListTag part = acc.chunks.get(i);
                if (part != null) merged.addAll(part);
            }
            PENDING.remove(k);

            // Apply at the NBT level so no per-list Java code is needed.
            CompoundTag tag = live.save();
            tag.put(p.listKey, merged);
            Location updated = Location.load(tag);
            if (updated == null) return;

            WaveDefenseMod.locationManager.updateLocation(updated);
            WaveDefenseMod.waveManager.broadcastLocationData();
            WaveDefenseMod.LOGGER.debug("[WaveDefense] Replaced '{}' in '{}' with {} entr(ies)",
                p.listKey, p.locationName, merged.size());
        });
        ctx.get().setPacketHandled(true);
    }

    /** Drops buffers whose missing chunks never arrived, so a dropped save cannot leak. */
    private static void cleanupStale() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(e -> now - e.getValue().createdAt > STALE_AFTER_MS);
    }
}
