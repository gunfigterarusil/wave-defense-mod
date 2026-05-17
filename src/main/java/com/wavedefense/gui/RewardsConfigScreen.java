package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RewardsConfigScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private EditBox pointsRewardInput;
    private EditBox effectInput;     // ID ефекту, наприклад: minecraft:speed
    private EditBox amplifierInput;  // 0=I, 1=II, тощо
    private EditBox commandInput;    // команда при завершенні

    // Популярні ефекти для підказки
    private static final String[] EFFECT_SUGGESTIONS = {
        "minecraft:speed", "minecraft:slowness", "minecraft:haste",
        "minecraft:strength", "minecraft:resistance", "minecraft:fire_resistance",
        "minecraft:regeneration", "minecraft:poison", "minecraft:wither",
        "minecraft:glowing", "minecraft:night_vision", "minecraft:weakness"
    };
    private int suggestionIndex = 0;

    public RewardsConfigScreen(Screen parentScreen, WaveConfig waveConfig) {
        super(Component.translatable("wavedefense.title.wave_rewards", waveConfig.getWaveNumber()));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 38;

        // --- Поінти за завершення хвилі ---
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.поінти_за_завершення_хвилі_всім_96a12997"), button -> {}
        ).bounds(cx - 180, y, 280, 16).build()).active = false;

        pointsRewardInput = new EditBox(this.font, cx + 105, y, 75, 20, Component.translatable("wavedefense.auto.поінти_66d72273"));
        pointsRewardInput.setValue(String.valueOf(waveConfig.getPointsReward()));
        pointsRewardInput.setMaxLength(7);
        this.addRenderableWidget(pointsRewardInput);
        y += 30;

        // --- Ефект на гравців усю хвилю ---
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ефект_на_гравців_весь_час_хвилі_ab514b9d"), button -> {}
        ).bounds(cx - 180, y, 230, 16).build()).active = false;
        y += 18;

        effectInput = new EditBox(this.font, cx - 180, y, 220, 20, Component.literal("minecraft:speed"));
        effectInput.setValue(waveConfig.hasEffect() ? waveConfig.getWaveEffect().toString() : "");
        effectInput.setMaxLength(100);
        this.addRenderableWidget(effectInput);

        // Кнопка очистити ефект
        this.addRenderableWidget(Button.builder(
                Component.literal("✕"), button -> { effectInput.setValue(""); amplifierInput.setValue("0"); }
        ).bounds(cx + 44, y, 18, 20).build());

        // Кнопки-підказки популярних ефектів
        this.addRenderableWidget(Button.builder(
                Component.literal("◀"), button -> {
                    suggestionIndex = (suggestionIndex - 1 + EFFECT_SUGGESTIONS.length) % EFFECT_SUGGESTIONS.length;
                    effectInput.setValue(EFFECT_SUGGESTIONS[suggestionIndex]);
                }
        ).bounds(cx + 68, y, 18, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("▶"), button -> {
                    suggestionIndex = (suggestionIndex + 1) % EFFECT_SUGGESTIONS.length;
                    effectInput.setValue(EFFECT_SUGGESTIONS[suggestionIndex]);
                }
        ).bounds(cx + 88, y, 18, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.підбір_43b3ce78"), button -> {}
        ).bounds(cx + 108, y, 70, 20).build()).active = false;
        y += 24;

        // Рівень ефекту
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.рівень_ефекту_0_i_1_ii_2_iii_2a574a95"), button -> {}
        ).bounds(cx - 180, y, 230, 16).build()).active = false;
        amplifierInput = new EditBox(this.font, cx + 55, y, 45, 20, Component.literal("0"));
        amplifierInput.setValue(String.valueOf(waveConfig.getWaveEffectAmplifier()));
        amplifierInput.setMaxLength(1);
        amplifierInput.setFilter(s -> s.matches("[0-4]?"));
        this.addRenderableWidget(amplifierInput);
        y += 28;

        // --- Команда при завершенні хвилі ---
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.команда_при_завершенні_хвилі_19a42be6"), button -> {}
        ).bounds(cx - 180, y, 230, 16).build()).active = false;
        y += 18;

        commandInput = new EditBox(this.font, cx - 180, y, 360, 20, Component.translatable("wavedefense.auto.say_wave_done_489b73c2"));
        commandInput.setValue(waveConfig.getCompletionCommand());
        commandInput.setMaxLength(256);
        this.addRenderableWidget(commandInput);
        y += 18;

        // Підказки щодо змінних
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.змінні_location_wave_players_0f06cf7b"), button -> {}
        ).bounds(cx - 180, y, 360, 14).build()).active = false;
        y += 6;

        // Приклади
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.приклад_say_players_пройшли_хвил_aa4f88d1"),
                button -> {}
        ).bounds(cx - 180, y, 360, 14).build()).active = false;

        // Кнопки
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.зберегти_617e5dc0"),
                button -> { save(); this.minecraft.setScreen(parentScreen); }
        ).bounds(cx - 110, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.скасувати_8b4c2025"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(cx + 10, this.height - 28, 100, 20).build());
    }

    private void save() {
        // Поінти
        try { waveConfig.setPointsReward(Integer.parseInt(pointsRewardInput.getValue())); }
        catch (NumberFormatException ignored) {}

        // Ефект
        String effectStr = effectInput.getValue().trim();
        if (effectStr.isEmpty()) {
            waveConfig.setWaveEffect(null);
        } else {
            try { waveConfig.setWaveEffect(new ResourceLocation(effectStr)); }
            catch (Exception ignored) { waveConfig.setWaveEffect(null); }
        }

        // Рівень ефекту
        try { waveConfig.setWaveEffectAmplifier(Integer.parseInt(amplifierInput.getValue())); }
        catch (NumberFormatException ignored) { waveConfig.setWaveEffectAmplifier(0); }

        // Команда
        waveConfig.setCompletionCommand(commandInput.getValue().trim());
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Показуємо поточний ефект якщо вибраний
        if (effectInput != null && !effectInput.getValue().isEmpty()) {
            String preview = net.minecraft.client.resources.language.I18n.get(
                    "wavedefense.label.wave_effect_preview",
                    effectInput.getValue(), waveConfig.getWaveEffectAmplifier() + 1);
            graphics.drawString(this.font, preview, this.width / 2 - 180, this.height - 48, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
