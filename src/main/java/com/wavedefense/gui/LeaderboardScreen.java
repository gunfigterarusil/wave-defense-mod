package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.LeaderboardManager;
import com.wavedefense.data.LeaderboardRecord;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestLeaderboardPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

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
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


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
        super(new TranslationTextComponent("wavedefense.leaderboard.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // H-7: filter out empty/null location names before adding to list
        locationNames = new ArrayList<>();
        for (String name : ClientLocationManager.getAllLocationNames()) {
            if (name != null && !name.trim().isEmpty()) locationNames.add(name);
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
        this.addButton(new Button(cx - 40, this.height - 28, 80, 20, new TranslationTextComponent("wavedefense.button.close"), b -> this.onClose()));

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
            this.addButton(new Button(bx, 30, btnW, 16, new StringTextComponent(sel ? "§a§l" + name : "§7" + name), b -> { selectedLocation = name; locScrollOffset = 0; rebuild(); requestData(); }));
        }

        // Scroll buttons for locations
        if (locScrollOffset > 0) {
            this.addButton(new Button(cx - this.width / 2 + 5, 30, 16, 16, new StringTextComponent("◀"), b -> { locScrollOffset = Math.max(0, locScrollOffset - 1); rebuild(); }));
        }
        if (locScrollOffset + maxVisible < locationNames.size()) {
            this.addButton(new Button(cx + this.width / 2 - 21, 30, 16, 16, new StringTextComponent("▶"), b -> { locScrollOffset++; rebuild(); }));
        }
    }

    private void buildModeTabs(int cx) {
        // M-2 fix: only show mode tabs that are relevant to the selected location's mode.
        // PvE tab is hidden for PvP locations; all PvP sub-mode tabs are hidden for PvE
        // locations. When the location data is unavailable on the client, all 6 tabs show.
        com.wavedefense.data.Location loc = ClientLocationManager.getLocation(selectedLocation);

        java.util.List<Integer> visible = new java.util.ArrayList<>();
        for (int i = 0; i < MODE_KEYS.length; i++) {
            if (loc == null || isTabRelevant(i, loc)) visible.add(i);
        }

        // If current selectedMode is no longer visible, reset to first visible tab
        if (!visible.isEmpty() && !visible.contains(selectedMode)) {
            selectedMode = visible.get(0);
        }

        int tabW   = 62;
        int tabGap = 2;
        int totalW = visible.size() * tabW + (visible.size() - 1) * tabGap;
        int startX = cx - totalW / 2;

        for (int vi = 0; vi < visible.size(); vi++) {
            final int idx = visible.get(vi);
            boolean sel = (selectedMode == idx);
            this.addButton(new Button(startX + vi * (tabW + tabGap), 52, tabW, 16, new StringTextComponent(sel ? "§a§l" + MODE_LABELS[idx] : "§7" + MODE_LABELS[idx]), b -> { selectedMode = idx; rebuild(); requestData(); }));
        }
    }

    /** Returns true when tab index {@code i} is relevant for the given location. */
    private static boolean isTabRelevant(int i, com.wavedefense.data.Location loc) {
        if (loc == null) return true;
        if (i == 0) return !loc.isPvp();   // PvE tab only for PvE locations
        return loc.isPvp();                // all PvP sub-mode tabs only for PvP locations
    }

    private void requestData() {
        if (selectedLocation == null || selectedLocation.isEmpty()) return;
        ClientLeaderboardCache.setLoading();
        PacketHandler.sendToServer(new RequestLeaderboardPacket(selectedLocation, MODE_KEYS[selectedMode]));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;

        // Title
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        // H-7: show "no locations" message when list is empty
        if (locationNames.isEmpty()) {
            GuiTheme.renderContentFrame(g, 8, 74 - 2, this.width - 8, this.height - 34 + 2);
            super.render(g, mouseX, mouseY, partialTick);
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.leaderboard.no_locations"),
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
        com.wavedefense.gui.GuiCompat.fill(g, cx + COL_RANK - 2, headerY - 1, cx + COL_DATE + 30, headerY + this.font.lineHeight + 1, 0xAA222244);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.leaderboard.rank"),   cx + COL_RANK,  headerY, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.leaderboard.player"), cx + COL_NAME,  headerY, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.leaderboard.score"),  cx + COL_SCORE, headerY, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, getModeSecondaryLabel(),                     cx + COL_SEC,   headerY, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.leaderboard.time"),   cx + COL_TIME,  headerY, 0xAAAAAA);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.leaderboard.date"),   cx + COL_DATE,  headerY, 0xAAAAAA);

        int dataY = headerY + this.font.lineHeight + 4;
        com.wavedefense.gui.GuiCompat.fill(g, cx + COL_RANK - 2, dataY - 1, cx + COL_DATE + 30, dataY, 0xFF444466);

        // ── Rows ──────────────────────────────────────────────────────────
        ScissorHelper.enable(0, dataY, this.width, Math.max(1, tableBot - dataY));

        if (ClientLeaderboardCache.isLoading()) {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.leaderboard.loading"),
                    cx, dataY + 10, GuiTheme.TEXT_MUTED);
        } else {
            List<LeaderboardRecord> records = ClientLeaderboardCache.getRecords();
            if (records.isEmpty()) {
                com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.leaderboard.no_records"),
                        cx, dataY + 10, GuiTheme.TEXT_MUTED);
            } else {
                int rowH = this.font.lineHeight + 4;
                for (int i = 0; i < Math.min(10, records.size()); i++) {
                    LeaderboardRecord rec = records.get(i);
                    int rowY = dataY + i * rowH + 2;
                    boolean even = (i % 2 == 0);
                    com.wavedefense.gui.GuiCompat.fill(g, cx + COL_RANK - 2, rowY - 1, cx + COL_DATE + 30, rowY + this.font.lineHeight + 1,
                            even ? 0x20FFFFFF : 0x10FFFFFF);

                    // L-2: podium colors — gold/silver/bronze (not red for bronze)
                    String rankColor = i == 0 ? "§e" : i == 1 ? "§7" : i == 2 ? "§6" : "§8";
                    // M-6: truncate long player names so they don't overflow into the Score column
                    String displayName = rec.playerName != null ? rec.playerName : "?";
                    if (displayName.length() > MAX_NAME_DISPLAY)
                        displayName = displayName.substring(0, MAX_NAME_DISPLAY - 1) + "…";

                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, rankColor + (i + 1),              cx + COL_RANK,  rowY, GuiTheme.TEXT);
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§f" + displayName,               cx + COL_NAME,  rowY, GuiTheme.TEXT);
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§a" + rec.primaryScore,          cx + COL_SCORE, rowY, GuiTheme.TEXT);
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§e" + rec.secondaryScore,        cx + COL_SEC,   rowY, GuiTheme.TEXT);
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§7" + formatDuration(rec.durationSec), cx + COL_TIME, rowY, GuiTheme.TEXT_MUTED);
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§8" + (rec.timestamp > 0 ? DATE_FMT.format(new Date(rec.timestamp)) : "-"),
                            cx + COL_DATE, rowY, GuiTheme.TEXT_MUTED);
                }
            }
        }

        com.wavedefense.gui.GuiCompat.flush(g);
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
                    rebuild();
                }
            } else if (delta > 0) {
                if (locScrollOffset > 0) {
                    locScrollOffset--;
                    rebuild();
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private String getModeSecondaryLabel() {
        switch (selectedMode) {
    case 0: return I18n.get("wavedefense.leaderboard.waves");
    default: return I18n.get("wavedefense.leaderboard.kills");
}
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
