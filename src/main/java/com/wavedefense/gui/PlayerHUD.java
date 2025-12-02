package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;

public class PlayerHUD {
    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        UUID playerId = minecraft.player.getUUID();
        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(playerId);

        if (data != null && data.isInWave()) {
            int points = data.getCurrentLocation().getPlayerPoints(playerId);
            String pointsText = "Очки: " + points;
            int textWidth = minecraft.font.width(pointsText);
            graphics.drawString(minecraft.font, pointsText, width - textWidth - 10, height - 20, 0xFFFFFF);

            if (data.isTimerActive() && data.isShowTimer()) {
                String timerText = "Наступна хвиля: " + data.getTimeUntilNextWave();
                int timerWidth = minecraft.font.width(timerText);
                graphics.drawString(minecraft.font, timerText, width - timerWidth - 10, height - 35, 0xFFFFFF);
            }
        }
    }
}
