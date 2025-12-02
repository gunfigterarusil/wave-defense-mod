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
    private static final int ITEMS_PER_PAGE = 10;
    private EditBox searchBox;

    public MobSelectionScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Вибір моба"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        availableMobs = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(type -> !type.getCategory().isFriendly())
                .collect(Collectors.toList());
        filteredMobs = new ArrayList<>(availableMobs);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        searchBox = new EditBox(this.font, centerX - 120, 25, 240, 20, Component.literal("Пошук"));
        searchBox.setResponder(this::filterMobs);
        this.addRenderableWidget(searchBox);

        updateMobList();
    }

    private void filterMobs(String query) {
        if (query.isEmpty()) {
            filteredMobs = new ArrayList<>(availableMobs);
        } else {
            filteredMobs = availableMobs.stream()
                .filter(type -> type.getDescription().getString().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        }
        updateMobList();
    }

    private void updateMobList() {
        clearWidgets();
        addRenderableWidget(searchBox);
        int centerX = this.width / 2;
        int startY = 50;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, filteredMobs.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredMobs.size()) break;

            EntityType<?> entityType = filteredMobs.get(index);
            String mobName = entityType.getDescription().getString();
            int yPos = startY + (i * 25);

            this.addRenderableWidget(Button.builder(
                    Component.literal(mobName),
                    button -> selectMob(entityType)
            ).bounds(centerX - 120, yPos, 240, 20).build());
        }
    }

    private void selectMob(EntityType<?> entityType) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        if (mobIndex != -1) {
            waveConfig.getMobs().get(mobIndex).setMobType(mobId);
        }
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }
}
