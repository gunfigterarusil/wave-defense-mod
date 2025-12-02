package com.wavedefense.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.gui.PlayerSettingsScreen;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SurrenderPacket;
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

    private static boolean surrenderButtonHovered = false;
    private static boolean settingsButtonHovered = false;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static int surrenderButtonX;
    private static int surrenderButtonY;
    private static int settingsButtonX;
    private static int settingsButtonY;

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

        // Points display
        int points = location.getPlayerPoints(player.getUUID());
        String pointsText = "§6Поінти: §e" + points;
        int pointsX = screenWidth - mc.font.width(pointsText) - 10;
        int pointsY = screenHeight - 30;

        graphics.fill(pointsX - 5, pointsY - 2,
                screenWidth - 5, pointsY + mc.font.lineHeight + 2,
                0x80000000);
        graphics.drawString(mc.font, pointsText, pointsX, pointsY, 0xFFFFFF);

        // Wave display
        String waveText = "§6Хвиля: §f" + playerData.getCurrentWave();
        int waveX = screenWidth - mc.font.width(waveText) - 10;
        int waveY = pointsY - 15;

        graphics.fill(waveX - 5, waveY - 2,
                screenWidth - 5, waveY + mc.font.lineHeight + 2,
                0x80000000);
        graphics.drawString(mc.font, waveText, waveX, waveY, 0xFFFFFF);

        // Timer display
        if (playerData.isShowTimer() && playerData.isTimerActive()) {
            int timeLeft = playerData.getTimeUntilNextWave();

            if (timeLeft > 0) {
                int minutes = timeLeft / 60;
                int seconds = timeLeft % 60;
                String timerText = String.format("§aНаступна хвиля через: §f%d:%02d", minutes, seconds);

                int timerX = (screenWidth - mc.font.width(timerText)) / 2;
                int timerY = 20;

                graphics.fill(timerX - 5, timerY - 2,
                        timerX + mc.font.width(timerText) + 5, timerY + mc.font.lineHeight + 2,
                        0x80000000);
                graphics.drawString(mc.font, timerText, timerX, timerY, 0xFFFFFF);

                if (playerData.getCurrentWave() > 0 &&
                        playerData.getCurrentWave() <= location.getWaves().size()) {

                    int totalTime = location.getWaves()
                            .get(playerData.getCurrentWave() - 1)
                            .getTimeBetweenWaves();

                    float progress = 1.0f - ((float) timeLeft / totalTime);
                    int barWidth = 200;
                    int barHeight = 4;
                    int barX = (screenWidth - barWidth) / 2;
                    int barY = timerY + mc.font.lineHeight + 5;

                    graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
                    int fillWidth = (int) (barWidth * progress);
                    graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00FF00);
                    graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFFFFFFFF);
                    graphics.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFFFFFFFF);
                    graphics.fill(barX - 1, barY, barX, barY + barHeight, 0xFFFFFFFF);
                    graphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFFFFFFFF);
                }
            }
        } else if (!playerData.isTimerActive() && playerData.getCurrentWave() > 0) {
            String activeText = "§c§lХВИЛЯ В ПРОЦЕСІ!";
            int activeX = (screenWidth - mc.font.width(activeText)) / 2;
            int activeY = 20;

            graphics.fill(activeX - 5, activeY - 2,
                    activeX + mc.font.width(activeText) + 5, activeY + mc.font.lineHeight + 2,
                    0xC0FF0000);
            graphics.drawString(mc.font, activeText, activeX, activeY, 0xFFFFFF);
        }

        // Location name
        String locationName = "§7" + location.getName();
        graphics.drawString(mc.font, locationName, 5, 5, 0xFFFFFF);

        // Buttons rendering
        surrenderButtonX = 10;
        surrenderButtonY = screenHeight / 2;
        settingsButtonX = 10;
        settingsButtonY = surrenderButtonY + 25;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

        surrenderButtonHovered = isMouseOver(mouseX, mouseY, surrenderButtonX, surrenderButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        settingsButtonHovered = isMouseOver(mouseX, mouseY, settingsButtonX, settingsButtonY, BUTTON_WIDTH, BUTTON_HEIGHT);

        // Surrender button
        int surrenderColor = surrenderButtonHovered ? 0xFFFF5555 : 0xFF555555;
        graphics.fill(surrenderButtonX, surrenderButtonY, surrenderButtonX + BUTTON_WIDTH, surrenderButtonY + BUTTON_HEIGHT, surrenderColor);
        graphics.fill(surrenderButtonX, surrenderButtonY, surrenderButtonX + BUTTON_WIDTH, surrenderButtonY + 1, 0xFFFFFFFF);
        graphics.fill(surrenderButtonX, surrenderButtonY + BUTTON_HEIGHT - 1, surrenderButtonX + BUTTON_WIDTH, surrenderButtonY + BUTTON_HEIGHT, 0xFF000000);
        graphics.fill(surrenderButtonX, surrenderButtonY, surrenderButtonX + 1, surrenderButtonY + BUTTON_HEIGHT, 0xFFFFFFFF);
        graphics.fill(surrenderButtonX + BUTTON_WIDTH - 1, surrenderButtonY, surrenderButtonX + BUTTON_WIDTH, surrenderButtonY + BUTTON_HEIGHT, 0xFF000000);

        String surrenderText = "Здатися";
        int surrenderTextX = surrenderButtonX + (BUTTON_WIDTH - mc.font.width(surrenderText)) / 2;
        int surrenderTextY = surrenderButtonY + (BUTTON_HEIGHT - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, surrenderText, surrenderTextX, surrenderTextY, 0xFFFFFF);

        // Settings button
        int settingsColor = settingsButtonHovered ? 0xFF555555 : 0xFF333333;
        graphics.fill(settingsButtonX, settingsButtonY, settingsButtonX + BUTTON_WIDTH, settingsButtonY + BUTTON_HEIGHT, settingsColor);
        graphics.fill(settingsButtonX, settingsButtonY, settingsButtonX + BUTTON_WIDTH, settingsButtonY + 1, 0xFFFFFFFF);
        graphics.fill(settingsButtonX, settingsButtonY + BUTTON_HEIGHT - 1, settingsButtonX + BUTTON_WIDTH, settingsButtonY + BUTTON_HEIGHT, 0xFF000000);
        graphics.fill(settingsButtonX, settingsButtonY, settingsButtonX + 1, settingsButtonY + BUTTON_HEIGHT, 0xFFFFFFFF);
        graphics.fill(settingsButtonX + BUTTON_WIDTH - 1, settingsButtonY, settingsButtonX + BUTTON_WIDTH, settingsButtonY + BUTTON_HEIGHT, 0xFF000000);

        String settingsText = "Налашт.";
        int settingsTextX = settingsButtonX + (BUTTON_WIDTH - mc.font.width(settingsText)) / 2;
        int settingsTextY = settingsButtonY + (BUTTON_HEIGHT - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, settingsText, settingsTextX, settingsTextY, 0xFFFFFF);

        RenderSystem.disableBlend();
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;

        PlayerWaveData playerData = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (playerData == null || !playerData.isInWave()) return false;

        if (isMouseOver(mouseX, mouseY, surrenderButtonX, surrenderButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            PacketHandler.sendToServer(new SurrenderPacket());
            return true;
        }

        if (isMouseOver(mouseX, mouseY, settingsButtonX, settingsButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            mc.setScreen(new PlayerSettingsScreen(playerData));
            return true;
        }

        return false;
    }

    private static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}