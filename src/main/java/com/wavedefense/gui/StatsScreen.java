package com.wavedefense.gui;

import com.wavedefense.data.GameStats;
import com.wavedefense.data.PlayerStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsScreen extends Screen {

    private final GameStats stats;
    // Н3: cache player names at init time to avoid getPlayerInfo() calls in render thread
    private final Map<UUID, String> playerNameCache = new HashMap<>();

    public StatsScreen() {
        super(Component.translatable("wavedefense.title.stats"));
        this.stats = ClientStatsManager.getStats();
    }

    @Override
    protected void init() {
        super.init();
        // Н3: build name cache once here, not on every render frame
        if (stats != null) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            for (UUID uuid : stats.getPlayerStats().keySet()) {
                net.minecraft.client.multiplayer.PlayerInfo info =
                    (mc.getConnection() != null) ? mc.getConnection().getPlayerInfo(uuid) : null;
                playerNameCache.put(uuid, info != null ? info.getProfile().getName() : uuid.toString());
            }
        }
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.close"),
                button -> this.onClose()
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);
        GuiTheme.renderHeader(graphics, this.font, this.title, this.width);

        int cx = this.width / 2;
        int panelX = cx - 120;
        int panelW = 240;

        if (stats == null) {
            graphics.drawCenteredString(this.font, Component.translatable("wavedefense.stats.no_data"), cx, 50, GuiTheme.TEXT_MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Фрейм контентної зони
        GuiTheme.renderContentFrame(graphics, panelX - 4, 36, panelX + panelW + 4, this.height - 28);

        int y = 44;
        // Загальна статистика сесії
        GuiTheme.sectionLabel(graphics, this.font, Component.translatable("wavedefense.stats.waves_completed", stats.getWavesCompleted()), panelX, y);
        y += 14;
        graphics.drawString(this.font, Component.translatable("wavedefense.stats.mobs_killed", stats.getMobsKilled()), panelX, y, GuiTheme.TEXT_MUTED, false);
        y += 18;

        // Рядки гравців
        for (Map.Entry<UUID, PlayerStats> entry : stats.getPlayerStats().entrySet()) {
            String playerName = playerNameCache.getOrDefault(entry.getKey(), entry.getKey().toString());
            PlayerStats playerStats = entry.getValue();
            boolean hovered = mouseY >= y - 2 && mouseY <= y + 38;
            GuiTheme.card(graphics, panelX - 2, y - 2, panelX + panelW + 2, y + 38, hovered);
            graphics.drawString(this.font, Component.translatable("wavedefense.stats.player", playerName), panelX + 2, y, GuiTheme.TEXT, false);
            y += 12;
            graphics.drawString(this.font, Component.translatable("wavedefense.stats.player_mobs", playerStats.getMobsKilled()), panelX + 8, y, GuiTheme.TEXT_MUTED, false);
            y += 12;
            graphics.drawString(this.font, Component.translatable("wavedefense.stats.player_points", playerStats.getPointsEarned()), panelX + 8, y, GuiTheme.ACCENT_ALT, false);
            y += 18;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
