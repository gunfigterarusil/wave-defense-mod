package com.wavedefense.gui.universal;

import com.wavedefense.data.Location;
import com.wavedefense.data.LocationMode;
import com.wavedefense.gui.GuiTheme;
import com.wavedefense.gui.ScissorHelper;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Unified location editor — replaces the dual PvE / PvP editor pair with a single
 * screen that has 6 fixed tabs, regardless of mode:
 *
 * <ol>
 *   <li><b>General</b> — name, mode toggle, player spawn, exit points, starting points,
 *       gamemode enforcement, keep-inventory.</li>
 *   <li><b>Gameplay</b> — mode-dependent contents:
 *       PvE = waves + per-wave rewards; PvP = rules per sub-mode.</li>
 *   <li><b>Area</b> — bbox + minimap + outline + boundary (radius + consequence)
 *       + auto-activate zone + portal. Same for PvE and PvP.</li>
 *   <li><b>Economy</b> — shop (global/point) + loot spawns + completion rewards
 *       + per-mode points (kill/death/win/lose).</li>
 *   <li><b>Visual</b> — info panels + zone particles + boundary particles + HUD options.</li>
 *   <li><b>Compat &amp; I/O</b> — import/export, Mine and Slash overrides, Tacz bulk-add.</li>
 * </ol>
 *
 * <p>Switching mode does NOT change tab structure — only the Gameplay tab's
 * content adapts. All other tabs stay consistent so admin's mental model is stable.
 *
 * <p>This screen is added <strong>in parallel</strong> to the existing
 * {@code LocationEditorScreen} and {@code PvpLocationEditorScreen}. The old editors
 * remain available so admins can compare both UIs and report regressions before the
 * old ones are deleted.
 */
@SuppressWarnings("deprecation") // We deep-link into legacy editors for advanced fields.
public class UniversalLocationEditor extends Screen {

    public enum Tab {
        GENERAL  ("wavedefense.editor2.tab.general",  "🏷"),
        GAMEPLAY ("wavedefense.editor2.tab.gameplay", "⚔"),
        AREA     ("wavedefense.editor2.tab.area",     "🗺"),
        ECONOMY  ("wavedefense.editor2.tab.economy",  "💰"),
        VISUAL   ("wavedefense.editor2.tab.visual",   "🎨"),
        COMPAT   ("wavedefense.editor2.tab.compat",   "📦");

        public final String langKey;
        public final String icon;
        Tab(String k, String i) { this.langKey = k; this.icon = i; }
    }

    /** The committed location from ClientLocationManager — never mutated until Save. */
    private final Location original;
    /** Working copy that all tabs read/write. Created by NBT roundtrip on open. */
    private final Location location; // kept named `location` so existing tab code unchanged
    private final Screen parent;
    private Tab activeTab = Tab.GENERAL;
    private int scrollOffset = 0;
    private int contentHeight = 600; // estimated, refined per-tab in init

    // Static widgets — header/footer that don't scroll
    private final Set<AbstractWidget> staticWidgets =
        Collections.newSetFromMap(new IdentityHashMap<>());

    // Layout constants (responsive on init from screen size)
    private static final int HEADER_H = 26;     // title bar
    private static final int TAB_BAR_Y = 32;
    private static final int TAB_BAR_H = 22;
    private static final int CONTENT_TOP_PAD = 4;
    private static final int FOOTER_H = 30;
    private static final int PAD = 16;          // outer horizontal padding
    private static final int MAX_CONTENT_W = 520;
    /** Cached responsive content width — refreshed on each init(). */
    private int colW = MAX_CONTENT_W;
    private int leftX = 0;

    public UniversalLocationEditor(Location location, Screen parent) {
        super(Component.translatable("wavedefense.editor2.title")
            .append(": ").append(location.getName()));
        this.original = location;
        // Deep-copy via NBT roundtrip so Cancel can discard edits cleanly.
        // The NBT path is the canonical serializer used by network packets;
        // anything it loses in transit would also be lost over the wire.
        this.location = Location.load(location.save());
        this.parent = parent;
    }

    /** Marks a widget as static (not subject to scissor clipping or scroll). */
    private <T extends AbstractWidget> T addStatic(T widget) {
        addRenderableWidget(widget);
        staticWidgets.add(widget);
        return widget;
    }

    @Override
    protected void init() {
        super.init();
        staticWidgets.clear();
        resetEditBoxRefs(); // null stale refs before tab re-populates
        int cx = this.width / 2;
        // Responsive content width
        this.colW = Math.min(MAX_CONTENT_W, this.width - 2 * PAD);
        this.leftX = cx - this.colW / 2;

        // ── Tab bar (fixed, static) ─────────────────────────────────────
        Tab[] tabs = Tab.values();
        int tabBarMaxW = Math.min(this.width - 20, 720);
        int tabW = Math.max(80, tabBarMaxW / tabs.length - 2);
        int totalW = tabW * tabs.length + 2 * (tabs.length - 1);
        int tabBarX = cx - totalW / 2;
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            boolean active = (t == activeTab);
            String label = (active ? "§e§l" : "§7") + t.icon + " " + I18n.get(t.langKey);
            Button btn = Button.builder(
                Component.literal(label),
                b -> { activeTab = t; scrollOffset = 0;
                       // QW1: discard any in-progress spawn-edit form so it doesn't
                       // resurrect with empty boxes on return to Gameplay tab.
                       spawnEditing = false; spawnEditingIndex = -1;
                       rebuildWidgets(); }
            ).bounds(tabBarX + i * (tabW + 2), TAB_BAR_Y, tabW, TAB_BAR_H).build();
            addStatic(btn);
        }

        // ── Save + Cancel (static footer, responsive width) ─────────────
        int footerY = this.height - FOOTER_H + 5;
        int btnW = Math.min(120, (this.width - 80) / 2);
        addStatic(Button.builder(
            Component.translatable("wavedefense.button.save"),
            b -> save()
        ).bounds(cx - btnW - 10, footerY, btnW, 20).build());
        addStatic(Button.builder(
            Component.translatable("wavedefense.button.cancel"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, footerY, btnW, 20).build());

        // ── Tab content ─────────────────────────────────────────────────
        int contentTop = TAB_BAR_Y + TAB_BAR_H + CONTENT_TOP_PAD;
        int contentY = contentTop - scrollOffset; // apply scroll
        switch (activeTab) {
            case GENERAL  -> contentHeight = initGeneralTab(cx, contentY)  - contentY + 20;
            case GAMEPLAY -> contentHeight = initGameplayTab(cx, contentY) - contentY + 20;
            case AREA     -> contentHeight = initAreaTab(cx, contentY)     - contentY + 20;
            case ECONOMY  -> contentHeight = initEconomyTab(cx, contentY)  - contentY + 20;
            case VISUAL   -> contentHeight = initVisualTab(cx, contentY)   - contentY + 20;
            case COMPAT   -> contentHeight = initCompatTab(cx, contentY)   - contentY + 20;
        }
    }

    // ─── Tab implementations ─────────────────────────────────────────────
    // Each method returns the Y position immediately AFTER the last widget,
    // so the caller can compute total content height for scrolling.

    // ─── Gameplay tab — mode-aware ───────────────────────────────────────
    private int initGameplayTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;

        if (location.getMode() == LocationMode.PVE) {
            // ── PvE: waves summary + navigation buttons ─────────────────────
            section(leftCol, y, colW, "wavedefense.editor2.section.waves");
            y += 18;

            int waves = location.getWaves().size();
            int mobs  = location.getWaves().stream().mapToInt(w -> w.getMobs().size()).sum();
            int triggers = (int) location.getWaves().stream().filter(com.wavedefense.data.WaveConfig::isTriggerEnabled).count();

            stat(leftCol, y, colW, "wavedefense.editor2.gameplay.waves_count", waves); y += 16;
            stat(leftCol, y, colW, "wavedefense.editor2.gameplay.mobs_count", mobs);    y += 16;
            stat(leftCol, y, colW, "wavedefense.editor2.gameplay.triggers_count", triggers); y += 20;

            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.gameplay.edit_waves"),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.WaveConfigScreen(location, this))
            ).bounds(leftCol, y, colW, 20).build()); y += 24;

            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.gameplay.edit_rewards"),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.CompletionRewardScreen(location, this))
            ).bounds(leftCol, y, colW, 20).build()); y += 26;

        } else {
            // ── PvP: sub-mode picker + key inline toggles + deep-edit nav ────
            section(leftCol, y, colW, "wavedefense.editor2.section.pvp_mode");
            y += 18;

            com.wavedefense.data.Location.PvpMode curMode = location.getPvpMode();
            com.wavedefense.data.Location.PvpMode[] modes = com.wavedefense.data.Location.PvpMode.values();
            String[] keys = {
                "wavedefense.editor2.gameplay.pvp_standard",
                "wavedefense.editor2.gameplay.pvp_dm",
                "wavedefense.editor2.gameplay.pvp_br",
                "wavedefense.editor2.gameplay.pvp_ctp",
                "wavedefense.editor2.gameplay.pvp_koth"
            };
            int btnW = colW / modes.length - 2;
            for (int i = 0; i < modes.length; i++) {
                final com.wavedefense.data.Location.PvpMode m = modes[i];
                boolean sel = (m == curMode);
                this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a§l✓ " : "§7○ ").append(Component.translatable(keys[i])),
                    b -> { location.setPvpMode(m); rebuildWidgets(); }
                ).bounds(leftCol + i * (btnW + 2), y, btnW, 20).build());
            }
            y += 26;

            // Common inline rules (always editable, applies to every PvP sub-mode)
            int halfW = colW / 2 - 2;
            // Min players (EditBox)
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.gameplay.min_players") + ":"),
                b -> {}
            ).bounds(leftCol, y, 110, 18).build()).active = false;
            pvpMinPlayersBox = new net.minecraft.client.gui.components.EditBox(
                this.font, leftCol + 114, y, 50, 18, Component.literal("2"));
            pvpMinPlayersBox.setValue(String.valueOf(location.getPvpMinPlayers()));
            pvpMinPlayersBox.setMaxLength(3);
            this.addRenderableWidget(pvpMinPlayersBox);
            y += 22;
            // Friendly fire + Auto-balance (2-col grid)
            boolean ff = location.isPvpFriendlyFire();
            this.addRenderableWidget(Button.builder(
                Component.literal((ff ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.gameplay.friendly_fire")),
                b -> { location.setPvpFriendlyFire(!location.isPvpFriendlyFire()); rebuildWidgets(); }
            ).bounds(leftCol, y, halfW, 18).build());
            boolean ab = location.isPvpTeamAutoBalance();
            this.addRenderableWidget(Button.builder(
                Component.literal((ab ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.gameplay.auto_balance")),
                b -> { location.setPvpTeamAutoBalance(!location.isPvpTeamAutoBalance()); rebuildWidgets(); }
            ).bounds(leftCol + halfW + 4, y, halfW, 18).build());
            y += 22;
            // Wait effect (single full-width)
            boolean we = location.isPvpWaitEffect();
            this.addRenderableWidget(Button.builder(
                Component.literal((we ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.gameplay.wait_effect")),
                b -> { location.setPvpWaitEffect(!location.isPvpWaitEffect()); rebuildWidgets(); }
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
            // v0.2.61: Ready-check timeout EditBox
            pvpReadyTimeoutBox = labelledIntRow(leftCol, y, colW,
                "wavedefense.editor2.gameplay.ready_timeout",
                location.getPvpReadyCheckTimeoutSec());
            y += 24;

            // ── Phase A: full inline per-sub-mode rules (v0.2.59) ─────────
            section(leftCol, y, colW, "wavedefense.editor2.section.pvp_rules");
            y += 18;
            y = initPvpSubModeRules(leftCol, y, colW, curMode);

            // ── Phase D: spawn-point inline list (v0.2.59) ────────────────
            y = initPvpSpawnPointSection(leftCol, y, colW);

            // ── Phase C: capture-points opener for CtP/KotH (v0.2.59) ─────
            if (curMode == com.wavedefense.data.Location.PvpMode.CAPTURE_THE_POINT
             || curMode == com.wavedefense.data.Location.PvpMode.KING_OF_THE_HILL) {
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.editor2.gameplay.open_capture_points"),
                    b -> this.minecraft.setScreen(new com.wavedefense.gui.CapturePointEditorScreen(location, this))
                ).bounds(leftCol, y, colW, 20).build());
                y += 24;
                int pts = location.getCapturePoints().size();
                this.addRenderableWidget(Button.builder(
                    Component.literal((pts == 0 ? "§c⚠ " : "§7") + "Capture points: §e" + pts),
                    b -> {}
                ).bounds(leftCol, y, colW, 12).build()).active = false;
                y += 16;
            }
        }
        return y;
    }

    // ─── Phase A: PvP per-sub-mode inline rules ─────────────────────────
    /** Renders editable EditBoxes for every field of the currently-selected
     *  PvP sub-mode. Replaces the v0.2.58 read-only summary + deep-link. */
    private int initPvpSubModeRules(int leftCol, int y, int colW, com.wavedefense.data.Location.PvpMode mode) {
        switch (mode) {
            case STANDARD -> {
                stdTotalRoundsBox      = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.total_rounds",      location.getPvpTotalRounds()); y += 22;
                stdBuyTimeBox          = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.buy_time",          location.getPvpBuyTime());     y += 22;
                stdRoundDelayBox       = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.round_delay",       location.getPvpRoundStartDelay()); y += 22;
                stdRoundStartPointsBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.round_start_pts",   location.getPvpRoundStartPoints()); y += 22;
                stdWinPointsBox        = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.win_pts",           location.getPvpWinPoints());   y += 22;
                stdLosePointsBox       = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.lose_pts",          location.getPvpLosePoints());  y += 22;
                stdRoundTimeLimitBox   = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.round_time_limit",  location.getPvpRoundTimeLimitSec()); y += 22;
            }
            case DEATHMATCH -> {
                dmKillsToWinBox     = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.kills_to_win",     location.getDmKillsToWin()); y += 22;
                dmMatchTimeLimitBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.match_time_limit", location.getPvpRoundTimeLimitSec()); y += 22;
                // DM spawn mode cycle button
                com.wavedefense.data.Location.DmSpawnMode sm = location.getDmSpawnMode();
                String smLabel = switch (sm) {
                    case RANDOM_SPAWN -> "RANDOM";
                    case SMART_SPAWN  -> "SMART";
                    default           -> "TEAM";
                };
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + I18n.get("wavedefense.editor2.pvp.dm_spawn_mode") + ": §e" + smLabel + " §7(click to cycle)"),
                    b -> {
                        com.wavedefense.data.Location.DmSpawnMode next = switch (location.getDmSpawnMode()) {
                            case TEAM_SPAWN   -> com.wavedefense.data.Location.DmSpawnMode.RANDOM_SPAWN;
                            case RANDOM_SPAWN -> com.wavedefense.data.Location.DmSpawnMode.SMART_SPAWN;
                            default           -> com.wavedefense.data.Location.DmSpawnMode.TEAM_SPAWN;
                        };
                        location.setDmSpawnMode(next); rebuildWidgets();
                    }
                ).bounds(leftCol, y, colW, 18).build());
                y += 22;
            }
            case BATTLE_ROYALE -> {
                brBorderRadiusBox   = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_border_radius",  location.getBrBorderRadius());   y += 22;
                brShrinkIntervalBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_shrink_interval", location.getBrShrinkIntervalSec()); y += 22;
                brShrinkAmountBox   = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_shrink_amount",   location.getBrShrinkAmountBlocks()); y += 22;
                brInitialWaitBox    = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_initial_wait",    location.getBrInitialWaitSec()); y += 22;
                brFinalRadiusBox    = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_final_radius",    location.getBrFinalRadius());    y += 22;
                brParticleCountBox  = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.br_particle_count",  location.getBrBorderParticleCount()); y += 22;
                // Particle id (string)
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + I18n.get("wavedefense.editor2.pvp.br_particle_id") + ":"), b -> {}
                ).bounds(leftCol, y, 140, 18).build()).active = false;
                brParticleIdBox = new net.minecraft.client.gui.components.EditBox(
                    this.font, leftCol + 144, y, colW - 144, 18, Component.literal("minecraft:flame"));
                brParticleIdBox.setValue(location.getBrBorderParticle());
                brParticleIdBox.setMaxLength(64);
                this.addRenderableWidget(brParticleIdBox);
                y += 22;
                // Damage toggle + amount
                boolean dmg = location.isBrBorderDamage();
                this.addRenderableWidget(Button.builder(
                    Component.literal((dmg ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.pvp.br_damage")),
                    b -> { location.setBrBorderDamage(!location.isBrBorderDamage()); rebuildWidgets(); }
                ).bounds(leftCol, y, colW / 2 - 2, 18).build());
                if (dmg) {
                    brDamageAmtBox = new net.minecraft.client.gui.components.EditBox(
                        this.font, leftCol + colW / 2 + 4, y, 60, 18, Component.literal("1.0"));
                    brDamageAmtBox.setValue(String.format("%.1f", location.getBrBorderDamageAmt()));
                    brDamageAmtBox.setMaxLength(5);
                    this.addRenderableWidget(brDamageAmtBox);
                    this.addRenderableWidget(Button.builder(
                        Component.literal("§8HP/s"), b -> {}
                    ).bounds(leftCol + colW / 2 + 68, y, 50, 18).build()).active = false;
                }
                y += 22;
            }
            case CAPTURE_THE_POINT, KING_OF_THE_HILL -> {
                boolean isCtp = (mode == com.wavedefense.data.Location.PvpMode.CAPTURE_THE_POINT);
                objScoreToWinBox  = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.score_to_win",  location.getObjectiveScoreToWin()); y += 22;
                objScorePerSecBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.score_per_sec", location.getObjectiveScorePerSec()); y += 22;
                boolean firstToScore = location.isObjectiveFirstToScore();
                this.addRenderableWidget(Button.builder(
                    Component.translatable(firstToScore
                        ? "wavedefense.ctp.win_mode.first" : "wavedefense.ctp.win_mode.timer"),
                    b -> {
                        if (isCtp) location.setCtpFirstToScore(!location.isCtpFirstToScore());
                        else        location.setKothFirstToScore(!location.isKothFirstToScore());
                        rebuildWidgets();
                    }
                ).bounds(leftCol, y, colW, 18).build());
                y += 22;
                if (!firstToScore) {
                    objRoundDurationBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.pvp.round_duration", location.getObjectiveRoundDurationSec()); y += 22;
                }
                if (isCtp) {
                    boolean spd = location.isCtpSpeedMultiplier();
                    this.addRenderableWidget(Button.builder(
                        Component.translatable(spd ? "wavedefense.ctp.speed_multiplier.on" : "wavedefense.ctp.speed_multiplier.off"),
                        b -> { location.setCtpSpeedMultiplier(!location.isCtpSpeedMultiplier()); rebuildWidgets(); }
                    ).bounds(leftCol, y, colW, 18).build());
                    y += 22;
                    boolean aw = location.isCtpCaptureAllWin();
                    this.addRenderableWidget(Button.builder(
                        Component.translatable(aw ? "wavedefense.ctp.capture_all_win.on" : "wavedefense.ctp.capture_all_win.off"),
                        b -> { location.setCtpCaptureAllWin(!location.isCtpCaptureAllWin()); rebuildWidgets(); }
                    ).bounds(leftCol, y, colW, 18).build());
                    y += 22;
                } else {
                    // KotH-only: hold mode + duration + reset-on-loss
                    boolean hm = location.isKothHoldMode();
                    this.addRenderableWidget(Button.builder(
                        Component.translatable(hm ? "wavedefense.koth.hold_mode.on" : "wavedefense.koth.hold_mode.off"),
                        b -> { location.setKothHoldMode(!location.isKothHoldMode()); rebuildWidgets(); }
                    ).bounds(leftCol, y, colW, 18).build());
                    y += 22;
                    if (hm) {
                        kothHoldDurationBox = labelledIntRow(leftCol, y, colW, "wavedefense.koth.hold_duration", location.getKothHoldDurationSec()); y += 22;
                        boolean ro = location.isKothResetOnLoss();
                        this.addRenderableWidget(Button.builder(
                            Component.translatable(ro ? "wavedefense.koth.reset_on_loss.on" : "wavedefense.koth.reset_on_loss.off"),
                            b -> { location.setKothResetOnLoss(!location.isKothResetOnLoss()); rebuildWidgets(); }
                        ).bounds(leftCol, y, colW, 18).build());
                        y += 22;
                    }
                }
            }
        }
        y += 4;
        return y;
    }

    /** Builds a "[label] [_____]" row with a 140px label and EditBox filling the rest. */
    private net.minecraft.client.gui.components.EditBox labelledIntRow(int x, int y, int w, String labelKey, int initial) {
        return labelledIntRowT(x, y, w, labelKey, initial, null);
    }

    /** v0.2.63: tooltip-aware variant. By convention, when {@code tooltipKey} is
     *  null the function auto-derives one: {@code labelKey + ".tooltip"} and
     *  checks if that key exists in the active lang file via {@link I18n#exists}.
     *  This means most callers can keep using {@link #labelledIntRow} unchanged
     *  and the tooltip appears as soon as the translation key is added. */
    private net.minecraft.client.gui.components.EditBox labelledIntRowT(int x, int y, int w, String labelKey, int initial, String tooltipKey) {
        int labelW = 160;
        Button label = Button.builder(
            Component.literal("§7" + I18n.get(labelKey) + ":"), b -> {}
        ).bounds(x, y, labelW, 18).build();
        label.active = false;
        // Auto-derived tooltip key — silently no-op if key missing in lang file
        String tk = (tooltipKey != null) ? tooltipKey : (labelKey + ".tooltip");
        if (net.minecraft.client.resources.language.I18n.exists(tk)) {
            label.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tk)));
        }
        this.addRenderableWidget(label);
        net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(
            this.font, x + labelW + 4, y, Math.min(80, w - labelW - 8), 18, Component.literal("0"));
        box.setValue(String.valueOf(initial));
        box.setMaxLength(7);
        // Same tooltip on the EditBox so hovering either part works
        if (net.minecraft.client.resources.language.I18n.exists(tk)) {
            box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tk)));
        }
        this.addRenderableWidget(box);
        return box;
    }

    // ─── Phase D: PvP spawn-point inline list + edit form ────────────────
    private int initPvpSpawnPointSection(int leftCol, int y, int colW) {
        section(leftCol, y, colW, "wavedefense.editor2.section.spawns");
        y += 18;
        if (spawnEditing) return initSpawnEditForm(leftCol, y, colW);

        java.util.List<com.wavedefense.data.PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        // "+ Add" button
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.spawns.add"),
            b -> { spawnEditing = true; spawnEditingIndex = -1; rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;

        if (spawns.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c⚠ " + I18n.get("wavedefense.editor2.spawns.none")), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 18;
            return y;
        }

        int perPage = 5;
        int rowH = 22;
        int start = Math.min(spawnListScrollOffset, Math.max(0, spawns.size() - perPage));
        for (int i = 0; i < Math.min(perPage, spawns.size() - start); i++) {
            final int idx = start + i;
            com.wavedefense.data.PvpSpawnPoint sp = spawns.get(idx);
            net.minecraft.core.BlockPos p = sp.getPos();
            String label = String.format("§e%s §7X%d Y%d Z%d%s",
                sp.getTeamName(), p.getX(), p.getY(), p.getZ(),
                sp.getSpawnRadius() > 0 ? " §8(R:" + sp.getSpawnRadius() + ")" : "");
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {})
                .bounds(leftCol, y, colW - 60, 18).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.literal("§e✎"),
                b -> { spawnEditing = true; spawnEditingIndex = idx; rebuildWidgets(); }
            ).bounds(leftCol + colW - 56, y, 26, 18).build());
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕"),
                b -> { location.removePvpSpawnPoint(idx); rebuildWidgets(); }
            ).bounds(leftCol + colW - 28, y, 26, 18).build());
            y += rowH;
        }
        if (spawns.size() > perPage) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (spawnListScrollOffset > 0) { spawnListScrollOffset--; rebuildWidgets(); } }
            ).bounds(leftCol + colW - 22, y - perPage * rowH, 20, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (spawnListScrollOffset + perPage < spawns.size()) { spawnListScrollOffset++; rebuildWidgets(); } }
            ).bounds(leftCol + colW - 22, y - 18, 20, 18).build());
        }
        return y;
    }

    /** Inline edit form for a single spawn point (new or existing). */
    private int initSpawnEditForm(int leftCol, int y, int colW) {
        java.util.List<com.wavedefense.data.PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        com.wavedefense.data.PvpSpawnPoint existing =
            (spawnEditingIndex >= 0 && spawnEditingIndex < spawns.size()) ? spawns.get(spawnEditingIndex) : null;

        // Team name
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.spawns.team_name") + ":"), b -> {}
        ).bounds(leftCol, y, 110, 18).build()).active = false;
        spawnNameBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 114, y, colW - 118, 18, Component.literal("team"));
        spawnNameBox.setValue(existing != null ? existing.getTeamName() : "");
        spawnNameBox.setMaxLength(32);
        this.addRenderableWidget(spawnNameBox);
        y += 22;

        // Coords + radius via CoordinatePickerWidget (withRadius=true for scatter)
        net.minecraft.core.BlockPos initialPos = existing != null ? existing.getPos() : null;
        int initialR = existing != null ? existing.getSpawnRadius() : 0;
        // We use the picker just for display + parsing; commit happens in Save handler.
        spawnCoordPicker = new com.wavedefense.gui.widgets.CoordinatePickerWidget(
            this.font, leftCol, y, initialPos, initialR, true, r -> {});
        spawnCoordPicker.addToScreen(this::addRenderableWidget);
        y += 22;

        // v0.2.64: Per-team starting items opener (only when editing existing spawn —
        // newly-being-added spawn isn't in the list yet so we can't open the editor
        // against its items list. Save first then re-open to edit items.)
        if (existing != null) {
            int teamItemCount = existing.getStartingItems().size();
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.spawns.edit_items", teamItemCount),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.StartingItemsScreen(
                    this, existing.getStartingItems(), existing.getTeamName()))
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
        } else {
            this.addRenderableWidget(Button.builder(
                Component.literal("§8" + I18n.get("wavedefense.editor2.spawns.items_save_first")),
                b -> {}
            ).bounds(leftCol, y, colW, 12).build()).active = false;
            y += 16;
        }

        // Save / Cancel buttons
        int halfW = colW / 2 - 4;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.save"),
            b -> saveSpawnForm()
        ).bounds(leftCol, y, halfW, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.cancel"),
            b -> { spawnEditing = false; spawnEditingIndex = -1; rebuildWidgets(); }
        ).bounds(leftCol + halfW + 8, y, halfW, 20).build());
        y += 24;
        return y;
    }

    private void saveSpawnForm() {
        String name = spawnNameBox != null ? spawnNameBox.getValue().trim() : "";
        if (name.isEmpty()) return; // silently keep the form open
        com.wavedefense.gui.widgets.CoordinatePickerWidget.Result r =
            spawnCoordPicker != null ? spawnCoordPicker.getValue() : null;

        // QW2: smart fallback — when editing existing spawn and user didn't touch
        // coords (picker returns null pos), keep the original coords. When creating
        // new and picker is empty, fall back to player position so admin doesn't
        // have to type or click 📌 explicitly.
        boolean isEdit = spawnEditingIndex >= 0 && spawnEditingIndex < location.getPvpSpawnPoints().size();
        net.minecraft.core.BlockPos pos = (r != null && r.pos != null) ? r.pos
            : (isEdit ? location.getPvpSpawnPoints().get(spawnEditingIndex).getPos()
                      : (minecraft.player != null ? minecraft.player.blockPosition() : null));
        if (pos == null) return;
        int radius = (r != null && r.pos != null) ? r.radius
            : (isEdit ? location.getPvpSpawnPoints().get(spawnEditingIndex).getSpawnRadius() : 0);

        if (isEdit) {
            com.wavedefense.data.PvpSpawnPoint sp = location.getPvpSpawnPoints().get(spawnEditingIndex);
            sp.setTeamName(name);
            sp.setPos(pos);
            sp.setSpawnRadius(radius);
        } else {
            com.wavedefense.data.PvpSpawnPoint sp = new com.wavedefense.data.PvpSpawnPoint(name, pos);
            sp.setSpawnRadius(radius);
            location.addPvpSpawnPoint(sp);
        }
        spawnEditing = false;
        spawnEditingIndex = -1;
        rebuildWidgets();
    }

    // ─── Economy tab ──────────────────────────────────────────────────────
    private int initEconomyTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;

        section(leftCol, y, colW, "wavedefense.editor2.section.shop");
        y += 18;
        int shopItems = location.getShopItems().size();
        int shopPts   = location.getShopPoints().size();
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Items: §e" + shopItems + "  §7Shop points: §e" + shopPts),
            b -> {}
        ).bounds(leftCol, y, colW, 14).build()).active = false;
        y += 18;
        // B2 v0.2.60: shopMode cycle (GLOBAL ↔ POINT)
        boolean isPoint = location.isPointShopMode();
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.economy.shop_mode")
                + ": §e" + I18n.get(isPoint ? "wavedefense.editor2.economy.shop_mode.point"
                                            : "wavedefense.editor2.economy.shop_mode.global")
                + " §7(click)"),
            b -> { location.setShopMode(isPoint
                ? com.wavedefense.data.Location.ShopMode.GLOBAL
                : com.wavedefense.data.Location.ShopMode.POINT); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.economy.open_shop"),
            b -> this.minecraft.setScreen(new com.wavedefense.gui.ShopEditorScreen(location, this))
        ).bounds(leftCol, y, colW, 20).build()); y += 26;

        section(leftCol, y, colW, "wavedefense.editor2.section.loot");
        y += 18;
        int loots = location.getLootSpawns().size();
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Loot points: §e" + loots), b -> {}
        ).bounds(leftCol, y, colW, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.economy.open_loot"),
            b -> this.minecraft.setScreen(new com.wavedefense.gui.LootSpawnEditorScreen(location, this))
        ).bounds(leftCol, y, colW, 20).build()); y += 26;

        // ── Phase C: Starting items opener (PvE only) ──────────────────
        if (location.getMode() == LocationMode.PVE) {
            section(leftCol, y, colW, "wavedefense.editor2.economy.starting_items");
            y += 18;
            int si = location.getStartingItems() != null ? location.getStartingItems().size() : 0;
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Starting items: §e" + si), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.economy.open_starting_items"),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.StartingItemsScreen(this, location))
            ).bounds(leftCol, y, colW, 20).build()); y += 26;
        }

        if (location.getMode() == LocationMode.PVE) {
            section(leftCol, y, colW, "wavedefense.editor2.section.points");
            y += 18;
            int rew = location.getCompletionRewards().size();
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Completion rewards: §e" + rew), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.economy.open_rewards"),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.CompletionRewardScreen(location, this))
            ).bounds(leftCol, y, colW, 20).build()); y += 26;
        }

        // Points read-only summary (full edit in PvP rules deep editor; PvE has wave rewards)
        section(leftCol, y, colW, "wavedefense.editor2.section.points");
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.economy.starting_pts")
                + ": §e" + location.getStartingPoints()), b -> {}
        ).bounds(leftCol, y, colW, 14).build()).active = false;
        y += 18;
        if (location.getMode() == LocationMode.PVP) {
            // QW3: inline EditBoxes for kill/death/win/lose/round-start points
            // (was: read-only summary + legacy deep-link). Closes the last
            // legacy-only PvP workflow.
            ecoKillPtsBox       = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.economy.kill_pts",   location.getPvpKillPoints());       y += 22;
            ecoDeathPenaltyBox  = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.economy.death_pen",  location.getPvpDeathPenalty());     y += 22;
            // Round-related points are Standard-only (DM has no rounds)
            if (location.getPvpMode() == com.wavedefense.data.Location.PvpMode.STANDARD) {
                ecoWinPtsBox        = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.economy.win_pts",      location.getPvpWinPoints());        y += 22;
                ecoLosePtsBox       = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.economy.lose_pts",     location.getPvpLosePoints());       y += 22;
                ecoRoundStartPtsBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.economy.round_start",  location.getPvpRoundStartPoints()); y += 22;
            }
        }
        y += 6;
        return y;
    }

    // ─── Visual tab ───────────────────────────────────────────────────────
    private int initVisualTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;

        // Info panels — now available for BOTH PvE and PvP locations
        sectionWithReset(leftCol, y, colW, "wavedefense.editor2.section.infopanels", () -> {
            com.wavedefense.data.InfoPanelSettings rIp = location.getInfoPanel();
            // Reset to "all panels on, defaults" — admin convenience
            rIp.setSpawnPanelEnabled(true);
            rIp.setMobSpawnPanelEnabled(true);
            rIp.setShowPlayerCount(true);
            rIp.setShowWaveNumber(true);
            rIp.setShowWaveTimer(true);
            rIp.setShowMobsRemaining(true);
            rIp.setShowSecretCount(false);
            rIp.setShowShopSecrets(false);
            rIp.setShowPoints(true);
            rIp.setShowFirstWaveTimer(true);
            rIp.setSpawnPanelOffsetY(1.0f);
            rIp.setTextScale(1.0f);
            rIp.setHasShadow(true);
        });
        y += 18;
        com.wavedefense.data.InfoPanelSettings ip = location.getInfoPanel();
        // Master toggle — spawn panel (the in-world floating panel container)
        boolean ipOn = ip.isSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal((ipOn ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.visual.spawn_panel")),
            b -> { ip.setSpawnPanelEnabled(!ip.isSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build()); y += 22;

        // Spawn panel offset Y (EditBox)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.visual.offset_y") + ":"), b -> {}
        ).bounds(leftCol, y, 110, 18).build()).active = false;
        spawnPanelOffsetYBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 114, y, 70, 18, Component.literal("1.0"));
        spawnPanelOffsetYBox.setValue(String.format("%.1f", ip.getSpawnPanelOffsetY()));
        spawnPanelOffsetYBox.setMaxLength(5);
        this.addRenderableWidget(spawnPanelOffsetYBox);
        y += 24;

        // 8 granular flags as a 2×4 toggle grid
        int gridCol = colW / 2 - 2;
        String[][] flags = {
            {"player_count",   String.valueOf(ip.isShowPlayerCount())},
            {"wave_number",    String.valueOf(ip.isShowWaveNumber())},
            {"wave_timer",     String.valueOf(ip.isShowWaveTimer())},
            {"mobs_remaining", String.valueOf(ip.isShowMobsRemaining())},
            {"secret_count",   String.valueOf(ip.isShowSecretCount())},
            {"shop_secrets",   String.valueOf(ip.isShowShopSecrets())},
            {"points",         String.valueOf(ip.isShowPoints())},
            {"first_wave_timer", String.valueOf(ip.isShowFirstWaveTimer())}
        };
        for (int i = 0; i < flags.length; i++) {
            final int fi = i;
            boolean v = Boolean.parseBoolean(flags[i][1]);
            int colX = (i % 2 == 0) ? leftCol : leftCol + gridCol + 4;
            int rowY = y + (i / 2) * 20;
            this.addRenderableWidget(Button.builder(
                Component.literal((v ? "§a✓ " : "§7○ ")
                    + I18n.get("wavedefense.editor2.visual.flag." + flags[fi][0])),
                b -> { toggleInfoPanelFlag(ip, fi); rebuildWidgets(); }
            ).bounds(colX, rowY, gridCol, 18).build());
        }
        y += ((flags.length + 1) / 2) * 20 + 6;
        // B12 v0.2.60: mobSpawnPanelEnabled toggle (separate panel)
        boolean msp = ip.isMobSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal((msp ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.visual.mob_spawn_panel")),
            b -> { ip.setMobSpawnPanelEnabled(!ip.isMobSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;
        // B11 v0.2.60: textScale EditBox + hasShadow toggle (2-col)
        int halfW2 = colW / 2 - 2;
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.visual.text_scale") + ":"), b -> {}
        ).bounds(leftCol, y, 100, 18).build()).active = false;
        infoPanelTextScaleBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 104, y, 60, 18, Component.literal("1.0"));
        infoPanelTextScaleBox.setValue(String.format("%.2f", ip.getTextScale()));
        infoPanelTextScaleBox.setMaxLength(5);
        this.addRenderableWidget(infoPanelTextScaleBox);
        boolean hs = ip.isHasShadow();
        this.addRenderableWidget(Button.builder(
            Component.literal((hs ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.visual.text_shadow")),
            b -> { ip.setHasShadow(!ip.isHasShadow()); rebuildWidgets(); }
        ).bounds(leftCol + 170, y, colW - 170, 18).build());
        y += 24;

        // Boundary / zone particle settings (delegated — many fields)
        section(leftCol, y, colW, "wavedefense.editor2.section.particles");
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Boundary: §e" + location.getBoundaryParticleType()
                + "  §7Count: §e" + location.getBoundaryParticleCount()), b -> {}
        ).bounds(leftCol, y, colW, 14).build()).active = false;
        y += 18;
        if (location.getMode() == LocationMode.PVP) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7BR border: §e" + location.getBrBorderParticle()
                    + "  §7Count: §e" + location.getBrBorderParticleCount()), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 18;
        }
        y += 4;

        // HUD note (HUD is per-player, see PlayerSettingsScreen)
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.visual.hud_note"), b -> {}
        ).bounds(leftCol, y, colW, 14).build()).active = false;
        y += 20;
        return y;
    }

    // ─── Compat & I/O tab ─────────────────────────────────────────────────
    private int initCompatTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;

        section(leftCol, y, colW, "wavedefense.editor2.section.io");
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.compat.open_import"),
            b -> this.minecraft.setScreen(new com.wavedefense.gui.ImportExportScreen(this))
        ).bounds(leftCol, y, colW, 20).build()); y += 26;

        // Mine and Slash
        sectionWithReset(leftCol, y, colW, "wavedefense.editor2.section.mns", () -> {
            // Reset all MnS overrides to 0 (no override)
            location.setMasLevel(0);
            location.setMasXpBonus(0);
            location.setMasFireResist(0);
            location.setMasWaterResist(0);
            location.setMasLightningResist(0);
            location.setMasChaosResist(0);
            location.setMasPhysicalResist(0);
        });
        y += 18;
        if (com.wavedefense.compat.MineAndSlashCompat.isLoaded()) {
            // 2-col grid of small labelled EditBoxes: Level | XP%, then 5 resists
            int labelW = 60, fieldW = 50, gap = 6;
            int rowH = 20;
            int row1Y = y;
            masLevelBox = masEditRow(leftCol, row1Y, labelW, fieldW,
                "wavedefense.editor2.compat.mas_level", location.getMasLevel());
            masXpBox    = masEditRow(leftCol + labelW + fieldW + gap + 14, row1Y, labelW, fieldW,
                "wavedefense.editor2.compat.mas_xp", location.getMasXpBonus());
            y = row1Y + rowH;
            // 5 resists in a 2-col flow
            int[][] resists = {
                {location.getMasFireResist(), 0},
                {location.getMasWaterResist(), 1},
                {location.getMasLightningResist(), 2},
                {location.getMasChaosResist(), 3},
                {location.getMasPhysicalResist(), 4}
            };
            String[] resKeys = {
                "wavedefense.editor2.compat.mas_fire",
                "wavedefense.editor2.compat.mas_water",
                "wavedefense.editor2.compat.mas_lightning",
                "wavedefense.editor2.compat.mas_chaos",
                "wavedefense.editor2.compat.mas_physical"
            };
            net.minecraft.client.gui.components.EditBox[] resBoxes = new net.minecraft.client.gui.components.EditBox[5];
            for (int i = 0; i < 5; i++) {
                int col = i % 2;
                int row = i / 2;
                int rx = (col == 0) ? leftCol : leftCol + labelW + fieldW + gap + 14;
                int ry = y + row * rowH;
                resBoxes[i] = masEditRow(rx, ry, labelW, fieldW, resKeys[i], resists[i][0]);
            }
            masFireBox      = resBoxes[0];
            masWaterBox     = resBoxes[1];
            masLightningBox = resBoxes[2];
            masChaosBox     = resBoxes[3];
            masPhysBox      = resBoxes[4];
            y += 3 * rowH + 4;
        } else {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.compat.mns_disabled"), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 22;
        }

        // Tacz
        section(leftCol, y, colW, "wavedefense.editor2.section.tacz");
        y += 18;
        if (com.wavedefense.compat.TaczCompat.isLoaded()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.compat.open_tacz"),
                b -> this.minecraft.setScreen(new com.wavedefense.gui.TaczBulkAddScreen(location, this))
            ).bounds(leftCol, y, colW, 20).build()); y += 26;
        } else {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.compat.tacz_disabled"), b -> {}
            ).bounds(leftCol, y, colW, 14).build()).active = false;
            y += 22;
        }
        return y;
    }

    /** Builds a "label: [ ___ ]" labelled-EditBox row and returns the EditBox so
     *  the caller can stash it for {@link #flushEditBoxes()} to read on Save. */
    private net.minecraft.client.gui.components.EditBox masEditRow(
            int x, int y, int labelW, int fieldW, String labelKey, int initial) {
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get(labelKey) + ":"), b -> {}
        ).bounds(x, y, labelW, 18).build()).active = false;
        net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(
            this.font, x + labelW + 2, y, fieldW, 18, Component.literal("0"));
        box.setValue(String.valueOf(initial));
        box.setMaxLength(5);
        this.addRenderableWidget(box);
        return box;
    }

    /** Toggle one of the 8 granular InfoPanel flags by index — keeps the Visual
     *  tab's grid loop terse without needing 8 explicit setter calls. */
    private void toggleInfoPanelFlag(com.wavedefense.data.InfoPanelSettings ip, int idx) {
        switch (idx) {
            case 0 -> ip.setShowPlayerCount(!ip.isShowPlayerCount());
            case 1 -> ip.setShowWaveNumber(!ip.isShowWaveNumber());
            case 2 -> ip.setShowWaveTimer(!ip.isShowWaveTimer());
            case 3 -> ip.setShowMobsRemaining(!ip.isShowMobsRemaining());
            case 4 -> ip.setShowSecretCount(!ip.isShowSecretCount());
            case 5 -> ip.setShowShopSecrets(!ip.isShowShopSecrets());
            case 6 -> ip.setShowPoints(!ip.isShowPoints());
            case 7 -> ip.setShowFirstWaveTimer(!ip.isShowFirstWaveTimer());
        }
    }

    /** Renders a "§7label: §evalue" stat line. */
    private void stat(int x, int y, int w, String labelKey, Object value) {
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get(labelKey, value)),
            b -> {}
        ).bounds(x, y, w, 14).build()).active = false;
    }

    /** General tab — implemented inline; first concrete content.
     *  Coordinate fields use {@link com.wavedefense.gui.widgets.CoordinatePickerWidget}
     *  to give admins both manual XYZ entry and the 📌 "use my position" shortcut. */
    private int initGeneralTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;
        int halfW = colW / 2 - 4;
        int rightCol = cx + 4;

        // Section: Identity
        section(leftCol, y, colW, "wavedefense.editor2.section.identity");
        y += 18;

        // Mode toggle (PvE / PvP)
        boolean isPve = location.getMode() == LocationMode.PVE;
        this.addRenderableWidget(Button.builder(
            Component.literal((isPve ? "§a§l✓ " : "§7○ ") + "PvE"),
            b -> {
                if (!isPve) { location.setMode(LocationMode.PVE); rebuildWidgets(); }
            }
        ).bounds(leftCol, y, halfW, 18).build());
        this.addRenderableWidget(Button.builder(
            Component.literal((!isPve ? "§c§l✓ " : "§7○ ") + "PvP"),
            b -> {
                if (isPve) { location.setMode(LocationMode.PVP); rebuildWidgets(); }
            }
        ).bounds(rightCol, y, halfW, 18).build());
        y += 20;
        // ⚠ Mode-switch hint — destructive editing made safe by Cancel deep-copy
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.editor2.mode_switch_warn"),
            b -> {}
        ).bounds(leftCol, y, colW, 12).build()).active = false;
        y += 18;

        // Section: Player spawn (XYZ via CoordinatePickerWidget)
        section(leftCol, y, colW, "wavedefense.editor2.section.spawn");
        y += 18;
        // Spawn-context hint — clarifies which spawn this is when in PvP mode
        if (!isPve) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.editor2.spawn.pvp_hint"), b -> {}
            ).bounds(leftCol, y, colW, 12).build()).active = false;
            y += 14;
        }
        net.minecraft.core.BlockPos sp = location.getPlayerSpawn();
        if (sp == null) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.spawn.unset")), b -> {}
            ).bounds(leftCol, y, colW, 12).build()).active = false;
            y += 14;
        }
        com.wavedefense.gui.widgets.CoordinatePickerWidget spPicker =
            new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                this.font, leftCol, y, sp, 0, false,
                r -> location.setPlayerSpawn(r.pos));
        spPicker.addToScreen(this::addRenderableWidget);
        y += 22;

        // Section: Exit points (used by surrender / victory)
        section(leftCol, y, colW, "wavedefense.editor2.section.exits");
        y += 18;
        net.minecraft.core.BlockPos vep = location.getVictoryExitPos();
        this.addRenderableWidget(Button.builder(Component.literal(vep == null
                ? "§7" + I18n.get("wavedefense.editor2.exit.victory_unset")
                : "§a✓ §7Victory exit"), b -> {})
            .bounds(leftCol, y, colW, 12).build()).active = false;
        y += 14;
        com.wavedefense.gui.widgets.CoordinatePickerWidget vepPicker =
            new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                this.font, leftCol, y, vep, 0, false,
                r -> location.setVictoryExitPos(r.pos));
        vepPicker.addToScreen(this::addRenderableWidget);
        y += 22;

        net.minecraft.core.BlockPos sep = location.getSurrenderExitPos();
        this.addRenderableWidget(Button.builder(Component.literal(sep == null
                ? "§7" + I18n.get("wavedefense.editor2.exit.surrender_unset")
                : "§a✓ §7Surrender exit"), b -> {})
            .bounds(leftCol, y, colW, 12).build()).active = false;
        y += 14;
        com.wavedefense.gui.widgets.CoordinatePickerWidget sepPicker =
            new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                this.font, leftCol, y, sep, 0, false,
                r -> location.setSurrenderExitPos(r.pos));
        sepPicker.addToScreen(this::addRenderableWidget);
        y += 24;

        // Section: Behaviour (game-mode enforcement, inventory)
        section(leftCol, y, colW, "wavedefense.editor2.section.behaviour");
        y += 18;
        boolean egm = location.isEnforceGameMode();
        this.addRenderableWidget(Button.builder(
            Component.literal((egm ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.behaviour.enforce_gm")),
            b -> { location.setEnforceGameMode(!location.isEnforceGameMode()); rebuildWidgets(); }
        ).bounds(leftCol, y, halfW, 18).build());
        boolean ki = location.isKeepInventory();
        this.addRenderableWidget(Button.builder(
            Component.literal((ki ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.behaviour.keep_inv")),
            b -> { location.setKeepInventory(!location.isKeepInventory()); rebuildWidgets(); }
        ).bounds(rightCol, y, halfW, 18).build());
        y += 22;

        // Section: Visibility in player menu
        boolean hid = location.isHiddenFromPlayers();
        this.addRenderableWidget(Button.builder(
            Component.literal((hid ? "§c✓ " : "§7○ ") + I18n.get("wavedefense.editor2.behaviour.hidden_from_menu")),
            b -> { location.setHiddenFromPlayers(!location.isHiddenFromPlayers()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;
        // B1 v0.2.60: victoryScreenEnabled toggle (full-width)
        boolean vs = location.isVictoryScreenEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal((vs ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.behaviour.victory_screen")),
            b -> { location.setVictoryScreenEnabled(!location.isVictoryScreenEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 24;

        // ── Phase B: Behaviour timers (leave / victory linger / re-entry) ──
        section(leftCol, y, colW, "wavedefense.editor2.section.timers");
        y += 18;
        leaveTimerBox      = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.behaviour.leave_timer",       location.getLocationLeaveTimerSec()); y += 22;
        victoryLingerBox   = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.behaviour.victory_linger",    location.getVictoryLingerTimeSec()); y += 22;
        reEntryCooldownBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.behaviour.re_entry_cooldown", location.getReEntryCooldownSec()); y += 24;

        return y;
    }

    /** Area tab — bbox + minimap + outline + boundary + auto-zone + portal. */
    private int initAreaTab(int cx, int y) {
        int leftCol = this.leftX;
        int colW = this.colW;

        // ── BBox section ────────────────────────────────────────────────
        section(leftCol, y, colW, "wavedefense.section.bbox");
        y += 18;
        net.minecraft.core.BlockPos bmin = location.getBboxMin();
        net.minecraft.core.BlockPos bmax = location.getBboxMax();

        // Corner 1
        this.addRenderableWidget(Button.builder(Component.literal(bmin == null
                ? I18n.get("wavedefense.bbox.corner1_unset")
                : "§a✓ §7Corner 1"), b -> {})
            .bounds(leftCol, y, colW, 12).build()).active = false;
        y += 14;
        com.wavedefense.gui.widgets.CoordinatePickerWidget c1Picker =
            new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                this.font, leftCol, y, bmin, 0, false,
                r -> location.setBboxMin(r.pos));
        c1Picker.addToScreen(this::addRenderableWidget);
        y += 20;

        // Corner 2
        this.addRenderableWidget(Button.builder(Component.literal(bmax == null
                ? I18n.get("wavedefense.bbox.corner2_unset")
                : "§a✓ §7Corner 2"), b -> {})
            .bounds(leftCol, y, colW, 12).build()).active = false;
        y += 14;
        com.wavedefense.gui.widgets.CoordinatePickerWidget c2Picker =
            new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                this.font, leftCol, y, bmax, 0, false,
                r -> location.setBboxMax(r.pos));
        c2Picker.addToScreen(this::addRenderableWidget);
        y += 22;

        boolean canShow = location.hasBbox();
        boolean mm = location.isMinimapEnabled();
        this.addRenderableWidget(Button.builder(
            mm ? Component.translatable("wavedefense.bbox.minimap_on")
               : Component.translatable("wavedefense.bbox.minimap_off"),
            b -> { location.setMinimapEnabled(!location.isMinimapEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build()).active = canShow;
        y += 20;
        // v0.2.61 — Phase B: minimap preview (text-based summary). When bbox is
        // set, show dimensions + spawn-point count so admins can sanity-check
        // before enabling. Real graphical preview deferred to v0.2.62.
        if (canShow) {
            net.minecraft.core.BlockPos a = location.getBboxMin();
            net.minecraft.core.BlockPos b = location.getBboxMax();
            int dx = Math.abs(b.getX() - a.getX()) + 1;
            int dz = Math.abs(b.getZ() - a.getZ()) + 1;
            int dy = Math.abs(b.getY() - a.getY()) + 1;
            int spawns = location.getPvpSpawnPoints().size();
            // v0.2.62 — Phase E: graphical preview when there's space, text fallback otherwise.
            // 80x80 widget on the right; status text in remaining left column.
            int previewSize = 80;
            boolean wide = colW >= 360;
            if (wide) {
                int previewX = leftCol + colW - previewSize;
                this.addRenderableWidget(new com.wavedefense.gui.widgets.MinimapPreviewWidget(
                    previewX, y, previewSize, location));
                // Status text to the left of the widget
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + dx + "×" + dz + " §8(h=" + dy + ")  "
                        + (spawns > 0 ? "§a● ×" + spawns : "§8(no spawns)")
                        + (mm ? "\n§a✓ HUD ON" : "\n§7○ HUD OFF")),
                    bn -> {}
                ).bounds(leftCol, y, colW - previewSize - 8, 14).build()).active = false;
                y += previewSize + 4;
            } else {
                this.addRenderableWidget(Button.builder(
                    Component.literal("§8" + I18n.get("wavedefense.editor2.area.minimap_preview")
                        + ": §7" + dx + "×" + dz + " §8(h=" + dy + ") "
                        + (spawns > 0 ? "§a● ×" + spawns : "§8(no spawns)")
                        + (mm ? " §a✓ HUD ON" : " §7○ HUD OFF")),
                    bn -> {}
                ).bounds(leftCol, y, colW, 12).build()).active = false;
                y += 16;
            }
        }

        boolean outlineOn = location.isBboxOutlineEnabled();
        this.addRenderableWidget(Button.builder(
            outlineOn ? Component.translatable("wavedefense.bbox.outline_on")
                      : Component.translatable("wavedefense.bbox.outline_off"),
            b -> { location.setBboxOutlineEnabled(!location.isBboxOutlineEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build()).active = canShow;
        y += 24;

        // ── Boundary section (single radius-based — works for both modes) ────
        section(leftCol, y, colW, "wavedefense.editor2.section.boundary");
        y += 18;
        boolean boundaryOn = location.isLocationBoundaryEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal((boundaryOn ? "§a✓ " : "§7○ ")
                + I18n.get("wavedefense.editor2.boundary.tracking")),
            b -> { location.setLocationBoundaryEnabled(!location.isLocationBoundaryEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;

        if (boundaryOn) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.boundary.radius_label") + ":"),
                b -> {}
            ).bounds(leftCol, y, 80, 18).build()).active = false;
            boundaryRadiusBox = new net.minecraft.client.gui.components.EditBox(
                this.font, leftCol + 84, y, 70, 18,
                Component.literal(String.valueOf(location.getLocationBoundaryRadius())));
            boundaryRadiusBox.setValue(String.valueOf(location.getLocationBoundaryRadius()));
            boundaryRadiusBox.setMaxLength(4);
            this.addRenderableWidget(boundaryRadiusBox);
            this.addRenderableWidget(Button.builder(Component.literal("§8blocks"), b -> {})
                .bounds(leftCol + 158, y, 50, 18).build()).active = false;
            y += 22;
            // QW4: boundary consequence cycle (TIMER_SURRENDER / DAMAGE / TELEPORT_BACK / INSTANT_SURRENDER)
            com.wavedefense.data.Location.BoundaryConsequence bc = location.getBoundaryConsequence();
            String bcKey = "wavedefense.editor2.boundary.consequence." + bc.name().toLowerCase(java.util.Locale.ROOT);
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.boundary.consequence_label")
                    + ": §e" + I18n.get(bcKey) + " §7(click to cycle)"),
                b -> {
                    com.wavedefense.data.Location.BoundaryConsequence[] all =
                        com.wavedefense.data.Location.BoundaryConsequence.values();
                    com.wavedefense.data.Location.BoundaryConsequence cur = location.getBoundaryConsequence();
                    int next = (cur.ordinal() + 1) % all.length;
                    location.setBoundaryConsequence(all[next]);
                    rebuildWidgets();
                }
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
            // B7 v0.2.60: boundaryDamagePerSec — only meaningful when consequence=DAMAGE
            if (bc == com.wavedefense.data.Location.BoundaryConsequence.DAMAGE) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + I18n.get("wavedefense.editor2.boundary.damage_per_sec") + ":"), b -> {}
                ).bounds(leftCol, y, 160, 18).build()).active = false;
                boundaryDamageBox = new net.minecraft.client.gui.components.EditBox(
                    this.font, leftCol + 164, y, 60, 18, Component.literal("1.0"));
                boundaryDamageBox.setValue(String.format("%.1f", location.getBoundaryDamagePerSec()));
                boundaryDamageBox.setMaxLength(5);
                this.addRenderableWidget(boundaryDamageBox);
                y += 22;
            }
            // B6 v0.2.60: boundaryParticlesEnabled — master toggle for particle rendering
            boolean bpe = location.isBoundaryParticlesEnabled();
            this.addRenderableWidget(Button.builder(
                Component.literal((bpe ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.boundary.particles_enabled")),
                b -> { location.setBoundaryParticlesEnabled(!location.isBoundaryParticlesEnabled()); rebuildWidgets(); }
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
        }

        // ── Phase B: Boundary particles (id + count + height) ──────────
        section(leftCol, y, colW, "wavedefense.editor2.area.boundary_particles");
        y += 18;
        // Particle ID (string)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.area.particle_id") + ":"), b -> {}
        ).bounds(leftCol, y, 120, 18).build()).active = false;
        boundaryParticleIdBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 124, y, colW - 124, 18, Component.literal("minecraft:smoke"));
        boundaryParticleIdBox.setValue(location.getBoundaryParticleType());
        boundaryParticleIdBox.setMaxLength(64);
        this.addRenderableWidget(boundaryParticleIdBox);
        y += 22;
        boundaryParticleCountBox  = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.particle_count",  location.getBoundaryParticleCount());  y += 22;
        boundaryParticleHeightBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.particle_height", location.getBoundaryParticleHeight()); y += 22;

        // ── Phase B: Portal timers ─────────────────────────────────────
        sectionWithReset(leftCol, y, colW, "wavedefense.editor2.area.portal", () -> {
            // Reset portal to disabled with sane defaults
            location.setPortalEnabled(false);
            location.setPortalOpenAfterStartSec(60);
            location.setPortalPenaltyTimerSec(30);
            location.setPortalRespawnTimerSec(300);
            location.setPortalPenaltyWave(-1);
            location.setPortalDisappearsOnComplete(true);
        });
        y += 18;
        // B3 v0.2.60: portalEnabled master toggle (gates the per-field UI below)
        boolean portalOn = location.isPortalEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal((portalOn ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.area.portal_enabled")),
            b -> { location.setPortalEnabled(!location.isPortalEnabled()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;
        if (portalOn) {
            portalOpenAfterBox    = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.portal_open_after",    location.getPortalOpenAfterStartSec()); y += 22;
            portalPenaltyTimerBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.portal_penalty_timer", location.getPortalPenaltyTimerSec());   y += 22;
            portalRespawnTimerBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.portal_respawn_timer", location.getPortalRespawnTimerSec());   y += 22;
            // B4 v0.2.60: portalPenaltyWave ± buttons
            int pw = location.getPortalPenaltyWave();
            String pwLabel = pw < 0
                ? I18n.get("wavedefense.editor2.area.portal_penalty_wave.disabled")
                : String.valueOf(pw + 1); // 1-indexed in UI
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.area.portal_penalty_wave") + ": §e" + pwLabel),
                b -> {}
            ).bounds(leftCol, y, colW - 60, 18).build()).active = false;
            this.addRenderableWidget(Button.builder(Component.literal("§e−"),
                b -> {
                    int cur = location.getPortalPenaltyWave();
                    location.setPortalPenaltyWave(cur <= -1 ? location.getWaves().size() - 1 : cur - 1);
                    rebuildWidgets();
                }
            ).bounds(leftCol + colW - 56, y, 26, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("§e+"),
                b -> {
                    int cur = location.getPortalPenaltyWave();
                    location.setPortalPenaltyWave(cur >= location.getWaves().size() - 1 ? -1 : cur + 1);
                    rebuildWidgets();
                }
            ).bounds(leftCol + colW - 28, y, 26, 18).build());
            y += 22;
            // B5 v0.2.60: portalDisappearsOnComplete toggle
            boolean pdoc = location.isPortalDisappearsOnComplete();
            this.addRenderableWidget(Button.builder(
                Component.literal((pdoc ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.area.portal_disappears")),
                b -> { location.setPortalDisappearsOnComplete(!location.isPortalDisappearsOnComplete()); rebuildWidgets(); }
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
        }

        // ── Phase B: Auto-activate zone ────────────────────────────────
        section(leftCol, y, colW, "wavedefense.editor2.area.auto_zone");
        y += 18;
        boolean azOn = location.isAutoActivate();
        this.addRenderableWidget(Button.builder(
            Component.literal((azOn ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.area.auto_zone_enabled")),
            b -> { location.setAutoActivate(!location.isAutoActivate()); rebuildWidgets(); }
        ).bounds(leftCol, y, colW, 18).build());
        y += 22;
        if (azOn) {
            autoZoneRadiusBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.auto_zone_radius", location.getAutoActivateRadius()); y += 22;
            // B9 v0.2.60: zone activation timing
            zoneActivationTimeBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.zone_activation_time", location.getZoneActivationTimeSec()); y += 22;
            zoneOpenAfterStartBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.zone_open_after",      location.getZoneOpenAfterStartSec()); y += 22;
            // Entry-position picker
            this.addRenderableWidget(Button.builder(
                Component.literal("§7" + I18n.get("wavedefense.editor2.area.auto_zone_entry") + ":"), b -> {}
            ).bounds(leftCol, y, colW, 12).build()).active = false;
            y += 14;
            net.minecraft.core.BlockPos ep = location.getAutoActivateEntryPos();
            com.wavedefense.gui.widgets.CoordinatePickerWidget epPicker =
                new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                    this.font, leftCol, y, ep, 0, false,
                    r -> location.setAutoActivateEntryPos(r.pos));
            epPicker.addToScreen(this::addRenderableWidget);
            y += 22;
            // B10 v0.2.60: zone custom center (toggle + optional picker)
            boolean ucc = location.isZoneUsesCustomCenter();
            this.addRenderableWidget(Button.builder(
                Component.literal((ucc ? "§a✓ " : "§7○ ") + I18n.get("wavedefense.editor2.area.zone_custom_center")),
                b -> { location.setZoneUsesCustomCenter(!location.isZoneUsesCustomCenter()); rebuildWidgets(); }
            ).bounds(leftCol, y, colW, 18).build());
            y += 22;
            if (ucc) {
                net.minecraft.core.BlockPos zc = location.getZoneCenter();
                com.wavedefense.gui.widgets.CoordinatePickerWidget zcPicker =
                    new com.wavedefense.gui.widgets.CoordinatePickerWidget(
                        this.font, leftCol, y, zc, 0, false,
                        r -> location.setZoneCenter(r.pos));
                zcPicker.addToScreen(this::addRenderableWidget);
                y += 22;
            }
        }

        // ── B8 v0.2.60: Zone particles (distinct from boundary particles) ──
        section(leftCol, y, colW, "wavedefense.editor2.area.zone_particles");
        y += 18;
        // Particle ID (string EditBox); zoneParticleType=null means default
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.area.particle_id") + ":"), b -> {}
        ).bounds(leftCol, y, 120, 18).build()).active = false;
        zoneParticleIdBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 124, y, colW - 124, 18, Component.literal("minecraft:squid_ink"));
        zoneParticleIdBox.setValue(location.getZoneParticleType());
        zoneParticleIdBox.setMaxLength(64);
        this.addRenderableWidget(zoneParticleIdBox);
        y += 22;
        zoneParticleCountBox    = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.zone_particle_count",    location.getZoneParticleCount());    y += 22;
        // Speed is a float — use a custom row since labelledIntRow is int-only
        this.addRenderableWidget(Button.builder(
            Component.literal("§7" + I18n.get("wavedefense.editor2.area.zone_particle_speed") + ":"), b -> {}
        ).bounds(leftCol, y, 160, 18).build()).active = false;
        zoneParticleSpeedBox = new net.minecraft.client.gui.components.EditBox(
            this.font, leftCol + 164, y, 60, 18, Component.literal("0.0"));
        zoneParticleSpeedBox.setValue(String.format("%.3f", location.getZoneParticleSpeed()));
        zoneParticleSpeedBox.setMaxLength(6);
        this.addRenderableWidget(zoneParticleSpeedBox);
        y += 22;
        zoneParticleIntervalBox = labelledIntRow(leftCol, y, colW, "wavedefense.editor2.area.zone_particle_interval", location.getZoneParticleInterval()); y += 22;

        return y;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private void section(int x, int y, int w, String langKey) {
        Button hdr = Button.builder(
            Component.literal("§b§l▸ " + I18n.get(langKey)),
            b -> {}
        ).bounds(x, y, w, 14).build();
        hdr.active = false;
        this.addRenderableWidget(hdr);
    }

    /** v0.2.64: section header with a small §a↩ reset button on the right.
     *  Click runs the reset action then rebuilds widgets so the new defaults
     *  show in EditBoxes. Used for sections with predictable factory defaults. */
    private void sectionWithReset(int x, int y, int w, String langKey, Runnable resetAction) {
        int btnW = 14;
        Button hdr = Button.builder(
            Component.literal("§b§l▸ " + I18n.get(langKey)),
            b -> {}
        ).bounds(x, y, w - btnW - 2, 14).build();
        hdr.active = false;
        this.addRenderableWidget(hdr);
        // Reset button — tooltip explains what gets reset
        Button reset = Button.builder(
            Component.literal("§a↩"),
            b -> { resetAction.run(); rebuildWidgets(); }
        ).bounds(x + w - btnW, y, btnW, 14).build();
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.editor2.section.reset_tooltip")));
        this.addRenderableWidget(reset);
    }

    private void save() {
        flushEditBoxes();
        // Commit working copy → client cache + server.
        com.wavedefense.gui.ClientLocationManager.updateSingleLocation(location);
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.auto.зміни_збережено_60feafc0"), true);
        }
    }

    /** Override so typed-but-unsaved EditBox values survive sibling toggles
     *  that trigger a rebuild. Mirrors PvpLocationEditorScreen.rebuildWidgets(). */
    @Override
    public void rebuildWidgets() {
        flushEditBoxes();
        super.rebuildWidgets();
    }

    // ─── EditBox state for inline edits (Phase B / v0.2.58, Phase A-D / v0.2.59) ──
    // Visual / Area / General
    private net.minecraft.client.gui.components.EditBox boundaryRadiusBox;
    private net.minecraft.client.gui.components.EditBox spawnPanelOffsetYBox;
    private net.minecraft.client.gui.components.EditBox pvpMinPlayersBox;
    private net.minecraft.client.gui.components.EditBox masLevelBox, masXpBox,
        masFireBox, masWaterBox, masLightningBox, masChaosBox, masPhysBox;
    // v0.2.59 — Area heavy fields
    private net.minecraft.client.gui.components.EditBox autoZoneRadiusBox;
    private net.minecraft.client.gui.components.EditBox boundaryParticleIdBox,
        boundaryParticleCountBox, boundaryParticleHeightBox;
    private net.minecraft.client.gui.components.EditBox portalOpenAfterBox,
        portalPenaltyTimerBox, portalRespawnTimerBox;
    // v0.2.59 — General behaviour
    private net.minecraft.client.gui.components.EditBox leaveTimerBox,
        victoryLingerBox, reEntryCooldownBox;
    // v0.2.59 — PvP per-sub-mode (Standard)
    private net.minecraft.client.gui.components.EditBox stdTotalRoundsBox,
        stdBuyTimeBox, stdRoundDelayBox, stdRoundStartPointsBox,
        stdWinPointsBox, stdLosePointsBox, stdRoundTimeLimitBox;
    // PvP per-sub-mode (Deathmatch)
    private net.minecraft.client.gui.components.EditBox dmKillsToWinBox,
        dmMatchTimeLimitBox;
    // PvP per-sub-mode (Battle Royale)
    private net.minecraft.client.gui.components.EditBox brBorderRadiusBox,
        brShrinkIntervalBox, brShrinkAmountBox, brInitialWaitBox,
        brFinalRadiusBox, brParticleCountBox, brParticleIdBox, brDamageAmtBox;
    // PvP per-sub-mode (CtP / KotH — share fields)
    private net.minecraft.client.gui.components.EditBox objScoreToWinBox,
        objScorePerSecBox, objRoundDurationBox, kothHoldDurationBox;
    // v0.2.59 QW3 — Economy PvP point fields
    private net.minecraft.client.gui.components.EditBox ecoKillPtsBox,
        ecoDeathPenaltyBox, ecoWinPtsBox, ecoLosePtsBox, ecoRoundStartPtsBox;
    // v0.2.61 — Ready-check timeout
    private net.minecraft.client.gui.components.EditBox pvpReadyTimeoutBox;
    // v0.2.60 — Pre-3.0 blockers (12 fields)
    private net.minecraft.client.gui.components.EditBox boundaryDamageBox;
    private net.minecraft.client.gui.components.EditBox zoneParticleIdBox,
        zoneParticleCountBox, zoneParticleSpeedBox, zoneParticleIntervalBox;
    private net.minecraft.client.gui.components.EditBox zoneActivationTimeBox,
        zoneOpenAfterStartBox;
    private net.minecraft.client.gui.components.EditBox infoPanelTextScaleBox;
    // v0.2.59 — Spawn-point inline editor
    private boolean spawnEditing = false;
    private int spawnEditingIndex = -1;
    private net.minecraft.client.gui.components.EditBox spawnNameBox;
    private com.wavedefense.gui.widgets.CoordinatePickerWidget spawnCoordPicker;
    private int spawnListScrollOffset = 0;

    /** Reads all EditBox values into the working Location. Called on Save and on
     *  every rebuildWidgets() so typed values survive sibling toggle clicks.
     *  Each field has its own try/catch — one bad number doesn't drop the rest. */
    private void flushEditBoxes() {
        // ── v0.2.58 fields ─────────────────────────────────────────────
        flushInt(boundaryRadiusBox, location::setLocationBoundaryRadius);
        if (spawnPanelOffsetYBox != null) try {
            location.getInfoPanel().setSpawnPanelOffsetY(Float.parseFloat(spawnPanelOffsetYBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        flushInt(pvpMinPlayersBox, location::setPvpMinPlayers);
        flushInt(masLevelBox,      location::setMasLevel);
        flushInt(masXpBox,         location::setMasXpBonus);
        flushInt(masFireBox,       location::setMasFireResist);
        flushInt(masWaterBox,      location::setMasWaterResist);
        flushInt(masLightningBox,  location::setMasLightningResist);
        flushInt(masChaosBox,      location::setMasChaosResist);
        flushInt(masPhysBox,       location::setMasPhysicalResist);

        // ── v0.2.59 — Area heavy fields ───────────────────────────────
        flushInt(autoZoneRadiusBox,        location::setAutoActivateRadius);
        if (boundaryParticleIdBox != null && !boundaryParticleIdBox.getValue().isBlank())
            location.setBoundaryParticleType(boundaryParticleIdBox.getValue().trim());
        flushInt(boundaryParticleCountBox,  location::setBoundaryParticleCount);
        flushInt(boundaryParticleHeightBox, location::setBoundaryParticleHeight);
        flushInt(portalOpenAfterBox,        location::setPortalOpenAfterStartSec);
        flushInt(portalPenaltyTimerBox,     location::setPortalPenaltyTimerSec);
        flushInt(portalRespawnTimerBox,     location::setPortalRespawnTimerSec);
        flushInt(leaveTimerBox,             location::setLocationLeaveTimerSec);
        flushInt(victoryLingerBox,          location::setVictoryLingerTimeSec);
        flushInt(reEntryCooldownBox,        location::setReEntryCooldownSec);

        // ── v0.2.59 — PvP Standard ────────────────────────────────────
        flushInt(stdTotalRoundsBox,      location::setPvpTotalRounds);
        flushInt(stdBuyTimeBox,          location::setPvpBuyTime);
        flushInt(stdRoundDelayBox,       location::setPvpRoundStartDelay);
        flushInt(stdRoundStartPointsBox, location::setPvpRoundStartPoints);
        flushInt(stdWinPointsBox,        location::setPvpWinPoints);
        flushInt(stdLosePointsBox,       location::setPvpLosePoints);
        flushInt(stdRoundTimeLimitBox,   location::setPvpRoundTimeLimitSec);

        // ── v0.2.59 — PvP Deathmatch ──────────────────────────────────
        flushInt(dmKillsToWinBox,     location::setDmKillsToWin);
        flushInt(dmMatchTimeLimitBox, location::setPvpRoundTimeLimitSec);

        // ── v0.2.59 — PvP Battle Royale ───────────────────────────────
        flushInt(brBorderRadiusBox,   location::setBrBorderRadius);
        flushInt(brShrinkIntervalBox, location::setBrShrinkIntervalSec);
        flushInt(brShrinkAmountBox,   location::setBrShrinkAmountBlocks);
        flushInt(brInitialWaitBox,    location::setBrInitialWaitSec);
        flushInt(brFinalRadiusBox,    location::setBrFinalRadius);
        flushInt(brParticleCountBox,  location::setBrBorderParticleCount);
        if (brParticleIdBox != null && !brParticleIdBox.getValue().isBlank())
            location.setBrBorderParticle(brParticleIdBox.getValue().trim());
        if (brDamageAmtBox != null) try {
            location.setBrBorderDamageAmt(Math.max(0f, Float.parseFloat(brDamageAmtBox.getValue().trim())));
        } catch (NumberFormatException ignored) {}

        // ── v0.2.59 — PvP Objective (CtP / KotH) ──────────────────────
        // Score-to-win / per-sec / round-duration route to the active mode's setter.
        boolean isCtp = location.getPvpMode() == com.wavedefense.data.Location.PvpMode.CAPTURE_THE_POINT;
        if (objScoreToWinBox  != null) try { int v = Math.max(1, Integer.parseInt(objScoreToWinBox.getValue().trim()));
            if (isCtp) location.setCtpScoreToWin(v); else location.setKothScoreToWin(v); } catch (NumberFormatException ignored) {}
        if (objScorePerSecBox != null) try { int v = Math.max(1, Integer.parseInt(objScorePerSecBox.getValue().trim()));
            if (isCtp) location.setCtpScorePerSec(v); else location.setKothScorePerSec(v); } catch (NumberFormatException ignored) {}
        if (objRoundDurationBox != null) try { int v = Math.max(30, Integer.parseInt(objRoundDurationBox.getValue().trim()));
            if (isCtp) location.setCtpRoundDurationSec(v); else location.setKothRoundDurationSec(v); } catch (NumberFormatException ignored) {}
        flushInt(kothHoldDurationBox, location::setKothHoldDurationSec);

        // ── v0.2.59 QW3 — Economy PvP points ──────────────────────────
        flushInt(ecoKillPtsBox,       location::setPvpKillPoints);
        flushInt(ecoDeathPenaltyBox,  location::setPvpDeathPenalty);
        flushInt(ecoWinPtsBox,        location::setPvpWinPoints);
        flushInt(ecoLosePtsBox,       location::setPvpLosePoints);
        flushInt(ecoRoundStartPtsBox, location::setPvpRoundStartPoints);
        // v0.2.61 — Ready-check timeout
        flushInt(pvpReadyTimeoutBox,  location::setPvpReadyCheckTimeoutSec);

        // ── v0.2.60 — Pre-3.0 blockers ─────────────────────────────────
        if (boundaryDamageBox != null) try {
            location.setBoundaryDamagePerSec(Math.max(0f, Float.parseFloat(boundaryDamageBox.getValue().trim())));
        } catch (NumberFormatException ignored) {}
        if (zoneParticleIdBox != null && !zoneParticleIdBox.getValue().isBlank())
            location.setZoneParticleType(zoneParticleIdBox.getValue().trim());
        flushInt(zoneParticleCountBox,    location::setZoneParticleCount);
        if (zoneParticleSpeedBox != null) try {
            location.setZoneParticleSpeed(Float.parseFloat(zoneParticleSpeedBox.getValue().replace(',', '.').trim()));
        } catch (NumberFormatException ignored) {}
        flushInt(zoneParticleIntervalBox, location::setZoneParticleInterval);
        flushInt(zoneActivationTimeBox,   location::setZoneActivationTimeSec);
        flushInt(zoneOpenAfterStartBox,   location::setZoneOpenAfterStartSec);
        if (infoPanelTextScaleBox != null) try {
            location.getInfoPanel().setTextScale(Float.parseFloat(infoPanelTextScaleBox.getValue().replace(',', '.').trim()));
        } catch (NumberFormatException ignored) {}
    }

    /** Compact helper for the common int-EditBox → setter flush pattern. */
    private void flushInt(net.minecraft.client.gui.components.EditBox box,
                          java.util.function.IntConsumer setter) {
        if (box == null) return;
        try { setter.accept(Integer.parseInt(box.getValue().trim())); }
        catch (NumberFormatException ignored) {}
    }

    /** Null out EditBox refs at the start of every init() — they're recreated
     *  per-tab and stale refs would feed wrong tab's values into flushEditBoxes. */
    private void resetEditBoxRefs() {
        boundaryRadiusBox = null;
        spawnPanelOffsetYBox = null;
        pvpMinPlayersBox = null;
        masLevelBox = masXpBox = masFireBox = masWaterBox = masLightningBox = masChaosBox = masPhysBox = null;
        autoZoneRadiusBox = null;
        boundaryParticleIdBox = boundaryParticleCountBox = boundaryParticleHeightBox = null;
        portalOpenAfterBox = portalPenaltyTimerBox = portalRespawnTimerBox = null;
        leaveTimerBox = victoryLingerBox = reEntryCooldownBox = null;
        stdTotalRoundsBox = stdBuyTimeBox = stdRoundDelayBox = null;
        stdRoundStartPointsBox = stdWinPointsBox = stdLosePointsBox = stdRoundTimeLimitBox = null;
        dmKillsToWinBox = dmMatchTimeLimitBox = null;
        brBorderRadiusBox = brShrinkIntervalBox = brShrinkAmountBox = null;
        brInitialWaitBox = brFinalRadiusBox = brParticleCountBox = null;
        brParticleIdBox = null; brDamageAmtBox = null;
        objScoreToWinBox = objScorePerSecBox = objRoundDurationBox = kothHoldDurationBox = null;
        ecoKillPtsBox = ecoDeathPenaltyBox = ecoWinPtsBox = ecoLosePtsBox = ecoRoundStartPtsBox = null;
        boundaryDamageBox = null;
        zoneParticleIdBox = zoneParticleCountBox = zoneParticleSpeedBox = zoneParticleIntervalBox = null;
        zoneActivationTimeBox = zoneOpenAfterStartBox = null;
        infoPanelTextScaleBox = null;
        pvpReadyTimeoutBox = null;
        spawnNameBox = null;
        spawnCoordPicker = null;
    }

    // ─── Render: header + tabs + scrollable content + footer ────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        // v0.2.63: missing-config warning bar — drawn under the header, above
        // the tab bar. Single line, scrolls horizontally if too long.
        renderConfigWarnings(g);

        int clipTop = TAB_BAR_Y + TAB_BAR_H + 2;
        int clipBot = this.height - FOOTER_H;

        // Content frame
        GuiTheme.renderContentFrame(g, 8, clipTop - 2, this.width - 8, clipBot + 2);

        // ── Pass 1: scrolled content (in scissor) ───────────────────────
        ScissorHelper.enable(0, clipTop, this.width, Math.max(1, clipBot - clipTop));
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w && !staticWidgets.contains(w)
                    && w.getY() + w.getHeight() > clipTop && w.getY() < clipBot) {
                w.render(g, mouseX, mouseY, partial);
            }
        }
        g.flush();
        ScissorHelper.disable();

        // ── Pass 2: static (tab bar + footer) ───────────────────────────
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w && staticWidgets.contains(w)) {
                w.render(g, mouseX, mouseY, partial);
            }
        }

        // Scrollbar
        GuiTheme.scrollBar(g, this.width - 8, clipTop, clipBot,
            scrollOffset, contentHeight, clipBot - clipTop);
    }

    /** v0.2.63: collects critical missing-config issues (no bbox, no spawns,
     *  etc.) and renders them as a yellow ⚠ line below the title. Empty if the
     *  location is sufficiently configured. */
    private void renderConfigWarnings(GuiGraphics g) {
        java.util.List<String> warns = new java.util.ArrayList<>();
        if (!location.hasBbox()) {
            warns.add(I18n.get("wavedefense.editor2.warn.no_bbox"));
        }
        if (location.getMode() == LocationMode.PVP) {
            int spawns = location.getPvpSpawnPoints().size();
            if (spawns < 2) {
                warns.add(I18n.get("wavedefense.editor2.warn.pvp_few_spawns", spawns));
            }
            if (location.isObjectiveMode() && location.getCapturePoints().isEmpty()) {
                warns.add(I18n.get("wavedefense.editor2.warn.no_capture_points"));
            }
        } else {
            if (location.getWaves().isEmpty()) {
                warns.add(I18n.get("wavedefense.editor2.warn.pve_no_waves"));
            }
            if (location.getPlayerSpawn() == null) {
                warns.add(I18n.get("wavedefense.editor2.warn.pve_no_spawn"));
            }
        }
        if (warns.isEmpty()) return;

        String msg = "§e⚠ " + String.join("  §8|  §e", warns);
        int textW = this.font.width(msg);
        int x = (this.width - textW) / 2;
        int y = TAB_BAR_Y - 12; // sits in the gap between header and tab bar
        // Subtle dark backdrop so it's legible over the GuiTheme header
        g.fill(x - 4, y - 1, x + textW + 4, y + this.font.lineHeight + 1, 0x80000000);
        g.drawString(this.font, msg, x, y, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int clipTop = TAB_BAR_Y + TAB_BAR_H + 2;
        int clipBot = this.height - FOOTER_H;
        // Static widgets always clickable
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && staticWidgets.contains(w)) {
                if (child.mouseClicked(mx, my, button)) {
                    this.setFocused(child);
                    if (button == 0) this.setDragging(true);
                    return true;
                }
            }
        }
        // Scrolled widgets — only inside scissor zone
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && !staticWidgets.contains(w)) {
                if (w.getY() + w.getHeight() <= clipTop || w.getY() >= clipBot) continue;
            }
            if (child.mouseClicked(mx, my, button)) {
                this.setFocused(child);
                if (button == 0) this.setDragging(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int clipTop = TAB_BAR_Y + TAB_BAR_H + 2;
        int clipBot = this.height - FOOTER_H;
        int maxScroll = Math.max(0, contentHeight - (clipBot - clipTop));
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset = Math.max(0, scrollOffset - 12);
            rebuildWidgets();
            return true;
        }
        if (delta < 0 && scrollOffset < maxScroll) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 12);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
