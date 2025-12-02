package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

public class WaveMobEditScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final ResourceLocation mobType;
    private final int mobIndex;

    private EditBox countInput;
    private EditBox growthPerWaveInput;
    private EditBox spawnChanceInput;
    private EditBox pointsPerKillInput;

    public WaveMobEditScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Редагування моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;
        this.mobType = waveConfig.getMobs().get(mobIndex).getMobType();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = 60;

        WaveMob existingMob = (mobIndex != -1) ? waveConfig.getMobs().get(mobIndex) : null;

        countInput = new EditBox(this.font, centerX - 25, startY, 60, 20, Component.literal("Кількість"));
        countInput.setValue(existingMob != null ? String.valueOf(existingMob.getCount()) : "1");
        this.addRenderableWidget(countInput);

        growthPerWaveInput = new EditBox(this.font, centerX - 25, startY + 30, 60, 20, Component.literal("Приріст"));
        growthPerWaveInput.setValue(existingMob != null ? String.valueOf(existingMob.getGrowthPerWave()) : "0");
        this.addRenderableWidget(growthPerWaveInput);

        spawnChanceInput = new EditBox(this.font, centerX - 25, startY + 60, 60, 20, Component.literal("Шанс"));
        spawnChanceInput.setValue(existingMob != null ? String.valueOf(existingMob.getSpawnChance()) : "100");
        this.addRenderableWidget(spawnChanceInput);

        pointsPerKillInput = new EditBox(this.font, centerX - 25, startY + 90, 60, 20, Component.literal("Очки"));
        pointsPerKillInput.setValue(existingMob != null ? String.valueOf(existingMob.getPointsPerKill()) : "10");
        this.addRenderableWidget(pointsPerKillInput);

        this.addRenderableWidget(Button.builder(Component.literal("Зберегти"), button -> save()).bounds(centerX - 100, this.height - 40, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Скасувати"), button -> this.minecraft.setScreen(parentScreen)).bounds(centerX + 20, this.height - 40, 80, 20).build());
    }

    private void save() {
        try {
            int count = Integer.parseInt(countInput.getValue());
            int growth = Integer.parseInt(growthPerWaveInput.getValue());
            int chance = Integer.parseInt(spawnChanceInput.getValue());
            int points = Integer.parseInt(pointsPerKillInput.getValue());

            WaveMob newMob = new WaveMob(mobType, count, growth, chance, points);
            if (mobIndex != -1) {
                waveConfig.getMobs().set(mobIndex, newMob);
            }
            this.minecraft.setScreen(parentScreen);
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
