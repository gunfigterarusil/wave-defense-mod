package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.WaveTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Тригер доступності товару в магазині.
 * Одна колонка, scissor-прокрутка, per-trigger поля налаштувань.
 *
 * Тригери ВИКЛЮЧНО магазинні:
 *   null                    — Завжди доступний
 *   SHOP_LOCATION_START     — З початку локації
 *   SHOP_WAVE_START         — З початку кожної хвилі
 *   SHOP_WAVE_N             — Тільки на хвилі N  [поле: N]
 *   SHOP_PLAYER_HAS_ITEM    — Якщо є предмет     [поле: item id]
 */
public class ShopAvailabilityScreen extends Screen {

    private final Screen   parent;
    private final Location location;
    private final int      itemIndex;
    // Якщо не null — тригер редагується для товару точки магазину
    private final com.wavedefense.data.ShopPoint shopPoint;

    private WaveTrigger selected;
    private EditBox waveInput;
    private EditBox itemInput;

    // Scissor bounds
    private int scrollTop = 0;
    private int scrollBot = 0;
    private int scrollOffset = 0;
    private static final int BTN_H   = 22;
    private static final int BTN_GAP = 2;

    // Shop triggers in display order
    private static final WaveTrigger[] SHOP_TRIGGERS = {
        null,
        WaveTrigger.SHOP_LOCATION_START,
        WaveTrigger.SHOP_WAVE_START,
        WaveTrigger.SHOP_WAVE_N,
        WaveTrigger.SHOP_PLAYER_HAS_ITEM
    };

    public ShopAvailabilityScreen(Screen parent, Location location, int itemIndex) {
        this(parent, location, null, itemIndex);
    }

    public ShopAvailabilityScreen(Screen parent, Location location,
            com.wavedefense.data.ShopPoint shopPoint, int itemIndex) {
        super(Component.translatable("wavedefense.auto.тригер_доступності_товару_fcf1c654"));
        this.parent    = parent;
        this.location  = location;
        this.shopPoint = shopPoint;
        this.itemIndex = itemIndex;
        java.util.List<ShopItem> list = shopPoint != null ? shopPoint.getItems() : location.getShopItems();
        if (itemIndex >= 0 && itemIndex < list.size()) {
            this.selected = list.get(itemIndex).getAvailabilityTrigger();
        }
    }

    private java.util.List<ShopItem> getSourceList() {
        return shopPoint != null ? shopPoint.getItems() : location.getShopItems();
    }

    @Override
    protected void init() {
        super.init();
        waveInput = null; itemInput = null;

        int cx   = this.width / 2;
        int btnW = Math.min(310, this.width - 30);
        int y    = 36;

        // Підказка
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.оберіть_умову_появи_товару_в_маг_7a185e1b"), b -> {}
        ).bounds(cx - btnW / 2, y, btnW, 14).build()).active = false;
        y += 18;

        scrollTop = y; // ── верхня межа scrollable зони

        // Висота per-trigger полів (налаштування під списком)
        int perTrigH = 0;
        if (selected == WaveTrigger.SHOP_WAVE_N)          perTrigH = 24;
        if (selected == WaveTrigger.SHOP_PLAYER_HAS_ITEM) perTrigH = 42;
        scrollBot = this.height - perTrigH - 32;
        if (scrollBot < scrollTop + BTN_H) scrollBot = scrollTop + BTN_H;

        // Scroll
        int listH   = scrollBot - scrollTop;
        int visible = Math.max(1, listH / (BTN_H + BTN_GAP));
        int maxScr  = Math.max(0, SHOP_TRIGGERS.length - visible);
        if (scrollOffset > maxScr) scrollOffset = maxScr;

        // ── Список тригерів — одна колонка ───────────────────────────
        int ty = scrollTop;
        for (int i = scrollOffset; i < SHOP_TRIGGERS.length; i++) {
            if (ty + BTN_H > scrollBot) break;
            WaveTrigger t = SHOP_TRIGGERS[i];
            boolean isSel = (t == selected);
            String lbl;
            String tooltip;
            if (t == null) {
                lbl     = isSel ? "§a§l▶ ☑ Завжди доступний" : "§7  ☐ Завжди доступний";
                tooltip = "Товар відображається в магазині завжди";
            } else {
                lbl     = (isSel ? "§a§l▶ ☑ " : "§7  ☐ ") + t.label;
                tooltip = t.tooltip;
            }
            final WaveTrigger ft = t;
            Button btn = Button.builder(
                Component.literal(lbl),
                b -> { selected = ft; rebuildWidgets(); }
            ).bounds(cx - btnW / 2, ty, btnW, BTN_H).build();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7" + tooltip)));
            this.addRenderableWidget(btn);
            ty += BTN_H + BTN_GAP;
        }

        // ── Per-trigger налаштування ─────────────────────────────────
        int sy = scrollBot + 8;
        ShopItem item = (itemIndex >= 0 && itemIndex < getSourceList().size())
            ? getSourceList().get(itemIndex) : null;

        if (selected == WaveTrigger.SHOP_WAVE_N) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.тільки_на_хвилі_88738afb"), b -> {}
            ).bounds(cx - btnW / 2, sy, 160, 18).build()).active = false;
            waveInput = new EditBox(this.font, cx - btnW / 2 + 164, sy, 60, 18, Component.literal("1"));
            waveInput.setValue(item != null ? String.valueOf(item.getAvailabilityWave()) : "1");
            waveInput.setMaxLength(3);
            this.addRenderableWidget(waveInput);
            sy += 22;
        }

        if (selected == WaveTrigger.SHOP_PLAYER_HAS_ITEM) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.предмет_item_id_через_кому_1f69adb0"), b -> {}
            ).bounds(cx - btnW / 2, sy, btnW - 26, 14).build()).active = false;
            // Кнопка "з руки"
            this.addRenderableWidget(Button.builder(
                Component.literal("✋"),
                b -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null && itemInput != null) {
                        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
                        if (!held.isEmpty()) {
                            net.minecraft.resources.ResourceLocation rl =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem());
                            if (rl != null) {
                                String existing = itemInput.getValue().trim();
                                itemInput.setValue(existing.isEmpty() ? rl.toString() : existing + "," + rl);
                            }
                        }
                    }
                }
            ).bounds(cx + btnW / 2 - 24, sy, 24, 14).build()
            ).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("wavedefense.auto.додати_предмет_з_основної_руки_д_199913a1")));
            sy += 16;
            itemInput = new EditBox(this.font, cx - btnW / 2, sy, btnW, 18, Component.translatable("wavedefense.auto.id_87ea5dfc"));
            itemInput.setMaxLength(256);
            itemInput.setValue(item != null && !item.getAvailabilityItemId().isEmpty()
                ? item.getAvailabilityItemId() : "minecraft:diamond");
            itemInput.setHint(Component.translatable("wavedefense.auto.minecraft_diamond_f20ff3f7"));
            this.addRenderableWidget(itemInput);
            sy += 20;
        }

        // ── Кнопки ───────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.зберегти_617e5dc0"), b -> save()
        ).bounds(cx - 110, this.height - 26, 100, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.скасувати_8b4c2025"), b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 26, 100, 20).build());
    }

    // ── RENDER ────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.shop.availability_title"), this.width / 2, 14, 0xFFFFFF);

        // Статичні до списку
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() < scrollTop)
                w.render(g, mx, my, pt);
        }
        // Scissored список тригерів
        if (scrollTop < scrollBot) {
            ScissorHelper.enable(0, scrollTop, this.width, Math.max(1, scrollBot - scrollTop));
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() + w.getHeight() > scrollTop && w.getY() < scrollBot)
                    w.render(g, mx, my, pt);
            }
            ScissorHelper.disable();
        }
        // Статичні після списку (кнопки Зберегти/Скасувати)
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= scrollBot)
                w.render(g, mx, my, pt);
        }
    }

    // ── SCROLL ────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int visible = Math.max(1, (scrollBot - scrollTop) / (BTN_H + BTN_GAP));
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
        else if (delta < 0 && scrollOffset + visible < SHOP_TRIGGERS.length) { scrollOffset++; rebuildWidgets(); }
        return true;
    }

    // ── SAVE ──────────────────────────────────────────────────────────
    private void save() {
        if (itemIndex < 0 || itemIndex >= getSourceList().size()) {
            this.minecraft.setScreen(parent); return;
        }
        ShopItem item = getSourceList().get(itemIndex);
        item.setAvailabilityTrigger(selected);
        if (waveInput != null) {
            try { item.setAvailabilityWave(Integer.parseInt(waveInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (itemInput != null) item.setAvailabilityItemId(itemInput.getValue().trim());
        this.minecraft.setScreen(parent);
    }

    @Override public boolean keyPressed(int k, int s, int m) { return super.keyPressed(k, s, m); }
    @Override public boolean charTyped(char c, int m) { return super.charTyped(c, m); }
    @Override public boolean isPauseScreen() { return false; }
}
