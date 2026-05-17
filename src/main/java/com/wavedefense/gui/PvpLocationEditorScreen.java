package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.wavedefense.gui.ScissorHelper;
import com.wavedefense.gui.TooltipHelper;

import java.util.List;

/**
 * Екран налаштування PvP локації.
 * Вкладки: Команди | Правила | Магазин | Лут
 */
public class PvpLocationEditorScreen extends Screen {

    private final Location location;
    private final Screen parent;
    private int currentTab = 0; // 0=команди, 1=правила, 2=магазин, 3=лут

    // Для редагування точки спавну
    private boolean editingSpawn = false;
    private int editingSpawnIndex = -1;
    private EditBox spawnNameInput;
    private CoordinateInputField spawnCoordField;
    private EditBox spawnRadiusInput;

    // Поля правил
    private EditBox minPlayersInput;
    private EditBox killPointsInput;
    private EditBox deathPenaltyInput;
    private EditBox totalRoundsInput;
    private EditBox buyTimeInput;
    private EditBox pvpStartingPointsInput;
    private EditBox roundStartDelayInput;
    private EditBox dmKillsToWinInput;
    private EditBox brBorderRadiusInput;
    private EditBox brShrinkIntervalInput;
    private EditBox brBorderParticleInput;
    private EditBox brBorderDamageAmtInput;
    private EditBox roundStartPointsInput;
    private EditBox winPointsInput;
    private EditBox losePointsInput;
    private EditBox boundaryRadiusInput;
    private EditBox leaveTimerInput;
    private EditBox boundaryDamageInput;
    private EditBox portalPenaltyTimerInput;
    private EditBox portalRespawnTimerInput;
    private EditBox portalOpenAfterStartInput;
    private EditBox victoryLingerInput;
    private EditBox reEntryCooldownInput;

    private int scrollOffset = 0;
    private int rulesScrollOffset = 0;
    private int rulesContentHeight = 0;
    private static final int PER_PAGE = 6;
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> staticWidgets
        = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addStatic(T w) {
        this.addRenderableWidget(w); staticWidgets.add(w); return w;
    }

    public PvpLocationEditorScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.pvp.editor.title", location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // Вкладки — 4 по 80px, відцентровані
        int tabW = 70, tabGap = 3;
        int totalTabW = 5 * tabW + 4 * tabGap;
        int tabX = cx - totalTabW / 2;

        staticWidgets.clear();
        String[] tabs = {"wavedefense.pvp.editor.tab.teams", "wavedefense.pvp.editor.tab.rules", "wavedefense.pvp.editor.tab.shop", "wavedefense.pvp.editor.tab.loot", "wavedefense.pvp.editor.tab.common"};
        for (int i = 0; i < tabs.length; i++) {
            final int ti = i;
            addStatic(Button.builder(
                    Component.literal(currentTab == i ? "?a?l? " : "?7? ").append(Component.translatable(tabs[i])),
                    button -> { currentTab = ti; scrollOffset = 0; rulesScrollOffset = 0; rebuildWidgets(); }
            ).bounds(tabX + i * (tabW + tabGap), 25, tabW, 20).build());
        }

        int startY = 52;

        switch (currentTab) {
            case 0 -> initTeamsTab(cx, startY);
            case 1 -> initModeAndRulesTab(cx, startY - rulesScrollOffset);
            case 2 -> initShopTab(cx, startY);
            case 3 -> initLootTab(cx, startY);
            case 4 -> initCommonLocationTab(cx, startY - rulesScrollOffset);
        }

        // Нижня панель
        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.зберегти_617e5dc0"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 28, 100, 20).build());

        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.назад_f6dab074"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    // ─── Вкладка: Команди ──────────────────────────────────────────────
    private void initTeamsTab(int cx, int y) {
        if (editingSpawn) {
            initEditSpawn(cx, y);
            return;
        }

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.додати_точку_спавну_команди_на_п_3cfc522e"),
                button -> startAddSpawn()
        ).bounds(cx - 175, y, 310, 20).build());
        y += 26;

        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        int rowH = 30;
        for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= spawns.size()) break;
            PvpSpawnPoint sp = spawns.get(idx);
            int yPos = y + i * rowH;

            BlockPos pos = sp.getPos();
            String radiusStr = sp.getSpawnRadius() > 0 ? " §8(R:" + sp.getSpawnRadius() + ")" : "";
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.s_x_d_y_d_z_d_16be266e", sp.getTeamName(), pos.getX(), pos.getY(), pos.getZ() + radiusStr),
                    button -> {}
            ).bounds(cx - 175, yPos, 300, 20).build()).active = false;

            final int fIdx = idx;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"), button -> startEditSpawn(fIdx)
            ).bounds(cx + 130, yPos, 22, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"), button -> { location.removePvpSpawnPoint(fIdx); rebuildWidgets(); }
            ).bounds(cx + 155, yPos, 22, 20).build());
        }

        if (spawns.size() > PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 182, y, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    button -> { if (scrollOffset + PER_PAGE < spawns.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 182, y + (PER_PAGE - 1) * rowH, 18, 18).build());
        }
    }

    private void initEditSpawn(int cx, int y) {
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.назва_команди_d589534e"), button -> {}
        ).bounds(cx - 155, y, 120, 16).build()).active = false;
        spawnNameInput = new EditBox(this.font, cx - 155, y + 18, 200, 20, Component.translatable("wavedefense.auto.назва_382c87f9"));
        if (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size()) {
            spawnNameInput.setValue(location.getPvpSpawnPoints().get(editingSpawnIndex).getTeamName());
        }
        spawnNameInput.setMaxLength(32);
        this.addRenderableWidget(spawnNameInput);
        y += 46;

        // Координати точки спавну з полями вводу
        BlockPos existingPos = null;
        if (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size()) {
            existingPos = location.getPvpSpawnPoints().get(editingSpawnIndex).getPos();
        }
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.координати_порожньо_ваша_позиція_4b5638ba"), b -> {}).bounds(cx - 155, y, 280, 14).build()).active = false;
        y += 16;

        // startX=cx-155, labelW=14, fieldW=52, height=16, stride=73
        spawnCoordField = new CoordinateInputField(this.font, cx - 155, y, 14, 52, 16, 73);
        spawnCoordField.setValue(existingPos); // null → clear fields; non-null → fill
        spawnCoordField.addToScreen(this::addRenderableWidget);

        this.addRenderableWidget(Button.builder(Component.literal("📌"),
                b -> spawnCoordField.setFromPlayer(minecraft.player)
        ).bounds(spawnCoordField.getEndX() + 7, y, 30, 16).build());

        y += 24;
        // Радіус розкиду
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.радіус_розкиду_гравців_0_точно_в_807ed3ed"), b -> {}).bounds(cx - 155, y, 246, 16).build()).active = false;
        int curRadius = (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size())
            ? location.getPvpSpawnPoints().get(editingSpawnIndex).getSpawnRadius() : 0;
        spawnRadiusInput = new EditBox(this.font, cx + 94, y, 50, 16, Component.literal("0"));
        spawnRadiusInput.setValue(String.valueOf(curRadius));
        spawnRadiusInput.setMaxLength(3);
        this.addRenderableWidget(spawnRadiusInput);

        y += 28;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.зберегти_617e5dc0"), button -> saveSpawn()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.скасувати_8b4c2025"), button -> { editingSpawn = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ─── Вкладка: Правила ──────────────────────────────────────────────
    /**
     * Вкладка "Режим + Правила" — вибір підрежиму PvP і всі правила,
     * згруповані за режимом (Стандарт / Deathmatch / Королівська Битва).
     */
    private void initModeAndRulesTab(int cx, int y) {
        com.wavedefense.data.Location.PvpMode mode = location.getPvpMode();

        // ── Вибір режиму ───────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.підрежим_pvp_b45dc081"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        String[] modeLabels = {"⚔ Стандарт", "⚡ Deathmatch", "👑 Королівська Битва"};
        com.wavedefense.data.Location.PvpMode[] modes = com.wavedefense.data.Location.PvpMode.values();
        int mBtnW = 100, mBtnGap = 5;
        int mStartX = cx - (modes.length * mBtnW + (modes.length - 1) * mBtnGap) / 2;
        for (int mi = 0; mi < modes.length; mi++) {
            final com.wavedefense.data.Location.PvpMode fm = modes[mi];
            boolean sel = mode == fm;
            this.addRenderableWidget(Button.builder(
                Component.literal(sel ? "§a§l" + modeLabels[mi] : "§7" + modeLabels[mi]),
                b -> { location.setPvpMode(fm); rulesScrollOffset = 0; rebuildWidgets(); }
            ).bounds(mStartX + mi * (mBtnW + mBtnGap), y, mBtnW, 20).build());
        }
        y += 28;

        y = initCommonRulesSection(cx, y);

        if (mode == com.wavedefense.data.Location.PvpMode.STANDARD || mode == null)
            y = initStandardSection(cx, y);
        else if (mode == com.wavedefense.data.Location.PvpMode.DEATHMATCH)
            y = initDeathmatchSection(cx, y);
        else if (mode == com.wavedefense.data.Location.PvpMode.BATTLE_ROYALE)
            y = initBattleRoyaleSection(cx, y);

        y += 4;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.застосувати_правила_8d60cd48"), b -> saveAllRules()
        ).bounds(cx - 110, y, 220, 22).build());
        y += 28;

        int teamCount = location.getPvpSpawnPoints().size();
        String info = teamCount < 2
            ? "§c⚠ Потрібно ≥2 точки спавну (вкладка Команди)"
            : String.format("§a✓ %d команд | мін. гравців: %d", teamCount, location.getPvpMinPlayers());
        this.addRenderableWidget(Button.builder(
            Component.literal(info), b -> {}
        ).bounds(cx - 160, y, 320, 16).build()).active = false;
        y += 20;
        rulesContentHeight = y + rulesScrollOffset - 52;
        int maxScroll = getRulesMaxScroll();
        if (rulesScrollOffset > maxScroll) {
            rulesScrollOffset = maxScroll;
            rebuildWidgets();
        }
    }

    // ── Секція: Загальні правила ──────────────────────────────────────────
    private int initCommonRulesSection(int cx, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.загальні_налаштування_bc65baa9"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.мін_гравців_9190e1f8"), b -> {}
        ).bounds(cx - 160, y, 100, 18).build()).active = false;
        minPlayersInput = new EditBox(this.font, cx - 55, y, 45, 18, Component.literal("2"));
        minPlayersInput.setValue(String.valueOf(location.getPvpMinPlayers()));
        minPlayersInput.setMaxLength(4);
        this.addRenderableWidget(minPlayersInput);
        this.addRenderableWidget(Button.builder(
            ((location.isPvpFriendlyFire()) ? Component.translatable("wavedefense.auto.friendly_fire_682b119f") : Component.translatable("wavedefense.auto.friendly_fire_c4d59c59")),
            b -> { location.setPvpFriendlyFire(!location.isPvpFriendlyFire()); rebuildWidgets(); }
        ).bounds(cx, y, 160, 18).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
            ((location.isEnforceGameMode()) ? Component.translatable("wavedefense.auto.прим_режим_гри_ef53fcd4") : Component.translatable("wavedefense.auto.прим_режим_гри_3560c1e1")),
            b -> { location.setEnforceGameMode(!location.isEnforceGameMode()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 155, 18).build());
        this.addRenderableWidget(Button.builder(
            ((location.isPvpWaitEffect()) ? Component.translatable("wavedefense.auto.ефекти_очікування_a00e16cf") : Component.translatable("wavedefense.auto.ефекти_очікування_68ecadcc")),
            b -> { location.setPvpWaitEffect(!location.isPvpWaitEffect()); rebuildWidgets(); }
        ).bounds(cx, y, 160, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.slowness_blindness_замість_spect_d2e35d3b")));
        y += 24;
        this.addRenderableWidget(Button.builder(
            ((location.isPvpTeamAutoBalance()) ? Component.translatable("wavedefense.auto.автобаланс_команд_8f24c22d") : Component.translatable("wavedefense.auto.автобаланс_команд_cfacda52")),
            b -> { location.setPvpTeamAutoBalance(!location.isPvpTeamAutoBalance()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build());
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.стартові_поінти_0ddb7986"), b -> {}
        ).bounds(cx - 160, y, 120, 18).build()).active = false;
        pvpStartingPointsInput = new EditBox(this.font, cx - 36, y, 50, 18, Component.literal("0"));
        pvpStartingPointsInput.setValue(String.valueOf(location.getStartingPoints()));
        pvpStartingPointsInput.setMaxLength(6);
        this.addRenderableWidget(pvpStartingPointsInput);
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.поінтів_за_вбивство_e46b811d"), b -> {}
        ).bounds(cx + 20, y, 140, 18).build()).active = false;
        killPointsInput = new EditBox(this.font, cx + 165, y, 50, 18, Component.literal("100"));
        killPointsInput.setValue(String.valueOf(location.getPvpKillPoints()));
        killPointsInput.setMaxLength(6);
        this.addRenderableWidget(killPointsInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.поінтів_за_смерть_6cf8af4d"), b -> {}
        ).bounds(cx - 160, y, 140, 18).build()).active = false;
        deathPenaltyInput = new EditBox(this.font, cx - 14, y, 50, 18, Component.literal("50"));
        deathPenaltyInput.setValue(String.valueOf(location.getPvpDeathPenalty()));
        deathPenaltyInput.setMaxLength(6);
        this.addRenderableWidget(deathPenaltyInput);
        y += 28;
        return y;
    }

    // ── Секція: Стандарт ─────────────────────────────────────────────────
    private int initStandardSection(int cx, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.стандарт_раунди_d4dfe43d"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.к_сть_раундів_d95b4363"), b -> {}
        ).bounds(cx - 160, y, 115, 18).build()).active = false;
        totalRoundsInput = new EditBox(this.font, cx - 40, y, 50, 18, Component.literal("10"));
        totalRoundsInput.setValue(String.valueOf(location.getPvpTotalRounds()));
        totalRoundsInput.setMaxLength(4);
        this.addRenderableWidget(totalRoundsInput);
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.час_покупок_сек_096e52d6"), b -> {}
        ).bounds(cx + 16, y, 135, 18).build()).active = false;
        buyTimeInput = new EditBox(this.font, cx + 155, y, 50, 18, Component.literal("20"));
        buyTimeInput.setValue(String.valueOf(location.getPvpBuyTime()));
        buyTimeInput.setMaxLength(4);
        this.addRenderableWidget(buyTimeInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.затримка_старту_раунду_сек_cfdb62ef"), b -> {}
        ).bounds(cx - 160, y, 200, 18).build()).active = false;
        roundStartDelayInput = new EditBox(this.font, cx + 46, y, 50, 18, Component.literal("5"));
        roundStartDelayInput.setValue(String.valueOf(location.getPvpRoundStartDelay()));
        roundStartDelayInput.setMaxLength(3);
        this.addRenderableWidget(roundStartDelayInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.поінти_на_початок_раунду_8cb5d92d"), b -> {}
        ).bounds(cx - 160, y, 180, 18).build()).active = false;
        roundStartPointsInput = new EditBox(this.font, cx + 26, y, 50, 18, Component.literal("0"));
        roundStartPointsInput.setValue(String.valueOf(location.getPvpRoundStartPoints()));
        roundStartPointsInput.setMaxLength(6);
        this.addRenderableWidget(roundStartPointsInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.поінтів_переможцю_раунду_346394bc"), b -> {}
        ).bounds(cx - 160, y, 190, 18).build()).active = false;
        winPointsInput = new EditBox(this.font, cx + 36, y, 50, 18, Component.literal("0"));
        winPointsInput.setValue(String.valueOf(location.getPvpWinPoints()));
        winPointsInput.setMaxLength(6);
        this.addRenderableWidget(winPointsInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.поінтів_команді_що_програла_64402749"), b -> {}
        ).bounds(cx - 160, y, 200, 18).build()).active = false;
        losePointsInput = new EditBox(this.font, cx + 46, y, 50, 18, Component.literal("0"));
        losePointsInput.setValue(String.valueOf(location.getPvpLosePoints()));
        losePointsInput.setMaxLength(6);
        this.addRenderableWidget(losePointsInput);
        y += 24;
        return y;
    }

    // ── Секція: Deathmatch ────────────────────────────────────────────────
    private int initDeathmatchSection(int cx, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.deathmatch_8cceaa3d"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.к_сть_раундів_d95b4363"), b -> {}
        ).bounds(cx - 160, y, 115, 18).build()).active = false;
        totalRoundsInput = new EditBox(this.font, cx - 40, y, 50, 18, Component.literal("1"));
        totalRoundsInput.setValue(String.valueOf(location.getPvpTotalRounds()));
        totalRoundsInput.setMaxLength(4);
        this.addRenderableWidget(totalRoundsInput);
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.вбивств_для_перемоги_2b7edec6"), b -> {}
        ).bounds(cx + 16, y, 150, 18).build()).active = false;
        dmKillsToWinInput = new EditBox(this.font, cx + 170, y, 50, 18, Component.literal("10"));
        dmKillsToWinInput.setValue(String.valueOf(location.getDmKillsToWin()));
        dmKillsToWinInput.setMaxLength(4);
        this.addRenderableWidget(dmKillsToWinInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.час_покупок_між_раундами_сек_407ce665"), b -> {}
        ).bounds(cx - 160, y, 210, 18).build()).active = false;
        buyTimeInput = new EditBox(this.font, cx + 55, y, 50, 18, Component.literal("20"));
        buyTimeInput.setValue(String.valueOf(location.getPvpBuyTime()));
        buyTimeInput.setMaxLength(4);
        this.addRenderableWidget(buyTimeInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.ℹ_гравці_відроджуються_миттєво_н_6cda78dd"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        return y;
    }

    // ── Секція: Королівська Битва ─────────────────────────────────────────
    private int initBattleRoyaleSection(int cx, int y) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.королівська_битва_054a8309"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.ℹ_гравців_кидає_на_випадкову_точ_32ff2c23"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.початковий_радіус_кордону_бл_e4aa3555"), b -> {}
        ).bounds(cx - 160, y, 210, 18).build()).active = false;
        brBorderRadiusInput = new EditBox(this.font, cx + 55, y, 60, 18, Component.literal("100"));
        brBorderRadiusInput.setValue(String.valueOf(location.getBrBorderRadius()));
        brBorderRadiusInput.setMaxLength(5);
        this.addRenderableWidget(brBorderRadiusInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.звуження_1_бл_кожні_сек_7aabf000"), b -> {}
        ).bounds(cx - 160, y, 210, 18).build()).active = false;
        brShrinkIntervalInput = new EditBox(this.font, cx + 55, y, 60, 18, Component.literal("30"));
        brShrinkIntervalInput.setValue(String.valueOf(location.getBrShrinkIntervalSec()));
        brShrinkIntervalInput.setMaxLength(5);
        this.addRenderableWidget(brShrinkIntervalInput);
        y += 24;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.частинки_кордону_id_bcab91af"), b -> {}
        ).bounds(cx - 160, y, 155, 18).build()).active = false;
        brBorderParticleInput = new EditBox(this.font, cx, y, 160, 18, Component.literal("minecraft:flame"));
        brBorderParticleInput.setValue(location.getBrBorderParticle());
        brBorderParticleInput.setMaxLength(64);
        this.addRenderableWidget(brBorderParticleInput);
        y += 24;
        boolean dmg = location.isBrBorderDamage();
        this.addRenderableWidget(Button.builder(
            ((dmg) ? Component.translatable("wavedefense.auto.шкода_поза_зоною_aff5f80e") : Component.translatable("wavedefense.auto.без_шкоди_53dc7156")),
            b -> { location.setBrBorderDamage(!location.isBrBorderDamage()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 150, 18).build());
        if (dmg) {
            brBorderDamageAmtInput = new EditBox(this.font, cx, y, 55, 18, Component.literal("1.0"));
            brBorderDamageAmtInput.setValue(String.format("%.1f", location.getBrBorderDamageAmt()));
            brBorderDamageAmtInput.setMaxLength(5);
            // Persist immediately so the value survives rebuildWidgets() when the toggle is clicked
            brBorderDamageAmtInput.setResponder(s -> {
                try { location.setBrBorderDamageAmt(Math.max(0f, Float.parseFloat(s.trim()))); }
                catch (NumberFormatException ignored) {}
            });
            this.addRenderableWidget(brBorderDamageAmtInput);
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.hp_сек_801e0516"), b -> {}
            ).bounds(cx + 58, y, 50, 18).build()).active = false;
        }
        y += 24;
        return y;
    }

    private void saveAllRules() {
        try {
            if (minPlayersInput != null)        location.setPvpMinPlayers(Integer.parseInt(minPlayersInput.getValue()));
            if (killPointsInput != null)        location.setPvpKillPoints(Math.max(0, Integer.parseInt(killPointsInput.getValue())));
            if (deathPenaltyInput != null)      location.setPvpDeathPenalty(Math.max(0, Integer.parseInt(deathPenaltyInput.getValue())));
            if (totalRoundsInput != null)       location.setPvpTotalRounds(Math.max(1, Integer.parseInt(totalRoundsInput.getValue())));
            if (buyTimeInput != null)           location.setPvpBuyTime(Math.max(5, Integer.parseInt(buyTimeInput.getValue())));
            if (pvpStartingPointsInput != null) location.setStartingPoints(Math.max(0, Integer.parseInt(pvpStartingPointsInput.getValue())));
            if (roundStartDelayInput != null)   location.setPvpRoundStartDelay(Math.max(0, Integer.parseInt(roundStartDelayInput.getValue())));
            if (roundStartPointsInput != null)  location.setPvpRoundStartPoints(Math.max(0, Integer.parseInt(roundStartPointsInput.getValue())));
            if (winPointsInput != null)         location.setPvpWinPoints(Math.max(0, Integer.parseInt(winPointsInput.getValue())));
            if (losePointsInput != null)        location.setPvpLosePoints(Math.max(0, Integer.parseInt(losePointsInput.getValue())));
            if (dmKillsToWinInput != null)      location.setDmKillsToWin(Math.max(1, Integer.parseInt(dmKillsToWinInput.getValue())));
            if (brBorderRadiusInput != null)    location.setBrBorderRadius(Math.max(10, Integer.parseInt(brBorderRadiusInput.getValue())));
            if (brShrinkIntervalInput != null)  location.setBrShrinkIntervalSec(Math.max(1, Integer.parseInt(brShrinkIntervalInput.getValue())));
            if (brBorderParticleInput != null && !brBorderParticleInput.getValue().isBlank())
                location.setBrBorderParticle(brBorderParticleInput.getValue().trim());
            if (brBorderDamageAmtInput != null) location.setBrBorderDamageAmt(Math.max(0f, Float.parseFloat(brBorderDamageAmtInput.getValue())));
        } catch (NumberFormatException ignored) {}
    }


    // ─── Вкладка: Магазин ──────────────────────────────────────────────
    private void initShopTab(int cx, int y) {
        int left = cx - 170;
        int w = 340;
        int pointItems = location.getShopPoints().stream().mapToInt(p -> p.getItems().size()).sum();
        String mode = location.isPointShopMode() ? "POINT shops" : "GLOBAL shop";

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.pvp_shop_8bf923a3"),
                button -> {}
        ).bounds(left, y, w, 14).build()).active = false;
        y += 20;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.mode_value_c1d0d163", mode),
                button -> {}
        ).bounds(left, y, w, 18).build()).active = false;
        y += 24;

        this.addRenderableWidget(Button.builder(
                Component.literal("Global items: " + location.getShopItems().size()),
                button -> {}
        ).bounds(left, y, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("Shop points: " + location.getShopPoints().size()),
                button -> {}
        ).bounds(left + 180, y, 160, 18).build()).active = false;
        y += 22;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.point_items_value_1adaa723", pointItems),
                button -> {}
        ).bounds(left, y, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(
                ((location.isPointShopMode()) ? Component.translatable("wavedefense.auto.use_global_shop_e7e61137") : Component.translatable("wavedefense.auto.use_point_shops_0603e880")),
                button -> { location.setShopMode(location.isPointShopMode() ? Location.ShopMode.GLOBAL : Location.ShopMode.POINT); rebuildWidgets(); }
        ).bounds(left + 180, y, 160, 18).build());
        y += 30;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.edit_shop_setup_e7f332da"),
                button -> this.minecraft.setScreen(new ShopEditorScreen(location, this))
        ).bounds(left, y, 160, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.preview_player_shop_2937ead0"),
                button -> this.minecraft.setScreen(new PlayerShopScreen(location))
        ).bounds(left + 180, y, 160, 22).build());
        y += 30;

        this.addRenderableWidget(Button.builder(
                ((location.isPointShopMode()) ? Component.translatable("wavedefense.auto.players_open_shops_only_inside_confi_c08ebfe1") : Component.translatable("wavedefense.auto.players_open_one_shared_shop_with_th_49d0485d")),
                button -> {}
        ).bounds(left, y, w, 16).build()).active = false;
    }

    private void initLootTab(int cx, int y) {
        int left = cx - 170;
        int w = 340;
        int itemStacks = location.getLootSpawns().stream().mapToInt(l -> l.getItems().size()).sum();
        int triggerLinks = location.getLootSpawns().stream().mapToInt(l -> l.getTriggers().size()).sum();
        int pvpLinked = location.getLootSpawns().stream().mapToInt(l -> (int) l.getTriggers().stream().filter(t -> t.pvp).count()).sum();

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.pvp_loot_a18a62aa"),
                button -> {}
        ).bounds(left, y, w, 14).build()).active = false;
        y += 20;

        this.addRenderableWidget(Button.builder(
                Component.literal("Loot points: " + location.getLootSpawns().size()),
                button -> {}
        ).bounds(left, y, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.item_stacks_value_140ad990", itemStacks),
                button -> {}
        ).bounds(left + 180, y, 160, 18).build()).active = false;
        y += 22;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.trigger_links_value_de433c3c", triggerLinks),
                button -> {}
        ).bounds(left, y, 160, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.pvp_triggers_value_f99caec4", pvpLinked),
                button -> {}
        ).bounds(left + 180, y, 160, 18).build()).active = false;
        y += 30;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.edit_loot_points_401d74d4"),
                button -> this.minecraft.setScreen(new LootSpawnEditorScreen(location, this))
        ).bounds(left, y, 160, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.configure_loot_triggers_c99989d6"),
                button -> this.minecraft.setScreen(new LootSpawnEditorScreen(location, this))
        ).bounds(left + 180, y, 160, 22).build());
        y += 30;

        this.addRenderableWidget(Button.builder(
                ((pvpLinked > 0) ? Component.translatable("wavedefense.auto.loot_can_react_to_round_start_end_bu_33e34812") : Component.translatable("wavedefense.auto.no_pvp_loot_triggers_configured_yet_9dd0dc34")),
                button -> {}
        ).bounds(left, y, w, 16).build()).active = false;
    }

    // ─── Дії ────────────────────────────────────────────────────────────
    private void initCommonLocationTab(int cx, int y) {
        int left = cx - 165;
        int w = 330;

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.common_location_settings_7f12dccd"), b -> {})
                .bounds(left, y, w, 14).build()).active = false;
        y += 18;

        this.addRenderableWidget(Button.builder(
                ((location.isHiddenFromPlayers()) ? Component.translatable("wavedefense.auto.hidden_from_player_menu_3ab7450a") : Component.translatable("wavedefense.auto.visible_in_player_menu_d411add4")),
                b -> { location.setHiddenFromPlayers(!location.isHiddenFromPlayers()); rebuildWidgets(); }
        ).bounds(left, y, 160, 18).build());
        this.addRenderableWidget(Button.builder(
                ((location.isKeepInventory()) ? Component.translatable("wavedefense.auto.keep_player_inventory_25c1373b") : Component.translatable("wavedefense.auto.clear_inventory_on_entry_d1b25723")),
                b -> { location.setKeepInventory(!location.isKeepInventory()); rebuildWidgets(); }
        ).bounds(left + 170, y, 160, 18).build());
        y += 24;

        this.addRenderableWidget(Button.builder(
                Component.literal("Starting items: " + location.getStartingItems().size()),
                b -> this.minecraft.setScreen(new StartingItemsScreen(this, location))
        ).bounds(left, y, 160, 18).build());
        this.addRenderableWidget(Button.builder(
                ((location.isVictoryScreenEnabled()) ? Component.translatable("wavedefense.auto.victory_screen_enabled_860241d7") : Component.translatable("wavedefense.auto.victory_screen_disabled_652ba9d0")),
                b -> { location.setVictoryScreenEnabled(!location.isVictoryScreenEnabled()); rebuildWidgets(); }
        ).bounds(left + 170, y, 160, 18).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.victory_linger_sec_df6be8e1"), b -> {})
                .bounds(left, y, 130, 18).build()).active = false;
        victoryLingerInput = new EditBox(this.font, left + 132, y, 50, 18, Component.literal("30"));
        victoryLingerInput.setValue(String.valueOf(location.getVictoryLingerTimeSec()));
        victoryLingerInput.setMaxLength(4);
        this.addRenderableWidget(victoryLingerInput);
        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.re_entry_cooldown_0ef9ee46"), b -> {})
                .bounds(left + 190, y, 105, 18).build()).active = false;
        reEntryCooldownInput = new EditBox(this.font, left + 300, y, 45, 18, Component.literal("0"));
        reEntryCooldownInput.setValue(String.valueOf(location.getReEntryCooldownSec()));
        reEntryCooldownInput.setMaxLength(5);
        this.addRenderableWidget(reEntryCooldownInput);
        y += 30;

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.boundary_b99c3080"), b -> {})
                .bounds(left, y, w, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
                ((location.isLocationBoundaryEnabled()) ? Component.translatable("wavedefense.auto.boundary_enabled_fc346b0f") : Component.translatable("wavedefense.auto.boundary_disabled_cf23e383")),
                b -> { location.setLocationBoundaryEnabled(!location.isLocationBoundaryEnabled()); rebuildWidgets(); }
        ).bounds(left, y, 160, 18).build());
        if (location.isLocationBoundaryEnabled()) {
            this.addRenderableWidget(Button.builder(Component.literal("§7Radius:"), b -> {})
                    .bounds(left + 170, y, 55, 18).build()).active = false;
            boundaryRadiusInput = new EditBox(this.font, left + 226, y, 50, 18, Component.literal("50"));
            boundaryRadiusInput.setValue(String.valueOf(location.getLocationBoundaryRadius()));
            boundaryRadiusInput.setMaxLength(5);
            this.addRenderableWidget(boundaryRadiusInput);
            y += 24;

            Location.BoundaryConsequence[] values = Location.BoundaryConsequence.values();
            String[] labels = {"Timer", "Damage", "TP Back", "Instant"};
            for (int i = 0; i < values.length; i++) {
                Location.BoundaryConsequence consequence = values[i];
                boolean selected = location.getBoundaryConsequence() == consequence;
                int bw = 78;
                this.addRenderableWidget(Button.builder(
                        Component.literal((selected ? "§a" : "§7") + labels[i]),
                        b -> { location.setBoundaryConsequence(consequence); rebuildWidgets(); }
                ).bounds(left + i * (bw + 4), y, bw, 18).build());
            }
            y += 24;

            if (location.getBoundaryConsequence() == Location.BoundaryConsequence.TIMER_SURRENDER) {
                this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.leave_timer_sec_383d03ea"), b -> {})
                        .bounds(left, y, 120, 18).build()).active = false;
                leaveTimerInput = new EditBox(this.font, left + 122, y, 50, 18, Component.literal("30"));
                leaveTimerInput.setValue(String.valueOf(location.getLocationLeaveTimerSec()));
                leaveTimerInput.setMaxLength(5);
                this.addRenderableWidget(leaveTimerInput);
                y += 24;
            } else if (location.getBoundaryConsequence() == Location.BoundaryConsequence.DAMAGE) {
                this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.damage_per_sec_8d7daba9"), b -> {})
                        .bounds(left, y, 120, 18).build()).active = false;
                boundaryDamageInput = new EditBox(this.font, left + 122, y, 50, 18, Component.literal("2.0"));
                boundaryDamageInput.setValue(String.format("%.1f", location.getBoundaryDamagePerSec()));
                boundaryDamageInput.setMaxLength(5);
                // Persist immediately so value survives rebuildWidgets() on consequence-type switch
                boundaryDamageInput.setResponder(s -> {
                    try { location.setBoundaryDamagePerSec(Math.max(0f, Float.parseFloat(s.trim()))); }
                    catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(boundaryDamageInput);
                y += 24;
            }
        } else {
            y += 24;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.portal_bf89eb68"), b -> {})
                .bounds(left, y, w, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
                ((location.isPortalEnabled()) ? Component.translatable("wavedefense.auto.portal_enabled_4d725cd5") : Component.translatable("wavedefense.auto.portal_disabled_4ecd4cd7")),
                b -> { location.setPortalEnabled(!location.isPortalEnabled()); rebuildWidgets(); }
        ).bounds(left, y, 160, 18).build());
        if (location.isPortalEnabled()) {
            this.addRenderableWidget(Button.builder(
                    ((location.isPortalDisappearsOnComplete()) ? Component.translatable("wavedefense.auto.disappears_on_complete_6b703c5d") : Component.translatable("wavedefense.auto.stays_after_complete_a05a7155")),
                    b -> { location.setPortalDisappearsOnComplete(!location.isPortalDisappearsOnComplete()); rebuildWidgets(); }
            ).bounds(left + 170, y, 160, 18).build());
            y += 24;

            this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.penalty_timer_295ad456"), b -> {})
                    .bounds(left, y, 105, 18).build()).active = false;
            portalPenaltyTimerInput = new EditBox(this.font, left + 108, y, 50, 18, Component.literal("60"));
            portalPenaltyTimerInput.setValue(String.valueOf(location.getPortalPenaltyTimerSec()));
            portalPenaltyTimerInput.setMaxLength(5);
            this.addRenderableWidget(portalPenaltyTimerInput);
            this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.open_after_start_4440bff2"), b -> {})
                    .bounds(left + 166, y, 120, 18).build()).active = false;
            portalOpenAfterStartInput = new EditBox(this.font, left + 290, y, 50, 18, Component.literal("-1"));
            portalOpenAfterStartInput.setValue(String.valueOf(location.getPortalOpenAfterStartSec()));
            portalOpenAfterStartInput.setMaxLength(5);
            this.addRenderableWidget(portalOpenAfterStartInput);
            y += 24;

            if (location.isPortalDisappearsOnComplete()) {
                this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.respawn_timer_ac455eca"), b -> {})
                        .bounds(left, y, 105, 18).build()).active = false;
                portalRespawnTimerInput = new EditBox(this.font, left + 108, y, 50, 18, Component.literal("300"));
                portalRespawnTimerInput.setValue(String.valueOf(location.getPortalRespawnTimerSec()));
                portalRespawnTimerInput.setMaxLength(5);
                this.addRenderableWidget(portalRespawnTimerInput);
                y += 24;
            }
        } else {
            y += 24;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.info_panels_be683025"), b -> {})
                .bounds(left, y, w, 14).build()).active = false;
        y += 18;
        this.addRenderableWidget(Button.builder(
                ((location.getInfoPanel().isSpawnPanelEnabled()) ? Component.translatable("wavedefense.auto.spawn_info_panel_b52064cf") : Component.translatable("wavedefense.auto.spawn_info_panel_46806a47")),
                b -> { location.getInfoPanel().setSpawnPanelEnabled(!location.getInfoPanel().isSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(left, y, 160, 18).build());
        this.addRenderableWidget(Button.builder(
                ((location.getInfoPanel().isMobSpawnPanelEnabled()) ? Component.translatable("wavedefense.auto.mob_info_panels_844b436b") : Component.translatable("wavedefense.auto.mob_info_panels_b81dacec")),
                b -> { location.getInfoPanel().setMobSpawnPanelEnabled(!location.getInfoPanel().isMobSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(left + 170, y, 160, 18).build());
        y += 26;

        this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.apply_common_settings_40b70d97"), b -> saveSharedSettings())
                .bounds(left + 55, y, 220, 22).build());
        y += 28;

        rulesContentHeight = y + rulesScrollOffset - 52;
        int maxScroll = getRulesMaxScroll();
        if (rulesScrollOffset > maxScroll) {
            rulesScrollOffset = maxScroll;
            rebuildWidgets();
        }
    }

    private void startAddSpawn() {
        editingSpawn = true;
        editingSpawnIndex = -1;
        rebuildWidgets();
    }

    private void startEditSpawn(int idx) {
        editingSpawn = true;
        editingSpawnIndex = idx;
        rebuildWidgets();
    }

    private void saveSpawn() {
        String name = spawnNameInput != null ? spawnNameInput.getValue().trim() : "";
        if (name.isEmpty()) return;

        net.minecraft.core.BlockPos pos = resolveSpawnPos();
        if (pos == null) return;

        int radius = 0;
        try { if (spawnRadiusInput != null) radius = Math.max(0, Integer.parseInt(spawnRadiusInput.getValue().trim())); }
        catch (NumberFormatException ignored) {}

        if (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size()) {
            location.getPvpSpawnPoints().get(editingSpawnIndex).setTeamName(name);
            location.getPvpSpawnPoints().get(editingSpawnIndex).setPos(pos);
            location.getPvpSpawnPoints().get(editingSpawnIndex).setSpawnRadius(radius);
        } else {
            PvpSpawnPoint sp = new PvpSpawnPoint(name, pos);
            sp.setSpawnRadius(radius);
            location.addPvpSpawnPoint(sp);
        }
        editingSpawn = false;
        rebuildWidgets();
    }

    private net.minecraft.core.BlockPos resolveSpawnPos() {
        try {
            if (spawnCoordField == null) return minecraft.player != null ? minecraft.player.blockPosition() : null;
            if (spawnCoordField.isEmpty()) return minecraft.player != null ? minecraft.player.blockPosition() : null;
            net.minecraft.core.BlockPos pos = spawnCoordField.getValue();
            if (pos == null && minecraft.player != null)
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.auto.невірний_формат_координат_607bcb51"), true);
            return pos;
        } catch (Exception e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.auto.невірний_формат_координат_607bcb51"), true);
            return null;
        }
    }

    private void saveSharedSettings() {
        try {
            if (boundaryRadiusInput != null)
                location.setLocationBoundaryRadius(Integer.parseInt(boundaryRadiusInput.getValue().trim()));
            if (leaveTimerInput != null)
                location.setLocationLeaveTimerSec(Integer.parseInt(leaveTimerInput.getValue().trim()));
            if (boundaryDamageInput != null)
                location.setBoundaryDamagePerSec(Float.parseFloat(boundaryDamageInput.getValue().trim()));
            if (portalPenaltyTimerInput != null)
                location.setPortalPenaltyTimerSec(Integer.parseInt(portalPenaltyTimerInput.getValue().trim()));
            if (portalRespawnTimerInput != null)
                location.setPortalRespawnTimerSec(Integer.parseInt(portalRespawnTimerInput.getValue().trim()));
            if (portalOpenAfterStartInput != null)
                location.setPortalOpenAfterStartSec(Integer.parseInt(portalOpenAfterStartInput.getValue().trim()));
            if (victoryLingerInput != null)
                location.setVictoryLingerTimeSec(Integer.parseInt(victoryLingerInput.getValue().trim()));
            if (reEntryCooldownInput != null)
                location.setReEntryCooldownSec(Integer.parseInt(reEntryCooldownInput.getValue().trim()));
        } catch (NumberFormatException ignored) {}
    }

    private void saveChanges() {
        saveAllRules();
        saveSharedSettings();
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.pvp_локацію_збережено_e519ca75"), true);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int listTop = 48, listBot = this.height - 32;
        // Спочатку перевіряємо статичні віджети (вкладки, кнопки збереження)
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && staticWidgets.contains(w)) {
                if (child.mouseClicked(mx, my, button)) {
                    this.setFocused(child);
                    if (button == 0) this.setDragging(true);
                    return true;
                }
            }
        }
        // Потім перевіряємо контентні віджети — тільки якщо вони у видимій clip-зоні
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && !staticWidgets.contains(w)) {
                int wy = w.getY(), wh = w.getHeight();
                if (wy + wh <= listTop || wy >= listBot) continue; // поза scissor — пропускаємо
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == 1 || currentTab == 4) { // scrollable tabs
            int listTop = 48, listBot = this.height - 32;
            if (mouseY < listTop || mouseY > listBot) return super.mouseScrolled(mouseX, mouseY, delta);
            int step = 18;
            if (delta > 0) rulesScrollOffset = Math.max(0, rulesScrollOffset - step);
            else           rulesScrollOffset = Math.min(getRulesMaxScroll(), rulesScrollOffset + step);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getRulesMaxScroll() {
        int visibleHeight = Math.max(1, this.height - 32 - 48);
        return Math.max(0, rulesContentHeight - visibleHeight);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFF5555);
        // Scissor: вміст вкладки між таблою (48) і нижніми кнопками (height-32)
        int listTop = 48, listBot = this.height - 32;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && !staticWidgets.contains(w)
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                w.render(g, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Static: вкладки (top) та кнопки Зберегти/Назад (bottom) — поверх контенту
        ScissorHelper.enable(0, 0, this.width, listTop);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && staticWidgets.contains(w) && w.getY() < listTop)
                w.render(g, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        ScissorHelper.enable(0, listBot, this.width, this.height - listBot);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && staticWidgets.contains(w) && w.getY() >= listBot)
                w.render(g, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Tooltips
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn
                    && btn.isHoveredOrFocused() && btn.active) {
                String t = getTip(btn.getMessage().getString());
                if (t != null) TooltipHelper.renderIfEnabled(g, this.font, t, mouseX, mouseY);
            }
        });
    }

    private String getTip(String label) {
        if (label.contains("Дружній вогонь") || label.contains("Friendly Fire")) return TooltipHelper.PVP_FF;
        if (label.contains("раундів"))            return TooltipHelper.PVP_ROUNDS;
        if (label.contains("покупок"))            return TooltipHelper.PVP_BUY_TIME;
        if (label.contains("Радіус розкиду"))     return TooltipHelper.PVP_SPAWN_RADIUS;
        if (label.contains("Ефекти очікування") || label.contains("спостерігача при очікуванні")) return TooltipHelper.PVP_WAIT_EFFECT;
        if (label.contains("Автобаланс"))         return TooltipHelper.PVP_AUTO_BALANCE;
        if (label.contains("Затримка старту"))    return TooltipHelper.PVP_ROUND_DELAY;
        if (label.contains("переможцю раунду"))   return TooltipHelper.PVP_WIN_POINTS;
        if (label.contains("програла раунд"))     return TooltipHelper.PVP_LOSE_POINTS;
        if (label.contains("початок раунду"))     return TooltipHelper.PVP_ROUND_POINTS;
        if (label.contains("Примусовий режим"))   return TooltipHelper.ENFORCE_GAMEMODE;
        if (label.contains("вбивств для перемоги")) return TooltipHelper.DM_KILLS_TO_WIN;
        if (label.contains("радіус кордону"))     return TooltipHelper.BR_BORDER_RADIUS;
        if (label.contains("Звуження на"))        return TooltipHelper.BR_SHRINK;
        if (label.contains("Частинки кордону"))   return TooltipHelper.BR_PARTICLE;
        if (label.contains("Шкода при виході"))   return TooltipHelper.BR_DAMAGE;
        if (label.contains("ROYALE") || label.contains("Битва")) return TooltipHelper.BR_RANDOM_SPAWN;
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
