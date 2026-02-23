package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LocationInfoScreen extends Screen {
    private final Location location;
    private final Screen parentScreen;

    public LocationInfoScreen(Location location, Screen parentScreen) {
        super(Component.literal("Інформація про локацію"));
        this.location = location;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = 30;

        graphics.drawCenteredString(this.font, "§6§l" + location.getName(), centerX, startY, 0xFFFFFF);

        startY += 30;

        // Основна інформація
        graphics.drawString(this.font, "§e=== Основна інформація ===", centerX - 100, startY, 0xFFFFFF);
        startY += 20;

        graphics.drawString(this.font, "Кількість хвиль: §a" + location.getWaves().size(),
                centerX - 100, startY, 0xFFFFFF);
        startY += 15;

        graphics.drawString(this.font, "Точок спавну мобів: §a" + location.getMobSpawns().size() + "/10",
                centerX - 100, startY, 0xFFFFFF);
        startY += 15;

        graphics.drawString(this.font, "Товарів у магазині: §a" + location.getShopItems().size(),
                centerX - 100, startY, 0xFFFFFF);
        startY += 15;

        String inventoryMode = location.isKeepInventory() ? "§aЗберігається" : "§cОчищується";
        graphics.drawString(this.font, "Інвентар: " + inventoryMode,
                centerX - 100, startY, 0xFFFFFF);
        startY += 15;

        if (!location.isKeepInventory()) {
            graphics.drawString(this.font, "Стартових предметів: §a" + location.getStartingItems().size(),
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;
        }

        // Інформація про хвилі
        if (!location.getWaves().isEmpty()) {
            startY += 15;
            graphics.drawString(this.font, "§e=== Хвилі ===", centerX - 100, startY, 0xFFFFFF);
            startY += 20;

            int totalMobs = 0;
            for (var wave : location.getWaves()) {
                totalMobs += wave.getMobs().size();
            }

            graphics.drawString(this.font, "Загальна кількість типів мобів: §a" + totalMobs,
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;

            int avgTime = 0;
            for (var wave : location.getWaves()) {
                avgTime += wave.getTimeBetweenWaves();
            }
            if (!location.getWaves().isEmpty()) {
                avgTime /= location.getWaves().size();
            }

            graphics.drawString(this.font,
                    String.format("Середній час між хвилями: §a%d:%02d", avgTime / 60, avgTime % 60),
                    centerX - 100, startY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}