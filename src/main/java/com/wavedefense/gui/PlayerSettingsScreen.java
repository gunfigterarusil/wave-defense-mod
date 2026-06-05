package com.wavedefense.gui;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdatePlayerSettingsPacket;
import com.wavedefense.wave.PlayerWaveData;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class PlayerSettingsScreen extends Screen {
    private final PlayerWaveData playerData;

    public PlayerSettingsScreen(PlayerWaveData playerData) {
        super(new TranslationTextComponent("wavedefense.title.hud_settings"));
        this.playerData = playerData;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 50;
        final int W = 220, H = 20, GAP = 6;

        // ── Таймер ────────────────────────────────────────────────────
        this.addButton(new Button(cx - W/2, y, W, H, new TranslationTextComponent("wavedefense.settings.show_timer",
                new TranslationTextComponent(playerData.isShowTimer() ? "wavedefense.bool.yes" : "wavedefense.bool.no")), b -> { playerData.setShowTimer(!playerData.isShowTimer()); sendUpdate(); init(); }));

        // ── Сповіщення ────────────────────────────────────────────────
        y += H + GAP;
        this.addButton(new Button(cx - W/2, y, W, H, new TranslationTextComponent("wavedefense.settings.show_notifications",
                new TranslationTextComponent(playerData.isShowNotifications() ? "wavedefense.bool.yes" : "wavedefense.bool.no")), b -> { playerData.setShowNotifications(!playerData.isShowNotifications()); sendUpdate(); init(); }));

        // ── Панель союзників (HUD тімейти) ───────────────────────────
        y += H + GAP;
        this.addButton(new Button(cx - W/2, y, W, H, new TranslationTextComponent("wavedefense.settings.show_teammates",
                new TranslationTextComponent(playerData.isShowTeammates() ? "wavedefense.bool.yes" : "wavedefense.bool.no")), b -> { playerData.setShowTeammates(!playerData.isShowTeammates()); sendUpdate(); init(); }))
        /* setTooltip omitted on 1.16.5 */;

        // ── Позиція HUD ───────────────────────────────────────────────
        y += H + GAP;
        this.addButton(new Button(cx - W/2, y, W, H, new TranslationTextComponent("wavedefense.button.hud_position"), b -> this.minecraft.setScreen(new HudEditScreen(this))));

        // ── Закрити ───────────────────────────────────────────────────
        this.addButton(new Button(cx - 55, this.height - 30, 110, H, new TranslationTextComponent("wavedefense.button.close"), b -> this.onClose()));
    }

    private void sendUpdate() {
        PacketHandler.sendToServer(new UpdatePlayerSettingsPacket(
            playerData.isShowTimer(),
            playerData.isShowNotifications(),
            playerData.isShowTeammates()
        ));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 20, GuiTheme.TEXT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
