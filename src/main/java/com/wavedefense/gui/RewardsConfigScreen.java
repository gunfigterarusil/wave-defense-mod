package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RewardsConfigScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;

    public RewardsConfigScreen(Screen parentScreen, WaveConfig waveConfig) {
        super(Component.literal("Налаштування нагород"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        // Інструкція
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Тримайте предмет у руці та натисніть 'Додати'"),
                button -> {}
        ).bounds(centerX - 150, 35, 300, 20).build()).active = false;

        // Кнопка додавання
        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати нагороду"),
                button -> addReward()
        ).bounds(centerX - 100, startY, 200, 20).build());

        // Список нагород
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, waveConfig.getRewards().size()); i++) {
            int index = i + scrollOffset;
            if (index >= waveConfig.getRewards().size()) break;

            ItemStack reward = waveConfig.getRewards().get(index);
            int yPos = startY + 30 + (i * 30);

            String itemName = reward.getHoverName().getString();
            if (itemName.length() > 30) {
                itemName = itemName.substring(0, 27) + "...";
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + itemName + " §7x" + reward.getCount()),
                    button -> {}
            ).bounds(centerX - 120, yPos, 200, 20).build()).active = false;

            final int finalIndex = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeReward(finalIndex)
            ).bounds(centerX + 85, yPos, 20, 20).build());
        }

        // Кнопки прокрутки
        if (waveConfig.getRewards().size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 115, startY + 30, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 115, startY + 210, 25, 25).build());
        }

        // Кнопка назад
        this.addRenderableWidget(Button.builder(
                Component.literal("Готово"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void addReward() {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            waveConfig.addReward(minecraft.player.getMainHandItem().copy());
            this.rebuildWidgets();
        }
    }

    private void removeReward(int index) {
        if (index >= 0 && index < waveConfig.getRewards().size()) {
            waveConfig.removeReward(index);

            // Коригуємо scrollOffset
            if (scrollOffset > 0 && scrollOffset >= waveConfig.getRewards().size()) {
                scrollOffset = Math.max(0, waveConfig.getRewards().size() - ITEMS_PER_PAGE);
            }

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
        if (scrollOffset + ITEMS_PER_PAGE < waveConfig.getRewards().size()) {
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

        // Відображення іконок нагород
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, waveConfig.getRewards().size()); i++) {
            int index = i + scrollOffset;
            if (index >= waveConfig.getRewards().size()) break;

            ItemStack reward = waveConfig.getRewards().get(index);
            int yPos = startY + 30 + (i * 30);

            graphics.renderItem(reward, centerX - 145, yPos);
            graphics.renderItemDecorations(this.font, reward, centerX - 145, yPos);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Підказки
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, waveConfig.getRewards().size()); i++) {
            int index = i + scrollOffset;
            if (index >= waveConfig.getRewards().size()) break;

            ItemStack reward = waveConfig.getRewards().get(index);
            int yPos = startY + 30 + (i * 30);

            if (mouseX >= centerX - 145 && mouseX <= centerX - 145 + 16 &&
                    mouseY >= yPos && mouseY <= yPos + 16) {
                graphics.renderTooltip(this.font, reward, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}