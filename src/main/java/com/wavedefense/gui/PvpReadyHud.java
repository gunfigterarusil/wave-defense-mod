package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-centre HUD overlay shown only during PvP READY_CHECK phase.
 *
 * <p>Layout:
 * <pre>
 *     ┌──────────────────────────────────────────────┐
 *     │ §eReady up! Press §6§lR§e to confirm — 3/5 │
 *     │ §aRed: ●●○  §9Blue: ●○            §eAuto-start in 25s│
 *     └──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Rendered from {@link com.wavedefense.events.ClientEventHandler#onRenderGuiOverlay}
 * after the regular {@link PlayerHUD#render}. Self-guards: returns immediately
 * if phase != READY_CHECK.
 *
 * <p>v0.2.62.
 */
public class PvpReadyHud {

    private static final int BG_COLOR     = 0xC0000000; // semi-transparent black
    private static final int BORDER_COLOR = 0xFF606060;
    private static final int PAD          = 6;

    public static void render(MatrixStack g, int width, int height) {
        if (!"READY_CHECK".equals(ClientPvpStateManager.getPhase())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Set<String> ready = ClientPvpStateManager.getReadyNames();
        List<ClientPvpStateManager.PlayerRow> players = ClientPvpStateManager.getPlayers();
        if (players.isEmpty()) return;

        // ── Line 1: title + count ─────────────────────────────────────
        int total = players.size();
        int readyCount = ready.size();
        boolean isMeReady = ClientPvpStateManager.isMeReady();
        String title = I18n.get(isMeReady
            ? "wavedefense.hud.ready_press_r_off"
            : "wavedefense.hud.ready_press_r");
        String count = I18n.get("wavedefense.hud.ready_count", readyCount, total);
        String line1 = title + " §7— " + count;

        // ── Line 2: per-team dots ─────────────────────────────────────
        // Group players by team, render: §<color>TeamName: ●●○ (one bullet per player)
        Map<String, StringBuilder> teamDots = new LinkedHashMap<>();
        for (ClientPvpStateManager.PlayerRow p : players) {
            StringBuilder sb = teamDots.computeIfAbsent(p.team, k -> new StringBuilder());
            sb.append(ready.contains(p.name) ? "§a● " : "§7○ ");
        }
        StringBuilder line2sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, StringBuilder> e : teamDots.entrySet()) {
            if (!first) line2sb.append("  §8| ");
            line2sb.append("§f").append(e.getKey()).append("§7: ").append(e.getValue());
            first = false;
        }
        String line2 = line2sb.toString();

        // ── Line 3: auto-start timer ──────────────────────────────────
        int timer = ClientPvpStateManager.getTimerSeconds();
        String line3 = timer > 0
            ? I18n.get("wavedefense.hud.ready_auto_start", timer)
            : I18n.get("wavedefense.hud.ready_no_timeout");

        // ── Compute bounding box (max of 3 line widths) ───────────────
        int w1 = mc.font.width(new StringTextComponent(line1));
        int w2 = mc.font.width(new StringTextComponent(line2));
        int w3 = mc.font.width(new StringTextComponent(line3));
        int maxW = Math.max(w1, Math.max(w2, w3));
        int boxW = maxW + 2 * PAD;
        int boxH = mc.font.lineHeight * 3 + 4 + 2 * PAD;
        int x = (width - boxW) / 2;
        int y = height / 5; // 20% from top — above hotbar/crosshair

        // Background + border
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + boxW, y + boxH, BG_COLOR);
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + boxW, y + 1, BORDER_COLOR);
        com.wavedefense.gui.GuiCompat.fill(g, x, y + boxH - 1, x + boxW, y + boxH, BORDER_COLOR);
        com.wavedefense.gui.GuiCompat.fill(g, x, y, x + 1, y + boxH, BORDER_COLOR);
        com.wavedefense.gui.GuiCompat.fill(g, x + boxW - 1, y, x + boxW, y + boxH, BORDER_COLOR);

        // Lines
        int lineY = y + PAD;
        com.wavedefense.gui.GuiCompat.drawString(g, mc.font, line1, x + PAD, lineY, 0xFFFFFF);
        lineY += mc.font.lineHeight + 2;
        com.wavedefense.gui.GuiCompat.drawString(g, mc.font, line2, x + PAD, lineY, 0xFFFFFF);
        lineY += mc.font.lineHeight + 2;
        com.wavedefense.gui.GuiCompat.drawString(g, mc.font, line3, x + PAD, lineY, 0xFFFFFF);
    }
}
