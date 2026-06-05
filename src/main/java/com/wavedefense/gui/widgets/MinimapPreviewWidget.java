package com.wavedefense.gui.widgets;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import com.wavedefense.data.Location;
import com.wavedefense.data.PvpSpawnPoint;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Graphical preview of a location's bbox + spawn points, rendered as a small
 * top-down 2D map inside the editor Area tab. Replaces the text-summary line
 * when there's enough horizontal space (colW &gt;= 360).
 *
 * <p>Drawing model:
 * <ul>
 *   <li>Outer rectangle = bbox top-down projection (X×Z, scaled to fit a square)</li>
 *   <li>Dark fill, light border</li>
 *   <li>Each {@link PvpSpawnPoint} = 3×3 coloured dot at scaled (x, z)</li>
 *   <li>Centre label: "WxL" bbox dimensions</li>
 * </ul>
 *
 * <p>v0.2.62.
 */
public class MinimapPreviewWidget extends Widget {

    private static final int BG_FILL      = 0xC0202020;
    private static final int BORDER       = 0xFF808080;
    private static final int LABEL_COLOUR = 0xFFE0E0E0;
    private static final int DOT_SIZE     = 3;

    private final Location location;

    public MinimapPreviewWidget(int x, int y, int sizePx, Location location) {
        super(x, y, sizePx, sizePx, new StringTextComponent("Minimap preview"));
        this.location = location;
    }

    @Override
    public void renderButton(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        BlockPos a = location.getBboxMin();
        BlockPos b = location.getBboxMax();
        if (a == null || b == null) return; // shouldn't render when bbox unset

        int x = this.x, y = this.y, w = this.getWidth(), h = this.getHeight();
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int dx = Math.max(1, maxX - minX);
        int dz = Math.max(1, maxZ - minZ);

        // Background + border
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + w, y + h, BG_FILL);
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + w, y + 1, BORDER);
        com.wavedefense.gui.GuiCompat.fill(g, x, y + h - 1, x + w, y + h, BORDER);
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + 1, y + h, BORDER);
        com.wavedefense.gui.GuiCompat.fill(g, x + w - 1, y, x + w, y + h, BORDER);

        // Compute scale so the bbox fits with 4-pixel padding on each side
        int innerSize = Math.max(8, Math.min(w, h) - 8);
        int innerX = x + (w - innerSize) / 2;
        int innerY = y + (h - innerSize) / 2;

        // Spawn dots
        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        for (PvpSpawnPoint sp : spawns) {
            BlockPos p = sp.getPos();
            if (p == null) continue;
            int sx = innerX + (int) ((double) (p.getX() - minX) * innerSize / dx);
            int sz = innerY + (int) ((double) (p.getZ() - minZ) * innerSize / dz);
            // Clamp to widget area
            sx = Math.max(x + 1, Math.min(x + w - DOT_SIZE - 1, sx));
            sz = Math.max(y + 1, Math.min(y + h - DOT_SIZE - 1, sz));
            // v0.2.65: prefer explicit TextFormatting color if admin set one
            int colour = sp.getColorName().isEmpty()
                ? teamColour(sp.getTeamName())
                : chatColourToArgb(sp.resolveChatColor());
            com.wavedefense.gui.GuiCompat.fill(g, sx, sz, sx + DOT_SIZE, sz + DOT_SIZE, colour);
        }

        // Centre label (dimensions in blocks)
        Minecraft mc = Minecraft.getInstance();
        String label = (dx + 1) + "×" + (dz + 1);
        int labelW = mc.font.width(label);
        com.wavedefense.gui.GuiCompat.drawString(g, mc.font, label, x + (w - labelW) / 2, y + h - mc.font.lineHeight - 2, LABEL_COLOUR);
    }

    /** v0.2.65: convert a TextFormatting colour to ARGB. */
    private static int chatColourToArgb(net.minecraft.util.text.TextFormatting cf) {
        Integer rgb = cf.getColor();
        return rgb == null ? 0xFFFFFFFF : (0xFF000000 | rgb);
    }

    /** Hash team name → one of 8 TextFormatting colours, ARGB int. */
    private static int teamColour(String teamName) {
        int hash = teamName == null ? 0 : Math.abs(teamName.hashCode());
        int[] palette = {
            0xFFE04040, // red
            0xFF4080E0, // blue
            0xFF40E060, // green
            0xFFE0C040, // yellow
            0xFFB040E0, // purple
            0xFF40E0E0, // cyan
            0xFFE08040, // orange
            0xFFE040A0  // pink
        };
        return palette[hash % palette.length];
    }

    // 1.16.5: Widget interface has no narration API. Method removed.
}
