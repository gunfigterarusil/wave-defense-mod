package com.wavedefense.gui;

import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
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

    private final Screen parent;
    private final WaveMob mob;
    private List<String> effects;

    private List<MobEffect> allEffects;
    private List<MobEffect> filteredEffects;
    private EditBox searchBox;
    private int effectScrollOffset = 0;
    private int activeScrollOffset = 0;

    private EditBox amplifierInput;
    private EditBox durationInput;

    private MobEffect selectedEffect = null;

    private static final int EFFECT_ROW_H = 16;
    private static final int ACTIVE_ROW_H = 18;

    public MobEffectsEditorScreen(Screen parent, WaveMob mob) {
        super(Component.translatable("wavedefense.title.mob_effects"));
        this.parent = parent;
        this.mob = mob;
        this.effects = new ArrayList<>(mob.getEffects());

        allEffects = ForgeRegistries.MOB_EFFECTS.getValues().stream()
                .sorted((a, b) -> a.getDisplayName().getString().compareTo(b.getDisplayName().getString()))
                .collect(Collectors.toList());
        filteredEffects = new ArrayList<>(allEffects);
    }

    @Override
    protected void init() {
        super.init();;
        int cx = this.width / 2;
        int panelH = this.height - 60;

        // ── Ліва панель: активні ефекти ──────────────────────────────
        int leftW = cx - 14;
        int lx = 4;
        int y = 28;

        this.addRenderableWidget(Button.builder(
                Component.literal(I18n.get("wavedefense.mob_effect.active_header", effects.size())), b -> {}
        ).bounds(lx, y, leftW - 4, 14).build()).active = false;
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

            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + label), b -> {}
            ).bounds(lx, y, leftW - 26, ACTIVE_ROW_H).build()).active = false;

            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    b -> { effects.remove(fi); if (activeScrollOffset > 0 && activeScrollOffset >= effects.size()) activeScrollOffset--; rebuildWidgets(); }
            ).bounds(lx + leftW - 24, y, 20, ACTIVE_ROW_H).build());
            y += ACTIVE_ROW_H + 1;
        }

        // Скрол активних ефектів
        if (effects.size() > activeVisibleMax) {
            int sbX = lx + leftW - 4;
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (activeScrollOffset > 0) { activeScrollOffset--; rebuildWidgets(); } }
            ).bounds(sbX, 44, 14, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (activeScrollOffset + activeVisibleMax < effects.size()) { activeScrollOffset++; rebuildWidgets(); } }
            ).bounds(sbX, 44 + (activeVisibleMax - 1) * ACTIVE_ROW_H, 14, 14).build());
        }

        // ── Права панель: вибір ефекту ────────────────────────────────
        int rightX = cx + 4;
        int rightW = this.width - rightX - 4;
        int ry = 28;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.вибір_ефекту_b7199ce3"), b -> {}
        ).bounds(rightX, ry, rightW, 14).build()).active = false;
        ry += 16;

        // Пошук
        searchBox = new EditBox(this.font, rightX, ry, rightW, 16, Component.translatable("wavedefense.auto.пошук_8375e4ea"));
        searchBox.setResponder(s -> { effectScrollOffset = 0; applyFilter(s); });
        this.addRenderableWidget(searchBox);
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
            MobEffect ef = filteredEffects.get(idx);
            final MobEffect fef = ef;
            boolean sel = ef == selectedEffect;
            // Колір ефекту
            int color = ef.getColor();
            String hex = String.format("%06X", color & 0xFFFFFF);
            String label = (sel ? "§a§l▶ " : "§7") + ef.getDisplayName().getString();

            this.addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> { selectedEffect = fef; rebuildWidgets(); }
            ).bounds(rightX, ry + i * EFFECT_ROW_H, rightW - 16, EFFECT_ROW_H - 1).build());
        }

        // Скрол списку ефектів
        if (filteredEffects.size() > effectVisibleMax) {
            int sbX = rightX + rightW - 14;
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (effectScrollOffset > 0) { effectScrollOffset--; rebuildWidgets(); } }
            ).bounds(sbX, ry, 12, 14).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (effectScrollOffset + effectVisibleMax < filteredEffects.size()) { effectScrollOffset++; rebuildWidgets(); } }
            ).bounds(sbX, ry + (effectVisibleMax - 1) * EFFECT_ROW_H, 12, 14).build());
        }

        // Нижня частина: рівень, тривалість, додати
        int bottomY = this.height - 58;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.рівень_0_i_66c640e3"), b -> {}
        ).bounds(rightX, bottomY, 80, 14).build()).active = false;
        amplifierInput = new EditBox(this.font, rightX + 82, bottomY, 40, 14, Component.literal("0"));
        amplifierInput.setValue("0");
        amplifierInput.setMaxLength(2);
        this.addRenderableWidget(amplifierInput);

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.тіків_600_30с_90fe954d"), b -> {}
        ).bounds(rightX, bottomY + 16, 90, 14).build()).active = false;
        durationInput = new EditBox(this.font, rightX + 92, bottomY + 16, 50, 14, Component.literal("600"));
        durationInput.setValue("600");
        durationInput.setMaxLength(6);
        this.addRenderableWidget(durationInput);

        String addLbl = selectedEffect != null
                ? "§a➕ " + selectedEffect.getDisplayName().getString()
                : I18n.get("wavedefense.mob_effect.select_hint");
        if (addLbl.length() > 28) addLbl = addLbl.substring(0, 27) + "…";
        this.addRenderableWidget(Button.builder(
                Component.literal(addLbl),
                b -> addSelectedEffect()
        ).bounds(rightX, bottomY + 32, rightW, 18).build());

        // ── Кнопки знизу ──────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.save"), b -> save()
        ).bounds(cx - 110, this.height - 24, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"), b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 24, 100, 20).build());
    }

    private void applyFilter(String q) {
        filteredEffects = allEffects.stream()
                .filter(e -> q.isEmpty() ||
                        e.getDisplayName().getString().toLowerCase().contains(q.toLowerCase()) ||
                        ForgeRegistries.MOB_EFFECTS.getKey(e).toString().toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());
        rebuildWidgets();
    }

    private void addSelectedEffect() {
        if (selectedEffect == null) return;
        try {
            int amp = Integer.parseInt(amplifierInput.getValue().trim());
            int dur = Integer.parseInt(durationInput.getValue().trim());
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(selectedEffect);
            if (key != null) {
                effects.add(key + ":" + amp + ":" + dur);
                rebuildWidgets();
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
            MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(name));
            String displayName = ef != null ? ef.getDisplayName().getString() : name;
            return displayName + " Lv" + (amp + 1) + " " + dur + "t";
        } else if (parts.length == 3) {
            String name = parts[0];
            int amp = 0, dur = 0;
            try { amp = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
            try { dur = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
            MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(name));
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
            if (delta > 0 && effectScrollOffset > 0) { effectScrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && effectScrollOffset + effectVisibleMax < filteredEffects.size()) { effectScrollOffset++; rebuildWidgets(); }
        } else {
            if (delta > 0 && activeScrollOffset > 0) { activeScrollOffset--; rebuildWidgets(); }
            else if (delta < 0) { activeScrollOffset++; rebuildWidgets(); }
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, GuiTheme.TEXT);
        // Роздільник між двома панелями
        g.fill(cx, 24, cx + 1, this.height - 28, GuiTheme.BORDER);

        // Scissor: обрізаємо прокручувану зону, щоб елементи не накладались на нижні контролери
        int clipBot = this.height - 62;
        ScissorHelper.enable(0, 24, this.width, Math.max(1, clipBot - 24));
        ScissorHelper.renderBand(this.renderables, g, mouseX, mouseY, partial, 0, clipBot);
        g.flush();
        ScissorHelper.disable();

        // Нижня зона: поля вводу та кнопки — рендер без scissor
        ScissorHelper.renderBand(this.renderables, g, mouseX, mouseY, partial, clipBot, this.height);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
