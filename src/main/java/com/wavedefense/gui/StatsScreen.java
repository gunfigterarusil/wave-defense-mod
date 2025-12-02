package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StatsScreen extends Screen {
    private final PlayerWaveData playerData;

    public StatsScreen(PlayerWaveData playerData) {
        super(Component.literal("Статистика"));
        this.playerData = playerData;
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = 40;

        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), centerX, 20, 0xFFFFFF);

        Location location = playerData.getCurrentLocation();
        if (location != null && minecraft.player != null) {
            // Локація
            graphics.drawString(this.font, "§e=== Поточна гра ===",
                    centerX - 100, startY, 0xFFFFFF);
            startY += 20;

            graphics.drawString(this.font, "Локація: §e" + location.getName(),
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;

            // Прогрес
            int currentWave = playerData.getCurrentWave();
            int totalWaves = location.getWaves().size();
            graphics.drawString(this.font,
                    String.format("Прогрес: §a%d§7/§e%d §7хвиль", currentWave, totalWaves),
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;

            // Прогрес-бар
            int barWidth = 200;
            int barX = centerX - barWidth / 2;
            float progress = totalWaves > 0 ? (float) currentWave / totalWaves : 0;

            graphics.fill(barX, startY, barX + barWidth, startY + 10, 0xFF333333);
            graphics.fill(barX, startY, barX + (int)(barWidth * progress), startY + 10, 0xFF4CAF50);
            graphics.fill(barX - 1, startY - 1, barX + barWidth + 1, startY, 0xFFFFFFFF);
            graphics.fill(barX - 1, startY + 10, barX + barWidth + 1, startY + 11, 0xFFFFFFFF);
            graphics.fill(barX - 1, startY, barX, startY + 10, 0xFFFFFFFF);
            graphics.fill(barX + barWidth, startY, barX + barWidth + 1, startY + 10, 0xFFFFFFFF);

            startY += 25;

            // Поінти
            int points = location.getPlayerPoints(minecraft.player.getUUID());
            graphics.drawString(this.font, "Зароблено поінтів: §6" + points,
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;

            // Час
            long playTime = (System.currentTimeMillis() - playerData.getWaveStartTime()) / 1000;
            int hours = (int) (playTime / 3600);
            int minutes = (int) ((playTime % 3600) / 60);
            int seconds = (int) (playTime % 60);

            String timeStr = hours > 0 ?
                    String.format("%d:%02d:%02d", hours, minutes, seconds) :
                    String.format("%d:%02d", minutes, seconds);

            graphics.drawString(this.font, "Час у грі: §b" + timeStr,
                    centerX - 100, startY, 0xFFFFFF);
            startY += 20;

            // Таймер до наступної хвилі
            if (playerData.isTimerActive() && playerData.getTimeUntilNextWave() > 0) {
                int timeLeft = playerData.getTimeUntilNextWave();
                int mins = timeLeft / 60;
                int secs = timeLeft % 60;

                graphics.drawString(this.font,
                        String.format("§aНаступна хвиля через: §f%d:%02d", mins, secs),
                        centerX - 100, startY, 0xFFFFFF);
            } else if (playerData.isTimerActive()) {
                graphics.drawString(this.font, "§cХвиля в процесі!",
                        centerX - 100, startY, 0xFFFFFF);
            }
            startY += 20;

            // Налаштування
            startY += 10;
            graphics.drawString(this.font, "§e=== Налаштування ===",
                    centerX - 100, startY, 0xFFFFFF);
            startY += 20;

            graphics.drawString(this.font,
                    (playerData.isShowTimer() ? "§a✓" : "§c✗") + " §7Таймер",
                    centerX - 100, startY, 0xFFFFFF);
            startY += 15;

            graphics.drawString(this.font,
                    (playerData.isShowNotifications() ? "§a✓" : "§c✗") + " §7Сповіщення",
                    centerX - 100, startY, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(this.font, "§7Ви не в грі", centerX, startY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}