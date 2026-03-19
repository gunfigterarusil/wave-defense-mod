package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.wavedefense.data.ShopItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
    private int iconRowY = 44; // Y-координата рядку іконок (встановлюється в init)

    // Якщо не null — редагуємо товари точки магазину (не глобального списку)
    private final com.wavedefense.data.ShopPoint shopPoint;

    private static final int SLOT_W = 70;
    private static final int SLOT_H = 16;
    private static final int SLOT_GAP = 6;

    // ── Конструктор для глобального магазину локації ─────────────────
    public ShopItemEditorScreen(Location location, int itemIndex, Screen parent) {
        this(location, null, itemIndex, parent);
    }

    // ── Конструктор для точки магазину ───────────────────────────────
    public ShopItemEditorScreen(Location location, com.wavedefense.data.ShopPoint shopPoint, int itemIndex, Screen parent) {
        super(Component.literal(itemIndex >= 0 ? "Редагування товару" : "Новий товар"));
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
        int startY = 30;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети товару (до 4 слотів):"),
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
            int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
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
                net.minecraft.network.chat.Component.literal("§7Взяти предмет з основної руки")));

            // Очистити
            this.addRenderableWidget(Button.builder(
                    Component.literal("§cОчистити"),
                    button -> { items.set(si, ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(xPos, startY + (SLOT_H + 2) * 2, dynSlotW, SLOT_H).build());
        }

        startY += (SLOT_H + 2) * 2 + SLOT_H + 6;

        int labelW = 155;
        int fieldW = 80;
        int fieldX = cx + 10;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ціна купівлі (поінти):"),
                button -> {}
        ).bounds(cx - 155, startY, labelW, 18).build()).active = false;

        buyPriceInput = new EditBox(this.font, fieldX, startY, fieldW, 20, Component.literal("Ціна купівлі"));
        buyPriceInput.setValue(itemIndex >= 0 && itemIndex < getSourceList().size() ? String.valueOf(getSourceList().get(itemIndex).getBuyPrice()) : "0");
        this.addRenderableWidget(buyPriceInput);

        startY += 26;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ціна продажу (0 = без продажу):"),
                button -> {}
        ).bounds(cx - 155, startY, 280, 18).build()).active = false;

        startY += 20;

        sellPriceInput = new EditBox(this.font, cx - 155, startY, fieldW, 20, Component.literal("Ціна продажу"));
        sellPriceInput.setValue(itemIndex >= 0 && itemIndex < getSourceList().size() ? String.valueOf(getSourceList().get(itemIndex).getSellPrice()) : "0");
        this.addRenderableWidget(sellPriceInput);

        // Категорія
        startY += 26;
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Категорія товару:"), button -> {}
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
            Component.literal(requireNbt ? "§a☑ Перевіряти NBT при продажу" : "§7☐ Перевіряти NBT при продажу"),
            b -> {
                if (itemIndex >= 0 && itemIndex < getSourceList().size()) {
                    getSourceList().get(itemIndex).setRequireNbtMatch(!getSourceList().get(itemIndex).isRequireNbtMatch());
                    rebuildWidgets();
                }
            }
        ).bounds(cx - 155, startY, 200, 16).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            net.minecraft.network.chat.Component.literal("§7Продаж можливий лише якщо предмет у гравця\n§7має NBT теги що збігаються з вказаними нижче")));

        if (requireNbt) {
            startY += 20;
            this.addRenderableWidget(Button.builder(
                Component.literal("§7SNBT-рядок (наприклад: {display:{Name:\"...\"}}):"), b -> {}
            ).bounds(cx - 155, startY, 310, 14).build()).active = false;
            startY += 16;
            String curNbt = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getNbtRequiredTag() : "";
            EditBox nbtInput = new EditBox(this.font, cx - 155, startY, 310, 16, Component.literal("NBT"));
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
                Component.literal("§8ℹ Часткова відповідність: всі вказані ключі повинні збігатись"), b -> {}
            ).bounds(cx - 155, startY, 310, 11).build()).active = false;
            startY += 14;
        } else {
            startY += 20;
        }

        // Тригер доступності товару
        startY += 6;
        com.wavedefense.data.WaveTrigger avTrig = (itemIndex >= 0 && itemIndex < getSourceList().size())
                ? getSourceList().get(itemIndex).getAvailabilityTrigger() : null;
        String avTrigLbl = avTrig == null ? "§7☐ Тригер доступності: Завжди"
                : "§a☑ Тригер: §e" + avTrig.label;
        this.addRenderableWidget(Button.builder(
                Component.literal(avTrigLbl.length() > 42 ? avTrigLbl.substring(0, 40) + "…" : avTrigLbl),
                b -> this.minecraft.setScreen(new ShopAvailabilityScreen(this, location, shopPoint, itemIndex))
        ).bounds(cx - 155, startY, 310, 16).build());
        startY += 22;

        // Зберегти / Скасувати — завжди видимі внизу (поза scissor)
        this.addRenderableWidget(Button.builder(
                Component.literal("§aЗберегти"), button -> save()
        ).bounds(cx - 110, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), button -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 28, 100, 20).build());
    }

    private void save() {
        List<ItemStack> finalItems = items.stream().filter(i -> !i.isEmpty()).collect(Collectors.toList());
        if (finalItems.isEmpty()) return;
        try {
            int buyPrice = Integer.parseInt(buyPriceInput.getValue());
            int sellPrice = Integer.parseInt(sellPriceInput.getValue());
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Scissor: вміст між заголовком (26) і нижніми кнопками (height-28)
        int clipTop = 26;
        int clipBot = this.height - 28;
        ScissorHelper.enable(0, clipTop, this.width, Math.max(1, clipBot - clipTop));
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
        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
            ItemStack item = items.get(i);
            int iconX = xPos + (dynSlotW2 - 16) / 2;
            g.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, 0xFF555555);
            g.fill(iconX,     iconY,     iconX + 16, iconY + 16, 0xFF222222);
            g.renderItem(item, iconX, iconY);
            g.renderItemDecorations(this.font, item, iconX, iconY);
            if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16) {
                tooltipItem = item;
            }
        }
        if (tooltipItem != null) g.renderTooltip(this.font, tooltipItem, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
