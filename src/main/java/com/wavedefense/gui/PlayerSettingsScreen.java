package com.wavedefense.gui;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdatePlayerSettingsPacket;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PlayerSettingsScreen extends Screen {
    private final PlayerWaveData playerData;

    public PlayerSettingsScreen(PlayerWaveData playerData) {
        super(Component.translatable("wavedefense.title.hud_settings"));
        this.playerData = playerData;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 50;
        final int W = 220, H = 20, GAP = 6;

        // ── Таймер ────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.settings.show_timer",
                Component.translatable(playerData.isShowTimer() ? "wavedefense.bool.yes" : "wavedefense.bool.no")),
            b -> { playerData.setShowTimer(!playerData.isShowTimer()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build());

        // ── Сповіщення ────────────────────────────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.settings.show_notifications",
                Component.translatable(playerData.isShowNotifications() ? "wavedefense.bool.yes" : "wavedefense.bool.no")),
            b -> { playerData.setShowNotifications(!playerData.isShowNotifications()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build());

        // ── Панель союзників (HUD тімейти) ───────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.settings.show_teammates",
                Component.translatable(playerData.isShowTeammates() ? "wavedefense.bool.yes" : "wavedefense.bool.no")),
            b -> { playerData.setShowTeammates(!playerData.isShowTeammates()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build())
        .setTooltip(Tooltip.create(Component.translatable("wavedefense.tooltip.show_teammates")));

        // ── Позиція HUD ───────────────────────────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.hud_position"),
            b -> this.minecraft.setScreen(new HudEditScreen(this))
        ).bounds(cx - W/2, y, W, H).build());

        // ── Закрити ───────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.close"),
            b -> this.onClose()
        ).bounds(cx - 55, this.height - 30, 110, H).build());
    }

    private void sendUpdate() {
        PacketHandler.sendToServer(new UpdatePlayerSettingsPacket(
            playerData.isShowTimer(),
            playerData.isShowNotifications(),
            playerData.isShowTeammates()
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
