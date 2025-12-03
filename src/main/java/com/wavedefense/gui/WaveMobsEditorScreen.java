package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaveMobsEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveMobsEditorScreen.class);
    private final Location location;
    private final int waveIndex;
    private final Screen parent;
    private final WaveConfig waveConfig;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 5;

    public WaveMobsEditorScreen(Location location, int waveIndex, Screen parent) {
        super(Component.literal("Моби хвилі " + (waveIndex + 1)));
        this.location = location;
        this.waveIndex = waveIndex;
        this.parent = parent;
        this.waveConfig = location.getWaves().get(waveIndex);
        LOGGER.info("Opened mob editor for wave {} in location {}", waveIndex + 1, location.getName());
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        // Кнопка додавання моба
        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати моба"),
                button -> addNewMob()
        ).bounds(centerX - 100, 35, 200, 20).build());

        if (waveConfig.getMobs().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Моби не додані. Натисніть 'Додати моба' вище."),
                    button -> {}
            ).bounds(centerX - 150, startY, 300, 20).build()).active = false;
        } else {
            for (int i = 0; i < Math.min(ITEMS_PER_PAGE, waveConfig.getMobs().size()); i++) {
                int mobIndex = i + scrollOffset;
                if (mobIndex >= waveConfig.getMobs().size()) break;

                WaveMob mob = waveConfig.getMobs().get(mobIndex);
                int yPos = startY + (i * 55);

                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
                String mobName = entityType != null ? entityType.getDescription().getString() : "???";

                this.addRenderableWidget(Button.builder(
                        Component.literal("§e#" + (mobIndex + 1) + " " + mobName),
                        button -> {}
                ).bounds(centerX - 150, yPos, 120, 20).build()).active = false;

                final int finalMobIndex = mobIndex;
                this.addRenderableWidget(Button.builder(
                        Component.literal("Змінити"),
                        button -> selectMob(finalMobIndex)
                ).bounds(centerX - 25, yPos, 70, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("⚙ Налашт."),
                        button -> editMob(finalMobIndex)
                ).bounds(centerX + 50, yPos, 80, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("§c✕ Видалити"),
                        button -> deleteMob(finalMobIndex)
                ).bounds(centerX + 135, yPos, 85, 20).build());

                String info = String.format("§7К-сть: %d | Приріст: %d | Шанс: %d%% | Поінти: %d",
                        mob.getCount(), mob.getGrowthPerWave(), mob.getSpawnChance(), mob.getPointsPerKill());

                this.addRenderableWidget(Button.builder(
                        Component.literal(info),
                        button -> {}
                ).bounds(centerX - 150, yPos + 22, 370, 18).build()).active = false;
            }

            if (waveConfig.getMobs().size() > ITEMS_PER_PAGE) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp()
                ).bounds(centerX + 230, startY, 25, 25).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown()
                ).bounds(centerX + 230, startY + 200, 25, 25).build());
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void addNewMob() {
        LOGGER.debug("Opening mob selection screen");
        this.minecraft.setScreen(new MobSelectionScreen(this, waveConfig, -1));
    }

    private void selectMob(int mobIndex) {
        LOGGER.debug("Changing mob type at index {}", mobIndex);
        this.minecraft.setScreen(new MobSelectionScreen(this, waveConfig, mobIndex));
    }

    private void editMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            LOGGER.debug("Editing mob settings at index {}", mobIndex);
            this.minecraft.setScreen(new WaveMobSettingsScreen(this, waveConfig, mobIndex));
        }
    }

    private void deleteMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            LOGGER.info("Deleting mob at index {} from wave {}", mobIndex, waveIndex + 1);
            waveConfig.removeMob(mobIndex);

            if (scrollOffset > 0 && scrollOffset >= waveConfig.getMobs().size()) {
                scrollOffset = Math.max(0, waveConfig.getMobs().size() - ITEMS_PER_PAGE);
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
        if (scrollOffset + ITEMS_PER_PAGE < waveConfig.getMobs().size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (!waveConfig.getMobs().isEmpty()) {
            graphics.drawString(this.font,
                    "§7Налаштуйте кожного моба або видаліть непотрібних",
                    this.width / 2 - 150, 20, 0xFFFFFF);
        } else {
            graphics.drawString(this.font,
                    "§7Спочатку додайте мобів для цієї хвилі",
                    this.width / 2 - 120, 20, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
