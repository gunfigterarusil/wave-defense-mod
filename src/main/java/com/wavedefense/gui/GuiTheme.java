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

    public static final int STATUS_PVP     = 0xFFE05555;
    public static final int STATUS_PVE     = 0xFF55C855;
    public static final int STATUS_WAITING = 0xFF888888;
    public static final int STATUS_ACTIVE  = 0xFF5CC8FF;
    public static final int DANGER         = 0xFFEF4444;
    public static final int WARN           = 0xFFF59E0B;
    public static final int SECTION_HEADER = 0xCC0D1825;

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

    /** Pill-shaped colored badge: "PvP", "Active", "2/8" etc. */
    public static void badge(GuiGraphics g, Font font, String text, int x, int y, int bgColor) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 10, bgColor);
        g.fill(x, y, x + 1, y + 10, 0x40FFFFFF); // subtle left-edge highlight
        g.drawString(font, text, x + 3, y + 1, TEXT, false);
    }

    /** Section header with accent stripe — visual divider between form sections. */
    public static void sectionDivider(GuiGraphics g, Font font, Component label, int x, int y, int width) {
        g.fill(x, y + 4, x + width, y + 5, BORDER);
        g.fill(x, y + 4, x + Math.min(4, width), y + 5, ACCENT);
        int labelW = font.width(label);
        g.fill(x + 8, y, x + 12 + labelW + 4, y + 12, SECTION_HEADER);
        g.drawString(font, label, x + 12, y + 2, ACCENT, false);
    }

    /** Reusable progress bar (wave progress, health, etc.). */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int fillColor) {
        g.fill(x, y, x + w, y + h, 0x80000000);
        int filled = (int)(net.minecraft.util.Mth.clamp(progress, 0f, 1f) * w);
        if (filled > 0) g.fillGradient(x, y, x + filled, y + h, fillColor, (fillColor & 0x00FFFFFF) | 0xCC000000);
        outline(g, x, y, x + w, y + h, BORDER);
    }

    /** Sub-section panel with colored left accent border. */
    public static void renderSectionFrame(GuiGraphics g, int x1, int y1, int x2, int y2, int accentColor) {
        g.fill(x1, y1, x2, y2, 0x30101822);
        g.fill(x1, y1, x1 + 2, y2, accentColor);
        g.fill(x1, y2 - 1, x2, y2, BORDER);
    }

    /** Horizontal status line with a colored block — for HUD rendering. */
    public static void statusLine(GuiGraphics g, Font font, String text,
                                   int blockX, int blockW, int y, int bgColor, int textColor) {
        g.fill(blockX, y, blockX + blockW, y + 12, bgColor);
        g.drawString(font, text, blockX + 3, y + 2, textColor, false);
    }

    public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
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
