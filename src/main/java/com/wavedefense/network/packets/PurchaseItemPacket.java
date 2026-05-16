package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Запит на купівлю товару в магазині.
 * shopPointIndex = -1 → глобальний магазин локації.
 * shopPointIndex >= 0 → товар у точці магазину з цим індексом.
 */
public class PurchaseItemPacket {
    private final String locationName;
    private final int    shopPointIndex; // -1 = global
    private final int    itemIndex;

    // Зворотна сумісність: старий конструктор (глобальний магазин)
    public PurchaseItemPacket(String locationName, int itemIndex) {
        this(locationName, -1, itemIndex);
    }

    public PurchaseItemPacket(String locationName, int shopPointIndex, int itemIndex) {
        this.locationName   = locationName;
        this.shopPointIndex = shopPointIndex;
        this.itemIndex      = itemIndex;
    }

    public static void encode(PurchaseItemPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName);
        buf.writeInt(p.shopPointIndex);
        buf.writeInt(p.itemIndex);
    }

    public static PurchaseItemPacket decode(FriendlyByteBuf buf) {
        return new PurchaseItemPacket(buf.readUtf(), buf.readInt(), buf.readInt());
    }

    public static void handle(PurchaseItemPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) return;

            // Отримуємо список товарів (глобальний або з точки).
            // Захоплюємо посилання на список і елемент одразу, щоб уникнути TOCTOU
            // якщо адмін редагує магазин одночасно з купівлею.
            List<ShopItem> sourceList;
            if (packet.shopPointIndex >= 0) {
                List<ShopPoint> points = location.getShopPoints();
                if (packet.shopPointIndex >= points.size()) return;
                ShopPoint sp = points.get(packet.shopPointIndex);
                if (sp == null) return;
                sourceList = sp.getItems();
            } else {
                sourceList = location.getShopItems();
            }
            if (sourceList == null) return;

            if (packet.itemIndex < 0 || packet.itemIndex >= sourceList.size()) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.item_not_available"), true);
                return;
            }
            ShopItem shopItem = sourceList.get(packet.itemIndex);
            if (shopItem == null) return;
            int price = shopItem.getBuyPrice();

            // Server-side перевірка тригеру доступності
            if (shopItem.hasAvailabilityTrigger()) {
                int curWave = WaveDefenseMod.locationManager.getCurrentWaveForLocation(packet.locationName);
                com.wavedefense.data.WaveTrigger at = shopItem.getAvailabilityTrigger();
                boolean available = false;
                switch (at) {
                    case SHOP_LOCATION_START: available = curWave >= 1; break;
                    case SHOP_WAVE_START:     available = curWave >= 1; break;
                    case SHOP_WAVE_N:         available = (curWave == shopItem.getAvailabilityWave()); break;
                    case SHOP_PLAYER_HAS_ITEM:
                        if (shopItem.getAvailabilityItemId() != null && !shopItem.getAvailabilityItemId().isEmpty()) {
                            net.minecraft.resources.ResourceLocation rl =
                                new net.minecraft.resources.ResourceLocation(shopItem.getAvailabilityItemId());
                            net.minecraft.world.item.Item reqItem =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                            available = reqItem != null && player.getInventory().hasAnyMatching(s -> s.getItem() == reqItem);
                        }
                        break;
                    default: available = true;
                }
                if (!available) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.item_not_available"), true);
                    return;
                }
            }

            if (location.getPlayerPoints(player.getUUID()) >= price) {
                location.addPoints(player.getUUID(), -price);
                // Зберігаємо зміну поінтів на диск, щоб не загубились при рестарті
                WaveDefenseMod.locationManager.updateLocation(location);
                for (ItemStack item : shopItem.getItems()) {
                    player.getInventory().add(item.copy());
                }
                // Сповіщення
                String itemName = shopItem.getItems().isEmpty() ? "товар"
                    : shopItem.getItems().get(0).getHoverName().getString();
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.purchased", itemName), true);
                // Синхронізуємо очки
                WaveDefenseMod.waveManager.syncPlayerData(player);
            } else {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.not_enough_points"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
