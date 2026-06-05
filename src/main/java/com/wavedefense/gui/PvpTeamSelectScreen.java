package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.TeleportPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PvpTeamSelectScreen extends Screen {

    private static final int CARD_W = 190;
    private static final int CARD_H = 44;
    private static final int GAP = 10;

    private final Location location;
    private final Screen parent;
    private int scrollRow;
    private List<TeamOption> teamOptions = java.util.Collections.emptyList();

    public PvpTeamSelectScreen(Location location, Screen parent) {
        super(titleFor(location));
        this.location = location;
        this.parent = parent;
    }

    /** 1.16.5: helper replacing Java 14 switch expression (must be static — called from super(...)). */
    private static net.minecraft.util.text.ITextComponent titleFor(Location location) {
        Location.PvpMode m = location.getPvpMode();
        if (m == Location.PvpMode.BATTLE_ROYALE) return new TranslationTextComponent("wavedefense.pvp.team_select.battle_royale", location.getName());
        if (m == Location.PvpMode.DEATHMATCH)    return new TranslationTextComponent("wavedefense.pvp.team_select.deathmatch",    location.getName());
        return new TranslationTextComponent("wavedefense.pvp.team_select.choose", location.getName());
    }

    @Override
    protected void init() {
        super.init();
        this.teamOptions = buildTeamOptions();
        clampScroll();

        int cx = this.width / 2;
        int y = 42;

        if (location.isBattleRoyale()) {
            addLabel(cx - 150, y, 300, new TranslationTextComponent("wavedefense.pvp.team_select.br_title"));
            y += 22;
            addLabel(cx - 150, y, 300, new TranslationTextComponent("wavedefense.pvp.team_select.br_hint"));
            y += 28;
            this.addButton(new Button(cx - 80, y, 160, 24, new TranslationTextComponent("wavedefense.button.enter_game"), b -> joinTeam(-1)));
        } else {
            addModeSummary(cx, y);
            y += 52; // A3 fix: addModeSummary now has 3 rows (was 2), +18px
            addTeamCards(y);
        }

        this.addButton(new Button(cx - 50, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.back"), button -> this.minecraft.setScreen(parent)));
    }

    private void addModeSummary(int cx, int y) {
        ITextComponent mode = location.isDeathmatch()
            ? new TranslationTextComponent("wavedefense.pvp.team_select.dm_mode")
            : new TranslationTextComponent("wavedefense.pvp.team_select.team_mode");
        ITextComponent rule = location.isDeathmatch()
            ? new TranslationTextComponent("wavedefense.pvp.team_select.dm_rule", location.getDmKillsToWin())
            : new TranslationTextComponent("wavedefense.pvp.team_select.team_rule");
        addLabel(cx - 160, y, 320, new StringTextComponent("§e§l").append(mode));
        addLabel(cx - 160, y + 18, 320, new StringTextComponent("§7").append(rule));
        // A3 fix: show minimum player requirement so players know when the match will start
        addLabel(cx - 160, y + 34, 320,
            new TranslationTextComponent("wavedefense.pvp.team_select.min_players", location.getPvpMinPlayers()));
    }

    private void addTeamCards(int startY) {
        if (teamOptions.isEmpty()) {
            addLabel(this.width / 2 - 155, startY + 10, 310,
                new StringTextComponent("§c").append(new TranslationTextComponent("wavedefense.pvp.team_select.no_teams")));
            return;
        }

        int cols = columns();
        int totalW = cols * CARD_W + (cols - 1) * GAP;
        int startX = this.width / 2 - totalW / 2;
        int visibleRows = visibleRows(startY);
        int first = scrollRow * cols;
        int last = Math.min(teamOptions.size(), first + visibleRows * cols);

        for (int i = first; i < last; i++) {
            TeamOption option = teamOptions.get(i);
            int local = i - first;
            int col = local % cols;
            int row = local / cols;
            int x = startX + col * (CARD_W + GAP);
            int y = startY + row * (CARD_H + GAP);

            this.addButton(new Button(x, y, CARD_W, CARD_H, new TranslationTextComponent("wavedefense.pvp.team_select.card",
                    option.colorCode(), shorten(option.teamName(), 18),
                    option.spawnIndex() + 1, option.spawn().getSpawnRadius()), b -> joinTeam(option.spawnIndex())));
        }

        int max = maxScrollRows(startY);
        if (max > 0) {
            int x = startX + totalW + 8;
            this.addButton(new Button(x, startY, 22, 20, new StringTextComponent("▲"), b -> {
                scrollRow = Math.max(0, scrollRow - 1);
                init();
            })).active = scrollRow > 0;
            this.addButton(new Button(x, Math.min(this.height - 54, startY + visibleRows * (CARD_H + GAP) - 20), 22, 20, new StringTextComponent("▼"), b -> {
                scrollRow = Math.min(maxScrollRows(startY), scrollRow + 1);
                init();
            })).active = scrollRow < max;
        }
    }

    private List<TeamOption> buildTeamOptions() {
        // Count how many spawn points share each team name (to detect duplicates)
        Map<String, Integer> nameCount = new java.util.LinkedHashMap<>();
        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        for (PvpSpawnPoint spawn : spawns) {
            String raw = spawn.getTeamName() == null || spawn.getTeamName().trim().isEmpty() ? "" : spawn.getTeamName();
            nameCount.merge(raw, 1, Integer::sum);
        }
        // Track per-name occurrence index for suffix labelling
        Map<String, Integer> nameIdx = new java.util.LinkedHashMap<>();

        List<TeamOption> result = new ArrayList<>();
        for (int i = 0; i < spawns.size(); i++) {
            PvpSpawnPoint spawn = spawns.get(i);
            String raw = spawn.getTeamName() == null || spawn.getTeamName().trim().isEmpty() ? "" : spawn.getTeamName();
            // Base display name
            String baseName = raw.trim().isEmpty()
                ? new TranslationTextComponent("wavedefense.pvp.team_select.team_fallback", i + 1).getString()
                : raw;
            // Append spawn-index suffix only when multiple spawns share the same name
            String displayName;
            if (nameCount.getOrDefault(raw, 0) > 1) {
                int occurrence = nameIdx.merge(raw, 1, Integer::sum);
                displayName = baseName + " (" + occurrence + ")";
            } else {
                displayName = baseName;
            }
            result.add(new TeamOption(displayName, i, spawn, colorFor(raw)));
        }
        return result;
    }

    private String colorFor(String teamName) {
        String normalized = teamName.toLowerCase(Locale.ROOT);
        if (normalized.contains("red") || normalized.contains("cherv") || normalized.contains("черв")) return "\u00A7c";
        if (normalized.contains("blue") || normalized.contains("syn") || normalized.contains("син")) return "\u00A79";
        if (normalized.contains("green") || normalized.contains("zelen") || normalized.contains("зелен")) return "\u00A7a";
        if (normalized.contains("yellow") || normalized.contains("zhov") || normalized.contains("жов")) return "\u00A7e";
        return "\u00A7f";
    }

    private String shorten(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(1, maxChars - 3)) + "...";
    }

    private int columns() {
        return this.width >= 500 ? 2 : 1;
    }

    private int visibleRows(int startY) {
        int available = Math.max(CARD_H, this.height - startY - 58);
        return Math.max(1, available / (CARD_H + GAP));
    }

    private int maxScrollRows(int startY) {
        int totalRows = (teamOptions.size() + columns() - 1) / columns();
        return Math.max(0, totalRows - visibleRows(startY));
    }

    private void clampScroll() {
        scrollRow = MathHelper.clamp(scrollRow, 0, maxScrollRows(76));
    }

    private void addLabel(int x, int y, int w, ITextComponent text) {
        Button label = new Button(x, y, w, 16, text, b -> {});
        label.active = false;
        this.addButton(label);
    }

    private void joinTeam(int spawnIndex) {
        PacketHandler.sendToServer(new TeleportPacket(location.getName(), spawnIndex));
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (location.isBattleRoyale() || teamOptions.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int max = maxScrollRows(76);
        int next = scrollRow + (delta < 0 ? 1 : -1);
        scrollRow = MathHelper.clamp(next, 0, max);
        init();
        return true;
    }

    @Override
    public void render(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);
        Location.PvpMode mode = location.getPvpMode();
        int titleColor;
        if (mode == Location.PvpMode.BATTLE_ROYALE) titleColor = 0xFF5555;
        else if (mode == Location.PvpMode.DEATHMATCH) titleColor = 0xFFAA33;
        else titleColor = 0xFF66AAFF;
        com.wavedefense.gui.GuiCompat.drawCenteredString(graphics, this.font, this.title, this.width / 2, 14, titleColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 1.16.5 port: record → plain final class with accessor methods. */
    private static final class TeamOption {
        public final String teamName;
        public final int spawnIndex;
        public final PvpSpawnPoint spawn;
        public final String colorCode;
        TeamOption(String teamName, int spawnIndex, PvpSpawnPoint spawn, String colorCode) {
            this.teamName = teamName; this.spawnIndex = spawnIndex;
            this.spawn = spawn; this.colorCode = colorCode;
        }
        public String  teamName()   { return teamName; }
        public int     spawnIndex() { return spawnIndex; }
        public PvpSpawnPoint spawn(){ return spawn; }
        public String  colorCode()  { return colorCode; }
    }
}
