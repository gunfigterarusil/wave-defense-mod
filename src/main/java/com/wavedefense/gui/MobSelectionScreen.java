package com.wavedefense.gui;

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
import java.util.stream.Collectors;

public class MobSelectionScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final int mobIndex;
    private List<EntityType<?>> availableMobs;
    private List<EntityType<?>> filteredMobs;
    private int scrollOffset = 0;
    private EditBox searchBox;

    public MobSelectionScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Вибір моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        availableMobs = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(type -> !type.getCategory().isFriendly())
                .sorted((a, b) -> a.getDescription().getString().compareTo(b.getDescription().getString()))
                .collect(Collectors.toList());
        filteredMobs = new ArrayList<>(availableMobs);
    }

    // Адаптивна кількість рядків залежно від висоти екрану
    private int getItemsPerPage() {
        return Math.max(3, (this.height - 100) / 22);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        // Поле пошуку
        searchBox = new EditBox(this.font, centerX - 120, 25, 200, 20, Component.literal("Пошук моба..."));
        searchBox.setResponder(this::filterMobs);
        this.addRenderableWidget(searchBox);

        // Кнопка "Назад"
        this.addRenderableWidget(Button.builder(
                Component.literal("← Назад"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 85, 25, 70, 20).build());

        buildMobList();
    }

    private void filterMobs(String query) {
        scrollOffset = 0;
        if (query.isEmpty()) {
            filteredMobs = new ArrayList<>(availableMobs);
        } else {
            filteredMobs = availableMobs.stream()
                    .filter(type -> type.getDescription().getString().toLowerCase().contains(query.toLowerCase())
                            || ForgeRegistries.ENTITY_TYPES.getKey(type).toString().toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());
        }
        buildMobList();
    }

    private void buildMobList() {
        // Зберігаємо searchBox
        clearWidgets();
        addRenderableWidget(searchBox);

        int centerX = this.width / 2;
        int startY = 52;
        int itemsPerPage = getItemsPerPage();

        // Кнопка назад
        this.addRenderableWidget(Button.builder(
                Component.literal("← Назад"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 85, 25, 70, 20).build());

        // Список мобів
        for (int i = 0; i < Math.min(itemsPerPage, filteredMobs.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredMobs.size()) break;

            EntityType<?> entityType = filteredMobs.get(index);
            String mobName = entityType.getDescription().getString();
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            String modId = mobId != null ? "[" + mobId.getNamespace() + "] " : "";
            int yPos = startY + (i * 22);

            int btnWidth = Math.min(280, this.width - 60);
            this.addRenderableWidget(Button.builder(
                    Component.literal(modId + mobName),
                    button -> selectMob(entityType)
            ).bounds(centerX - btnWidth / 2, yPos, btnWidth, 20).build());
        }

        // Кнопки скролу (якщо потрібні)
        if (filteredMobs.size() > itemsPerPage) {
            int scrollBtnX = centerX + Math.min(145, this.width / 2 - 20);

            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset--; buildMobList(); } }
            ).bounds(scrollBtnX, startY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> { if (scrollOffset + itemsPerPage < filteredMobs.size()) { scrollOffset++; buildMobList(); } }
            ).bounds(scrollBtnX, startY + (itemsPerPage - 1) * 22, 20, 20).build());

            // Лічильник
            String counter = (scrollOffset + 1) + "-" + Math.min(scrollOffset + itemsPerPage, filteredMobs.size()) + "/" + filteredMobs.size();
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + counter), button -> {}
            ).bounds(scrollBtnX - 10, startY + (itemsPerPage / 2) * 22, 40, 18).build()).active = false;
        }
    }

    private void selectMob(EntityType<?> entityType) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        if (mobIndex != -1 && mobId != null) {
            waveConfig.getMobs().get(mobIndex).setMobType(mobId);
        }
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int itemsPerPage = getItemsPerPage();
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; buildMobList(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < filteredMobs.size()) { scrollOffset++; buildMobList(); }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
