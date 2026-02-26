package com.wavedefense.gui;

import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Редактор ефектів моба у хвилі.
 * Список ефектів з кнопками додати/видалити.
 * Формат збереження: "effectId:рівень:тіків"
 */
public class MobEffectsEditorScreen extends Screen {

    private final Screen parent;
    private final WaveMob mob;
    private List<String> effects;

    private List<MobEffect> allEffects;
    private List<MobEffect> filteredEffects;
    private EditBox searchBox;
    private int scrollOffset = 0;

    // Поля нового ефекту
    private EditBox amplifierInput;
    private EditBox durationInput;

    private MobEffect selectedEffect = null;

    public MobEffectsEditorScreen(Screen parent, WaveMob mob) {
        super(Component.literal("Ефекти моба"));
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
        super.init();
        int cx = this.width / 2;

        // Ліва частина: список поточних ефектів
        int leftW = cx - 10;
        int y = 30;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Активні ефекти (" + effects.size() + "):"), b -> {}
        ).bounds(4, y, leftW - 4, 14).build()).active = false;
        y += 16;

        for (int i = 0; i < effects.size(); i++) {
            final int fi = i;
            String ef = effects.get(i);
            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + formatEffect(ef)),
                    b -> {}
            ).bounds(4, y, leftW - 30, 18).build()).active = false;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    b -> { effects.remove(fi); rebuildWidgets(); }
            ).bounds(leftW - 24, y, 22, 18).build());
            y += 20;
        }

        // Права частина: вибір нового ефекту
        int rightX = cx + 5;
        int rightW = cx - 10;

        searchBox = new EditBox(this.font, rightX, 26, rightW - 2, 16, Component.literal("Пошук ефекту..."));
        searchBox.setResponder(s -> { scrollOffset = 0; applyFilter(s); });
        this.addRenderableWidget(searchBox);

        buildEffectList(rightX, rightW);

        // Поля для amplifier та duration
        int bottomY = this.height - 72;
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Рівень (0=1, 1=2...):"), b -> {}
        ).bounds(rightX, bottomY, rightW, 14).build()).active = false;
        amplifierInput = new EditBox(this.font, rightX, bottomY + 16, 80, 18, Component.literal("0"));
        amplifierInput.setValue("0");
        this.addRenderableWidget(amplifierInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Тривалість (тіків, 600=30c):"), b -> {}
        ).bounds(rightX + 85, bottomY, rightW - 85, 14).build()).active = false;
        durationInput = new EditBox(this.font, rightX + 85, bottomY + 16, 80, 18, Component.literal("600"));
        durationInput.setValue("600");
        this.addRenderableWidget(durationInput);

        // Додати ефект
        this.addRenderableWidget(Button.builder(
                Component.literal(selectedEffect != null ? "§a➕ Додати: " + selectedEffect.getDisplayName().getString() : "§7[Виберіть ефект]"),
                b -> addSelectedEffect()
        ).bounds(rightX, bottomY + 36, rightW, 20).build());

        // ОК / Скасувати
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), b -> save()
        ).bounds(cx - 110, this.height - 26, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), b -> this.minecraft.setScreen(parent)
        ).bounds(cx + 10, this.height - 26, 100, 20).build());
    }

    private void applyFilter(String q) {
        filteredEffects = allEffects.stream()
                .filter(e -> q.isEmpty() ||
                        e.getDisplayName().getString().toLowerCase().contains(q.toLowerCase()) ||
                        ForgeRegistries.MOB_EFFECTS.getKey(e).toString().toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());
        rebuildWidgets();
    }

    private void buildEffectList(int rightX, int rightW) {
        int startY = 46;
        int ipp = Math.max(3, (this.height - 140) / 18);
        for (int i = 0; i < Math.min(ipp, filteredEffects.size()); i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredEffects.size()) break;
            MobEffect ef = filteredEffects.get(idx);
            final MobEffect fef = ef;
            boolean sel = ef == selectedEffect;
            this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a▶ " + ef.getDisplayName().getString() : "§7" + ef.getDisplayName().getString()),
                    b -> { selectedEffect = fef; rebuildWidgets(); }
            ).bounds(rightX, startY + i * 18, rightW - 2, 16).build());
        }
    }

    private void addSelectedEffect() {
        if (selectedEffect == null) return;
        try {
            int amp = Integer.parseInt(amplifierInput.getValue());
            int dur = Integer.parseInt(durationInput.getValue());
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(selectedEffect);
            if (key != null) {
                effects.add(key + ":" + amp + ":" + dur);
                rebuildWidgets();
            }
        } catch (NumberFormatException ignored) {}
    }

    private String formatEffect(String raw) {
        String[] parts = raw.split(":");
        if (parts.length >= 3) {
            String id = parts[0] + (parts.length > 1 && parts[0].contains(":") ? "" : ":" + parts[1]);
            // "namespace:path:amp:dur"
            if (parts.length == 4) {
                return parts[0] + ":" + parts[1] + " Lv" + (Integer.parseInt(parts[2]) + 1) + " " + parts[3] + "t";
            }
            return raw;
        }
        return raw;
    }

    private void save() {
        mob.setEffects(effects);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        // Роздільник між двома панелями
        g.fill(this.width / 2, 20, this.width / 2 + 1, this.height - 30, 0xFF444444);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
