package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ShopEditorScreen extends Screen {
    private final Location location;
    private final Screen parent;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 6;

    public ShopEditorScreen(Location location, Screen parent) {
        super(Component.literal("Магазин - " + location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати товар"),
                button -> addShopItem()
        ).bounds(centerX - 100, 25, 200, 20).build());

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getShopItems().size()); i++) {
            int itemIndex = i + scrollOffset;
            if (itemIndex >= location.getShopItems().size()) break;

            ShopItem shopItem = location.getShopItems().get(itemIndex);
            List<ItemStack> items = shopItem.getItems();
            int yPos = startY + (i * 60);

            String firstItemName = items.isEmpty() ? "Пусто" : items.get(0).getHoverName().getString();
            if (firstItemName.length() > 25) {
                firstItemName = firstItemName.substring(0, 22) + "...";
            }
            if (items.size() > 1) {
                firstItemName += " (+" + (items.size() - 1) + ")";
            }


            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + firstItemName),
                    button -> {}
            ).bounds(centerX - 140, yPos + 5, 150, 20).build()).active = false;

            final int finalIndex = itemIndex;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎ Редагувати"),
                    button -> editShopItem(finalIndex)
            ).bounds(centerX + 15, yPos, 80, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕ Видалити"),
                    button -> deleteShopItem(finalIndex)
            ).bounds(centerX + 100, yPos, 80, 20).build());

            String priceInfo = String.format("§6Купівля: %d | §aПродаж: %d",
                    shopItem.getBuyPrice(), shopItem.getSellPrice());

            this.addRenderableWidget(Button.builder(
                    Component.literal(priceInfo),
                    button -> {}
            ).bounds(centerX - 140, yPos + 25, 320, 18).build()).active = false;
        }

        if (location.getShopItems().size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(this.width - 30, startY, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(this.width - 30, this.height - 80, 25, 25).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти і повернутися"),
                button -> saveChanges()
        ).bounds(centerX - 110, this.height - 30, 220, 20).build());
    }

    private void addShopItem() {
        this.minecraft.setScreen(new ShopItemEditorScreen(location, -1, this));
    }

    private void editShopItem(int index) {
        this.minecraft.setScreen(new ShopItemEditorScreen(location, index, this));
    }

    private void deleteShopItem(int index) {
        location.removeShopItem(index);
        if (scrollOffset > 0 && scrollOffset >= location.getShopItems().size()) {
            scrollOffset = Math.max(0, location.getShopItems().size() - ITEMS_PER_PAGE);
        }
        this.rebuildWidgets();
    }

    private void saveChanges() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));

        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Магазин збережено!"),
                    true
            );
        }

        this.minecraft.setScreen(parent);
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < location.getShopItems().size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 50;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getShopItems().size()); i++) {
            int itemIndex = i + scrollOffset;
            if (itemIndex >= location.getShopItems().size()) break;

            ShopItem shopItem = location.getShopItems().get(itemIndex);
            List<ItemStack> items = shopItem.getItems();
            int yPos = startY + (i * 60);

            for (int j = 0; j < items.size(); j++) {
                ItemStack item = items.get(j);
                int xPos = centerX - 165 + (j * 18);
                graphics.renderItem(item, xPos, yPos + 3);
                graphics.renderItemDecorations(this.font, item, xPos, yPos + 3);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getShopItems().size()); i++) {
            int itemIndex = i + scrollOffset;
            if (itemIndex >= location.getShopItems().size()) break;

            ShopItem shopItem = location.getShopItems().get(itemIndex);
            List<ItemStack> items = shopItem.getItems();
            int yPos = startY + (i * 60);

            for (int j = 0; j < items.size(); j++) {
                ItemStack item = items.get(j);
                int xPos = centerX - 165 + (j * 18);
                if (mouseX >= xPos && mouseX <= xPos + 16 &&
                        mouseY >= yPos + 3 && mouseY <= yPos + 3 + 16) {
                    graphics.renderTooltip(this.font, item, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
