package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.EntityType;
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
        super(new TranslationTextComponent("wavedefense.title.mob_type_selection"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;

        availableMobs = new ArrayList<>();
        ForgeRegistries.ENTITIES.getValues().forEach(entityType -> {
            if (entityType.getCategory().isFriendly() == false &&
                    entityType.getCategory() != net.minecraft.entity.EntityClassification.MISC) {
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
            init();
            return true;
        }
        if (delta < 0 && scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) {
            scrollOffset += ITEMS_PER_PAGE;
            init();
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
            this.addButton(new Button(xPos, yPos, 110, 25, new StringTextComponent((selectedIndex == finalIndex ? "§a✓ " : "") + mobName), button -> selectMob(finalIndex)));
        }

        // Scroll buttons
        if (availableMobs.size() > ITEMS_PER_PAGE) {
            this.addButton(new Button(centerX + 150, startY, 25, 25, new StringTextComponent("▲"), button -> { if (scrollOffset > 0) { scrollOffset -= ITEMS_PER_PAGE; if (scrollOffset < 0) scrollOffset = 0; init(); } }));

            this.addButton(new Button(centerX + 150, this.height - 80, 25, 25, new StringTextComponent("▼"), button -> { if (scrollOffset + ITEMS_PER_PAGE < availableMobs.size()) { scrollOffset += ITEMS_PER_PAGE; init(); } }));
        }

        // ── Footer (static) ─────────────────────────────────────────
        Button confirmButton = addStatic(new Button(centerX - 110, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.button.select"), button -> confirm()));
        confirmButton.active = selectedIndex >= 0;

        addStatic(new Button(centerX + 10, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), button -> this.minecraft.setScreen(parentScreen)));
    }

    // ─── Render hooks ──────────────────────────────────────────────────

    @Override
    protected void renderHeader(MatrixStack g, int mx, int my, float pt) {
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, new TranslationTextComponent("wavedefense.stats.mobs_available", availableMobs.size()), 10, 30, 0xAAAAAA);
    }

    // ─── Дії ───────────────────────────────────────────────────────────

    private void selectMob(int index) {
        selectedIndex = index;
        this.init();
    }

    private void confirm() {
        if (selectedIndex >= 0) {
            EntityType<?> selectedType = availableMobs.get(selectedIndex);
            ResourceLocation mobId = ForgeRegistries.ENTITIES.getKey(selectedType);

            WaveMob mob = waveConfig.getMobs().get(mobIndex);
            mob.setMobType(mobId);

            this.minecraft.setScreen(parentScreen);
        }
    }
}
