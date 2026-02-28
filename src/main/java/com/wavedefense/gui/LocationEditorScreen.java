package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.LocationMode;
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
    // Scroll for special tab
    private int specialScrollOffset = 0;

    public LocationEditorScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.location_editor").append(": ").append(location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // ── Вибір режиму PvE / PvP ──────────────────────────────────────────
        boolean isPve = !location.isPvp();

        this.addRenderableWidget(Button.builder(
                Component.literal(isPve ? "§2§l⬤ PvE §7(Мобів хвилі)" : "§7○ PvE"),
                button -> { location.setMode(LocationMode.PVE); rebuildWidgets(); }
        ).bounds(cx - 105, 25, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(!isPve ? "§c§l⬤ PvP §7(Гравці vs Гравці)" : "§7○ PvP"),
                button -> { location.setMode(LocationMode.PVP); rebuildWidgets(); }
        ).bounds(cx + 5, 25, 100, 20).build());

        // ── Якщо PvP — відкриваємо окремий редактор ───────────────────────
        if (!isPve) {
            int btnY = 55;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c⚔ Налаштувати PvP локацію..."),
                    button -> this.minecraft.setScreen(new PvpLocationEditorScreen(location, this))
            ).bounds(cx - 120, btnY, 240, 24).build());

            // Коротка довідка
            int teamCount = location.getPvpSpawnPoints().size();
            String teamInfo = teamCount < 2
                    ? "§c⚠ Потрібно ≥2 команди для роботи PvP"
                    : String.format("§a✓ %d команд | Мін. гравців: %d | Вбивство: +%d | Смерть: -%d",
                        teamCount, location.getPvpMinPlayers(),
                        location.getPvpKillPoints(), location.getPvpDeathPenalty());
            this.addRenderableWidget(Button.builder(
                    Component.literal(teamInfo), button -> {}
            ).bounds(cx - 160, btnY + 30, 330, 16).build()).active = false;

            // Збереження
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
            this.addRenderableWidget(Button.builder(
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

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти зміни"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 30, 130, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Назад до списку"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 20, this.height - 30, 130, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"), button -> this.onClose()
        ).bounds(cx + 120, this.height - 30, 40, 20).build());
    }

    private void initBasicTab(int cx, int startY) {
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
            BlockPos pos = location.getMobSpawns().get(realIndex);
            final int index = realIndex;
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7#%d: §fX:%d Y:%d Z:%d", realIndex + 1, pos.getX(), pos.getY(), pos.getZ())),
                    button -> {}
            ).bounds(cx - 150, listY + (i * 22), 220, 20).build()).active = false;
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
        if (invY > this.height - 120) invY = this.height - 120;

        // Авто-активація зони (тільки PvE)
        if (!location.isPvp()) {
            boolean aa = location.isAutoActivate();
            this.addRenderableWidget(Button.builder(
                    Component.literal(aa ? "§a☑ Авто-активація зони" : "§7☐ Авто-активація зони"),
                    b -> { location.setAutoActivate(!location.isAutoActivate()); rebuildWidgets(); }
            ).bounds(cx - 150, invY, 200, 18).build());
            if (aa) {
                // Радіус
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7Радіус (5-9999 бл):"), b -> {}
                ).bounds(cx + 55, invY, 120, 16).build()).active = false;
                EditBox aaRInput = new EditBox(this.font, cx + 177, invY, 45, 16, Component.literal("5"));
                aaRInput.setValue(String.valueOf(location.getAutoActivateRadius()));
                aaRInput.setMaxLength(4);
                aaRInput.setResponder(s -> {
                    try { location.setAutoActivateRadius(Integer.parseInt(s.trim())); }
                    catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(aaRInput);
                invY += 20;

                // Точка входу (autoActivate entry spawn) — де телепортуватись при авто-активації
                net.minecraft.core.BlockPos entryPos = location.getAutoActivateEntryPos() != null
                        ? location.getAutoActivateEntryPos() : location.getPlayerSpawn();
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7Точка входу (📌 = точка спавну):"), b -> {}
                ).bounds(cx + 55, invY, 210, 14).build()).active = false;
                invY += 16;
                this.addRenderableWidget(Button.builder(
                        Component.literal("📌 Моя позиція"),
                        b -> { if (minecraft.player != null) { location.setAutoActivateEntryPos(minecraft.player.blockPosition()); rebuildWidgets(); } }
                ).bounds(cx + 55, invY, 100, 14).build());
                this.addRenderableWidget(Button.builder(
                        Component.literal("= Точка спавну"),
                        b -> { location.setAutoActivateEntryPos(null); rebuildWidgets(); }
                ).bounds(cx + 158, invY, 100, 14).build());
                if (entryPos != null) {
                    this.addRenderableWidget(Button.builder(
                            Component.literal(String.format("§8X%d Y%d Z%d", entryPos.getX(), entryPos.getY(), entryPos.getZ())), b -> {}
                    ).bounds(cx + 55, invY + 16, 200, 12).build()).active = false;
                    invY += 14;
                }
            }
            invY += 24;
        }

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
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6Товарів у магазині: §e%d", location.getShopItems().size())),
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

    private void switchTab(int tab) { this.currentTab = tab; this.rebuildWidgets(); }
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
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("§a✓ Зміни збережено!"), true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!location.isPvp() && currentTab == 0) {
            if (delta > 0 && mobSpawnScrollOffset > 0) { mobSpawnScrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && mobSpawnScrollOffset + MOB_SPAWN_PER_PAGE < location.getMobSpawns().size()) { mobSpawnScrollOffset++; rebuildWidgets(); }
            return true;
        }
        if (currentTab == 4) { // Спец tab
            if (delta > 0 && specialScrollOffset > 0) { specialScrollOffset -= 10; if (specialScrollOffset < 0) specialScrollOffset = 0; rebuildWidgets(); }
            else if (delta < 0) { specialScrollOffset += 10; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
        // Tooltips
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn && btn.isHoveredOrFocused()) {
                String t = getLocTip(btn.getMessage().getString());
                if (t != null) TooltipHelper.renderIfEnabled(graphics, this.font, t, mouseX, mouseY);
            }
        });
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════════════════════════
    //  СПЕЦІАЛЬНА ВКЛАДКА — Boundary + Location Trigger + Portal
    // ══════════════════════════════════════════════════════════════════
    private void initSpecialTab(int cx, int y) {
        // Reset inputs on rebuild
        boundaryRadiusInput = null;
        leaveTimerInput = null;
        portalPenaltyTimerInput = null;
        portalRespawnTimerInput = null;

        int panelW = Math.min(330, this.width - 40);
        int lx = cx - panelW / 2;
        y -= specialScrollOffset; // apply scroll

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
            y += 22;
            // Радіус (1-9999)
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Радіус кордону (1-9999 блоків):"), b -> {}
            ).bounds(lx, y, 200, 16).build()).active = false;
            boundaryRadiusInput = new EditBox(this.font, lx + 204, y, 60, 16, Component.literal("50"));
            boundaryRadiusInput.setValue(String.valueOf(location.getLocationBoundaryRadius()));
            boundaryRadiusInput.setMaxLength(4);
            this.addRenderableWidget(boundaryRadiusInput);

            y += 20;
            // Таймер повернення
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Час на повернення (сек):"), b -> {}
            ).bounds(lx, y, 200, 16).build()).active = false;
            leaveTimerInput = new EditBox(this.font, lx + 204, y, 60, 16, Component.literal("30"));
            leaveTimerInput.setValue(String.valueOf(location.getLocationLeaveTimerSec()));
            leaveTimerInput.setMaxLength(4);
            this.addRenderableWidget(leaveTimerInput);
            y += 16;

            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ Якщо гравець виходить за кордон — title + таймер, потім здача"),
                b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
        }

        y += boundaryOn ? 18 : 22;

        // ─── 2. ТРИГЕР ЗАПУСКУ ЛОКАЦІЇ ────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§6§l── Тригер запуску локації ──"), b -> {}
        ).bounds(lx, y, panelW, 14).build()).active = false;
        y += 18;

        boolean locTrigOn = location.isLocationTriggerEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(locTrigOn ? "§a☑ Запуск по тригеру УВІМКНЕНО" : "§7☐ Запуск по тригеру вимкнено"),
            b -> { location.setLocationTriggerEnabled(!location.isLocationTriggerEnabled()); rebuildWidgets(); }
        ).bounds(lx, y, panelW, 18).build());

        if (locTrigOn) {
            y += 22;
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Тригер: §e" + location.getLocationTriggerType().label), b -> {}
            ).bounds(lx, y, panelW, 14).build()).active = false;
            y += 18;

            // Двоколонний список тригерів
            int colW  = panelW / 2 - 2;
            int bH    = 16;
            int c1X   = lx, c2X = lx + colW + 4;
            int c1Y   = y,  c2Y = y;
            boolean col1 = true;
            for (WaveTrigger t : WaveTrigger.values()) {
                if (!t.pve) continue; // локаційний тригер — тільки PvE
                boolean isSel = (t == location.getLocationTriggerType());
                String lbl = (isSel ? "§e§l▶ " : "§8  ") + t.label;
                final WaveTrigger ft = t;
                Button trigBtn2 = Button.builder(
                    Component.literal(lbl),
                    b -> { location.setLocationTriggerType(ft); rebuildWidgets(); }
                ).bounds(col1 ? c1X : c2X, col1 ? c1Y : c2Y, colW, bH).build();
                trigBtn2.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    net.minecraft.network.chat.Component.literal("§7" + ft.tooltip)));
                this.addRenderableWidget(trigBtn2);
                if (col1) c1Y += bH + 2; else c2Y += bH + 2;
                col1 = !col1;
            }
            y = Math.max(c1Y, c2Y) + 4;
            this.addRenderableWidget(Button.builder(
                Component.literal("§8ℹ При спрацюванні — всі гравці в радіусі потраплять на локацію"),
                b -> {}
            ).bounds(lx, y, panelW, 12).build()).active = false;
        }

        y += locTrigOn ? 18 : 22;

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
    }

    private String getLocTip(String label) {
        if (label.contains("Авто-активац")) return TooltipHelper.ZONE_ACTIVATE;
        if (label.contains("Радіус активації")) return TooltipHelper.ZONE_RADIUS;
        if (label.contains("Зберегти зміни")) return "§7Зберегти всі налаштування локації на сервері";
        if (label.contains("PvP") || label.contains("PvE")) return "§7Перемкнути режим локації (PvE = хвилі мобів, PvP = гравці vs гравці)";
        if (label.contains("речі") || label.contains("Зберіг") || label.contains("Очищати")) return TooltipHelper.KEEP_INVENTORY;
        if (label.contains("поінти") || label.contains("Стартові")) return TooltipHelper.STARTING_POINTS;
        if (label.contains("📌") || label.contains("Моя позиція")) return TooltipHelper.SPAWN_COORDS;
        if (label.contains("Застосувати")) return TooltipHelper.SPAWN_COORDS;
        if (label.contains("Кордон") || label.contains("кордон")) return "§7Відстежує чи гравець вийшов за межі локації\n§8При виході — відлік, потім автоздача\n§8Радіус кордону = радіус де гравці ще вважаються "в локації"";
        if (label.contains("Тригер запуску")) return "§7Локація стартує автоматично при спрацюванні тригера\n§8Всі гравці в радіусі одразу телепортуються";
        if (label.contains("Портал")) return "§7Рандомний портал — вертикальне кільце частинок\n§8Після першого гравця — портал закривається\n§8Якщо ніхто не зайшов — штрафна хвиля";
        if (label.contains("Разово")) return "§7Тригерна хвиля спрацює лише один раз за всю сесію локації";
        if (label.contains("AND")) return "§7Всі вказані тригери мають спрацювати ОДНОЧАСНО";
        return null;
    }
}
