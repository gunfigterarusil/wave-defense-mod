package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → server: change exactly one entry in a location's shop.
 *
 * <p>Shop editing used to round-trip the entire {@link Location} through
 * {@code UpdateLocationPacket} for every add, edit or delete. That is fine for a
 * hand-made shop and fatal for a generated one: a few thousand TACZ guns serialize to
 * megabytes, so changing a single price exceeded the 32767-byte serverbound payload
 * limit and dropped the connection. The shop is the only list in a location that grows
 * without bound, so it gets its own per-item protocol.
 *
 * <p>Requires permission level &gt;= 2.
 */
public class ShopItemOpPacket {

    /** What to do with the entry at {@link #index}. */
    public enum Op { ADD, UPDATE, REMOVE }

    /** Sentinel for {@link Op#ADD}, which appends rather than targeting an index. */
    public static final int APPEND = -1;

    private final String locationName;
    /** Shop point to edit, or empty for the location's global shop. */
    private final String pointName;
    private final Op op;
    private final int index;
    /** Item payload; {@code null} for {@link Op#REMOVE}. */
    private final CompoundTag itemNbt;

    public ShopItemOpPacket(String locationName, String pointName, Op op, int index, CompoundTag itemNbt) {
        this.locationName = locationName;
        this.pointName = pointName == null ? "" : pointName;
        this.op = op;
        this.index = index;
        this.itemNbt = itemNbt;
    }

    /** Appends a new item to the end of the target shop. */
    public static ShopItemOpPacket add(String locationName, String pointName, ShopItem item) {
        return new ShopItemOpPacket(locationName, pointName, Op.ADD, APPEND, item.save());
    }

    /** Replaces the item at {@code index}. */
    public static ShopItemOpPacket update(String locationName, String pointName, int index, ShopItem item) {
        return new ShopItemOpPacket(locationName, pointName, Op.UPDATE, index, item.save());
    }

    /** Deletes the item at {@code index}. */
    public static ShopItemOpPacket remove(String locationName, String pointName, int index) {
        return new ShopItemOpPacket(locationName, pointName, Op.REMOVE, index, null);
    }

    public static void encode(ShopItemOpPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName == null ? "" : p.locationName, 256);
        buf.writeUtf(p.pointName, 128);
        buf.writeEnum(p.op);
        buf.writeVarInt(p.index);
        buf.writeBoolean(p.itemNbt != null);
        if (p.itemNbt != null) buf.writeNbt(p.itemNbt);
    }

    public static ShopItemOpPacket decode(FriendlyByteBuf buf) {
        String loc = buf.readUtf(256);
        String point = buf.readUtf(128);
        Op op = buf.readEnum(Op.class);
        int idx = buf.readVarInt();
        CompoundTag nbt = buf.readBoolean() ? buf.readNbt() : null;
        return new ShopItemOpPacket(loc, point, op, idx, nbt);
    }

    public static void handle(ShopItemOpPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), ShopItemOpPacket.class, 50L)) return;
            if (p.locationName == null || p.locationName.isBlank()) return;

            Location loc = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            // Empty pointName = the location's global shop; otherwise a named shop point.
            boolean global = p.pointName == null || p.pointName.isEmpty();
            List<ShopItem> shop;
            if (global) {
                shop = loc.getShopItems();
            } else {
                com.wavedefense.data.ShopPoint sp = loc.getShopPoints().stream()
                    .filter(x -> p.pointName.equals(x.getName()))
                    .findFirst().orElse(null);
                if (sp == null) return;   // point deleted while the editor was open
                shop = sp.getItems();
            }
            boolean changed = false;

            switch (p.op) {
                case ADD -> {
                    ShopItem item = parse(p.itemNbt);
                    if (item != null) {
                        int before = shop.size();
                        // The cap belongs to the location's global shop; a point's own
                        // list is bounded by how many the admin places by hand.
                        if (global) loc.addShopItem(item); else shop.add(item);
                        changed = shop.size() > before;
                        if (!changed) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "wavedefense.tacz.bulk.cap_reached",
                                com.wavedefense.config.WaveDefenseConfig.MAX_SHOP_ITEMS.get()), false);
                        }
                    }
                }
                case UPDATE -> {
                    ShopItem item = parse(p.itemNbt);
                    // Index is client-supplied: a stale editor could point past the end
                    // after another admin deleted an entry.
                    if (item != null && p.index >= 0 && p.index < shop.size()) {
                        shop.set(p.index, item);
                        changed = true;
                    }
                }
                case REMOVE -> {
                    if (p.index >= 0 && p.index < shop.size()) {
                        shop.remove(p.index);
                        changed = true;
                    }
                }
            }

            if (!changed) return;

            WaveDefenseMod.locationManager.saveToFile();
            WaveDefenseMod.waveManager.broadcastLocationData();

            // Re-sync the shop to anyone standing in the location so an open shop screen
            // reflects the change immediately.
            SyncShopPacket syncPkt = new SyncShopPacket(p.locationName, loc.save());
            for (ServerPlayer sp : player.getServer().getPlayerList().getPlayers()) {
                PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(sp.getUUID());
                if (pd != null && pd.getCurrentLocation() != null
                        && pd.getCurrentLocation().getName().equals(p.locationName)) {
                    PacketHandler.sendToPlayer(sp, syncPkt);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** Deserializes one shop item; a malformed payload is dropped rather than thrown. */
    private static ShopItem parse(CompoundTag nbt) {
        if (nbt == null) return null;
        try {
            return ShopItem.load(nbt);
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.warn("[WaveDefense] Rejected malformed shop item: {}", e.getMessage());
            return null;
        }
    }
}
