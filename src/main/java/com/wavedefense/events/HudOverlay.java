package com.wavedefense.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

        PlayerWaveData playerData = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (playerData == null || !playerData.isInWave()) return;

        Location location = playerData.getCurrentLocation();
        if (location == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // --- Центральний блок (таймер або статус) ---
        int centerTopY = 10;

        // "Локація: назва" — над таймером/статусом
        String locationLabel = "§7Локація: §f" + location.getName();
        int locLabelX = (screenWidth - mc.font.width(locationLabel)) / 2;
        graphics.fill(locLabelX - 5, centerTopY - 2, locLabelX + mc.font.width(locationLabel) + 5, centerTopY + mc.font.lineHeight + 2, 0x80000000);
        graphics.drawString(mc.font, locationLabel, locLabelX, centerTopY, 0xFFFFFF);

        centerTopY += mc.font.lineHeight + 6;

        if (playerData.isShowTimer() && playerData.isTimerActive()) {
            int timeLeft = playerData.getTimeUntilNextWave();
            if (timeLeft > 0) {
                int minutes = timeLeft / 60;
                int seconds = timeLeft % 60;
                String timerText = String.format("§a%d:%02d до наступної хвилі", minutes, seconds);
                int timerX = (screenWidth - mc.font.width(timerText)) / 2;
                graphics.fill(timerX - 5, centerTopY - 2, timerX + mc.font.width(timerText) + 5, centerTopY + mc.font.lineHeight + 2, 0x80000000);
                graphics.drawString(mc.font, timerText, timerX, centerTopY, 0xFFFFFF);

                if (playerData.getCurrentWave() > 0 && playerData.getCurrentWave() <= location.getWaves().size()) {
                    int totalTime = location.getWaves().get(playerData.getCurrentWave() - 1).getTimeBetweenWaves();
                    float progress = totalTime > 0 ? 1.0f - ((float) timeLeft / totalTime) : 0f;
                    int barWidth = 200;
                    int barH = 4;
                    int barX = (screenWidth - barWidth) / 2;
                    int barY = centerTopY + mc.font.lineHeight + 4;
                    graphics.fill(barX, barY, barX + barWidth, barY + barH, 0xFF333333);
                    graphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barH, 0xFF00CC00);
                    graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFF999999);
                    graphics.fill(barX - 1, barY + barH, barX + barWidth + 1, barY + barH + 1, 0xFF999999);
                    graphics.fill(barX - 1, barY, barX, barY + barH, 0xFF999999);
                    graphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barH, 0xFF999999);
                }
            }
        } else if (!playerData.isTimerActive() && playerData.getCurrentWave() > 0) {
            // "Хвиля в процесі" — жовтий текст на темно-синьому фоні
            String activeText = "§e⚔ ХВИЛЯ В ПРОЦЕСІ ⚔";
            int activeX = (screenWidth - mc.font.width(activeText)) / 2;
            graphics.fill(activeX - 8, centerTopY - 3, activeX + mc.font.width(activeText) + 8, centerTopY + mc.font.lineHeight + 3, 0xDD00007A);
            graphics.fill(activeX - 9, centerTopY - 4, activeX + mc.font.width(activeText) + 9, centerTopY - 3, 0xFFFFAA00);
            graphics.fill(activeX - 9, centerTopY + mc.font.lineHeight + 3, activeX + mc.font.width(activeText) + 9, centerTopY + mc.font.lineHeight + 4, 0xFFFFAA00);
            graphics.fill(activeX - 9, centerTopY - 3, activeX - 8, centerTopY + mc.font.lineHeight + 3, 0xFFFFAA00);
            graphics.fill(activeX + mc.font.width(activeText) + 8, centerTopY - 3, activeX + mc.font.width(activeText) + 9, centerTopY + mc.font.lineHeight + 3, 0xFFFFAA00);
            graphics.drawString(mc.font, activeText, activeX, centerTopY, 0xFFFFFF);

            // Лічильник мобів під статусом хвилі
            int mobsLeft = playerData.getMobsRemaining();
            if (mobsLeft > 0) {
                centerTopY += mc.font.lineHeight + 5;
                // Колір залежить від кількості: >10 червоний, 6-10 жовтий, 1-5 зелений
                int mobColor = mobsLeft > 10 ? 0xFF5555 : (mobsLeft > 5 ? 0xFFAA00 : 0x55FF55);
                String mobText = "Мобів залишилось: " + mobsLeft;
                int mobX = (screenWidth - mc.font.width(mobText)) / 2;
                graphics.fill(mobX - 5, centerTopY - 2, mobX + mc.font.width(mobText) + 5, centerTopY + mc.font.lineHeight + 2, 0xAA000000);
                graphics.drawString(mc.font, mobText, mobX, centerTopY, mobColor);
            }
        }

        RenderSystem.disableBlend();
    }

    // handleClick більше не потрібний — кнопки перенесено в інвентар
    public static boolean handleClick(double mouseX, double mouseY) {
        return false;
    }
}
