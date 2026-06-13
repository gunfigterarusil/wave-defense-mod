package com.wavedefense.gui;

import net.minecraft.util.text.TranslationTextComponent;


import com.wavedefense.data.WaveConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

/**
 * Редактор точки спавну мобів для конкретної хвилі.
 * Якщо задана — пріоритет вище, ніж точки спавну мобів локації.
 */
public class WaveSpawnEditorScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private final Screen    parent;
    private final WaveConfig wave;
    private final int       waveIndex;

    private CoordinateInputField coordField;

    public WaveSpawnEditorScreen(Screen parent, WaveConfig wave, int waveIndex) {
        super(new TranslationTextComponent("wavedefense.title.wave_spawn", waveIndex + 1));
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

        this.addButton(new Button(cx - 160, y, 320, 14, new TranslationTextComponent("wavedefense.auto.особлива_точка_спавну_мобів_для_757a9ade"), b -> {})).active = false;
        y += 18;

        this.addButton(new Button(cx - 160, y, 320, 12, new TranslationTextComponent("wavedefense.auto.пріоритет_вище_ніж_точки_спавну_84045c3f"), b -> {})).active = false;
        y += 18;

        // Coordinate inputs — startX=cx-160, labelW=18, fieldW=65, stride=92
        coordField = new CoordinateInputField(this.font, cx - 160, y, 18, 65, 18, 92);
        coordField.setValue(hasPos ? pos : null);
        coordField.addToScreen(this::addButton);

        this.addButton(new Button(coordField.getEndX() + 7, y, 90, 18, new TranslationTextComponent("wavedefense.auto.моя_позиція_8b5c4cb8"), b -> coordField.setFromPlayer(minecraft.player)));
        y += 26;

        // Enable/disable toggle
        this.addButton(new Button(cx - 160, y, 320, 16, ((hasPos) ? new TranslationTextComponent("wavedefense.auto.видалити_особливий_спавн_використову_61926c09") : new TranslationTextComponent("wavedefense.auto.заповни_координати_вище_будуть_викор_3dc97c8d")), b -> {
                if (wave.hasWaveSpawnPos()) {
                    wave.setWaveSpawnPos(null);
                    init();
                }
            }));
        y += 24;

        // Current status
        if (hasPos) {
            this.addButton(new Button(cx - 160, y, 320, 14, new TranslationTextComponent("wavedefense.auto.поточний_спавн_x_d_y_d_z_d_417194c9",
                        pos.getX(), pos.getY(), pos.getZ()), b -> {})).active = false;
        }

        // Bottom buttons
        this.addButton(new Button(cx - 110, this.height - 26, 100, 20, new TranslationTextComponent("wavedefense.auto.зберегти_617e5dc0"), b -> {
                save();
                // Авто-збереження: надсилаємо пакет оновлення локації на сервер
                if (parent instanceof WaveConfigScreen) { WaveConfigScreen wcs = (WaveConfigScreen) parent;
                    wcs.autoSave();
                }
                this.minecraft.setScreen(parent);
            }));
        this.addButton(new Button(cx + 10, this.height - 26, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> this.minecraft.setScreen(parent)));
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
    public void render(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 16, GuiTheme.TEXT);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
