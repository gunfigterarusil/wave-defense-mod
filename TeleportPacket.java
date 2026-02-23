package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.PurchaseItemPacket;
import com.wavedefense.network.packets.SellItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerShopScreen extends Screen {
    private final Location location;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 6;
    private int playerPoints;

    public PlayerShopScreen(Location location) {
        super(Component.literal("Магазин"));
        this.location = location;
        updatePlayerPoints();
    }

    private void updatePlayerPoints() {
        if (Minecraft.getInstance().player != null) {
            this.playerPoints = location.getPlayerPoints(Minecraft.getInstance().player.getUUID());
        }
    }

    @Override
    protected void init() {
        super.init();
        updatePlayerPoints();

        int centerX = this.width / 2;
        int startY = 80;

        List<ShopItem> shopItems = location.getShopItems();

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            int yPos = startY + (i * 40);

            final int finalIndex = index;
            Button buyButton = Button.builder(
                    Component.literal("Купити (" + shopItem.getBuyPrice() + ")"),
                    button -> buyItem(finalIndex)
            ).bounds(centerX - 100, yPos, 80, 20).build();
            buyButton.active = playerPoints >= shopItem.getBuyPrice();
            this.addRenderableWidget(buyButton);

            if (shopItem.canSell()) {
                Button sellButton = Button.builder(
                        Component.literal("Продати (" + shopItem.getSellPrice() + ")"),
                        button -> sellItem(finalIndex)
                ).bounds(centerX - 10, yPos, 80, 20).build();
                sellButton.active = canPlayerSell(shopItem);
                this.addRenderableWidget(sellButton);
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void buyItem(int index) {
        PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), index));
    }

    private void sellItem(int index) {
        PacketHandler.sendToServer(new SellItemPacket(location.getName(), index));
    }

    private boolean canPlayerSell(ShopItem shopItem) {
        if (minecraft.player == null) return false;
        Map<ItemStack, Integer> requiredItems = new HashMap<>();
        for (ItemStack item : shopItem.getItems()) {
            requiredItems.merge(item, 1, Integer::sum);
        }

        for (Map.Entry<ItemStack, Integer> entry : requiredItems.entrySet()) {
            if (minecraft.player.getInventory().countItem(entry.getKey().getItem()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§6Ваші очки: §e" + playerPoints, this.width / 2, 30, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 80;
        List<ShopItem> shopItems = location.getShopItems();

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            int yPos = startY + (i * 40);

            for (int j = 0; j < shopItem.getItems().size(); j++) {
                ItemStack item = shopItem.getItems().get(j);
                graphics.renderItem(item, centerX + 80 + (j * 18), yPos);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
