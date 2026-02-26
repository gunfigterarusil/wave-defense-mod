package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Таблиця статистики PvP у стилі Counter-Strike.
 * Відображає гравців посортованих за командою (відносно точок спавну),
 * з колонками: Нік | Кіли | Смерті | Асисти | K/D
 *
 * Гравці ворожої команди відображаються без нікнеймів якщо раунд ACTIVE.
 */
public class PvpScoreboardScreen extends Screen {

    private static final int COL_NAME  = -170;
    private static final int COL_K     =  60;
    private static final int COL_D     =  90;
    private static final int COL_A     = 120;
    private static final int COL_KD    = 150;
    private static final int COL_ALIVE =  -20;

    public PvpScoreboardScreen() {
        super(Component.literal("§c⚔ PvP Статистика"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(
                Component.literal("✕ Закрити"), button -> this.onClose()
        ).bounds(cx - 40, this.height - 28, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        String phase       = ClientPvpStateManager.getPhase();
        int currentRound   = ClientPvpStateManager.getCurrentRound();
        int totalRounds    = ClientPvpStateManager.getTotalRounds();
        int timerSec       = ClientPvpStateManager.getTimerSeconds();
        String myTeam      = ClientPvpStateManager.getMyTeam();
        boolean isActive   = "ACTIVE".equals(phase);
        boolean isBuy      = "BUY".equals(phase);

        // Заголовок
        String title;
        if (isBuy) title = String.format("§e⏱ ЧАС ПОКУПОК: %d сек | Раунд %d/%d", timerSec, currentRound, totalRounds);
        else if (isActive) title = String.format("§c⚔ РАУНД %d/%d", currentRound, totalRounds);
        else title = "§7Очікування гравців...";
        g.drawCenteredString(this.font, title, cx, 12, 0xFFFFFF);

        // Перемоги команд
        Map<String, Integer> wins = ClientPvpStateManager.getTeamWins();
        int wy = 24;
        for (Map.Entry<String, Integer> e : wins.entrySet()) {
            String winsText = "§e" + e.getKey() + " §7Перемоги: §a" + e.getValue();
            g.drawCenteredString(this.font, winsText, cx, wy, 0xFFFFFF);
            wy += 10;
        }

        // Групуємо гравців за командами
        List<ClientPvpStateManager.PlayerRow> rows = ClientPvpStateManager.getPlayers();
        Map<String, List<ClientPvpStateManager.PlayerRow>> byTeam = new LinkedHashMap<>();
        for (ClientPvpStateManager.PlayerRow row : rows) {
            byTeam.computeIfAbsent(row.team, k -> new ArrayList<>()).add(row);
        }

        // Сортуємо: моя команда зверху
        List<String> teamOrder = new ArrayList<>(byTeam.keySet());
        teamOrder.sort((a, b) -> a.equals(myTeam) ? -1 : b.equals(myTeam) ? 1 : 0);

        int y = wy + 8;

        // Заголовок колонок
        drawHeader(g, cx, y);
        y += 12;

        for (String team : teamOrder) {
            List<ClientPvpStateManager.PlayerRow> teamRows = byTeam.get(team);
            boolean isMyTeam = team.equals(myTeam);

            // Назва команди
            int teamColor = isMyTeam ? 0x55FF55 : 0xFF5555;
            String teamWinsStr = wins.containsKey(team) ? " [" + wins.get(team) + "]" : "";
            g.drawCenteredString(this.font, "§l" + team + teamWinsStr, cx, y, teamColor);
            y += 10;

            // Роздільник
            g.fill(cx - 180, y, cx + 180, y + 1, 0xFF666666);
            y += 3;

            // Сортуємо за кілами
            teamRows.sort((a, b) -> b.kills - a.kills);

            for (ClientPvpStateManager.PlayerRow row : teamRows) {
                // Якщо ворожа команда та активний раунд — ховаємо нік
                String displayName;
                if (!isMyTeam && isActive) {
                    displayName = "§7???";
                } else {
                    displayName = (row.alive ? "§f" : "§7☠ §m") + row.name;
                }

                String aliveIcon = row.alive ? "§a●" : "§c✕";

                drawRow(g, cx, y, displayName, row.kills, row.deaths, row.assists,
                        row.deaths == 0 ? row.kills : (float) row.kills / row.deaths, aliveIcon);
                y += 11;
            }
            y += 4;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics g, int cx, int y) {
        g.fill(cx - 182, y - 1, cx + 182, y + this.font.lineHeight + 1, 0xAA222244);
        g.drawString(this.font, "§7Гравець", cx + COL_NAME, y, 0xAAAAAA);
        g.drawString(this.font, "§7K",  cx + COL_K,  y, 0xAAAAAA);
        g.drawString(this.font, "§7D",  cx + COL_D,  y, 0xAAAAAA);
        g.drawString(this.font, "§7A",  cx + COL_A,  y, 0xAAAAAA);
        g.drawString(this.font, "§7K/D",cx + COL_KD, y, 0xAAAAAA);
        g.drawString(this.font, "§7●",  cx + COL_ALIVE, y, 0xAAAAAA);
    }

    private void drawRow(GuiGraphics g, int cx, int y,
                         String name, int k, int d, int a, float kd, String alive) {
        g.drawString(this.font, name,                         cx + COL_NAME,  y, 0xFFFFFF);
        g.drawString(this.font, String.valueOf(k),            cx + COL_K,     y, 0xFFFF55);
        g.drawString(this.font, String.valueOf(d),            cx + COL_D,     y, 0xFF5555);
        g.drawString(this.font, String.valueOf(a),            cx + COL_A,     y, 0x55FFFF);
        g.drawString(this.font, String.format("%.2f", kd),   cx + COL_KD,    y, 0xAAAAAA);
        g.drawString(this.font, alive,                        cx + COL_ALIVE, y, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
