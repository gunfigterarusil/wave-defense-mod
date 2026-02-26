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

    // Поля правил
    private EditBox minPlayersInput;
    private EditBox killPointsInput;
    private EditBox deathPenaltyInput;
    private EditBox totalRoundsInput;
    private EditBox buyTimeInput;
    private EditBox pvpStartingPointsInput;

    private int scrollOffset = 0;
    private static final int PER_PAGE = 6;

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
        int tabW = 80, tabGap = 4;
        int totalTabW = 4 * tabW + 3 * tabGap;
        int tabX = cx - totalTabW / 2;

        String[] tabs = {"⚑ Команди", "⚙ Правила", "🛒 Магазин", "📦 Лут"};
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal(currentTab == i ? "§a§l● " + tabs[i] : "§7○ " + tabs[i]),
                    button -> { currentTab = ti; scrollOffset = 0; rebuildWidgets(); }
            ).bounds(tabX + i * (tabW + tabGap), 25, tabW, 20).build());
        }

        int startY = 52;

        switch (currentTab) {
            case 0 -> initTeamsTab(cx, startY);
            case 1 -> initRulesTab(cx, startY);
            case 2 -> initShopTab(cx, startY);
            case 3 -> initLootTab(cx, startY);
        }

        // Нижня панель
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveChanges()
        ).bounds(cx - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(
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
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§e%s §7X%d Y%d Z%d", sp.getTeamName(), pos.getX(), pos.getY(), pos.getZ())),
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

        y += 28;
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveSpawn()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), button -> { editingSpawn = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ─── Вкладка: Правила ──────────────────────────────────────────────
    private void initRulesTab(int cx, int y) {
        // Мінімальна кількість гравців
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Мінімальна кількість гравців для старту:"), button -> {}
        ).bounds(cx - 160, y, 280, 18).build()).active = false;
        y += 20;
        minPlayersInput = new EditBox(this.font, cx + 130, y - 20, 50, 18, Component.literal("2"));
        minPlayersInput.setValue(String.valueOf(location.getPvpMinPlayers()));
        minPlayersInput.setMaxLength(4);
        this.addRenderableWidget(minPlayersInput);

        // Friendly Fire
        y += 4;
        this.addRenderableWidget(Button.builder(
                Component.literal(location.isPvpFriendlyFire() ? "§a☑ Friendly Fire (союзники б'ють союзників)" : "§c☐ Friendly Fire (вимкнено)"),
                button -> { location.setPvpFriendlyFire(!location.isPvpFriendlyFire()); rebuildWidgets(); }
        ).bounds(cx - 160, y, 340, 20).build());
        y += 28;

        // Поінти за вбивство
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Поінтів за вбивство гравця:"), button -> {}
        ).bounds(cx - 160, y, 220, 18).build()).active = false;
        killPointsInput = new EditBox(this.font, cx + 70, y, 60, 18, Component.literal("100"));
        killPointsInput.setValue(String.valueOf(location.getPvpKillPoints()));
        killPointsInput.setMaxLength(6);
        this.addRenderableWidget(killPointsInput);
        y += 28;

        // Штраф за смерть
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Поінтів знімається за смерть:"), button -> {}
        ).bounds(cx - 160, y, 220, 18).build()).active = false;
        deathPenaltyInput = new EditBox(this.font, cx + 70, y, 60, 18, Component.literal("50"));
        deathPenaltyInput.setValue(String.valueOf(location.getPvpDeathPenalty()));
        deathPenaltyInput.setMaxLength(6);
        this.addRenderableWidget(deathPenaltyInput);
        y += 28;

        // Кількість раундів
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Кількість раундів:"), button -> {}
        ).bounds(cx - 160, y, 220, 18).build()).active = false;
        totalRoundsInput = new EditBox(this.font, cx + 70, y, 60, 18, Component.literal("10"));
        totalRoundsInput.setValue(String.valueOf(location.getPvpTotalRounds()));
        totalRoundsInput.setMaxLength(4);
        this.addRenderableWidget(totalRoundsInput);
        y += 28;

        // Час покупок між раундами
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Час покупок між раундами (сек):"), button -> {}
        ).bounds(cx - 160, y, 220, 18).build()).active = false;
        buyTimeInput = new EditBox(this.font, cx + 70, y, 60, 18, Component.literal("20"));
        buyTimeInput.setValue(String.valueOf(location.getPvpBuyTime()));
        buyTimeInput.setMaxLength(4);
        this.addRenderableWidget(buyTimeInput);
        y += 30;

        // Стартові поінти для PvP
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Стартові поінти для покупок:"), button -> {}
        ).bounds(cx - 160, y, 220, 18).build()).active = false;
        pvpStartingPointsInput = new EditBox(this.font, cx + 70, y, 60, 18, Component.literal("0"));
        pvpStartingPointsInput.setValue(String.valueOf(location.getStartingPoints()));
        pvpStartingPointsInput.setMaxLength(6);
        this.addRenderableWidget(pvpStartingPointsInput);
        y += 30;

        // Зберегти правила
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Застосувати правила"), button -> saveRules()
        ).bounds(cx - 100, y, 200, 20).build());
        y += 28;

        // Інформаційний рядок
        int teamCount = location.getPvpSpawnPoints().size();
        String info = teamCount < 2
                ? "§c⚠ Потрібно мінімум 2 точки спавну (команди)!"
                : String.format("§a✓ %d команд налаштовано | Мін. гравців: %d", teamCount, location.getPvpMinPlayers());
        this.addRenderableWidget(Button.builder(
                Component.literal(info), button -> {}
        ).bounds(cx - 160, y, 340, 18).build()).active = false;
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

        // Визначаємо позицію: з полів або поточна позиція гравця
        net.minecraft.core.BlockPos pos = resolveSpawnPos();
        if (pos == null) return;

        if (editingSpawnIndex >= 0 && editingSpawnIndex < location.getPvpSpawnPoints().size()) {
            location.getPvpSpawnPoints().get(editingSpawnIndex).setTeamName(name);
            location.getPvpSpawnPoints().get(editingSpawnIndex).setPos(pos);
        } else {
            location.addPvpSpawnPoint(new PvpSpawnPoint(name, pos));
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

    private void saveRules() {
        try {
            if (minPlayersInput != null) location.setPvpMinPlayers(Integer.parseInt(minPlayersInput.getValue()));
            if (killPointsInput != null) location.setPvpKillPoints(Math.max(0, Integer.parseInt(killPointsInput.getValue())));
            if (deathPenaltyInput != null) location.setPvpDeathPenalty(Math.max(0, Integer.parseInt(deathPenaltyInput.getValue())));
            if (totalRoundsInput != null) location.setPvpTotalRounds(Math.max(1, Integer.parseInt(totalRoundsInput.getValue())));
            if (buyTimeInput != null) location.setPvpBuyTime(Math.max(5, Integer.parseInt(buyTimeInput.getValue())));
            if (pvpStartingPointsInput != null) location.setStartingPoints(Math.max(0, Integer.parseInt(pvpStartingPointsInput.getValue())));
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.literal("§a✓ Правила збережено!"), true);
        } catch (NumberFormatException ignored) {}
    }

    private void saveChanges() {
        saveRules();
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.literal("§a✓ PvP локацію збережено!"), true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFF5555);
        // Tooltips
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn && btn.isHoveredOrFocused()) {
                String tip = getTip(btn.getMessage().getString());
                if (tip != null) TooltipHelper.renderIfEnabled(graphics, this.font, tip, mouseX, mouseY);
            }
        });
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String getTip(String label) {
        if (label.contains("Дружній вогонь")) return TooltipHelper.PVP_FF;
        if (label.contains("раундів"))        return TooltipHelper.PVP_ROUNDS;
        if (label.contains("покупок"))        return TooltipHelper.PVP_BUY_TIME;
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
