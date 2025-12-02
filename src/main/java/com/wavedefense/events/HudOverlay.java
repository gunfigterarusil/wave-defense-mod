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
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
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

        int points = location.getPlayerPoints(player.getUUID());
        String pointsText = "§6Поінти: §e" + points;
        int pointsX = screenWidth - mc.font.width(pointsText) - 10;
        int pointsY = screenHeight - 30;

        graphics.fill(pointsX - 5, pointsY - 2,
                screenWidth - 5, pointsY + mc.font.lineHeight + 2,
                0x80000000);
        graphics.drawString(mc.font, pointsText, pointsX, pointsY, 0xFFFFFF);

        String waveText = "§6Хвиля: §f" + playerData.getCurrentWave();
        int waveX = screenWidth - mc.font.width(waveText) - 10;
        int waveY = pointsY - 15;

        graphics.fill(waveX - 5, waveY - 2,
                screenWidth - 5, waveY + mc.font.lineHeight + 2,
                0x80000000);
        graphics.drawString(mc.font, waveText, waveX, waveY, 0xFFFFFF);

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
            } else {
                String activeText = "§c§lХВИЛЯ В ПРОЦЕСІ!";
                int activeX = (screenWidth - mc.font.width(activeText)) / 2;
                int activeY = 20;

                graphics.fill(activeX - 5, activeY - 2,
                        activeX + mc.font.width(activeText) + 5, activeY + mc.font.lineHeight + 2,
                        0xC0FF0000);
                graphics.drawString(mc.font, activeText, activeX, activeY, 0xFFFFFF);
            }
        }

        String locationName = "§7" + location.getName();
        graphics.drawString(mc.font, locationName, 5, 5, 0xFFFFFF);

        // Buttons
        int buttonY = screenHeight / 2;
        Button surrenderButton = Button.builder(Component.literal("Здатися"), button -> {
            PacketHandler.sendToServer(new SurrenderPacket());
        }).bounds(10, buttonY, 80, 20).build();
        surrenderButton.render(graphics, 0, 0, 0);

        Button settingsButton = Button.builder(Component.literal("Налаштування"), button -> {
            mc.setScreen(new PlayerSettingsScreen(playerData));
        }).bounds(10, buttonY + 25, 80, 20).build();
        settingsButton.render(graphics, 0, 0, 0);

        RenderSystem.disableBlend();
    }
}
