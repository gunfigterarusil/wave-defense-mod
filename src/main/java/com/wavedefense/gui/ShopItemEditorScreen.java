package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.wavedefense.data.ShopItem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Редактор товару в магазині.
 * Предмети тепер вибираються через ItemSelectionScreen (не з руки).
 * Підказки при наведенні на кнопки.
 */
public class ShopItemEditorScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Location location;
    private final int itemIndex;
    private final Screen parent;
    private List<ItemStack> items = new ArrayList<>();
    private TextFieldWidget buyPriceInput;
    private TextFieldWidget sellPriceInput;
    private ShopItem.ShopCategory selectedCategory = ShopItem.ShopCategory.OTHER;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private int iconRowY = 44; // Y-координата рядку іконок (встановлюється в init)

    // Якщо не null — редагуємо товари точки магазину (не глобального списку)
    private final com.wavedefense.data.ShopPoint shopPoint;

    // Буфери ціни — зберігаються між init() щоб не губити введені значення
    private int pendingBuyPrice  = -1; // -1 = ще не ініціалізовано
    private int pendingSellPrice = -1;
    // Буфери кількостей предметів у кожному з 4 слотів (зберігаються між rebuildWidgets).
    // -1 = ще не ініціалізовано (буде заповнено з items[i].getCount() при першому init()).
    private final int[] pendingCounts = { -1, -1, -1, -1 };

    private static final int SLOT_W = 70;
    private static final int SLOT_H = 16;
    private static final int SLOT_GAP = 6;
    private static final int CLIP_TOP = 26;

    // ── Конструктор для глобального магазину локації ─────────────────
    public ShopItemEditorScreen(Location location, int itemIndex, Screen parent) {
        this(location, null, itemIndex, parent);
    }

    // ── Конструктор для точки магазину ───────────────────────────────
    public ShopItemEditorScreen(Location location, com.wavedefense.data.ShopPoint shopPoint, int itemIndex, Screen parent) {
        super(itemIndex >= 0 ? new TranslationTextComponent("wavedefense.title.edit_item") : new TranslationTextComponent("wavedefense.title.new_item"));
        this.location  = location;
        this.shopPoint = shopPoint;
        this.itemIndex = itemIndex;
        this.parent    = parent;

        List<com.wavedefense.data.ShopItem> sourceList = getSourceList();
        if (itemIndex >= 0 && itemIndex < sourceList.size()) {
            this.items.addAll(sourceList.get(itemIndex).getItems());
        }
        while (this.items.size() < 4) this.items.add(ItemStack.EMPTY);
        if (itemIndex >= 0 && itemIndex < sourceList.size()) {
            selectedCategory = sourceList.get(itemIndex).getCategory();
        }
    }

    /** Повертає список товарів що редагується — або точки, або локації. */
    private List<com.wavedefense.data.ShopItem> getSourceList() {
        return shopPoint != null ? shopPoint.getItems() : location.getShopItems();
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int startY = 30 - scrollOffset;

        this.addButton(new Button(cx - 155, startY, 220, 14, new TranslationTextComponent("wavedefense.auto.предмети_товару_до_4_слотів_16b40145"), button -> {})).active = false;

        startY += 14;

        // Рядок іконок — зберігаємо Y для render()
        iconRowY = startY; // клас-поле, доступне у render()
        startY += 22; // відступ під іконки (16 + 6 gap)

        // Динамічна ширина слотів під ширину екрану
        int availW = Math.min(310, this.width - 40);
        int dynSlotW = Math.max(40, (availW - 3 * SLOT_GAP) / 4);
        int totalSlotsW = 4 * dynSlotW + 3 * SLOT_GAP;
        int slotsLeft = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (dynSlotW + SLOT_GAP);
            final int si = i;

            // Вибрати предмет через меню
            final ItemStack curShopItem = items.get(si);
            // Initialise pending count BEFORE building the button so the picker
            // receives the correct preselected count even on first init.
            if (pendingCounts[si] < 1) {
                pendingCounts[si] = curShopItem.isEmpty() ? 1 : Math.max(1, curShopItem.getCount());
            }
            String shopSlotLbl = curShopItem.isEmpty() ? I18n.get("wavedefense.shop.slot_empty")
                : "§a✓ " + (curShopItem.getHoverName().getString().length() > 8
                    ? curShopItem.getHoverName().getString().substring(0, 7) + "…"
                    : curShopItem.getHoverName().getString());
            this.addButton(new Button(xPos, startY, dynSlotW, SLOT_H, new StringTextComponent(shopSlotLbl), button -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        items.set(si, stack);
                        // Adopt the click-counted count from the picker so the per-slot
                        // ×N TextFieldWidget below shows it. Picker guarantees count is in [1,64].
                        pendingCounts[si] = Math.max(1, Math.min(64, stack.getCount()));
                        init();
                    }, curShopItem.isEmpty()
                            ? curShopItem
                            : withCount(curShopItem, Math.max(1, pendingCounts[si]))))));

            // "З руки"
            this.addButton(new Button(xPos, startY + SLOT_H + 2, dynSlotW, SLOT_H, new StringTextComponent("§7✋"), button -> {
                        if (minecraft.player != null) {
                            net.minecraft.item.ItemStack held = minecraft.player.getMainHandItem();
                            if (!held.isEmpty()) { items.set(si, held.copy()); init(); }
                        }
                    })
            )/* setTooltip omitted on 1.16.5 */;

            // Очистити
            this.addButton(new Button(xPos, startY + (SLOT_H + 2) * 2, dynSlotW, SLOT_H, new TranslationTextComponent("wavedefense.button.clear_item"), button -> { items.set(si, ItemStack.EMPTY); pendingCounts[si] = 1; init(); }));

            // ── Кількість предмета у слоті (×N) — buffer initialised above ──
            int countRowY = startY + (SLOT_H + 2) * 3;
            // Label "×" зліва (вузький)
            this.addButton(new Button(xPos, countRowY, 12, SLOT_H, new StringTextComponent("§7×"), b -> {})).active = false;
            // TextFieldWidget праворуч
            TextFieldWidget countBox = new TextFieldWidget(this.font, xPos + 14, countRowY,
                    Math.max(20, dynSlotW - 14), SLOT_H,
                    new TranslationTextComponent("wavedefense.shop.item_count"));
            countBox.setMaxLength(2);
            countBox.setValue(String.valueOf(pendingCounts[si]));
            countBox.setResponder(s -> {
                try {
                    int v = Integer.parseInt(s.trim());
                    pendingCounts[si] = Math.max(1, Math.min(64, v));
                } catch (NumberFormatException ignored) {}
            });
            this.addButton(countBox)
                /* setTooltip omitted on 1.16.5 */;
        }

        startY += (SLOT_H + 2) * 3 + SLOT_H + 6;

        int labelW = 155;
        int fieldW = 80;
        int fieldX = cx + 10;

        this.addButton(new Button(cx - 155, startY, labelW, 18, new TranslationTextComponent("wavedefense.auto.ціна_купівлі_поінти_c0c2e7a9"), button -> {})).active = false;

        // Ініціалізувати буфери лише один раз (при першому init())
        if (pendingBuyPrice < 0) {
            pendingBuyPrice = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getBuyPrice() : 0;
        }
        if (pendingSellPrice < 0) {
            pendingSellPrice = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getSellPrice() : 0;
        }

        buyPriceInput = new TextFieldWidget(this.font, fieldX, startY, fieldW, 20, new TranslationTextComponent("wavedefense.auto.ціна_купівлі_31506e84"));
        buyPriceInput.setValue(String.valueOf(pendingBuyPrice));
        buyPriceInput.setResponder(s -> {
            try { pendingBuyPrice = Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        });
        this.addButton(buyPriceInput);

        startY += 26;

        this.addButton(new Button(cx - 155, startY, 280, 18, new TranslationTextComponent("wavedefense.auto.ціна_продажу_0_без_продажу_b229a3b3"), button -> {})).active = false;

        startY += 20;

        sellPriceInput = new TextFieldWidget(this.font, cx - 155, startY, fieldW, 20, new TranslationTextComponent("wavedefense.auto.ціна_продажу_27f60154"));
        sellPriceInput.setValue(String.valueOf(pendingSellPrice));
        sellPriceInput.setResponder(s -> {
            try { pendingSellPrice = Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        });
        this.addButton(sellPriceInput);

        // Категорія
        startY += 26;
        this.addButton(new Button(cx - 155, startY, 140, 18, new TranslationTextComponent("wavedefense.auto.категорія_товару_0d0ab0fe"), button -> {})).active = false;
        ShopItem.ShopCategory[] cats = ShopItem.ShopCategory.values();
        int catX = cx - 155;
        for (ShopItem.ShopCategory cat : cats) {
            if (cat == ShopItem.ShopCategory.ALL) continue;
            String lbl = (cat == selectedCategory ? "§e" : "§7") + I18n.get(cat.label);
            final ShopItem.ShopCategory fc = cat;
            this.addButton(new Button(catX, startY + 20, 58, 16, new StringTextComponent(lbl), b -> { selectedCategory = fc; init(); }));
            catX += 62;
        }
        startY += 40; // label(18) + gap(2) + buttons(16) + gap(4)

        // ── NBT-перевірка при продажу ───────────────────────────────────
        boolean requireNbt = (itemIndex >= 0 && itemIndex < getSourceList().size())
            ? getSourceList().get(itemIndex).isRequireNbtMatch() : false;
        this.addButton(new Button(cx - 155, startY, 200, 16, ((requireNbt) ? new TranslationTextComponent("wavedefense.auto.перевіряти_nbt_при_продажу_7fcb81ad") : new TranslationTextComponent("wavedefense.auto.перевіряти_nbt_при_продажу_58fb4deb")), b -> {
                if (itemIndex >= 0 && itemIndex < getSourceList().size()) {
                    getSourceList().get(itemIndex).setRequireNbtMatch(!getSourceList().get(itemIndex).isRequireNbtMatch());
                    init();
                }
            }))
        /* setTooltip omitted on 1.16.5 */;

        if (requireNbt) {
            startY += 20;
            this.addButton(new Button(cx - 155, startY, 310, 14, new TranslationTextComponent("wavedefense.auto.snbt_рядок_наприклад_display_nam_b6d8c691"), b -> {})).active = false;
            startY += 16;
            String curNbt = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getNbtRequiredTag() : "";
            TextFieldWidget nbtInput = new TextFieldWidget(this.font, cx - 155, startY, 310, 16, new TranslationTextComponent("wavedefense.auto.nbt_ab2a3c11"));
            nbtInput.setMaxLength(512);
            nbtInput.setValue(curNbt);
            final int fi = itemIndex;
            nbtInput.setResponder(s -> {
                if (fi >= 0 && fi < getSourceList().size())
                    getSourceList().get(fi).setNbtRequiredTag(s);
            });
            this.addButton(nbtInput);
            startY += 18;
            this.addButton(new Button(cx - 155, startY, 310, 11, new TranslationTextComponent("wavedefense.auto.ℹ_часткова_відповідність_всі_вка_acbfc949"), b -> {})).active = false;
            startY += 14;
        } else {
            startY += 20;
        }

        // Тригер доступності товару
        startY += 6;
        com.wavedefense.data.WaveTrigger avTrig = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getAvailabilityTrigger() : null;
        String avTrigLbl = avTrig == null
                ? "§7☐ " + I18n.get("wavedefense.shop.availability.always_label")
                : "§a☑ " + I18n.get("wavedefense.shop.availability.trigger_prefix") + " §e" + I18n.get(avTrig.label);
        this.addButton(new Button(cx - 155, startY, 310, 16, new StringTextComponent(avTrigLbl.length() > 42 ? avTrigLbl.substring(0, 40) + "…" : avTrigLbl), b -> this.minecraft.setScreen(new ShopAvailabilityScreen(this, location, shopPoint, itemIndex))));
        startY += 22;

        contentHeight = startY + scrollOffset + 4;
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            init();
            return;
        }

        // Зберегти / Скасувати — завжди видимі внизу (поза scissor)
        this.addButton(new Button(cx - 110, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.save"), button -> save()));

        this.addButton(new Button(cx + 10, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), button -> this.minecraft.setScreen(parent)));
    }

    /** Returns a copy of {@code st} with count set (≥1). */
    private static ItemStack withCount(ItemStack st, int count) {
        ItemStack copy = st.copy();
        copy.setCount(Math.max(1, count));
        return copy;
    }

    private void save() {
        // Apply per-slot count from pendingCounts buffer before filtering empties.
        for (int i = 0; i < items.size() && i < pendingCounts.length; i++) {
            ItemStack st = items.get(i);
            if (!st.isEmpty() && pendingCounts[i] >= 1) {
                st.setCount(Math.min(64, pendingCounts[i]));
            }
        }
        List<ItemStack> finalItems = items.stream().filter(i -> !i.isEmpty()).collect(java.util.stream.Collectors.toList());
        if (finalItems.isEmpty()) {
            // В2: show error instead of silently ignoring the save
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.shop.error_no_items"), true);
            }
            return;
        }
        try {
            // Читаємо з буферів (оновлюються setResponder, безпечні після rebuildWidgets)
            int buyPrice  = pendingBuyPrice  >= 0 ? pendingBuyPrice  : Integer.parseInt(buyPriceInput.getValue());
            int sellPrice = pendingSellPrice >= 0 ? pendingSellPrice : Integer.parseInt(sellPriceInput.getValue());
            ShopItem shopItem = new ShopItem(finalItems, buyPrice, sellPrice);
            shopItem.setCategory(selectedCategory);
            List<com.wavedefense.data.ShopItem> list = getSourceList();
            // Зберігаємо тригер доступності (якщо вже є)
            if (itemIndex >= 0 && itemIndex < list.size()) {
                ShopItem existing = list.get(itemIndex);
                shopItem.setAvailabilityTrigger(existing.getAvailabilityTrigger());
                shopItem.setAvailabilityWave(existing.getAvailabilityWave());
                shopItem.setAvailabilityItemId(existing.getAvailabilityItemId());
                shopItem.setRequireNbtMatch(existing.isRequireNbtMatch());
                shopItem.setNbtRequiredTag(existing.getNbtRequiredTag());
                list.set(itemIndex, shopItem);
            } else {
                list.add(shopItem);
            }
            // Зберігаємо на сервері
            PacketHandler.sendToServer(new UpdateLocationPacket(location));
            this.minecraft.setScreen(parent);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    private int getClipBottom() {
        return this.height - 28;
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - Math.max(1, getClipBottom() - CLIP_TOP));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int clipBot = getClipBottom();
        if (mouseY < CLIP_TOP || mouseY > clipBot) return super.mouseScrolled(mouseX, mouseY, delta);
        int oldOffset = scrollOffset;
        int step = 18;
        if (delta > 0) scrollOffset = Math.max(0, scrollOffset - step);
        else scrollOffset = Math.min(getMaxScroll(), scrollOffset + step);
        if (oldOffset != scrollOffset) {
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int clipBot = getClipBottom();
        for (Object child : this.children()) {
            if (child instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) child;
                if (w.y >= clipBot) {
                    if (((net.minecraft.client.gui.IGuiEventListener) child).mouseClicked(mx, my, button)) {
                        this.setFocused((net.minecraft.client.gui.IGuiEventListener) child);
                        if (button == 0) this.setDragging(true);
                        return true;
                    }
                }
            }
        }
        if (my < CLIP_TOP || my > clipBot) return false;
        for (Object child : this.children()) {
            if (child instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) child;
                if (w.y >= clipBot) continue;
                if (w.y + w.getHeight() <= CLIP_TOP || w.y >= clipBot) continue;
            }
            if (((net.minecraft.client.gui.IGuiEventListener) child).mouseClicked(mx, my, button)) {
                this.setFocused((net.minecraft.client.gui.IGuiEventListener) child);
                if (button == 0) this.setDragging(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.fill(g, this.width / 2 - 42, 21, this.width / 2 + 42, 22, GuiTheme.ACCENT);

        // Scissor: вміст між заголовком (26) і нижніми кнопками (height-28)
        int clipBot = this.height - 28;
        GuiTheme.renderContentFrame(g, 8, CLIP_TOP - 4, this.width - 8, clipBot + 4);
        ScissorHelper.enable(0, CLIP_TOP, this.width, Math.max(1, clipBot - CLIP_TOP));
        super.render(g, mouseX, mouseY, partialTick);
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Re-render нижні кнопки поверх scissor
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y >= clipBot) {
                    w.render(g, mouseX, mouseY, partialTick);
                }
            }
        }

        // Іконки предметів — між заголовком і кнопками слотів
        int cx2 = this.width / 2;
        int availW2 = Math.min(310, this.width - 40);
        int dynSlotW2 = Math.max(40, (availW2 - 3 * SLOT_GAP) / 4);
        int totalSlotsW = 4 * dynSlotW2 + 3 * SLOT_GAP;
        int slotsLeft = cx2 - totalSlotsW / 2;
        int iconY = iconRowY; // встановлено в init() // startY(30) + label(14) = 44, саме тут розміщено іконки
        ItemStack tooltipItem = null;
        ScissorHelper.enable(0, CLIP_TOP, this.width, Math.max(1, clipBot - CLIP_TOP)); // second pass: item icons
        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (dynSlotW2 + SLOT_GAP);
            ItemStack item = items.get(i);
            int iconX = xPos + (dynSlotW2 - 16) / 2;
            com.wavedefense.gui.GuiCompat.fill(g, iconX - 1, iconY - 1, iconX + 17, iconY + 17, GuiTheme.BORDER);
            com.wavedefense.gui.GuiCompat.fill(g, iconX,     iconY,     iconX + 16, iconY + 16, GuiTheme.PANEL_DARK);
            com.wavedefense.gui.GuiCompat.renderItem(g, item, iconX, iconY);
            com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, item, iconX, iconY);
            if (!item.isEmpty() && mouseY >= CLIP_TOP && mouseY <= clipBot
                    && mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                tooltipItem = item;
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();
        if (tooltipItem != null) com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, tooltipItem, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
