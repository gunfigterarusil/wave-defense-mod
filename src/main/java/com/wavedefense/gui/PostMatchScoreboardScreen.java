package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.network.packets.OpenPostMatchScoreboardPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-match summary screen shown to every player when a PvP match ends.
 *
 * <p>Renders a table of all participants sorted by points (then kills) and
 * highlights the winning team. Opened by {@link OpenPostMatchScoreboardPacket}.
 */
public class PostMatchScoreboardScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private final String modeLabel;
    private final String winnerTeam;       // empty = draw
    private final List<OpenPostMatchScoreboardPacket.PlayerRow> rows;
    private final Map<String, Integer> teamWins;

    public PostMatchScoreboardScreen(String modeLabel, String winnerTeam,
                                     List<OpenPostMatchScoreboardPacket.PlayerRow> rows,
                                     Map<String, Integer> teamWins) {
        super(new TranslationTextComponent("wavedefense.scoreboard.title"));
        this.modeLabel  = modeLabel != null ? modeLabel : "";
        this.winnerTeam = winnerTeam != null ? winnerTeam : "";
        this.rows       = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
        this.teamWins   = teamWins != null ? new LinkedHashMap<>(teamWins) : new LinkedHashMap<>();
        // Sort: points desc, then kills desc, then deaths asc
        this.rows.sort(Comparator
            .comparingInt((OpenPostMatchScoreboardPacket.PlayerRow r) -> r.points).reversed()
            .thenComparing(Comparator.comparingInt((OpenPostMatchScoreboardPacket.PlayerRow r) -> r.kills).reversed())
            .thenComparingInt(r -> r.deaths));
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new Button(this.width / 2 - 50, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.close"), b -> this.onClose()));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        int cx = this.width / 2;
        int panelW = Math.min(420, this.width - 32);
        int panelX = cx - panelW / 2;
        int contentTop = 38;
        int contentBot = this.height - 34;
        GuiTheme.renderContentFrame(g, panelX - 4, contentTop - 4, panelX + panelW + 4, contentBot + 4);

        int y = contentTop;

        // Mode + winner
        String modeText = I18n.get("wavedefense.scoreboard.mode", modeLabel);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, modeText, cx, y, GuiTheme.TEXT);
        y += 12;
        String winnerText = winnerTeam.isEmpty()
            ? I18n.get("wavedefense.scoreboard.draw")
            : I18n.get("wavedefense.scoreboard.winner", winnerTeam);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, winnerText, cx,  y, 0xFFE0A020);
        y += 16;

        // Team wins (Standard only — shows nothing for DM/BR/CtP if empty)
        if (!teamWins.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> e : teamWins.entrySet()) {
                if (!first) sb.append("  §7|§r  ");
                sb.append("§a").append(e.getKey()).append("§7: §e").append(e.getValue());
                first = false;
            }
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new StringTextComponent(sb.toString()), cx, y, GuiTheme.TEXT);
            y += 14;
        }

        // Table header
        y += 4;
        int colName    = panelX + 6;
        int colTeam    = panelX + 130;
        int colKills   = panelX + 210;
        int colDeaths  = panelX + 250;
        int colAssists = panelX + 290;
        int colPoints  = panelX + 340;

        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.name"),    colName,    y, GuiTheme.TEXT_MUTED);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.team"),    colTeam,    y, GuiTheme.TEXT_MUTED);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.kills"),   colKills,   y, GuiTheme.TEXT_MUTED);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.deaths"),  colDeaths,  y, GuiTheme.TEXT_MUTED);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.assists"), colAssists, y, GuiTheme.TEXT_MUTED);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.scoreboard.col.points"),  colPoints,  y, GuiTheme.TEXT_MUTED);
        y += 10;
        com.wavedefense.gui.GuiCompat.fill(g, panelX + 4, y, panelX + panelW - 4, y + 1, GuiTheme.BORDER);
        y += 4;

        // Rows
        int rowH = 11;
        int maxRows = Math.max(1, (contentBot - y) / rowH);
        int shown = 0;
        for (OpenPostMatchScoreboardPacket.PlayerRow r : rows) {
            if (shown >= maxRows) break;
            String name = trunc(r.name == null ? "?" : r.name, 18);
            String team = trunc(r.team == null ? "" : r.team, 10);
            boolean isWinner = !winnerTeam.isEmpty() && winnerTeam.equals(r.team);
            int colTeamColour = isWinner ? 0xFF80FF80 : GuiTheme.TEXT_MUTED;
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, isWinner ? "§a▶ " + name : name, colName,  y, GuiTheme.TEXT);
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, team,                    colTeam,    y, colTeamColour);
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(r.kills),   colKills,   y, GuiTheme.TEXT);
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(r.deaths),  colDeaths,  y, GuiTheme.TEXT);
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(r.assists), colAssists, y, GuiTheme.TEXT);
            com.wavedefense.gui.GuiCompat.drawString(g, this.font, String.valueOf(r.points),  colPoints,  y, 0xFFE0A020);
            y += rowH;
            shown++;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
