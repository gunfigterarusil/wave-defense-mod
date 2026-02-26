package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.LootSpawn;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Редактор точок спавну луту.
 * ✓ Меню тригерів (17 варіантів: час, хвилі, раунди, смерть, тощо)
 * ✓ Адаптивна верстка
 * ✓ Відображення активних тригерів у списку
 */
public class LootSpawnEditorScreen extends Screen {
    private final Location location;
    private final Screen   parent;

    private boolean editingItem = false;
    private int     editingIndex = -1;
    private boolean showTriggers = false; // режим вибору тригерів

    private List<ItemStack> editItems = new ArrayList<>();
    private EditBox chanceInput;
    private EditBox countInput;

    private int scrollOffset = 0;
    private static final int PER_PAGE = 5;
    private static final int SLOT_W   = 70;
    private static final int SLOT_H   = 16;
    private static final int SLOT_GAP = 6;

    public LootSpawnEditorScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.loot_spawns")
                .append(": ").append(location.getName()));
        this.location = location;
        this.parent   = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 30;

        if (editingItem && showTriggers) {
            initTriggerMode(cx, y);
            return;
        }
        if (editingItem) {
            initEditMode(cx, y);
            return;
        }

        // ── Список точок ─────────────────────────────────────────────
        int btnW = Math.min(340, this.width - 60);

        this.addRenderableWidget(Button.builder(
                Component.literal("§e➕ Додати точку луту"),
                button -> startAddAtPlayerPos()
        ).bounds(cx - btnW / 2, y, btnW - 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§7" + location.getLootSpawns().size() + " точок"),
                b -> {}
        ).bounds(cx + btnW / 2 - 95, y, 95, 20).build()).active = false;

        y += 26;

        List<LootSpawn> spawns = location.getLootSpawns();
        int rowH = 36;
        for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= spawns.size()) break;
            LootSpawn ls = spawns.get(idx);
            int yPos = y + i * rowH;

            BlockPos pos = ls.getPos();
            List<ItemStack> items = ls.getItems().stream()
                    .filter(it -> !it.isEmpty()).collect(Collectors.toList());
            String firstName = items.isEmpty() ? "порожньо"
                    : items.get(0).getHoverName().getString();
            if (firstName.length() > 14) firstName = firstName.substring(0, 12) + "…";
            if (items.size() > 1) firstName += " (+" + (items.size() - 1) + ")";

            // Зведений рядок тригерів
            String trigStr = ls.getTriggers().stream()
                    .map(t -> t.label)
                    .limit(2)
                    .collect(Collectors.joining(", "));
            if (ls.getTriggers().size() > 2) trigStr += "…";

            String lbl = String.format("§e%s §7X%d Y%d Z%d §7%d%% x%d",
                    firstName, pos.getX(), pos.getY(), pos.getZ(),
                    ls.getSpawnChance(), ls.getCount());

            this.addRenderableWidget(Button.builder(
                    Component.literal(lbl), b -> {}
            ).bounds(cx - btnW / 2, yPos, btnW - 48, 16).build()).active = false;

            // Тригери рядок
            this.addRenderableWidget(Button.builder(
                    Component.literal("§8⚡" + trigStr), b -> {}
            ).bounds(cx - btnW / 2, yPos + 17, btnW - 48, 14).build()).active = false;

            final int fIdx = idx;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"), button -> startEditItem(fIdx)
            ).bounds(cx + btnW / 2 - 45, yPos, 22, 32).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    button -> { location.removeLootSpawn(fIdx); rebuildWidgets(); }
            ).bounds(cx + btnW / 2 - 20, yPos, 22, 32).build());
        }

        if (spawns.size() > PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + this.width / 2 - 24, y, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (scrollOffset + PER_PAGE < spawns.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + this.width / 2 - 24, y + (PER_PAGE - 1) * rowH, 18, 18).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти і назад"), b -> saveAndBack()
        ).bounds(cx - 110, this.height - 26, 220, 20).build());
    }

    // ── Режим редагування точки луту ──────────────────────────────────
    private void initEditMode(int cx, int y) {
        int btnW = Math.min(340, this.width - 40);

        if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
            BlockPos pos = location.getLootSpawns().get(editingIndex).getPos();
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7Позиція: X%d Y%d Z%d",
                            pos.getX(), pos.getY(), pos.getZ())), b -> {}
            ).bounds(cx - btnW / 2, y, btnW, 14).build()).active = false;
            y += 18;
        }

        // Слоти предметів
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети луту:"), b -> {}
        ).bounds(cx - btnW / 2, y, 120, 14).build()).active = false;
        y += 16;

        int totalSW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsL  = cx - totalSW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos = slotsL + i * (SLOT_W + SLOT_GAP);
            final int si = i;
            ItemStack it = i < editItems.size() ? editItems.get(i) : ItemStack.EMPTY;
            String slotLbl = it.isEmpty() ? "§8[Порожньо]" : "§a✓ " + it.getHoverName().getString();
            if (slotLbl.length() > 14) slotLbl = slotLbl.substring(0, 13) + "…";

            // Вибрати через ItemSelectionScreen
            this.addRenderableWidget(Button.builder(
                    Component.literal(slotLbl),
                    b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editItems.set(si, stack);
                        rebuildWidgets();
                    }, it))
            ).bounds(xPos, y, SLOT_W, SLOT_H).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("§cОчистити"),
                    b -> { editItems.set(si, ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(xPos, y + SLOT_H + 2, SLOT_W, SLOT_H).build());
        }
        y += SLOT_H * 2 + 14;

        // Шанс
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Шанс (1-100%):"), b -> {}
        ).bounds(cx - btnW / 2, y, 140, 18).build()).active = false;
        chanceInput = new EditBox(this.font, cx, y, 55, 18, Component.literal("Шанс"));
        chanceInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getSpawnChance()) : "100");
        chanceInput.setMaxLength(3);
        this.addRenderableWidget(chanceInput);
        y += 24;

        // Кількість
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Кількість:"), b -> {}
        ).bounds(cx - btnW / 2, y, 140, 18).build()).active = false;
        countInput = new EditBox(this.font, cx, y, 55, 18, Component.literal("К-сть"));
        countInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getCount()) : "1");
        countInput.setMaxLength(4);
        this.addRenderableWidget(countInput);
        y += 28;

        // Кнопка тригерів
        int trigCount = editingIndex >= 0
                ? location.getLootSpawns().get(editingIndex).getTriggers().size() : 1;
        this.addRenderableWidget(Button.builder(
                Component.literal("§b⚡ Тригери спавну (" + trigCount + " обрано)"),
                b -> { showTriggers = true; rebuildWidgets(); }
        ).bounds(cx - btnW / 2, y, 210, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), b -> saveItem()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
                b -> { editingItem = false; showTriggers = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ── Режим вибору тригерів ──────────────────────────────────────────
    private void initTriggerMode(int cx, int y) {
        // Заголовок та легенда
        y += 6;

        // Отримуємо поточний набір тригерів
        Set<LootSpawn.Trigger> activeTriggers = editingIndex >= 0
                ? new LinkedHashSet<>(location.getLootSpawns().get(editingIndex).getTriggers())
                : new LinkedHashSet<>();
        // Зберігаємо у тимчасовому полі, щоб кнопки могли читати стан
        // (використовуємо editTriggers з поля)

        boolean isPvp = location.isPvp();
        int btnW = Math.min(220, (this.width - 30) / 2 - 8);
        int col1X = cx - btnW - 4;
        int col2X = cx + 4;
        int bH    = 18;
        int bGap  = 2;

        int col1Y = y, col2Y = y;

        boolean useCol1 = true;
        for (LootSpawn.Trigger t : LootSpawn.Trigger.values()) {
            // Показуємо тільки відповідні тригери для режиму
            if (isPvp && !t.pvp) continue;
            if (!isPvp && !t.pve) continue;

            boolean active = activeTriggers.contains(t);
            String lbl     = (active ? "§a§l☑ " : "§8☐ ") + t.label;

            final LootSpawn.Trigger ft = t;
            final Set<LootSpawn.Trigger> fActive = activeTriggers;

            Button btn = Button.builder(
                    Component.literal(lbl),
                    b -> {
                        if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                            LootSpawn ls = location.getLootSpawns().get(editingIndex);
                            if (ls.hasTrigger(ft)) ls.removeTrigger(ft);
                            else                    ls.addTrigger(ft);
                        } else {
                            // Новий елемент — тригери в editTriggers
                            if (editTriggers.contains(ft)) editTriggers.remove(ft);
                            else                            editTriggers.add(ft);
                        }
                        rebuildWidgets();
                    }
            ).bounds(useCol1 ? col1X : col2X, useCol1 ? col1Y : col2Y, btnW, bH).build();
            this.addRenderableWidget(btn);

            if (useCol1) col1Y += bH + bGap;
            else         col2Y += bH + bGap;
            useCol1 = !useCol1;
        }

        int bottomY = this.height - 28;
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Готово"),
                b -> { showTriggers = false; rebuildWidgets(); }
        ).bounds(cx - 60, bottomY, 120, 20).build());
    }

    // Тригери для нового (ще не збереженого) loot spawn
    private Set<LootSpawn.Trigger> editTriggers = new LinkedHashSet<>(
            Collections.singleton(LootSpawn.Trigger.WAVE_START));

    private void startAddAtPlayerPos() {
        if (minecraft.player == null) return;
        editingItem   = true;
        editingIndex  = -1;
        showTriggers  = false;
        editItems     = new ArrayList<>();
        editTriggers  = new LinkedHashSet<>(Collections.singleton(LootSpawn.Trigger.WAVE_START));
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void startEditItem(int idx) {
        editingItem  = true;
        editingIndex = idx;
        showTriggers = false;
        LootSpawn ls = location.getLootSpawns().get(idx);
        editItems    = new ArrayList<>(ls.getItems());
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void saveItem() {
        try {
            int chance = Math.max(1, Math.min(100,
                    Integer.parseInt(chanceInput != null ? chanceInput.getValue() : "100")));
            int count  = Math.max(1,
                    Integer.parseInt(countInput  != null ? countInput.getValue()  : "1"));
            List<ItemStack> finalItems = editItems.stream()
                    .filter(i -> !i.isEmpty()).collect(Collectors.toList());
            if (finalItems.isEmpty()) {
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(
                        Component.literal("§cДодайте хоча б один предмет!"), true);
                return;
            }

            if (editingIndex >= 0) {
                LootSpawn existing = location.getLootSpawns().get(editingIndex);
                existing.setItems(finalItems);
                existing.setSpawnChance(chance);
                existing.setCount(count);
                // тригери вже оновлюються in-place через кнопки
            } else {
                BlockPos pos = minecraft.player != null
                        ? minecraft.player.blockPosition() : BlockPos.ZERO;
                LootSpawn newLs = new LootSpawn(pos, finalItems, chance, count);
                newLs.setTriggers(new LinkedHashSet<>(editTriggers));
                location.addLootSpawn(newLs);
            }
            editingItem  = false;
            showTriggers = false;
            rebuildWidgets();
        } catch (NumberFormatException e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    Component.literal("§cНевірний формат числа!"), true);
        }
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                Component.literal("§a✓ Точки луту збережено!"), true);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);

        if (editingItem && showTriggers) {
            g.drawCenteredString(this.font, "§b⚡ Оберіть тригери спавну:", cx, 22, 0x55FFFF);
            g.drawString(this.font, "§7(вибрати кілька одночасно)", cx - 80, 32, 0xAAAAAA);
        } else if (editingItem) {
            // Рендер іконок предметів поверх кнопок
            int baseY = (editingIndex >= 0 ? 30 + 18 : 30) + 16;
            int totalSW = 4 * SLOT_W + 3 * SLOT_GAP;
            int slotsL  = cx - totalSW / 2;

            for (int i = 0; i < 4; i++) {
                int xPos = slotsL + i * (SLOT_W + SLOT_GAP);
                ItemStack item = i < editItems.size() ? editItems.get(i) : ItemStack.EMPTY;
                int iconX = xPos + (SLOT_W - 18) / 2;

                g.fill(iconX - 1, baseY - 1, iconX + 19, baseY + 17, 0xFF555555);
                g.fill(iconX, baseY, iconX + 18, baseY + 16, 0xFF222222);
                g.renderItem(item, iconX, baseY);
                g.renderItemDecorations(this.font, item, iconX, baseY);

                if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16
                        && mouseY >= baseY && mouseY <= baseY + 16) {
                    g.renderTooltip(this.font, item, mouseX, mouseY);
                }
            }
        } else {
            // Рендер іконок у списку
            int listY = 56;
            int rowH  = 36;
            int btnW  = Math.min(340, this.width - 60);
            List<LootSpawn> spawns = location.getLootSpawns();
            for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= spawns.size()) break;
                List<ItemStack> items = spawns.get(idx).getItems().stream()
                        .filter(it -> !it.isEmpty()).collect(Collectors.toList());
                int yPos = listY + i * rowH + 16;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    g.renderItem(items.get(j), cx - btnW / 2 + j * 20, yPos);
                    g.renderItemDecorations(this.font, items.get(j), cx - btnW / 2 + j * 20, yPos);
                }
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!editingItem) {
            if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && scrollOffset + PER_PAGE < location.getLootSpawns().size()) {
                scrollOffset++; rebuildWidgets();
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
