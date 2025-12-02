package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PurchaseItemPacket {
    private final String locationName;
    private final int itemIndex;
    private final boolean isBuying; // true = купівля, false = продаж

    public PurchaseItemPacket(String locationName, int itemIndex, boolean isBuying) {
        this.locationName = locationName;
        this.itemIndex = itemIndex;
        this.isBuying = isBuying;
    }

    public static void encode(PurchaseItemPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.itemIndex);
        buf.writeBoolean(packet.isBuying);
    }

    public static PurchaseItemPacket decode(FriendlyByteBuf buf) {
        return new PurchaseItemPacket(buf.readUtf(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(PurchaseItemPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) return;

            if (packet.itemIndex < 0 || packet.itemIndex >= location.getShopItems().size()) {
                return;
            }

            ShopItem shopItem = location.getShopItems().get(packet.itemIndex);
            int playerPoints = location.getPlayerPoints(player.getUUID());

            if (packet.isBuying) {
                // КУПІВЛЯ
                if (playerPoints >= shopItem.getBuyPrice()) {
                    location.addPoints(player.getUUID(), -shopItem.getBuyPrice());
                    player.getInventory().add(shopItem.getItem().copy());

                    player.displayClientMessage(
                            Component.literal("§aКуплено: " + shopItem.getItem().getHoverName().getString()),
                            true
                    );

                    PacketHandler.sendToPlayer(
                            new UpdatePointsPacket(location.getPlayerPoints(player.getUUID()), location.getName()),
                            player
                    );
                } else {
                    player.displayClientMessage(
                            Component.literal("§cНедостатньо поінтів!"),
                            true
                    );
                }
            } else {
                // ПРОДАЖ
                if (!shopItem.canSell()) {
                    player.displayClientMessage(
                            Component.literal("§cЦей предмет не можна продати!"),
                            true
                    );
                    return;
                }

                // Шукаємо предмет в інвентарі з урахуванням NBT
                ItemStack templateItem = shopItem.getItem();
                ItemStack foundItem = null;
                int slotIndex = -1;

                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);

                    if (itemsMatch(stack, templateItem)) {
                        foundItem = stack;
                        slotIndex = i;
                        break;
                    }
                }

                if (foundItem != null && slotIndex >= 0) {
                    // Забираємо кількість з шаблону
                    int removeCount = Math.min(templateItem.getCount(), foundItem.getCount());
                    foundItem.shrink(removeCount);

                    if (foundItem.isEmpty()) {
                        player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                    }

                    // Додаємо поінти
                    location.addPoints(player.getUUID(), shopItem.getSellPrice());

                    player.displayClientMessage(
                            Component.literal("§aПродано: " + templateItem.getHoverName().getString() +
                                    " §7x" + removeCount),
                            true
                    );

                    PacketHandler.sendToPlayer(
                            new UpdatePointsPacket(location.getPlayerPoints(player.getUUID()), location.getName()),
                            player
                    );
                } else {
                    player.displayClientMessage(
                            Component.literal("§cУ вас немає цього предмета!"),
                            true
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean itemsMatch(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack1, stack2)) return false;

        if (stack1.hasTag() != stack2.hasTag()) return false;

        if (stack1.hasTag() && stack2.hasTag()) {
            return stack1.getTag().equals(stack2.getTag());
        }

        return true;
    }
}