package com.wavedefense.gui;


import net.minecraft.potion.Effect;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveMob;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Редактор ефектів моба у хвилі.
 * ✓ Виправлено GUI верстку
 * ✓ Скрол списку ефектів
 * ✓ Розширений список з пошуком
 */
public class MobEffectsEditorScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private final Screen parent;
    private final WaveMob mob;
    private List<String> effects;

    private List<Effect> allEffects;
    private List<Effect> filteredEffects;
    private TextFieldWidget searchBox;
    private int effectScrollOffset = 0;
    private int activeScrollOffset = 0;

    private TextFieldWidget amplifierInput;
    private TextFieldWidget durationInput;

    private Effect selectedEffect = null;

    private static final int EFFECT_ROW_H = 16;
    private static final int ACTIVE_ROW_H = 18;

    public MobEffectsEditorScreen(Screen parent, WaveMob mob) {
        super(new TranslationTextComponent("wavedefense.title.mob_effects"));
        this.parent = parent;
        this.mob = mob;
        this.effects = new ArrayList<>(mob.getEffects());

        allEffects = ForgeRegistries.POTIONS.getValues().stream()
                .sorted((a, b) -> a.getDisplayName().getString().compareTo(b.getDisplayName().getString()))
                .collect(java.util.stream.Collectors.toList());
        filteredEffects = new ArrayList<>(allEffects);
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int panelH = this.height - 60;

        // ── Ліва панель: активні ефекти ──────────────────────────────
        int leftW = cx - 14;
        int lx = 4;
        int y = 28;

        this.addButton(new Button(lx, y, leftW - 4, 14, new StringTextComponent(I18n.get("wavedefense.mob_effect.active_header", effects.size())), b -> {})).active = false;
        y += 16;

        int activeVisibleMax = Math.max(2, (panelH - 100) / ACTIVE_ROW_H);
        // clamp scroll
        if (activeScrollOffset > 0 && activeScrollOffset >= effects.size())
            activeScrollOffset = Math.max(0, effects.size() - 1);

        for (int i = 0; i < Math.min(activeVisibleMax, effects.size()); i++) {
            int idx = i + activeScrollOffset;
            if (idx >= effects.size()) break;
            final int fi = idx;
            String ef = effects.get(idx);
            String label = formatEffect(ef);
            // Скорочуємо якщо довге
            if (label.length() > 22) label = label.substring(0, 20) + "…";

            this.addButton(new Button(lx, y, leftW - 26, ACTIVE_ROW_H, new StringTextComponent("§e" + label), b -> {})).active = false;

            this.addButton(new Button(lx + leftW - 24, y, 20, ACTIVE_ROW_H, new StringTextComponent("§c✕"), b -> { effects.remove(fi); if (activeScrollOffset > 0 && activeScrollOffset >= effects.size()) activeScrollOffset--; init(); }));
            y += ACTIVE_ROW_H + 1;
        }

        // Скрол активних ефектів
        if (effects.size() > activeVisibleMax) {
            int sbX = lx + leftW - 4;
            this.addButton(new Button(sbX, 44, 14, 14, new StringTextComponent("▲"), b -> { if (activeScrollOffset > 0) { activeScrollOffset--; init(); } }));
            this.addButton(new Button(sbX, 44 + (activeVisibleMax - 1) * ACTIVE_ROW_H, 14, 14, new StringTextComponent("▼"), b -> { if (activeScrollOffset + activeVisibleMax < effects.size()) { activeScrollOffset++; init(); } }));
        }

        // ── Права панель: вибір ефекту ────────────────────────────────
        int rightX = cx + 4;
        int rightW = this.width - rightX - 4;
        int ry = 28;

        this.addButton(new Button(rightX, ry, rightW, 14, new TranslationTextComponent("wavedefense.auto.вибір_ефекту_b7199ce3"), b -> {})).active = false;
        ry += 16;

        // Пошук
        searchBox = new TextFieldWidget(this.font, rightX, ry, rightW, 16, new TranslationTextComponent("wavedefense.auto.пошук_8375e4ea"));
        searchBox.setResponder(s -> { effectScrollOffset = 0; applyFilter(s); });
        this.addButton(searchBox);
        ry += 20;

        // Список ефектів зі скролом
        int effectListH = panelH - 110;
        int effectVisibleMax = Math.max(3, effectListH / EFFECT_ROW_H);

        // clamp
        if (effectScrollOffset > 0 && effectScrollOffset >= filteredEffects.size())
            effectScrollOffset = Math.max(0, filteredEffects.size() - effectVisibleMax);

        for (int i = 0; i < Math.min(effectVisibleMax, filteredEffects.size()); i++) {
            int idx = effectScrollOffset + i;
            if (idx >= filteredEffects.size()) break;
            Effect ef = filteredEffects.get(idx);
            final Effect fef = ef;
            boolean sel = ef == selectedEffect;
            // Колір ефекту
            int color = ef.getColor();
            String hex = String.format("%06X", color & 0xFFFFFF);
            String label = (sel ? "§a§l▶ " : "§7") + ef.getDisplayName().getString();

            this.addButton(new Button(rightX, ry + i * EFFECT_ROW_H, rightW - 16, EFFECT_ROW_H - 1, new StringTextComponent(label), b -> { selectedEffect = fef; init(); }));
        }

        // Скрол списку ефектів
        if (filteredEffects.size() > effectVisibleMax) {
            int sbX = rightX + rightW - 14;
            this.addButton(new Button(sbX, ry, 12, 14, new StringTextComponent("▲"), b -> { if (effectScrollOffset > 0) { effectScrollOffset--; init(); } }));
            this.addButton(new Button(sbX, ry + (effectVisibleMax - 1) * EFFECT_ROW_H, 12, 14, new StringTextComponent("▼"), b -> { if (effectScrollOffset + effectVisibleMax < filteredEffects.size()) { effectScrollOffset++; init(); } }));
        }

        // Нижня частина: рівень, тривалість, додати
        int bottomY = this.height - 58;

        this.addButton(new Button(rightX, bottomY, 80, 14, new TranslationTextComponent("wavedefense.auto.рівень_0_i_66c640e3"), b -> {})).active = false;
        amplifierInput = new TextFieldWidget(this.font, rightX + 82, bottomY, 40, 14, new StringTextComponent("0"));
        amplifierInput.setValue("0");
        amplifierInput.setMaxLength(2);
        this.addButton(amplifierInput);

        this.addButton(new Button(rightX, bottomY + 16, 90, 14, new TranslationTextComponent("wavedefense.auto.тіків_600_30с_90fe954d"), b -> {})).active = false;
        durationInput = new TextFieldWidget(this.font, rightX + 92, bottomY + 16, 50, 14, new StringTextComponent("600"));
        durationInput.setValue("600");
        durationInput.setMaxLength(6);
        this.addButton(durationInput);

        String addLbl = selectedEffect != null
                ? "§a➕ " + selectedEffect.getDisplayName().getString()
                : I18n.get("wavedefense.mob_effect.select_hint");
        if (addLbl.length() > 28) addLbl = addLbl.substring(0, 27) + "…";
        this.addButton(new Button(rightX, bottomY + 32, rightW, 18, new StringTextComponent(addLbl), b -> addSelectedEffect()));

        // ── Кнопки знизу ──────────────────────────────────────────────
        this.addButton(new Button(cx - 110, this.height - 24, 100, 20, new TranslationTextComponent("wavedefense.button.save"), b -> save()));
        this.addButton(new Button(cx + 10, this.height - 24, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> this.minecraft.setScreen(parent)));
    }

    private void applyFilter(String q) {
        filteredEffects = allEffects.stream()
                .filter(e -> q.isEmpty() ||
                        e.getDisplayName().getString().toLowerCase().contains(q.toLowerCase()) ||
                        ForgeRegistries.POTIONS.getKey(e).toString().toLowerCase().contains(q.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
        rebuild();
    }

    private void addSelectedEffect() {
        if (selectedEffect == null) return;
        try {
            int amp = Integer.parseInt(amplifierInput.getValue().trim());
            int dur = Integer.parseInt(durationInput.getValue().trim());
            ResourceLocation key = ForgeRegistries.POTIONS.getKey(selectedEffect);
            if (key != null) {
                effects.add(key + ":" + amp + ":" + dur);
                rebuild();
            }
        } catch (NumberFormatException ignored) {}
    }

    private String formatEffect(String raw) {
        String[] parts = raw.split(":");
        // Format: "namespace:path:amp:dur" (4 parts) or "namespace:path:amp" (3)
        if (parts.length >= 4) {
            // namespace:path:amp:dur
            String name = parts[0] + ":" + parts[1];
            int amp = 0, dur = 0;
            try { amp = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
            try { dur = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
            Effect ef = ForgeRegistries.POTIONS.getValue(new ResourceLocation(name));
            String displayName = ef != null ? ef.getDisplayName().getString() : name;
            return displayName + " Lv" + (amp + 1) + " " + dur + "t";
        } else if (parts.length == 3) {
            String name = parts[0];
            int amp = 0, dur = 0;
            try { amp = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
            try { dur = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
            Effect ef = ForgeRegistries.POTIONS.getValue(new ResourceLocation(name));
            String displayName = ef != null ? ef.getDisplayName().getString() : name;
            return displayName + " Lv" + (amp + 1) + " " + dur + "t";
        }
        return raw;
    }

    private void save() {
        mob.setEffects(effects);
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Scroll effects list on right side
        int cx = this.width / 2;
        if (mx > cx) {
            int panelH = this.height - 60;
            int effectListH = panelH - 110;
            int effectVisibleMax = Math.max(3, effectListH / EFFECT_ROW_H);
            if (delta > 0 && effectScrollOffset > 0) { effectScrollOffset--; rebuild(); }
            else if (delta < 0 && effectScrollOffset + effectVisibleMax < filteredEffects.size()) { effectScrollOffset++; rebuild(); }
        } else {
            int panelH2 = this.height - 60;
            int activeVisibleMax2 = Math.max(2, (panelH2 - 100) / ACTIVE_ROW_H);
            if (delta > 0 && activeScrollOffset > 0) { activeScrollOffset--; rebuild(); }
            else if (delta < 0 && activeScrollOffset + activeVisibleMax2 < effects.size()) { activeScrollOffset++; rebuild(); }
        }
        return true;
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
    public void render(MatrixStack g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, cx, 10, GuiTheme.TEXT);
        // Роздільник між двома панелями
        com.wavedefense.gui.GuiCompat.fill(g, cx, 24, cx + 1, this.height - 28, GuiTheme.BORDER);

        // Scissor: обрізаємо прокручувану зону, щоб елементи не накладались на нижні контролери
        int clipBot = this.height - 62;
        ScissorHelper.enable(0, 24, this.width, Math.max(1, clipBot - 24));
        ScissorHelper.renderBand(this.buttons, g, mouseX, mouseY, partial, 0, clipBot);
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Нижня зона: поля вводу та кнопки — рендер без scissor
        ScissorHelper.renderBand(this.buttons, g, mouseX, mouseY, partial, clipBot, this.height);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
