package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.LootSpawn;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Редактор точок спавну луту.
 * ✓ Меню тригерів (17 варіантів: час, хвилі, раунди, смерть, тощо)
 * ✓ Адаптивна верстка
 * ✓ Відображення активних тригерів у списку
 */
public class LootSpawnEditorScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Location location;
    private final Screen   parent;

    private boolean editingItem = false;
    private int     editingIndex = -1;
    private boolean showTriggers = false; // режим вибору тригерів
    private int     slotIconBaseY = 0;   // Y slot icons in edit mode
    private int     listIconsY    = 56;  // Y перший рядок у списку (синхронізується з init)   // Y координата слотів предметів (для render())

    private List<ItemStack> editItems = new ArrayList<>();
    private TextFieldWidget chanceInput;
    private TextFieldWidget countInput;
    // Координати точки спавну луту
    private TextFieldWidget lootXInput, lootYInput, lootZInput;

    private int scrollOffset = 0;
    private static final int PER_PAGE = 5;
    // G6c: Підтвердження видалення loot spawn
    private int pendingDeleteLootIndex = -1;
    private static final int SLOT_W    = 70;
    private static final int LIST_ROW_H = 44; // висота рядка у списку (достатньо для іконок + 2 рядки тексту)
    private static final int SLOT_H   = 16;
    private static final int SLOT_GAP = 6;

    public LootSpawnEditorScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.title.loot_spawns")
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

        this.addButton(new Button(cx - btnW / 2, y, btnW - 100, 20, new TranslationTextComponent("wavedefense.button.add_loot_point"), button -> startAddAtPlayerPos()));

        this.addButton(new Button(cx + btnW / 2 - 95, y, 95, 20, new TranslationTextComponent("wavedefense.label.loot_spawn_count", location.getLootSpawns().size()), b -> {})).active = false;

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
                    .filter(it -> !it.isEmpty()).collect(java.util.stream.Collectors.toList());
            String firstName = items.isEmpty()
                    ? net.minecraft.client.resources.I18n.get("wavedefense.label.loot_empty")
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
            this.addButton(new Button(cx - btnW / 2 + 4*20 + 4, yPos, btnW - 48 - 4*20 - 4, 14, new StringTextComponent(lbl), b -> {})).active = false;

            // Тригери рядок
            this.addButton(new Button(cx - btnW / 2, yPos + 18, btnW - 48, 14, new StringTextComponent("§8⚡" + trigStr), b -> {})).active = false;

            final int fIdx = idx;
            boolean isPendingDelLoot = (pendingDeleteLootIndex == fIdx);
            this.addButton(new Button(cx + btnW / 2 - 45, yPos, 22, 38, new StringTextComponent("✎"), button -> { pendingDeleteLootIndex = -1; startEditItem(fIdx); }));
            // Ширина розширюється при підтвердженні (35px як у AdminMenuScreen)
            int delLootW = isPendingDelLoot ? 35 : 22;
            int delLootX = isPendingDelLoot ? cx + btnW / 2 - 35 : cx + btnW / 2 - 20; // правий край cx+btnW/2
            this.addButton(new Button(delLootX, yPos, delLootW, 38, isPendingDelLoot
                        ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                        : new StringTextComponent("§c✕"), button -> {
                        if (isPendingDelLoot) {
                            pendingDeleteLootIndex = -1;
                            location.removeLootSpawn(fIdx);
                            init();
                        } else {
                            pendingDeleteLootIndex = fIdx;
                            init();
                        }
                    }));
        }

        if (spawns.size() > PER_PAGE) {
            this.addButton(new Button(cx + this.width / 2 - 24, y, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } }));
            this.addButton(new Button(cx + this.width / 2 - 24, y + (PER_PAGE - 1) * rowH, 18, 18, new StringTextComponent("▼"), b -> { if (scrollOffset + PER_PAGE < spawns.size()) { scrollOffset++; init(); } }));
        }

        this.addButton(new Button(cx - 110, this.height - 26, 220, 20, new TranslationTextComponent("wavedefense.button.save_back"), b -> saveAndBack()));
    }

    // ── Режим редагування точки луту ──────────────────────────────────
    private void initEditMode(int cx, int y) {
        int btnW = Math.min(340, this.width - 40);

        // ── Слоти предметів ────────────────────────────────────────────
        this.addButton(new Button(cx - btnW / 2, y, 120, 14, new TranslationTextComponent("wavedefense.auto.предмети_луту_87a19179"), b -> {})).active = false;

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
            String slotLbl = it.isEmpty() ? "§8[" + net.minecraft.client.resources.I18n.get("wavedefense.label.empty") + "]" : "§a✓ " + it.getHoverName().getString();
            if (slotLbl.length() > 14) slotLbl = slotLbl.substring(0, 13) + "…";

            // Вибрати через ItemSelectionScreen (кнопка нижче іконки)
            this.addButton(new Button(xPos, y, dynSlotW, SLOT_H, new StringTextComponent(slotLbl), b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editItems.set(si, stack);
                        rebuild();
                    }, it))));

            this.addButton(new Button(xPos, y + SLOT_H + 2, dynSlotW, SLOT_H, new TranslationTextComponent("wavedefense.button.clear_item"), b -> { editItems.set(si, ItemStack.EMPTY); rebuild(); }));

            // Кнопка "←" взяти з руки — під кожним слотом
            this.addButton(new Button(xPos, y + SLOT_H * 2 + 4, dynSlotW, SLOT_H, new StringTextComponent("←"), b -> {
                        if (minecraft.player != null) {
                            net.minecraft.item.ItemStack held = minecraft.player.getMainHandItem();
                            if (!held.isEmpty()) {
                                while (editItems.size() <= si) editItems.add(net.minecraft.item.ItemStack.EMPTY);
                                editItems.set(si, held.copy());
                                rebuild();
                            }
                        }
                    }))
            /* setTooltip omitted on 1.16.5 */;
        }
        y += SLOT_H * 3 + 16;

        // ── Координати точки спавну луту (компактно) ─────────────────
        {
            net.minecraft.util.math.BlockPos curPos;
            if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                curPos = location.getLootSpawns().get(editingIndex).getPos();
            } else {
                curPos = minecraft.player != null ? minecraft.player.blockPosition() : net.minecraft.util.math.BlockPos.ZERO;
            }
            int fw = 45;
            int cx2 = cx - btnW / 2;
            this.addButton(new Button(cx2, y, 28, 14, new TranslationTextComponent("wavedefense.auto.x_be0e6536"), b -> {})).active = false;
            lootXInput = new TextFieldWidget(this.font, cx2 + 30, y, fw, 14, new StringTextComponent("X"));
            lootXInput.setValue(String.valueOf(curPos.getX())); lootXInput.setMaxLength(7);
            this.addButton(lootXInput);

            this.addButton(new Button(cx2 + 79, y, 14, 14, new StringTextComponent("§7Y:"), b -> {})).active = false;
            lootYInput = new TextFieldWidget(this.font, cx2 + 95, y, fw, 14, new StringTextComponent("Y"));
            lootYInput.setValue(String.valueOf(curPos.getY())); lootYInput.setMaxLength(7);
            this.addButton(lootYInput);

            this.addButton(new Button(cx2 + 144, y, 14, 14, new StringTextComponent("§7Z:"), b -> {})).active = false;
            lootZInput = new TextFieldWidget(this.font, cx2 + 160, y, fw, 14, new StringTextComponent("Z"));
            lootZInput.setValue(String.valueOf(curPos.getZ())); lootZInput.setMaxLength(7);
            this.addButton(lootZInput);

            this.addButton(new Button(cx2 + 210, y, 18, 14, new StringTextComponent("📌"), b -> {
                        if (minecraft.player != null) {
                            net.minecraft.util.math.BlockPos pp = minecraft.player.blockPosition();
                            if (lootXInput != null) lootXInput.setValue(String.valueOf(pp.getX()));
                            if (lootYInput != null) lootYInput.setValue(String.valueOf(pp.getY()));
                            if (lootZInput != null) lootZInput.setValue(String.valueOf(pp.getZ()));
                        }
                    }));
            y += 18;
        }

        // Шанс
        this.addButton(new Button(cx - btnW / 2, y, 140, 18, new TranslationTextComponent("wavedefense.auto.шанс_1_100_dd19b46b"), b -> {})).active = false;
        chanceInput = new TextFieldWidget(this.font, cx, y, 55, 18, new TranslationTextComponent("wavedefense.auto.шанс_7ce3c7bb"));
        chanceInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getSpawnChance()) : "100");
        chanceInput.setMaxLength(3);
        this.addButton(chanceInput);
        y += 24;

        // Кількість
        this.addButton(new Button(cx - btnW / 2, y, 140, 18, new TranslationTextComponent("wavedefense.auto.кількість_e25b1eb1"), b -> {})).active = false;
        countInput = new TextFieldWidget(this.font, cx, y, 55, 18, new TranslationTextComponent("wavedefense.auto.к_сть_7beea1a7"));
        countInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getCount()) : "1");
        countInput.setMaxLength(4);
        this.addButton(countInput);
        y += 28;

        // Кнопка тригерів
        int trigCount = editingIndex >= 0
                ? location.getLootSpawns().get(editingIndex).getTriggers().size() : 1;
        this.addButton(new Button(cx - btnW / 2, y, 210, 20, new TranslationTextComponent("wavedefense.label.loot_trigger_count", trigCount), b -> { showTriggers = true; rebuild(); }));
        y += 28;

        this.addButton(new Button(cx - 110, y, 100, 20, new TranslationTextComponent("wavedefense.button.save"), b -> saveItem()));
        this.addButton(new Button(cx + 10, y, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> { editingItem = false; showTriggers = false; rebuild(); }));
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
        this.addButton(new Button(cx - btnW / 2, y, btnW, 12, new TranslationTextComponent("wavedefense.auto.клік_увімк_вимк_активний_прокрут_151cf949"), b -> {})).active = false;
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
            Button btn = new Button(cx - btnW / 2, ty, btnW, LOOT_BTN_H, new StringTextComponent(lbl), b -> {
                    if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
                        LootSpawn ls = location.getLootSpawns().get(editingIndex);
                        if (ls.hasTrigger(ft)) ls.removeTrigger(ft);
                        else                    ls.addTrigger(ft);
                    } else {
                        if (editTriggers.contains(ft)) editTriggers.remove(ft);
                        else                            editTriggers.add(ft);
                    }
                    rebuild();
                });
            /* setTooltip omitted on 1.16.5: btn */
            this.addButton(btn);
            ty += LOOT_BTN_H + LOOT_BTN_GAP;
        }

        // ── Per-trigger налаштування (під scissored зоною) ─────────
        if (!valueTriggers.isEmpty()) {
            int sy = lootTrigScrollBot + 4;
            this.addButton(new Button(cx - btnW / 2, sy, btnW, 14, new TranslationTextComponent("wavedefense.auto.налаштування_значень_n_b3f05f0e"), b -> {})).active = false;
            sy += 16;
            for (LootSpawn.Trigger vt : valueTriggers) {
                String lbl = vt == LootSpawn.Trigger.WAVE_N      ? I18n.get("wavedefense.loot.trigger.value_wave_n") :
                             vt == LootSpawn.Trigger.MOBS_KILLED_N ? I18n.get("wavedefense.loot.trigger.value_mobs_n") :
                                                                      I18n.get("wavedefense.loot.trigger.value_generic_n");
                this.addButton(new Button(cx - btnW / 2, sy, 200, 18, new StringTextComponent(lbl), b -> {})).active = false;
                final LootSpawn.Trigger fvt = vt;
                int currentVal = editingIndex >= 0 && editingIndex < location.getLootSpawns().size()
                    ? location.getLootSpawns().get(editingIndex).getTriggerValue(vt)
                    : editTriggerValues.getOrDefault(vt, 1);
                TextFieldWidget vInput = new TextFieldWidget(this.font, cx + btnW / 2 - 66, sy, 66, 18, new StringTextComponent("1"));
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
                this.addButton(vInput);
                if (triggerValueTarget == null) { triggerValueTarget = vt; triggerValueInput = vInput; }
                sy += 24;
            }
        }

        // ── Кнопка Готово ─────────────────────────────────────────────
        this.addButton(new Button(cx - 60, this.height - 26, 120, 20, new TranslationTextComponent("wavedefense.button.done"), b -> { showTriggers = false; rebuild(); }));
    }

    // Тригери для нового (ще не збереженого) loot spawn
    private Set<LootSpawn.Trigger> editTriggers = new LinkedHashSet<>(
            Collections.singleton(LootSpawn.Trigger.WAVE_START));
    // Per-trigger values для нового loot spawn
    private java.util.Map<LootSpawn.Trigger, Integer> editTriggerValues = new java.util.EnumMap<>(LootSpawn.Trigger.class);
    // TextFieldWidget для per-trigger налаштувань (відображається під scissored списком)
    private TextFieldWidget triggerValueInput = null;
    private LootSpawn.Trigger triggerValueTarget = null;

    private void startAddAtPlayerPos() {
        if (minecraft.player == null) return;
        editingItem   = true;
        editingIndex  = -1;
        showTriggers  = false;
        editItems     = new ArrayList<>();
        editTriggers  = new LinkedHashSet<>(Collections.singleton(LootSpawn.Trigger.WAVE_START));
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuild();
    }

    private void startEditItem(int idx) {
        editingItem  = true;
        editingIndex = idx;
        showTriggers = false;
        LootSpawn ls = location.getLootSpawns().get(idx);
        editItems    = new ArrayList<>(ls.getItems());
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuild();
    }

    private void saveItem() {
        try {
            int chance = Math.max(1, Math.min(100,
                    Integer.parseInt(chanceInput != null ? chanceInput.getValue() : "100")));
            int count  = Math.max(1,
                    Integer.parseInt(countInput  != null ? countInput.getValue()  : "1"));
            List<ItemStack> finalItems = editItems.stream()
                    .filter(i -> !i.isEmpty()).collect(java.util.stream.Collectors.toList());
            if (finalItems.isEmpty()) {
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(
                        new TranslationTextComponent("wavedefense.auto.додайте_хоча_б_один_предмет_aa9e1df5"), true);
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
            rebuild();
        } catch (NumberFormatException e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.auto.невірний_формат_числа_c9f66713"), true);
        }
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.auto.точки_луту_збережено_099a7605"), true);
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
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, cx, 10, GuiTheme.TEXT);

        // ── Визначаємо межі scissor для поточного режиму ─────────────
        // Список завжди між заголовком (~28) і нижньою кнопкою (height-30)
        int listTop = 28, listBot = this.height - 30;

        if (editingItem && showTriggers) {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.loot.trigger_pick_title"), cx, 16, 0x55FFFF);
            // Статичні елементи ДО scissor (заголовок вже намальований)
            for (Object r : this.buttons) {
                if (r instanceof net.minecraft.client.gui.widget.Widget) {
                    net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                    if (w.y < lootTrigScrollTop) w.render(g, mouseX, mouseY, partialTick);
                }
            }
            // Scissored список тригерів
            if (lootTrigScrollTop < lootTrigScrollBot) {
                ScissorHelper.enable(0, lootTrigScrollTop, this.width,
                        Math.max(1, lootTrigScrollBot - lootTrigScrollTop));
                for (Object r : this.buttons) {
                    if (r instanceof net.minecraft.client.gui.widget.Widget) {
                        net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                        if (w.y + w.getHeight() > lootTrigScrollTop
                            && w.y < lootTrigScrollBot) w.render(g, mouseX, mouseY, partialTick);
                    }
                }
                com.wavedefense.gui.GuiCompat.flush(g);
                ScissorHelper.disable();
            }
            // Статичні після scissor (per-trigger налаштування + Готово)
            for (Object r : this.buttons) {
                if (r instanceof net.minecraft.client.gui.widget.Widget) {
                    net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                    if (w.y >= lootTrigScrollBot) w.render(g, mouseX, mouseY, partialTick);
                }
            }
        } else {
            // Список точок луту або режим редагування — scissor між заголовком і футером
            for (Object r : this.buttons) {
                if (r instanceof net.minecraft.client.gui.widget.Widget) {
                    net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                    if (w.y < listTop) w.render(g, mouseX, mouseY, partialTick);
                }
            }
            ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
            for (Object r : this.buttons) {
                if (r instanceof net.minecraft.client.gui.widget.Widget) {
                    net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                    if (w.y + w.getHeight() > listTop && w.y < listBot) w.render(g, mouseX, mouseY, partialTick);
                }
            }
            com.wavedefense.gui.GuiCompat.flush(g);
            ScissorHelper.disable();
            for (Object r : this.buttons) {
                if (r instanceof net.minecraft.client.gui.widget.Widget) {
                    net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                    if (w.y >= listBot) w.render(g, mouseX, mouseY, partialTick);
                }
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
                com.wavedefense.gui.GuiCompat.fill(g, iconX - 1, iconY - 1, iconX + 17, iconY + 17, GuiTheme.BORDER);
                com.wavedefense.gui.GuiCompat.fill(g, iconX, iconY, iconX + 16, iconY + 16, GuiTheme.PANEL_DARK);
                com.wavedefense.gui.GuiCompat.renderItem(g, item, iconX, iconY);
                com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, item, iconX, iconY);

                if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16
                        && mouseY >= iconY && mouseY <= iconY + 16) {
                    com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, item, mouseX, mouseY);
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
                        .filter(it -> !it.isEmpty()).collect(java.util.stream.Collectors.toList());
                int iconY = listStartY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    int ix = cx - btnW / 2 + j * 20;
                    com.wavedefense.gui.GuiCompat.renderItem(g, items.get(j), ix, iconY);
                    com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, items.get(j), ix, iconY);
                }
            }
            com.wavedefense.gui.GuiCompat.flush(g);
            ScissorHelper.disable();
            // Tooltips поза scissor
            for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= spawns.size()) break;
                List<ItemStack> items = spawns.get(idx).getItems().stream()
                        .filter(it -> !it.isEmpty()).collect(java.util.stream.Collectors.toList());
                int iconY = listStartY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    int ix = cx - btnW / 2 + j * 20;
                    if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iconY && mouseY <= iconY + 16)
                        com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, items.get(j), mouseX, mouseY);
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
            if (delta > 0 && lootTriggerScrollOffset > 0) { lootTriggerScrollOffset--; rebuild(); }
            else if (delta < 0 && lootTriggerScrollOffset + visible < cnt) { lootTriggerScrollOffset++; rebuild(); }
            return true;
        }
        if (!editingItem) {
            if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuild(); }
            else if (delta < 0 && scrollOffset + PER_PAGE < location.getLootSpawns().size()) {
                scrollOffset++; rebuild();
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
