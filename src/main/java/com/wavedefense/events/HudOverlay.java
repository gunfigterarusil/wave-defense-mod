package com.wavedefense.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.gui.ClientCtpStateManager;
import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.gui.GuiTheme;
import com.wavedefense.gui.HudLayout;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
public class HudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        // The "enableHUD" config option existed since the mod's first release but was
        // never read — toggling it off did nothing. This is where it belongs.
        if (!com.wavedefense.config.WaveDefenseConfig.ENABLE_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // ── ВИПРАВЛЕНО: використовуємо клієнтський менеджер, не серверний waveManager ──
        PlayerWaveData playerData = ClientPlayerDataManager.getPlayerData();
        if (playerData == null || (!playerData.isInWave() && !playerData.isInPvp())) return;

        Location location = playerData.getCurrentLocation();
        if (location == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // ── Визначення victory-рядка ──────────────────────────────────
        boolean hasVictory = playerData.getVictoryCountdownSec() > 0;

        // ── Будуємо рядки HUD ─────────────────────────────────────────
        String locationLabel = "§7" + I18n.get("wavedefense.hud.location", location.getName());

        // Основний рядок (таймер / хвиля активна / PvP статус)
        String mainLine = null;
        boolean showProgressBar = false;
        float barProgress = 0f;

        // Мобів
        String mobLine = null;

        if (hasVictory) {
            mainLine = I18n.get("wavedefense.hud.victory_countdown", playerData.getVictoryCountdownSec());
        } else if (playerData.isShowTimer() && playerData.isTimerActive()) {
            int timeLeft = playerData.getTimeUntilNextWave();
            if (timeLeft > 0) {
                int minutes = timeLeft / 60;
                int seconds = timeLeft % 60;
                mainLine = "§a" + I18n.get("wavedefense.hud.next_wave", minutes, seconds);
                // Прогрес-бар
                if (playerData.getCurrentWave() > 0 && playerData.getCurrentWave() <= location.getWaves().size()) {
                    int totalTime = location.getWaves().get(playerData.getCurrentWave() - 1).getTimeBetweenWaves();
                    barProgress = totalTime > 0 ? 1.0f - ((float) timeLeft / totalTime) : 0f;
                    showProgressBar = true;
                }
            }
        } else if (!playerData.isTimerActive() && playerData.getCurrentWave() > 0) {
            if (location.isPvp()) {
                String pvpPhase = com.wavedefense.gui.ClientPvpStateManager.getPhase();
                int pvpRound    = com.wavedefense.gui.ClientPvpStateManager.getCurrentRound();
                int pvpTotal    = com.wavedefense.gui.ClientPvpStateManager.getTotalRounds();
                int pvpTimer    = com.wavedefense.gui.ClientPvpStateManager.getTimerSeconds();
                if ("BUY".equals(pvpPhase))
                    mainLine = I18n.get("wavedefense.pvp.buy_phase", pvpTimer, pvpRound, pvpTotal);
                else if ("ACTIVE".equals(pvpPhase))
                    mainLine = I18n.get("wavedefense.pvp.round_active", pvpRound, pvpTotal);
                else
                    mainLine = I18n.get("wavedefense.pvp.waiting");
            } else {
                mainLine = "§e" + I18n.get("wavedefense.hud.wave_active");
            }

            int mobsLeft = playerData.getMobsRemaining();
            if (mobsLeft > 0) {
                mobLine = location.isPvp()
                    ? I18n.get("wavedefense.hud.mobs_left", mobsLeft)
                    : I18n.get("wavedefense.hud.mobs_left", mobsLeft);
            }
        }

        // ── Ширина блоку — один прохід по всіх рядках ───────────────
        int estimatedW = 240;
        estimatedW = Math.max(estimatedW, mc.font.width(locationLabel) + 10);
        if (mainLine != null) estimatedW = Math.max(estimatedW, mc.font.width(mainLine) + 10);
        if (mobLine  != null) estimatedW = Math.max(estimatedW, mc.font.width(mobLine)  + 10);
        if (showProgressBar)  estimatedW = Math.max(estimatedW, 210);

        // ── Position from HudLayout ──────────────────────────────────
        HudLayout hl = HudLayout.get();
        int blockX = hl.resolveX(screenW, estimatedW);
        int blockY = hl.resolveY(screenH, 70); // 70px max block height

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int lineH = mc.font.lineHeight;
        int curY  = blockY;

        // ── Обчислюємо висоту блоку для зовнішнього panel ───────────────
        int blockH = lineH + 6;
        if (mainLine != null) blockH += lineH + 4;
        if (showProgressBar)  blockH += 4 + 6;
        if (mobLine  != null) blockH += lineH + 4;
        GuiTheme.panel(graphics, blockX - 3, blockY - 3, blockX + estimatedW + 3, blockY + blockH + 3);

        // ── Локація ───────────────────────────────────────────────────
        drawLine(graphics, mc, locationLabel, blockX, estimatedW, curY, 0x50000000, GuiTheme.TEXT_MUTED);
        curY += lineH + 6;

        // ── Основний рядок ────────────────────────────────────────────
        if (mainLine != null) {
            int bgColor = hasVictory ? 0xCC007700
                : (playerData.isTimerActive() ? 0x50000000 : (location.isPvp() ? 0xCC440000 : 0xCC00007A));
            drawLine(graphics, mc, mainLine, blockX, estimatedW, curY, bgColor, GuiTheme.TEXT);

            if (!playerData.isTimerActive() && !hasVictory) {
                // Рамка для "хвиля активна" — використовуємо ACCENT_ALT
                int bx = blockX - 1; int by = curY - 3;
                int bw = estimatedW + 2; int bh = lineH + 6;
                GuiTheme.outline(graphics, bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.ACCENT_ALT);
            }
            curY += lineH + 4;

            // Прогрес-бар — замінено на GuiTheme.progressBar
            if (showProgressBar) {
                GuiTheme.progressBar(graphics, blockX, curY, estimatedW, 4, barProgress, GuiTheme.STATUS_ACTIVE);
                curY += 4 + 6;
            }
        }

        // ── Мобів залишилось ─────────────────────────────────────────
        if (mobLine != null) {
            int mobsLeft = playerData.getMobsRemaining();
            int mobColor = mobsLeft > 5 ? GuiTheme.ACCENT
                         : mobsLeft > 1 ? GuiTheme.WARN
                         : GuiTheme.DANGER;
            drawLine(graphics, mc, mobLine, blockX, estimatedW, curY, 0xAA000000, mobColor);
        }

        RenderSystem.disableBlend();

        // ── CtP / KotH objective overlay ─────────────────────────────
        if (ClientCtpStateManager.isActive()) {
            renderCtpOverlay(graphics, mc, screenW, screenH);
        }
    }

    /** Renders the CtP / KotH HUD panel on the right side of the screen. */
    private static void renderCtpOverlay(GuiGraphics g, net.minecraft.client.Minecraft mc,
                                          int screenW, int screenH) {
        java.util.Map<String, String>  owners    = ClientCtpStateManager.getPointOwners();
        java.util.Map<String, String>  names     = ClientCtpStateManager.getPointNames();
        java.util.Map<String, Integer> capTicks  = ClientCtpStateManager.getCaptureTimeTicks(); // H-3
        java.util.Map<String, Integer> progress  = ClientCtpStateManager.getCaptureProgress();
        java.util.Map<String, Integer> scores    = ClientCtpStateManager.getObjectiveScore();
        int scoreToWin      = ClientCtpStateManager.getScoreToWin();
        int roundTicksLeft  = ClientCtpStateManager.getRoundTicksLeft();

        int lineH  = mc.font.lineHeight;
        int padX   = 5;
        int panelW = 140;
        int panelX = screenW - panelW - 5;
        int panelY = 48;

        // M-3: panel height must account for progress bars (each non-zero progress adds 4px bar + 2px gap)
        int activeProgressBars = (int) progress.values().stream().filter(v -> v != 0).count();
        int textRows = 1 + owners.size() + 1 + scores.size() + (roundTicksLeft > 0 ? 1 : 0); // title + points + separator + scores + timer
        int panelH = textRows * (lineH + 3) + activeProgressBars * 6 + 8;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        GuiTheme.panel(g, panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2);

        int y = panelY + 3;

        // Title row
        g.drawString(mc.font, "§b§l" + I18n.get("wavedefense.ctp.hud.title"), panelX + padX, y, GuiTheme.ACCENT);
        y += lineH + 3;

        // Per-point ownership + progress bar
        for (java.util.Map.Entry<String, String> e : owners.entrySet()) {
            String pointId = e.getKey();
            String owner   = e.getValue();
            int prog       = progress.getOrDefault(pointId, 0);

            // L-4: truncate long display names to fit in 140px panel
            String rawLabel = names.getOrDefault(pointId, pointId.substring(0, Math.min(8, pointId.length())));
            String pointLabel = rawLabel.length() > 12 ? rawLabel.substring(0, 11) + "…" : rawLabel;
            String ownerLabel = owner != null ? "§a" + owner : "§7---";
            String display = "§7" + pointLabel + ": " + ownerLabel;
            g.drawString(mc.font, display, panelX + padX, y, GuiTheme.TEXT_MUTED);
            y += lineH + 1;

            // Progress bar — H-3: use server-supplied capTicks as denominator
            if (prog != 0) {
                int cap = capTicks.getOrDefault(pointId, 200);
                float frac = cap > 0 ? Math.min(1f, (float) Math.abs(prog) / cap) : 0f;
                int barW = panelW - padX * 2;
                int barColor = prog > 0 ? 0xFF4488FF : 0xFFFF4444;
                g.fill(panelX + padX, y, panelX + padX + (int)(barW * frac), y + 2, barColor);
                g.fill(panelX + padX, y, panelX + padX + barW, y + 2, 0x40FFFFFF);
                y += 4 + 2;
            } else {
                y += 2;
            }
        }

        // Separator
        g.fill(panelX + padX, y, panelX + panelW - padX, y + 1, 0x60AAAAAA);
        y += 4;

        // Team scores
        for (java.util.Map.Entry<String, Integer> e : scores.entrySet()) {
            String line;
            if (scoreToWin > 0) {
                line = "§e" + e.getKey() + "§7: §f" + e.getValue() + "§8/§7" + scoreToWin;
            } else {
                line = "§e" + e.getKey() + "§7: §f" + e.getValue();
            }
            g.drawString(mc.font, line, panelX + padX, y, GuiTheme.TEXT);
            y += lineH + 3;
        }

        // Timer (if in timer mode)
        if (roundTicksLeft > 0) {
            int totalSec = roundTicksLeft / 20;
            int hrs = totalSec / 3600;
            int min = (totalSec % 3600) / 60;
            int sec = totalSec % 60;
            // M-6 fix: show hours component when round duration exceeds 59 minutes
            String timerLine = "§c⏱ " + (hrs > 0
                ? String.format("%d:%02d:%02d", hrs, min, sec)
                : String.format("%d:%02d", min, sec));
            g.drawString(mc.font, timerLine, panelX + padX, y, GuiTheme.WARN);
        }

        RenderSystem.disableBlend();
    }

    private static void drawLine(GuiGraphics g, Minecraft mc, String text,
                                  int blockX, int blockW, int y, int bg, int color) {
        g.fill(blockX, y - 2, blockX + blockW, y + mc.font.lineHeight + 2, bg);
        // По центру блоку
        int textX = blockX + (blockW - mc.font.width(text)) / 2;
        g.drawString(mc.font, text, textX, y, color);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        return false;
    }
}
