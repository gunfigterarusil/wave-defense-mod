package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

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
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


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
    private TextFieldWidget customItemInput;
    private TextFieldWidget customValueInput;
    private TextFieldWidget cooldownValueInput;
    private TextFieldWidget activateFromWaveInput;

    private WaveTrigger selected;

    public WaveTriggerEditorScreen(Screen parent, WaveConfig wave, int waveIndex, boolean isPvp) {
        super(new TranslationTextComponent("wavedefense.title.wave_trigger", waveIndex + 1));
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
        this.addButton(new Button(cx - btnW / 2, y, btnW, 18, ((enabled) ? new TranslationTextComponent("wavedefense.auto.тригерна_хвиля_увімкнена_3f634921") : new TranslationTextComponent("wavedefense.auto.тригерна_хвиля_вимкнена_bd77b179")), b -> { wave.setTriggerEnabled(!wave.isTriggerEnabled()); init(); }));
        y += 22;

        if (!enabled) {
            this.addButton(new Button(cx - btnW / 2, y, btnW, 13, new TranslationTextComponent("wavedefense.auto.увімкніть_щоб_налаштувати_2abc90f7"), b -> {})).active = false;
            addBottomButtons(cx, this.height - 26);
            return;
        }

        // ── Підказка AND + активні AND ──────────────────────────────────
        this.addButton(new Button(cx - btnW / 2, y, btnW, 12, new TranslationTextComponent("wavedefense.auto.клік_головний_ctrl_клік_and_умов_25f7352f"), b -> {})).active = false;
        y += 14;

        List<WaveTrigger> extras = wave.getExtraTriggers();
        if (!extras.isEmpty()) {
            StringBuilder sb = new StringBuilder("§b+AND: ");
            for (WaveTrigger t : extras) sb.append("§f").append(t.label).append("§b  ");
            String s = sb.toString().trim();
            this.addButton(new Button(cx - btnW / 2, y, btnW - 52, 13, new StringTextComponent(s.length() > 52 ? s.substring(0, 50) + "…" : s), b -> {})).active = false;
            this.addButton(new Button(cx + btnW / 2 - 50, y, 50, 13, new TranslationTextComponent("wavedefense.auto.очистити_and_710932c7"), b -> { wave.setExtraTriggers(new ArrayList<>()); init(); }));
            y += 16;
        }

        scrollTop = y; // ── верхня межа scissored зони ──────────────────

        // Розраховуємо висоту нижньої статичної зони
        int staticH = calcStaticHeight();
        scrollBot = this.height - staticH - 28; // 28 = кнопки внизу + відступ
        // Мінімум 3 видимі рядки в зоні тригерів (захист від дуже малих вікон)
        int minScrollZone = 3 * (BTN_H + BTN_GAP);
        if (scrollBot < scrollTop + minScrollZone) scrollBot = scrollTop + minScrollZone;

        // Скрол
        int listH   = scrollBot - scrollTop;
        int visible = Math.max(1, listH / (BTN_H + BTN_GAP));
        int maxScr  = Math.max(0, available.size() - visible);
        if (triggerScrollOffset > maxScr) triggerScrollOffset = maxScr;

        // ── Список тригерів (одна колонка) ──────────────────────────────
        // U1: [+AND] / [-AND] кнопка поряд з кожним не-primary тригером
        final int AND_BTN_W = 38;
        int ty = scrollTop;
        for (int i = triggerScrollOffset; i < available.size(); i++) {
            if (ty + BTN_H > scrollBot) break;
            WaveTrigger t = available.get(i);
            boolean isSel   = (t == selected);
            boolean isExtra = wave.getExtraTriggers().contains(t);
            String lbl = (isSel ? "§e§l▶ " : isExtra ? "§b§l+ " : "§7  ") + t.label;
            final WaveTrigger ft = t;
            // Основна кнопка: коротша якщо не primary (місце для [+AND])
            int mainBtnW = isSel ? btnW : btnW - AND_BTN_W - 2;
            Button btn = new Button(cx - btnW / 2, ty, mainBtnW, BTN_H, new StringTextComponent(lbl), b -> {
                    if (ft == selected) return;
                    // Клік по AND-тригеру — він залишається AND (прибирається кнопкою [-AND])
                    // Клік по звичайному — обираємо як primary
                    if (!wave.getExtraTriggers().contains(ft)) {
                        selected = ft;
                        wave.setTriggerType(ft);
                        init();
                    }
                });
            /* setTooltip omitted on 1.16.5: btn */
            this.addButton(btn);
            // [+AND] / [-AND] кнопка для не-primary тригерів
            if (!isSel) {
                Button andBtn = new Button(cx - btnW / 2 + mainBtnW + 2, ty, AND_BTN_W, BTN_H, isExtra
                        ? new StringTextComponent("§c-AND")
                        : new StringTextComponent("§b+AND"), b -> {
                        if (isExtra) wave.removeExtraTrigger(ft);
                        else wave.addExtraTrigger(ft);
                        init();
                    });
                /* setTooltip omitted on 1.16.5: andBtn */
                this.addButton(andBtn);
            }
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
            this.addButton(new Button(cx - btnW / 2, y, btnW - 26, 18, new TranslationTextComponent("wavedefense.auto.предмет_и_item_id_через_кому_fa084ffb"), b -> {})).active = false;
            this.addButton(new Button(cx + btnW / 2 - 24, y, 24, 18, new StringTextComponent("✋"), b -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null && customItemInput != null) {
                        net.minecraft.item.ItemStack held = mc.player.getMainHandItem();
                        if (!held.isEmpty()) {
                            net.minecraft.util.ResourceLocation rl =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem());
                            if (rl != null) {
                                String cur = customItemInput.getValue().trim();
                                customItemInput.setValue(cur.isEmpty() ? rl.toString() : cur + "," + rl);
                            }
                        }
                    }
                })
            )/* setTooltip omitted on 1.16.5 */;
            y += 20;
            customItemInput = new TextFieldWidget(this.font, cx - btnW / 2, y, btnW, 18, new TranslationTextComponent("wavedefense.auto.id_87ea5dfc"));
            customItemInput.setMaxLength(256);
            customItemInput.setValue(wave.getTriggerCustomItemId().isEmpty()
                ? "minecraft:diamond" : wave.getTriggerCustomItemId());
        /* customItemInput.setHint(...) omitted on 1.16.5 */
            this.addButton(customItemInput);
            y += 22;
        }

        // TIMER_CUSTOM / MOBS_KILLED_N / WAVES_SURVIVED_N
        if (needsValue()) {
            WaveTrigger vt = valueTrigger();
            String emoji = vt == WaveTrigger.TIMER_CUSTOM ? "⏱" : vt == WaveTrigger.MOBS_KILLED_N ? "⚔" : "🏆";
            String lbl   = vt == WaveTrigger.TIMER_CUSTOM    ? emoji + " §7" + I18n.get("wavedefense.trigger.label.interval_sec") :
                           vt == WaveTrigger.MOBS_KILLED_N   ? emoji + " §7" + I18n.get("wavedefense.trigger.label.kill_count") :
                                                                emoji + " §7" + I18n.get("wavedefense.trigger.label.waves_survived");
            this.addButton(new Button(cx - btnW / 2, y, 200, 18, new StringTextComponent(lbl), b -> {})).active = false;
            customValueInput = new TextFieldWidget(this.font, cx + btnW / 2 - 72, y, 72, 18, new StringTextComponent("60"));
            customValueInput.setMaxLength(6);
            customValueInput.setValue(String.valueOf(wave.getTriggerCustomValue()));
            this.addButton(customValueInput);
            y += 22;
        }
        return y;
    }

    private void addCommonSettings(int cx, int y, int btnW) {
        // Разово + Активувати з хвилі
        boolean oneTime = wave.isOneTimeOnly();
        this.addButton(new Button(cx - btnW / 2, y, 150, 18, ((oneTime) ? new TranslationTextComponent("wavedefense.auto.разово_1_раз_сесія_7d7863f6") : new TranslationTextComponent("wavedefense.auto.разово_9e507e8c")), b -> { wave.setOneTimeOnly(!wave.isOneTimeOnly()); rebuild(); }));
        this.addButton(new Button(cx + 4, y, 110, 18, new TranslationTextComponent("wavedefense.auto.активувати_з_хвилі_a57c9a7c"), b -> {})).active = false;
        activateFromWaveInput = new TextFieldWidget(this.font, cx + 118, y, 40, 18, new StringTextComponent("0"));
        activateFromWaveInput.setValue(String.valueOf(wave.getActivateFromWave()));
        activateFromWaveInput.setMaxLength(3);
        this.addButton(activateFromWaveInput);
        y += 22;

        // Перезарядка
        this.addButton(new Button(cx - btnW / 2, y, 180, 18, new TranslationTextComponent("wavedefense.auto.перезарядка_мін_5с_4172cb22"), b -> {})).active = false;
        y += 22;

        WaveConfig.CooldownMode cm = wave.getCooldownMode();
        int mW = 76;
        this.addButton(new Button(cx - 116, y, mW, 18, ((cm == WaveConfig.CooldownMode.NONE) ? new TranslationTextComponent("wavedefense.auto.немає_331a7c36") : new TranslationTextComponent("wavedefense.auto.немає_aad1d2c8")), b -> { wave.setCooldownMode(WaveConfig.CooldownMode.NONE); rebuild(); }));
        this.addButton(new Button(cx - 36, y, mW, 18, ((cm == WaveConfig.CooldownMode.SECONDS) ? new TranslationTextComponent("wavedefense.auto.секунди_454eece8") : new TranslationTextComponent("wavedefense.auto.секунди_8913d206")), b -> { wave.setCooldownMode(WaveConfig.CooldownMode.SECONDS); rebuild(); }));
        this.addButton(new Button(cx + 44, y, mW, 18, ((cm == WaveConfig.CooldownMode.WAVES) ? new TranslationTextComponent("wavedefense.auto.хвилі_a1a07f8a") : new TranslationTextComponent("wavedefense.auto.хвилі_e76a6167")), b -> { wave.setCooldownMode(WaveConfig.CooldownMode.WAVES); rebuild(); }));
        y += 22;

        if (cm != WaveConfig.CooldownMode.NONE) {
            String lbl = cm == WaveConfig.CooldownMode.SECONDS
                ? "§7" + I18n.get("wavedefense.trigger.label.cooldown_seconds")
                : "§7" + I18n.get("wavedefense.trigger.label.cooldown_waves");
            this.addButton(new Button(cx - 116, y, 80, 18, new StringTextComponent(lbl), b -> {})).active = false;
            cooldownValueInput = new TextFieldWidget(this.font, cx - 32, y, 60, 18, new StringTextComponent("0"));
            cooldownValueInput.setValue(String.valueOf(wave.getCooldownValue()));
            cooldownValueInput.setMaxLength(6);
            this.addButton(cooldownValueInput);
        }
    }

    private void addBottomButtons(int cx, int y) {
        this.addButton(new Button(cx - 110, y, 100, 20, new TranslationTextComponent("wavedefense.auto.зберегти_617e5dc0"), b -> { save(); this.minecraft.setScreen(parent); }));
        this.addButton(new Button(cx + 10, y, 100, 20, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), b -> this.minecraft.setScreen(parent)));
    }

    // ── RENDER ────────────────────────────────────────────────────────────
    @Override
    public void render(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.wave.trigger_title", waveIndex + 1), this.width / 2, 8, GuiTheme.TEXT);

        if (!wave.isTriggerEnabled() || scrollTop >= scrollBot) {
            super.render(g, mx, my, pt);
            return;
        }

        // 1) Рендеримо статичні елементи ДО списку (поза scissor)
        renderWidgetsBand(g, mx, my, pt, 0, scrollTop);

        // 2) Scissor: список тригерів
        ScissorHelper.enable(0, scrollTop, this.width, scrollBot - scrollTop);
        renderWidgetsBand(g, mx, my, pt, scrollTop, scrollBot);
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // 3) Статичні елементи ПІСЛЯ списку
        renderWidgetsBand(g, mx, my, pt, scrollBot, this.height);
    }

    /** Рендерить тільки widgets чий getY() потрапляє в [yFrom, yTo) */
    private void renderWidgetsBand(MatrixStack g, int mx, int my, float pt, int yFrom, int yTo) {
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) { net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y >= yFrom && w.y < yTo) {
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
            if (triggerScrollOffset > 0) { triggerScrollOffset--; rebuild(); }
        } else {
            int visible = Math.max(1, (scrollBot - scrollTop) / (BTN_H + BTN_GAP));
            if (triggerScrollOffset + visible < available.size()) { triggerScrollOffset++; rebuild(); }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hasControlDown()) {
            for (Object widget : this.buttons) {
                if (widget instanceof Button) {
                    Button btn = (Button) widget;
                    if (btn.isMouseOver(mx, my)) {
                        String msg = btn.getMessage().getString();
                        for (WaveTrigger t : WaveTrigger.values()) {
                            if (msg.contains(t.label) && t != selected && !t.name().startsWith("SHOP_")) {
                                if (wave.getExtraTriggers().contains(t)) wave.removeExtraTrigger(t);
                                else wave.addExtraTrigger(t);
                                rebuild();
                                return true;
                            }
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
