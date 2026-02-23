package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

public class WaveMobSettingsScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final int mobIndex;
    private final WaveMob mob;

    private EditBox countInput;
    private EditBox growthInput;
    private EditBox chanceInput;
    private EditBox pointsInput;

    public WaveMobSettingsScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Налаштування моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;
        this.mob = waveConfig.getMobs().get(mobIndex);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        this.addRenderableWidget(Button.builder(
                Component.literal("§6Моб: §e" + mobName),
                button -> {}
        ).bounds(centerX - 150, 30, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("Кількість мобів:"),
                button -> {}
        ).bounds(centerX - 150, startY, 120, 20).build()).active = false;

        countInput = new EditBox(this.font, centerX - 25, startY, 80, 20,
                Component.literal("Кількість"));
        countInput.setValue(String.valueOf(mob.getCount()));
        countInput.setMaxLength(3);
        this.addRenderableWidget(countInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Приріст за хвилю:"),
                button -> {}
        ).bounds(centerX - 150, startY + 35, 120, 20).build()).active = false;

        growthInput = new EditBox(this.font, centerX - 25, startY + 35, 80, 20,
                Component.literal("Приріст"));
        growthInput.setValue(String.valueOf(mob.getGrowthPerWave()));
        growthInput.setMaxLength(3);
        this.addRenderableWidget(growthInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Шанс появи (1-100%):"),
                button -> {}
        ).bounds(centerX - 150, startY + 70, 140, 20).build()).active = false;

        chanceInput = new EditBox(this.font, centerX - 5, startY + 70, 80, 20,
                Component.literal("Шанс"));
        chanceInput.setValue(String.valueOf(mob.getSpawnChance()));
        chanceInput.setMaxLength(3);
        this.addRenderableWidget(chanceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Поінтів за вбивство:"),
                button -> {}
        ).bounds(centerX - 150, startY + 105, 140, 20).build()).active = false;

        pointsInput = new EditBox(this.font, centerX - 5, startY + 105, 80, 20,
                Component.literal("Поінти"));
        pointsInput.setValue(String.valueOf(mob.getPointsPerKill()));
        pointsInput.setMaxLength(4);
        this.addRenderableWidget(pointsInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти"),
                button -> save()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void save() {
        try {
            int count = Integer.parseInt(countInput.getValue());
            int growth = Integer.parseInt(growthInput.getValue());
            int chance = Math.min(100, Math.max(1, Integer.parseInt(chanceInput.getValue())));
            int points = Integer.parseInt(pointsInput.getValue());

            mob.setCount(count);
            mob.setGrowthPerWave(growth);
            mob.setSpawnChance(chance);
            mob.setPointsPerKill(points);

            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException e) {
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
