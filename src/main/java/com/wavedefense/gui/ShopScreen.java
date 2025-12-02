package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.PurchaseItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ShopScreen extends Screen {

    private final Location location;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 6;
    private int playerPoints;

    public ShopScreen(Location location) {
        super(Component.literal("Магазин - " + location.getName()));
        this.location = location;
        this.playerPoints = location.getPlayerPoints(Minecraft.getInstance().player.getUUID());
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        List<ShopItem> shopItems = location.getShopItems();

        // Відображення товарів
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            int yPos = startY + (i * 50);
            final int finalIndex = index;

            // Кнопка купівлі
            Button buyButton = Button.builder(
                    Component.literal("Купити за " + shopItem.getPointsCost() + " поінтів"),
                    button -> purchaseItem(finalIndex)
            ).bounds(centerX - 180, yPos + 25, 160, 20).build();

            // Деактивуємо кнопку якщо недостатньо поінтів
            if (playerPoints < shopItem.getPointsCost()) {
                buyButton.active = false;
            }

            this.addRenderableWidget(buyButton);
        }

        // Кнопки прокрутки
        if (shopItems.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(this.width - 30, startY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(this.width - 30, this.height - 60, 20, 20).build());
        }

        // Кнопка закриття
        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void purchaseItem(int index) {
        ShopItem shopItem = location.getShopItems().get(index);

        if (playerPoints >= shopItem.getPointsCost()) {
            // Відправка пакету на сервер
            PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), index));

            // Оновлення локальних поінтів (буде синхронізовано з сервером)
            playerPoints -= shopItem.getPointsCost();

            this.rebuildWidgets();
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        List<ShopItem> shopItems = location.getShopItems();
        if (scrollOffset + ITEMS_PER_PAGE < shopItems.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;

        // Заголовок
        graphics.drawCenteredString(this.font, this.title, centerX, 10, 0xFFFFFF);

        // Відображення поінтів
        graphics.drawCenteredString(this.font,
                "Ваші поінти: §e" + playerPoints,
                centerX, 30, 0xFFFFFF);

        // Відображення товарів
        List<ShopItem> shopItems = location.getShopItems();
        int startY = 60;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            ItemStack item = shopItem.getItem();
            int yPos = startY + (i * 50);

            // Рендеринг іконки предмета
            graphics.renderItem(item, centerX - 200, yPos);

            // Назва предмета
            String itemName = item.getHoverName().getString();
            graphics.drawString(this.font, itemName, centerX - 175, yPos + 5, 0xFFFFFF);

            // Кількість
            if (item.getCount() > 1) {
                graphics.drawString(this.font, "x" + item.getCount(), centerX - 175, yPos + 17, 0xAAAAAA);
            }

            // Ціна
            int cost = shopItem.getPointsCost();
            int color = playerPoints >= cost ? 0x55FF55 : 0xFF5555;
            graphics.drawString(this.font, "Ціна: " + cost, centerX + 20, yPos + 30, color);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Підказки при наведенні
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            ItemStack item = shopItem.getItem();
            int yPos = startY + (i * 50);

            // Перевірка наведення на іконку
            if (mouseX >= centerX - 200 && mouseX <= centerX - 200 + 16 &&
                    mouseY >= yPos && mouseY <= yPos + 16) {
                graphics.renderTooltip(this.font, item, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}