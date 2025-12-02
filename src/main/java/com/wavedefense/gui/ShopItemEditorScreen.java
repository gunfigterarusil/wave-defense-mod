package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ShopItemEditorScreen extends Screen {
    private final Location location;
    private final int itemIndex;
    private final Screen parent;
    private ItemStack selectedItem = ItemStack.EMPTY;
    private EditBox buyPriceInput;
    private EditBox sellPriceInput;

    public ShopItemEditorScreen(Location location, int itemIndex, Screen parent) {
        super(Component.literal(itemIndex >= 0 ? "Редагування товару" : "Новий товар"));
        this.location = location;
        this.itemIndex = itemIndex;
        this.parent = parent;

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            this.selectedItem = location.getShopItems().get(itemIndex).getItem();
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 80;

        this.addRenderableWidget(Button.builder(
                Component.literal("§eВозьміть предмет у руку та натисніть 'Взяти з руки'"),
                button -> {}
        ).bounds(centerX - 180, 40, 360, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("✋ Взяти предмет з руки"),
                button -> takeItemFromHand()
        ).bounds(centerX - 90, 65, 180, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Ціна купівлі (поінти):"),
                button -> {}
        ).bounds(centerX - 150, startY + 60, 140, 20).build()).active = false;

        buyPriceInput = new EditBox(this.font, centerX - 5, startY + 60, 100, 20,
                Component.literal("Ціна купівлі"));

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            buyPriceInput.setValue(String.valueOf(location.getShopItems().get(itemIndex).getBuyPrice()));
        } else {
            buyPriceInput.setValue("100");
        }
        buyPriceInput.setMaxLength(6);
        this.addRenderableWidget(buyPriceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Гравець платить цю ціну"),
                button -> {}
        ).bounds(centerX + 100, startY + 60, 160, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("Ціна продажу (поінти):"),
                button -> {}
        ).bounds(centerX - 150, startY + 90, 140, 20).build()).active = false;

        sellPriceInput = new EditBox(this.font, centerX - 5, startY + 90, 100, 20,
                Component.literal("Ціна продажу"));

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            sellPriceInput.setValue(String.valueOf(location.getShopItems().get(itemIndex).getSellPrice()));
        } else {
            sellPriceInput.setValue("50");
        }
        sellPriceInput.setMaxLength(6);
        this.addRenderableWidget(sellPriceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Гравець отримує цю ціну"),
                button -> {}
        ).bounds(centerX + 100, startY + 90, 160, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Якщо ціна продажу = 0, предмет не можна продати"),
                button -> {}
        ).bounds(centerX - 150, startY + 120, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти"),
                button -> save()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void takeItemFromHand() {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            selectedItem = minecraft.player.getMainHandItem().copy();
            this.rebuildWidgets();
        }
    }

    private void save() {
        if (selectedItem.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("§cСпочатку виберіть предмет!"),
                        true
                );
            }
            return;
        }

        try {
            int buyPrice = Integer.parseInt(buyPriceInput.getValue());
            int sellPrice = Integer.parseInt(sellPriceInput.getValue());

            if (buyPrice < 0 || sellPrice < 0) {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("§cЦіна не може бути негативною!"),
                            true
                    );
                }
                return;
            }

            ShopItem shopItem = new ShopItem(selectedItem, buyPrice, sellPrice);

            if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
                location.getShopItems().set(itemIndex, shopItem);
            } else {
                location.addShopItem(shopItem);
            }

            this.minecraft.setScreen(parent);
        } catch (NumberFormatException e) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("§cНевірний формат ціни!"),
                        true
                );
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 80;

        if (!selectedItem.isEmpty()) {
            graphics.fill(centerX - 50, startY - 10, centerX + 50, startY + 30, 0xFF1a1a1a);
            graphics.fill(centerX - 51, startY - 11, centerX + 51, startY - 10, 0xFF666666);
            graphics.fill(centerX - 51, startY + 30, centerX + 51, startY + 31, 0xFF666666);
            graphics.fill(centerX - 51, startY - 10, centerX - 50, startY + 30, 0xFF666666);
            graphics.fill(centerX + 50, startY - 10, centerX + 51, startY + 30, 0xFF666666);

            graphics.renderItem(selectedItem, centerX - 8, startY + 2);
            graphics.renderItemDecorations(this.font, selectedItem, centerX - 8, startY + 2);

            String name = selectedItem.getHoverName().getString();
            if (name.length() > 20) {
                name = name.substring(0, 17) + "...";
            }
            graphics.drawCenteredString(this.font, "§e" + name, centerX + 20, startY + 6, 0xFFFFFF);

            if (selectedItem.hasTag()) {
                graphics.drawString(this.font, "§a✓ З NBT", centerX - 45, startY + 35, 0xFFFFFF);
            }
        } else {
            graphics.drawCenteredString(this.font, "§7Предмет не обрано", centerX, startY + 10, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!selectedItem.isEmpty()) {
            if (mouseX >= centerX - 8 && mouseX <= centerX - 8 + 16 &&
                    mouseY >= startY + 2 && mouseY <= startY + 2 + 16) {
                graphics.renderTooltip(this.font, selectedItem, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
