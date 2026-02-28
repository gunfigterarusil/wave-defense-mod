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
 * Екран налаштування тригера доступності товару в магазині.
 * Дозволяє вибрати: завжди / з початку хвилі / на хвилі N / якщо є предмет
 */
public class ShopAvailabilityScreen extends Screen {

    private final Screen   parent;
    private final Location location;
    private final int      itemIndex;

    private WaveTrigger selected;
    private EditBox     waveInput;
    private EditBox     itemInput;

    // SHOP-специфічні тригери
    private static final WaveTrigger[] SHOP_TRIGGERS = {
        null, // null = завжди
        WaveTrigger.SHOP_LOCATION_START,
        WaveTrigger.SHOP_WAVE_START,
        WaveTrigger.SHOP_WAVE_N,
        WaveTrigger.SHOP_PLAYER_HAS_ITEM
    };

    public ShopAvailabilityScreen(Screen parent, Location location, int itemIndex) {
        super(Component.literal("🛒 Тригер доступності товару"));
        this.parent    = parent;
        this.location  = location;
        this.itemIndex = itemIndex;

        if (itemIndex >= 0 && itemIndex < location.getShopItems().size()) {
            this.selected = location.getShopItems().get(itemIndex).getAvailabilityTrigger();
        }
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 40;

        this.addRenderableWidget(Button.builder(
            Component.literal("§7Вибери умову появи товару в магазині:"), b -> {}
        ).bounds(cx - 160, y, 320, 14).build()).active = false;
        y += 20;

        ShopItem item = (itemIndex >= 0 && itemIndex < location.getShopItems().size())
                ? location.getShopItems().get(itemIndex) : null;

        // Варіант: Завжди (null trigger)
        boolean isAlways = (selected == null);
        this.addRenderableWidget(Button.builder(
            Component.literal(isAlways ? "§a§l▶ Завжди доступний" : "§7  Завжди доступний"),
            b -> { selected = null; rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7Товар відображається у магазині завжди")));
        y += 22;

        // Варіант: З початку локації (= завжди, але візуально зрозуміліше)
        boolean isLocStart = (selected == WaveTrigger.SHOP_LOCATION_START);
        this.addRenderableWidget(Button.builder(
            Component.literal(isLocStart ? "§a§l▶ " + WaveTrigger.SHOP_LOCATION_START.label
                                         : "§7  " + WaveTrigger.SHOP_LOCATION_START.label),
            b -> { selected = WaveTrigger.SHOP_LOCATION_START; rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7" + WaveTrigger.SHOP_LOCATION_START.tooltip)));
        y += 22;

        // Варіант: З початку хвилі
        boolean isWaveStart = (selected == WaveTrigger.SHOP_WAVE_START);
        this.addRenderableWidget(Button.builder(
            Component.literal(isWaveStart ? "§a§l▶ " + WaveTrigger.SHOP_WAVE_START.label
                                          : "§7  " + WaveTrigger.SHOP_WAVE_START.label),
            b -> { selected = WaveTrigger.SHOP_WAVE_START; rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7" + WaveTrigger.SHOP_WAVE_START.tooltip)));
        y += 22;

        // Варіант: Тільки на хвилі N
        boolean isWaveN = (selected == WaveTrigger.SHOP_WAVE_N);
        this.addRenderableWidget(Button.builder(
            Component.literal(isWaveN ? "§a§l▶ " + WaveTrigger.SHOP_WAVE_N.label
                                      : "§7  " + WaveTrigger.SHOP_WAVE_N.label),
            b -> { selected = WaveTrigger.SHOP_WAVE_N; rebuildWidgets(); }
        ).bounds(cx - 160, y, 280, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7" + WaveTrigger.SHOP_WAVE_N.tooltip)));

        if (isWaveN) {
            this.addRenderableWidget(Button.builder(Component.literal("§7Хвиля:"), b -> {})
            .bounds(cx + 124, y, 50, 18).build()).active = false;
            waveInput = new EditBox(this.font, cx + 176, y, 40, 18, Component.literal("1"));
            waveInput.setValue(item != null ? String.valueOf(item.getAvailabilityWave()) : "1");
            waveInput.setMaxLength(3);
            this.addRenderableWidget(waveInput);
        }
        y += 22;

        // Варіант: Якщо є предмет
        boolean isHasItem = (selected == WaveTrigger.SHOP_PLAYER_HAS_ITEM);
        this.addRenderableWidget(Button.builder(
            Component.literal(isHasItem ? "§a§l▶ " + WaveTrigger.SHOP_PLAYER_HAS_ITEM.label
                                        : "§7  " + WaveTrigger.SHOP_PLAYER_HAS_ITEM.label),
            b -> { selected = WaveTrigger.SHOP_PLAYER_HAS_ITEM; rebuildWidgets(); }
        ).bounds(cx - 160, y, 320, 18).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7" + WaveTrigger.SHOP_PLAYER_HAS_ITEM.tooltip)));
        y += 22;

        if (isHasItem) {
            this.addRenderableWidget(Button.builder(Component.literal("§7Предмет (registry id):"), b -> {})
            .bounds(cx - 160, y, 140, 16).build()).active = false;
            itemInput = new EditBox(this.font, cx - 16, y, 176, 16, Component.literal("minecraft:diamond"));
            itemInput.setValue(item != null && !item.getAvailabilityItemId().isEmpty()
                    ? item.getAvailabilityItemId() : "minecraft:diamond");
            itemInput.setMaxLength(60);
            this.addRenderableWidget(itemInput);
            y += 20;
        }

        // Кнопки
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓ Зберегти"),
            b -> save()
        ).bounds(cx - 110, this.height - 26, 100, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("Скасувати"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 26, 100, 20).build());
    }

    private void save() {
        if (itemIndex < 0 || itemIndex >= location.getShopItems().size()) {
            this.minecraft.setScreen(parent);
            return;
        }
        ShopItem item = location.getShopItems().get(itemIndex);
        item.setAvailabilityTrigger(selected);
        if (waveInput != null) {
            try { item.setAvailabilityWave(Integer.parseInt(waveInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (itemInput != null) {
            item.setAvailabilityItemId(itemInput.getValue().trim());
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "§6🛒 Тригер доступності товару", this.width / 2, 16, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
