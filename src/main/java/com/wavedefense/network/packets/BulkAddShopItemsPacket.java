package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → server: append a batch of {@link ShopItem}s to a location's global shop.
 *
 * <p>Used by Tacz bulk-add and any other workflow that creates many items at once.
 * Sending one {@link UpdateLocationPacket} after building 100+ shop items can
 * overflow the Forge channel size (the entire Location NBT, including every
 * gun's GunId NBT, is serialised — that's tens of KB and grows quickly).
 *
 * <p>This packet carries only the locationName and the new items, which keeps each
 * packet small. Callers should batch in groups of ~25 items (see TaczBulkAddScreen).
 *
 * <p>Requires permission level &gt;= 2 to apply.
 */
public class BulkAddShopItemsPacket {

    private final String locationName;
    private final List<ShopItem> items;
    /**
     * True only on the final packet of an upload. Everything before it appends in
     * memory; the flag is what triggers the single save, broadcast and shop re-sync.
     */
    private final boolean last;

    public BulkAddShopItemsPacket(String locationName, List<ShopItem> items) {
        this(locationName, items, true);
    }

    public BulkAddShopItemsPacket(String locationName, List<ShopItem> items, boolean last) {
        this.locationName = locationName;
        this.items = items != null ? items : new ArrayList<>();
        this.last = last;
    }

    public static void encode(BulkAddShopItemsPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName == null ? "" : p.locationName, 256);
        ListTag list = new ListTag();
        for (ShopItem si : p.items) {
            if (si != null) list.add(si.save());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("items", list);
        buf.writeNbt(tag);
        buf.writeBoolean(p.last);
    }

    public static BulkAddShopItemsPacket decode(FriendlyByteBuf buf) {
        String loc = buf.readUtf(256);
        CompoundTag tag = buf.readNbt();
        List<ShopItem> items = new ArrayList<>();
        if (tag != null && tag.contains("items")) {
            ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                try { items.add(ShopItem.load(list.getCompound(i))); }
                catch (Throwable ignored) {}
            }
        }
        boolean last = buf.readBoolean();
        return new BulkAddShopItemsPacket(loc, items, last);
    }

    public static void handle(BulkAddShopItemsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            // Deliberately NOT rate-limited: a bulk upload is thousands of one-item
            // packets by design, and the old 500 ms limit silently dropped all but the
            // first of each burst. Pacing is the client's job (ClientShopUploadQueue);
            // the server-side backstop is the shop-size cap below, and op permission.
            if (p.locationName == null || p.locationName.isBlank()) return;
            if (p.items == null || p.items.isEmpty()) return;

            Location loc = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            // Append through addShopItem so the configured maxShopItems cap applies here
            // too — the bulk path used to write the list directly and bypass it.
            int before = loc.getShopItems().size();
            for (ShopItem si : p.items) {
                if (si != null) loc.addShopItem(si);
            }
            boolean capped = loc.getShopItems().size() == before && !p.items.isEmpty();

            // Everything above is in-memory. Only the final packet of an upload pays for
            // persistence and network fan-out: doing it per packet re-serialized the
            // whole (growing) location thousands of times and produced a response that
            // itself outgrew the payload limit.
            if (!p.last) return;

            if (capped) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.tacz.bulk.cap_reached",
                    com.wavedefense.config.WaveDefenseConfig.MAX_SHOP_ITEMS.get()), false);
            }

            WaveDefenseMod.locationManager.saveToFile();
            WaveDefenseMod.waveManager.broadcastLocationData();

            // Sync shop to players currently inside the location
            CompoundTag locNbt = loc.save();
            SyncShopPacket syncPkt = new SyncShopPacket(p.locationName, locNbt);
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
}
