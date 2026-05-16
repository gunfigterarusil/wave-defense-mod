package com.wavedefense.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GuiTheme {
    public static final int BG_TOP = 0xFF111722;
    public static final int BG_BOTTOM = 0xFF05070B;
    public static final int PANEL = 0xD0182432;
    public static final int PANEL_DARK = 0xE00B1018;
    public static final int PANEL_SOFT = 0x801C2B3A;
    public static final int BORDER = 0xFF3C5B74;
    public static final int BORDER_BRIGHT = 0xFF6EA7C8;
    public static final int ACCENT = 0xFF5CC8FF;
    public static final int ACCENT_ALT = 0xFFFFC857;
    public static final int TEXT = 0xFFE7F5FF;
    public static final int TEXT_MUTED = 0xFF9FB4C5;

    private GuiTheme() {}

    public static void renderBackground(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        renderGrid(g, width, height);
        g.fillGradient(0, 0, width, 44, 0xAA1C2A38, 0x001C2A38);
        g.fillGradient(0, height - 42, width, height, 0x001C2A38, 0xAA05070B);
    }

    public static void renderHeader(GuiGraphics g, Font font, Component title, int width) {
        panel(g, width / 2 - Math.min(220, width / 2 - 12), 8,
                width / 2 + Math.min(220, width / 2 - 12), 31);
        g.drawCenteredString(font, title, width / 2, 15, TEXT);
        g.fill(width / 2 - 42, 29, width / 2 + 42, 30, ACCENT);
        g.fill(width / 2 - 18, 30, width / 2 + 18, 31, ACCENT_ALT);
    }

    public static void renderContentFrame(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (x2 <= x1 || y2 <= y1) return;
        g.fill(x1, y1, x2, y2, 0x60101822);
        g.fillGradient(x1, y1, x2, Math.min(y1 + 16, y2), 0x501C3444, 0x001C3444);
        outline(g, x1, y1, x2, y2, BORDER);
    }

    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, PANEL);
        g.fillGradient(x1, y1, x2, Math.min(y1 + 12, y2), PANEL_SOFT, 0x001C2B3A);
        outline(g, x1, y1, x2, y2, BORDER);
    }

    public static void card(GuiGraphics g, int x1, int y1, int x2, int y2, boolean hovered) {
        int fill = hovered ? 0xD0223444 : PANEL_DARK;
        int border = hovered ? BORDER_BRIGHT : BORDER;
        g.fill(x1, y1, x2, y2, fill);
        g.fillGradient(x1, y1, x2, Math.min(y1 + 10, y2), hovered ? 0x403B6B86 : 0x301E3444, 0x001E3444);
        outline(g, x1, y1, x2, y2, border);
    }

    public static void sectionLabel(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, TEXT_MUTED, false);
        g.fill(x, y + 11, x + Math.min(96, font.width(text)), y + 12, ACCENT);
    }

    public static void scrollBar(GuiGraphics g, int x, int y1, int y2, int offset, int size, int page) {
        if (size <= page || y2 <= y1) return;
        g.fill(x, y1, x + 3, y2, 0x80304050);
        int track = y2 - y1;
        int thumbH = Math.max(12, track * page / Math.max(1, size));
        int maxOffset = Math.max(1, size - page);
        int thumbY = y1 + (track - thumbH) * Math.max(0, offset) / maxOffset;
        g.fill(x - 1, thumbY, x + 4, thumbY + thumbH, ACCENT);
    }

    private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void renderGrid(GuiGraphics g, int width, int height) {
        int grid = 24;
        for (int x = 0; x < width; x += grid) {
            g.fill(x, 0, x + 1, height, 0x14000000);
        }
        for (int y = 0; y < height; y += grid) {
            g.fill(0, y, width, y + 1, 0x12000000);
        }
    }
}
