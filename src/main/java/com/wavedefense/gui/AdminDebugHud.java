package com.wavedefense.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * F4-toggle admin debug overlay — F3-style left-aligned dark info panel.
 *
 * <p>Only renders when:
 * <ul>
 *   <li>{@link #visible} is true (toggled by hotkey F4)</li>
 *   <li>Local player has op permission level &gt;= 2</li>
 *   <li>{@code mc.options.hideGui} is false</li>
 * </ul>
 *
 * <p>Lines:
 * <pre>
 *  WaveDefense [DEBUG]                  F4 to hide
 *  Tick: 20.0/s (target 20)             [LAGGY] when below
 *  Last PvP sync: 12 ms ago             updated on packet rcv
 *  Locations: 3 active PvP, 1 active PvE
 *  This loc: ready_check (READY 2/4, 25s left)
 *  My team: Red, X 105 Y 64 Z -42
 * </pre>
 *
 * <p>v0.2.65.
 */
public class AdminDebugHud {

    private static final int BG_COLOR     = 0xB0000000;
    private static final int BORDER_COLOR = 0xFF505050;
    private static final int TEXT_COLOR   = 0xFFE0E0E0;
    private static final int PAD          = 4;

    /** Toggled by F4 keybind (see {@link com.wavedefense.events.KeyBindings}). */
    public static boolean visible = false;

    public static void render(GuiGraphics g, int width, int height) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // Op-level gate — same threshold as AdminMenuScreen access
        if (!mc.player.hasPermissions(2)) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§e§lWaveDefense §7[DEBUG]   §8F4 to hide");

        // ── Tick rate (client-side estimate via System.nanoTime ring buffer) ─
        double tps = TickRateProbe.getTps();
        String tpsTag = tps >= 19.0 ? "§a" : (tps >= 15.0 ? "§e" : "§c");
        lines.add(String.format("Tick: %s%.1f§r/s (target 20)", tpsTag, tps));

        // ── PvP sync freshness ─────────────────────────────────────────
        long lastSyncAgo = ClientPvpStateManager.getLastUpdateAgoMs();
        if (lastSyncAgo >= 0) {
            String tag = lastSyncAgo < 1000 ? "§a" : (lastSyncAgo < 5000 ? "§e" : "§c");
            lines.add(String.format("Last PvP sync: %s%dms§r ago", tag, lastSyncAgo));
        } else {
            lines.add("§7Last PvP sync: §8never");
        }

        // ── Current location summary ───────────────────────────────────
        String loc = ClientPvpStateManager.getLocation();
        if (loc != null && !loc.isEmpty()) {
            String phase = ClientPvpStateManager.getPhase();
            int t = ClientPvpStateManager.getTimerSeconds();
            int ready = ClientPvpStateManager.getReadyNames().size();
            int players = ClientPvpStateManager.getPlayers().size();
            String phaseTag = "READY_CHECK".equals(phase) ? "§e" : ("ACTIVE".equals(phase) ? "§a" : "§7");
            lines.add(String.format("This loc: §f%s§r %s%s§r (R %d/%d, %ds left)",
                loc, phaseTag, phase, ready, players, t));
            String myTeam = ClientPvpStateManager.getMyTeam();
            if (myTeam != null && !myTeam.isEmpty()) {
                net.minecraft.core.BlockPos pos = mc.player.blockPosition();
                lines.add(String.format("My team: §f%s§r  §7X %d Y %d Z %d",
                    myTeam, pos.getX(), pos.getY(), pos.getZ()));
            }
        } else {
            lines.add("§7This loc: §8(not in a PvP location)");
        }

        // ── Memory + FPS (cheap quick win) ─────────────────────────────
        long heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long heapMax = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int fps = mc.getFps();
        lines.add(String.format("§7Heap §f%d§7/%dMB  FPS §f%d", heap, heapMax, fps));

        // ── Compute width as max line width + padding ──────────────────
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, mc.font.width(l));
        int boxW = maxW + 2 * PAD;
        int boxH = lines.size() * (mc.font.lineHeight + 1) + 2 * PAD - 1;
        int x = 4;
        int y = 4;

        // Background + border
        g.fill(x, y, x + boxW, y + boxH, BG_COLOR);
        g.fill(x, y, x + boxW, y + 1, BORDER_COLOR);
        g.fill(x, y + boxH - 1, x + boxW, y + boxH, BORDER_COLOR);
        g.fill(x, y, x + 1, y + boxH, BORDER_COLOR);
        g.fill(x + boxW - 1, y, x + boxW, y + boxH, BORDER_COLOR);

        // Lines
        int ly = y + PAD;
        for (String l : lines) {
            g.drawString(mc.font, l, x + PAD, ly, TEXT_COLOR);
            ly += mc.font.lineHeight + 1;
        }
    }

    /** Simple client-side TPS probe — averages frame intervals over the last
     *  ~1s using a small ring buffer. Not exact (we measure FRAME pace, not
     *  server tick) but close enough for "is the world ticking smoothly". */
    public static class TickRateProbe {
        private static final int SAMPLES = 20;
        private static final long[] frameTimesNs = new long[SAMPLES];
        private static int index = 0;
        private static long lastNs = 0L;

        public static void recordTick() {
            long now = System.nanoTime();
            if (lastNs > 0) {
                frameTimesNs[index] = now - lastNs;
                index = (index + 1) % SAMPLES;
            }
            lastNs = now;
        }

        public static double getTps() {
            long sum = 0; int n = 0;
            for (long t : frameTimesNs) { if (t > 0) { sum += t; n++; } }
            if (n == 0) return 20.0;
            double avgNs = (double) sum / n;
            return Math.min(20.0, 1_000_000_000.0 / avgNs);
        }
    }
}
