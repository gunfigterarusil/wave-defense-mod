package com.wavedefense.gui;

import com.wavedefense.data.MobSpawn;
import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

// ============= MobSelectionScreen.java =============
public class MobSelectionScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private List<EntityType<?>> availableMobs;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 10;
    private int selectedMobIndex = -1;

    public MobSelectionScreen(Screen parentScreen, WaveConfig waveConfig) {
        super(Component.literal("Вибір моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;

        // Отримуємо список всіх доступних мобів
        availableMobs = new ArrayList<>();
        ForgeRegistries.ENTITY_TYPES.getValues().forEach(entityType -> {
            // Фільтруємо тільки ворожих мобів
            if (entityType.getCategory().isFriendly() == false) {
                availableMobs.add(entityType);
            }
        });
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        // Пошуковий рядок
        EditBox searchBox = new EditBox(this.font, centerX - 120, 25, 240, 20,
                Component.literal("Пошук"));
        searchBox.setHint(Component.literal("Введіть назву моба..."));
        this.addRenderableWidget(searchBox);

        // Список мобів
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, availableMobs.size()); i++) {
            int index = i + scrollOffset;
            if (index >= availableMobs.size()) break;

            EntityType<?> entityType = availableMobs.get(index);
            String mobName = entityType.getDescription().getString();
            int yPos = startY + (i * 25);
            final int finalIndex = index;

            this.addRenderableWidget(Button.builder(
                    Component.literal(mobName + (selectedMobIndex == finalIndex ? " ✓" : "")),
                    button -> selectMob(finalIndex)
            ).bounds(centerX - 120, yPos, 240, 20).build());
        }

        // Кнопки прокрутки
        if (availableMobs.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(this.width - 30, startY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(this.width - 30, startY + 220, 20, 20).build());
        }

        // Кнопки навігації
        Button selectButton = Button.builder(
                Component.literal("Вибрати"),
                button -> confirmSelection()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build();
        selectButton.active = selectedMobIndex >= 0;
        this.addRenderableWidget(selectButton);

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    private void selectMob(int index) {
        selectedMobIndex = index;
        this.rebuildWidgets();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    private void confirmSelection() {
        if (selectedMobIndex >= 0) {
            EntityType<?> selectedType = availableMobs.get(selectedMobIndex);
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(selectedType);

            // Відкриваємо екран налаштування моба
            this.minecraft.setScreen(new MobEditScreen(parentScreen, waveConfig, mobId));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        graphics.drawString(this.font,
                "Доступно мобів: " + availableMobs.size(),
                10, this.height - 15, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
