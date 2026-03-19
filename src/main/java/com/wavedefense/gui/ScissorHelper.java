package com.wavedefense.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;

import java.util.List;

/**
 * Scissor-кліп для GUI — ховає scrollable елементи за межами зони,
 * як у ванільному Language/ResourcePack екрані.
 *
 * OpenGL scissor: (0,0) = лівий НИЖНІЙ кут, Y від низу до верху.
 * GUI: (0,0) = лівий ВЕРХНІЙ кут, Y від верху до низу.
 * Треба інвертувати Y і врахувати guiScale для HiDPI.
 */
public final class ScissorHelper {

    private ScissorHelper() {}

    /**
     * Вмикає scissor для зони [x, y, x+width, y+height] (GUI-координати).
     */
    public static void enable(int x, int y, int width, int height) {
        double scale  = Minecraft.getInstance().getWindow().getGuiScale();
        int    screenH = Minecraft.getInstance().getWindow().getHeight();

        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round(screenH - (y + height) * scale);
        int sw = (int) Math.round(width  * scale);
        int sh = (int) Math.round(height * scale);

        RenderSystem.enableScissor(sx, Math.max(0, sy), Math.max(0, sw), Math.max(0, sh));
    }

    /** Вимикає scissor. */
    public static void disable() {
        RenderSystem.disableScissor();
    }

    /**
     * Рендерить усі AbstractWidget зі списку renderables,
     * чий getY() належить [yFrom, yTo).
     */
    public static void renderBand(
            List<? extends Renderable> renderables,
            GuiGraphics g, int mx, int my, float pt,
            int yFrom, int yTo) {
        for (Renderable r : renderables) {
            if (r instanceof AbstractWidget w) {
                if (w.getY() >= yFrom && w.getY() < yTo) {
                    w.render(g, mx, my, pt);
                }
            }
        }
    }
}
