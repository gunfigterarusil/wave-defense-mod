package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class StartingItemsScreen extends Screen {
    private final Screen parentScreen;
    private final Location location;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;

    public StartingItemsScreen(Screen parentScreen, Location location) {
        super(Component.literal("Стартове спорядження"));
        this.parentScreen = parentScreen;
        this.location = location;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Тримайте предмет у руці та натисніть 'Додати'"),
                button -> {}
        ).bounds(centerX - 150, 35, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати предмет"),
                button -> addItem()
        ).bounds(centerX - 100, startY, 200, 20).build());

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getStartingItems().size()); i++) {
            int index = i + scrollOffset;
            if (index >= location.getStartingItems().size()) break;

            ItemStack item = location.getStartingItems().get(index);
            int yPos = startY + 30 + (i * 30);

            String itemName = item.getHoverName().getString();
            if (itemName.length() > 30) {
                itemName = itemName.substring(0, 27) + "...";
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + itemName + " §7x" + item.getCount()),
                    button -> {}
            ).bounds(centerX - 120, yPos, 200, 20).build()).active = false;

            final int finalIndex = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeItem(finalIndex)
            ).bounds(centerX + 85, yPos, 20, 20).build());
        }

        if (location.getStartingItems().size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 115, startY + 30, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 115, startY + 210, 25, 25).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Готово"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void addItem() {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            location.addStartingItem(minecraft.player.getMainHandItem().copy());
            this.rebuildWidgets();
        }
    }

    private void removeItem(int index) {
        if (index >= 0 && index < location.getStartingItems().size()) {
            location.getStartingItems().remove(index);
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
        if (scrollOffset + ITEMS_PER_PAGE < location.getStartingItems().size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 60;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getStartingItems().size()); i++) {
            int index = i + scrollOffset;
            if (index >= location.getStartingItems().size()) break;

            ItemStack item = location.getStartingItems().get(index);
            int yPos = startY + 30 + (i * 30);

            graphics.renderItem(item, centerX - 145, yPos);
            graphics.renderItemDecorations(this.font, item, centerX - 145, yPos);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getStartingItems().size()); i++) {
            int index = i + scrollOffset;
            if (index >= location.getStartingItems().size()) break;

            ItemStack item = location.getStartingItems().get(index);
            int yPos = startY + 30 + (i * 30);

            if (mouseX >= centerX - 145 && mouseX <= centerX - 145 + 16 &&
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
