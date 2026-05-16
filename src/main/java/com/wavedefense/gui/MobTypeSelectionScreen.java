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

public class MobTypeSelectionScreen extends ScrollableScreen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final int mobIndex;
    private List<EntityType<?>> availableMobs;
    private static final int ITEMS_PER_PAGE = 12;
    private int selectedIndex = -1;

    public MobTypeSelectionScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.translatable("wavedefense.title.mob_type_selection"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        availableMobs = new ArrayList<>();
        ForgeRegistries.ENTITY_TYPES.getValues().forEach(entityType -> {
            if (entityType.getCategory().isFriendly() == false &&
                    entityType.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                availableMobs.add(entityType);
            }
        });
    }

    // ─── ScrollableScreen API ──────────────────────────────────────────

    @Override protected int getClipTop() { return 48; }
    @Override protected int getClipBot() { return this.height - 34; }
    // Grid: 3 columns, so "rows" = ceil(availableMobs / 3), items visible = ITEMS_PER_PAGE
    @Override protected int getListSize() { return availableMobs.size(); }
    @Override protected int getItemsPerPage() { return ITEMS_PER_PAGE; }

    // Page-based scroll override (scroll by ITEMS_PER_PAGE at a time)
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset -= ITEMS_PER_PAGE;
            if (scrollOffset < 0) scrollOffset = 0;
            rebuildWidgets();
            return true;
        }
        if (delta < 0 && scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) {
            scrollOffset += ITEMS_PER_PAGE;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    // ─── init() ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        // ── Content (scrollable) — grid 3 columns ───────────────────
        int cols = 3;
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

        // Scroll buttons
        if (availableMobs.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset -= ITEMS_PER_PAGE; if (scrollOffset < 0) scrollOffset = 0; rebuildWidgets(); } }
            ).bounds(centerX + 150, startY, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> { if (scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) { scrollOffset += ITEMS_PER_PAGE; rebuildWidgets(); } }
            ).bounds(centerX + 150, this.height - 80, 25, 25).build());
        }

        // ── Footer (static) ─────────────────────────────────────────
        Button confirmButton = addStatic(Button.builder(
                Component.translatable("wavedefense.button.select"),
                button -> confirm()
        ).bounds(centerX - 110, this.height - 30, 100, 20).build());
        confirmButton.active = selectedIndex >= 0;

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX + 10, this.height - 30, 100, 20).build());
    }

    // ─── Render hooks ──────────────────────────────────────────────────

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("wavedefense.stats.mobs_available", availableMobs.size()), 10, 30, 0xAAAAAA);
    }

    // ─── Дії ───────────────────────────────────────────────────────────

    private void selectMob(int index) {
        selectedIndex = index;
        this.rebuildWidgets();
    }

    private void confirm() {
        if (selectedIndex >= 0) {
            EntityType<?> selectedType = availableMobs.get(selectedIndex);
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(selectedType);

            WaveMob mob = waveConfig.getMobs().get(mobIndex);
            mob.setMobType(mobId);

            this.minecraft.setScreen(parentScreen);
        }
    }
}
