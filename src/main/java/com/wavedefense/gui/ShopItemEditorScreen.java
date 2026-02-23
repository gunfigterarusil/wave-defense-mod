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

    // Розміри слота
    private static final int SLOT_W = 70;
    private static final int SLOT_H = 16;
    private static final int SLOT_GAP = 6;

    public ShopItemEditorScreen(Location location, int itemIndex, Screen parent) {
        super(Component.literal(itemIndex >= 0 ? "Редагування товару" : "Новий товар"));
        this.location = location;
        this.itemIndex = itemIndex;
        this.parent = parent;

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            this.items.addAll(location.getShopItems().get(itemIndex).getItems());
        }
        while (this.items.size() < 4) this.items.add(ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int startY = 30;

        // Підпис
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети товару:"),
                button -> {}
        ).bounds(cx - 155, startY, 140, 14).build()).active = false;

        startY += 14;

        // 4 слоти предметів — кожен: підпис, іконка (render), Встановити, Очистити
        // Розміщення: 4 колонки по SLOT_W+SLOT_GAP
        int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
            final int si = i;

            // Підпис слота
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Слот " + (i + 1)), button -> {}
            ).bounds(xPos, startY, SLOT_W, 12).build()).active = false;

            // Іконка рендериться у render() на startY+14

            // Кнопка Встановити
            this.addRenderableWidget(Button.builder(
                    Component.literal("Встановити"),
                    button -> setItemFromHand(si)
            ).bounds(xPos, startY + 32, SLOT_W, SLOT_H).build());

            // Кнопка Очистити
            this.addRenderableWidget(Button.builder(
                    Component.literal("Очистити"),
                    button -> clearItem(si)
            ).bounds(xPos, startY + 32 + SLOT_H + 2, SLOT_W, SLOT_H).build());
        }

        startY += 32 + SLOT_H * 2 + 10;

        // Ціна купівлі
        int labelW = 155;
        int fieldW = 80;
        int fieldX = cx + 10;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ціна купівлі (поінти):"),
                button -> {}
        ).bounds(cx - 155, startY, labelW, 18).build()).active = false;

        buyPriceInput = new EditBox(this.font, fieldX, startY, fieldW, 20,
                Component.literal("Ціна купівлі"));
        buyPriceInput.setValue(itemIndex >= 0
                ? String.valueOf(location.getShopItems().get(itemIndex).getBuyPrice()) : "0");
        this.addRenderableWidget(buyPriceInput);

        startY += 26;

        // Ціна продажу — підпис на окремому рядку, поле під ним
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ціна продажу (поінти, 0 = без продажу):"),
                button -> {}
        ).bounds(cx - 155, startY, 280, 18).build()).active = false;

        startY += 20;

        sellPriceInput = new EditBox(this.font, cx - 155, startY, fieldW, 20,
                Component.literal("Ціна продажу"));
        sellPriceInput.setValue(itemIndex >= 0
                ? String.valueOf(location.getShopItems().get(itemIndex).getSellPrice()) : "0");
        this.addRenderableWidget(sellPriceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти"),
                button -> save()
        ).bounds(cx - 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 30, 100, 20).build());
    }

    private void setItemFromHand(int slotIndex) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            items.set(slotIndex, minecraft.player.getMainHandItem().copy());
            rebuildWidgets();
        }
    }

    private void clearItem(int slotIndex) {
        items.set(slotIndex, ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void save() {
        List<ItemStack> finalItems = items.stream()
                .filter(i -> !i.isEmpty()).collect(Collectors.toList());
        if (finalItems.isEmpty()) return;
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
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int cx = this.width / 2;
        int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft = cx - totalSlotsW / 2;
        int iconY = 58; // startY(30) + 14(підпис) + 14(відступ до іконки)

        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
            ItemStack item = items.get(i);

            // Рамка під іконку
            graphics.fill(xPos + (SLOT_W - 18) / 2 - 1, iconY - 1,
                    xPos + (SLOT_W - 18) / 2 + 19, iconY + 17, 0xFF555555);
            graphics.fill(xPos + (SLOT_W - 18) / 2, iconY,
                    xPos + (SLOT_W - 18) / 2 + 18, iconY + 16, 0xFF222222);

            // Іконка по центру слота
            int iconX = xPos + (SLOT_W - 16) / 2;
            graphics.renderItem(item, iconX, iconY);
            graphics.renderItemDecorations(this.font, item, iconX, iconY);

            // Tooltip при наведенні
            if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16
                    && mouseY >= iconY && mouseY <= iconY + 16) {
                graphics.renderTooltip(this.font, item, mouseX, mouseY);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
