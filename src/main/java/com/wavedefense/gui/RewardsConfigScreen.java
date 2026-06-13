package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

public class RewardsConfigScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private TextFieldWidget pointsRewardInput;
    private TextFieldWidget effectInput;     // ID ефекту, наприклад: minecraft:speed
    private TextFieldWidget amplifierInput;  // 0=I, 1=II, тощо
    private TextFieldWidget commandInput;    // команда при завершенні

    // Популярні ефекти для підказки
    private static final String[] EFFECT_SUGGESTIONS = {
        "minecraft:speed", "minecraft:slowness", "minecraft:haste",
        "minecraft:strength", "minecraft:resistance", "minecraft:fire_resistance",
        "minecraft:regeneration", "minecraft:poison", "minecraft:wither",
        "minecraft:glowing", "minecraft:night_vision", "minecraft:weakness"
    };
    private int suggestionIndex = 0;

    public RewardsConfigScreen(Screen parentScreen, WaveConfig waveConfig) {
        super(new TranslationTextComponent("wavedefense.title.wave_rewards", waveConfig.getWaveNumber()));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 38;

        // --- Поінти за завершення хвилі ---
        this.addButton(new Button(cx - 180, y, 280, 16, new TranslationTextComponent("wavedefense.auto.поінти_за_завершення_хвилі_всім_96a12997"), button -> {})).active = false;

        pointsRewardInput = new TextFieldWidget(this.font, cx + 105, y, 75, 20, new TranslationTextComponent("wavedefense.auto.поінти_66d72273"));
        pointsRewardInput.setValue(String.valueOf(waveConfig.getPointsReward()));
        pointsRewardInput.setMaxLength(7);
        this.addButton(pointsRewardInput);
        y += 30;

        // --- Ефект на гравців усю хвилю ---
        this.addButton(new Button(cx - 180, y, 230, 16, new TranslationTextComponent("wavedefense.auto.ефект_на_гравців_весь_час_хвилі_ab514b9d"), button -> {})).active = false;
        y += 18;

        effectInput = new TextFieldWidget(this.font, cx - 180, y, 220, 20, new StringTextComponent("minecraft:speed"));
        effectInput.setValue(waveConfig.hasEffect() ? waveConfig.getWaveEffect().toString() : "");
        effectInput.setMaxLength(100);
        this.addButton(effectInput);

        // Кнопка очистити ефект
        this.addButton(new Button(cx + 44, y, 18, 20, new StringTextComponent("✕"), button -> { effectInput.setValue(""); amplifierInput.setValue("0"); }));

        // Кнопки-підказки популярних ефектів
        this.addButton(new Button(cx + 68, y, 18, 20, new StringTextComponent("◀"), button -> {
                    suggestionIndex = (suggestionIndex - 1 + EFFECT_SUGGESTIONS.length) % EFFECT_SUGGESTIONS.length;
                    effectInput.setValue(EFFECT_SUGGESTIONS[suggestionIndex]);
                }));
        this.addButton(new Button(cx + 88, y, 18, 20, new StringTextComponent("▶"), button -> {
                    suggestionIndex = (suggestionIndex + 1) % EFFECT_SUGGESTIONS.length;
                    effectInput.setValue(EFFECT_SUGGESTIONS[suggestionIndex]);
                }));

        this.addButton(new Button(cx + 108, y, 70, 20, new TranslationTextComponent("wavedefense.auto.підбір_43b3ce78"), button -> {})).active = false;
        y += 24;

        // Рівень ефекту
        this.addButton(new Button(cx - 180, y, 230, 16, new TranslationTextComponent("wavedefense.auto.рівень_ефекту_0_i_1_ii_2_iii_2a574a95"), button -> {})).active = false;
        amplifierInput = new TextFieldWidget(this.font, cx + 55, y, 45, 20, new StringTextComponent("0"));
        amplifierInput.setValue(String.valueOf(waveConfig.getWaveEffectAmplifier()));
        amplifierInput.setMaxLength(1);
        amplifierInput.setFilter(s -> s.matches("[0-4]?"));
        this.addButton(amplifierInput);
        y += 28;

        // --- Команда при завершенні хвилі ---
        this.addButton(new Button(cx - 180, y, 230, 16, new TranslationTextComponent("wavedefense.auto.команда_при_завершенні_хвилі_19a42be6"), button -> {})).active = false;
        y += 18;

        commandInput = new TextFieldWidget(this.font, cx - 180, y, 360, 20, new TranslationTextComponent("wavedefense.auto.say_wave_done_489b73c2"));
        commandInput.setValue(waveConfig.getCompletionCommand());
        commandInput.setMaxLength(256);
        this.addButton(commandInput);
        y += 18;

        // Підказки щодо змінних
        this.addButton(new Button(cx - 180, y, 360, 14, new TranslationTextComponent("wavedefense.auto.змінні_location_wave_players_0f06cf7b"), button -> {})).active = false;
        y += 6;

        // Приклади
        this.addButton(new Button(cx - 180, y, 360, 14, new TranslationTextComponent("wavedefense.auto.приклад_say_players_пройшли_хвил_aa4f88d1"), button -> {})).active = false;

        // Кнопки
        this.addButton(new Button(cx - 110, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.auto.зберегти_617e5dc0"), button -> { save(); this.minecraft.setScreen(parentScreen); }));

        this.addButton(new Button(cx + 10, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), button -> this.minecraft.setScreen(parentScreen)));
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
    public void render(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(graphics, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(graphics, this.font, this.title, this.width / 2, 12, GuiTheme.TEXT);

        // Показуємо поточний ефект якщо вибраний
        if (effectInput != null && !effectInput.getValue().isEmpty()) {
            String preview = net.minecraft.client.resources.I18n.get(
                    "wavedefense.label.wave_effect_preview",
                    effectInput.getValue(), waveConfig.getWaveEffectAmplifier() + 1);
            com.wavedefense.gui.GuiCompat.drawString(graphics, this.font, preview, this.width / 2 - 180, this.height - 48, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
