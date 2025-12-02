package com.wavedefense.gui;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdatePlayerSettingsPacket;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PlayerSettingsScreen extends Screen {
    private final PlayerWaveData playerData;

    public PlayerSettingsScreen(PlayerWaveData playerData) {
        super(Component.literal("Налаштування гравця"));
        this.playerData = playerData;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        this.addRenderableWidget(Button.builder(
                Component.literal("Показувати таймер: " + (playerData.isShowTimer() ? "§aТак" : "§cНі")),
                button -> {
                    playerData.setShowTimer(!playerData.isShowTimer());
                    sendSettingsUpdate();
                    this.rebuildWidgets();
                }
        ).bounds(centerX - 100, startY, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Показувати сповіщення: " + (playerData.isShowNotifications() ? "§aТак" : "§cНі")),
                button -> {
                    playerData.setShowNotifications(!playerData.isShowNotifications());
                    sendSettingsUpdate();
                    this.rebuildWidgets();
                }
        ).bounds(centerX - 100, startY + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void sendSettingsUpdate() {
        PacketHandler.sendToServer(new UpdatePlayerSettingsPacket(playerData.isShowTimer(), playerData.isShowNotifications()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
