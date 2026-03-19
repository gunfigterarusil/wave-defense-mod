package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.InfoPanelSettings;
import com.wavedefense.data.LocationMode;
import com.wavedefense.data.ShopPoint;
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
    private EditBox spawnXInput, spawnYInput, spawnZInput;
    // Поле стартових поінтів
    private EditBox startingPointsInput;
    // Boundary inputs
    private EditBox boundaryRadiusInput;
    private EditBox leaveTimerInput;
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
        boolean isPve = !location.isPvp();

        addStatic(Button.builder(
                Component.literal(isPve ? "§2§l⬤ PvE §7(Мобів хвилі)" : "§7○ PvE"),
                button -> { location.setMode(LocationMode.PVE); rebuildWidgets(); }
        ).bounds(cx - 105, 25, 100, 20).build());

        addStatic(Button.builder(
                Component.literal(!isPve ? "§c§l⬤ PvP §7(Гравці vs Гравці)" : "§7○ PvP"),
                button -> { location.setMode(LocationMode.PVP); rebuildWidgets(); }
        ).bounds(cx + 5, 25, 100, 20).build());

        // ── PvP — одразу переходимо до редактора PvP (без проміжного екрану) ──
        if (!isPve) {
            // Відкладаємо перехід на наступний тік щоб уникнути рекурсії в init()
            net.minecraft.client.Minecraft.getInstance().tell(() ->
                this.minecraft.setScreen(new PvpLocationEditorScreen(location, this)));
            // Показуємо порожній екран поки не відбудеться перехід
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Завантаження PvP редактора..."), b -> {}
            ).bounds(cx - 120, 60, 240, 20).build()).active = false;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§a✓ Зберегти"), button -> saveChanges()
            ).bounds(cx - 60, this.height - 28, 120, 20).build());
            return;
        }

        // ── PvE: вкладки (5 шт) ────────────────────────────────────────────
        int tabW = 68, tabGap = 3;
        int totalTabW = 5 * tabW + 4 * tabGap;
        int tabStartX = cx - totalTabW / 2;

        String[] tabNames = {"Основні","Хвилі","Магазин","Лут","⚙ Спец"};
        for (int ti = 0; ti < tabNames.length; ti++) {
            final int fti = ti;
            addStatic(Button.builder(
                Component.literal(currentTab == ti ? "§a§l⬤ " + tabNames[ti] : "§7○ " + tabNames[ti]),
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
                Component.literal("§a✓ Зберегти зміни"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 30, 130, 20).build());
        addStatic(Button.builder(
                Component.literal("Назад до списку"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 20, this.height - 30, 130, 20).build());
        addStatic(Button.builder(
                Component.literal("Закрити"), button -> this.onClose()
        ).bounds(cx + 120, this.height - 30, 40, 20).build());
    }

    private void initBasicTab(int cx, int startY) {
        startY -= basicScrollOffset; // apply scroll
        // ── Точка спавну гравця — поля вводу координат ──────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("§7📍 Точка спавну гравця:"), button -> {}
        ).bounds(cx - 150, startY, 180, 16).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("📌 Моя позиція"),
                button -> setPlayerSpawn()
        ).bounds(cx + 35, startY, 115, 16).build());

        BlockPos sp = location.getPlayerSpawn();
        String spawnX = sp != null ? String.valueOf(sp.getX()) : "";
        String spawnY = sp != null ? String.valueOf(sp.getY()) : "";
        String spawnZ = sp != null ? String.valueOf(sp.getZ()) : "";

        int coordY = startY + 18;
        int fieldW = 55;

        this.addRenderableWidget(Button.builder(Component.literal("§7X:"), b -> {}).bounds(cx - 150, coordY, 16, 16).build()).active = false;
        spawnXInput = new EditBox(this.font, cx - 132, coordY, fieldW, 16, Component.literal("X"));
        spawnXInput.setValue(spawnX); spawnXInput.setMaxLength(7);
        this.addRenderableWidget(spawnXInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Y:"), b -> {}).bounds(cx - 72, coordY, 16, 16).build()).active = false;
        spawnYInput = new EditBox(this.font, cx - 54, coordY, fieldW, 16, Component.literal("Y"));
        spawnYInput.setValue(spawnY); spawnYInput.setMaxLength(7);
        this.addRenderableWidget(spawnYInput);

        this.addRenderableWidget(Button.builder(Component.literal("§7Z:"), b -> {}).bounds(cx + 6, coordY, 16, 16).build()).active = false;
        spawnZInput = new EditBox(this.font, cx + 24, coordY, fieldW, 16, Component.literal("Z"));
        spawnZInput.setValue(spawnZ); spawnZInput.setMaxLength(7);
        this.addRenderableWidget(spawnZInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Застосувати"),
                button -> applySpawnCoords()
        ).bounds(cx + 84, coordY, 66, 16).build());

        int mobSpawnY = startY + 50;
        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати точку спавну мобів"),
                button -> addMobSpawn()
        ).bounds(cx - 150, mobSpawnY, 200, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Налаштовано: %d/%d", location.getMobSpawns().size(), com.wavedefense.config.WaveDefenseConfig.MAX_MOB_SPAWNS.get())),
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
                    Component.literal(String.format("§7#%d: §fX:%d Y:%d Z:%d", realIndex + 1,
                        pos.getX(), pos.getY(), pos.getZ()) + radLabel),
                    button -> {}
            ).bounds(cx - 150, listY + (i * 22), 190, 20).build()).active = false;
            // Кнопка радіусу
            this.addRenderableWidget(Button.builder(
                    Component.literal("r" + (msp.getRadius() > 0 ? msp.getRadius() : "?")),
                    button -> cycleMobSpawnRadius(index)
            ).bounds(cx + 44, listY + (i * 22), 28, 20).build())
            .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7Радіус розкиду мобів\n§8(0/3/5/10/15/20)")));
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
            ).bounds(cx + 105, listY + (MOB_SPAWN_PER_PAGE - 1) * 22, 20, 20).build());
        }

        int invY = listY + MOB_SPAWN_PER_PAGE * 22 + 8;

        // Видимість в меню гравців (перенесено з Спец вкладки)
        boolean hiddenBasic = location.isHiddenFromPlayers();
        this.addRenderableWidget(Button.builder(
            Component.literal(hiddenBasic ? "§c☒ Прихована від гравців (адміни бачать)" : "§a☑ Видима в меню гравців"),
            b -> { location.setHiddenFromPlayers(!location.isHiddenFromPlayers()); saveChanges(); rebuildWidgets(); }
        ).bounds(cx - 150, invY, 300, 18).build());
        invY += 22;

        // Збереження / очищення інвентаря
        this.addRenderableWidget(Button.builder(
                Component.literal(location.isKeepInventory() ? "§a☑ Зберігати речі гравця" : "§c☐ Очищати речі при вході"),
                button -> toggleKeepInventory()
        ).bounds(cx - 150, invY, 200, 18).build());

        if (!location.isKeepInventory()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("⚙ Стартове спорядження"),
                    button -> this.minecraft.setScreen(new StartingItemsScreen(this, location))
            ).bounds(cx - 150, invY + 22, 200, 18).build());

            // Стартові поінти
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Стартові поінти:"), b -> {}
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
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Налаштування хвиль (моби, час, нагороди):"), button -> {}
        ).bounds(cx - 150, startY, 300, 18).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("⚙ Налаштувати моби та хвилі"),
                button -> this.minecraft.setScreen(new WaveConfigScreen(location, this))
        ).bounds(cx - 130, startY + 26, 260, 24).build());

        int waveCount = location.getWaves().size();
        String waveInfo = waveCount > 0
                ? String.format("§7Хвиль: §e%d §7| Час: §e%d сек", waveCount,
                    location.getWaves().isEmpty() ? 0 : location.getWaves().get(0).getTimeBetweenWaves())
                : "§cХвилі не налаштовані";
        this.addRenderableWidget(Button.builder(
                Component.literal(waveInfo), button -> {}
        ).bounds(cx - 150, startY + 58, 300, 18).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§6🏆 Нагороди за проходження локації"),
                button -> this.minecraft.setScreen(new CompletionRewardScreen(location, this))
        ).bounds(cx - 130, startY + 84, 260, 24).build());
    }

    private void initShopTab(int cx, int startY) {
        // Лічильник — залежить від режиму магазину
        int totalItems;
        if (location.isPointShopMode()) {
            totalItems = location.getShopPoints().stream()
                .mapToInt(p -> p.getItems().size()).sum();
        } else {
            totalItems = location.getShopItems().size();
        }
        String modeLabel = location.isPointShopMode()
            ? String.format("§6Точок: §e%d §6| Товарів (всього): §e%d",
                location.getShopPoints().size(), totalItems)
            : String.format("§6Товарів у магазині: §e%d", totalItems);

        this.addRenderableWidget(Button.builder(
                Component.literal(modeLabel),
                button -> {}
        ).bounds(cx - 150, startY, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("🛒 Редагувати магазин"),
                button -> this.minecraft.setScreen(new ShopEditorScreen(location, this))
        ).bounds(cx - 100, startY + 30, 200, 25).build());
    }

    private void initLootTab(int cx, int startY) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Точок луту: §e%d", location.getLootSpawns().size())),
                button -> {}
        ).bounds(cx - 150, startY, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("📦 Редагувати точки луту"),
                button -> this.minecraft.setScreen(new LootSpawnEditorScreen(location, this))
        ).bounds(cx - 100, startY + 30, 200, 25).build());
    }

    private void switchTab(int tab) {
        // Зберігаємо поточні значення EditBox перед перемиканням вкладки
        flushCurrentTabInputs();
        this.currentTab = tab;
        this.basicScrollOffset = 0; // скидаємо скрол при зміні вкладки
        this.rebuildWidgets();
    }

    /** Записує поточні значення всіх EditBox у об'єкт location (щоб не губити при перебудові) */
    private void flushCurrentTabInputs() {
        if (spawnXInput != null && spawnYInput != null && spawnZInput != null) {
            applySpawnCoords();
        }
        if (startingPointsInput != null) {
            try { location.setStartingPoints(Integer.parseInt(startingPointsInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (boundaryRadiusInput != null) {
            try { location.setLocationBoundaryRadius(Integer.parseInt(boundaryRadiusInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (leaveTimerInput != null) {
            try { location.setLocationLeaveTimerSec(Integer.parseInt(leaveTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalPenaltyTimerInput != null) {
            try { location.setPortalPenaltyTimerSec(Integer.parseInt(portalPenaltyTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalRespawnTimerInput != null) {
            try { location.setPortalRespawnTimerSec(Integer.parseInt(portalRespawnTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (reEntryCooldownInput != null) {
            try { location.setReEntryCooldownSec(Integer.parseInt(reEntryCooldownInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (zoneRadiusInput != null) {
            try { location.setAutoActivateRadius(Integer.parseInt(zoneRadiusInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (zoneActivationTimerInput != null) {
            try { location.setZoneActivationTimeSec(Integer.parseInt(zoneActivationTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (zoneOpenAfterStartInput != null) {
            try { location.setZoneOpenAfterStartSec(Integer.parseInt(zoneOpenAfterStartInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalOpenAfterStartInput != null) {
            try { location.setPortalOpenAfterStartSec(Integer.parseInt(portalOpenAfterStartInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (victoryLingerInput != null) {
            try { location.setVictoryLingerTimeSec(Integer.parseInt(victoryLingerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
    }
    private void setPlayerSpawn() {
        if (minecraft.player != null) {
            net.minecraft.core.BlockPos pos = minecraft.player.blockPosition();
            location.setPlayerSpawn(pos);
            rebuildWidgets();
        }
    }

    private void applySpawnCoords() {
        try {
            // Якщо поля порожні — беремо позицію гравця
            String sx = spawnXInput != null ? spawnXInput.getValue().trim() : "";
            String sy = spawnYInput != null ? spawnYInput.getValue().trim() : "";
            String sz = spawnZInput != null ? spawnZInput.getValue().trim() : "";
            if (sx.isEmpty() && sy.isEmpty() && sz.isEmpty()) {
                setPlayerSpawn();
                return;
            }
            if (minecraft.player == null) return;
            net.minecraft.core.BlockPos cur = location.getPlayerSpawn() != null
                    ? location.getPlayerSpawn() : minecraft.player.blockPosition();
            int x = sx.isEmpty() ? cur.getX() : Integer.parseInt(sx);
            int y = sy.isEmpty() ? cur.getY() : Integer.parseInt(sy);
            int z = sz.isEmpty() ? cur.getZ() : Integer.parseInt(sz);
            location.setPlayerSpawn(new net.minecraft.core.BlockPos(x, y, z));
            rebuildWidgets();
        } catch (NumberFormatException e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cНевірний формат координат!"), true);
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
        if (startingPointsInput != null) {
            try { location.setStartingPoints(Integer.parseInt(startingPointsInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        applySpawnCoords();
        // Boundary inputs
        if (boundaryRadiusInput != null) {
            try { location.setLocationBoundaryRadius(Integer.parseInt(boundaryRadiusInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (leaveTimerInput != null) {
            try { location.setLocationLeaveTimerSec(Integer.parseInt(leaveTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Portal inputs
        if (portalPenaltyTimerInput != null) {
            try { location.setPortalPenaltyTimerSec(Integer.parseInt(portalPenaltyTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalRespawnTimerInput != null) {
            try { location.setPortalRespawnTimerSec(Integer.parseInt(portalRespawnTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (reEntryCooldownInput != null) {
            try { location.setReEntryCooldownSec(Integer.parseInt(reEntryCooldownInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Zone / auto-activate inputs (були відсутні — причина баги)
        if (zoneRadiusInput != null) {
            try { location.setAutoActivateRadius(Integer.parseInt(zoneRadiusInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (zoneActivationTimerInput != null) {
            try { location.setZoneActivationTimeSec(Integer.parseInt(zoneActivationTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (zoneOpenAfterStartInput != null) {
            try { location.setZoneOpenAfterStartSec(Integer.parseInt(zoneOpenAfterStartInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Particle inputs (були відсутні — причина баги з particleSpeed)
        if (particleCountInput != null) {
            try { location.setZoneParticleCount(Integer.parseInt(particleCountInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (particleSpeedInput != null) {
            try { location.setZoneParticleSpeed(Float.parseFloat(particleSpeedInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (particleIntervalInput != null) {
            try { location.setZoneParticleInterval(Integer.parseInt(particleIntervalInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Portal open-after-start
        if (portalOpenAfterStartInput != null) {
            try { location.setPortalOpenAfterStartSec(Integer.parseInt(portalOpenAfterStartInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (victoryLingerInput != null) {
            try { location.setVictoryLingerTimeSec(Integer.parseInt(victoryLingerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("§a✓ Зміни збережено!"), true);
        // Примітка: навмисно НЕ надсилаємо RequestLocationDataPacket після збереження,
        // щоб уникнути race-condition де відповідь з OLD даними перезаписує свіжі зміни.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Блокуємо кліки на widgets що вийшли за межі scissor-зони (для не-PvP режиму)
        if (!location.isPvp()) {
            final int CLIP_TOP = 76;
            final int CLIP_BOT = this.height - 30;
            // Блокуємо кліки на прокручені widgets що вийшли за межі scissor-зони
            if (my >= CLIP_TOP && my < CLIP_BOT) {
                for (var r : this.renderables) {
                    if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                        if (staticWidgets.contains(w)) continue; // статичні — не блокуємо
                        int wy = w.getY(), wh = w.getHeight();
                        // Widget повністю поза scissor — блокуємо клік
                        if ((wy + wh <= CLIP_TOP || wy >= CLIP_BOT) && w.isMouseOver(mx, my)) {
                            return false;
                        }
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
        this.renderBackground(graphics);

        if (location.isPvp()) {
            // PvP режим — без scissor
            graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
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
        final int CLIP_BOT      = CONTENT_BOT;

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
        ScissorHelper.disable();

        // ── Крок 2: статична зона ВГОРІ (заголовок + PvE/PvP toggle + таби) ──
        // Рендеримо поверх контенту — вони завжди видимі.
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
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
                String t = getLocTip(btn.getMessage().getString());
                if (t != null) TooltipHelper.renderIfEnabled(graphics, this.font, t, mouseX, mouseY);
            }
        });
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════════════════════════
    //  СПЕЦІАЛЬНА ВКЛАДКА — Boundary + Location Trigger + Portal
    // ══════════════════════════════════════════════════════════════════
    private void initSpecialTab(int cx, int y) {
        // Flush current editbox values before rebuilding (prevents value loss on scroll/rebuild)
        if (boundaryRadiusInput != null) {
            try { location.setLocationBoundaryRadius(Integer.parseInt(boundaryRadiusInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (leaveTimerInput != null) {
            try { location.setLocationLeaveTimerSec(Integer.parseInt(leaveTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalPenaltyTimerInput != null) {
            try { location.setPortalPenaltyTimerSec(Integer.parseInt(portalPenaltyTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (portalRespawnTimerInput != null) {
            try { location.setPortalRespawnTimerSec(Integer.parseInt(portalRespawnTimerInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (reEntryCooldownInput != null) {
            try { location.setReEntryCooldownSec(Integer.parseInt(reEntryCooldownInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Reset input references
        boundaryRadiusInput = null;
        leaveTimerInput = null;
        portalPenaltyTimerInput = null;
        portalRespawnTimerInput = null;
        reEntryCooldownInput = null;
        infoPanelOffsetYInput = null;
        mobPanelOffsetYInput = null;
        infoPanelTextScaleInput = null;
        victoryLingerInput = null;
        particleCountInput = null;
        particleSpeedInput = null;
        particleIntervalInput = null;

        int panelW = Math.min(330, this.width - 40);
        int lx = cx - panelW / 2;
        y -= specialScrollOffset; // apply scroll

        // ─── -1. РЕЖИМ ГРИ ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Режим гри ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        boolean egm = location.isEnforceGameMode();
        this.addRenderableWidget(Button.builder(
            Component.literal(egm
                ? "§a☑ Примусовий режим гри (survival/adventure)"
                : "§7☐ Не примусувати режим гри"),
            b -> { location.setEnforceGameMode(!location.isEnforceGameMode()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 16).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            net.minecraft.network.chat.Component.literal(
                "§7Якщо увімкнено — Creative автоматично змінюється на Survival/Adventure при вході та щосекунди протягом гри")));
        y += 20;

        // ─── 0. ПЕРЕМОГА — екран та затримка ──────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Перемога ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        boolean vs = location.isVictoryScreenEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(vs ? "§a☑ Показувати екран ПЕРЕМОГИ" : "§7☐ Без екрану перемоги (одразу виходити)"),
            b -> { location.setVictoryScreenEnabled(!location.isVictoryScreenEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 16).build());
        y += 20;

        if (vs) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Час на локації після перемоги (сек):"), b -> {}
            ).bounds(lx, y, 226, 16).build()).active = false;
            // Завжди створюємо нову EditBox з поточним y (scroll-safe)
            victoryLingerInput = new EditBox(this.font, lx + 230, y, 60, 16, Component.literal("30"));
            victoryLingerInput.setMaxLength(4);
            victoryLingerInput.setValue(String.valueOf(location.getVictoryLingerTimeSec()));
            victoryLingerInput.setResponder(s -> { try { location.setVictoryLingerTimeSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(victoryLingerInput);
            y += 22;
            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ Після перемоги — title «ПЕРЕМОГА» і гравці залишаються N сек"), b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
            y += 16;
        }

        y += 4;
        // ─── 0b. ТОЧКИ ВИХОДУ ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Точки виходу ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        // Точка виходу після перемоги
        net.minecraft.core.BlockPos vep = location.getVictoryExitPos();
        String vepLbl = vep != null
            ? String.format("§a✓ Перемога: X%d Y%d Z%d", vep.getX(), vep.getY(), vep.getZ())
            : "§7Перемога: повернути на попереднє місце";
        this.addRenderableWidget(Button.builder(Component.literal(vepLbl), b -> {}).bounds(lx, y, panelW - 88, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.literal("📌 Задати"),
            b -> { if (minecraft.player != null) { location.setVictoryExitPos(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
        ).bounds(lx + panelW - 84, y, 42, 14).build());
        if (vep != null) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕"),
                b -> { location.setVictoryExitPos(null); saveChanges(); rebuildWidgets(); }
            ).bounds(lx + panelW - 40, y, 40, 14).build());
        }
        y += 18;

        // Точка виходу після здачі
        net.minecraft.core.BlockPos sep = location.getSurrenderExitPos();
        String sepLbl = sep != null
            ? String.format("§a✓ Здача: X%d Y%d Z%d", sep.getX(), sep.getY(), sep.getZ())
            : "§7Здача: повернути на попереднє місце";
        this.addRenderableWidget(Button.builder(Component.literal(sepLbl), b -> {}).bounds(lx, y, panelW - 88, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.literal("📌 Задати"),
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
            Component.literal("§8ℹ null = гравець повертається на місце звідки прийшов на локацію"), b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 14;

        // (Затримка першої хвилі перенесена до Хвилі 1 → Таймер у WaveConfigScreen)

        // ─── 0e. ЧАСТИНКИ ЗОНИ АКТИВАЦІЇ ─────────────────────────────────
        y += 4;
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Частинки зони входу ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;
        String curPart = location.getZoneParticleType();
        String partDisplay = (curPart == null || curPart.isEmpty()) ? "§7minecraft:squid_ink (за замовчуванням)" : "§a" + curPart;
        this.addRenderableWidget(Button.builder(Component.literal(partDisplay), b -> {}).bounds(lx, y, panelW, 14).build()).active = false;
        y += 16;
        // Швидкий вибір популярних частинок — 7 кнопок у рядок (44px кожна)
        String[] particlePresets = {"minecraft:squid_ink","minecraft:flame","minecraft:end_rod","minecraft:witch","minecraft:portal","minecraft:snowflake","minecraft:happy_villager"};
        String[] particleLabels  = {"squid","flame","star","witch","portal","snow","✿ villager"};
        int ppBtnW = (panelW - 6) / 7; // рівномірна ширина кнопок
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
            Component.literal("§8ℹ Тип частинок у колі навколо точки входу на локацію"), b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 16;
        // Кількість точок у кільці (0 = авто)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Точок у кільці (0=авто):"), b -> {}
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
        // Швидкість (інтенсивність) частинок
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Швидкість частинок (0=тихо, 0.5=помірно):"), b -> {}
        ).bounds(lx, y, 236, 14).build()).active = false;
        {
            // Локальний прапор - блокує responder під час setValue (Forge викликає responder в setValue)
            boolean[] suppressParticleSpeed = {true};
            particleSpeedInput = new EditBox(this.font, lx + 240, y, 56, 14, Component.literal("0.02"));
            particleSpeedInput.setMaxLength(6);
            particleSpeedInput.setResponder(s -> {
                if (suppressParticleSpeed[0]) return; // ігноруємо під час ініціалізації
                try {
                    float v = Float.parseFloat(s.replace(',', '.').trim());
                    if (v >= 0f && v <= 10f) location.setZoneParticleSpeed(v);
                } catch (NumberFormatException ignored) {}
            });
            particleSpeedInput.setValue(String.format("%.3f", location.getZoneParticleSpeed()));
            suppressParticleSpeed[0] = false; // тепер responder активний
            this.addRenderableWidget(particleSpeedInput);
        }
        y += 18;
        // Кулдаун частинок (інтервал між появою)
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Інтервал частинок (тіків, 1=кожен тік, 20=раз/сек):"), b -> {}
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

        y += 4;
        // ─── 1. КОРДОН ЛОКАЦІЇ ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Кордон локації ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        boolean boundaryOn = location.isLocationBoundaryEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(boundaryOn ? "§a☑ Відстеження кордону УВІМКНЕНО" : "§7☐ Відстеження кордону вимкнено"),
            b -> { location.setLocationBoundaryEnabled(!location.isLocationBoundaryEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());

        if (boundaryOn) {
            y += 20;

            // ── Радіус ──────────────────────────────────────────────────
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Радіус кордону (блоків):"), b -> {}
            ).bounds(lx, y, 190, 16).build()).active = false;
            boundaryRadiusInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("50"));
            boundaryRadiusInput.setValue(String.valueOf(location.getLocationBoundaryRadius()));
            boundaryRadiusInput.setMaxLength(5);
            this.addRenderableWidget(boundaryRadiusInput);
            y += 22;

            // ── Наслідок виходу ─────────────────────────────────────────
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Наслідок виходу за кордон:"), b -> {}
            ).bounds(lx, y, panelW, 14).build()).active = false;
            y += 16;

            com.wavedefense.data.Location.BoundaryConsequence[] conseqs = {
                com.wavedefense.data.Location.BoundaryConsequence.TIMER_SURRENDER,
                com.wavedefense.data.Location.BoundaryConsequence.DAMAGE,
                com.wavedefense.data.Location.BoundaryConsequence.TELEPORT_BACK,
                com.wavedefense.data.Location.BoundaryConsequence.INSTANT_SURRENDER
            };
            String[] conseqLabels = { "⏱ Таймер → здача", "💀 Постійна шкода", "↩ Телепорт назад", "⚡ Миттєва здача" };
            for (int ci = 0; ci < conseqs.length; ci++) {
                final com.wavedefense.data.Location.BoundaryConsequence cq = conseqs[ci];
                boolean sel = location.getBoundaryConsequence() == cq;
                this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a● " + conseqLabels[ci] : "§7○ " + conseqLabels[ci]),
                    b -> { location.setBoundaryConsequence(cq); rebuildWidgets(); }
                ).bounds(lx + (ci % 2) * (panelW / 2), y + (ci / 2) * 20, panelW / 2 - 2, 18).build());
            }
            y += 44;

            // ── Параметри залежно від наслідку ────────────────────────────
            com.wavedefense.data.Location.BoundaryConsequence bc = location.getBoundaryConsequence();
            if (bc == com.wavedefense.data.Location.BoundaryConsequence.TIMER_SURRENDER) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Час на повернення (сек):"), b -> {}
                ).bounds(lx, y, 190, 16).build()).active = false;
                leaveTimerInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("30"));
                leaveTimerInput.setValue(String.valueOf(location.getLocationLeaveTimerSec()));
                leaveTimerInput.setMaxLength(4);
                this.addRenderableWidget(leaveTimerInput);
                y += 22;
            } else if (bc == com.wavedefense.data.Location.BoundaryConsequence.DAMAGE) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Шкода (HP/сек):"), b -> {}
                ).bounds(lx, y, 190, 16).build()).active = false;
                EditBox dmgInput = new EditBox(this.font, lx + 194, y, 60, 16, Component.literal("2.0"));
                dmgInput.setValue(String.format("%.1f", location.getBoundaryDamagePerSec()));
                dmgInput.setMaxLength(5);
                dmgInput.setResponder(s -> { try { location.setBoundaryDamagePerSec(Float.parseFloat(s)); } catch(Exception ignored){} });
                this.addRenderableWidget(dmgInput);
                y += 22;
            }

            // ── Частинки кордону ────────────────────────────────────────
            boolean partOn = location.isBoundaryParticlesEnabled();
            this.addRenderableWidget(Button.builder(
                Component.literal(partOn ? "§a☑ Частинки кордону (УВІМКНЕНО)" : "§7☐ Частинки кордону (вимкнено)"),
                b -> { location.setBoundaryParticlesEnabled(!location.isBoundaryParticlesEnabled()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 20;

            if (partOn) {
                // Тип частинок
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Тип частинок (registry id):"), b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
                y += 14;
                EditBox ptypeBox = new EditBox(this.font, lx, y, 190, 16, Component.literal("minecraft:smoke"));
                ptypeBox.setValue(location.getBoundaryParticleType());
                ptypeBox.setMaxLength(64);
                ptypeBox.setResponder(s -> { if (!s.isBlank()) location.setBoundaryParticleType(s.trim()); });
                this.addRenderableWidget(ptypeBox);
                y += 20;

                // К-сть частинок і висота кільця
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7К-сть:"), b -> {}
                ).bounds(lx, y, 44, 16).build()).active = false;
                EditBox pcntBox = new EditBox(this.font, lx + 46, y, 40, 16, Component.literal("4"));
                pcntBox.setValue(String.valueOf(location.getBoundaryParticleCount()));
                pcntBox.setMaxLength(3);
                pcntBox.setResponder(s -> { try { location.setBoundaryParticleCount(Integer.parseInt(s)); } catch(Exception ig){} });
                this.addRenderableWidget(pcntBox);

                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Висота:"), b -> {}
                ).bounds(lx + 96, y, 50, 16).build()).active = false;
                EditBox phtBox = new EditBox(this.font, lx + 148, y, 40, 16, Component.literal("3"));
                phtBox.setValue(String.valueOf(location.getBoundaryParticleHeight()));
                phtBox.setMaxLength(3);
                phtBox.setResponder(s -> { try { location.setBoundaryParticleHeight(Integer.parseInt(s)); } catch(Exception ig){} });
                this.addRenderableWidget(phtBox);
                y += 22;

                // Швидкі пресети
                this.addRenderableWidget(Button.builder(
                    Component.literal("§8Пресет:"), b -> {}
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

        // ─── 2. АВТО-АКТИВАЦІЯ ЗОНИ ───────────────────────────────────────
        {
            boolean locTrigOn = true; // Зона авто-активації завжди доступна
            y += 4;
            // ── Авто-активація зони (вбудована у тригер запуску) ─────────
            boolean aa = location.isAutoActivate();
            this.addRenderableWidget(Button.builder(
                Component.literal(aa ? "§a☑ Авто-активація зони (УВІМКНЕНО)" : "§7☐ Авто-активація зони (вимкнено)"),
                b -> { location.setAutoActivate(!location.isAutoActivate()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 20;

            if (aa) {
                // Центр зони
                boolean useCustomCenter = location.isZoneUsesCustomCenter();
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Центр зони: " + (useCustomCenter ? "§a✓ Вказана точка" : "§e→ Точка спавну гравця")),
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
                        Component.literal("📌 Встановити тут"),
                        b -> { if (minecraft.player != null) { location.setZoneCenter(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
                    ).bounds(lx + panelW - 86, y, 86, 14).build());
                    y += 18;
                }

                // Точка входу (куди телепортувати)
                net.minecraft.core.BlockPos ep = location.getAutoActivateEntryPos();
                String epLabel = ep != null
                    ? String.format("§a✓ Вхід: X%d Y%d Z%d", ep.getX(), ep.getY(), ep.getZ())
                    : "§7Вхід: = точка спавну гравця";
                this.addRenderableWidget(Button.builder(Component.literal(epLabel), b -> {}).bounds(lx, y, panelW - 86, 14).build()).active = false;
                this.addRenderableWidget(Button.builder(
                    Component.literal("📌 Задати вхід"),
                    b -> { if (minecraft.player != null) { location.setAutoActivateEntryPos(minecraft.player.blockPosition()); saveChanges(); rebuildWidgets(); } }
                ).bounds(lx + panelW - 86, y, 86, 14).build());
                if (ep != null) {
                    y += 14;
                    this.addRenderableWidget(Button.builder(
                        Component.literal("§c✕ Скинути точку входу"),
                        b -> { location.setAutoActivateEntryPos(null); saveChanges(); rebuildWidgets(); }
                    ).bounds(lx, y, panelW, 12).build());
                }
                y += 18;

                // Радіус зони
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Радіус зони (бл):"), b -> {}
                ).bounds(lx, y, 140, 16).build()).active = false;
                zoneRadiusInput = new EditBox(this.font, lx + 144, y, 50, 16, Component.literal("5"));
                zoneRadiusInput.setValue(String.valueOf(location.getAutoActivateRadius()));
                zoneRadiusInput.setMaxLength(4);
                zoneRadiusInput.setResponder(s -> { try { location.setAutoActivateRadius(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
                this.addRenderableWidget(zoneRadiusInput);
                y += 20;

                // Таймер до активації (0 = миттєво)
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Затримка активації (сек, 0=миттєво):"), b -> {}
                ).bounds(lx, y, 220, 16).build()).active = false;
                zoneActivationTimerInput = new EditBox(this.font, lx + 224, y, 50, 16, Component.literal("0"));
                zoneActivationTimerInput.setValue(String.valueOf(location.getZoneActivationTimeSec()));
                zoneActivationTimerInput.setMaxLength(4);
                zoneActivationTimerInput.setResponder(s -> { try { location.setZoneActivationTimeSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
                this.addRenderableWidget(zoneActivationTimerInput);
                y += 20;

                // Час відкритості після старту (0 = закрити одразу)
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Зона відкрита після старту (сек, 0=закрити):"), b -> {}
                ).bounds(lx, y, 262, 16).build()).active = false;
                zoneOpenAfterStartInput = new EditBox(this.font, lx + 266, y, 50, 16, Component.literal("0"));
                zoneOpenAfterStartInput.setValue(String.valueOf(location.getZoneOpenAfterStartSec()));
                zoneOpenAfterStartInput.setMaxLength(4);
                zoneOpenAfterStartInput.setResponder(s -> { try { location.setZoneOpenAfterStartSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
                this.addRenderableWidget(zoneOpenAfterStartInput);
                y += 20;

                this.addRenderableWidget(Button.builder(
                    Component.literal("§8ℹ 0 = зона вимикається після запуску | >0 = запізнілі можуть зайти"), b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
                y += 14;
            }
        }

        y += 8;

        // ─── 3. ПОРТАЛ ────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Портал ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        boolean portalOn = location.isPortalEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(portalOn ? "§a☑ Рандомний портал УВІМКНЕНО" : "§7☐ Рандомний портал вимкнено"),
            b -> { location.setPortalEnabled(!location.isPortalEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());

        if (portalOn) {
            y += 22;
            // Штрафна хвиля
            int numWaves  = location.getWaves().size();
            int penaltyW  = location.getPortalPenaltyWave(); // -1 = всі
            String penStr = (penaltyW == -1) ? "§eВсі хвилі по порядку" : ("§eХвиля " + (penaltyW + 1));
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Штрафна хвиля: " + penStr), b -> {}
            ).bounds(lx, y, panelW - 80, 16).build()).active = false;

            // Кнопки переключення штрафної хвилі
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

            // Таймер до штрафної хвилі
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Таймер штрафної хвилі (сек):"), b -> {}
            ).bounds(lx, y, 200, 16).build()).active = false;
            portalPenaltyTimerInput = new EditBox(this.font, lx + 204, y, 60, 16, Component.literal("60"));
            portalPenaltyTimerInput.setValue(String.valueOf(location.getPortalPenaltyTimerSec()));
            portalPenaltyTimerInput.setMaxLength(5);
            this.addRenderableWidget(portalPenaltyTimerInput);
            y += 20;

            // Час відкритості порталу після запуску локації
            y += 4;
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Портал відкритий після старту (сек):"), b -> {}
            ).bounds(lx, y, 232, 16).build()).active = false;
            portalOpenAfterStartInput = new EditBox(this.font, lx + 236, y, 60, 16, Component.literal("-1"));
            portalOpenAfterStartInput.setValue(String.valueOf(location.getPortalOpenAfterStartSec()));
            portalOpenAfterStartInput.setMaxLength(5);
            portalOpenAfterStartInput.setResponder(s -> { try { location.setPortalOpenAfterStartSec(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(portalOpenAfterStartInput);
            y += 18;
            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ -1=grace 30сек | 0=закрити одразу | >0=відкритий N сек після старту"), b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
            y += 16;

            // Зникнення після проходження
            boolean disappears = location.isPortalDisappearsOnComplete();
            this.addRenderableWidget(Button.builder(
                Component.literal(disappears
                    ? "§a☑ Зникає після проходження локації"
                    : "§7☐ Не зникає (штрафний таймер скидається)"),
                b -> { location.setPortalDisappearsOnComplete(!location.isPortalDisappearsOnComplete()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 18).build());

            if (disappears) {
                y += 22;
                this.addRenderableWidget(Button.builder(
                    Component.literal("§7Час до відродження порталу (сек):"), b -> {}
                ).bounds(lx, y, 220, 16).build()).active = false;
                portalRespawnTimerInput = new EditBox(this.font, lx + 224, y, 60, 16, Component.literal("300"));
                portalRespawnTimerInput.setValue(String.valueOf(location.getPortalRespawnTimerSec()));
                portalRespawnTimerInput.setMaxLength(5);
                this.addRenderableWidget(portalRespawnTimerInput);
                y += 18;
                this.addRenderableWidget(Button.builder(
                    Component.literal("§8ℹ Після проходження портал зʼявиться у новому місці через цей час"),
                    b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
            } else {
                y += 22;
                this.addRenderableWidget(Button.builder(
                    Component.literal("§8ℹ Портал залишається, штрафний таймер скидається після кожної хвилі"),
                    b -> {}
                ).bounds(lx, y, panelW, 12).build()).active = false;
            }
        }

        y += 8;
        // ─── INFO PANEL — TextDisplay над точкою спавну гравців ───────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── ℹ Інфо-панель (TextDisplay) ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        // Загальна інформація що таке InfoPanel
        this.addRenderableWidget(Button.builder(
            Component.literal("§8TextDisplay entity — плаваючий текст у грі над спавном"),
            b -> {}
        ).bounds(lx, y, panelW, 11).build()).active = false;
        y += 14;

        // ── Spawn panel ────────────────────────────────────────────────────
        com.wavedefense.data.InfoPanelSettings ips = location.getInfoPanel();
        boolean spawnPanel = ips.isSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(spawnPanel ? "§a☑ Панель на точці спавну ГРАВЦІВ" : "§7☐ Панель на точці спавну гравців"),
            b -> { location.getInfoPanel().setSpawnPanelEnabled(!location.getInfoPanel().isSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        y += 22;

        if (spawnPanel) {
            // Що показувати
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Відображати:"), b -> {}
            ).bounds(lx, y, 90, 12).build()).active = false;
            y += 14;

            int bW = (panelW - 4) / 2;
            // Row 1
            boolean showPlayers = ips.isShowPlayerCount();
            this.addRenderableWidget(Button.builder(
                Component.literal(showPlayers ? "§a☑ Гравців у локації" : "§7☐ Гравців у локації"),
                b -> { location.getInfoPanel().setShowPlayerCount(!location.getInfoPanel().isShowPlayerCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showWave = ips.isShowWaveNumber();
            this.addRenderableWidget(Button.builder(
                Component.literal(showWave ? "§a☑ Номер хвилі" : "§7☐ Номер хвилі"),
                b -> { location.getInfoPanel().setShowWaveNumber(!location.getInfoPanel().isShowWaveNumber()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;

            // Row 2
            boolean showTimer = ips.isShowWaveTimer();
            this.addRenderableWidget(Button.builder(
                Component.literal(showTimer ? "§a☑ Таймер до хвилі" : "§7☐ Таймер до хвилі"),
                b -> { location.getInfoPanel().setShowWaveTimer(!location.getInfoPanel().isShowWaveTimer()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showMobs = ips.isShowMobsRemaining();
            this.addRenderableWidget(Button.builder(
                Component.literal(showMobs ? "§a☑ Мобів залишилось" : "§7☐ Мобів залишилось"),
                b -> { location.getInfoPanel().setShowMobsRemaining(!location.getInfoPanel().isShowMobsRemaining()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;

            // Row 3
            boolean showSecrets = ips.isShowSecretCount();
            this.addRenderableWidget(Button.builder(
                Component.literal(showSecrets ? "§a☑ Секретних хвиль" : "§7☐ Секретних хвиль"),
                b -> { location.getInfoPanel().setShowSecretCount(!location.getInfoPanel().isShowSecretCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean showShop = ips.isShowShopSecrets();
            this.addRenderableWidget(Button.builder(
                Component.literal(showShop ? "§a☑ Умовних товарів" : "§7☐ Умовних товарів"),
                b -> { location.getInfoPanel().setShowShopSecrets(!location.getInfoPanel().isShowShopSecrets()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;

            // Row 4
            boolean showPoints = ips.isShowPoints();
            this.addRenderableWidget(Button.builder(
                Component.literal(showPoints ? "§a☑ Поінти гравця" : "§7☐ Поінти гравця"),
                b -> { location.getInfoPanel().setShowPoints(!location.getInfoPanel().isShowPoints()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            // Row 4b
            boolean showFwt = ips.isShowFirstWaveTimer();
            this.addRenderableWidget(Button.builder(
                Component.literal(showFwt ? "§a☑ Таймер першої хвилі" : "§7☐ Таймер першої хвилі"),
                b -> { location.getInfoPanel().setShowFirstWaveTimer(!location.getInfoPanel().isShowFirstWaveTimer()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            // Row 5 — лобі таймер (над точкою входу)
            boolean showLbt = ips.isShowLobbyTimer();
            this.addRenderableWidget(Button.builder(
                Component.literal(showLbt ? "§a☑ Таймер лоббі (над входом)" : "§7☐ Таймер лоббі (над входом)"),
                b -> { location.getInfoPanel().setShowLobbyTimer(!location.getInfoPanel().isShowLobbyTimer()); rebuildWidgets(); }
            ).bounds(lx, y, panelW, 16).build());
            y += 20;

            // Висота панелі
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Висота над спавном (блоків):"), b -> {}
            ).bounds(lx, y, 186, 16).build()).active = false;
            infoPanelOffsetYInput = new EditBox(this.font, lx + 190, y, 60, 16, Component.literal("2.5"));
            infoPanelOffsetYInput.setValue(String.valueOf(ips.getSpawnPanelOffsetY()));
            infoPanelOffsetYInput.setMaxLength(5);
            infoPanelOffsetYInput.setResponder(s -> { try { location.getInfoPanel().setSpawnPanelOffsetY(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(infoPanelOffsetYInput);
            y += 22;
        }

        y += 4;
        // ── Mob spawn panel ────────────────────────────────────────────────
        boolean mobPanel = ips.isMobSpawnPanelEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(mobPanel ? "§a☑ Панель над точками спавну МОБІВ" : "§7☐ Панель над точками спавну мобів"),
            b -> { location.getInfoPanel().setMobSpawnPanelEnabled(!location.getInfoPanel().isMobSpawnPanelEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());
        y += 22;

        if (mobPanel) {
            int bW = (panelW - 4) / 2;
            boolean mTimer = ips.isMobShowWaveTimer();
            this.addRenderableWidget(Button.builder(
                Component.literal(mTimer ? "§a☑ Таймер до хвилі" : "§7☐ Таймер до хвилі"),
                b -> { location.getInfoPanel().setMobShowWaveTimer(!location.getInfoPanel().isMobShowWaveTimer()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            boolean mWave = ips.isMobShowWaveNumber();
            this.addRenderableWidget(Button.builder(
                Component.literal(mWave ? "§a☑ Номер хвилі" : "§7☐ Номер хвилі"),
                b -> { location.getInfoPanel().setMobShowWaveNumber(!location.getInfoPanel().isMobShowWaveNumber()); rebuildWidgets(); }
            ).bounds(lx + bW + 4, y, bW, 16).build());
            y += 20;
            boolean mMob = ips.isMobShowMobCount();
            this.addRenderableWidget(Button.builder(
                Component.literal(mMob ? "§a☑ К-ть мобів в хвилі" : "§7☐ К-ть мобів в хвилі"),
                b -> { location.getInfoPanel().setMobShowMobCount(!location.getInfoPanel().isMobShowMobCount()); rebuildWidgets(); }
            ).bounds(lx, y, bW, 16).build());
            y += 20;
            // Висота
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Висота над спавном мобів (блоків):"), b -> {}
            ).bounds(lx, y, 210, 16).build()).active = false;
            mobPanelOffsetYInput = new EditBox(this.font, lx + 214, y, 60, 16, Component.literal("2.5"));
            mobPanelOffsetYInput.setValue(String.valueOf(ips.getMobSpawnOffsetY()));
            mobPanelOffsetYInput.setMaxLength(5);
            mobPanelOffsetYInput.setResponder(s -> { try { location.getInfoPanel().setMobSpawnOffsetY(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(mobPanelOffsetYInput);
            y += 22;
        }

        // Загальний стиль (якщо хоч одна панель увімкнена)
        if (spawnPanel || mobPanel) {
            y += 4;
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Масштаб тексту (0.1-2.0):"), b -> {}
            ).bounds(lx, y, 172, 16).build()).active = false;
            infoPanelTextScaleInput = new EditBox(this.font, lx + 176, y, 50, 16, Component.literal("0.5"));
            infoPanelTextScaleInput.setValue(String.valueOf(ips.getTextScale()));
            infoPanelTextScaleInput.setMaxLength(5);
            infoPanelTextScaleInput.setResponder(s -> { try { location.getInfoPanel().setTextScale(Float.parseFloat(s.trim())); } catch (NumberFormatException ignored) {} });
            this.addRenderableWidget(infoPanelTextScaleInput);
            y += 20;

            boolean shadow = ips.isHasShadow();
            this.addRenderableWidget(Button.builder(
                Component.literal(shadow ? "§a☑ Тінь тексту" : "§7☐ Без тіні"),
                b -> { location.getInfoPanel().setHasShadow(!location.getInfoPanel().isHasShadow()); rebuildWidgets(); }
            ).bounds(lx, y, 140, 16).build());
            y += 18;

            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ Панель видима з будь-якого боку. З'являється після запуску, зникає після проходження."),
                b -> {}
            ).bounds(lx, y, panelW, 11).build()).active = false;
            y += 14;
        }
        // Розраховуємо реальну висоту вмісту Спец вкладки (відносно startY=80)
        specialContentHeight = (y + specialScrollOffset) - 80 + 20;
    }

    private String getLocTip(String label) {
        // Strip colour codes for matching
        String plain = label.replaceAll("§.", "").toLowerCase();
        if (plain.contains("авто-активац")) return TooltipHelper.ZONE_ACTIVATE;
        if (plain.contains("радіус активації")) return TooltipHelper.ZONE_RADIUS;
        if (plain.contains("зберегти зміни")) return "§7Записати всі налаштування локації на сервер\n§8Зміни набудуть чинності одразу";
        if (plain.contains("зберегти") && !plain.contains("назад")) return "§7Зберегти налаштування";
        if (plain.contains("pvp") || plain.contains("pve")) return "§7Перемкнути режим локації\n§8PvE — хвилі мобів, PvP — гравці vs гравці";
        if (plain.contains("зберігати речі") || plain.contains("очищати речі")) return TooltipHelper.KEEP_INVENTORY;
        if (plain.contains("стартові поінти") || plain.contains("поінти")) return TooltipHelper.STARTING_POINTS;
        if (plain.contains("📌") || plain.contains("моя позиція")) return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("застосувати")) return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("кордон")) return "§7Радіус де гравці вважаються «у зоні бою»\n§8При виході — відлік, потім автоздача";
        if (plain.contains("тригер запуску")) return "§7Локація стартує автоматично при тригері\n§8Всі гравці в радіусі телепортуються";
        if (plain.contains("портал")) return "§7Рандомний портал — кільце частинок\n§8Grace period 30 сек після першого гравця";
        if (plain.contains("разово")) return "§7Тригерна хвиля спрацює лише 1 раз за сесію локації";
        if (plain.contains("and")) return "§7Всі вказані тригери мають спрацювати одночасно";
        if (plain.contains("точка спавну гравця")) return TooltipHelper.SPAWN_COORDS;
        if (plain.contains("додати точку спавну мобів")) return "§7Додати точку де можуть з'являтись моби\n§8Поточна ваша позиція";
        if (plain.contains("таймер лобі") || plain.contains("таймер лоббі")) return TooltipHelper.LOBBY_TIMER;
        return null;
    }
}
