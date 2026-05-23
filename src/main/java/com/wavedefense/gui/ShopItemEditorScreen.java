package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.wavedefense.data.ShopItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Редактор товару в магазині.
 * Предмети тепер вибираються через ItemSelectionScreen (не з руки).
 * Підказки при наведенні на кнопки.
 */
public class ShopItemEditorScreen extends Screen {
    private final Location location;
    private final int itemIndex;
    private final Screen parent;
    private List<ItemStack> items = new ArrayList<>();
    private EditBox buyPriceInput;
    private EditBox sellPriceInput;
    private ShopItem.ShopCategory selectedCategory = ShopItem.ShopCategory.OTHER;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private int iconRowY = 44; // Y-координата рядку іконок (встановлюється в init)

    // Якщо не null — редагуємо товари точки магазину (не глобального списку)
    private final com.wavedefense.data.ShopPoint shopPoint;

    // Буфери ціни — зберігаються між rebuildWidgets() щоб не губити введені значення
    private int pendingBuyPrice  = -1; // -1 = ще не ініціалізовано
    private int pendingSellPrice = -1;

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
        super(itemIndex >= 0 ? Component.translatable("wavedefense.title.edit_item") : Component.translatable("wavedefense.title.new_item"));
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

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.предмети_товару_до_4_слотів_16b40145"),
                button -> {}
        ).bounds(cx - 155, startY, 220, 14).build()).active = false;

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
            String shopSlotLbl = curShopItem.isEmpty() ? "§8[Порожньо]"
                : "§a✓ " + (curShopItem.getHoverName().getString().length() > 8
                    ? curShopItem.getHoverName().getString().substring(0, 7) + "…"
                    : curShopItem.getHoverName().getString());
            this.addRenderableWidget(Button.builder(
                    Component.literal(shopSlotLbl),
                    button -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        items.set(si, stack);
                        rebuildWidgets();
                    }, curShopItem))
            ).bounds(xPos, startY, dynSlotW, SLOT_H).build());

            // "З руки"
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7✋"),
                    button -> {
                        if (minecraft.player != null) {
                            net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
                            if (!held.isEmpty()) { items.set(si, held.copy()); rebuildWidgets(); }
                        }
                    }
            ).bounds(xPos, startY + SLOT_H + 2, dynSlotW, SLOT_H).build()
            ).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.translatable("wavedefense.auto.взяти_предмет_з_основної_руки_c2a7a392")));

            // Очистити
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.clear_item"),
                    button -> { items.set(si, ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(xPos, startY + (SLOT_H + 2) * 2, dynSlotW, SLOT_H).build());
        }

        startY += (SLOT_H + 2) * 2 + SLOT_H + 6;

        int labelW = 155;
        int fieldW = 80;
        int fieldX = cx + 10;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ціна_купівлі_поінти_c0c2e7a9"),
                button -> {}
        ).bounds(cx - 155, startY, labelW, 18).build()).active = false;

        // Ініціалізувати буфери лише один раз (при першому init())
        if (pendingBuyPrice < 0) {
            pendingBuyPrice = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getBuyPrice() : 0;
        }
        if (pendingSellPrice < 0) {
            pendingSellPrice = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getSellPrice() : 0;
        }

        buyPriceInput = new EditBox(this.font, fieldX, startY, fieldW, 20, Component.translatable("wavedefense.auto.ціна_купівлі_31506e84"));
        buyPriceInput.setValue(String.valueOf(pendingBuyPrice));
        buyPriceInput.setResponder(s -> {
            try { pendingBuyPrice = Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(buyPriceInput);

        startY += 26;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ціна_продажу_0_без_продажу_b229a3b3"),
                button -> {}
        ).bounds(cx - 155, startY, 280, 18).build()).active = false;

        startY += 20;

        sellPriceInput = new EditBox(this.font, cx - 155, startY, fieldW, 20, Component.translatable("wavedefense.auto.ціна_продажу_27f60154"));
        sellPriceInput.setValue(String.valueOf(pendingSellPrice));
        sellPriceInput.setResponder(s -> {
            try { pendingSellPrice = Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(sellPriceInput);

        // Категорія
        startY += 26;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.категорія_товару_0d0ab0fe"), button -> {}
        ).bounds(cx - 155, startY, 140, 18).build()).active = false;
        ShopItem.ShopCategory[] cats = ShopItem.ShopCategory.values();
        int catX = cx - 155;
        for (ShopItem.ShopCategory cat : cats) {
            if (cat == ShopItem.ShopCategory.ALL) continue;
            String lbl = (cat == selectedCategory ? "§e" : "§7") + cat.label;
            final ShopItem.ShopCategory fc = cat;
            this.addRenderableWidget(Button.builder(
                Component.literal(lbl),
                b -> { selectedCategory = fc; rebuildWidgets(); }
            ).bounds(catX, startY + 20, 58, 16).build());
            catX += 62;
        }
        startY += 40; // label(18) + gap(2) + buttons(16) + gap(4)

        // ── NBT-перевірка при продажу ───────────────────────────────────
        boolean requireNbt = (itemIndex >= 0 && itemIndex < getSourceList().size())
            ? getSourceList().get(itemIndex).isRequireNbtMatch() : false;
        this.addRenderableWidget(Button.builder(
            ((requireNbt) ? Component.translatable("wavedefense.auto.перевіряти_nbt_при_продажу_7fcb81ad") : Component.translatable("wavedefense.auto.перевіряти_nbt_при_продажу_58fb4deb")),
            b -> {
                if (itemIndex >= 0 && itemIndex < getSourceList().size()) {
                    getSourceList().get(itemIndex).setRequireNbtMatch(!getSourceList().get(itemIndex).isRequireNbtMatch());
                    rebuildWidgets();
                }
            }
        ).bounds(cx - 155, startY, 200, 16).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            net.minecraft.network.chat.Component.translatable("wavedefense.auto.продаж_можливий_лише_якщо_предме_d795cdea")));

        if (requireNbt) {
            startY += 20;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.snbt_рядок_наприклад_display_nam_b6d8c691"), b -> {}
            ).bounds(cx - 155, startY, 310, 14).build()).active = false;
            startY += 16;
            String curNbt = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getNbtRequiredTag() : "";
            EditBox nbtInput = new EditBox(this.font, cx - 155, startY, 310, 16, Component.translatable("wavedefense.auto.nbt_ab2a3c11"));
            nbtInput.setMaxLength(512);
            nbtInput.setValue(curNbt);
            final int fi = itemIndex;
            nbtInput.setResponder(s -> {
                if (fi >= 0 && fi < getSourceList().size())
                    getSourceList().get(fi).setNbtRequiredTag(s);
            });
            this.addRenderableWidget(nbtInput);
            startY += 18;
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ℹ_часткова_відповідність_всі_вка_acbfc949"), b -> {}
            ).bounds(cx - 155, startY, 310, 11).build()).active = false;
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
        this.addRenderableWidget(Button.builder(
                Component.literal(avTrigLbl.length() > 42 ? avTrigLbl.substring(0, 40) + "…" : avTrigLbl),
                b -> this.minecraft.setScreen(new ShopAvailabilityScreen(this, location, shopPoint, itemIndex))
        ).bounds(cx - 155, startY, 310, 16).build());
        startY += 22;

        contentHeight = startY + scrollOffset + 4;
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            rebuildWidgets();
            return;
        }

        // Зберегти / Скасувати — завжди видимі внизу (поза scissor)
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.save"), button -> save()
        ).bounds(cx - 110, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 28, 100, 20).build());
    }

    private void save() {
        List<ItemStack> finalItems = items.stream().filter(i -> !i.isEmpty()).collect(Collectors.toList());
        if (finalItems.isEmpty()) {
            // В2: show error instead of silently ignoring the save
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.shop.error_no_items"), true);
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
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int clipBot = getClipBottom();
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= clipBot) {
                if (child.mouseClicked(mx, my, button)) {
                    this.setFocused(child);
                    if (button == 0) this.setDragging(true);
                    return true;
                }
            }
        }
        if (my < CLIP_TOP || my > clipBot) return false;
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() >= clipBot) continue;
                if (w.getY() + w.getHeight() <= CLIP_TOP || w.getY() >= clipBot) continue;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Scissor: вміст між заголовком (26) і нижніми кнопками (height-28)
        int clipBot = this.height - 28;
        ScissorHelper.enable(0, CLIP_TOP, this.width, Math.max(1, clipBot - CLIP_TOP));
        super.render(g, mouseX, mouseY, partialTick);
        ScissorHelper.disable();

        // Re-render нижні кнопки поверх scissor
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= clipBot) {
                w.render(g, mouseX, mouseY, partialTick);
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
        ScissorHelper.enable(0, CLIP_TOP, this.width, Math.max(1, clipBot - CLIP_TOP));
        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (dynSlotW2 + SLOT_GAP);
            ItemStack item = items.get(i);
            int iconX = xPos + (dynSlotW2 - 16) / 2;
            g.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, 0xFF555555);
            g.fill(iconX,     iconY,     iconX + 16, iconY + 16, 0xFF222222);
            g.renderItem(item, iconX, iconY);
            g.renderItemDecorations(this.font, item, iconX, iconY);
            if (!item.isEmpty() && mouseY >= CLIP_TOP && mouseY <= clipBot
                    && mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                tooltipItem = item;
            }
        }
        ScissorHelper.disable();
        if (tooltipItem != null) g.renderTooltip(this.font, tooltipItem, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
