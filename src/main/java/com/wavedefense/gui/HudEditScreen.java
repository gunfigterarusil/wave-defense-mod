package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

/**
 * Екран редагування позиції HUD.
 * Показує прев'ю HUD-блоку який можна:
 *   а) перетягнути мишею (drag)
 *   б) вибрати з пресетів
 */
public class HudEditScreen extends Screen {

    private final Screen parent;
    private final HudLayout layout;

    // Drag state
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    // Розміри прев'ю блоку (приблизні, як у реальному HUD)
    private static final int PREVIEW_W = 240;
    private static final int PREVIEW_H = 58;

    // Поточна позиція прев'ю (мутується під час drag)
    private int previewX, previewY;

    public HudEditScreen(Screen parent) {
        super(new TranslationTextComponent("wavedefense.title.hud_edit"));
        this.parent = parent;
        this.layout = HudLayout.get();
    }

    @Override
    protected void init() {
        super.init();
        recalcPreviewPos();

        int bx = 4, by = this.height - 80;

        // Пресети
        HudLayout.Preset[] presets = HudLayout.Preset.values();
        for (int i = 0; i < presets.length - 1; i++) {
            final HudLayout.Preset p = presets[i];
            boolean active = layout.preset == p;
            this.addButton(new Button(bx + i * 120, by, 118, 16, new StringTextComponent(active ? "§a☑ " : "§7☐ ").append(new TranslationTextComponent(p.key)), b -> { layout.preset = p; layout.blockX = -1; layout.blockY = -1; recalcPreviewPos(); init(); }));
        }

        // Зберегти / скасувати
        int cx = this.width / 2;
        this.addButton(new Button(cx - 110, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.save"), b -> { layout.save(); minecraft.setScreen(parent); }));

        this.addButton(new Button(cx - 5, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> { HudLayout.invalidate(); minecraft.setScreen(parent); }));

        // Підказка
        this.addButton(new Button(cx - 150, this.height - 55, 300, 12, new TranslationTextComponent("wavedefense.hud.drag_hint"), b -> {})).active = false;
    }

    private void recalcPreviewPos() {
        previewX = layout.resolveX(this.width, PREVIEW_W);
        previewY = layout.resolveY(this.height, PREVIEW_H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && isOverPreview(mx, my)) {
            dragging = true;
            dragOffsetX = (int) mx - previewX;
            dragOffsetY = (int) my - previewY;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging && btn == 0) {
            previewX = Math.max(0, Math.min(this.width  - PREVIEW_W, (int) mx - dragOffsetX));
            previewY = Math.max(0, Math.min(this.height - PREVIEW_H, (int) my - dragOffsetY));
            layout.preset  = HudLayout.Preset.CUSTOM;
            layout.blockX  = previewX;
            layout.blockY  = previewY;
            init();
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    private boolean isOverPreview(double mx, double my) {
        return mx >= previewX && mx <= previewX + PREVIEW_W
            && my >= previewY && my <= previewY + PREVIEW_H;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partial) {
        // Напівпрозорий фон
        GuiTheme.renderBackground(g, this.width, this.height);

        // Заголовок
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // ── Прев'ю HUD-блоку ──────────────────────────────────────
        boolean hover = isOverPreview(mouseX, mouseY) || dragging;
        int bgColor  = hover ? 0xCC1A3A1A : 0xCC000040;
        int brdColor = hover ? 0xFF00FF88 : 0xFF4488FF;

        // Фон блоку
        com.wavedefense.gui.GuiCompat.fill(g, previewX,     previewY,     previewX + PREVIEW_W,     previewY + PREVIEW_H,     bgColor);
        // Рамка
        com.wavedefense.gui.GuiCompat.fill(g, previewX - 1, previewY - 1, previewX + PREVIEW_W + 1, previewY,                 brdColor);
        com.wavedefense.gui.GuiCompat.fill(g, previewX - 1, previewY + PREVIEW_H, previewX + PREVIEW_W + 1, previewY + PREVIEW_H + 1, brdColor);
        com.wavedefense.gui.GuiCompat.fill(g, previewX - 1, previewY,     previewX,                 previewY + PREVIEW_H,     brdColor);
        com.wavedefense.gui.GuiCompat.fill(g, previewX + PREVIEW_W, previewY, previewX + PREVIEW_W + 1, previewY + PREVIEW_H, brdColor);

        // Вміст прев'ю
        int ty = previewY + 6;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.hud.preview.location"),  previewX + PREVIEW_W / 2, ty, 0xFFFFFF); ty += 12;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.hud.preview.next_wave"), previewX + PREVIEW_W / 2, ty, 0xFFFFFF); ty += 12;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.hud.preview.mobs"),      previewX + PREVIEW_W / 2, ty, 0xFFFFFF); ty += 12;
        // Полоса прогресу
        int bw = PREVIEW_W - 20;
        com.wavedefense.gui.GuiCompat.fill(g, previewX + 10, ty, previewX + 10 + bw, ty + 4, 0xFF333333);
        com.wavedefense.gui.GuiCompat.fill(g, previewX + 10, ty, previewX + 10 + bw / 2, ty + 4, 0xFF00CC00);

        // Підпис «перетягніть»
        if (hover) {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.hud.drag_label"),
                previewX + PREVIEW_W / 2, previewY + PREVIEW_H / 2 - 4, 0xFFFFAA00);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
