package com.wavedefense.gui;

import com.wavedefense.data.MobSpawn;
import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

class MobEditScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final ResourceLocation mobType;
    private final int mobIndex; // -1 для нового моба

    private EditBox baseCountInput;
    private EditBox increasePerWaveInput;
    private EditBox spawnChanceInput;
    private EditBox pointsPerKillInput;

    public MobEditScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Редагування моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            this.mobType = waveConfig.getMobs().get(mobIndex).getMobType();
        } else {
            this.mobType = null;
        }
    }

    public MobEditScreen(Screen parentScreen, WaveConfig waveConfig, ResourceLocation mobType) {
        super(Component.literal("Налаштування моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobType = mobType;
        this.mobIndex = -1;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        MobSpawn existingMob = null;
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            existingMob = waveConfig.getMobs().get(mobIndex);
        }

        // Назва моба
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobType);
        String mobName = entityType != null ? entityType.getDescription().getString() : "Невідомий моб";

        this.addRenderableWidget(Button.builder(
                Component.literal("Моб: §e" + mobName),
                button -> {}
        ).bounds(centerX - 150, 30, 300, 20).build()).active = false;

        // Базова кількість
        this.addRenderableWidget(Button.builder(
                Component.literal("Базова кількість:"),
                button -> {}
        ).bounds(centerX - 150, startY, 120, 20).build()).active = false;

        baseCountInput = new EditBox(this.font, centerX - 25, startY, 60, 20,
                Component.literal("Кількість"));
        baseCountInput.setValue(existingMob != null ? String.valueOf(existingMob.getBaseCount()) : "5");
        baseCountInput.setMaxLength(3);
        this.addRenderableWidget(baseCountInput);

        // Приріст за хвилю
        this.addRenderableWidget(Button.builder(
                Component.literal("Приріст за хвилю:"),
                button -> {}
        ).bounds(centerX - 150, startY + 30, 120, 20).build()).active = false;

        increasePerWaveInput = new EditBox(this.font, centerX - 25, startY + 30, 60, 20,
                Component.literal("Приріст"));
        increasePerWaveInput.setValue(existingMob != null ?
                String.valueOf(existingMob.getCountIncreasePerWave()) : "2");
        increasePerWaveInput.setMaxLength(3);
        this.addRenderableWidget(increasePerWaveInput);

        // Шанс появи (1-100)
        this.addRenderableWidget(Button.builder(
                Component.literal("Шанс появи (1-100):"),
                button -> {}
        ).bounds(centerX - 150, startY + 60, 120, 20).build()).active = false;

        spawnChanceInput = new EditBox(this.font, centerX - 25, startY + 60, 60, 20,
                Component.literal("Шанс"));
        spawnChanceInput.setValue(existingMob != null ?
                String.valueOf(existingMob.getSpawnChance()) : "100");
        spawnChanceInput.setMaxLength(3);
        this.addRenderableWidget(spawnChanceInput);

        // Поінтів за вбивство
        this.addRenderableWidget(Button.builder(
                Component.literal("Поінтів за вбивство:"),
                button -> {}
        ).bounds(centerX - 150, startY + 90, 120, 20).build()).active = false;

        pointsPerKillInput = new EditBox(this.font, centerX - 25, startY + 90, 60, 20,
                Component.literal("Поінти"));
        pointsPerKillInput.setValue(existingMob != null ?
                String.valueOf(existingMob.getPointsPerKill()) : "10");
        pointsPerKillInput.setMaxLength(4);
        this.addRenderableWidget(pointsPerKillInput);

        // Попередній перегляд
        int previewY = startY + 140;
        this.addRenderableWidget(Button.builder(
                Component.literal("§6Попередній перегляд"),
                button -> {}
        ).bounds(centerX - 100, previewY, 200, 20).build()).active = false;

        // Кнопки
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
            int baseCount = Integer.parseInt(baseCountInput.getValue());
            int increasePerWave = Integer.parseInt(increasePerWaveInput.getValue());
            int spawnChance = Math.min(100, Math.max(1, Integer.parseInt(spawnChanceInput.getValue())));
            int pointsPerKill = Integer.parseInt(pointsPerKillInput.getValue());

            MobSpawn mobSpawn = new MobSpawn(mobType, baseCount, increasePerWave,
                    spawnChance, pointsPerKill);

            if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
                waveConfig.getMobs().set(mobIndex, mobSpawn);
            } else {
                waveConfig.addMob(mobSpawn);
            }

            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException e) {
            // Помилка введення - не зберігаємо
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Попередній перегляд розрахунків
        try {
            int baseCount = Integer.parseInt(baseCountInput.getValue());
            int increasePerWave = Integer.parseInt(increasePerWaveInput.getValue());

            int centerX = this.width / 2;
            int previewY = 230;

            graphics.drawString(this.font, "§7Хвиля 1: §f" + baseCount + " мобів",
                    centerX - 80, previewY, 0xFFFFFF);
            graphics.drawString(this.font, "§7Хвиля 5: §f" + (baseCount + increasePerWave * 4) + " мобів",
                    centerX - 80, previewY + 12, 0xFFFFFF);
            graphics.drawString(this.font, "§7Хвиля 10: §f" + (baseCount + increasePerWave * 9) + " мобів",
                    centerX - 80, previewY + 24, 0xFFFFFF);
        } catch (NumberFormatException e) {
            // Ігноруємо помилки при попередньому перегляді
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}