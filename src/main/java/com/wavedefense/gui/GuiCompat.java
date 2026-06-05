package com.wavedefense.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.16.5 GUI compatibility shim.
 *
 * <p>Extends {@link AbstractGui} so we can call its protected instance
 * {@code fillGradient}; static methods on this class wrap that via a
 * shared instance.
 */
public class GuiCompat extends AbstractGui {

    private static final GuiCompat I = new GuiCompat();
    private GuiCompat() {}

    // ── drawString ─────────────────────────────────────────────────────────

    public static void drawString(MatrixStack g, FontRenderer font, String text, int x, int y, int color) {
        font.draw(g, text, x, y, color);
    }
    public static void drawString(MatrixStack g, FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(g, text, x, y, color);
        else        font.draw(g, text, x, y, color);
    }
    public static void drawString(MatrixStack g, FontRenderer font, ITextComponent text, int x, int y, int color) {
        font.draw(g, text, x, y, color);
    }
    public static void drawString(MatrixStack g, FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(g, text, x, y, color);
        else        font.draw(g, text, x, y, color);
    }
    public static void drawString(MatrixStack g, FontRenderer font, IFormattableTextComponent text, int x, int y, int color) {
        font.draw(g, (ITextComponent) text, x, y, color);
    }

    // ── drawCenteredString ─────────────────────────────────────────────────

    public static void drawCenteredString(MatrixStack g, FontRenderer font, String text, int cx, int y, int color) {
        font.draw(g, text, cx - font.width(text) / 2.0f, y, color);
    }
    public static void drawCenteredString(MatrixStack g, FontRenderer font, ITextComponent text, int cx, int y, int color) {
        font.draw(g, text, cx - font.width(text) / 2.0f, y, color);
    }

    // ── fill / fillGradient ────────────────────────────────────────────────

    public static void fill(MatrixStack g, int x1, int y1, int x2, int y2, int color) {
        AbstractGui.fill(g, x1, y1, x2, y2, color);
    }
    public static void fillGrad(MatrixStack g, int x1, int y1, int x2, int y2, int colorTop, int colorBot) {
        I.fillGradient(g, x1, y1, x2, y2, colorTop, colorBot);
    }

    // ── flush (no-op on 1.16.5) ────────────────────────────────────────────

    public static void flush(MatrixStack g) { /* no-op — 1.16.5 renders eagerly */ }

    // ── renderItem / renderItemDecorations ─────────────────────────────────

    public static void renderItem(MatrixStack g, ItemStack stack, int x, int y) {
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        ir.renderAndDecorateItem(stack, x, y);
    }
    public static void renderItemDecorations(MatrixStack g, FontRenderer font, ItemStack stack, int x, int y) {
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        ir.renderGuiItemDecorations(font, stack, x, y);
    }

    // ── renderTooltip (item) ───────────────────────────────────────────────

    public static void renderTooltip(Screen screen, MatrixStack g, FontRenderer font, ItemStack stack, int mx, int my) {
        List<ITextComponent> lines = screen.getTooltipFromItem(stack);
        List<IReorderingProcessor> processors = new ArrayList<>();
        for (ITextComponent line : lines) processors.add(line.getVisualOrderText());
        screen.renderTooltip(g, processors, mx, my);
    }
}
