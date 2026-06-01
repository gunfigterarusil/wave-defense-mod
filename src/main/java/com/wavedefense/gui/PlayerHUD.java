package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Map;

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

        // ── Лічильник хвиль (нижній правий, над таймером) ────────
        com.wavedefense.data.Location currentLoc = data.getCurrentLocation();
        if (currentLoc != null
                && currentLoc.getMode() == com.wavedefense.data.LocationMode.PVE
                && data.getCurrentWave() > 0) {
            int totalWaves = currentLoc.getTotalWaves();
            String waveText = I18n.get("wavedefense.hud.wave_counter",
                    data.getCurrentWave(), totalWaves);
            int ww = mc.font.width(waveText);
            g.drawString(mc.font, waveText, width - ww - 10, height - 50, 0xFFE0A020);
        }

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

        // ── G7 fix: CtP/KotH overlay (top-centre) ─────────────────
        if (ClientCtpStateManager.isActive()) {
            renderCtpOverlay(g, mc, width);
        }

        // ── B2: DM per-player kill leaderboard (top-right) ─────────
        if (data.isInPvp() && currentLoc != null && currentLoc.isDeathmatch()) {
            renderDmLeaderboard(g, mc, width, mc.player.getName().getString(), currentLoc.getDmKillsToWin());
        }
    }

    /**
     * B2: top-right 3-line panel for DM mode showing this player's kills,
     * the current leader, and the kill target.
     */
    private static void renderDmLeaderboard(GuiGraphics g, Minecraft mc, int width,
                                             String myName, int killTarget) {
        List<ClientPvpStateManager.PlayerRow> players = ClientPvpStateManager.getPlayers();
        if (players.isEmpty()) return;

        int myKills = 0;
        ClientPvpStateManager.PlayerRow leader = null;
        for (ClientPvpStateManager.PlayerRow p : players) {
            if (p.name.equals(myName)) myKills = p.kills;
            if (leader == null || p.kills > leader.kills) leader = p;
        }

        String line1 = I18n.get("wavedefense.hud.dm.your_kills", myKills);
        String line2 = leader != null
            ? I18n.get("wavedefense.hud.dm.top", trunc(leader.name, 12), leader.kills)
            : "";
        String line3 = I18n.get("wavedefense.hud.dm.target", killTarget);

        int w = Math.max(mc.font.width(line1), Math.max(mc.font.width(line2), mc.font.width(line3)));
        int x = width - w - 10;
        int y = 8;

        // Background panel
        g.fill(x - 4, y - 2, x + w + 4, y + 30, 0x80000000);
        g.drawString(mc.font, line1, x, y,      0xFFE0A020);
        g.drawString(mc.font, line2, x, y + 10, 0xFFCFCFCF);
        g.drawString(mc.font, line3, x, y + 20, 0xFF80FF80);
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
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

    /**
     * Renders the Capture the Point / King of the Hill overlay at the top-centre of the screen.
     * Shows each capture point name, its current owner (colour-coded), a capture progress bar,
     * and the team score (or round timer if the location is in timer mode).
     */
    private static void renderCtpOverlay(GuiGraphics g, Minecraft mc, int screenW) {
        Map<String, String>  owners   = ClientCtpStateManager.getPointOwners();
        Map<String, String>  names    = ClientCtpStateManager.getPointNames();
        Map<String, Integer> progress = ClientCtpStateManager.getCaptureProgress();
        Map<String, Integer> timeTicks= ClientCtpStateManager.getCaptureTimeTicks();
        Map<String, Integer> score    = ClientCtpStateManager.getObjectiveScore();
        int scoreToWin   = ClientCtpStateManager.getScoreToWin();
        int ticksLeft    = ClientCtpStateManager.getRoundTicksLeft();

        if (owners.isEmpty()) return;

        final int ENTRY_W  = 80;
        final int BAR_H    = 4;
        final int ROW_H    = 18;
        final int GAP      = 4;
        int count  = owners.size();
        int totalW = count * ENTRY_W + (count - 1) * GAP;
        int startX = (screenW - totalW) / 2;
        int startY = 6;

        int col = 0;
        for (String pointId : owners.keySet()) {
            int x = startX + col * (ENTRY_W + GAP);
            String owner      = owners.getOrDefault(pointId, "");
            String displayName= names.getOrDefault(pointId, pointId);
            int    prog       = progress.getOrDefault(pointId, 0);   // 0-100
            int    maxTicks   = timeTicks.getOrDefault(pointId, 1);

            // Background panel
            g.fill(x - 2, startY - 1, x + ENTRY_W + 2, startY + ROW_H + 1, 0x88000000);

            // Point label
            String label = truncate(displayName, 10);
            int lw = mc.font.width(label);
            g.drawString(mc.font, label, x + (ENTRY_W - lw) / 2, startY, 0xFFFFFFFF, false);

            // Owner name
            int ownerColor = owner.isEmpty() ? 0xFFAAAAAA : teamColor(owner);
            String ownerLabel = owner.isEmpty() ? I18n.get("wavedefense.hud.ctp_neutral") : truncate(owner, 8);
            int ow = mc.font.width(ownerLabel);
            g.drawString(mc.font, ownerLabel, x + (ENTRY_W - ow) / 2, startY + 8, ownerColor, false);

            // Capture progress bar (only shown when being contested, i.e. prog > 0 and < 100)
            int barY = startY + ROW_H - BAR_H;
            g.fill(x, barY, x + ENTRY_W, barY + BAR_H, 0xFF222222);
            if (prog > 0) {
                int filled = (int)(ENTRY_W * (prog / 100.0f));
                g.fill(x, barY, x + filled, barY + BAR_H, 0xFFFFD700);
            }

            col++;
        }

        // Score / timer row below the capture points
        if (!score.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : score.entrySet()) {
                if (sb.length() > 0) sb.append("  ");
                int tc = teamColor(e.getKey());
                // Convert ARGB to §-code: approximate with nearest §colour
                String colCode = tc == 0xFFFF5555 ? "§c" : tc == 0xFF5555FF ? "§9" : tc == 0xFF55FF55 ? "§a" : "§f";
                sb.append(colCode).append(truncate(e.getKey(), 6)).append(" §f").append(e.getValue());
                if (scoreToWin > 0) sb.append("/").append(scoreToWin);
            }
            if (ticksLeft > 0) {
                int secs = ticksLeft / 20;
                sb.append("  §7").append(String.format("%d:%02d", secs / 60, secs % 60));
            }
            String scoreText = sb.toString();
            int sw = mc.font.width(scoreText);
            g.drawString(mc.font, scoreText, (screenW - sw) / 2, startY + ROW_H + 3, 0xFFFFFFFF, false);
        }
    }

    /** Returns a distinct colour for a team name (consistent across calls). */
    private static int teamColor(String team) {
        if (team == null || team.isBlank()) return 0xFFAAAAAA;
        // Map common team name patterns to colours; fall back to hash-based colour
        String lower = team.toLowerCase();
        if (lower.contains("red"))   return 0xFFFF5555;
        if (lower.contains("blue"))  return 0xFF5555FF;
        if (lower.contains("green")) return 0xFF55FF55;
        if (lower.contains("gold") || lower.contains("yellow")) return 0xFFFFD700;
        // Deterministic colour from hash
        int h = Math.abs(team.hashCode());
        int r = 100 + (h % 156);
        int grn = 100 + ((h >> 8) % 156);
        int b = 100 + ((h >> 16) % 156);
        return 0xFF000000 | (r << 16) | (grn << 8) | b;
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars - 1) + "…";
    }
}
