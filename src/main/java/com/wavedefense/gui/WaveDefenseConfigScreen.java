package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.config.WaveDefenseConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/**
 * In-game configuration screen for Wave Defense.
 * Registered via ConfigScreenHandler.ConfigScreenFactory in WaveDefenceMod.clientSetup().
 * Opens from the Forge Mods menu → Wave Defense → Config button.
 *
 * Five tabs: General | PvP | Mobs & Shop | Limits | Debug
 * All changes are applied immediately to ForgeConfigSpec and saved to disk on "Save & Close".
 */
public class WaveDefenseConfigScreen extends Screen {

    private final Screen parent;
    private int currentTab = 0;

    /** Translation keys for each tab — resolved at render time. */
    private static final String[] TAB_KEYS = {
        "wavedefense.config.tab.general",
        "wavedefense.config.tab.pvp",
        "wavedefense.config.tab.mobs_shop",
        "wavedefense.config.tab.limits",
        "wavedefense.config.tab.debug"
    };

    // ── Pending values (edited in GUI, committed on Save) ──────────────────

    // General
    private boolean pendingHud;
    private int     pendingWaveTime;
    private boolean pendingTooltips;
    private int     pendingLobbyTimer;
    private String  pendingGameMode;
    private int     pendingMaxPlayers;
    private boolean pendingWaveTitle;

    // PvP
    private boolean pendingHideNametags;
    private int     pendingDefaultRounds;
    private int     pendingBuyTime;
    private int     pendingRespawnDelay;

    // Mobs & Shop
    private boolean pendingMobEquip;
    private double  pendingArmorDrop;
    private boolean pendingShopCategories;
    private boolean pendingShopHotkey;

    // Limits
    private int pendingMaxMobTypes;
    private int pendingMaxWaves;
    private int pendingMaxMobSpawns;
    private int pendingMaxPlayerSpawns;
    private int pendingMaxShopItems;
    private int pendingMaxLootSpawns;

    // Debug
    private boolean pendingDebugAdmin;
    private boolean pendingDebugLogging;

    // ── Constructor ────────────────────────────────────────────────────────

    public WaveDefenseConfigScreen(Screen parent) {
        super(new StringTextComponent("§6§l").append(new TranslationTextComponent("wavedefense.config.title")));
        this.parent = parent;
        loadFromConfig();
    }

    private void loadFromConfig() {
        pendingHud            = WaveDefenseConfig.ENABLE_HUD.get();
        pendingWaveTime       = WaveDefenseConfig.DEFAULT_WAVE_TIME.get();
        pendingTooltips       = WaveDefenseConfig.ENABLE_UI_TOOLTIPS.get();
        pendingLobbyTimer     = WaveDefenseConfig.LOBBY_TIMER_SECONDS.get();
        pendingGameMode       = WaveDefenseConfig.LOCATION_GAME_MODE.get();

        pendingHideNametags   = WaveDefenseConfig.PVP_HIDE_ENEMY_NAMETAGS.get();
        pendingDefaultRounds  = WaveDefenseConfig.PVP_DEFAULT_ROUNDS.get();
        pendingBuyTime        = WaveDefenseConfig.PVP_DEFAULT_BUY_TIME.get();
        pendingRespawnDelay   = WaveDefenseConfig.PVP_RESPAWN_DELAY_SECONDS.get();

        pendingMobEquip       = WaveDefenseConfig.MOBS_CAN_HAVE_EQUIPMENT.get();
        pendingArmorDrop      = WaveDefenseConfig.MOB_ARMOR_DROP_CHANCE.get();
        pendingShopCategories = WaveDefenseConfig.SHOP_CATEGORIES_ENABLED.get();
        pendingShopHotkey     = WaveDefenseConfig.SHOP_HOTKEY_ENABLED.get();

        pendingMaxPlayers     = WaveDefenseConfig.MAX_PLAYERS_PER_LOCATION.get();
        pendingWaveTitle      = WaveDefenseConfig.WAVE_START_TITLE_ENABLED.get();

        pendingMaxMobTypes    = WaveDefenseConfig.MAX_MOB_TYPES.get();
        pendingMaxWaves       = WaveDefenseConfig.MAX_WAVES.get();
        pendingMaxMobSpawns   = WaveDefenseConfig.MAX_MOB_SPAWNS.get();
        pendingMaxPlayerSpawns= WaveDefenseConfig.MAX_PLAYER_SPAWNS.get();
        pendingMaxShopItems   = WaveDefenseConfig.MAX_SHOP_ITEMS.get();
        pendingMaxLootSpawns  = WaveDefenseConfig.MAX_LOOT_SPAWNS.get();

        pendingDebugAdmin     = WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.get();
        pendingDebugLogging   = WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get();
    }

    // ── init ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int cx   = this.width / 2;
        int tabW = 82;
        int gap  = 2;
        int totalW = TAB_KEYS.length * tabW + (TAB_KEYS.length - 1) * gap;
        int tabX = cx - totalW / 2;

        // Tab bar
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int ti = i;
            boolean active = (currentTab == ti);
            this.addButton(new Button(tabX + i * (tabW + gap), 24, tabW, 16, new StringTextComponent(active ? "§a§l" : "§7")
                    .append(new TranslationTextComponent(TAB_KEYS[ti])), b -> { currentTab = ti; init(); }));
        }

        // Tab content
        switch (currentTab) { case 0: initGeneralTab(cx); break; case 1: initPvpTab(cx); break; case 2: initMobsShopTab(cx); break; case 3: initLimitsTab(cx); break; case 4: initDebugTab(cx); break; }

        // Footer
        this.addButton(new Button(cx - 116, this.height - 26, 110, 20, new TranslationTextComponent("wavedefense.config.save_close"), b -> { saveToConfig(); this.minecraft.setScreen(parent); }));

        this.addButton(new Button(cx + 6, this.height - 26, 110, 20, new TranslationTextComponent("wavedefense.config.cancel"), b -> this.minecraft.setScreen(parent)));
    }

    // ── Tab: General ──────────────────────────────────────────────────────

    private void initGeneralTab(int cx) {
        int y = 48;
        int lw = 180, rw = 130, lx = cx - 160;

        label(lx, y, lw, "wavedefense.config.general.hud");
        addToggle(cx + 24, y, rw, pendingHud,
            v -> pendingHud = v,
            "wavedefense.config.general.hud.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.wave_time");
        addIntBox(cx + 24, y, rw, pendingWaveTime, 3, 600,
            v -> pendingWaveTime = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.tooltips");
        addToggle(cx + 24, y, rw, pendingTooltips,
            v -> pendingTooltips = v,
            "wavedefense.config.general.tooltips.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.lobby");
        addIntBox(cx + 24, y, rw, pendingLobbyTimer, 5, 300,
            v -> pendingLobbyTimer = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.game_mode");
        boolean isAdventure = "adventure".equalsIgnoreCase(pendingGameMode);
        Button gmBtn = new Button(cx + 24, y, rw, 18, new StringTextComponent(isAdventure ? "§e" : "§b")
                .append(new TranslationTextComponent(isAdventure
                    ? "wavedefense.config.general.game_mode.adventure"
                    : "wavedefense.config.general.game_mode.survival")), b -> {
                boolean nowAdventure = "adventure".equalsIgnoreCase(pendingGameMode);
                pendingGameMode = nowAdventure ? "survival" : "adventure";
                boolean nextAdventure = !nowAdventure;
                b.setMessage(new StringTextComponent(nextAdventure ? "§e" : "§b")
                    .append(new TranslationTextComponent(nextAdventure
                        ? "wavedefense.config.general.game_mode.adventure"
                        : "wavedefense.config.general.game_mode.survival")));
            });
        /* setTooltip omitted on 1.16.5: gmBtn */
        this.addButton(gmBtn);
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.max_players");
        addIntBox(cx + 24, y, rw, pendingMaxPlayers, 0, 200,
            v -> pendingMaxPlayers = v,
            "wavedefense.config.general.max_players.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.general.wave_title");
        addToggle(cx + 24, y, rw, pendingWaveTitle,
            v -> pendingWaveTitle = v,
            "wavedefense.config.general.wave_title.tooltip");
    }

    // ── Tab: PvP ─────────────────────────────────────────────────────────

    private void initPvpTab(int cx) {
        int y = 48;
        int lw = 180, rw = 130, lx = cx - 160;

        label(lx, y, lw, "wavedefense.config.pvp.hide_nametags");
        addToggle(cx + 24, y, rw, pendingHideNametags,
            v -> pendingHideNametags = v,
            "wavedefense.config.pvp.hide_nametags.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.pvp.rounds");
        addIntBox(cx + 24, y, rw, pendingDefaultRounds, 1, 100,
            v -> pendingDefaultRounds = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.pvp.buy_time");
        addIntBox(cx + 24, y, rw, pendingBuyTime, 5, 120,
            v -> pendingBuyTime = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.pvp.respawn_delay");
        addIntBox(cx + 24, y, rw, pendingRespawnDelay, 0, 30,
            v -> pendingRespawnDelay = v,
            "wavedefense.config.pvp.respawn_delay.tooltip");
        y += 28;

        infoLine(cx, y, "wavedefense.config.pvp.note");
    }

    // ── Tab: Mobs & Shop ──────────────────────────────────────────────────

    private void initMobsShopTab(int cx) {
        int y = 48;
        int lw = 180, rw = 130, lx = cx - 160;

        label(lx, y, lw, "wavedefense.config.mobs.equipment");
        addToggle(cx + 24, y, rw, pendingMobEquip,
            v -> pendingMobEquip = v,
            "wavedefense.config.mobs.equipment.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.mobs.armor_drop");
        TextFieldWidget dropBox = new TextFieldWidget(this.font, cx + 24, y, rw, 18,
            new StringTextComponent("0.085"));
        dropBox.setValue(String.format("%.3f", pendingArmorDrop));
        dropBox.setMaxLength(7);
        dropBox.setResponder(s -> {
            try { pendingArmorDrop = Math.max(0.0, Math.min(1.0,
                Double.parseDouble(s.trim()))); }
            catch (NumberFormatException ignored) {}
        });
        /* setTooltip omitted on 1.16.5: dropBox */
        this.addButton(dropBox);
        y += 24;

        label(lx, y, lw, "wavedefense.config.shop.categories");
        addToggle(cx + 24, y, rw, pendingShopCategories,
            v -> pendingShopCategories = v,
            "wavedefense.config.shop.categories.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.shop.hotkey");
        addToggle(cx + 24, y, rw, pendingShopHotkey,
            v -> pendingShopHotkey = v,
            "wavedefense.config.shop.hotkey.tooltip");
    }

    // ── Tab: Limits ───────────────────────────────────────────────────────

    private void initLimitsTab(int cx) {
        int y = 48;
        int lw = 190, rw = 120, lx = cx - 160;

        label(lx, y, lw, "wavedefense.config.limits.mob_types");
        addIntBox(cx + 34, y, rw, pendingMaxMobTypes, 1, 9999,
            v -> pendingMaxMobTypes = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.limits.waves");
        addIntBox(cx + 34, y, rw, pendingMaxWaves, 1, 9999,
            v -> pendingMaxWaves = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.limits.mob_spawns");
        addIntBox(cx + 34, y, rw, pendingMaxMobSpawns, 1, 9999,
            v -> pendingMaxMobSpawns = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.limits.player_spawns");
        addIntBox(cx + 34, y, rw, pendingMaxPlayerSpawns, 1, 9999,
            v -> pendingMaxPlayerSpawns = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.limits.shop_items");
        addIntBox(cx + 34, y, rw, pendingMaxShopItems, 1, 9999,
            v -> pendingMaxShopItems = v);
        y += 24;

        label(lx, y, lw, "wavedefense.config.limits.loot_spawns");
        addIntBox(cx + 34, y, rw, pendingMaxLootSpawns, 1, 9999,
            v -> pendingMaxLootSpawns = v);
        y += 28;

        infoLine(cx, y, "wavedefense.config.limits.note");
    }

    // ── Tab: Debug ────────────────────────────────────────────────────────

    private void initDebugTab(int cx) {
        int y = 48;
        int lw = 180, rw = 130, lx = cx - 160;

        label(lx, y, lw, "wavedefense.config.debug.admin_msgs");
        addToggle(cx + 24, y, rw, pendingDebugAdmin,
            v -> pendingDebugAdmin = v,
            "wavedefense.config.debug.admin_msgs.tooltip");
        y += 24;

        label(lx, y, lw, "wavedefense.config.debug.logging");
        addToggle(cx + 24, y, rw, pendingDebugLogging,
            v -> pendingDebugLogging = v,
            "wavedefense.config.debug.logging.tooltip");
        y += 28;

        infoLine(cx, y, "wavedefense.config.debug.note");
    }

    // ── Save ──────────────────────────────────────────────────────────────

    private void saveToConfig() {
        WaveDefenseConfig.ENABLE_HUD.set(pendingHud);
        WaveDefenseConfig.DEFAULT_WAVE_TIME.set(pendingWaveTime);
        WaveDefenseConfig.ENABLE_UI_TOOLTIPS.set(pendingTooltips);
        WaveDefenseConfig.LOBBY_TIMER_SECONDS.set(pendingLobbyTimer);
        WaveDefenseConfig.LOCATION_GAME_MODE.set(pendingGameMode);

        WaveDefenseConfig.PVP_HIDE_ENEMY_NAMETAGS.set(pendingHideNametags);
        WaveDefenseConfig.PVP_DEFAULT_ROUNDS.set(pendingDefaultRounds);
        WaveDefenseConfig.PVP_DEFAULT_BUY_TIME.set(pendingBuyTime);
        WaveDefenseConfig.PVP_RESPAWN_DELAY_SECONDS.set(pendingRespawnDelay);

        WaveDefenseConfig.MOBS_CAN_HAVE_EQUIPMENT.set(pendingMobEquip);
        WaveDefenseConfig.MOB_ARMOR_DROP_CHANCE.set(pendingArmorDrop);
        WaveDefenseConfig.SHOP_CATEGORIES_ENABLED.set(pendingShopCategories);
        WaveDefenseConfig.SHOP_HOTKEY_ENABLED.set(pendingShopHotkey);

        WaveDefenseConfig.MAX_MOB_TYPES.set(pendingMaxMobTypes);
        WaveDefenseConfig.MAX_WAVES.set(pendingMaxWaves);
        WaveDefenseConfig.MAX_MOB_SPAWNS.set(pendingMaxMobSpawns);
        WaveDefenseConfig.MAX_PLAYER_SPAWNS.set(pendingMaxPlayerSpawns);
        WaveDefenseConfig.MAX_SHOP_ITEMS.set(pendingMaxShopItems);
        WaveDefenseConfig.MAX_LOOT_SPAWNS.set(pendingMaxLootSpawns);

        WaveDefenseConfig.MAX_PLAYERS_PER_LOCATION.set(pendingMaxPlayers);
        WaveDefenseConfig.WAVE_START_TITLE_ENABLED.set(pendingWaveTitle);

        WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.set(pendingDebugAdmin);
        WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(pendingDebugLogging);

        WaveDefenseConfig.SPEC.save();
    }

    // ── Widget helpers ────────────────────────────────────────────────────

    /** Static left-column label backed by a translation key. */
    private void label(int x, int y, int w, String translationKey) {
        this.addButton(new Button(x, y, w, 18, new StringTextComponent("§7").append(new TranslationTextComponent(translationKey)), b -> {})).active = false;
    }

    /** Info / hint line centered across the full width (§ color codes live in lang value). */
    private void infoLine(int cx, int y, String translationKey) {
        this.addButton(new Button(cx - 160, y, 320, 14, new TranslationTextComponent(translationKey), b -> {})).active = false;
    }

    /**
     * Toggle button: shows "§aEnabled" / "§cDisabled" (localized).
     * @param setter      called immediately when the value changes
     * @param tooltipKey  translation key for hover description
     */
    private void addToggle(int x, int y, int w, boolean initial,
                           java.util.function.Consumer<Boolean> setter, String tooltipKey) {
        final boolean[] val = { initial };
        Button btn = new Button(x, y, w, 18, new StringTextComponent(val[0] ? "§a" : "§c")
                .append(new TranslationTextComponent(val[0]
                    ? "wavedefense.config.toggle.enabled"
                    : "wavedefense.config.toggle.disabled")), b -> {
                val[0] = !val[0];
                setter.accept(val[0]);
                b.setMessage(new StringTextComponent(val[0] ? "§a" : "§c")
                    .append(new TranslationTextComponent(val[0]
                        ? "wavedefense.config.toggle.enabled"
                        : "wavedefense.config.toggle.disabled")));
            });
        /* setTooltip omitted on 1.16.5: btn */
        this.addButton(btn);
    }

    /** Integer TextFieldWidget with tooltip. */
    private void addIntBox(int x, int y, int w, int initialValue, int min, int max,
                           java.util.function.IntConsumer setter, String tooltipKey) {
        TextFieldWidget box = addIntBox(x, y, w, initialValue, min, max, setter);
        /* setTooltip omitted on 1.16.5: box */
    }

    /**
     * Integer TextFieldWidget with responder, clamped to [min, max].
     * @param setter called on every keystroke with the parsed (clamped) value
     */
    private TextFieldWidget addIntBox(int x, int y, int w, int initialValue, int min, int max,
                           java.util.function.IntConsumer setter) {
        TextFieldWidget box = new TextFieldWidget(this.font, x, y, w, 18, new StringTextComponent(""));
        box.setValue(String.valueOf(initialValue));
        box.setMaxLength(5);
        box.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                setter.accept(Math.max(min, Math.min(max, v)));
            } catch (NumberFormatException ignored) {}
        });
        /* setTooltip omitted on 1.16.5: box */
        this.addButton(box);
        return box;
    }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;

        // Title
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, cx, 8, GuiTheme.TEXT);

        // Separator below tabs
        com.wavedefense.gui.GuiCompat.fill(g, cx - 165, 42, cx + 165, 43, GuiTheme.BORDER);

        // Separator above footer
        com.wavedefense.gui.GuiCompat.fill(g, cx - 165, this.height - 30, cx + 165, this.height - 29, GuiTheme.BORDER);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
