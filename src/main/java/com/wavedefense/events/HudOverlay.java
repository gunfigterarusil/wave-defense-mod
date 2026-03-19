package com.wavedefense.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.gui.ClientPlayerDataManager;
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
            mainLine = "§a§lПЕРЕМОГА! §r§7Вихід через §e" + playerData.getVictoryCountdownSec() + " §7сек";
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
                    mainLine = String.format("§e🛒 ЧАС ПОКУПОК: %d сек | Раунд %d/%d", pvpTimer, pvpRound, pvpTotal);
                else if ("ACTIVE".equals(pvpPhase))
                    mainLine = String.format("§c⚔ РАУНД %d/%d — БИЙТЕСЬ!", pvpRound, pvpTotal);
                else
                    mainLine = "§7Чекаємо гравців...";
            } else {
                mainLine = "§e" + I18n.get("wavedefense.hud.wave_active");
            }

            int mobsLeft = playerData.getMobsRemaining();
            if (mobsLeft > 0) {
                mobLine = location.isPvp()
                    ? "§cВорогів залишилось: §f" + mobsLeft
                    : I18n.get("wavedefense.hud.mobs_left", mobsLeft);
            }
        }

        // ── Оцінка ширини блоку ───────────────────────────────────────
        int estimatedW = 240;
        // Уточнюємо за реальними рядками
        int w1 = mc.font.width(locationLabel) + 10;
        int w2 = mainLine != null ? mc.font.width(mainLine) + 10 : 0;
        int w3 = mobLine  != null ? mc.font.width(mobLine)  + 10 : 0;
        estimatedW = Math.max(estimatedW, Math.max(w1, Math.max(w2, w3)));
        if (showProgressBar) estimatedW = Math.max(estimatedW, 200 + 10);

        // ── Позиція з HudLayout ───────────────────────────────────────
        HudLayout hl = HudLayout.get();
        int blockX = hl.resolveX(screenW, estimatedW);
        int blockY = hl.resolveY(screenH, 70); // 70px макс висота блоку

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int lineH = mc.font.lineHeight;
        int curY  = blockY;

        // ── Локація ───────────────────────────────────────────────────
        drawLine(graphics, mc, locationLabel, blockX, estimatedW, curY, 0x80000000, 0xFFFFFF);
        curY += lineH + 6;

        // ── Основний рядок ────────────────────────────────────────────
        if (mainLine != null) {
            int bgColor = hasVictory ? 0xCC007700
                : (playerData.isTimerActive() ? 0x80000000 : (location.isPvp() ? 0xDD440000 : 0xDD00007A));
            drawLine(graphics, mc, mainLine, blockX, estimatedW, curY, bgColor, 0xFFFFFF);

            if (!playerData.isTimerActive() && !hasVictory) {
                // Рамка для "хвиля активна"
                int bx = blockX - 1; int by = curY - 3;
                int bw = estimatedW + 2; int bh = lineH + 6;
                int brdC = 0xFFFFAA00;
                graphics.fill(bx, by - 1,  bx + bw, by,      brdC);
                graphics.fill(bx, by + bh, bx + bw, by+bh+1, brdC);
                graphics.fill(bx - 1, by,  bx,       by + bh, brdC);
                graphics.fill(bx + bw, by, bx+bw+1,  by + bh, brdC);
            }
            curY += lineH + 4;

            // Прогрес-бар
            if (showProgressBar) {
                int barW = estimatedW;
                int barH = 4;
                graphics.fill(blockX,           curY, blockX + barW,           curY + barH, 0xFF333333);
                graphics.fill(blockX,           curY, blockX + (int)(barW * barProgress), curY + barH, 0xFF00CC00);
                graphics.fill(blockX - 1, curY - 1, blockX + barW + 1, curY,         0xFF999999);
                graphics.fill(blockX - 1, curY+barH, blockX+barW+1,   curY+barH+1,  0xFF999999);
                graphics.fill(blockX - 1, curY,     blockX,           curY + barH,  0xFF999999);
                graphics.fill(blockX+barW, curY,    blockX+barW+1,    curY + barH,  0xFF999999);
                curY += barH + 6;
            }
        }

        // ── Мобів залишилось ─────────────────────────────────────────
        if (mobLine != null) {
            int mobsLeft = playerData.getMobsRemaining();
            int mobColor = mobsLeft > 10 ? 0xFF5555 : (mobsLeft > 5 ? 0xFFAA00 : 0x55FF55);
            drawLine(graphics, mc, mobLine, blockX, estimatedW, curY, 0xAA000000, mobColor);
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
