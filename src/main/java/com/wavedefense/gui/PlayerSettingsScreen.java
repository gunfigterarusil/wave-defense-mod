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
        super(Component.literal("§6Налаштування HUD"));
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
            Component.literal("Показувати таймер: "
                + (playerData.isShowTimer() ? "§aТак" : "§cНі")),
            b -> { playerData.setShowTimer(!playerData.isShowTimer()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build());

        // ── Сповіщення ────────────────────────────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.literal("Показувати сповіщення: "
                + (playerData.isShowNotifications() ? "§aТак" : "§cНі")),
            b -> { playerData.setShowNotifications(!playerData.isShowNotifications()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build());

        // ── Панель союзників (HUD тімейти) ───────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.literal("Панель союзників: "
                + (playerData.isShowTeammates() ? "§aТак" : "§cНі")),
            b -> { playerData.setShowTeammates(!playerData.isShowTeammates()); sendUpdate(); rebuildWidgets(); }
        ).bounds(cx - W/2, y, W, H).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7Показувати список тімейтів з HP зліва на екрані")));

        // ── Позиція HUD ───────────────────────────────────────────────
        y += H + GAP;
        this.addRenderableWidget(Button.builder(
            Component.literal("§b⊹ Позиція HUD"),
            b -> this.minecraft.setScreen(new HudEditScreen(this))
        ).bounds(cx - W/2, y, W, H).build());

        // ── Закрити ───────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("Закрити"),
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
