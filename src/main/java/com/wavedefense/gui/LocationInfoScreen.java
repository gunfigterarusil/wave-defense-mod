package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class LocationInfoScreen extends Screen {
    private final Location location;
    private final Screen parentScreen;

    public LocationInfoScreen(Location location, Screen parentScreen) {
        super(new TranslationTextComponent("wavedefense.auto.інформація_про_локацію_987e4899"));
        this.location = location;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        this.addButton(new Button(this.width / 2 - 50, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.auto.назад_f6dab074"), button -> this.minecraft.setScreen(parentScreen)));
    }

    @Override
    public void render(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);

        int centerX = this.width / 2;
        int startY = 30;

        com.wavedefense.gui.GuiCompat.drawCenteredString(graphics, this.font, new StringTextComponent("\u00A76\u00A7l" + location.getName()), centerX, startY, 0xFFFFFF);

        startY += 30;
        com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.section.basic"), centerX - 100, startY, 0xFFFFFF);
        startY += 20;
        com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.wave_count", location.getWaves().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.mob_spawn_count", location.getMobSpawns().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.shop_item_count", location.getShopItems().size()), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        ITextComponent inventoryMode = location.isKeepInventory()
                ? new TranslationTextComponent("wavedefense.info.inventory_kept")
                : new TranslationTextComponent("wavedefense.info.inventory_cleared");
        com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.inventory_mode", inventoryMode), centerX - 100, startY, 0xFFFFFF);
        startY += 15;
        if (!location.isKeepInventory()) {
            com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.starting_item_count", location.getStartingItems().size()), centerX - 100, startY, 0xFFFFFF);
            startY += 15;
        }
        if (!location.getWaves().isEmpty()) {
            startY += 15;
            com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.section.waves"), centerX - 100, startY, 0xFFFFFF);
            startY += 20;
            int totalMobs = 0;
            for (com.wavedefense.data.WaveConfig wave : location.getWaves()) totalMobs += wave.getMobs().size();
            com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.total_mob_types", totalMobs), centerX - 100, startY, 0xFFFFFF);
            startY += 15;
            int avgTime = 0;
            for (com.wavedefense.data.WaveConfig wave : location.getWaves()) avgTime += wave.getTimeBetweenWaves();
            avgTime /= location.getWaves().size();
            com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, new TranslationTextComponent("wavedefense.info.avg_wave_time", avgTime / 60, avgTime % 60), centerX - 100, startY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
