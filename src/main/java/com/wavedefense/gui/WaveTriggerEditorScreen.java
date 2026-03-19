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
 * Редактор тригера хвилі — одна колонка зі scissor-прокруткою.
 * Scissor ховає тригери за межі списку — як у ванільному Language Screen.
 *
 * Макет (зверху вниз, статично):
 *   Заголовок | Вмикач | AND підказка + активні AND
 *   ─── scissored список тригерів ───
 *   Per-trigger налаштування (PLAYER_HAS_ITEM поле, TIMER/MOBS_N поле)
 *   Разово | Активувати з хвилі | Перезарядка
 *   [Зберегти] [Скасувати]
 */
public class WaveTriggerEditorScreen extends Screen {

    private final Screen     parent;
    private final WaveConfig wave;
    private final int        waveIndex;
    private final boolean    isPvp;

    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 2;

    // Межі scrollable зони — розраховуються в init()
    private int scrollTop = 0;
    private int scrollBot = 0;

    private int triggerScrollOffset = 0;
    private List<WaveTrigger> available = new ArrayList<>();

    // Per-trigger inputs
    private EditBox customItemInput;
    private EditBox customValueInput;
    private EditBox cooldownValueInput;
    private EditBox activateFromWaveInput;

    private WaveTrigger selected;

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
        customItemInput = null; customValueInput = null;
        cooldownValueInput = null; activateFromWaveInput = null;

        available.clear();
        for (WaveTrigger t : WaveTrigger.values()) {
            if (isPvp  && !t.pvp) continue;
            if (!isPvp && !t.pve) continue;
            if (t.name().startsWith("SHOP_")) continue;
            available.add(t);
        }

        int cx = this.width / 2;
        int btnW = Math.min(310, this.width - 30);
        int y = 26;

        // ── Вмикач ─────────────────────────────────────────────────────
        boolean enabled = wave.isTriggerEnabled();
        this.addRenderableWidget(Button.builder(
            Component.literal(enabled ? "§a☑ Тригерна хвиля УВІМКНЕНА" : "§7☐ Тригерна хвиля вимкнена"),
            b -> { wave.setTriggerEnabled(!wave.isTriggerEnabled()); rebuildWidgets(); }
        ).bounds(cx - btnW / 2, y, btnW, 18).build());
        y += 22;

        if (!enabled) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§8Увімкніть щоб налаштувати"), b -> {}
            ).bounds(cx - btnW / 2, y, btnW, 13).build()).active = false;
            addBottomButtons(cx, this.height - 26);
            return;
        }

        // ── Підказка AND + активні AND ──────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§8Клік=головний  │  Ctrl+Клік=§b+AND§8 умова"), b -> {}
        ).bounds(cx - btnW / 2, y, btnW, 12).build()).active = false;
        y += 14;

        List<WaveTrigger> extras = wave.getExtraTriggers();
        if (!extras.isEmpty()) {
            StringBuilder sb = new StringBuilder("§b+AND: ");
            for (WaveTrigger t : extras) sb.append("§f").append(t.label).append("§b  ");
            String s = sb.toString().trim();
            this.addRenderableWidget(Button.builder(
                Component.literal(s.length() > 52 ? s.substring(0, 50) + "…" : s), b -> {}
            ).bounds(cx - btnW / 2, y, btnW - 52, 13).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ Очистити AND"),
                b -> { wave.setExtraTriggers(new ArrayList<>()); rebuildWidgets(); }
            ).bounds(cx + btnW / 2 - 50, y, 50, 13).build());
            y += 16;
        }

        scrollTop = y; // ── верхня межа scissored зони ──────────────────

        // Розраховуємо висоту нижньої статичної зони
        int staticH = calcStaticHeight();
        scrollBot = this.height - staticH - 28; // 28 = кнопки внизу + відступ
        if (scrollBot < scrollTop + BTN_H) scrollBot = scrollTop + BTN_H;

        // Скрол
        int listH   = scrollBot - scrollTop;
        int visible = Math.max(1, listH / (BTN_H + BTN_GAP));
        int maxScr  = Math.max(0, available.size() - visible);
        if (triggerScrollOffset > maxScr) triggerScrollOffset = maxScr;

        // ── Список тригерів (одна колонка) ──────────────────────────────
        int ty = scrollTop;
        for (int i = triggerScrollOffset; i < available.size(); i++) {
            if (ty + BTN_H > scrollBot) break;
            WaveTrigger t = available.get(i);
            boolean isSel   = (t == selected);
            boolean isExtra = wave.getExtraTriggers().contains(t);
            String lbl = (isSel ? "§e§l▶ " : isExtra ? "§b§l+ " : "§7  ") + t.label;
            final WaveTrigger ft = t;
            Button btn = Button.builder(
                Component.literal(lbl),
                b -> {
                    if (ft == selected) return;
                    if (wave.getExtraTriggers().contains(ft)) wave.removeExtraTrigger(ft);
                    else { selected = ft; wave.setTriggerType(ft); }
                    rebuildWidgets();
                }
            ).bounds(cx - btnW / 2, ty, btnW, BTN_H).build();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7" + t.tooltip +
                    (isExtra ? "\n§b[AND: обидві умови мають спрацювати]" : ""))));
            this.addRenderableWidget(btn);
            ty += BTN_H + BTN_GAP;
        }

        // ── Статичні налаштування ────────────────────────────────────────
        int sy = scrollBot + 6;
        sy = addPerTriggerSettings(cx, sy, btnW);
        addCommonSettings(cx, sy, btnW);
        addBottomButtons(cx, this.height - 26);
    }

    // ── Розрахунок висоти статичної зони ────────────────────────────────
    private int calcStaticHeight() {
        int h = 0;
        if (needsItem())  h += 38; // label 14 + input 20 + gap 4
        if (needsValue()) h += 24; // label+input 20 + gap 4
        h += 22; // Разово + з хвилі
        h += 36; // Перезарядка label + радіо
        if (wave.getCooldownMode() != WaveConfig.CooldownMode.NONE) h += 22;
        return h;
    }

    private boolean needsItem() {
        return selected == WaveTrigger.PLAYER_HAS_ITEM
            || wave.getExtraTriggers().contains(WaveTrigger.PLAYER_HAS_ITEM);
    }
    private boolean needsValue() {
        for (WaveTrigger t : new WaveTrigger[]{
                WaveTrigger.TIMER_CUSTOM, WaveTrigger.MOBS_KILLED_N, WaveTrigger.WAVES_SURVIVED_N}) {
            if (selected == t || wave.getExtraTriggers().contains(t)) return true;
        }
        return false;
    }
    private WaveTrigger valueTrigger() {
        for (WaveTrigger t : new WaveTrigger[]{
                WaveTrigger.TIMER_CUSTOM, WaveTrigger.MOBS_KILLED_N, WaveTrigger.WAVES_SURVIVED_N}) {
            if (selected == t) return t;
        }
        for (WaveTrigger t : wave.getExtraTriggers()) {
            if (t == WaveTrigger.TIMER_CUSTOM || t == WaveTrigger.MOBS_KILLED_N || t == WaveTrigger.WAVES_SURVIVED_N) return t;
        }
        return null;
    }

    // ── Per-trigger налаштування ──────────────────────────────────────────
    private int addPerTriggerSettings(int cx, int y, int btnW) {
        // PLAYER_HAS_ITEM
        if (needsItem()) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§e🎁 §7Предмет(и) — item id через кому:"), b -> {}
            ).bounds(cx - btnW / 2, y, btnW - 26, 14).build()).active = false;
            this.addRenderableWidget(Button.builder(
                Component.literal("✋"),
                b -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null && customItemInput != null) {
                        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
                        if (!held.isEmpty()) {
                            net.minecraft.resources.ResourceLocation rl =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem());
                            if (rl != null) {
                                String cur = customItemInput.getValue().trim();
                                customItemInput.setValue(cur.isEmpty() ? rl.toString() : cur + "," + rl);
                            }
                        }
                    }
                }
            ).bounds(cx + btnW / 2 - 24, y, 24, 14).build()
            ).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7Вставити id з основної руки")));
            y += 16;
            customItemInput = new EditBox(this.font, cx - btnW / 2, y, btnW, 18, Component.literal("id"));
            customItemInput.setMaxLength(256);
            customItemInput.setValue(wave.getTriggerCustomItemId().isEmpty()
                ? "minecraft:diamond" : wave.getTriggerCustomItemId());
            customItemInput.setHint(Component.literal("§8minecraft:diamond,..."));
            this.addRenderableWidget(customItemInput);
            y += 22;
        }

        // TIMER_CUSTOM / MOBS_KILLED_N / WAVES_SURVIVED_N
        if (needsValue()) {
            WaveTrigger vt = valueTrigger();
            String emoji = vt == WaveTrigger.TIMER_CUSTOM ? "⏱" : vt == WaveTrigger.MOBS_KILLED_N ? "⚔" : "🏆";
            String lbl   = vt == WaveTrigger.TIMER_CUSTOM    ? emoji + " §7Інтервал (сек):" :
                           vt == WaveTrigger.MOBS_KILLED_N   ? emoji + " §7Мін. вбитих мобів:" :
                                                                emoji + " §7Мін. хвиль пережито:";
            this.addRenderableWidget(Button.builder(
                Component.literal(lbl), b -> {}
            ).bounds(cx - btnW / 2, y, 200, 18).build()).active = false;
            customValueInput = new EditBox(this.font, cx + btnW / 2 - 72, y, 72, 18, Component.literal("60"));
            customValueInput.setMaxLength(6);
            customValueInput.setValue(String.valueOf(wave.getTriggerCustomValue()));
            this.addRenderableWidget(customValueInput);
            y += 22;
        }
        return y;
    }

    private void addCommonSettings(int cx, int y, int btnW) {
        // Разово + Активувати з хвилі
        boolean oneTime = wave.isOneTimeOnly();
        this.addRenderableWidget(Button.builder(
            Component.literal(oneTime ? "§a☑ Разово (1 раз/сесія)" : "§7☐ Разово"),
            b -> { wave.setOneTimeOnly(!wave.isOneTimeOnly()); rebuildWidgets(); }
        ).bounds(cx - btnW / 2, y, 150, 18).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Активувати з хвилі:"), b -> {}
        ).bounds(cx + 4, y, 110, 18).build()).active = false;
        activateFromWaveInput = new EditBox(this.font, cx + 118, y, 40, 18, Component.literal("0"));
        activateFromWaveInput.setValue(String.valueOf(wave.getActivateFromWave()));
        activateFromWaveInput.setMaxLength(3);
        this.addRenderableWidget(activateFromWaveInput);
        y += 22;

        // Перезарядка
        this.addRenderableWidget(Button.builder(
            Component.literal("§7Перезарядка §8(мін. 5с):"), b -> {}
        ).bounds(cx - btnW / 2, y, 180, 14).build()).active = false;
        y += 16;

        WaveConfig.CooldownMode cm = wave.getCooldownMode();
        int mW = 76;
        this.addRenderableWidget(Button.builder(
            Component.literal(cm == WaveConfig.CooldownMode.NONE    ? "§a● Немає"   : "§7○ Немає"),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.NONE); rebuildWidgets(); }
        ).bounds(cx - 116, y, mW, 18).build());
        this.addRenderableWidget(Button.builder(
            Component.literal(cm == WaveConfig.CooldownMode.SECONDS ? "§a● Секунди" : "§7○ Секунди"),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.SECONDS); rebuildWidgets(); }
        ).bounds(cx - 36, y, mW, 18).build());
        this.addRenderableWidget(Button.builder(
            Component.literal(cm == WaveConfig.CooldownMode.WAVES   ? "§a● Хвилі"   : "§7○ Хвилі"),
            b -> { wave.setCooldownMode(WaveConfig.CooldownMode.WAVES); rebuildWidgets(); }
        ).bounds(cx + 44, y, mW, 18).build());
        y += 22;

        if (cm != WaveConfig.CooldownMode.NONE) {
            String lbl = cm == WaveConfig.CooldownMode.SECONDS ? "§7Секунд:" : "§7Хвиль:";
            this.addRenderableWidget(Button.builder(
                Component.literal(lbl), b -> {}
            ).bounds(cx - 116, y, 80, 18).build()).active = false;
            cooldownValueInput = new EditBox(this.font, cx - 32, y, 60, 18, Component.literal("0"));
            cooldownValueInput.setValue(String.valueOf(wave.getCooldownValue()));
            cooldownValueInput.setMaxLength(6);
            this.addRenderableWidget(cooldownValueInput);
        }
    }

    private void addBottomButtons(int cx, int y) {
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓ Зберегти"),
            b -> { save(); this.minecraft.setScreen(parent); }
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("Скасувати"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ── RENDER ────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "§d⚡ §6Тригер §eХвилі " + (waveIndex + 1), this.width / 2, 8, 0xFFFFFF);

        if (!wave.isTriggerEnabled() || scrollTop >= scrollBot) {
            super.render(g, mx, my, pt);
            return;
        }

        // 1) Рендеримо статичні елементи ДО списку (поза scissor)
        renderWidgetsBand(g, mx, my, pt, 0, scrollTop);

        // 2) Scissor: список тригерів
        ScissorHelper.enable(0, scrollTop, this.width, scrollBot - scrollTop);
        renderWidgetsBand(g, mx, my, pt, scrollTop, scrollBot);
        ScissorHelper.disable();

        // 3) Статичні елементи ПІСЛЯ списку
        renderWidgetsBand(g, mx, my, pt, scrollBot, this.height);
    }

    /** Рендерить тільки widgets чий getY() потрапляє в [yFrom, yTo) */
    private void renderWidgetsBand(GuiGraphics g, int mx, int my, float pt, int yFrom, int yTo) {
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() >= yFrom && w.getY() < yTo) {
                    w.render(g, mx, my, pt);
                }
            }
        }
    }

    // ── SCROLL + CTRL+CLICK ───────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!wave.isTriggerEnabled()) return true;
        if (delta > 0) {
            if (triggerScrollOffset > 0) { triggerScrollOffset--; rebuildWidgets(); }
        } else {
            int visible = Math.max(1, (scrollBot - scrollTop) / (BTN_H + BTN_GAP));
            if (triggerScrollOffset + visible < available.size()) { triggerScrollOffset++; rebuildWidgets(); }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hasControlDown()) {
            for (var widget : this.renderables) {
                if (widget instanceof Button btn && btn.isMouseOver(mx, my)) {
                    String msg = btn.getMessage().getString();
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

    // ── SAVE ──────────────────────────────────────────────────────────────
    private void save() {
        if (cooldownValueInput   != null) try { wave.setCooldownValue(Integer.parseInt(cooldownValueInput.getValue().trim())); } catch (NumberFormatException ignored) {}
        if (customItemInput      != null) wave.setTriggerCustomItemId(customItemInput.getValue().trim());
        if (customValueInput     != null) try { wave.setTriggerCustomValue(Integer.parseInt(customValueInput.getValue().trim())); } catch (NumberFormatException ignored) {}
        if (activateFromWaveInput!= null) try { wave.setActivateFromWave(Integer.parseInt(activateFromWaveInput.getValue().trim())); } catch (NumberFormatException ignored) {}
    }

    @Override public boolean keyPressed(int k, int s, int m) { return super.keyPressed(k, s, m); }
    @Override public boolean charTyped(char c, int m) { return super.charTyped(c, m); }
    @Override public boolean isPauseScreen() { return false; }
}
