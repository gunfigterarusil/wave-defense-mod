package com.wavedefense.gui;


import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private CoordinateInputField coordField;

    public WaveSpawnEditorScreen(Screen parent, WaveConfig wave, int waveIndex) {
        super(Component.translatable("wavedefense.title.wave_spawn", waveIndex + 1));
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
            Component.translatable("wavedefense.auto.особлива_точка_спавну_мобів_для_757a9ade"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.пріоритет_вище_ніж_точки_спавну_84045c3f"), b -> {}
        ).bounds(cx - 160, y, 320, 12).build()).active = false;
        y += 18;

        // Coordinate inputs — startX=cx-160, labelW=18, fieldW=65, stride=92
        coordField = new CoordinateInputField(this.font, cx - 160, y, 18, 65, 18, 92);
        coordField.setValue(hasPos ? pos : null);
        coordField.addToScreen(this::addRenderableWidget);

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.моя_позиція_8b5c4cb8"),
            b -> coordField.setFromPlayer(minecraft.player)
        ).bounds(coordField.getEndX() + 7, y, 90, 18).build());
        y += 26;

        // Enable/disable toggle
        this.addRenderableWidget(Button.builder(
            ((hasPos) ? Component.translatable("wavedefense.auto.видалити_особливий_спавн_використову_61926c09") : Component.translatable("wavedefense.auto.заповни_координати_вище_будуть_викор_3dc97c8d")),
            b -> {
                if (wave.hasWaveSpawnPos()) {
                    wave.setWaveSpawnPos(null);
                    rebuildWidgets();
                }
            }
        ).bounds(cx - 160, y, 320, 16).build());
        y += 24;

        // Current status
        if (hasPos) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.поточний_спавн_x_d_y_d_z_d_417194c9",
                        pos.getX(), pos.getY(), pos.getZ()), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
        }

        // Bottom buttons
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.зберегти_617e5dc0"),
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
            Component.translatable("wavedefense.button.cancel"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 26, 100, 20).build());
    }

    private void save() {
        wave.setWaveSpawnPos(coordField.getValue()); // null if empty or invalid
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
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
