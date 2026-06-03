package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Tactical top-down minimap shown in the bottom-left corner during PvP matches
 * when the location has both a configured bbox area and {@code minimapEnabled = true}.
 *
 * <p>Renders:
 * <ul>
 *   <li>An outline of the bbox region scaled to fit a fixed-size HUD square</li>
 *   <li>A dot per teammate (from {@link ClientTeammatesManager}) — green for self,
 *       team-coloured for other allies</li>
 *   <li>A small triangle indicating the local player's facing direction</li>
 * </ul>
 *
 * <p>Enemies are NOT shown — by design (tactical advantage shouldn't be a wallhack).
 *
 * <p>Called from {@link PlayerHUD#render} once per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class MinimapRenderer {

    private MinimapRenderer() {}

    // ── Layout constants ─────────────────────────────────────────────────
    // Map size scales with screen — never smaller than 72 px, never larger than 160 px.
    // Uses min(width, height)/6 so the map stays a reasonable proportion on both
    // small (720p with large GUI scale) and large (4K with small GUI scale) screens.
    private static final int MAP_SIZE_MIN = 72;
    private static final int MAP_SIZE_MAX = 160;
    private static final int MARGIN     = 6;    // from screen bottom-left
    private static final int PAD        = 4;    // border-to-content padding
    private static final int BG         = 0xCC0A0F0A;  // dark green-tinted background
    private static final int BORDER     = 0xFF1F4F1F;  // tactical green outline
    private static final int GRID       = 0x40406040;  // light grid lines
    private static final int SELF_COLOR = 0xFF80FF80;
    private static final int ALLY_COLOR = 0xFF4090FF;
    private static final int DEAD_COLOR = 0xFF555555;

    /**
     * Renders the minimap if the conditions are met. Safe to call every frame.
     *
     * @param g       graphics context
     * @param data    local player's session data (may be null)
     * @param sw      screen width
     * @param sh      screen height
     */
    public static void render(GuiGraphics g, PlayerWaveData data, int sw, int sh) {
        if (data == null) return;
        if (!data.isInPvp()) return;
        Location loc = data.getCurrentLocation();
        if (loc == null) return;
        if (!loc.isMinimapEnabled()) return;
        if (!loc.hasBbox()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos low  = loc.getBboxLowCorner();
        BlockPos high = loc.getBboxHighCorner();
        if (low == null || high == null) return;

        // Adaptive map size — scales with the smaller screen dimension.
        int mapSize = Math.max(MAP_SIZE_MIN, Math.min(MAP_SIZE_MAX, Math.min(sw, sh) / 6));

        // Pixel rect of the minimap on screen (bottom-left corner anchor)
        int x0 = MARGIN;
        int y0 = sh - MARGIN - mapSize;
        int x1 = x0 + mapSize;
        int y1 = y0 + mapSize;

        // Background + border
        g.fill(x0, y0, x1, y1, BG);
        // 2-pixel border by overdrawing 4 thin strips
        g.fill(x0,     y0,     x1,     y0 + 1, BORDER);
        g.fill(x0,     y1 - 1, x1,     y1,     BORDER);
        g.fill(x0,     y0,     x0 + 1, y1,     BORDER);
        g.fill(x1 - 1, y0,     x1,     y1,     BORDER);

        // World-space extents (top-down: X→X, Z→Y)
        int wx = high.getX() - low.getX() + 1;
        int wz = high.getZ() - low.getZ() + 1;
        if (wx <= 0 || wz <= 0) return;

        // Content area inside padding
        int innerW = mapSize - 2 * PAD;
        int innerH = mapSize - 2 * PAD;
        int cx0 = x0 + PAD;
        int cy0 = y0 + PAD;

        // Light grid (3×3)
        for (int i = 1; i < 3; i++) {
            int gx = cx0 + innerW * i / 3;
            int gy = cy0 + innerH * i / 3;
            g.fill(gx, cy0, gx + 1, cy0 + innerH, GRID);
            g.fill(cx0, gy, cx0 + innerW, gy + 1, GRID);
        }

        // Helper: world (x, z) → screen pixel (returns null if outside bbox)
        // Self position
        renderTeammates(g, mc, low, wx, wz, cx0, cy0, innerW, innerH);

        // Local player marker — bigger, on top of everything
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        renderDot(g, low, wx, wz, cx0, cy0, innerW, innerH, px, pz, SELF_COLOR, 3);

        // Facing arrow — 6-pixel line from the dot in the direction the player faces.
        // Minecraft yaw convention: 0=south (+Z), 90=west (-X), 180=north (-Z), 270/-90=east (+X).
        // Top-down map convention: screen +X = world +X, screen +Y = world +Z.
        // So world-delta → screen-delta is identity:
        //   dx_world = -sin(yaw_rad)
        //   dz_world =  cos(yaw_rad)
        float yaw = mc.player.getYRot();
        int sx = (int) Math.round(cx0 + (px - low.getX()) / (double) wx * innerW);
        int sy = (int) Math.round(cy0 + (pz - low.getZ()) / (double) wz * innerH);
        double yawRad = Math.toRadians(yaw);
        double dx = -Math.sin(yawRad);
        double dz =  Math.cos(yawRad);
        int tipX = (int) Math.round(sx + dx * 6);
        int tipY = (int) Math.round(sy + dz * 6);
        plotLine(g, sx, sy, tipX, tipY, SELF_COLOR);
    }

    private static void renderTeammates(GuiGraphics g, Minecraft mc, BlockPos low,
                                         int wx, int wz, int cx0, int cy0,
                                         int innerW, int innerH) {
        java.util.UUID myUuid = mc.player != null ? mc.player.getUUID() : null;
        for (ClientTeammatesManager.PlayerEntry e : ClientTeammatesManager.getPlayers()) {
            // Skip self — drawn separately with arrow
            if (myUuid != null && e.uuid().equals(myUuid)) continue;
            int color = e.alive() ? ALLY_COLOR : DEAD_COLOR;
            renderDot(g, low, wx, wz, cx0, cy0, innerW, innerH, e.x(), e.z(), color, 2);
        }
    }

    /** Plots a small filled square centred on the projected world position, clamped to inside the map. */
    private static void renderDot(GuiGraphics g, BlockPos low, int wx, int wz,
                                   int cx0, int cy0, int innerW, int innerH,
                                   double worldX, double worldZ, int color, int radius) {
        double rx = (worldX - low.getX()) / (double) wx;
        double rz = (worldZ - low.getZ()) / (double) wz;
        if (rx < 0 || rx > 1 || rz < 0 || rz > 1) return; // outside bbox — don't render
        int px = (int) Math.round(cx0 + rx * innerW);
        int py = (int) Math.round(cy0 + rz * innerH);
        g.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
    }

    /** Simple Bresenham line plot for the heading indicator. */
    private static void plotLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int safety = 64;
        while (safety-- > 0) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
