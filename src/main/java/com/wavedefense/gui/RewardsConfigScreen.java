package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RewardsConfigScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private EditBox pointsRewardInput;

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

        pointsRewardInput = new EditBox(this.font, centerX - 50, startY, 100, 20, Component.literal("Очки за хвилю"));
        pointsRewardInput.setValue(String.valueOf(waveConfig.getPointsReward()));
        this.addRenderableWidget(pointsRewardInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Готово"),
                button -> {
                    save();
                    this.minecraft.setScreen(parentScreen);
                }
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void save() {
        try {
            int points = Integer.parseInt(pointsRewardInput.getValue());
            waveConfig.setPointsReward(points);
        } catch (NumberFormatException e) {
            // Error handling
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
