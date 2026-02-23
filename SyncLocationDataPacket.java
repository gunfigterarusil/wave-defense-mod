package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class MobTypeSelectionScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final int mobIndex;
    private List<EntityType<?>> availableMobs;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 12;
    private int selectedIndex = -1;

    public MobTypeSelectionScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Вибір типу моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        // Фільтруємо тільки ворожих мобів
        availableMobs = new ArrayList<>();
        ForgeRegistries.ENTITY_TYPES.getValues().forEach(entityType -> {
            if (entityType.getCategory().isFriendly() == false &&
                    entityType.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                availableMobs.add(entityType);
            }
        });
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        // Список мобів (3 колонки)
        int cols = 3;
        int rows = (int) Math.ceil((double) Math.min(ITEMS_PER_PAGE, availableMobs.size()) / cols);

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, availableMobs.size()); i++) {
            int index = i + scrollOffset;
            if (index >= availableMobs.size()) break;

            EntityType<?> entityType = availableMobs.get(index);
            String mobName = entityType.getDescription().getString();
            if (mobName.length() > 14) {
                mobName = mobName.substring(0, 11) + "...";
            }

            int row = i / cols;
            int col = i % cols;
            int xPos = centerX - 180 + (col * 120);
            int yPos = startY + (row * 30);

            final int finalIndex = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal((selectedIndex == finalIndex ? "§a✓ " : "") + mobName),
                    button -> selectMob(finalIndex)
            ).bounds(xPos, yPos, 110, 25).build());
        }

        // Кнопки прокрутки
        if (availableMobs.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 150, startY, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 150, this.height - 80, 25, 25).build());
        }

        // Кнопки навігації
        Button confirmButton = Button.builder(
                Component.literal("Вибрати"),
                button -> confirm()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build();
        confirmButton.active = selectedIndex >= 0;
        this.addRenderableWidget(confirmButton);

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void selectMob(int index) {
        selectedIndex = index;
        this.rebuildWidgets();
    }

    private void confirm() {
        if (selectedIndex >= 0) {
            EntityType<?> selectedType = availableMobs.get(selectedIndex);
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(selectedType);

            // Оновлюємо тип моба
            WaveMob mob = waveConfig.getMobs().get(mobIndex);
            mob.setMobType(mobId);

            this.minecraft.setScreen(parentScreen);
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset -= ITEMS_PER_PAGE;
            if (scrollOffset < 0) scrollOffset = 0;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) {
            scrollOffset += ITEMS_PER_PAGE;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawString(this.font,
                "Доступно мобів: " + availableMobs.size(),
                10, 30, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}