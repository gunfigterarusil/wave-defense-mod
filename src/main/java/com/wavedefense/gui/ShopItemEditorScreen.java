package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopItemEditorScreen extends Screen {
    private final Location location;
    private final int itemIndex;
    private final Screen parent;
    private List<ItemStack> items = new ArrayList<>();
    private EditBox buyPriceInput;
    private EditBox sellPriceInput;

    public ShopItemEditorScreen(Location location, int itemIndex, Screen parent) {
        super(Component.literal(itemIndex >= 0 ? "Редагування товару" : "Новий товар"));
        this.location = location;
        this.itemIndex = itemIndex;
        this.parent = parent;

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            this.items.addAll(location.getShopItems().get(itemIndex).getItems());
        }
        while (this.items.size() < 4) {
            this.items.add(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 80;

        for (int i = 0; i < 4; i++) {
            int xPos = centerX - 150 + (i * 80);
            final int slotIndex = i;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Встановити"),
                    button -> setItemFromHand(slotIndex)
            ).bounds(xPos - 5, startY + 25, 60, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("Очистити"),
                    button -> clearItem(slotIndex)
            ).bounds(xPos - 5, startY + 50, 60, 20).build());
        }

        buyPriceInput = new EditBox(this.font, centerX - 40, startY + 90, 80, 20, Component.literal("Ціна купівлі"));
        if (itemIndex >= 0) {
            buyPriceInput.setValue(String.valueOf(location.getShopItems().get(itemIndex).getBuyPrice()));
        }
        this.addRenderableWidget(buyPriceInput);

        sellPriceInput = new EditBox(this.font, centerX - 40, startY + 120, 80, 20, Component.literal("Ціна продажу"));
        if (itemIndex >= 0) {
            sellPriceInput.setValue(String.valueOf(location.getShopItems().get(itemIndex).getSellPrice()));
        }
        this.addRenderableWidget(sellPriceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти"),
                button -> save()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void setItemFromHand(int slotIndex) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            items.set(slotIndex, minecraft.player.getMainHandItem().copy());
            this.rebuildWidgets();
        }
    }

    private void clearItem(int slotIndex) {
        items.set(slotIndex, ItemStack.EMPTY);
        this.rebuildWidgets();
    }

    private void save() {
        List<ItemStack> finalItems = items.stream().filter(item -> !item.isEmpty()).collect(Collectors.toList());
        if (finalItems.isEmpty()) {
            // Error: at least one item must be set
            return;
        }

        try {
            int buyPrice = Integer.parseInt(buyPriceInput.getValue());
            int sellPrice = Integer.parseInt(sellPriceInput.getValue());

            ShopItem shopItem = new ShopItem(finalItems, buyPrice, sellPrice);
            if (itemIndex >= 0) {
                location.getShopItems().set(itemIndex, shopItem);
            } else {
                location.addShopItem(shopItem);
            }
            this.minecraft.setScreen(parent);
        } catch (NumberFormatException e) {
            // Handle error
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 80;

        for (int i = 0; i < 4; i++) {
            int xPos = centerX - 150 + (i * 80);
            ItemStack item = items.get(i);
            graphics.renderItem(item, xPos + 18, startY);
            graphics.renderItemDecorations(this.font, item, xPos + 18, startY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
