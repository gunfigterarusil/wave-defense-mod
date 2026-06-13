package com.wavedefense.gui;

import net.minecraft.util.text.TranslationTextComponent;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import com.wavedefense.gui.ClientCtpStateManager;
import com.wavedefense.gui.ScissorHelper;

import java.util.*;

/**
 * Таблиця статистики PvP у стилі Counter-Strike.
 * Відображає гравців посортованих за командою (відносно точок спавну),
 * з колонками: Нік | Кіли | Смерті | Асисти | K/D
 *
 * Гравці ворожої команди відображаються без нікнеймів якщо раунд ACTIVE.
 */
public class PvpScoreboardScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private static final int COL_NAME  = -170;
    private static final int COL_K     =  60;
    private static final int COL_D     =  90;
    private static final int COL_A     = 120;
    private static final int COL_KD    = 150;
    private static final int COL_ALIVE =  -20;

    public PvpScoreboardScreen() {
        super(new TranslationTextComponent("wavedefense.pvp.scoreboard_title"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        this.addButton(new Button(cx - 40, this.height - 28, 80, 20, new TranslationTextComponent("wavedefense.button.close"), button -> this.onClose()));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
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
        if (isBuy) title = String.format(I18n.get("wavedefense.pvp.buy_phase"), timerSec, currentRound, totalRounds);
        else if (isActive) title = String.format(I18n.get("wavedefense.pvp.round_active"), currentRound, totalRounds);
        else title = I18n.get("wavedefense.pvp.waiting");
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, title, cx, 12, GuiTheme.TEXT);

        // Перемоги команд
        Map<String, Integer> wins = ClientPvpStateManager.getTeamWins();
        int wy = 24;
        for (Map.Entry<String, Integer> e : wins.entrySet()) {
            String winsText = String.format(I18n.get("wavedefense.pvp.team_wins"), e.getKey(), e.getValue());
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, winsText, cx, wy, 0xFFFFFF);
            wy += 10;
        }

        // M-5 fix: show CtP/KotH objective scores when active
        if (ClientCtpStateManager.isActive()) {
            Map<String, Integer> objScores = ClientCtpStateManager.getObjectiveScore();
            int scoreToWin = ClientCtpStateManager.getScoreToWin();
            if (!objScores.isEmpty()) {
                wy += 3;
                // Divider line
                com.wavedefense.gui.GuiCompat.fill(g, cx - 180, wy, cx + 180, wy + 1, 0xFF444466);
                wy += 3;
                for (Map.Entry<String, Integer> e : objScores.entrySet()) {
                    String scoreText = scoreToWin > 0
                        ? String.format("§b%s§7: §f%d §7/ §e%d", e.getKey(), e.getValue(), scoreToWin)
                        : String.format("§b%s§7: §f%d", e.getKey(), e.getValue());
                    com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, scoreText, cx, wy, 0xFFFFFF);
                    wy += 10;
                }
                // Timer if in timer mode
                int ticksLeft = ClientCtpStateManager.getRoundTicksLeft();
                if (ticksLeft > 0) {
                    int totalSec = ticksLeft / 20;
                    int min = totalSec / 60;
                    int sec  = totalSec % 60;
                    String timerText = String.format("§c⏱ %d:%02d", min, sec);
                    com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, timerText, cx, wy, 0xFFFFFF);
                    wy += 10;
                }
                wy += 2;
            }
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

        // Scissor: обрізаємо список гравців щоб не накладався на кнопку закриття
        int clipBot = this.height - 32;
        ScissorHelper.enable(0, y, this.width, Math.max(1, clipBot - y));

        for (String team : teamOrder) {
            List<ClientPvpStateManager.PlayerRow> teamRows = byTeam.get(team);
            boolean isMyTeam = team.equals(myTeam);

            // Назва команди
            int teamColor = isMyTeam ? 0x55FF55 : 0xFF5555;
            String teamWinsStr = wins.containsKey(team) ? " [" + wins.get(team) + "]" : "";
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§l" + team + teamWinsStr, cx, y, teamColor);
            y += 10;

            // Роздільник
            com.wavedefense.gui.GuiCompat.fill(g, cx - 180, y, cx + 180, y + 1, 0xFF666666);
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

        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawHeader(MatrixStack g, int cx, int y) {
        com.wavedefense.gui.GuiCompat.fill(g, cx - 182, y - 1, cx + 182, y + this.font.lineHeight + 1, 0xAA222244);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.pvp.col_player"), cx + COL_NAME, y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7K",  cx + COL_K,  y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7D",  cx + COL_D,  y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7A",  cx + COL_A,  y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7K/D",cx + COL_KD, y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7●",  cx + COL_ALIVE, y, 0xAAAAAA);
    }

    private void drawRow(MatrixStack g, int cx, int y,
                         String name, int k, int d, int a, float kd, String alive) {
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, name,                         cx + COL_NAME,  y, 0xFFFFFF);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(k),            cx + COL_K,     y, 0xFFFF55);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(d),            cx + COL_D,     y, 0xFF5555);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(a),            cx + COL_A,     y, 0x55FFFF);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.format("%.2f", kd),   cx + COL_KD,    y, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, alive,                        cx + COL_ALIVE, y, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
