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
        super(Component.literal("Налаштування хвилі " + waveConfig.getWaveNumber()));
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
                Component.literal("§7Поінти за завершення хвилі (всім гравцям):"), button -> {}
        ).bounds(cx - 180, y, 280, 16).build()).active = false;

        pointsRewardInput = new EditBox(this.font, cx + 105, y, 75, 20, Component.literal("Поінти"));
        pointsRewardInput.setValue(String.valueOf(waveConfig.getPointsReward()));
        pointsRewardInput.setMaxLength(7);
        this.addRenderableWidget(pointsRewardInput);
        y += 30;

        // --- Ефект на гравців усю хвилю ---
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ефект на гравців (весь час хвилі):"), button -> {}
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
                Component.literal("§7←→ підбір"), button -> {}
        ).bounds(cx + 108, y, 70, 20).build()).active = false;
        y += 24;

        // Рівень ефекту
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Рівень ефекту (0=I, 1=II, 2=III...):"), button -> {}
        ).bounds(cx - 180, y, 230, 16).build()).active = false;
        amplifierInput = new EditBox(this.font, cx + 55, y, 45, 20, Component.literal("0"));
        amplifierInput.setValue(String.valueOf(waveConfig.getWaveEffectAmplifier()));
        amplifierInput.setMaxLength(1);
        amplifierInput.setFilter(s -> s.matches("[0-4]?"));
        this.addRenderableWidget(amplifierInput);
        y += 28;

        // --- Команда при завершенні хвилі ---
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Команда при завершенні хвилі:"), button -> {}
        ).bounds(cx - 180, y, 230, 16).build()).active = false;
        y += 18;

        commandInput = new EditBox(this.font, cx - 180, y, 360, 20, Component.literal("say Wave done!"));
        commandInput.setValue(waveConfig.getCompletionCommand());
        commandInput.setMaxLength(256);
        this.addRenderableWidget(commandInput);
        y += 18;

        // Підказки щодо змінних
        this.addRenderableWidget(Button.builder(
                Component.literal("§8Змінні: §7%location% %wave% %players%"), button -> {}
        ).bounds(cx - 180, y, 360, 14).build()).active = false;
        y += 6;

        // Приклади
        this.addRenderableWidget(Button.builder(
                Component.literal("§8Приклад: say %players% пройшли хвилю %wave% у %location%!"),
                button -> {}
        ).bounds(cx - 180, y, 360, 14).build()).active = false;

        // Кнопки
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"),
                button -> { save(); this.minecraft.setScreen(parentScreen); }
        ).bounds(cx - 110, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"),
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Показуємо поточний ефект якщо вибраний
        if (effectInput != null && !effectInput.getValue().isEmpty()) {
            String preview = "§7Ефект: §b" + effectInput.getValue()
                    + " §7рівень §e" + (waveConfig.getWaveEffectAmplifier() + 1)
                    + " §7(весь час хвилі)";
            graphics.drawString(this.font, preview, this.width / 2 - 180, this.height - 48, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
