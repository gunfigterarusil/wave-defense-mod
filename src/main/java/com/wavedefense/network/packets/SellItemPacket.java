package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

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

            ShopItem shopItem = location.getShopItems().get(packet.itemIndex);
            if (!shopItem.canSell()) return;

            for (ItemStack item : shopItem.getItems()) {
                if (!player.getInventory().clearOrCountMatchingItems(p -> ItemStack.isSameItemSameTags(p, item), item.getCount(), player.inventoryMenu.getCraftSlots())) {
                    return;
                }
            }

            location.addPoints(player.getUUID(), shopItem.getSellPrice());
        });
        ctx.get().setPacketHandled(true);
    }
}
