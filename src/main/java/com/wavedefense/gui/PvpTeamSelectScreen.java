package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.TeleportPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
    private List<TeamOption> teamOptions = List.of();

    public PvpTeamSelectScreen(Location location, Screen parent) {
        super(switch (location.getPvpMode()) {
            case BATTLE_ROYALE -> Component.translatable("wavedefense.pvp.team_select.battle_royale", location.getName());
            case DEATHMATCH -> Component.translatable("wavedefense.pvp.team_select.deathmatch", location.getName());
            default -> Component.translatable("wavedefense.pvp.team_select.choose", location.getName());
        });
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.teamOptions = buildTeamOptions();
        clampScroll();

        int cx = this.width / 2;
        int y = 42;

        if (location.isBattleRoyale()) {
            addLabel(cx - 150, y, 300, Component.translatable("wavedefense.pvp.team_select.br_title"));
            y += 22;
            addLabel(cx - 150, y, 300, Component.translatable("wavedefense.pvp.team_select.br_hint"));
            y += 28;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.enter_game"),
                b -> joinTeam(-1)
            ).bounds(cx - 80, y, 160, 24).build());
        } else {
            addModeSummary(cx, y);
            y += 52; // A3 fix: addModeSummary now has 3 rows (was 2), +18px
            addTeamCards(y);
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.back"),
            button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    private void addModeSummary(int cx, int y) {
        Component mode = location.isDeathmatch()
            ? Component.translatable("wavedefense.pvp.team_select.dm_mode")
            : Component.translatable("wavedefense.pvp.team_select.team_mode");
        Component rule = location.isDeathmatch()
            ? Component.translatable("wavedefense.pvp.team_select.dm_rule", location.getDmKillsToWin())
            : Component.translatable("wavedefense.pvp.team_select.team_rule");
        addLabel(cx - 160, y, 320, Component.translatable("wavedefense.auto.u00a7e_u00a7l_99162931").append(mode));
        addLabel(cx - 160, y + 18, 320, Component.translatable("wavedefense.auto.u00a77_d0a4b613").append(rule));
        // A3 fix: show minimum player requirement so players know when the match will start
        addLabel(cx - 160, y + 34, 320,
            Component.translatable("wavedefense.pvp.team_select.min_players", location.getPvpMinPlayers()));
    }

    private void addTeamCards(int startY) {
        if (teamOptions.isEmpty()) {
            addLabel(this.width / 2 - 155, startY + 10, 310,
                Component.translatable("wavedefense.auto.u00a7c_aae14fff").append(Component.translatable("wavedefense.pvp.team_select.no_teams")));
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

            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.pvp.team_select.card",
                    option.colorCode(), shorten(option.teamName(), 18),
                    option.spawnIndex() + 1, option.spawn().getSpawnRadius()),
                b -> joinTeam(option.spawnIndex())
            ).bounds(x, y, CARD_W, CARD_H).build());
        }

        int max = maxScrollRows(startY);
        if (max > 0) {
            int x = startX + totalW + 8;
            this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.u25b2_80c50879"), b -> {
                scrollRow = Math.max(0, scrollRow - 1);
                rebuildWidgets();
            }).bounds(x, startY, 22, 20).build()).active = scrollRow > 0;
            this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.u25bc_7a1f8e34"), b -> {
                scrollRow = Math.min(maxScrollRows(startY), scrollRow + 1);
                rebuildWidgets();
            }).bounds(x, Math.min(this.height - 54, startY + visibleRows * (CARD_H + GAP) - 20), 22, 20).build()).active = scrollRow < max;
        }
    }

    private List<TeamOption> buildTeamOptions() {
        // Count how many spawn points share each team name (to detect duplicates)
        Map<String, Integer> nameCount = new java.util.LinkedHashMap<>();
        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        for (PvpSpawnPoint spawn : spawns) {
            String raw = spawn.getTeamName() == null || spawn.getTeamName().isBlank() ? "" : spawn.getTeamName();
            nameCount.merge(raw, 1, Integer::sum);
        }
        // Track per-name occurrence index for suffix labelling
        Map<String, Integer> nameIdx = new java.util.LinkedHashMap<>();

        List<TeamOption> result = new ArrayList<>();
        for (int i = 0; i < spawns.size(); i++) {
            PvpSpawnPoint spawn = spawns.get(i);
            String raw = spawn.getTeamName() == null || spawn.getTeamName().isBlank() ? "" : spawn.getTeamName();
            // Base display name
            String baseName = raw.isBlank()
                ? Component.translatable("wavedefense.pvp.team_select.team_fallback", i + 1).getString()
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
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRows(76));
    }

    private void addLabel(int x, int y, int w, Component text) {
        Button label = Button.builder(text, b -> {})
            .bounds(x, y, w, 16)
            .build();
        label.active = false;
        this.addRenderableWidget(label);
    }

    private void joinTeam(int spawnIndex) {
        PacketHandler.sendToServer(new TeleportPacket(location.getName(), spawnIndex));
        this.onClose();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (location.isBattleRoyale() || teamOptions.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int max = maxScrollRows(76);
        int next = scrollRow + (delta < 0 ? 1 : -1);
        scrollRow = Mth.clamp(next, 0, max);
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);
        int titleColor = switch (location.getPvpMode()) {
            case BATTLE_ROYALE -> 0xFF5555;
            case DEATHMATCH -> 0xFFAA33;
            default -> 0xFF66AAFF;
        };
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, titleColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TeamOption(String teamName, int spawnIndex, PvpSpawnPoint spawn, String colorCode) {}
}
