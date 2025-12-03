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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaveMobSettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveMobSettingsScreen.class);
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
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
        String mobName = entityType != null ? entityType.getDescription().getString() : "Unknown";
        LOGGER.info("Opening settings for mob: {}", mobName);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 70;

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        // Назва моба
        this.addRenderableWidget(Button.builder(
                Component.literal("§6Моб: §e" + mobName),
                button -> {}
        ).bounds(centerX - 150, 30, 300, 20).build()).active = false;

        // === Початкова кількість ===
        this.addRenderableWidget(Button.builder(
                Component.literal("§eПочаткова кількість:"),
                button -> {}
        ).bounds(centerX - 150, startY, 140, 20).build()).active = false;

        countInput = new EditBox(this.font, centerX, startY, 80, 20,
                Component.literal("Кількість"));
        countInput.setHint(Component.literal("5"));
        countInput.setValue(String.valueOf(mob.getCount()));
        countInput.setMaxLength(3);
        countInput.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(countInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7За кожну хвилю спавниться ця к-сть"),
                button -> {}
        ).bounds(centerX + 85, startY, 150, 18).build()).active = false;

        // === Приріст за хвилю ===
        this.addRenderableWidget(Button.builder(
                Component.literal("§eПриріст за хвилю:"),
                button -> {}
        ).bounds(centerX - 150, startY + 38, 140, 20).build()).active = false;

        growthInput = new EditBox(this.font, centerX, startY + 38, 80, 20,
                Component.literal("Приріст"));
        growthInput.setHint(Component.literal("1"));
        growthInput.setValue(String.valueOf(mob.getGrowthPerWave()));
        growthInput.setMaxLength(3);
        growthInput.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(growthInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7На скільки збільшується кожну хвилю"),
                button -> {}
        ).bounds(centerX + 85, startY + 38, 150, 18).build()).active = false;

        // === Шанс спавну ===
        this.addRenderableWidget(Button.builder(
                Component.literal("§eШанс спавну (1-100%):"),
                button -> {}
        ).bounds(centerX - 150, startY + 76, 140, 20).build()).active = false;

        chanceInput = new EditBox(this.font, centerX, startY + 76, 80, 20,
                Component.literal("Шанс"));
        chanceInput.setHint(Component.literal("100"));
        chanceInput.setValue(String.valueOf(mob.getSpawnChance()));
        chanceInput.setMaxLength(3);
        chanceInput.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(chanceInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ймовірність появи моба у хвилі"),
                button -> {}
        ).bounds(centerX + 85, startY + 76, 150, 18).build()).active = false;

        // === Поінти за вбивство ===
        this.addRenderableWidget(Button.builder(
                Component.literal("§eПоінтів за вбивство:"),
                button -> {}
        ).bounds(centerX - 150, startY + 114, 140, 20).build()).active = false;

        pointsInput = new EditBox(this.font, centerX, startY + 114, 80, 20,
                Component.literal("Поінти"));
        pointsInput.setHint(Component.literal("10"));
        pointsInput.setValue(String.valueOf(mob.getPointsPerKill()));
        pointsInput.setMaxLength(4);
        pointsInput.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(pointsInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Нагорода гравцю за кожного моба"),
                button -> {}
        ).bounds(centerX + 85, startY + 114, 150, 18).build()).active = false;

        // === Приклад розрахунку ===
        int exampleWave = 5;
        try {
            int baseCount = Integer.parseInt(countInput.getValue());
            int growth = Integer.parseInt(growthInput.getValue());
            int exampleCount = baseCount + (growth * (exampleWave - 1));
            
            String exampleText = String.format("§6Приклад: §7на §e%d §7хвилі → §e%d §7мобів",
                    exampleWave, exampleCount);
            
            this.addRenderableWidget(Button.builder(
                    Component.literal(exampleText),
                    button -> {}
            ).bounds(centerX - 150, startY + 152, 350, 20).build()).active = false;
        } catch (NumberFormatException e) {
            // Ігноруємо помилки парсингу
        }

        // Кнопки збереження
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"),
                button -> save()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> cancel()
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void save() {
        try {
            int count = Integer.parseInt(countInput.getValue().isEmpty() ? "5" : countInput.getValue());
            int growth = Integer.parseInt(growthInput.getValue().isEmpty() ? "1" : growthInput.getValue());
            int chance = Integer.parseInt(chanceInput.getValue().isEmpty() ? "100" : chanceInput.getValue());
            int points = Integer.parseInt(pointsInput.getValue().isEmpty() ? "10" : pointsInput.getValue());

            // Валідація
            if (count < 1 || count > 999) {
                showError("Кількість: 1-999");
                return;
            }
            if (growth < 0 || growth > 999) {
                showError("Приріст: 0-999");
                return;
            }
            if (chance < 1 || chance > 100) {
                showError("Шанс: 1-100%");
                return;
            }
            if (points < 0 || points > 9999) {
                showError("Поінти: 0-9999");
                return;
            }

            mob.setCount(count);
            mob.setGrowthPerWave(growth);
            mob.setSpawnChance(chance);
            mob.setPointsPerKill(points);

            LOGGER.info("Saved mob settings - Count: {}, Growth: {}, Chance: {}%, Points: {}",
                    count, growth, chance, points);

            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid number format in mob settings", e);
            showError("Введіть коректні числа");
        }
    }

    private void cancel() {
        LOGGER.debug("Cancelled mob settings editing");
        this.minecraft.setScreen(parentScreen);
    }

    private void showError(String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§c✕ " + message),
                    true
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        
        // Лінія розділення
        int centerX = this.width / 2;
        graphics.fill(centerX - 155, 55, centerX + 155, 56, 0xFF444444);
        
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
            }
