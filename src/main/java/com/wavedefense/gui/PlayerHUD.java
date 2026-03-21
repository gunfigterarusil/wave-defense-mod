package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

public class PlayerHUD {

    public static void render(GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerWaveData data = ClientPlayerDataManager.getPlayerData();
        if (data == null || !data.isInWave()) return;

        // ── Поінти (нижній правий) ─────────────────────────────────
        int points = data.getPlayerPoints(); // синхронізується з сервера через SyncPlayerDataPacket
        String pointsText = I18n.get("wavedefense.hud.points", points);
        int textWidth = mc.font.width(pointsText);
        g.drawString(mc.font, pointsText, width - textWidth - 10, height - 20, 0xFFFFFF);

        // ── Таймер (нижній правий, над поінтами) ──────────────────
        if (data.getVictoryCountdownSec() <= 0 && data.isTimerActive() && data.isShowTimer()) {
            String timerText = I18n.get("wavedefense.hud.next_wave_timer", data.getTimeUntilNextWave());
            int tw = mc.font.width(timerText);
            g.drawString(mc.font, timerText, width - tw - 10, height - 35, 0xFFFFFF);
        }

        // ── Тімейт-панель (лівий бік) ─────────────────────────────
        if (data.isShowTeammates()) {
            renderTeamPanel(g, mc);
        }
    }

    private static void renderTeamPanel(GuiGraphics g, Minecraft mc) {
        List<ClientTeammatesManager.PlayerEntry> players = ClientTeammatesManager.getPlayers();
        // Показуємо лише коли ≥ 2 гравці (при 1 гравці панель не потрібна)
        if (players.size() < 2) return;

        final int X       = 4;     // лівий край панелі
        final int NAME_W  = 52;    // ширина під нік (символів * 6px)
        final int BAR_W   = 50;    // ширина HP-бару
        final int BAR_H   = 5;     // висота HP-бару
        final int ROW_H   = 14;    // висота рядка
        final int PAD     = 2;     // відступ між ніком і баром

        int totalH = players.size() * ROW_H;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int startY = screenH / 2 - totalH / 2;

        for (int i = 0; i < players.size(); i++) {
            ClientTeammatesManager.PlayerEntry e = players.get(i);
            int y = startY + i * ROW_H;

            boolean isMe  = mc.player != null && mc.player.getUUID().equals(e.uuid());
            boolean alive = e.alive();

            // ── Фон рядка ─────────────────────────────────────────
            int rowW = NAME_W + PAD + BAR_W;
            g.fill(X - 1, y - 1, X + rowW + 1, y + ROW_H - 2, isMe ? 0x44004400 : 0x44000000);

            // ── Нік (обрізаний) ───────────────────────────────────
            String prefix = isMe ? "§a" : (alive ? "§f" : "§8");
            String name   = prefix + truncate(e.name(), 8);
            g.drawString(mc.font, name, X, y + 1, 0xFFFFFF, false);

            // ── HP бар ────────────────────────────────────────────
            int bx = X + NAME_W + PAD;
            int by = y + (ROW_H - BAR_H) / 2;

            // Фон
            g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF1A1A1A);

            if (alive && e.maxHp() > 0) {
                float ratio  = Math.min(1f, (float) e.hp() / e.maxHp());
                int filled   = Math.max(1, (int)(BAR_W * ratio));
                int col = ratio > 0.5f ? 0xFF3CC93C
                        : ratio > 0.25f ? 0xFFFFA020
                        : 0xFFE03030;
                g.fill(bx, by, bx + filled, by + BAR_H, col);
            } else if (!alive) {
                // Червоний крест замість бару
                g.fill(bx, by, bx + BAR_W, by + BAR_H, 0x88440000);
                int cx2 = bx + BAR_W / 2;
                g.fill(cx2 - 1, by,     cx2 + 1, by + BAR_H, 0xFFCC2222);
                g.fill(bx,      by + 1, bx + BAR_W, by + 3,  0xFFCC2222);
            }

            // Рамка бару (1px)
            g.fill(bx,            by,          bx + BAR_W, by + 1,        0xFF555555);
            g.fill(bx,            by + BAR_H - 1, bx + BAR_W, by + BAR_H, 0xFF555555);
            g.fill(bx,            by,          bx + 1,     by + BAR_H,    0xFF555555);
            g.fill(bx + BAR_W - 1, by,         bx + BAR_W, by + BAR_H,   0xFF555555);
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 1) + "…";
    }
}
