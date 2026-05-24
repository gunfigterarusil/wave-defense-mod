package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Запит на продаж предмету в магазині.
 * shopPointIndex = -1 → глобальний магазин локації.
 * shopPointIndex >= 0 → товар у точці магазину з цим індексом.
 */
public class SellItemPacket {
    private final String locationName;
    private final int    shopPointIndex; // -1 = global
    private final int    itemIndex;

    // Зворотна сумісність: старий конструктор (глобальний магазин)
    public SellItemPacket(String locationName, int itemIndex) {
        this(locationName, -1, itemIndex);
    }

    public SellItemPacket(String locationName, int shopPointIndex, int itemIndex) {
        this.locationName   = locationName;
        this.shopPointIndex = shopPointIndex;
        this.itemIndex      = itemIndex;
    }

    public static void encode(SellItemPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.shopPointIndex);
        buf.writeInt(packet.itemIndex);
    }

    public static SellItemPacket decode(FriendlyByteBuf buf) {
        return new SellItemPacket(buf.readUtf(), buf.readInt(), buf.readInt());
    }

    public static void handle(SellItemPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) return;

            // Resolve source list: global shop or specific shop point
            java.util.List<ShopItem> sourceList;
            if (packet.shopPointIndex >= 0) {
                java.util.List<com.wavedefense.data.ShopPoint> points = location.getShopPoints();
                if (packet.shopPointIndex >= points.size()) return;
                com.wavedefense.data.ShopPoint sp = points.get(packet.shopPointIndex);
                if (sp == null) return;
                sourceList = sp.getItems();
            } else {
                sourceList = location.getShopItems();
            }
            if (sourceList == null) return;
            if (packet.itemIndex < 0 || packet.itemIndex >= sourceList.size()) return;

            ShopItem shopItem = sourceList.get(packet.itemIndex);
            if (!shopItem.canSell()) return;

            // Підраховуємо скільки кожного типу предмета потрібно
            Map<Item, Integer> requiredCounts = new HashMap<>();
            for (ItemStack required : shopItem.getItems()) {
                requiredCounts.merge(required.getItem(), required.getCount(), Integer::sum);
            }

            // Перевіряємо наявність у гравця.
            // Якщо requireNbtMatch=true — рахуємо лише слоти що проходять matchesNbtForSale()
            // (важливо для модів з NBT-важкими предметами: TACZ, кастомні зброя/броня тощо).
            // Якщо requireNbtMatch=false (типово) — поведінка без змін: будь-який предмет того типу.
            for (Map.Entry<Item, Integer> entry : requiredCounts.entrySet()) {
                int inInventory = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (!slot.isEmpty() && slot.getItem() == entry.getKey()
                            && shopItem.matchesNbtForSale(slot)) {
                        inInventory += slot.getCount();
                    }
                }
                if (inInventory < entry.getValue()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.not_enough_items"), false);
                    return;
                }
            }

            // Забираємо предмети з урахуванням NBT-перевірки
            for (Map.Entry<Item, Integer> entry : requiredCounts.entrySet()) {
                int toRemove = entry.getValue();
                Item targetItem = entry.getKey();
                for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (!slot.isEmpty() && slot.getItem() == targetItem
                            && shopItem.matchesNbtForSale(slot)) {
                        int removeFromSlot = Math.min(slot.getCount(), toRemove);
                        slot.shrink(removeFromSlot);
                        toRemove -= removeFromSlot;
                        if (slot.isEmpty()) {
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                }
            }

            // Нараховуємо поінти
            location.addPoints(player.getUUID(), shopItem.getSellPrice());
            // Зберігаємо зміну поінтів на диск, щоб не загубились при рестарті
            WaveDefenseMod.locationManager.updateLocation(location);

            // Синхронізуємо гравця
            WaveDefenseMod.waveManager.syncPlayerData(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
