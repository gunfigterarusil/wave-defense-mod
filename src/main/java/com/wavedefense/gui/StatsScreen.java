package com.wavedefense.gui;

import com.wavedefense.data.GameStats;
import com.wavedefense.data.PlayerStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

public class StatsScreen extends Screen {

    private final GameStats stats;

    public StatsScreen() {
        super(Component.literal("Статистика"));
        this.stats = ClientStatsManager.getStats();
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        if (stats == null) {
            graphics.drawCenteredString(this.font, "Немає даних", this.width / 2, 50, 0xFFFFFF);
            return;
        }

        int y = 50;
        graphics.drawString(this.font, "Хвиль пройдено: " + stats.getWavesCompleted(), this.width / 2 - 50, y, 0xFFFFFF);
        y += 10;
        graphics.drawString(this.font, "Мобів вбито: " + stats.getMobsKilled(), this.width / 2 - 50, y, 0xFFFFFF);
        y += 20;

        for (Map.Entry<UUID, PlayerStats> entry : stats.getPlayerStats().entrySet()) {
            String playerName = entry.getKey().toString().substring(0, 8);
            PlayerStats playerStats = entry.getValue();
            graphics.drawString(this.font, "Гравець: " + playerName, this.width / 2 - 50, y, 0xFFFFFF);
            y += 10;
            graphics.drawString(this.font, "  Мобів вбито: " + playerStats.getMobsKilled(), this.width / 2 - 40, y, 0xFFFFFF);
            y += 10;
            graphics.drawString(this.font, "  Очок зароблено: " + playerStats.getPointsEarned(), this.width / 2 - 40, y, 0xFFFFFF);
            y += 20;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
