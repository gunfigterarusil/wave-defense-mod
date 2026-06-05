package com.wavedefense.gui;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

public class WaveMobSettingsScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final int mobIndex;
    private final WaveMob mob;

    private TextFieldWidget countInput;
    private TextFieldWidget growthInput;
    private TextFieldWidget chanceInput;
    private TextFieldWidget pointsInput;

    public WaveMobSettingsScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(new TranslationTextComponent("wavedefense.auto.налаштування_моба_67f0a0ef"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;
        this.mob = waveConfig.getMobs().get(mobIndex);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(mob.getMobType());
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        this.addButton(new Button(centerX - 150, 30, 300, 20, new TranslationTextComponent("wavedefense.auto.моб_value_07edda18", mobName), button -> {})).active = false;

        this.addButton(new Button(centerX - 150, startY, 120, 20, new TranslationTextComponent("wavedefense.auto.кількість_мобів_f530588e"), button -> {})).active = false;

        countInput = new TextFieldWidget(this.font, centerX - 25, startY, 80, 20,
                new TranslationTextComponent("wavedefense.auto.кількість_df256936"));
        countInput.setValue(String.valueOf(mob.getCount()));
        countInput.setMaxLength(3);
        this.addButton(countInput);

        this.addButton(new Button(centerX - 150, startY + 35, 120, 20, new TranslationTextComponent("wavedefense.auto.приріст_за_хвилю_9514cfe7"), button -> {})).active = false;

        growthInput = new TextFieldWidget(this.font, centerX - 25, startY + 35, 80, 20,
                new TranslationTextComponent("wavedefense.auto.приріст_5779238f"));
        growthInput.setValue(String.valueOf(mob.getGrowthPerWave()));
        growthInput.setMaxLength(3);
        this.addButton(growthInput);

        this.addButton(new Button(centerX - 150, startY + 70, 140, 20, new TranslationTextComponent("wavedefense.auto.шанс_появи_1_100_8c7961cd"), button -> {})).active = false;

        chanceInput = new TextFieldWidget(this.font, centerX - 5, startY + 70, 80, 20,
                new TranslationTextComponent("wavedefense.auto.шанс_7ce3c7bb"));
        chanceInput.setValue(String.valueOf(mob.getSpawnChance()));
        chanceInput.setMaxLength(3);
        this.addButton(chanceInput);

        this.addButton(new Button(centerX - 150, startY + 105, 140, 20, new TranslationTextComponent("wavedefense.auto.поінтів_за_вбивство_04cc709e"), button -> {})).active = false;

        pointsInput = new TextFieldWidget(this.font, centerX - 5, startY + 105, 80, 20,
                new TranslationTextComponent("wavedefense.auto.поінти_66d72273"));
        pointsInput.setValue(String.valueOf(mob.getPointsPerKill()));
        pointsInput.setMaxLength(4);
        this.addButton(pointsInput);

        this.addButton(new Button(centerX - 110, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.auto.зберегти_bf610d49"), button -> save()));

        this.addButton(new Button(centerX + 10, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), button -> this.minecraft.setScreen(parentScreen)));
    }

    private void save() {
        // Clamp each field individually so a partially-typed value doesn't abort the whole save.
        int count  = parseIntSafe(countInput.getValue(),  mob.getCount());
        int growth = parseIntSafe(growthInput.getValue(), mob.getGrowthPerWave());
        int chance = Math.min(100, Math.max(1, parseIntSafe(chanceInput.getValue(), mob.getSpawnChance())));
        int points = parseIntSafe(pointsInput.getValue(), mob.getPointsPerKill());

        mob.setCount(Math.max(1, count));
        mob.setGrowthPerWave(Math.max(0, growth));
        mob.setSpawnChance(chance);
        mob.setPointsPerKill(Math.max(0, points));

        this.minecraft.setScreen(parentScreen);
    }

    private static int parseIntSafe(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
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
        com.wavedefense.gui.GuiCompat.drawCenteredString(graphics, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
