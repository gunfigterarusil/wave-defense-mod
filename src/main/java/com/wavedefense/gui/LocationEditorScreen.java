package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.InfoPanelSettings;
import com.wavedefense.data.LocationMode;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.gui.AdminMenuScreen;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.wavedefense.data.WaveTrigger;
import com.wavedefense.gui.TooltipHelper;

public class LocationEditorScreen extends Screen {

    private final Location location;
    private final Screen parent;
    private int currentTab = 0;
    private int mobSpawnScrollOffset = 0;
    private static final int MOB_SPAWN_PER_PAGE = 5;

    // Поля вводу координат точки спавну гравця
    private CoordinateInputField spawnCoordField;
    // Поле стартових поінтів
    private EditBox startingPointsInput;
    // Section divider Y positions for Special tab sectionDivider rendering
    private int ySectionBoundary = -1, ySectionZone = -1, ySectionPortal = -1;

    // Boundary inputs
    private EditBox boundaryRadiusInput;
    private EditBox leaveTimerInput;
    private EditBox boundaryDamageInput;  // С2: class field so value survives consequence-type changes
    // Portal inputs
    private EditBox portalPenaltyTimerInput;
    private EditBox portalRespawnTimerInput;
    // Re-entry cooldown
    private EditBox reEntryCooldownInput;
    // Zone activation inputs (у Спец вкладці)
    private EditBox zoneRadiusInput;
    private EditBox zoneActivationTimerInput;
    private EditBox zoneOpenAfterStartInput;
    // Portal open after start
    private EditBox portalOpenAfterStartInput;
    // Victory linger
    private EditBox victoryLingerInput;
    // InfoPanel inputs
    private EditBox infoPanelOffsetYInput;
    private EditBox mobPanelOffsetYInput;
    private EditBox infoPanelTextScaleInput;
    // Scroll for special tab
    private int specialScrollOffset = 0;
    private int basicScrollOffset   = 0;
    // Widgets що є "статичними" (не прокручуються) — зберігаємо після init щоб render знав їх
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> staticWidgets
        = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private int basicContentHeight  = 400; // розраховується в initBasicTab
    private int specialContentHeight = 1200; // розраховується в initSpecialTab
    // Кількість частинок зони (задається в Спец)
    private EditBox particleCountInput = null;
    private EditBox particleSpeedInput = null;
    private EditBox particleIntervalInput = null;

    public LocationEditorScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.location_editor").append(": ").append(location.getName()));
        this.location = location;
        this.parent = parent;
    }

    /** Помічає widget як статичний (не прокручується) */
    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addStatic(T w) {
        this.addRenderableWidget(w);
        staticWidgets.add(w);
        return w;
    }

    @Override
    protected void init() {
        super.init();
        staticWidgets.clear();
        int cx = this.width / 2;

        // ── Вибір режиму PvE / PvP ──────────────────────────────────────────
        boolean isPve = location.getMode() == LocationMode.PVE;

        addStatic(Button.builder(
                ((isPve) ? Component.translatable("wavedefense.auto.pve_мобів_хвилі_8b812f4e") : Component.translatable("wavedefense.auto.pve_084743fc")),
                button -> { location.setMode(LocationMode.PVE); rebuildWidgets(); }
        ).bounds(cx - 105, 25, 100, 20).build());

        addStatic(Button.builder(
                ((!isPve) ? Component.translatable("wavedefense.auto.pvp_гравці_vs_гравці_610d2647") : Component.translatable("wavedefense.auto.pvp_f44347cd")),
                button -> { location.setMode(LocationMode.PVP); rebuildWidgets(); }
        ).bounds(cx + 5, 25, 100, 20).build());

        // ── Кнопка адмін-меню ───────────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.адмін_меню_ce02b449"),
                button -> this.minecraft.setScreen(new AdminMenuScreen())
        ).bounds(cx + 110, 25, 110, 20).build());

        // ── PvP — показуємо кнопку переходу до PvP-редактора ──────────────────
        // Виправлення G1: більше не авто-редиректимо на PvP-редактор, щоб гравець
        // міг повернутися і переключити режим назад на PvE.
        if (!isPve) {
            addStatic(Button.builder(
                    Component.translatable("wavedefense.button.open_pvp_editor"),
                    b -> this.minecraft.setScreen(new PvpLocationEditorScreen(location, this))
            ).bounds(cx - 120, 60, 240, 22).build());
            // Явна кнопка повернення до PvE (N18: тестер не бачив маленьку сіру кнопку зверху)
            addStatic(Button.builder(
                    Component.literal("§a← ").append(Component.translatable("wavedefense.auto.pve_мобів_хвилі_8b812f4e")),
                    b -> { location.setMode(LocationMode.PVE); rebuildWidgets(); }
            ).bounds(cx - 120, 88, 240, 18).build());
            // U7: Кнопка Save і Back у PvP-гілці (раніше Back була відсутня)
            addStatic(Button.builder(
                    Component.translatable("wavedefense.button.save"), button -> saveChanges()
            ).bounds(cx - 90, this.height - 28, 85, 20).build());
            addStatic(Button.builder(
                    Component.translatable("wavedefense.button.back"),
                    b -> this.minecraft.setScreen(parent)
            ).bounds(cx + 4, this.height - 28, 85, 20).build());
            return;
        }

        // ── PvE: вкладки (5 шт) ────────────────────────────────────────────
        int tabW = 68, tabGap = 3;
        int totalTabW = 5 * tabW + 4 * tabGap;
        int tabStartX = cx - totalTabW / 2;

        String[] tabNames = {"wavedefense.editor.tab.basic", "wavedefense.editor.tab.waves", "wavedefense.editor.tab.shop", "wavedefense.editor.tab.loot", "wavedefense.editor.tab.special"};
        for (int ti = 0; ti < tabNames.length; ti++) {
            final int fti = ti;
            addStatic(Button.builder(
                Component.literal(currentTab == ti ? "§a§l▶ " : "§7▶ ").append(Component.translatable(tabNames[ti])),
                b -> switchTab(fti)
            ).bounds(tabStartX + (tabW + tabGap) * ti, 52, tabW, 20).build());
        }

        int startY = 80;
        if      (currentTab == 0) initBasicTab(cx, startY);
        else if (currentTab == 1) initWavesTab(cx, startY);
        else if (currentTab == 2) initShopTab(cx, startY);
        else if (currentTab == 3) initLootTab(cx, startY);
        else                      initSpecialTab(cx, startY);

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.save_changes"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 30, 130, 20).build());
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.back_to_list"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 20, this.height - 30, 130, 20).build());
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.close"), button -> this.onClose()
        ).bounds(cx + 120, this.height - 30, 40, 20).build());
    }

    private void initBasicTab(int cx, int startY) {
        startY -= basicScrollOffset; // apply scroll
        // ── Точка спавну гравця — поля вводу координат ──────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.точка_спавну_гравця_17495206"), button -> {}
        ).bounds(cx - 150, startY, 180, 16).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.my_position"),
                button -> setPlayerSpawn()
        ).bounds(cx + 35, startY, 115, 16).build());

        int coordY = startY + 18;

        // startX=cx-150, labelW=16, fieldW=55, height=16, stride=78
        spawnCoordField = new CoordinateInputField(this.font, cx - 150, coordY, 16, 55, 16, 78);
        spawnCoordField.setValue(location.getPlayerSpawn());
        spawnCoordField.addToScreen(this::addRenderableWidget);

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.apply"),
                button -> applySpawnCoords()
        ).bounds(spawnCoordField.getEndX() + 7, coordY, 66, 16).build());

        int mobSpawnY = startY + 50;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.add_mob_spawn"),
                button -> addMobSpawn()
        ).bounds(cx - 150, mobSpawnY, 200, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.налаштовано_d_d_7b7a4447", location.getMobSpawns().size(), com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get()),
                button -> {}
        ).bounds(cx - 150, mobSpawnY + 22, 200, 18).build()).active = false;

        int listY = mobSpawnY + 48;
        int maxVisible = Math.min(MOB_SPAWN_PER_PAGE, location.getMobSpawns().size());
        for (int i = 0; i < maxVisible; i++) {
            int realIndex = i + mobSpawnScrollOffset;
            if (realIndex >= location.getMobSpawns().size()) break;
            com.wavedefense.data.MobSpawnPoint msp = location.getMobSpawns().get(realIndex);
            BlockPos pos = msp.getPos();
            final int index = realIndex;
            String radLabel = msp.getRadius() > 0 ? " §8(r:" + msp.getRadius() + ")" : "";
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.d_x_d_y_d_z_d_86ea3712", realIndex + 1,
                        pos.getX(), pos.getY(), pos.getZ() + radLabel),
                    button -> {}
            ).bounds(cx - 150, listY + (i * 22), 190, 20).build()).active = false;
            // Кнопка радіусу
            this.addRenderableWidget(Button.builder(
                    Component.literal("r" + (msp.getRadius() > 0 ? msp.getRadius() : "?")),
                    button -> cycleMobSpawnRadius(index)
            ).bounds(cx + 44, listY + (i * 22), 28, 20).build())
            .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("wavedefense.auto.радіус_розкиду_мобів_0_3_5_10_15_2c6fe91e")));
            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"), button -> removeMobSpawn(index)
            ).bounds(cx + 75, listY + (i * 22), 25, 20).build());
        }

        if (location.getMobSpawns().size() > MOB_SPAWN_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    button -> { if (mobSpawnScrollOffset > 0) { mobSpawnScrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 105, listY, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    button -> { if (mobSpawnScrollOffset + MOB_SPAWN_PER_PAGE < location.getMobSpawns().size()) { mobSpawnScrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 105, listY + maxVisible * 22, 20, 20).build());
        }

        int invY = listY + MOB_SPAWN_PER_PAGE * 22 + 8;

        // Видимість в меню гравців (перенесено з Спец вкладки)
        boolean hiddenBasic = location.isHiddenFromPlayers();
        this.addRenderableWidget(Button.builder(
            ((hiddenBasic) ? Component.translatable("wavedefense.auto.прихована_від_гравців_адміни_бачать_5177abbf") : Component.translatable("wavedefense.auto.видима_в_меню_гравців_45ca6b3b")),
            b -> { location.setHiddenFromPlayers(!location.isHiddenFromPlayers()); saveChanges(); rebuildWidgets(); }
        ).bounds(cx - 150, invY, 300, 18).build());
        invY += 22;

        // Збереження / очищення інвентаря
        this.addRenderableWidget(Button.builder(
                ((location.isKeepInventory()) ? Component.translatable("wavedefense.auto.зберігати_речі_гравця_28c03f14") : Component.translatable("wavedefense.auto.очищати_речі_при_вході_65174234")),
                button -> toggleKeepInventory()
        ).bounds(cx - 150, invY, 200, 18).build());

        if (!location.isKeepInventory()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.стартове_спорядження_6adb70f2"),
                    button -> this.minecraft.setScreen(new StartingItemsScreen(this, location))
            ).bounds(cx - 150, invY + 22, 200, 18).build());

            // Стартові поінти
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.стартові_поінти_0ddb7986"), b -> {}
            ).bounds(cx + 55, invY + 22, 105, 18).build()).active = false;
            startingPointsInput = new EditBox(this.font, cx + 162, invY + 22, 50, 18, Component.literal("0"));
            startingPointsInput.setValue(String.valueOf(location.getStartingPoints()));
            startingPointsInput.setMaxLength(6);
            this.addRenderableWidget(startingPointsInput);
            basicContentHeight = (invY + 44) - 80; // відносно startY=80
        } else {
            basicContentHeight = invY - 80 + 30;
        }
    }

    private void initWavesTab(int cx, int startY) {
        int left = cx - 170;
        int w = 340;
        int waveCount = location.getWaves().size();
        int configuredMobs = location.getWaves().stream().mapToInt(wv -> wv.getMobs().size()).sum();
        int triggerWaves = (int) location.getWaves().stream().filter(wv -> wv.isTriggerEnabled()).count();

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.wave_setup_59384912"), b -> {})
                .bounds(left, startY, w, 14).build()).active = false;
        startY += 20;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.waves_value_a63ed38b", waveCount), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.mob_entries_value_6fb910f1", configuredMobs), b -> {})
                .bounds(left + 180, startY, 160, 18).build()).active = false;
        startY += 22;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.trigger_waves_value_f6d52565", triggerWaves), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.literal("Completion pts: " + location.getCompletionPointsReward()), b -> {})
                .bounds(left + 180, startY, 160, 18).build()).active = false;
        startY += 30;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.edit_waves_and_mobs_1d2d0b4e"),
                button -> this.minecraft.setScreen(new WaveConfigScreen(location, this))
        ).bounds(left, startY, 160, 22).build());
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.completion_rewards_8be9b7b4"),
                button -> this.minecraft.setScreen(new CompletionRewardScreen(location, this))
        ).bounds(left + 180, startY, 160, 22).build());
        startY += 30;
        this.addRenderableWidget(Button.builder(((waveCount > 0) ? Component.translatable("wavedefense.auto.first_wave_and_inter_wave_timing_are_77fcea5b") : Component.translatable("wavedefense.auto.no_waves_configured_yet_53763308")), b -> {})
                .bounds(left, startY, w, 16).build()).active = false;
    }

    private void initShopTab(int cx, int startY) {
        int left = cx - 170;
        int w = 340;
        int globalItems = location.getShopItems().size();
        int pointItems = location.getShopPoints().stream().mapToInt(p -> p.getItems().size()).sum();
        String mode = location.isPointShopMode() ? "POINT shops" : "GLOBAL shop";

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.shop_setup_c569a20b"), b -> {})
                .bounds(left, startY, w, 14).build()).active = false;
        startY += 20;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.mode_value_c1d0d163", mode), b -> {})
                .bounds(left, startY, w, 18).build()).active = false;
        startY += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.global_items_value_1fefbc32", globalItems), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.literal("Shop points: " + location.getShopPoints().size()), b -> {})
                .bounds(left + 180, startY, 160, 18).build()).active = false;
        startY += 22;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.point_items_value_1adaa723", pointItems), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(((location.isPointShopMode()) ? Component.translatable("wavedefense.auto.use_global_shop_e7e61137") : Component.translatable("wavedefense.auto.use_point_shops_0603e880")),
                b -> { location.setShopMode(location.isPointShopMode() ? Location.ShopMode.GLOBAL : Location.ShopMode.POINT); rebuildWidgets(); }
        ).bounds(left + 180, startY, 160, 18).build());
        startY += 30;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.edit_shop_setup_e7f332da"),
                button -> this.minecraft.setScreen(new ShopEditorScreen(location, this))
        ).bounds(left, startY, 160, 22).build());
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.preview_player_shop_2937ead0"),
                button -> this.minecraft.setScreen(new PlayerShopScreen(location))
        ).bounds(left + 180, startY, 160, 22).build());
        startY += 30;
        this.addRenderableWidget(Button.builder(((location.isPointShopMode()) ? Component.translatable("wavedefense.auto.players_must_stand_near_configured_s_cbc7c8ad") : Component.translatable("wavedefense.auto.players_use_one_shared_shop_list_df446195")), b -> {})
                .bounds(left, startY, w, 16).build()).active = false;
    }

    private void initLootTab(int cx, int startY) {
        int left = cx - 170;
        int w = 340;
        int itemStacks = location.getLootSpawns().stream().mapToInt(l -> l.getItems().size()).sum();
        int triggerLinks = location.getLootSpawns().stream().mapToInt(l -> l.getTriggers().size()).sum();
        int pveLinks = location.getLootSpawns().stream().mapToInt(l -> (int) l.getTriggers().stream().filter(t -> t.pve).count()).sum();

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.loot_setup_4fdf07f1"), b -> {})
                .bounds(left, startY, w, 14).build()).active = false;
        startY += 20;
        this.addRenderableWidget(Button.builder(Component.literal("Loot points: " + location.getLootSpawns().size()), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.item_stacks_value_140ad990", itemStacks), b -> {})
                .bounds(left + 180, startY, 160, 18).build()).active = false;
        startY += 22;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.trigger_links_value_de433c3c", triggerLinks), b -> {})
                .bounds(left, startY, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.pve_triggers_value_37060374", pveLinks), b -> {})
                .bounds(left + 180, startY, 160, 18).build()).active = false;
        startY += 30;
        // G5: Видалено дублікат кнопки "Configure triggers" — вона відкривала той самий екран
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.edit_loot_points_401d74d4"),
                button -> this.minecraft.setScreen(new LootSpawnEditorScreen(location, this))
        ).bounds(left, startY, 200, 22).build());
        startY += 30;
        this.addRenderableWidget(Button.builder(((triggerLinks > 0) ? Component.translatable("wavedefense.auto.loot_is_connected_to_wave_player_loc_48bd6878") : Component.translatable("wavedefense.auto.no_loot_triggers_configured_yet_bafe0803")), b -> {})
                .bounds(left, startY, w, 16).build()).active = false;
    }

    private void switchTab(int tab) {
        // parseAllInputsToLocation() буде викликано автоматично з rebuildWidgets()
        this.currentTab = tab;
        this.basicScrollOffset = 0; // скидаємо скрол при зміні вкладки
        this.rebuildWidgets();
    }

    /**
     * Читає всі активні EditBox-поля і записує їх значення в об'єкт location.
     * Безпечно викликати будь-коли — перевіряє null для кожного поля.
     * НЕ викликає rebuildWidgets().
     */
    private void parseAllInputsToLocation() {
        // Координати спавну — без rebuildWidgets (applySpawnCoords() дзвонить rebuild)
        if (spawnCoordField != null && !spawnCoordField.isEmpty()) {
            BlockPos pos = spawnCoordField.getValue();
            if (pos != null) location.setPlayerSpawn(pos);
        }
        if (startingPointsInput != null) {
            try { location.setStartingPoints(Math.max(0, Integer.parseInt(startingPointsInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (boundaryRadiusInput != null) {
            try { location.setLocationBoundaryRadius(Math.max(1, Integer.parseInt(boundaryRadiusInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (leaveTimerInput != null) {
            try { location.setLocationLeaveTimerSec(Math.max(0, Integer.parseInt(leaveTimerInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (portalPenaltyTimerInput != null) {
            try { location.setPortalPenaltyTimerSec(Math.max(0, Integer.parseInt(portalPenaltyTimerInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (portalRespawnTimerInput != null) {
            try { location.setPortalRespawnTimerSec(Math.max(0, Integer.parseInt(portalRespawnTimerInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (reEntryCooldownInput != null) {
            try { location.setReEntryCooldownSec(Math.max(0, Integer.parseInt(reEntryCooldownInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (zoneRadiusInput != null) {
            try { location.setAutoActivateRadius(Math.max(1, Integer.parseInt(zoneRadiusInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (zoneActivationTimerInput != null) {
            try { location.setZoneActivationTimeSec(Math.max(1, Integer.parseInt(zoneActivationTimerInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (zoneOpenAfterStartInput != null) {
            // -1 is a valid sentinel meaning "disabled"
            try { int v = Integer.parseInt(zoneOpenAfterStartInput.getValue().trim()); location.setZoneOpenAfterStartSec(v < 0 ? -1 : v); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (portalOpenAfterStartInput != null) {
            // -1 is a valid sentinel meaning "disabled"
            try { int v = Integer.parseInt(portalOpenAfterStartInput.getValue().trim()); location.setPortalOpenAfterStartSec(v < 0 ? -1 : v); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (victoryLingerInput != null) {
            try { location.setVictoryLingerTimeSec(Math.max(0, Integer.parseInt(victoryLingerInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (particleCountInput != null) {
            try { location.setZoneParticleCount(Math.max(1, Integer.parseInt(particleCountInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (particleSpeedInput != null) {
            try { location.setZoneParticleSpeed(Math.max(0f, Float.parseFloat(particleSpeedInput.getValue().replace(',', '.').trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (particleIntervalInput != null) {
            try { location.setZoneParticleInterval(Math.max(1, Integer.parseInt(particleIntervalInput.getValue().trim()))); } // С1
            catch (NumberFormatException ignored) {}
        }
        if (infoPanelOffsetYInput != null) {
            try { location.getInfoPanel().setSpawnPanelOffsetY(Float.parseFloat(infoPanelOffsetYInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (mobPanelOffsetYInput != null) {
            try { location.getInfoPanel().setMobSpawnOffsetY(Float.parseFloat(mobPanelOffsetYInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (infoPanelTextScaleInput != null) {
            try { location.getInfoPanel().setTextScale(Float.parseFloat(infoPanelTextScaleInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
    }
    /**
     * Перед кожним перебудовуванням GUI зберігаємо поточні значення полів у location.
     * Це запобігає тихій втраті даних при скролі або будь-якому rebuildWidgets() виклику.
     */
    @Override
    public void rebuildWidgets() {
        parseAllInputsToLocation();
        super.rebuildWidgets();
    }

    private void setPlayerSpawn() {
        if (minecraft.player != null) {
            net.minecraft.core.BlockPos pos = minecraft.player.blockPosition();
            location.setPlayerSpawn(pos);
            rebuildWidgets();
        }
    }

    private void applySpawnCoords() {
        if (spawnCoordField == null) return;
        if (spawnCoordField.isEmpty()) {
            setPlayerSpawn();
            return;
        }
        BlockPos pos = spawnCoordField.getValue();
        if (pos != null) {
            location.setPlayerSpawn(pos);
            rebuildWidgets();
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.auto.невірний_формат_координат_607bcb51"), true);
        }
    }
    private void addMobSpawn() { if (minecraft.player != null && location.getMobSpawns().size() < com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get()) { location.addMobSpawn(minecraft.player.blockPosition()); rebuildWidgets(); } }
    private void cycleMobSpawnRadius(int index) {
        if (index < 0 || index >= location.getMobSpawns().size()) return;
        com.wavedefense.data.MobSpawnPoint msp = location.getMobSpawns().get(index);
        int[] steps = {0, 3, 5, 10, 15, 20};
        int cur = msp.getRadius();
        int next = steps[0];
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == cur) { next = steps[(i + 1) % steps.length]; break; }
        }
        msp.setRadius(next);
        rebuildWidgets();
    }

    private void removeMobSpawn(int index) {
        location.removeMobSpawn(index);
        if (mobSpawnScrollOffset > 0 && mobSpawnScrollOffset >= location.getMobSpawns().size())
            mobSpawnScrollOffset = Math.max(0, location.getMobSpawns().size() - MOB_SPAWN_PER_PAGE);
        rebuildWidgets();
    }
    private void toggleKeepInventory() { location.setKeepInventory(!location.isKeepInventory()); rebuildWidgets(); }

    private void saveChanges() {
        parseAllInputsToLocation();
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.зміни_збережено_60feafc0"), true);
        // Примітка: навмисно НЕ надсилаємо RequestLocationDataPacket після збереження,
        // щоб уникнути race-condition де відповідь з OLD даними перезаписує свіжі зміни.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Блокуємо кліки на scrolled widgets що вийшли за межі scissor-зони (PvE режим)
        if (!location.isPvp()) {
            final int CLIP_TOP = 76;
            final int CLIP_BOT = this.height - 34;
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    if (staticWidgets.contains(w)) continue; // статичні — не блокуємо
                    int wy = w.getY(), wh = w.getHeight();
                    // Widget повністю поза scissor (вгорі або внизу) — блокуємо клік
                    // незалежно від того де знаходиться миша, щоб scrolled-in-header баг не спрацьовував
                    if ((wy + wh <= CLIP_TOP || wy >= CLIP_BOT) && w.isMouseOver(mx, my)) {
                        return false;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape — закриваємо без збереження
        if (keyCode == 256) { this.minecraft.setScreen(parent); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!location.isPvp() && currentTab == 0) {
            // Скрол всієї вкладки "Основні"
            int clipBot0 = this.height - 34;
            int maxScroll0 = Math.max(0, basicContentHeight - (clipBot0 - 76));
            if (delta > 0 && basicScrollOffset > 0) { basicScrollOffset = Math.max(0, basicScrollOffset - 12); rebuildWidgets(); }
            else if (delta < 0 && basicScrollOffset < maxScroll0) { basicScrollOffset = Math.min(maxScroll0, basicScrollOffset + 12); rebuildWidgets(); }
            return true;
        }
        if (currentTab == 4) { // Спец tab
            int clipBot4 = this.height - 34;
            int maxScroll4 = Math.max(0, specialContentHeight - (clipBot4 - 76));
            if (delta > 0 && specialScrollOffset > 0) { specialScrollOffset = Math.max(0, specialScrollOffset - 12); rebuildWidgets(); }
            else if (delta < 0 && specialScrollOffset < maxScroll4) { specialScrollOffset = Math.min(maxScroll4, specialScrollOffset + 12); rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);
        GuiTheme.renderHeader(graphics, this.font, this.title, this.width);

        if (location.isPvp()) {
            // PvP режим — без scissor
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Таби розміщено на Y=52, height=20 → нижній край = 72.
        // Контент починається від startY=80 (до скролу).
        // Нижні кнопки: Y = height-30, height=20 → верхній край = height-30.
        final int TAB_Y         = 52;  // фіксований Y вкладок
        final int TAB_H         = 20;  // висота вкладок
        final int CONTENT_TOP   = TAB_Y + TAB_H; // = 72, нижній край табів
        final int CONTENT_BOT   = this.height - 30; // верхній край нижніх кнопок
        final int CLIP_TOP      = CONTENT_TOP + 4;  // = 76, з невеликим відступом
        final int CLIP_BOT      = CONTENT_BOT - 4;  // 4px буфер щоб контент не торкався footer

        // Content frame around the scrollable area
        GuiTheme.renderContentFrame(graphics, 8, CLIP_TOP - 4, this.width - 8, CLIP_BOT + 4);

        // ── Крок 1: прокручений контент через scissor [CLIP_TOP..CLIP_BOT] ──
        // Статичні widgets (PvE/PvP toggle, таби, нижні кнопки) у scissor не рендеряться.
        ScissorHelper.enable(0, CLIP_TOP, this.width, Math.max(1, CLIP_BOT - CLIP_TOP));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (!staticWidgets.contains(w)) {
                    w.render(graphics, mouseX, mouseY, partialTick);
                }
            }
        }
        // Special tab: section dividers for Boundary / Zone / Portal
        if (currentTab == 4) {
            int panelW = Math.min(330, this.width - 40);
            int lx = this.width / 2 - panelW / 2;
            if (ySectionBoundary >= CLIP_TOP && ySectionBoundary <= CLIP_BOT)
                GuiTheme.sectionDivider(graphics, this.font,
                    net.minecraft.network.chat.Component.translatable("wavedefense.section.boundary"),
                    lx, ySectionBoundary, panelW);
            if (ySectionZone >= CLIP_TOP && ySectionZone <= CLIP_BOT)
                GuiTheme.sectionDivider(graphics, this.font,
                    net.minecraft.network.chat.Component.translatable("wavedefense.section.zone"),
                    lx, ySectionZone, panelW);
            if (ySectionPortal >= CLIP_TOP && ySectionPortal <= CLIP_BOT)
                GuiTheme.sectionDivider(graphics, this.font,
                    net.minecraft.network.chat.Component.translatable("wavedefense.section.portal"),
                    lx, ySectionPortal, panelW);
        }
        ScissorHelper.disable();

        // ── Крок 2: статична зона ВГОРІ (заголовок + PvE/PvP toggle + таби) ──
        // GuiTheme.renderHeader вже намалював заголовок — рендеримо лише статичні widgets.
        ScissorHelper.enable(0, 0, this.width, CLIP_TOP);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && staticWidgets.contains(w) && w.getY() < CLIP_TOP) {
                w.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        ScissorHelper.disable();

        // ── Крок 3: статична зона ВНИЗУ (Зберегти / Назад / Закрити) ─────
        ScissorHelper.enable(0, CONTENT_BOT, this.width, this.height - CONTENT_BOT);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && staticWidgets.contains(w) && w.getY() >= CONTENT_BOT) {
                w.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        ScissorHelper.disable();

        // Tooltips (після всіх renders)
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn && btn.isHoveredOrFocused() && btn.active) {
                String t = getLocTip(btn.getMessage());
                if (t != null) TooltipHelper.renderIfEnabled(graphics, this.font, t, mouseX, mouseY);
            }
        });
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════════════════════════
    //  СПЕЦІАЛЬНА ВКЛАДКА — Boundary + Location Trigger + Portal
    // ══════════════════════════════════════════════════════════════════
    private void initSpecialTab(int cx, int startY) {
        // Flush all inputs before rebuilding, then reset field references
        parseAllInputsToLocation();
        boundaryRadiusInput      = null;
        leaveTimerInput          = null;
        portalPenaltyTimerInput  = null;
        portalRespawnTimerInput  = null;
        reEntryCooldownInput     = null;
        infoPanelOffsetYInput    = null;
        mobPanelOffsetYInput     = null;
        infoPanelTextScaleInput  = null;
        victoryLingerInput       = null;
        particleCountInput       = null;
        particleSpeedInput       = null;
        particleIntervalInput    = null;

        int panelW = Math.min(330, this.width - 40);
        int lx = cx - panelW / 2;
        int y = startY - specialScrollOffset;

        y = initGameModeSection(lx, panelW, y);
        y = initVictorySection(lx, panelW, y);
        y = initExitPointsSection(lx, panelW, y);
        y = initParticlesSection(lx, panelW, y);
        y = initBoundarySection(lx, panelW, y);
        y = initZoneSection(lx, panelW, y);
        y = initPortalSection(lx, panelW, y);
        y = initInfoPanelsSection(lx, panelW, y);

        specialContentHeight = (y + specialScrollOffset) - startY + 20;
    }

    // ── Секція: Режим гри ─────────────────────────────────────────────────
    private int initGameModeSection(int lx, int panelW, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.режим_гри_cd24dd4a"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        boolean egm = location.isEnforceGameMode();
        this.addRenderableWidget(Button.builder(
            ((egm) ? Component.translatable("wavedefense.auto.примусовий_режим_гри_survival_advent_1ffc85b7") : Component.translatable("wavedefense.auto.не_примусувати_режим_гри_8df3c880")),
            b -> { location.setEnforceGameMode(!location.isEnforceGameMode()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 16).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            net.minecraft.network.chat.Component.translatable("wavedefense.auto.якщо_увімкнено_creative_автомати_d277a6a9")));
        y += 20;
        return y;
    }

    // ── Секція: Перемога ──────────────────────────────────────────────────
    private int initVictorySection(int lx, int panelW, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.перемога_22c21fa7"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        boolean vs = location.isVictoryScreenEnabled();
        this.addRenderableWidget(Button.builder(
            ((vs) ? Component.translatable("wavedefense.auto.показувати_екран_перемоги_23a65e43") : Component.translatable("wavedefense.auto.без_екрану_перемоги_одразу_виходити_753ec2e8")),
            b -> { location.setVictoryScreenEnabled(!location.isVictoryScreenEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 16).build());
        y += 20;
        if (vs) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.час_на_локації_після_перемоги_се_fb069255"), b -> {}
            ).bounds(lx, y, 226, 16).build()).active = false;
            victoryLingerInput = new EditBox(this.font, lx + 230, y, 60, 16, Component.literal("30"));
            victoryLingerInput.setMaxLength(4);
            victoryLingerInput.setValue(String.valueOf(location.getVictoryLingerTimeSec()));
            victoryLingerInput.setResponder(s -> { try { location.setVictoryLingerTimeSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(victoryLingerInput);
            y += 22;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ℹ_після_перемоги_title_перемога_c6807e66"), b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
            y += 16;
        }
        return y;
    }

    // ── Секція: Точки виходу ──────────────────────────────────────────────
    private int initExitPointsSection(int lx, int panelW, int y) {
        y += 4;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.точки_виходу_94b4bb33"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        net.minecraft.core.BlockPos vep = location.getVictoryExitPos();
        String vepLbl = vep != null
            ? String.format("§a✓ Перемога: X%d Y%d Z%d", vep.getX(), vep.getY(), vep.getZ())
            : "§7Перемога: повернути на попереднє місце";
        this.addRenderableWidget(Button.builder(Component.literal(vepLbl), b -> {}).bounds(lx, y, panelW - 88, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.задати_311c5f00"),
            b -> { if (minecraft.player != null) { location.setVictoryExitPos(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
        ).bounds(lx + panelW - 84, y, 42, 14).build());
        if (vep != null) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕"),
                b -> { location.setVictoryExitPos(null); saveChanges(); rebuildWidgets(); }
            ).bounds(lx + panelW - 40, y, 40, 14).build());
        }
        y += 18;
        net.minecraft.core.BlockPos sep = location.getSurrenderExitPos();
        String sepLbl = sep != null
            ? String.format("§a✓ Здача: X%d Y%d Z%d", sep.getX(), sep.getY(), sep.getZ())
            : "§7Здача: повернути на попереднє місце";
        this.addRenderableWidget(Button.builder(Component.literal(sepLbl), b -> {}).bounds(lx, y, panelW - 88, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.задати_311c5f00"),
            b -> { if (minecraft.player != null) { location.setSurrenderExitPos(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
        ).bounds(lx + panelW - 84, y, 42, 14).build());
        if (sep != null) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕"),
                b -> { location.setSurrenderExitPos(null); saveChanges(); rebuildWidgets(); }
            ).bounds(lx + panelW - 40, y, 40, 14).build());
        }
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.ℹ_null_гравець_повертається_на_м_e0cde428"), b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 14;
        return y;
    }

    // ── Секція: Частинки зони входу ───────────────────────────────────────
    private int initParticlesSection(int lx, int panelW, int y) {
        y += 4;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.частинки_зони_входу_344e5bd1"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        String curPart = location.getZoneParticleType();
        String partDisplay = (curPart == null || curPart.isEmpty()) ? "§7minecraft:squid_ink (за замовчуванням)" : "§a" + curPart;
        this.addRenderableWidget(Button.builder(Component.literal(partDisplay), b -> {}).bounds(lx, y, panelW, 14).build()).active = false;
        y += 16;
        String[] particlePresets = {"minecraft:squid_ink","minecraft:flame","minecraft:end_rod","minecraft:witch","minecraft:portal","minecraft:snowflake","minecraft:happy_villager"};
        String[] particleLabels  = {"squid","flame","star","witch","portal","snow","✿ villager"};
        int ppBtnW = (panelW - 6) / 7;
        for (int pi = 0; pi < particlePresets.length; pi++) {
            final String pp = particlePresets[pi];
            final int fpi = pi;
            boolean sel = pp.equals(curPart) || (pi == 0 && (curPart == null || curPart.isEmpty()));
            this.addRenderableWidget(Button.builder(
                Component.literal(sel ? "§a§l" + particleLabels[pi] : "§7" + particleLabels[pi]),
                b -> { location.setZoneParticleType(fpi == 0 ? null : pp); saveChanges(); rebuildWidgets(); }
            ).bounds(lx + pi * (ppBtnW + 1), y, ppBtnW, 16).build())
            .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("§7" + pp)));
        }
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.ℹ_тип_частинок_у_колі_навколо_то_2317049d"), b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 16;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.точок_у_кільці_0_авто_4c831a7f"), b -> {}
        ).bounds(lx, y, 170, 14).build()).active = false;
        particleCountInput = new EditBox(this.font, lx + 174, y, 60, 14, Component.literal("0"));
        particleCountInput.setMaxLength(3);
        particleCountInput.setValue(String.valueOf(location.getZoneParticleCount()));
        particleCountInput.setResponder(s -> {
            try { location.setZoneParticleCount(Integer.parseInt(s.trim())); }
            catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(particleCountInput);
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.швидкість_частинок_0_тихо_0_5_по_c839e80c"), b -> {}
        ).bounds(lx, y, 236, 14).build()).active = false;
        {
            boolean[] suppressParticleSpeed = {true};
            particleSpeedInput = new EditBox(this.font, lx + 240, y, 56, 14, Component.literal("0.02"));
            particleSpeedInput.setMaxLength(6);
            particleSpeedInput.setResponder(s -> {
                if (suppressParticleSpeed[0]) return;
                try {
                    float v = Float.parseFloat(s.replace(',', '.').trim());
                    if (v >= 0f && v <= 10f) location.setZoneParticleSpeed(v);
                } catch (NumberFormatException ignored) {}
            });
            particleSpeedInput.setValue(String.format("%.3f", location.getZoneParticleSpeed()));
            suppressParticleSpeed[0] = false;
            this.addRenderableWidget(particleSpeedInput);
        }
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.інтервал_частинок_тіків_1_кожен_6c362ed0"), b -> {}
        ).bounds(lx, y, 270, 14).build()).active = false;
        {
            boolean[] suppressInterval = {true};
            particleIntervalInput = new EditBox(this.font, lx + 274, y, 40, 14, Component.literal("1"));
            particleIntervalInput.setMaxLength(3);
            particleIntervalInput.setResponder(s -> {
                if (suppressInterval[0]) return;
                try {
                    int v = Integer.parseInt(s.trim());
                    if (v >= 1 && v <= 200) location.setZoneParticleInterval(v);
                } catch (NumberFormatException ignored) {}
            });
            particleIntervalInput.setValue(String.valueOf(location.getZoneParticleInterval()));
            suppressInterval[0] = false;
            this.addRenderableWidget(particleIntervalInput);
        }
        y += 18;
        return y;
    }

    // ── Секція: Кордон локації ────────────────────────────────────────────
    private int initBoundarySection(int lx, int panelW, int y) {
        ySectionBoundary = y;
        y += 4;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.кордон_локації_a8d00d3b"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        boolean boundaryOn = location.isLocationBoundaryEnabled();
        this.addRenderableWidget(Button.builder(
            ((boundaryOn) ? Component.translatable("wavedefense.auto.відстеження_кордону_увімкнено_4e64ccbf") : Component.translatable("wavedefense.auto.відстеження_кордону_вимкнено_fee949db")),
            b -> { location.setLocationBoundaryEnabled(!location.isLocationBoundaryEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        if (boundaryOn) {
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.радіус_кордону_блоків_9d7152e5"), b -> {}
            ).bounds(lx, y, 190, 16).build()).active = false;
            boundaryRadiusInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("50"));
            boundaryRadiusInput.setValue(String.valueOf(location.getLocationBoundaryRadius()));
            boundaryRadiusInput.setMaxLength(5);
            this.addRenderableWidget(boundaryRadiusInput);
            y += 22;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.наслідок_виходу_за_кордон_70311161"), b -> {}
            ).bounds(lx, y, panelW, 14).build()).active = false;
            y += 16;
            com.wavedefense.data.Location.BoundaryConsequence[] conseqs = {
                com.wavedefense.data.Location.BoundaryConsequence.TIMER_SURRENDER,
                com.wavedefense.data.Location.BoundaryConsequence.DAMAGE,
                com.wavedefense.data.Location.BoundaryConsequence.TELEPORT_BACK,
                com.wavedefense.data.Location.BoundaryConsequence.INSTANT_SURRENDER
            };
            String[] conseqKeys = {
                "wavedefense.boundary.consequence.timer_surrender",
                "wavedefense.boundary.consequence.damage",
                "wavedefense.boundary.consequence.teleport_back",
                "wavedefense.boundary.consequence.instant_surrender"
            };
            for (int ci = 0; ci < conseqs.length; ci++) {
                final com.wavedefense.data.Location.BoundaryConsequence cq = conseqs[ci];
                boolean sel = location.getBoundaryConsequence() == cq;
                String lbl = (sel ? "§a● " : "§7○ ")
                    + net.minecraft.client.resources.language.I18n.get(conseqKeys[ci]);
                this.addRenderableWidget(Button.builder(
                    Component.literal(lbl),
                    b -> { location.setBoundaryConsequence(cq); rebuildWidgets(); }
                ).bounds(lx + (ci % 2) * (panelW / 2), y + (ci / 2) * 20, panelW / 2 - 2, 18).build());
            }
            y += 44;
            com.wavedefense.data.Location.BoundaryConsequence bc = location.getBoundaryConsequence();
            if (bc == com.wavedefense.data.Location.BoundaryConsequence.TIMER_SURRENDER) {
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.час_на_повернення_сек_abeae7e4"), b -> {}
                ).bounds(lx, y, 190, 16).build()).active = false;
                leaveTimerInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("30"));
                leaveTimerInput.setValue(String.valueOf(location.getLocationLeaveTimerSec()));
                leaveTimerInput.setMaxLength(4);
                this.addRenderableWidget(leaveTimerInput);
                y += 22;
            } else if (bc == com.wavedefense.data.Location.BoundaryConsequence.DAMAGE) {
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.шкода_hp_сек_70beedeb"), b -> {}
                ).bounds(lx, y, 190, 16).build()).active = false;
                // С2: use class field so the typed value is preserved across consequence-type changes
                boundaryDamageInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("2.0"));
                boundaryDamageInput.setValue(String.format("%.1f", location.getBoundaryDamagePerSec()));
                boundaryDamageInput.setMaxLength(5);
                boundaryDamageInput.setResponder(s -> { try { location.setBoundaryDamagePerSec(Float.parseFloat(s)); } catch(Exception ignored){} });
                this.addRenderableWidget(boundaryDamageInput);
                y += 22;
            }
            boolean partOn = location.isBoundaryParticlesEnabled();
            this.addRenderableWidget(Button.builder(
                ((partOn) ? Component.translatable("wavedefense.auto.частинки_кордону_увімкнено_fe3e3d59") : Component.translatable("wavedefense.auto.частинки_кордону_вимкнено_fc06f33c")),
                b -> { location.setBoundaryParticlesEnabled(!location.isBoundaryParticlesEnabled()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 20;
            if (partOn) {
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.тип_частинок_registry_id_279de6c4"), b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
                y += 14;
                EditBox ptypeBox = new EditBox(this.font, lx, y, 190, 16, Component.literal("minecraft:smoke"));
                ptypeBox.setValue(location.getBoundaryParticleType());
                ptypeBox.setMaxLength(64);
                ptypeBox.setResponder(s -> { if (!s.isBlank()) location.setBoundaryParticleType(s.trim()); });
                this.addRenderableWidget(ptypeBox);
                y += 20;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.к_сть_93fe635b"), b -> {}
                ).bounds(lx, y, 44, 16).build()).active = false;
                EditBox pcntBox = new EditBox(this.font, lx + 46, y, 40, 16, Component.literal("4"));
                pcntBox.setValue(String.valueOf(location.getBoundaryParticleCount()));
                pcntBox.setMaxLength(3);
                pcntBox.setResponder(s -> { try { location.setBoundaryParticleCount(Integer.parseInt(s)); } catch(Exception ig){} });
                this.addRenderableWidget(pcntBox);
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.висота_8dfaebb3"), b -> {}
                ).bounds(lx + 96, y, 50, 16).build()).active = false;
                EditBox phtBox = new EditBox(this.font, lx + 148, y, 40, 16, Component.literal("3"));
                phtBox.setValue(String.valueOf(location.getBoundaryParticleHeight()));
                phtBox.setMaxLength(3);
                phtBox.setResponder(s -> { try { location.setBoundaryParticleHeight(Integer.parseInt(s)); } catch(Exception ig){} });
                this.addRenderableWidget(phtBox);
                y += 22;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.пресет_a7847c4d"), b -> {}
                ).bounds(lx, y, 48, 14).build()).active = false;
                String[] presets = {"§7smoke","§cflame","§6portal","§bsnow","§denchant"};
                String[] pids    = {"minecraft:smoke","minecraft:flame","minecraft:portal",
                                    "minecraft:snowflake","minecraft:enchant"};
                for (int pi = 0; pi < presets.length; pi++) {
                    final String pid = pids[pi];
                    this.addRenderableWidget(Button.builder(
                        Component.literal(presets[pi]),
                        b -> { location.setBoundaryParticleType(pid); rebuildWidgets(); }
                    ).bounds(lx + 50 + pi * 50, y, 48, 14).build());
                }
                y += 18;
            }
        }
        y += boundaryOn ? 6 : 22;
        return y;
    }

    // ── Секція: Авто-активація зони ───────────────────────────────────────
    private int initZoneSection(int lx, int panelW, int y) {
        ySectionZone = y;
        y += 4;
        boolean aa = location.isAutoActivate();
        this.addRenderableWidget(Button.builder(
            ((aa) ? Component.translatable("wavedefense.auto.авто_активація_зони_увімкнено_af93bd2a") : Component.translatable("wavedefense.auto.авто_активація_зони_вимкнено_2c63e91a")),
            b -> { location.setAutoActivate(!location.isAutoActivate()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 16).build());
        y += 20;
        if (aa) {
            boolean useCustomCenter = location.isZoneUsesCustomCenter();
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.location.zone_center", useCustomCenter ? net.minecraft.client.resources.language.I18n.get("wavedefense.location.zone_center.custom") : net.minecraft.client.resources.language.I18n.get("wavedefense.location.zone_center.player_spawn")), 
                b -> { location.setZoneUsesCustomCenter(!location.isZoneUsesCustomCenter()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 18;
            if (useCustomCenter) {
                net.minecraft.core.BlockPos zc = location.getZoneCenter();
                String zcLabel = zc != null
                    ? String.format("§a✓ X%d Y%d Z%d", zc.getX(), zc.getY(), zc.getZ())
                    : "§c⚠ Не встановлено";
                this.addRenderableWidget(Button.builder(
                    Component.literal(zcLabel), b -> {}
                ).bounds(lx, y, panelW - 90, 14).build()).active = false;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.встановити_тут_bc78c4b4"),
                    b -> { if (minecraft.player != null) { location.setZoneCenter(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
                ).bounds(lx + panelW - 86, y, 86, 14).build());
                y += 18;
            }
            net.minecraft.core.BlockPos ep = location.getAutoActivateEntryPos();
            String epLabel = ep != null
                ? String.format("§a✓ Вхід: X%d Y%d Z%d", ep.getX(), ep.getY(), ep.getZ())
                : "§7Вхід: = точка спавну гравця";
            this.addRenderableWidget(Button.builder(Component.literal(epLabel), b -> {}).bounds(lx, y, panelW - 86, 14).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.задати_вхід_cc0fd093"),
                b -> { if (minecraft.player != null) { location.setAutoActivateEntryPos(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
            ).bounds(lx + panelW - 86, y, 86, 14).build());
            if (ep != null) {
                y += 14;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.скинути_точку_входу_5ae06e05"),
                    b -> { location.setAutoActivateEntryPos(null); saveChanges(); rebuildWidgets(); }
                ).bounds(lx, y, panelW, 12).build());
            }
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.радіус_зони_бл_9e546ee1"), b -> {}
            ).bounds(lx, y, 140, 16).build()).active = false;
            zoneRadiusInput = new EditBox(this.font, lx + 144, y, 50, 16, Component.literal("5"));
            zoneRadiusInput.setValue(String.valueOf(location.getAutoActivateRadius()));
            zoneRadiusInput.setMaxLength(4);
            zoneRadiusInput.setResponder(s -> { try { location.setAutoActivateRadius(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(zoneRadiusInput);
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.затримка_активації_сек_0_миттєво_9bd4fffe"), b -> {}
            ).bounds(lx, y, 220, 16).build()).active = false;
            zoneActivationTimerInput = new EditBox(this.font, lx + 224, y, 50, 16, Component.literal("0"));
            zoneActivationTimerInput.setValue(String.valueOf(location.getZoneActivationTimeSec()));
            zoneActivationTimerInput.setMaxLength(4);
            zoneActivationTimerInput.setResponder(s -> { try { location.setZoneActivationTimeSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(zoneActivationTimerInput);
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.зона_відкрита_після_старту_сек_0_e16dd239"), b -> {}
            ).bounds(lx, y, 262, 16).build()).active = false;
            zoneOpenAfterStartInput = new EditBox(this.font, lx + 266, y, 50, 16, Component.literal("0"));
            zoneOpenAfterStartInput.setValue(String.valueOf(location.getZoneOpenAfterStartSec()));
            zoneOpenAfterStartInput.setMaxLength(4);
            zoneOpenAfterStartInput.setResponder(s -> { try { location.setZoneOpenAfterStartSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(zoneOpenAfterStartInput);
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ℹ_0_зона_вимикається_після_запус_630eddd4"), b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
            y += 14;
        }
        return y;
    }

    // ── Секція: Портал ────────────────────────────────────────────────────
    private int initPortalSection(int lx, int panelW, int y) {
        ySectionPortal = y;
        y += 8;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.портал_3a6b2e9f"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        boolean portalOn = location.isPortalEnabled();
        this.addRenderableWidget(Button.builder(
            ((portalOn) ? Component.translatable("wavedefense.auto.рандомний_портал_увімкнено_10cf9544") : Component.translatable("wavedefense.auto.рандомний_портал_вимкнено_05bf94ae")),
            b -> { location.setPortalEnabled(!location.isPortalEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        if (portalOn) {
            y += 22;
            int penaltyW  = location.getPortalPenaltyWave();
            String penStr = (penaltyW == -1) ? "§eВсі хвилі по порядку" : ("§eХвиля " + (penaltyW + 1));
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.штрафна_хвиля_value_6ff1fd17", penStr), b -> {}
            ).bounds(lx, y, panelW - 80, 16).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.literal("◀"),
                b -> {
                    int pw = location.getPortalPenaltyWave();
                    location.setPortalPenaltyWave(pw <= -1 ? location.getWaves().size() - 1 : pw - 1);
                    rebuildWidgets();
                }
            ).bounds(lx + panelW - 76, y, 22, 16).build());
            this.addRenderableWidget(Button.builder(
                Component.literal("▶"),
                b -> {
                    int pw = location.getPortalPenaltyWave();
                    location.setPortalPenaltyWave(pw >= location.getWaves().size() - 1 ? -1 : pw + 1);
                    rebuildWidgets();
                }
            ).bounds(lx + panelW - 50, y, 22, 16).build());
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.таймер_штрафної_хвилі_сек_dbf1e775"), b -> {}
            ).bounds(lx, y, 200, 16).build()).active = false;
            portalPenaltyTimerInput = new EditBox(this.font, lx + 204, y, 60, 16, Component.literal("60"));
            portalPenaltyTimerInput.setValue(String.valueOf(location.getPortalPenaltyTimerSec()));
            portalPenaltyTimerInput.setMaxLength(5);
            this.addRenderableWidget(portalPenaltyTimerInput);
            y += 20;
            y += 4;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.портал_відкритий_після_старту_се_fb5b2465"), b -> {}
            ).bounds(lx, y, 232, 16).build()).active = false;
            portalOpenAfterStartInput = new EditBox(this.font, lx + 236, y, 60, 16, Component.literal("-1"));
            portalOpenAfterStartInput.setValue(String.valueOf(location.getPortalOpenAfterStartSec()));
            portalOpenAfterStartInput.setMaxLength(5);
            portalOpenAfterStartInput.setResponder(s -> { try { location.setPortalOpenAfterStartSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(portalOpenAfterStartInput);
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ℹ_1_grace_30сек_0_закрити_одразу_7733239c"), b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
            y += 16;
            boolean disappears = location.isPortalDisappearsOnComplete();
            this.addRenderableWidget(Button.builder(
                ((disappears) ? Component.translatable("wavedefense.auto.зникає_після_проходження_локації_c618248e") : Component.translatable("wavedefense.auto.не_зникає_штрафний_таймер_скидається_c420e6b9")),
                b -> { location.setPortalDisappearsOnComplete(!location.isPortalDisappearsOnComplete()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 18).build());
            if (disappears) {
                y += 22;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.час_до_відродження_порталу_сек_1d853c38"), b -> {}
                ).bounds(lx, y, 220, 16).build()).active = false;
                portalRespawnTimerInput = new EditBox(this.font, lx + 224, y, 60, 16, Component.literal("300"));
                portalRespawnTimerInput.setValue(String.valueOf(location.getPortalRespawnTimerSec()));
                portalRespawnTimerInput.setMaxLength(5);
                this.addRenderableWidget(portalRespawnTimerInput);
                y += 18;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.ℹ_після_проходження_портал_зʼяви_417790bf"),
                    b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
            } else {
                y += 22;
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.ℹ_портал_залишається_штрафний_та_4ab3ed5c"),
                    b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
            }
        }
        return y;
    }

    // ── Секція: Інфо-панелі (TextDisplay) ────────────────────────────────
    private int initInfoPanelsSection(int lx, int panelW, int y) {
        y += 8;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.ℹ_інфо_панель_textdisplay_29d73807"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.textdisplay_entity_плаваючий_тек_370a3888"),
            b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 14;
        com.wavedefense.data.InfoPanelSettings ips = location.getInfoPanel();
        boolean spawnPanel = ips.isSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            ((spawnPanel) ? Component.translatable("wavedefense.auto.панель_на_точці_спавну_гравців_5b432a6c") : Component.translatable("wavedefense.auto.панель_на_точці_спавну_гравців_f194768e")),
            b -> { location.getInfoPanel().setSpawnPanelEnabled(!location.getInfoPanel().isSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        y += 22;
        if (spawnPanel) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.відображати_8516d51a"), b -> {}
            ).bounds(lx, y, 90, 12).build()).active = false;
            y += 14;
            int bW = (panelW - 4) / 2;
            boolean showPlayers = ips.isShowPlayerCount();
            this.addRenderableWidget(Button.builder(
                ((showPlayers) ? Component.translatable("wavedefense.auto.гравців_у_локації_aa087ff4") : Component.translatable("wavedefense.auto.гравців_у_локації_7e4c8270")),
                b -> { location.getInfoPanel().setShowPlayerCount(!location.getInfoPanel().isShowPlayerCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showWave = ips.isShowWaveNumber();
            this.addRenderableWidget(Button.builder(
                ((showWave) ? Component.translatable("wavedefense.auto.номер_хвилі_1ad4a3ff") : Component.translatable("wavedefense.auto.номер_хвилі_8a901b48")),
                b -> { location.getInfoPanel().setShowWaveNumber(!location.getInfoPanel().isShowWaveNumber()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean showTimer = ips.isShowWaveTimer();
            this.addRenderableWidget(Button.builder(
                ((showTimer) ? Component.translatable("wavedefense.auto.таймер_до_хвилі_1217f3f4") : Component.translatable("wavedefense.auto.таймер_до_хвилі_8c2dba5a")),
                b -> { location.getInfoPanel().setShowWaveTimer(!location.getInfoPanel().isShowWaveTimer()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showMobs = ips.isShowMobsRemaining();
            this.addRenderableWidget(Button.builder(
                ((showMobs) ? Component.translatable("wavedefense.auto.мобів_залишилось_9af0b6f5") : Component.translatable("wavedefense.auto.мобів_залишилось_d181b6ae")),
                b -> { location.getInfoPanel().setShowMobsRemaining(!location.getInfoPanel().isShowMobsRemaining()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean showSecrets = ips.isShowSecretCount();
            this.addRenderableWidget(Button.builder(
                ((showSecrets) ? Component.translatable("wavedefense.auto.секретних_хвиль_333873b8") : Component.translatable("wavedefense.auto.секретних_хвиль_289b154b")),
                b -> { location.getInfoPanel().setShowSecretCount(!location.getInfoPanel().isShowSecretCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showShop = ips.isShowShopSecrets();
            this.addRenderableWidget(Button.builder(
                ((showShop) ? Component.translatable("wavedefense.auto.умовних_товарів_339a8618") : Component.translatable("wavedefense.auto.умовних_товарів_04defc96")),
                b -> { location.getInfoPanel().setShowShopSecrets(!location.getInfoPanel().isShowShopSecrets()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean showPoints = ips.isShowPoints();
            this.addRenderableWidget(Button.builder(
                ((showPoints) ? Component.translatable("wavedefense.auto.поінти_гравця_69c456e4") : Component.translatable("wavedefense.auto.поінти_гравця_a902bb96")),
                b -> { location.getInfoPanel().setShowPoints(!location.getInfoPanel().isShowPoints()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showFwt = ips.isShowFirstWaveTimer();
            this.addRenderableWidget(Button.builder(
                ((showFwt) ? Component.translatable("wavedefense.auto.таймер_першої_хвилі_4e93f6f1") : Component.translatable("wavedefense.auto.таймер_першої_хвилі_fbeb613d")),
                b -> { location.getInfoPanel().setShowFirstWaveTimer(!location.getInfoPanel().isShowFirstWaveTimer()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean showLbt = ips.isShowLobbyTimer();
            this.addRenderableWidget(Button.builder(
                ((showLbt) ? Component.translatable("wavedefense.auto.таймер_лоббі_над_входом_f25befbc") : Component.translatable("wavedefense.auto.таймер_лоббі_над_входом_7943344b")),
                b -> { location.getInfoPanel().setShowLobbyTimer(!location.getInfoPanel().isShowLobbyTimer()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.висота_над_спавном_блоків_7ffddea4"), b -> {}
            ).bounds(lx, y, 186, 16).build()).active = false;
            infoPanelOffsetYInput = new EditBox(this.font, lx + 190, y, 60, 16, Component.literal("2.5"));
            infoPanelOffsetYInput.setValue(String.valueOf(ips.getSpawnPanelOffsetY()));
            infoPanelOffsetYInput.setMaxLength(5);
            infoPanelOffsetYInput.setResponder(s -> { try { location.getInfoPanel().setSpawnPanelOffsetY(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(infoPanelOffsetYInput);
            y += 22;
        }
        y += 4;
        boolean mobPanel = ips.isMobSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            ((mobPanel) ? Component.translatable("wavedefense.auto.панель_над_точками_спавну_мобів_c0275577") : Component.translatable("wavedefense.auto.панель_над_точками_спавну_мобів_0058db55")),
            b -> { location.getInfoPanel().setMobSpawnPanelEnabled(!location.getInfoPanel().isMobSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        y += 22;
        if (mobPanel) {
            int bW = (panelW - 4) / 2;
            boolean mTimer = ips.isMobShowWaveTimer();
            this.addRenderableWidget(Button.builder(
                ((mTimer) ? Component.translatable("wavedefense.auto.таймер_до_хвилі_1217f3f4") : Component.translatable("wavedefense.auto.таймер_до_хвилі_8c2dba5a")),
                b -> { location.getInfoPanel().setMobShowWaveTimer(!location.getInfoPanel().isMobShowWaveTimer()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean mWave = ips.isMobShowWaveNumber();
            this.addRenderableWidget(Button.builder(
                ((mWave) ? Component.translatable("wavedefense.auto.номер_хвилі_1ad4a3ff") : Component.translatable("wavedefense.auto.номер_хвилі_8a901b48")),
                b -> { location.getInfoPanel().setMobShowWaveNumber(!location.getInfoPanel().isMobShowWaveNumber()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean mMob = ips.isMobShowMobCount();
            this.addRenderableWidget(Button.builder(
                ((mMob) ? Component.translatable("wavedefense.auto.к_ть_мобів_в_хвилі_7043e0be") : Component.translatable("wavedefense.auto.к_ть_мобів_в_хвилі_84413751")),
                b -> { location.getInfoPanel().setMobShowMobCount(!location.getInfoPanel().isMobShowMobCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.висота_над_спавном_мобів_блоків_07872bd5"), b -> {}
            ).bounds(lx, y, 210, 16).build()).active = false;
            mobPanelOffsetYInput = new EditBox(this.font, lx + 214, y, 60, 16, Component.literal("2.5"));
            mobPanelOffsetYInput.setValue(String.valueOf(ips.getMobSpawnOffsetY()));
            mobPanelOffsetYInput.setMaxLength(5);
            mobPanelOffsetYInput.setResponder(s -> { try { location.getInfoPanel().setMobSpawnOffsetY(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(mobPanelOffsetYInput);
            y += 22;
        }
        if (spawnPanel || mobPanel) {
            y += 4;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.масштаб_тексту_0_1_2_0_951e20d9"), b -> {}
            ).bounds(lx, y, 172, 16).build()).active = false;
            infoPanelTextScaleInput = new EditBox(this.font, lx + 176, y, 50, 16, Component.literal("0.5"));
            infoPanelTextScaleInput.setValue(String.valueOf(ips.getTextScale()));
            infoPanelTextScaleInput.setMaxLength(5);
            infoPanelTextScaleInput.setResponder(s -> { try { location.getInfoPanel().setTextScale(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(infoPanelTextScaleInput);
            y += 20;
            boolean shadow = ips.isHasShadow();
            this.addRenderableWidget(Button.builder(
                ((shadow) ? Component.translatable("wavedefense.auto.тінь_тексту_0216d6a1") : Component.translatable("wavedefense.auto.без_тіні_058581fc")),
                b -> { location.getInfoPanel().setHasShadow(!location.getInfoPanel().isHasShadow()); rebuildWidgets(); }
            ).bounds(lx, y, 140, 16).build());
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ℹ_панель_видима_з_будь_якого_бок_a7b9b4ab"),
                b -> {}
            ).bounds(lx, y, panelW, 11).build()).active = false;
            y += 14;
        }
        return y;
    }

    private String getLocTip(net.minecraft.network.chat.Component msg) {
        // Primary match: use the stable i18n key (locale-independent)
        net.minecraft.network.chat.ComponentContents contents = msg.getContents();
        if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            String key = tc.getKey();
            if (key.contains("авто_активація_зони"))  return TooltipHelper.ZONE_ACTIVATE;
            if (key.contains("радіус_зони_бл"))        return TooltipHelper.ZONE_RADIUS;
            if (key.equals("wavedefense.button.save_changes")) return TooltipHelper.SAVE_ALL_SETTINGS;
            if (key.equals("wavedefense.button.save")) return TooltipHelper.SAVE_SETTINGS;
            if (key.startsWith("wavedefense.auto.pve_") || key.startsWith("wavedefense.auto.pvp_")) return TooltipHelper.MODE_TOGGLE;
            if (key.contains("зберігати_речі") || key.contains("очищати_речі")) return TooltipHelper.KEEP_INVENTORY;
            if (key.contains("стартові_поінти"))       return TooltipHelper.STARTING_POINTS;
            if (key.equals("wavedefense.button.my_position") || key.contains("точка_спавну_гравця")) return TooltipHelper.SPAWN_COORDS;
            if (key.equals("wavedefense.button.apply")) return TooltipHelper.SPAWN_COORDS;
            if (key.contains("відстеження_кордону") || key.contains("кордон_локації")) return TooltipHelper.BOUNDARY_ZONE;
            if (key.contains("портал") || key.contains("рандомний_портал")) return TooltipHelper.PORTAL_RANDOM;
            if (key.contains("примусовий_режим_гри") || key.contains("не_примусувати_режим")) return TooltipHelper.ENFORCE_GAMEMODE;
            if (key.equals("wavedefense.button.add_mob_spawn")) return TooltipHelper.MOB_SPAWN_ADD;
            if (key.contains("таймер_лоббі"))          return TooltipHelper.LOBBY_TIMER;
        }
        // Fallback: plain-text matching for any remaining non-translatable labels
        String plain = msg.getString().replaceAll("§.", "").toLowerCase();
        if (plain.contains("авто-активац"))                                    return TooltipHelper.ZONE_ACTIVATE;
        if (plain.contains("радіус активації"))                                return TooltipHelper.ZONE_RADIUS;
        if (plain.contains("зберегти зміни"))                                  return TooltipHelper.SAVE_ALL_SETTINGS;
        if (plain.contains("зберегти") && !plain.contains("назад"))            return TooltipHelper.SAVE_SETTINGS;
        if (plain.contains("pvp") || plain.contains("pve"))                    return TooltipHelper.MODE_TOGGLE;
        if (plain.contains("зберігати речі") || plain.contains("очищати речі")) return TooltipHelper.KEEP_INVENTORY;
        if (plain.contains("стартові поінти") || plain.contains("поінти"))     return TooltipHelper.STARTING_POINTS;
        if (plain.contains("📌") || plain.contains("моя позиція"))             return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("застосувати"))                                     return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("кордон"))                                          return TooltipHelper.BOUNDARY_ZONE;
        if (plain.contains("тригер запуску"))                                  return TooltipHelper.AUTO_TRIGGER;
        if (plain.contains("портал"))                                          return TooltipHelper.PORTAL_RANDOM;
        if (plain.contains("разово"))                                          return TooltipHelper.WAVE_TRIGGER_ONCE;
        if (plain.contains("and"))                                             return TooltipHelper.WAVE_TRIGGER_AND;
        if (plain.contains("точка спавну гравця"))                             return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("додати точку спавну мобів"))                       return TooltipHelper.MOB_SPAWN_ADD;
        if (plain.contains("таймер лобі") || plain.contains("таймер лоббі"))   return TooltipHelper.LOBBY_TIMER;
        return null;
    }
}


