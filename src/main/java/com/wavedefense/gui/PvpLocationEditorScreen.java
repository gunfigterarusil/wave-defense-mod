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
    private EditBox spawnXInput, spawnYInput, spawnZInput;
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
    private EditBox brBorderParticleCountInput;
    private EditBox brBorderDamageAmtInput;
    private EditBox roundStartPointsInput;
    private EditBox winPointsInput;
    private EditBox losePointsInput;

    private int scrollOffset = 0;
    private int rulesScrollOffset = 0;
    private static final int PER_PAGE = 6;
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> staticWidgets
        = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addStatic(T w) {
        this.addRenderableWidget(w); staticWidgets.add(w); return w;
    }

    public PvpLocationEditorScreen(Location location, Screen parent) {
        super(Component.literal("§c⚔ PvP Локація: §f" + location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // Вкладки — 4 по 80px, відцентровані
        int tabW = 80, tabGap = 3;
        int totalTabW = 4 * tabW + 3 * tabGap;
        int tabX = cx - totalTabW / 2;

        staticWidgets.clear();
        String[] tabs = {"⚑ Команди", "🎮 Режим+Правила", "🛒 Магазин", "📦 Лут"};
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            addStatic(Button.builder(
                    Component.literal(currentTab == i ? "§a§l● " + tabs[i] : "§7○ " + tabs[i]),
                    button -> { currentTab = ti; scrollOffset = 0; rulesScrollOffset = 0; rebuildWidgets(); }
            ).bounds(tabX + i * (tabW + tabGap), 25, tabW, 20).build());
        }

        int startY = 52;

        switch (currentTab) {
            case 0 -> initTeamsTab(cx, startY);
            case 1 -> initModeAndRulesTab(cx, startY - rulesScrollOffset);
            case 2 -> initShopTab(cx, startY);
            case 3 -> initLootTab(cx, startY);
        }

        // Нижня панель
        addStatic(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 28, 100, 20).build());

        addStatic(Button.builder(
                Component.literal("Назад"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    // ─── Вкладка: Команди ──────────────────────────────────────────────
    private void initTeamsTab(int cx, int y) {
        if (editingSpawn) {
            initEditSpawn(cx, y);
            return;
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§e➕ Додати точку спавну команди (на позиції гравця)"),
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
                    Component.literal(String.format("§e%s §7X%d Y%d Z%d", sp.getTeamName(), pos.getX(), pos.getY(), pos.getZ()) + radiusStr),
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
                Component.literal("§7Назва команди:"), button -> {}
        ).bounds(cx - 155, y, 120, 16).build()).active = false;
        spawnNameInput = new EditBox(this.font, cx - 155, y + 18, 200, 20, Component.literal("Назва"));
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
        String sx = existingPos != null ? String.valueOf(existingPos.getX()) : "";
        String sy = existingPos != null ? String.valueOf(existingPos.getY()) : "";
        String sz = existingPos != null ? String.valueOf(existingPos.getZ()) : "";

        this.addRenderableWidget(Button.builder(Component.literal("§7Координати (порожньо = ваша позиція):"), b -> {}).bounds(cx - 155, y, 280, 14).build()).active = false;
        y += 16;

        int fw = 52;
        this.addRenderableWidget(Button.builder(Component.literal("§7X:"), b -> {}).bounds(cx - 155, y, 14, 16).build()).active = false;
        spawnXInput = new EditBox(this.font, cx - 139, y, fw, 16, Component.literal("X"));
        spawnXInput.setValue(sx); spawnXInput.setMaxLength(8); this.addRenderableWidget(spawnXInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Y:"), b -> {}).bounds(cx - 82, y, 14, 16).build()).active = false;
        spawnYInput = new EditBox(this.font, cx - 66, y, fw, 16, Component.literal("Y"));
        spawnYInput.setValue(sy); spawnYInput.setMaxLength(8); this.addRenderableWidget(spawnYInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Z:"), b -> {}).bounds(cx - 8, y, 14, 16).build()).active = false;
        spawnZInput = new EditBox(this.font, cx + 8, y, fw, 16, Component.literal("Z"));
        spawnZInput.setValue(sz); spawnZInput.setMaxLength(8); this.addRenderableWidget(spawnZInput);

        this.addRenderableWidget(Button.builder(Component.literal("📌"),
                b -> {
                    if (minecraft.player != null) {
                        net.minecraft.core.BlockPos pp = minecraft.player.blockPosition();
                        if (spawnXInput != null) spawnXInput.setValue(String.valueOf(pp.getX()));
                        if (spawnYInput != null) spawnYInput.setValue(String.valueOf(pp.getY()));
                        if (spawnZInput != null) spawnZInput.setValue(String.valueOf(pp.getZ()));
                    }
                }
        ).bounds(cx + 64, y, 30, 16).build());

        y += 24;
        // Радіус розкиду
        this.addRenderableWidget(Button.builder(Component.literal("§7Радіус розкиду гравців (0 = точно в блоці):"), b -> {}).bounds(cx - 155, y, 246, 16).build()).active = false;
        int curRadius = (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size())
            ? location.getPvpSpawnPoints().get(editingSpawnIndex).getSpawnRadius() : 0;
        spawnRadiusInput = new EditBox(this.font, cx + 94, y, 50, 16, Component.literal("0"));
        spawnRadiusInput.setValue(String.valueOf(curRadius));
        spawnRadiusInput.setMaxLength(3);
        this.addRenderableWidget(spawnRadiusInput);

        y += 28;
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveSpawn()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), button -> { editingSpawn = false; rebuildWidgets(); }
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
            Component.literal("§6§l── Підрежим PvP ──"), b -> {}
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

        // ── Загальні правила ───────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§e§l── Загальні налаштування ──"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 18;

        // Мін. гравців + Friendly Fire (рядок 1)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Мін. гравців:"), b -> {}
        ).bounds(cx - 160, y, 100, 18).build()).active = false;
        minPlayersInput = new EditBox(this.font, cx - 55, y, 45, 18, Component.literal("2"));
        minPlayersInput.setValue(String.valueOf(location.getPvpMinPlayers()));
        minPlayersInput.setMaxLength(4);
        this.addRenderableWidget(minPlayersInput);
        this.addRenderableWidget(Button.builder(
            Component.literal(location.isPvpFriendlyFire() ? "§a☑ Friendly Fire" : "§7☐ Friendly Fire"),
            b -> { location.setPvpFriendlyFire(!location.isPvpFriendlyFire()); rebuildWidgets(); }
        ).bounds(cx, y, 160, 18).build());
        y += 24;

        // Enforce GameMode + WaitEffect (рядок 2)
        this.addRenderableWidget(Button.builder(
            Component.literal(location.isEnforceGameMode() ? "§a☑ Прим. режим гри" : "§7☐ Прим. режим гри"),
            b -> { location.setEnforceGameMode(!location.isEnforceGameMode()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 155, 18).build());
        this.addRenderableWidget(Button.builder(
            Component.literal(location.isPvpWaitEffect() ? "§a☑ Ефекти очікування" : "§7☐ Ефекти очікування"),
            b -> { location.setPvpWaitEffect(!location.isPvpWaitEffect()); rebuildWidgets(); }
        ).bounds(cx, y, 160, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7Slowness+Blindness замість spectator при очікуванні")));
        y += 24;

        // Автобаланс (рядок 3)
        this.addRenderableWidget(Button.builder(
            Component.literal(location.isPvpTeamAutoBalance() ? "§a☑ Автобаланс команд" : "§7☐ Автобаланс команд"),
            b -> { location.setPvpTeamAutoBalance(!location.isPvpTeamAutoBalance()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build());
        y += 24;

        // Стартові поінти + Поінти за вбивство (рядок 4)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Стартові поінти:"), b -> {}
        ).bounds(cx - 160, y, 120, 18).build()).active = false;
        pvpStartingPointsInput = new EditBox(this.font, cx - 36, y, 50, 18, Component.literal("0"));
        pvpStartingPointsInput.setValue(String.valueOf(location.getStartingPoints()));
        pvpStartingPointsInput.setMaxLength(6);
        this.addRenderableWidget(pvpStartingPointsInput);
        this.addRenderableWidget(Button.builder(
            Component.literal("§7+поінтів за вбивство:"), b -> {}
        ).bounds(cx + 20, y, 140, 18).build()).active = false;
        killPointsInput = new EditBox(this.font, cx + 165, y, 50, 18, Component.literal("100"));
        killPointsInput.setValue(String.valueOf(location.getPvpKillPoints()));
        killPointsInput.setMaxLength(6);
        this.addRenderableWidget(killPointsInput);
        y += 24;

        // Штраф за смерть (рядок 5)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7-поінтів за смерть:"), b -> {}
        ).bounds(cx - 160, y, 140, 18).build()).active = false;
        deathPenaltyInput = new EditBox(this.font, cx - 14, y, 50, 18, Component.literal("50"));
        deathPenaltyInput.setValue(String.valueOf(location.getPvpDeathPenalty()));
        deathPenaltyInput.setMaxLength(6);
        this.addRenderableWidget(deathPenaltyInput);
        y += 28;

        // ── Налаштування конкретного режиму ───────────────────────────
        if (mode == com.wavedefense.data.Location.PvpMode.STANDARD) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§b§l── Стандарт: раунди ──"), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
            y += 18;

            // К-сть раундів + Час покупок
            this.addRenderableWidget(Button.builder(
                Component.literal("§7К-сть раундів:"), b -> {}
            ).bounds(cx - 160, y, 115, 18).build()).active = false;
            totalRoundsInput = new EditBox(this.font, cx - 40, y, 50, 18, Component.literal("10"));
            totalRoundsInput.setValue(String.valueOf(location.getPvpTotalRounds()));
            totalRoundsInput.setMaxLength(4);
            this.addRenderableWidget(totalRoundsInput);
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Час покупок (сек):"), b -> {}
            ).bounds(cx + 16, y, 135, 18).build()).active = false;
            buyTimeInput = new EditBox(this.font, cx + 155, y, 50, 18, Component.literal("20"));
            buyTimeInput.setValue(String.valueOf(location.getPvpBuyTime()));
            buyTimeInput.setMaxLength(4);
            this.addRenderableWidget(buyTimeInput);
            y += 24;

            // Затримка старту
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Затримка старту раунду (сек):"), b -> {}
            ).bounds(cx - 160, y, 200, 18).build()).active = false;
            roundStartDelayInput = new EditBox(this.font, cx + 46, y, 50, 18, Component.literal("5"));
            roundStartDelayInput.setValue(String.valueOf(location.getPvpRoundStartDelay()));
            roundStartDelayInput.setMaxLength(3);
            this.addRenderableWidget(roundStartDelayInput);
            y += 24;

            // Поінти на початок раунду + за перемогу
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Поінти на початок раунду:"), b -> {}
            ).bounds(cx - 160, y, 180, 18).build()).active = false;
            roundStartPointsInput = new EditBox(this.font, cx + 26, y, 50, 18, Component.literal("0"));
            roundStartPointsInput.setValue(String.valueOf(location.getPvpRoundStartPoints()));
            roundStartPointsInput.setMaxLength(6);
            this.addRenderableWidget(roundStartPointsInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7+поінтів переможцю раунду:"), b -> {}
            ).bounds(cx - 160, y, 190, 18).build()).active = false;
            winPointsInput = new EditBox(this.font, cx + 36, y, 50, 18, Component.literal("0"));
            winPointsInput.setValue(String.valueOf(location.getPvpWinPoints()));
            winPointsInput.setMaxLength(6);
            this.addRenderableWidget(winPointsInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7+поінтів команді що програла:"), b -> {}
            ).bounds(cx - 160, y, 200, 18).build()).active = false;
            losePointsInput = new EditBox(this.font, cx + 46, y, 50, 18, Component.literal("0"));
            losePointsInput.setValue(String.valueOf(location.getPvpLosePoints()));
            losePointsInput.setMaxLength(6);
            this.addRenderableWidget(losePointsInput);
            y += 24;

        } else if (mode == com.wavedefense.data.Location.PvpMode.DEATHMATCH) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§e§l── Deathmatch ──"), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
            y += 18;

            // К-сть раундів + Вбивств до перемоги
            this.addRenderableWidget(Button.builder(
                Component.literal("§7К-сть раундів:"), b -> {}
            ).bounds(cx - 160, y, 115, 18).build()).active = false;
            totalRoundsInput = new EditBox(this.font, cx - 40, y, 50, 18, Component.literal("1"));
            totalRoundsInput.setValue(String.valueOf(location.getPvpTotalRounds()));
            totalRoundsInput.setMaxLength(4);
            this.addRenderableWidget(totalRoundsInput);
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Вбивств для перемоги:"), b -> {}
            ).bounds(cx + 16, y, 150, 18).build()).active = false;
            dmKillsToWinInput = new EditBox(this.font, cx + 170, y, 50, 18, Component.literal("10"));
            dmKillsToWinInput.setValue(String.valueOf(location.getDmKillsToWin()));
            dmKillsToWinInput.setMaxLength(4);
            this.addRenderableWidget(dmKillsToWinInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7Час покупок між раундами (сек):"), b -> {}
            ).bounds(cx - 160, y, 210, 18).build()).active = false;
            buyTimeInput = new EditBox(this.font, cx + 55, y, 50, 18, Component.literal("20"));
            buyTimeInput.setValue(String.valueOf(location.getPvpBuyTime()));
            buyTimeInput.setMaxLength(4);
            this.addRenderableWidget(buyTimeInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ Гравці відроджуються миттєво на точці команди"), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
            y += 18;

        } else if (mode == com.wavedefense.data.Location.PvpMode.BATTLE_ROYALE) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c§l── Королівська Битва ──"), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
            y += 18;

            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ Гравців кидає на ВИПАДКОВУ точку. Кордон звужується."), b -> {}
            ).bounds(cx - 160, y, 320, 14).build()).active = false;
            y += 18;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7Початковий радіус кордону (бл):"), b -> {}
            ).bounds(cx - 160, y, 210, 18).build()).active = false;
            brBorderRadiusInput = new EditBox(this.font, cx + 55, y, 60, 18, Component.literal("100"));
            brBorderRadiusInput.setValue(String.valueOf(location.getBrBorderRadius()));
            brBorderRadiusInput.setMaxLength(5);
            this.addRenderableWidget(brBorderRadiusInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7Звуження 1 бл кожні (сек):"), b -> {}
            ).bounds(cx - 160, y, 210, 18).build()).active = false;
            brShrinkIntervalInput = new EditBox(this.font, cx + 55, y, 60, 18, Component.literal("30"));
            brShrinkIntervalInput.setValue(String.valueOf(location.getBrShrinkIntervalSec()));
            brShrinkIntervalInput.setMaxLength(5);
            this.addRenderableWidget(brShrinkIntervalInput);
            y += 24;

            this.addRenderableWidget(Button.builder(
                Component.literal("§7Частинки кордону (id):"), b -> {}
            ).bounds(cx - 160, y, 155, 18).build()).active = false;
            brBorderParticleInput = new EditBox(this.font, cx, y, 160, 18, Component.literal("minecraft:flame"));
            brBorderParticleInput.setValue(location.getBrBorderParticle());
            brBorderParticleInput.setMaxLength(64);
            this.addRenderableWidget(brBorderParticleInput);
            y += 24;

            boolean dmg = location.isBrBorderDamage();
            this.addRenderableWidget(Button.builder(
                Component.literal(dmg ? "§a☑ Шкода поза зоною" : "§7☐ Без шкоди"),
                b -> { location.setBrBorderDamage(!location.isBrBorderDamage()); rebuildWidgets(); }
            ).bounds(cx - 160, y, 150, 18).build());
            if (dmg) {
                brBorderDamageAmtInput = new EditBox(this.font, cx, y, 55, 18, Component.literal("1.0"));
                brBorderDamageAmtInput.setValue(String.format("%.1f", location.getBrBorderDamageAmt()));
                brBorderDamageAmtInput.setMaxLength(5);
                this.addRenderableWidget(brBorderDamageAmtInput);
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7HP/сек"), b -> {}
                ).bounds(cx + 58, y, 50, 18).build()).active = false;
            }
            y += 24;
        }

        y += 4;
        // ── Зберегти ──────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓ Застосувати правила"), b -> saveAllRules()
        ).bounds(cx - 110, y, 220, 22).build());
        y += 28;

        int teamCount = location.getPvpSpawnPoints().size();
        String info = teamCount < 2
            ? "§c⚠ Потрібно ≥2 точки спавну (вкладка Команди)"
            : String.format("§a✓ %d команд | мін. гравців: %d", teamCount, location.getPvpMinPlayers());
        this.addRenderableWidget(Button.builder(
            Component.literal(info), b -> {}
        ).bounds(cx - 160, y, 320, 16).build()).active = false;
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
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("§a✓ Правила збережено!"), true);
    }


    // ─── Вкладка: Магазин ──────────────────────────────────────────────
    private void initShopTab(int cx, int y) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6Товарів у магазині: §e%d", location.getShopItems().size())),
                button -> {}
        ).bounds(cx - 150, y, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("🛒 Редагувати магазин"),
                button -> this.minecraft.setScreen(new ShopEditorScreen(location, this))
        ).bounds(cx - 100, y + 28, 200, 24).build());
    }

    // ─── Вкладка: Лут ──────────────────────────────────────────────────
    private void initLootTab(int cx, int y) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Точок луту: §e%d", location.getLootSpawns().size())),
                button -> {}
        ).bounds(cx - 150, y, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("📦 Редагувати точки луту"),
                button -> this.minecraft.setScreen(new LootSpawnEditorScreen(location, this))
        ).bounds(cx - 100, y + 28, 200, 24).build());
    }

    // ─── Дії ────────────────────────────────────────────────────────────
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
            String sx = spawnXInput != null ? spawnXInput.getValue().trim() : "";
            String sy = spawnYInput != null ? spawnYInput.getValue().trim() : "";
            String sz = spawnZInput != null ? spawnZInput.getValue().trim() : "";
            // Якщо всі порожні — беремо поточну позицію гравця
            if (sx.isEmpty() && sy.isEmpty() && sz.isEmpty()) {
                if (minecraft.player == null) return null;
                return minecraft.player.blockPosition();
            }
            if (minecraft.player == null) return null;
            net.minecraft.core.BlockPos cur = minecraft.player.blockPosition();
            int x = sx.isEmpty() ? cur.getX() : Integer.parseInt(sx);
            int y = sy.isEmpty() ? cur.getY() : Integer.parseInt(sy);
            int z = sz.isEmpty() ? cur.getZ() : Integer.parseInt(sz);
            return new net.minecraft.core.BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cНевірний формат координат!"), true);
            return null;
        }
    }

    private void saveChanges() {
        saveAllRules();
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("§a✓ PvP локацію збережено!"), true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == 1) { // Mode+Rules tab (scrollable)
            int step = 18;
            if (delta > 0) rulesScrollOffset = Math.max(0, rulesScrollOffset - step);
            else           rulesScrollOffset = Math.min(320, rulesScrollOffset + step);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
