package com.wavedefense.gui;

import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LeaderboardRecord;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestLeaderboardPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Displays the per-location per-mode leaderboard top-10.
 *
 * Layout:
 *   Title row
 *   Location selector buttons (one row, scrollable)
 *   Mode tabs: PvE | Standard | DM | BR | Capture | KotH
 *   Table: # | Player | Score | <mode col> | Time | Date
 *   10 data rows
 *   Close button
 */
public class LeaderboardScreen extends Screen {

    private static final String[] MODE_KEYS = {
        LeaderboardManager.MODE_PVE,
        LeaderboardManager.MODE_STANDARD,
        LeaderboardManager.MODE_DEATHMATCH,
        LeaderboardManager.MODE_BATTLE_ROYALE,
        LeaderboardManager.MODE_CTP,
        LeaderboardManager.MODE_KOTH
    };
    private static final String[] MODE_LABELS = {
        "PvE", "Standard", "DM", "BR", "Capture", "KotH"
    };

    private final Screen parent;

    private List<String>  locationNames    = new ArrayList<>();
    private String        selectedLocation = "";
    private int           selectedMode     = 0;   // index into MODE_KEYS

    private int locScrollOffset = 0;

    // Column offsets relative to center-x
    private static final int COL_RANK   = -175;
    private static final int COL_NAME   = -160;
    private static final int COL_SCORE  =   20;
    private static final int COL_SEC    =   70;
    private static final int COL_TIME   =  110;
    private static final int COL_DATE   =  140;

    // Max characters for the player name column before truncation (M-6)
    private static final int MAX_NAME_DISPLAY = 14;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yy");

    public LeaderboardScreen(Screen parent) {
        super(Component.translatable("wavedefense.leaderboard.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // H-7: filter out empty/null location names before adding to list
        locationNames = new ArrayList<>();
        for (String name : ClientLocationManager.getAllLocationNames()) {
            if (name != null && !name.isBlank()) locationNames.add(name);
        }
        // Do NOT add "" as fallback — we detect the empty case in render() separately.

        // Select first location if none selected yet or previous selection gone
        if (!locationNames.contains(selectedLocation)) {
            selectedLocation = locationNames.isEmpty() ? "" : locationNames.get(0);
        }

        int cx = this.width / 2;

        // ── Location selector row (only if locations exist) ───────────────
        if (!locationNames.isEmpty()) {
            buildLocationRow(cx);
        }

        // ── Mode tabs ─────────────────────────────────────────────────────
        if (!locationNames.isEmpty()) {
            buildModeTabs(cx);
        }

        // ── Close button ─────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.close"), b -> this.onClose()
        ).bounds(cx - 40, this.height - 28, 80, 20).build());

        // Request data for initial selection
        if (!locationNames.isEmpty()) {
            requestData();
        }
    }

    private void buildLocationRow(int cx) {
        int btnW   = 70;
        int btnGap = 3;
        int maxVisible = Math.max(1, (this.width - 40) / (btnW + btnGap));

        int startX = cx - (Math.min(maxVisible, locationNames.size()) * (btnW + btnGap)) / 2;

        for (int i = 0; i < maxVisible; i++) {
            int idx = i + locScrollOffset;
            if (idx >= locationNames.size()) break;
            final String name = locationNames.get(idx);
            boolean sel = name.equals(selectedLocation);
            int bx = startX + i * (btnW + btnGap);
            this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a§l" + name : "§7" + name),
                    b -> { selectedLocation = name; locScrollOffset = 0; rebuildWidgets(); requestData(); }
            ).bounds(bx, 30, btnW, 16).build());
        }

        // Scroll buttons for locations
        if (locScrollOffset > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("◀"),
                    b -> { locScrollOffset = Math.max(0, locScrollOffset - 1); rebuildWidgets(); }
            ).bounds(cx - this.width / 2 + 5, 30, 16, 16).build());
        }
        if (locScrollOffset + maxVisible < locationNames.size()) {
            this.addRenderableWidget(Button.builder(Component.literal("▶"),
                    b -> { locScrollOffset++; rebuildWidgets(); }
            ).bounds(cx + this.width / 2 - 21, 30, 16, 16).build());
        }
    }

    private void buildModeTabs(int cx) {
        int tabW  = 62;
        int tabGap = 2;
        int totalW = MODE_KEYS.length * tabW + (MODE_KEYS.length - 1) * tabGap;
        int startX = cx - totalW / 2;

        for (int i = 0; i < MODE_KEYS.length; i++) {
            final int idx = i;
            boolean sel = (selectedMode == i);
            this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a§l" + MODE_LABELS[i] : "§7" + MODE_LABELS[i]),
                    b -> { selectedMode = idx; rebuildWidgets(); requestData(); }
            ).bounds(startX + i * (tabW + tabGap), 52, tabW, 16).build());
        }
    }

    private void requestData() {
        if (selectedLocation == null || selectedLocation.isEmpty()) return;
        ClientLeaderboardCache.setLoading();
        PacketHandler.sendToServer(new RequestLeaderboardPacket(selectedLocation, MODE_KEYS[selectedMode]));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;

        // Title
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        // H-7: show "no locations" message when list is empty
        if (locationNames.isEmpty()) {
            GuiTheme.renderContentFrame(g, 8, 74 - 2, this.width - 8, this.height - 34 + 2);
            super.render(g, mouseX, mouseY, partialTick);
            g.drawCenteredString(this.font, I18n.get("wavedefense.leaderboard.no_locations"),
                    cx, this.height / 2 - 5, GuiTheme.TEXT_MUTED);
            return;
        }

        // Content frame
        int tableTop = 74;
        int tableBot = this.height - 34;
        GuiTheme.renderContentFrame(g, 8, tableTop - 2, this.width - 8, tableBot + 2);

        super.render(g, mouseX, mouseY, partialTick);

        // ── Table header ──────────────────────────────────────────────────
        int headerY = tableTop + 2;
        g.fill(cx + COL_RANK - 2, headerY - 1, cx + COL_DATE + 30, headerY + this.font.lineHeight + 1, 0xAA222244);
        g.drawString(this.font, I18n.get("wavedefense.leaderboard.rank"),   cx + COL_RANK,  headerY, 0xAAAAAA);
        g.drawString(this.font, I18n.get("wavedefense.leaderboard.player"), cx + COL_NAME,  headerY, 0xAAAAAA);
        g.drawString(this.font, I18n.get("wavedefense.leaderboard.score"),  cx + COL_SCORE, headerY, 0xAAAAAA);
        g.drawString(this.font, getModeSecondaryLabel(),                     cx + COL_SEC,   headerY, 0xAAAAAA);
        g.drawString(this.font, I18n.get("wavedefense.leaderboard.time"),   cx + COL_TIME,  headerY, 0xAAAAAA);
        g.drawString(this.font, I18n.get("wavedefense.leaderboard.date"),   cx + COL_DATE,  headerY, 0xAAAAAA);

        int dataY = headerY + this.font.lineHeight + 4;
        g.fill(cx + COL_RANK - 2, dataY - 1, cx + COL_DATE + 30, dataY, 0xFF444466);

        // ── Rows ──────────────────────────────────────────────────────────
        ScissorHelper.enable(0, dataY, this.width, Math.max(1, tableBot - dataY));

        if (ClientLeaderboardCache.isLoading()) {
            g.drawCenteredString(this.font, I18n.get("wavedefense.leaderboard.loading"),
                    cx, dataY + 10, GuiTheme.TEXT_MUTED);
        } else {
            List<LeaderboardRecord> records = ClientLeaderboardCache.getRecords();
            if (records.isEmpty()) {
                g.drawCenteredString(this.font, I18n.get("wavedefense.leaderboard.no_records"),
                        cx, dataY + 10, GuiTheme.TEXT_MUTED);
            } else {
                int rowH = this.font.lineHeight + 4;
                for (int i = 0; i < Math.min(10, records.size()); i++) {
                    LeaderboardRecord rec = records.get(i);
                    int rowY = dataY + i * rowH + 2;
                    boolean even = (i % 2 == 0);
                    g.fill(cx + COL_RANK - 2, rowY - 1, cx + COL_DATE + 30, rowY + this.font.lineHeight + 1,
                            even ? 0x20FFFFFF : 0x10FFFFFF);

                    // L-2: podium colors — gold/silver/bronze (not red for bronze)
                    String rankColor = i == 0 ? "§e" : i == 1 ? "§7" : i == 2 ? "§6" : "§8";
                    // M-6: truncate long player names so they don't overflow into the Score column
                    String displayName = rec.playerName != null ? rec.playerName : "?";
                    if (displayName.length() > MAX_NAME_DISPLAY)
                        displayName = displayName.substring(0, MAX_NAME_DISPLAY - 1) + "…";

                    g.drawString(this.font, rankColor + (i + 1),              cx + COL_RANK,  rowY, GuiTheme.TEXT);
                    g.drawString(this.font, "§f" + displayName,               cx + COL_NAME,  rowY, GuiTheme.TEXT);
                    g.drawString(this.font, "§a" + rec.primaryScore,          cx + COL_SCORE, rowY, GuiTheme.TEXT);
                    g.drawString(this.font, "§e" + rec.secondaryScore,        cx + COL_SEC,   rowY, GuiTheme.TEXT);
                    g.drawString(this.font, "§7" + formatDuration(rec.durationSec), cx + COL_TIME, rowY, GuiTheme.TEXT_MUTED);
                    g.drawString(this.font, "§8" + (rec.timestamp > 0 ? DATE_FMT.format(new Date(rec.timestamp)) : "-"),
                            cx + COL_DATE, rowY, GuiTheme.TEXT_MUTED);
                }
            }
        }

        g.flush();
        ScissorHelper.disable();
    }

    /** L-3: Mouse scroll moves the location selector left/right. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!locationNames.isEmpty()) {
            int btnW  = 70;
            int btnGap = 3;
            int maxVisible = Math.max(1, (this.width - 40) / (btnW + btnGap));
            if (delta < 0) {
                if (locScrollOffset + maxVisible < locationNames.size()) {
                    locScrollOffset++;
                    rebuildWidgets();
                }
            } else if (delta > 0) {
                if (locScrollOffset > 0) {
                    locScrollOffset--;
                    rebuildWidgets();
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private String getModeSecondaryLabel() {
        return switch (selectedMode) {
            case 0 -> I18n.get("wavedefense.leaderboard.waves");
            default -> I18n.get("wavedefense.leaderboard.kills");
        };
    }

    private String formatDuration(int totalSec) {
        if (totalSec <= 0) return "-";
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
