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

public class SellItemPacket {
    private final String locationName;
    private final int itemIndex;

    public SellItemPacket(String locationName, int itemIndex) {
        this.locationName = locationName;
        this.itemIndex = itemIndex;
    }

    public static void encode(SellItemPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.itemIndex);
    }

    public static SellItemPacket decode(FriendlyByteBuf buf) {
        return new SellItemPacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(SellItemPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) return;
            if (packet.itemIndex < 0 || packet.itemIndex >= location.getShopItems().size()) return;

            ShopItem shopItem = location.getShopItems().get(packet.itemIndex);
            if (!shopItem.canSell()) return;

            // Підраховуємо скільки кожного ТИПУ предмета потрібно
            // Порівнюємо тільки за Item (без NBT) — щоб арбуз з будь-якими тегами приймався
            Map<Item, Integer> requiredCounts = new HashMap<>();
            for (ItemStack required : shopItem.getItems()) {
                requiredCounts.merge(required.getItem(), required.getCount(), Integer::sum);
            }

            // Перевіряємо наявність у гравця (лише за типом предмету, без NBT)
            for (Map.Entry<Item, Integer> entry : requiredCounts.entrySet()) {
                int inInventory = player.getInventory().countItem(entry.getKey());
                if (inInventory < entry.getValue()) {
                    // Не вистачає предметів — відмовляємо
                    return;
                }
            }

            // Забираємо предмети (лише за типом, без перевірки NBT)
            for (Map.Entry<Item, Integer> entry : requiredCounts.entrySet()) {
                int toRemove = entry.getValue();
                Item targetItem = entry.getKey();
                // Проходимо інвентар і видаляємо потрібну кількість
                for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (!slot.isEmpty() && slot.getItem() == targetItem) {
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

            // Синхронізуємо гравця
            WaveDefenseMod.waveManager.syncPlayerData(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
