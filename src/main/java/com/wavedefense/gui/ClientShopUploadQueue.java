package com.wavedefense.gui;

import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.BulkAddShopItemsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Uploads shop items to the server one packet at a time, paced across client ticks.
 *
 * <p>Bulk-adding a large TACZ pack used to build ~25 items per packet and fire every
 * packet in a single frame. That broke in three separate ways at a few thousand guns:
 *
 * <ol>
 *   <li>A gun's NBT is heavy, so 25 of them overflowed the 32767-byte serverbound
 *       payload limit and the connection was dropped outright.</li>
 *   <li>The server rate-limited the packet to one per 500 ms, so of the ~120 packets
 *       sent in that single frame all but one were <b>silently discarded</b> — items
 *       were being lost long before the size error appeared.</li>
 *   <li>Every packet triggered a full save, a location broadcast and a shop re-sync
 *       carrying the entire location NBT, which grows with each batch — quadratic work
 *       and an ever-larger response packet.</li>
 * </ol>
 *
 * <p>One item per packet keeps every payload tiny regardless of how much NBT a gun
 * carries. Pacing keeps the channel from flooding, and the final packet is flagged so
 * the server saves and broadcasts exactly once for the whole upload.
 */
public final class ClientShopUploadQueue {

    private ClientShopUploadQueue() {}

    /**
     * Items sent per client tick. At 20 TPS this is ~160 items/second — fast enough that
     * a 3000-gun pack finishes in under half a minute, slow enough that the connection
     * is never swamped by a burst.
     */
    private static final int ITEMS_PER_TICK = 8;

    /** How often to tell the player how far along the upload is. */
    private static final int PROGRESS_EVERY = 250;

    private static final Deque<ShopItem> pending = new ArrayDeque<>();
    private static String targetLocation;
    private static int total;
    private static int sent;

    /**
     * Queues items for upload, replacing any upload already in progress.
     *
     * @param locationName the location whose global shop receives the items
     * @param items        items to append; a copy is taken, the caller may reuse the list
     */
    public static void enqueue(String locationName, List<ShopItem> items) {
        if (locationName == null || items == null || items.isEmpty()) return;
        pending.clear();
        for (ShopItem si : items) {
            if (si != null) pending.addLast(si);
        }
        targetLocation = locationName;
        total = pending.size();
        sent = 0;
    }

    /** True while items are still waiting to be sent. */
    public static boolean isUploading() {
        return !pending.isEmpty();
    }

    /** Items still queued — drives the progress readout. */
    public static int remaining() {
        return pending.size();
    }

    /** Total queued for the current upload. */
    public static int total() {
        return total;
    }

    /**
     * Sends the next slice. Called once per client tick.
     *
     * <p>Stops immediately if the player left the world mid-upload, so a disconnect does
     * not leave the queue spinning against a dead connection.
     */
    public static void tick() {
        if (pending.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            cancel();
            return;
        }

        for (int i = 0; i < ITEMS_PER_TICK && !pending.isEmpty(); i++) {
            ShopItem item = pending.pollFirst();
            boolean last = pending.isEmpty();
            PacketHandler.sendToServer(new BulkAddShopItemsPacket(
                targetLocation, Collections.singletonList(item), last));
            sent++;

            if (last) {
                mc.player.displayClientMessage(Component.translatable(
                    "wavedefense.tacz.bulk.upload_done", sent), false);
                // Pull the shop back so the client reflects what the server actually
                // stored. The items were added optimistically for instant feedback, but
                // the server may have stopped at maxShopItems — without this the editor
                // would keep showing entries that were never saved.
                PacketHandler.sendToServer(
                    new com.wavedefense.network.packets.RequestShopDataPacket(targetLocation));
            } else if (sent % PROGRESS_EVERY == 0) {
                mc.player.displayClientMessage(Component.translatable(
                    "wavedefense.tacz.bulk.upload_progress", sent, total), true);
            }
        }
    }

    /** Drops anything still queued — used when the player disconnects mid-upload. */
    public static void cancel() {
        pending.clear();
        targetLocation = null;
        total = 0;
        sent = 0;
    }
}
