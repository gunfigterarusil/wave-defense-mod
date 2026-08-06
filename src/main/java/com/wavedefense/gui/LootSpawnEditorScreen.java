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
import net.minecraft.client.resources.language.I18n;
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
    private int     slotIconBaseY = 0;   // Y slot icons in edit mode
    private int     listIconsY    = 56;  // Y перший рядок у списку (синхронізується з init)   // Y координата слотів предметів (для render())

    private List<ItemStack> editItems = new ArrayList<>();
    private EditBox chanceInput;
    private EditBox countInput;
    // Координати точки спавну луту
    private EditBox lootXInput, lootYInput, lootZInput;

    private int scrollOffset = 0;
    private static final int PER_PAGE = 5;
    // G6c: Підтвердження видалення loot spawn
    private int pendingDeleteLootIndex = -1;
    private static final int SLOT_W    = 70;
    private static final int LIST_ROW_H = 44; // висота рядка у списку (достатньо для іконок + 2 рядки тексту)
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
                Component.translatable("wavedefense.button.add_loot_point"),
                button -> startAddAtPlayerPos()
        ).bounds(cx - btnW / 2, y, btnW - 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.label.loot_spawn_count", location.getLootSpawns().size()),
                b -> {}
        ).bounds(cx + btnW / 2 - 95, y, 95, 20).build()).active = false;

        y += 26;

        List<LootSpawn> spawns = location.getLootSpawns();
        int rowH = LIST_ROW_H;
        listIconsY = y; // синхронізуємо для render()
        for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= spawns.size()) break;
            LootSpawn ls = spawns.get(idx);
            int yPos = y + i * rowH;

            BlockPos pos = ls.getPos();
            List<ItemStack> items = ls.getItems().stream()
                    .filter(it -> !it.isEmpty()).collect(Collectors.toList());
            String firstName = items.isEmpty()
                    ? net.minecraft.client.resources.language.I18n.get("wavedefense.label.loot_empty")
                    : items.get(0).getHoverName().getString();
            if (firstName.length() > 14) firstName = firstName.substring(0, 12) + "…";
            if (items.size() > 1) firstName += " (+" + (items.size() - 1) + ")";

            // Зведений рядок тригерів
            String trigStr = ls.getTriggers().stream()
                    .map(t -> I18n.get(t.label))
                    .limit(2)
                    .collect(Collectors.joining(", "));
            if (ls.getTriggers().size() > 2) trigStr += "…";

            String lbl = String.format("§e%s §7X%d Y%d Z%d §7%d%% x%d",
                    firstName, pos.getX(), pos.getY(), pos.getZ(),
                    ls.getSpawnChance(), ls.getCount());

            // yPos:    placeholder під іконки (рендеряться в render())
            // yPos+18: назва + позиція
            // yPos+30: тригери
            this.addRenderableWidget(Button.builder(
                    Component.literal(lbl), b -> {}
            ).bounds(cx - btnW / 2 + 4*20 + 4, yPos, btnW - 48 - 4*20 - 4, 14).build()).active = false;

            // Тригери рядок
            this.addRenderableWidget(Button.builder(
                    Component.literal("§8⚡" + trigStr), b -> {}
            ).bounds(cx - btnW / 2, yPos + 18, btnW - 48, 14).build()).active = false;

            final int fIdx = idx;
            boolean isPendingDelLoot = (pendingDeleteLootIndex == fIdx);
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"), button -> { pendingDeleteLootIndex = -1; startEditItem(fIdx); }
            ).bounds(cx + btnW / 2 - 45, yPos, 22, 38).build());
            // Ширина розширюється при підтвердженні (35px як у AdminMenuScreen)
            int delLootW = isPendingDelLoot ? 35 : 22;
            int delLootX = isPendingDelLoot ? cx + btnW / 2 - 35 : cx + btnW / 2 - 20; // правий край cx+btnW/2
            this.addRenderableWidget(Button.builder(
                    isPendingDelLoot
                        ? Component.translatable("wavedefense.button.confirm_delete")
                        : Component.literal("§c✕"),
                    button -> {
                        if (isPendingDelLoot) {
                            pendingDeleteLootIndex = -1;
                            location.removeLootSpawn(fIdx);
                            rebuildWidgets();
                        } else {
                            pendingDeleteLootIndex = fIdx;
                            rebuildWidgets();
                        }
                    }
            ).bounds(delLootX, yPos, delLootW, 38).build());
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
                Component.translatable("wavedefense.button.save_back"), b -> saveAndBack()
        ).bounds(cx - 110, this.height - 26, 220, 20).build());
    }

    // ── Режим редагування точки луту ──────────────────────────────────
    private void initEditMode(int cx, int y) {
        int btnW = Math.min(340, this.width - 40);

        // ── Слоти предметів ────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.предмети_луту_87a19179"), b -> {}
        ).bounds(cx - btnW / 2, y, 120, 14).build()).active = false;

        y += 4;

        // Динамічна ширина слотів (адаптується до розміру екрану)
        int dynSlotW = Math.max(40, (btnW - 3 * SLOT_GAP) / 4);
        int totalSW = 4 * dynSlotW + 3 * SLOT_GAP;
        int slotsL  = cx - totalSW / 2;
        slotIconBaseY = y; // запам'ятовуємо Y для render() — іконки тут
        y += 20; // відступ під іконки (18px іконка + 2px gap)

        for (int i = 0; i < 4; i++) {
            int xPos = slotsL + i * (SLOT_W + SLOT_GAP);
            final int si = i;
            ItemStack it = i < editItems.size() ? editItems.get(i) : ItemStack.EMPTY;
            String slotLbl = it.isEmpty() ? "§8[" + net.minecraft.client.resources.language.I18n.get("wavedefense.label.empty") + "]" : "§a✓ " + it.getHoverName().getString();
            if (slotLbl.length() > 14) slotLbl = slotLbl.substring(0, 13) + "…";

            // Вибрати через ItemSelectionScreen (кнопка нижче іконки)
            this.addRenderableWidget(Button.builder(
                    Component.literal(slotLbl),
                    b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editItems.set(si, stack);
                        rebuildWidgets();
                    }, it))
            ).bounds(xPos, y, dynSlotW, SLOT_H).build());

            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.clear_item"),
                    b -> { editItems.set(si, ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(xPos, y + SLOT_H + 2, dynSlotW, SLOT_H).build());

            // Кнопка "←" взяти з руки — під кожним слотом
            this.addRenderableWidget(Button.builder(
                    Component.literal("←"),
                    b -> {
                        if (minecraft.player != null) {
                            net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
                            if (!held.isEmpty()) {
                                while (editItems.size() <= si) editItems.add(net.minecraft.world.item.ItemStack.EMPTY);
                                editItems.set(si, held.copy());
                                rebuildWidgets();
                            }
                        }
                    }
            ).bounds(xPos, y + SLOT_H * 2 + 4, dynSlotW, SLOT_H).build())
            .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("wavedefense.button.from_hand")));
        }
        y += SLOT_H * 3 + 16;

        // ── Координати точки спавну луту (компактно) ─────────────────
        {
            net.minecraft.core.BlockPos curPos;
            if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                curPos = location.getLootSpawns().get(editingIndex).getPos();
            } else {
                curPos = minecraft.player != null ? minecraft.player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
            }
            int fw = 45;
            int cx2 = cx - btnW / 2;
            this.addRenderableWidget(Button.builder(Component.translatable("wavedefense.auto.x_be0e6536"), b -> {}).bounds(cx2, y, 28, 14).build()).active = false;
            lootXInput = new EditBox(this.font, cx2 + 30, y, fw, 14, Component.literal("X"));
            lootXInput.setValue(String.valueOf(curPos.getX())); lootXInput.setMaxLength(7);
            this.addRenderableWidget(lootXInput);

            this.addRenderableWidget(Button.builder(Component.literal("§7Y:"), b -> {}).bounds(cx2 + 79, y, 14, 14).build()).active = false;
            lootYInput = new EditBox(this.font, cx2 + 95, y, fw, 14, Component.literal("Y"));
            lootYInput.setValue(String.valueOf(curPos.getY())); lootYInput.setMaxLength(7);
            this.addRenderableWidget(lootYInput);

            this.addRenderableWidget(Button.builder(Component.literal("§7Z:"), b -> {}).bounds(cx2 + 144, y, 14, 14).build()).active = false;
            lootZInput = new EditBox(this.font, cx2 + 160, y, fw, 14, Component.literal("Z"));
            lootZInput.setValue(String.valueOf(curPos.getZ())); lootZInput.setMaxLength(7);
            this.addRenderableWidget(lootZInput);

            this.addRenderableWidget(Button.builder(
                    Component.literal("📌"),
                    b -> {
                        if (minecraft.player != null) {
                            net.minecraft.core.BlockPos pp = minecraft.player.blockPosition();
                            if (lootXInput != null) lootXInput.setValue(String.valueOf(pp.getX()));
                            if (lootYInput != null) lootYInput.setValue(String.valueOf(pp.getY()));
                            if (lootZInput != null) lootZInput.setValue(String.valueOf(pp.getZ()));
                        }
                    }
            ).bounds(cx2 + 210, y, 18, 14).build());
            y += 18;
        }

        // Шанс
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.шанс_1_100_dd19b46b"), b -> {}
        ).bounds(cx - btnW / 2, y, 140, 18).build()).active = false;
        chanceInput = new EditBox(this.font, cx, y, 55, 18, Component.translatable("wavedefense.auto.шанс_7ce3c7bb"));
        chanceInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getSpawnChance()) : "100");
        chanceInput.setMaxLength(3);
        this.addRenderableWidget(chanceInput);
        y += 24;

        // Кількість
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.кількість_e25b1eb1"), b -> {}
        ).bounds(cx - btnW / 2, y, 140, 18).build()).active = false;
        countInput = new EditBox(this.font, cx, y, 55, 18, Component.translatable("wavedefense.auto.к_сть_7beea1a7"));
        countInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getCount()) : "1");
        countInput.setMaxLength(4);
        this.addRenderableWidget(countInput);
        y += 28;

        // Кнопка тригерів
        int trigCount = editingIndex >= 0
                ? location.getLootSpawns().get(editingIndex).getTriggers().size() : 1;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.label.loot_trigger_count", trigCount),
                b -> { showTriggers = true; rebuildWidgets(); }
        ).bounds(cx - btnW / 2, y, 210, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.save"), b -> saveItem()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                b -> { editingItem = false; showTriggers = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ── Режим вибору тригерів ──────────────────────────────────────────
    // Scroll offset для списку тригерів у loot-редакторі
    private int lootTriggerScrollOffset = 0;
    // Межі scissored зони (встановлюється в initTriggerMode, читається в render)
    int lootTrigScrollTop = 0;
    int lootTrigScrollBot = 0;

    private static final int LOOT_BTN_H   = 20;
    private static final int LOOT_BTN_GAP = 2;

    private void initTriggerMode(int cx, int y) {
        y += 4;
        boolean isPvp = location.isPvp();
        int btnW = Math.min(310, this.width - 30);
        triggerValueInput  = null;
        triggerValueTarget = null;

        // Підказка
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.клік_увімк_вимк_активний_прокрут_151cf949"), b -> {}
        ).bounds(cx - btnW / 2, y, btnW, 12).build()).active = false;
        y += 16;

        // ── Збираємо доступні тригери по контексту (PvE/PvP) ────────
        java.util.List<LootSpawn.Trigger> avail = new java.util.ArrayList<>();
        for (LootSpawn.Trigger t : LootSpawn.Trigger.values()) {
            if (isPvp  && !t.pvp) continue;
            if (!isPvp && !t.pve) continue;
            avail.add(t);
        }

        // ── Per-trigger налаштування займають місце під списком ──────
        // Обчислюємо які тригери активні і потребують N-налаштування
        Set<LootSpawn.Trigger> activeTriggers = editingIndex >= 0 && editingIndex < location.getLootSpawns().size()
            ? location.getLootSpawns().get(editingIndex).getTriggers()
            : editTriggers;
        java.util.List<LootSpawn.Trigger> valueTriggers = new java.util.ArrayList<>();
        for (LootSpawn.Trigger t : activeTriggers) {
            if (t.needsValue) valueTriggers.add(t);
        }
        int perTrigH = valueTriggers.isEmpty() ? 0 : (valueTriggers.size() * 24 + 18);

        lootTrigScrollTop = y;
        lootTrigScrollBot = this.height - perTrigH - 30;
        if (lootTrigScrollBot < lootTrigScrollTop + LOOT_BTN_H) lootTrigScrollBot = lootTrigScrollTop + LOOT_BTN_H;

        int listH   = lootTrigScrollBot - lootTrigScrollTop;
        int visible = Math.max(1, listH / (LOOT_BTN_H + LOOT_BTN_GAP));
        int maxScr  = Math.max(0, avail.size() - visible);
        if (lootTriggerScrollOffset > maxScr) lootTriggerScrollOffset = maxScr;

        // ── Список тригерів — одна колонка зі scissor ────────────────
        int ty = lootTrigScrollTop;
        for (int i = lootTriggerScrollOffset; i < avail.size(); i++) {
            if (ty + LOOT_BTN_H > lootTrigScrollBot) break;
            LootSpawn.Trigger ft = avail.get(i);
            boolean active = activeTriggers.contains(ft);
            // Показуємо значення якщо є
            String valStr = (ft.needsValue && active)
                ? " §8[N=" + (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()
                    ? location.getLootSpawns().get(editingIndex).getTriggerValue(ft)
                    : editTriggerValues.getOrDefault(ft, 1)) + "]"
                : "";
            String lbl = (active ? "§a☑ " : "§7☐ ") + I18n.get(ft.label) + valStr;
            Button btn = Button.builder(
                Component.literal(lbl),
                b -> {
                    if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                        LootSpawn ls = location.getLootSpawns().get(editingIndex);
                        if (ls.hasTrigger(ft)) ls.removeTrigger(ft);
                        else                    ls.addTrigger(ft);
                    } else {
                        if (editTriggers.contains(ft)) editTriggers.remove(ft);
                        else                            editTriggers.add(ft);
                    }
                    rebuildWidgets();
                }
            ).bounds(cx - btnW / 2, ty, btnW, LOOT_BTN_H).build();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7" + I18n.get(ft.tooltip) + (ft.needsValue ? "\n" + I18n.get("wavedefense.loot.trigger.needs_value_hint") : ""))));
            this.addRenderableWidget(btn);
            ty += LOOT_BTN_H + LOOT_BTN_GAP;
        }

        // ── Per-trigger налаштування (під scissored зоною) ─────────
        if (!valueTriggers.isEmpty()) {
            int sy = lootTrigScrollBot + 4;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.налаштування_значень_n_b3f05f0e"), b -> {}
            ).bounds(cx - btnW / 2, sy, btnW, 14).build()).active = false;
            sy += 16;
            for (LootSpawn.Trigger vt : valueTriggers) {
                String lbl = vt == LootSpawn.Trigger.WAVE_N      ? I18n.get("wavedefense.loot.trigger.value_wave_n") :
                             vt == LootSpawn.Trigger.MOBS_KILLED_N ? I18n.get("wavedefense.loot.trigger.value_mobs_n") :
                                                                      I18n.get("wavedefense.loot.trigger.value_generic_n");
                this.addRenderableWidget(Button.builder(
                    Component.literal(lbl), b -> {}
                ).bounds(cx - btnW / 2, sy, 200, 18).build()).active = false;
                final LootSpawn.Trigger fvt = vt;
                int currentVal = editingIndex >= 0 && editingIndex < location.getLootSpawns().size()
                    ? location.getLootSpawns().get(editingIndex).getTriggerValue(vt)
                    : editTriggerValues.getOrDefault(vt, 1);
                EditBox vInput = new EditBox(this.font, cx + btnW / 2 - 66, sy, 66, 18, Component.literal("1"));
                vInput.setMaxLength(6);
                vInput.setValue(String.valueOf(currentVal));
                vInput.setResponder(s -> {
                    try {
                        int val = Integer.parseInt(s.trim());
                        if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                            location.getLootSpawns().get(editingIndex).setTriggerValue(fvt, val);
                        } else {
                            editTriggerValues.put(fvt, val);
                        }
                    } catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(vInput);
                if (triggerValueTarget == null) { triggerValueTarget = vt; triggerValueInput = vInput; }
                sy += 24;
            }
        }

        // ── Кнопка Готово ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.done"),
            b -> { showTriggers = false; rebuildWidgets(); }
        ).bounds(cx - 60, this.height - 26, 120, 20).build());
    }

    // Тригери для нового (ще не збереженого) loot spawn
    private Set<LootSpawn.Trigger> editTriggers = new LinkedHashSet<>(
            Collections.singleton(LootSpawn.Trigger.WAVE_START));
    // Per-trigger values для нового loot spawn
    private java.util.Map<LootSpawn.Trigger, Integer> editTriggerValues = new java.util.EnumMap<>(LootSpawn.Trigger.class);
    // EditBox для per-trigger налаштувань (відображається під scissored списком)
    private EditBox triggerValueInput = null;
    private LootSpawn.Trigger triggerValueTarget = null;

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
                        Component.translatable("wavedefense.auto.додайте_хоча_б_один_предмет_aa9e1df5"), true);
                return;
            }

            // Читаємо координати
            BlockPos newPos = BlockPos.ZERO;
            try {
                int lx = Integer.parseInt(lootXInput != null ? lootXInput.getValue().trim() : "0");
                int ly = Integer.parseInt(lootYInput != null ? lootYInput.getValue().trim() : "0");
                int lz = Integer.parseInt(lootZInput != null ? lootZInput.getValue().trim() : "0");
                newPos = new BlockPos(lx, ly, lz);
            } catch (NumberFormatException ignored) {
                if (minecraft.player != null) newPos = minecraft.player.blockPosition();
            }

            if (editingIndex >= 0) {
                LootSpawn existing = location.getLootSpawns().get(editingIndex);
                existing.setPos(newPos);
                existing.setItems(finalItems);
                existing.setSpawnChance(chance);
                existing.setCount(count);
                // тригери вже оновлюються in-place через кнопки
            } else {
                LootSpawn newLs = new LootSpawn(newPos, finalItems, chance, count);
                newLs.setTriggers(new LinkedHashSet<>(editTriggers));
                location.addLootSpawn(newLs);
            }
            editingItem  = false;
            showTriggers = false;
            rebuildWidgets();
        } catch (NumberFormatException e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    Component.translatable("wavedefense.auto.невірний_формат_числа_c9f66713"), true);
        }
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        // This list is excluded from UpdateLocationPacket (content-sized), so it
        // travels on the generic chunked list transport instead.
        {
            net.minecraft.nbt.ListTag _l = new net.minecraft.nbt.ListTag();
            for (var _e : location.getLootSpawns()) _l.add(_e.save());
            com.wavedefense.network.packets.ReplaceLocationListPacket.sendList(
                location.getName(), "lootSpawns", _l);
        }
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.auto.точки_луту_збережено_099a7605"), true);
        this.minecraft.setScreen(parent);
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
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, GuiTheme.TEXT);

        // ── Визначаємо межі scissor для поточного режиму ─────────────
        // Список завжди між заголовком (~28) і нижньою кнопкою (height-30)
        int listTop = 28, listBot = this.height - 30;

        if (editingItem && showTriggers) {
            g.drawCenteredString(this.font, Component.translatable("wavedefense.loot.trigger_pick_title"), cx, 16, 0x55FFFF);
            // Статичні елементи ДО scissor (заголовок вже намальований)
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() < lootTrigScrollTop)
                    w.render(g, mouseX, mouseY, partialTick);
            }
            // Scissored список тригерів
            if (lootTrigScrollTop < lootTrigScrollBot) {
                ScissorHelper.enable(0, lootTrigScrollTop, this.width,
                        Math.max(1, lootTrigScrollBot - lootTrigScrollTop));
                for (var r : this.renderables) {
                    if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                            && w.getY() + w.getHeight() > lootTrigScrollTop
                            && w.getY() < lootTrigScrollBot)
                        w.render(g, mouseX, mouseY, partialTick);
                }
                g.flush();
                ScissorHelper.disable();
            }
            // Статичні після scissor (per-trigger налаштування + Готово)
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() >= lootTrigScrollBot)
                    w.render(g, mouseX, mouseY, partialTick);
            }
        } else {
            // Список точок луту або режим редагування — scissor між заголовком і футером
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() < listTop)
                    w.render(g, mouseX, mouseY, partialTick);
            }
            ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                    w.render(g, mouseX, mouseY, partialTick);
            }
            g.flush();
            ScissorHelper.disable();
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() >= listBot)
                    w.render(g, mouseX, mouseY, partialTick);
            }
        }
        // Тепер рендеримо іконки ПОВЕРХ кнопок (після super.render)
        if (editingItem && !showTriggers) {
            // Іконки предметів у режимі редагування — поверх кнопок-слотів
            // slotIconBaseY = Y кнопок вибору слотів, встановлено в initEditMode
            int baseY = slotIconBaseY > 0 ? slotIconBaseY : 62;
            int totalSW = 4 * SLOT_W + 3 * SLOT_GAP;
            int slotsL  = cx - totalSW / 2;

            for (int i = 0; i < 4; i++) {
                int xPos = slotsL + i * (SLOT_W + SLOT_GAP);
                ItemStack item = i < editItems.size() ? editItems.get(i) : ItemStack.EMPTY;
                int iconX = xPos + (SLOT_W - 16) / 2;
                int iconY = baseY;  // точно на Y кнопки "вибрати"

                // Фон слоту поверх кнопки
                g.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, GuiTheme.BORDER);
                g.fill(iconX, iconY, iconX + 16, iconY + 16, GuiTheme.PANEL_DARK);
                g.renderItem(item, iconX, iconY);
                g.renderItemDecorations(this.font, item, iconX, iconY);

                if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16
                        && mouseY >= iconY && mouseY <= iconY + 16) {
                    g.renderTooltip(this.font, item, mouseX, mouseY);
                }
            }
        } else if (!editingItem) {
            // Іконки у списку точок луту — у scissor-зоні списку
            int listStartY = listIconsY;  // синхронізовано з init()
            int rowH  = LIST_ROW_H;
            int btnW  = Math.min(340, this.width - 60);
            List<LootSpawn> spawns = location.getLootSpawns();
            ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
            for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= spawns.size()) break;
                List<ItemStack> items = spawns.get(idx).getItems().stream()
                        .filter(it -> !it.isEmpty()).collect(Collectors.toList());
                int iconY = listStartY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    int ix = cx - btnW / 2 + j * 20;
                    g.renderItem(items.get(j), ix, iconY);
                    g.renderItemDecorations(this.font, items.get(j), ix, iconY);
                }
            }
            g.flush();
            ScissorHelper.disable();
            // Tooltips поза scissor
            for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= spawns.size()) break;
                List<ItemStack> items = spawns.get(idx).getItems().stream()
                        .filter(it -> !it.isEmpty()).collect(Collectors.toList());
                int iconY = listStartY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    int ix = cx - btnW / 2 + j * 20;
                    if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iconY && mouseY <= iconY + 16)
                        g.renderTooltip(this.font, items.get(j), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (editingItem && showTriggers) {
            // Скрол списку тригерів
            boolean isPvp = location.isPvp();
            int cnt = (int) java.util.Arrays.stream(LootSpawn.Trigger.values())
                .filter(t -> isPvp ? t.pvp : t.pve).count();
            int listH   = Math.max(1, lootTrigScrollBot - lootTrigScrollTop);
            int visible = listH / (LOOT_BTN_H + LOOT_BTN_GAP);
            if (delta > 0 && lootTriggerScrollOffset > 0) { lootTriggerScrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && lootTriggerScrollOffset + visible < cnt) { lootTriggerScrollOffset++; rebuildWidgets(); }
            return true;
        }
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
