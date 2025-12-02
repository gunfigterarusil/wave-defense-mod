package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PurchaseItemPacket {
    private final String locationName;
    private final int itemIndex;

    public PurchaseItemPacket(String locationName, int itemIndex) {
        this.locationName = locationName;
        this.itemIndex = itemIndex;
    }

    public static void encode(PurchaseItemPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.itemIndex);
    }

    public static PurchaseItemPacket decode(FriendlyByteBuf buf) {
        return new PurchaseItemPacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(PurchaseItemPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) return;

            ShopItem shopItem = location.getShopItems().get(packet.itemIndex);
            int price = shopItem.getBuyPrice();

            if (location.getPlayerPoints(player.getUUID()) >= price) {
                location.addPoints(player.getUUID(), -price);
                for (ItemStack item : shopItem.getItems()) {
                    player.getInventory().add(item.copy());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
