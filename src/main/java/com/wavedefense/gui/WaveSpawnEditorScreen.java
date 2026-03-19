package com.wavedefense.gui;


import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Редактор точки спавну мобів для конкретної хвилі.
 * Якщо задана — пріоритет вище, ніж точки спавну мобів локації.
 */
public class WaveSpawnEditorScreen extends Screen {

    private final Screen    parent;
    private final WaveConfig wave;
    private final int       waveIndex;

    private EditBox xInput, yInput, zInput;

    public WaveSpawnEditorScreen(Screen parent, WaveConfig wave, int waveIndex) {
        super(Component.literal("📍 Спавн Хвилі " + (waveIndex + 1)));
        this.parent     = parent;
        this.wave       = wave;
        this.waveIndex  = waveIndex;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 40;

        boolean hasPos = wave.hasWaveSpawnPos();
        BlockPos pos   = hasPos ? wave.getWaveSpawnPos() : BlockPos.ZERO;

        this.addRenderableWidget(Button.builder(
            Component.literal("§7Особлива точка спавну мобів для цієї хвилі:"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;

        this.addRenderableWidget(Button.builder(
            Component.literal("§8(пріоритет вище ніж точки спавну мобів у налаштуваннях локації)"), b -> {}
        ).bounds(cx - 160, y, 320, 12).build()).active = false;
        y += 18;

        // Coordinate inputs
        this.addRenderableWidget(Button.builder(Component.literal("§7X:"), b -> {}).bounds(cx - 160, y, 18, 18).build()).active = false;
        xInput = new EditBox(this.font, cx - 140, y, 65, 18, Component.literal("X"));
        xInput.setValue(hasPos ? String.valueOf(pos.getX()) : "");
        xInput.setMaxLength(7);
        this.addRenderableWidget(xInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Y:"), b -> {}).bounds(cx - 68, y, 18, 18).build()).active = false;
        yInput = new EditBox(this.font, cx - 48, y, 65, 18, Component.literal("Y"));
        yInput.setValue(hasPos ? String.valueOf(pos.getY()) : "");
        yInput.setMaxLength(7);
        this.addRenderableWidget(yInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Z:"), b -> {}).bounds(cx + 24, y, 18, 18).build()).active = false;
        zInput = new EditBox(this.font, cx + 44, y, 65, 18, Component.literal("Z"));
        zInput.setValue(hasPos ? String.valueOf(pos.getZ()) : "");
        zInput.setMaxLength(7);
        this.addRenderableWidget(zInput);

        this.addRenderableWidget(Button.builder(
            Component.literal("📌 Моя позиція"),
            b -> {
                if (minecraft.player != null) {
                    BlockPos pp = minecraft.player.blockPosition();
                    xInput.setValue(String.valueOf(pp.getX()));
                    yInput.setValue(String.valueOf(pp.getY()));
                    zInput.setValue(String.valueOf(pp.getZ()));
                }
            }
        ).bounds(cx + 114, y, 90, 18).build());
        y += 26;

        // Enable/disable toggle
        this.addRenderableWidget(Button.builder(
            Component.literal(hasPos ? "§c✕ Видалити особливий спавн (використовувати локаційний)" : "§8(заповни координати вище — будуть використані при збереженні)"),
            b -> {
                if (wave.hasWaveSpawnPos()) {
                    wave.setWaveSpawnPos(null);
                    if (xInput != null) xInput.setValue("");
                    if (yInput != null) yInput.setValue("");
                    if (zInput != null) zInput.setValue("");
                    rebuildWidgets();
                }
            }
        ).bounds(cx - 160, y, 320, 16).build());
        y += 24;

        // Current status
        if (hasPos) {
            this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§aПоточний спавн: §eX%d Y%d Z%d",
                        pos.getX(), pos.getY(), pos.getZ())), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
        }

        // Bottom buttons
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓ Зберегти"),
            b -> {
                save();
                // Авто-збереження: надсилаємо пакет оновлення локації на сервер
                if (parent instanceof WaveConfigScreen wcs) {
                    wcs.autoSave();
                }
                this.minecraft.setScreen(parent);
            }
        ).bounds(cx - 110, this.height - 26, 100, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("Скасувати"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 26, 100, 20).build());
    }

    private void save() {
        String sx = xInput.getValue().trim();
        String sy = yInput.getValue().trim();
        String sz = zInput.getValue().trim();
        if (sx.isEmpty() && sy.isEmpty() && sz.isEmpty()) {
            wave.setWaveSpawnPos(null);
            return;
        }
        try {
            int x = sx.isEmpty() ? 0 : Integer.parseInt(sx);
            int y = sy.isEmpty() ? 0 : Integer.parseInt(sy);
            int z = sz.isEmpty() ? 0 : Integer.parseInt(sz);
            wave.setWaveSpawnPos(new BlockPos(x, y, z));
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "§6📍 Спавн Хвилі §e" + (waveIndex + 1), this.width / 2, 16, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
