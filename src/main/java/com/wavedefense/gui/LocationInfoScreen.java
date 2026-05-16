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
        super(Component.translatable("wavedefense.auto.інформація_про_локацію_987e4899"));
        this.location = location;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.назад_f6dab074"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = 30;

        graphics.drawCenteredString(this.font, Component.literal("\u00A76\u00A7l" + location.getName()), centerX, startY, 0xFFFFFF);

        startY += 30;
        graphics.drawString(this.font, Component.translatable("wavedefense.info.section.basic"), centerX - 100, startY, 0xFFFFFF);
        startY += 20;
        graphics.drawString(this.font, Component.translatable("wavedefense.info.wave_count", location.getWaves().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        graphics.drawString(this.font, Component.translatable("wavedefense.info.mob_spawn_count", location.getMobSpawns().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        graphics.drawString(this.font, Component.translatable("wavedefense.info.shop_item_count", location.getShopItems().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        Component inventoryMode = location.isKeepInventory()
                ? Component.translatable("wavedefense.info.inventory_kept")
                : Component.translatable("wavedefense.info.inventory_cleared");
        graphics.drawString(this.font, Component.translatable("wavedefense.info.inventory_mode", inventoryMode), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        if (!location.isKeepInventory()) {
            graphics.drawString(this.font, Component.translatable("wavedefense.info.starting_item_count", location.getStartingItems().size()), centerX - 100, startY, 0xFFFFFF);
            startY += 15;
        }
        if (!location.getWaves().isEmpty()) {
            startY += 15;
            graphics.drawString(this.font, Component.translatable("wavedefense.info.section.waves"), centerX - 100, startY, 0xFFFFFF);
            startY += 20;
            int totalMobs = 0;
            for (var wave : location.getWaves()) totalMobs += wave.getMobs().size();
            graphics.drawString(this.font, Component.translatable("wavedefense.info.total_mob_types", totalMobs), centerX - 100, startY, 0xFFFFFF);
            startY += 15;
            int avgTime = 0;
            for (var wave : location.getWaves()) avgTime += wave.getTimeBetweenWaves();
            avgTime /= location.getWaves().size();
            graphics.drawString(this.font, Component.translatable("wavedefense.info.avg_wave_time", avgTime / 60, avgTime % 60), centerX - 100, startY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
