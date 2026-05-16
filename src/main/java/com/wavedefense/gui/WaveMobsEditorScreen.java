package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class WaveMobsEditorScreen extends ListEditorScreen<WaveMob> {
    private final Location location;
    private final int waveIndex;
    private final WaveConfig waveConfig;
    private EditBox mobCountInput;

    private static final int HEADER_Y  = 30;
    private static final int START_Y   = HEADER_Y + 26; // = 56 = getClipTop()
    private static final int ROW_H     = 46;

    public WaveMobsEditorScreen(Location location, int waveIndex, Screen parent) {
        super(Component.translatable("wavedefense.title.wave_mobs", waveIndex + 1), parent);
        this.location  = location;
        this.waveIndex = waveIndex;
        this.waveConfig = location.getWaves().get(waveIndex);
    }

    // ─── ListEditorScreen / ScrollableScreen API ───────────────────────────

    @Override protected List<WaveMob> getItems()     { return waveConfig.getMobs(); }
    @Override protected int getRowHeight()           { return ROW_H; }
    @Override protected int getStartY()              { return START_Y; }
    @Override protected int getClipTop()             { return START_Y; }
    @Override protected int getClipBot()             { return this.height - 32; }
    @Override protected int getItemsPerPage()        { return Math.max(2, (this.height - 120) / 48); }

    // ─── init() ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;

        // ── Header (static) ──────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.кількість_типів_мобів_f1909330"), button -> {}
        ).bounds(cx - 150, HEADER_Y, 140, 18).build()).active = false;

        mobCountInput = new EditBox(this.font, cx - 5, HEADER_Y, 50, 20, Component.translatable("wavedefense.auto.к_сть_7beea1a7"));
        mobCountInput.setValue(String.valueOf(waveConfig.getMobs().size()));
        mobCountInput.setMaxLength(2);
        addStatic(mobCountInput);

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.apply"),
                button -> applyMobCount()
        ).bounds(cx + 50, HEADER_Y, 90, 20).build());

        // ── Content (scrollable) ──────────────────────────────────────
        if (waveConfig.getMobs().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.моби_не_додані_встановіть_кількі_550ea3f3"),
                    button -> {}
            ).bounds(cx - 150, START_Y, 300, 20).build()).active = false;
        } else {
            buildVisibleRows();

            int scrollX = cx + 175;
            addScrollButtons(scrollX, START_Y, START_Y + (Math.max(1, getItemsPerPage()) - 1) * ROW_H, 22, 22);
        }

        // ── Footer (static) ───────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.done"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(cx + 5, this.height - 28, 95, 20).build());

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.discard"),
                button -> {
                    if (parent instanceof WaveConfigScreen wcs) {
                        wcs.discardWaveChanges();
                    }
                    this.minecraft.setScreen(parent);
                }
        ).bounds(cx - 105, this.height - 28, 105, 20).build());
    }

    // ─── Row builder ───────────────────────────────────────────────────────

    @Override
    protected void buildRowWidgets(int cx, int y, WaveMob mob, int index) {
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        int nameWidth = Math.min(110, cx - 30);
        this.addRenderableWidget(Button.builder(
                Component.literal("§e#" + (index + 1) + " " + mobName),
                button -> {}
        ).bounds(cx - 150, y, nameWidth, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.change"),
                button -> selectMob(index)
        ).bounds(cx - 150 + nameWidth + 4, y, 65, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.налашт_b4f0a8a2"),
                button -> editMob(index)
        ).bounds(cx - 150 + nameWidth + 73, y, 75, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕"),
                button -> deleteMob(index)
        ).bounds(cx - 150 + nameWidth + 152, y, 30, 20).build());

        String info = String.format("§7К-сть: §f%d §7| Приріст: §f%d §7| Шанс: §f%d%% §7| Поінти: §f%d",
                mob.getCount(), mob.getGrowthPerWave(), mob.getSpawnChance(), mob.getPointsPerKill());
        this.addRenderableWidget(Button.builder(
                Component.literal(info), button -> {}
        ).bounds(cx - 150, y + 22, 320, 18).build()).active = false;
    }

    // ─── Render hooks ──────────────────────────────────────────────────────

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);
        if (!waveConfig.getMobs().isEmpty()) {
            g.drawString(this.font, Component.translatable("wavedefense.wave.mobs_hint"),
                    cx - 150, 20, 0xFFFFFF);
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────

    private void applyMobCount() {
        try {
            int targetCount = Integer.parseInt(mobCountInput.getValue());
            // maxLength(2) allows 0-99; cap at 50 mob types per wave (sufficient for any design)
            if (targetCount < 0 || targetCount > 50) {
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.value_out_of_range", 0, 50), true);
                return;
            }

            int currentCount = waveConfig.getMobs().size();
            if (targetCount > currentCount) {
                ResourceLocation zombieId = ResourceLocation.tryParse("minecraft:zombie");
                for (int i = currentCount; i < targetCount; i++) {
                    waveConfig.addMob(new WaveMob(zombieId, 5, 1, 100, 10));
                }
            } else {
                while (waveConfig.getMobs().size() > targetCount) {
                    waveConfig.getMobs().remove(waveConfig.getMobs().size() - 1);
                }
            }
            clampScroll();
            this.rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void selectMob(int mobIndex) {
        this.minecraft.setScreen(new MobSelectionScreen(this, waveConfig, mobIndex));
    }

    private void editMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            this.minecraft.setScreen(new WaveMobEditScreen(this, waveConfig, mobIndex));
        }
    }

    private void deleteMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            waveConfig.removeMob(mobIndex);
            mobCountInput.setValue(String.valueOf(waveConfig.getMobs().size()));
            clampScroll();
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }
}
