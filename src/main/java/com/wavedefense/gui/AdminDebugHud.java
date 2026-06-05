package com.wavedefense.gui;

/**
 * 1.16.5 STUB — AdminDebugHud excluded from this port.
 *
 * <p>The 1.20.1 version is an F4-toggleable debug overlay (v0.2.65 polish).
 * Keeping only the public API surface that KeyBindings / ClientEventHandler
 * reference so the port compiles. All operations are no-ops.
 */
public class AdminDebugHud {
    public static boolean visible = false;

    /** Stub: 1.16.5 render no-op (the real implementation uses GuiGraphics). */
    public static void render(com.mojang.blaze3d.matrix.MatrixStack g, int w, int h) {
        // no-op
    }

    /** Stub for TPS probe. */
    public static class TickRateProbe {
        public static void recordTick() { /* no-op */ }
    }
}
