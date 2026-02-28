package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор тригера для окремої хвилі.
 * ✓ Мультитригери (кілька умов одночасно — AND)
 * ✓ "Разово" — спрацювати лише один раз за сесію
 * ✓ "Активувати з хвилі N"
 * ✓ Налаштування предмета для PLAYER_HAS_ITEM
 * ✓ Перезарядка: Немає / Секунди / Хвилі
 * ✓ Мінімальна 5с пауза (автоматично в WaveManager)
 */
public class WaveTriggerEditorScreen extends Screen {

    private final Screen     parent;
    private final WaveConfig wave;
    private final int        waveIndex;
    private final boolean    isPvp;

    private EditBox cooldownValueInput;
    private EditBox customItemInput;
    private EditBox activateFromWaveInput;

    private WaveTrigger selected;
    private int triggerScrollOffset = 0;

    public WaveTriggerEditorScreen(Screen parent, WaveConfig wave, int waveIndex, boolean isPvp) {
        super(Component.literal("⚡ Тригер Хвилі " + (waveIndex + 1)));
        this.parent    = parent;
        this.wave      = wave;
        this.waveIndex = waveIndex;
        this.isPvp     = isPvp;
        this.selected  = wave.getTriggerType();
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 28;

        // ── Вмикач ─────────────────────────────────────────────────────────
        boolean enabled = wave.isTriggerEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(enabled ? "§a☑ Тригерна хвиля УВІМКНЕНА" : "§7☐ Тригерна хвиля вимкнена"),
            b -> { wave.setTriggerEnabled(!wave.isTriggerEnabled()); rebuildWidgets(); }
        ).bounds(cx - 155, y, 310, 18).build());

        if (!enabled) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§8Увімкніть щоб налаштувати"),
                b -> {}
            ).bounds(cx - 155, y + 24, 310, 14).build()).active = false;
            addBottomButtons(cx);
            return;
        }

        y += 24;

        // ── Список тригерів ─────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Головний тригер (обов'язковий):"), b -> {}
        ).bounds(cx - 155, y, 310, 13).build()).active = false;
        y += 15;

        List<WaveTrigger> available = new ArrayList<>();
        for (WaveTrigger t : WaveTrigger.values()) {
            if (isPvp && !t.pvp) continue;
            if (!isPvp && !t.pve) continue;
            // Не показувати shop-тригери в тригерних хвилях
            if (t.name().startsWith("SHOP_")) continue;
            available.add(t);
        }

        int total    = available.size();
        int colW     = Math.min(200, (this.width - 36) / 2 - 4);
        int bH       = 16;
        int col1X    = cx - colW - 2;
        int col2X    = cx + 2;
        int listH    = this.height - y - 140;
        int perCol   = Math.max(3, listH / (bH + 2));
        int visTotal = perCol * 2;

        int maxScroll = Math.max(0, total - visTotal);
        if (triggerScrollOffset > maxScroll) triggerScrollOffset = maxScroll;

        int col1Y = y, col2Y = y;
        boolean col1 = true;
        int shown = 0;
        for (int i = triggerScrollOffset; i < total && shown < visTotal; i++) {
            WaveTrigger t = available.get(i);
            boolean isSel  = (t == selected);
            boolean isExtra = wave.getExtraTriggers().contains(t);
            String lbl = isSel   ? "§e§l▶ " + t.label
                       : isExtra ? "§b§l+ " + t.label
                       : "§7  " + t.label;
            final WaveTrigger ft = t;
            Button btn = Button.builder(
                Component.literal(lbl),
                b -> {
                    if (ft == selected) {
                        // вже вибраний — нічого
                    } else if (wave.getExtraTriggers().contains(ft)) {
                        wave.removeExtraTrigger(ft);
                    } else {
                        // Shift-клік або другий клік — додати як доп. умову
                        // Одинарний клік — замінити головний тригер
                        selected = ft;
                        wave.setTriggerType(ft);
                    }
                    rebuildWidgets();
                }
            ).bounds(col1 ? col1X : col2X, col1 ? col1Y : col2Y, colW, bH).build();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7" + t.tooltip +
                    (isExtra ? "\n§b[Додаткова умова AND]" : ""))));
            this.addRenderableWidget(btn);
            shown++;
            if (col1) col1Y += bH + 2;
            else      col2Y += bH + 2;
            col1 = !col1;
        }

        // Скрол
        if (total > visTotal) {
            int sbX = this.width - 20;
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (triggerScrollOffset > 0) { triggerScrollOffset -= 2; rebuildWidgets(); } }
            ).bounds(sbX, y, 16, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (triggerScrollOffset + visTotal < total) { triggerScrollOffset += 2; rebuildWidgets(); } }
            ).bounds(sbX, y + (perCol - 1) * (bH + 2), 16, 16).build());
        }

        y = Math.max(col1Y, col2Y) + 2;

        // ── Кнопка "+AND умова" ─────────────────────────────────────────────
        // Показуємо список доп. тригерів
        List<WaveTrigger> extras = wave.getExtraTriggers();
        if (!extras.isEmpty()) {
            StringBuilder sb = new StringBuilder("§b+AND: ");
            for (WaveTrigger t : extras) sb.append(t.label).append(", ");
            String extStr = sb.toString().replaceAll(", $", "");
            this.addRenderableWidget(Button.builder(
                Component.literal(extStr.length() > 55 ? extStr.substring(0, 53) + "…" : extStr),
                b -> {}
            ).bounds(cx - 155, y, 260, 13).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ Очистити AND"),
                b -> { wave.setExtraTriggers(new ArrayList<>()); rebuildWidgets(); }
            ).bounds(cx + 110, y, 90, 13).build());
            y += 16;
        }
        // Підказка про AND
        this.addRenderableWidget(Button.builder(
            Component.literal("§8[Клікни ще раз на не-вибраний тригер щоб додати як AND умову]"), b -> {}
        ).bounds(cx - 155, y, 310, 11).build()).active = false;
        y += 14;

        // ── Разово + Активувати з хвилі ────────────────────────────────────
        boolean oneTime = wave.isOneTimeOnly();
        this.addRenderableWidget(Button.builder(
            Component.literal(oneTime ? "§a☑ Разово (1 раз за сесію)" : "§7☐ Разово"),
            b -> { wave.setOneTimeOnly(!wave.isOneTimeOnly()); rebuildWidgets(); }
        ).bounds(cx - 155, y, 155, 16).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("§7Активувати з хвилі:"), b -> {}
        ).bounds(cx + 4, y, 110, 16).build()).active = false;
        activateFromWaveInput = new EditBox(this.font, cx + 116, y, 40, 16, Component.literal("0"));
        activateFromWaveInput.setValue(String.valueOf(wave.getActivateFromWave()));
        activateFromWaveInput.setMaxLength(3);
        this.addRenderableWidget(activateFromWaveInput);
        y += 20;

        // ── PLAYER_HAS_ITEM — поле предмета ──────────────────────────────
        if (selected == WaveTrigger.PLAYER_HAS_ITEM || wave.getExtraTriggers().contains(WaveTrigger.PLAYER_HAS_ITEM)) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмет (registry id):"), b -> {}
            ).bounds(cx - 155, y, 130, 16).build()).active = false;
            customItemInput = new EditBox(this.font, cx - 20, y, 175, 16, Component.literal("minecraft:diamond"));
            customItemInput.setValue(wave.getTriggerCustomItemId().isEmpty() ? "minecraft:diamond" : wave.getTriggerCustomItemId());
            customItemInput.setMaxLength(60);
            this.addRenderableWidget(customItemInput);
            y += 20;
        }

        // ── Перезарядка ────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Перезарядка:"), b -> {}
        ).bounds(cx - 155, y, 100, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.literal("§8(мін. 5 сек завжди)"), b -> {}
        ).bounds(cx - 50, y, 120, 14).build()).active = false;
        y += 16;

        WaveConfig.CooldownMode cm = wave.getCooldownMode();
        int mW = 78;
        String lblN = cm == WaveConfig.CooldownMode.NONE    ? "§a● Немає"   : "§7○ Немає";
        String lblS = cm == WaveConfig.CooldownMode.SECONDS ? "§a● Секунди" : "§7○ Секунди";
        String lblW = cm == WaveConfig.CooldownMode.WAVES   ? "§a● Хвилі"   : "§7○ Хвилі";
        this.addRenderableWidget(Button.builder(Component.literal(lblN),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.NONE); rebuildWidgets(); }
        ).bounds(cx - 120, y, mW, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(lblS),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.SECONDS); rebuildWidgets(); }
        ).bounds(cx - 38, y, mW, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(lblW),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.WAVES); rebuildWidgets(); }
        ).bounds(cx + 44, y, mW, 16).build());
        y += 20;

        if (cm != WaveConfig.CooldownMode.NONE) {
            String lbl = cm == WaveConfig.CooldownMode.SECONDS ? "§7Секунд:" : "§7Хвиль:";
            this.addRenderableWidget(Button.builder(Component.literal(lbl), b -> {}
            ).bounds(cx - 120, y, 80, 16).build()).active = false;
            cooldownValueInput = new EditBox(this.font, cx - 36, y, 56, 16, Component.literal("0"));
            cooldownValueInput.setValue(String.valueOf(wave.getCooldownValue()));
            cooldownValueInput.setMaxLength(6);
            this.addRenderableWidget(cooldownValueInput);
        }

        addBottomButtons(cx);
    }

    private void addBottomButtons(int cx) {
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓ Зберегти"),
            b -> { save(); this.minecraft.setScreen(parent); }
        ).bounds(cx - 110, this.height - 24, 100, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("Скасувати"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 24, 100, 20).build());
    }

    private void save() {
        if (cooldownValueInput != null) {
            try { wave.setCooldownValue(Integer.parseInt(cooldownValueInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (customItemInput != null) {
            wave.setTriggerCustomItemId(customItemInput.getValue().trim());
        }
        if (activateFromWaveInput != null) {
            try { wave.setActivateFromWave(Integer.parseInt(activateFromWaveInput.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, "§d⚡ §6Тригер Хвилі §e" + (waveIndex + 1), cx, 10, 0xFFFFFF);
        g.drawCenteredString(this.font,
            wave.isTriggerEnabled()
                ? "§7Клік — головний тригер  §b§lCtrl+Клік §7або §bклік вже-вибраного §7— AND умова"
                : "§8Хвиля по загальному порядку",
            cx, 20, 0x888888);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && triggerScrollOffset > 0) { triggerScrollOffset -= 2; if (triggerScrollOffset < 0) triggerScrollOffset = 0; rebuildWidgets(); }
        else if (delta < 0) { triggerScrollOffset += 2; rebuildWidgets(); }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Detect Ctrl+Click to add as extra trigger (AND condition)
        if (button == 0 && hasControlDown()) {
            // Find which trigger button was clicked and add as extra
            for (var widget : this.renderables) {
                if (widget instanceof Button btn && btn.isMouseOver(mx, my)) {
                    String msg = btn.getMessage().getString();
                    // Find matching trigger
                    for (WaveTrigger t : WaveTrigger.values()) {
                        if (msg.contains(t.label) && t != selected && !t.name().startsWith("SHOP_")) {
                            if (wave.getExtraTriggers().contains(t)) wave.removeExtraTrigger(t);
                            else wave.addExtraTrigger(t);
                            rebuildWidgets();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
