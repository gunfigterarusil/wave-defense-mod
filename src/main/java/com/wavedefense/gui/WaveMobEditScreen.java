package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Редактор параметрів конкретного моба у хвилі.
 * Додано: вибір броні (4 слоти), зброя (mainHand/offHand), ефекти.
 * Підказки при наведенні (feature #1).
 */
public class WaveMobEditScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final ResourceLocation mobType;
    private final int mobIndex;

    private TextFieldWidget countInput;
    private TextFieldWidget growthPerWaveInput;
    private TextFieldWidget spawnChanceInput;
    private TextFieldWidget pointsPerKillInput;

    // Поточний стан спорядження (зберігається між перебудовами)
    private WaveMob editMob;

    // Tooltip keys — resolved via I18n.get() at render time
    private static final java.util.Map<String, String> TOOLTIPS = new java.util.HashMap<>();
    static {
        TOOLTIPS.put("count",   "wavedefense.tooltip.mob_edit.count");
        TOOLTIPS.put("growth",  "wavedefense.tooltip.mob_edit.growth");
        TOOLTIPS.put("chance",  "wavedefense.tooltip.mob_edit.chance");
        TOOLTIPS.put("points",  "wavedefense.tooltip.mob_edit.points");
        TOOLTIPS.put("armor",   "wavedefense.tooltip.mob_edit.armor");
        TOOLTIPS.put("weapon",  "wavedefense.tooltip.mob_edit.weapon");
        TOOLTIPS.put("effects", "wavedefense.tooltip.mob_edit.effects");
    }

    public WaveMobEditScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(new TranslationTextComponent("wavedefense.auto.редагування_моба_a6d30768"));
        this.parentScreen = parentScreen;
        this.waveConfig = waveConfig;
        this.mobIndex = mobIndex;
        this.mobType = waveConfig.getMobs().get(mobIndex).getMobType();
        // Клонуємо для редагування
        this.editMob = cloneMob(waveConfig.getMobs().get(mobIndex));
    }

    private WaveMob cloneMob(WaveMob src) {
        WaveMob copy = new WaveMob(src.getMobType(), src.getCount(), src.getGrowthPerWave(),
                src.getSpawnChance(), src.getPointsPerKill());
        copy.setHelmet(src.getHelmet());
        copy.setChestplate(src.getChestplate());
        copy.setLeggings(src.getLeggings());
        copy.setBoots(src.getBoots());
        copy.setMainHand(src.getMainHand());
        copy.setOffHand(src.getOffHand());
        copy.setEffects(src.getEffects());
        return copy;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int startY = 35;

        net.minecraft.entity.EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(mobType);
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        // Назва моба
        this.addButton(new Button(cx - 150, startY - 20, 300, 18, new TranslationTextComponent("wavedefense.auto.моб_value_07edda18", mobName), button -> {})).active = false;

        // Кількість мобів
        addLabeledField(cx, startY, net.minecraft.client.resources.I18n.get("wavedefense.label.mob_count_wave"), "count");
        countInput = new TextFieldWidget(this.font, cx + 45, startY, 80, 20, new TranslationTextComponent("wavedefense.auto.кількість_df256936"));
        countInput.setValue(String.valueOf(editMob.getCount()));
        this.addButton(countInput);
        startY += 26;

        // Приріст
        addLabeledField(cx, startY, net.minecraft.client.resources.I18n.get("wavedefense.label.mob_growth_per_wave"), "growth");
        growthPerWaveInput = new TextFieldWidget(this.font, cx + 45, startY, 80, 20, new TranslationTextComponent("wavedefense.auto.приріст_5779238f"));
        growthPerWaveInput.setValue(String.valueOf(editMob.getGrowthPerWave()));
        this.addButton(growthPerWaveInput);
        startY += 26;

        // Шанс появи
        addLabeledField(cx, startY, net.minecraft.client.resources.I18n.get("wavedefense.label.mob_spawn_chance"), "chance");
        spawnChanceInput = new TextFieldWidget(this.font, cx + 45, startY, 80, 20, new TranslationTextComponent("wavedefense.auto.шанс_7ce3c7bb"));
        spawnChanceInput.setValue(String.valueOf(editMob.getSpawnChance()));
        this.addButton(spawnChanceInput);
        startY += 26;

        // Поінти за вбивство
        addLabeledField(cx, startY, net.minecraft.client.resources.I18n.get("wavedefense.label.mob_points_per_kill"), "points");
        pointsPerKillInput = new TextFieldWidget(this.font, cx + 45, startY, 80, 20, new TranslationTextComponent("wavedefense.auto.поінти_66d72273"));
        pointsPerKillInput.setValue(String.valueOf(editMob.getPointsPerKill()));
        this.addButton(pointsPerKillInput);
        startY += 30;

        // ── БРОНЯ ──────────────────────────────────────────────────────────
        this.addButton(new Button(cx - 150, startY, 300, 12, new TranslationTextComponent("wavedefense.auto.броня_мобів_0998c14b"), b -> {})).active = false;
        startY += 14;

        String[] armorLabels = {
            net.minecraft.client.resources.I18n.get("wavedefense.label.armor_slot.helmet"),
            net.minecraft.client.resources.I18n.get("wavedefense.label.armor_slot.chest"),
            net.minecraft.client.resources.I18n.get("wavedefense.label.armor_slot.legs"),
            net.minecraft.client.resources.I18n.get("wavedefense.label.armor_slot.boots")
        };
        ItemStack[] armorSlots = {editMob.getHelmet(), editMob.getChestplate(), editMob.getLeggings(), editMob.getBoots()};
        int armorW = 70, armorGap = 4;
        int totalArmorW = 4 * armorW + 3 * armorGap;
        int armorLeft = cx - totalArmorW / 2;

        for (int i = 0; i < 4; i++) {
            final int si = i;
            int x = armorLeft + i * (armorW + armorGap);
            final ItemStack curSlotItem = armorSlots[i];

            this.addButton(new Button(x, startY, armorW, 10, new StringTextComponent("§8" + armorLabels[i]), b -> {})).active = false;

            // Label показує назву обраного предмета якщо є — виділення
            String slotLbl;
            if (curSlotItem.isEmpty()) {
                slotLbl = "§8[—]";
            } else {
                String nm = curSlotItem.getHoverName().getString();
                slotLbl = "§6§l▶ §r§a" + (nm.length() > 8 ? nm.substring(0, 7) + "…" : nm);
            }
            this.addButton(new Button(x, startY + 12, armorW - 16, 16, new StringTextComponent(slotLbl), b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        switch (si) { case 0: editMob.setHelmet(stack); break; case 1: editMob.setChestplate(stack); break; case 2: editMob.setLeggings(stack); break; case 3: editMob.setBoots(stack); break; }
                        init();
                    }, curSlotItem))));
            // Кнопка очищення слоту броні
            net.minecraft.client.gui.widget.button.Button clearArmor = this.addButton(new Button(x + armorW - 14, startY + 12, 14, 16, new StringTextComponent("§c✕"), b -> {
                        switch (si) { case 0: editMob.setHelmet(ItemStack.EMPTY); break; case 1: editMob.setChestplate(ItemStack.EMPTY); break; case 2: editMob.setLeggings(ItemStack.EMPTY); break; case 3: editMob.setBoots(ItemStack.EMPTY); break; }
                        init();
                    }));
            clearArmor.active = !curSlotItem.isEmpty();
        }
        startY += 32;

        // ── ЗБРОЯ ─────────────────────────────────────────────────────────
        this.addButton(new Button(cx - 150, startY, 300, 12, new TranslationTextComponent("wavedefense.auto.зброя_мобів_06ba2742"), b -> {})).active = false;
        startY += 14;

        {
            final ItemStack curMain = editMob.getMainHand();
            String mainNm = curMain.isEmpty() ? "" : curMain.getHoverName().getString();
            String mainLabel = curMain.isEmpty()
                ? net.minecraft.client.resources.I18n.get("wavedefense.label.mainhand_empty")
                : "§6§l▶ §r§a" + (mainNm.length() > 9 ? mainNm.substring(0, 8) + "…" : mainNm);
            this.addButton(new Button(cx - 150, startY, 120, 20, new StringTextComponent(mainLabel), b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editMob.setMainHand(stack); init();
                    }, curMain))));
            // Кнопка очищення основної руки
            net.minecraft.client.gui.widget.button.Button clearMain = this.addButton(new Button(cx - 26, startY, 20, 20, new StringTextComponent("§c✕"), b -> { editMob.setMainHand(ItemStack.EMPTY); init(); }));
            clearMain.active = !curMain.isEmpty();
        }
        {
            final ItemStack curOff = editMob.getOffHand();
            String offNm = curOff.isEmpty() ? "" : curOff.getHoverName().getString();
            String offLabel = curOff.isEmpty()
                ? net.minecraft.client.resources.I18n.get("wavedefense.label.offhand_empty")
                : "§6§l▶ §r§a" + (offNm.length() > 9 ? offNm.substring(0, 8) + "…" : offNm);
            this.addButton(new Button(cx + 5, startY, 120, 20, new StringTextComponent(offLabel), b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editMob.setOffHand(stack); init();
                    }, curOff))));
            // Кнопка очищення лівої руки
            net.minecraft.client.gui.widget.button.Button clearOff = this.addButton(new Button(cx + 129, startY, 20, 20, new StringTextComponent("§c✕"), b -> { editMob.setOffHand(ItemStack.EMPTY); init(); }));
            clearOff.active = !curOff.isEmpty();
        }
        startY += 26;

        // ── ЕФЕКТИ ────────────────────────────────────────────────────────
        int effectCount = editMob.getEffects().size();
        this.addButton(new Button(cx - 150, startY, 300, 20,
                new TranslationTextComponent("wavedefense.auto.ефекти_value_2868f0cb", effectCount + ")"),
                b -> minecraft.setScreen(new MobEffectsEditorScreen(this, editMob))));

        // Кнопки збереження
        this.addButton(new Button(cx - 105, this.height - 32, 100, 20, new TranslationTextComponent("wavedefense.auto.зберегти_b7c070cf"), b -> save()));
        this.addButton(new Button(cx + 5, this.height - 32, 100, 20, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), b -> this.minecraft.setScreen(parentScreen)));
    }

    private void addLabeledField(int cx, int y, String text, String tooltipKey) {
        String tipI18nKey = TOOLTIPS.get(tooltipKey);
        net.minecraft.client.gui.widget.button.Button btn = this.addButton(new Button(cx - 150, y, 190, 18, new StringTextComponent(text), b -> {}));
        btn.active = false;
        if (tipI18nKey != null) {
            /* setTooltip omitted on 1.16.5: btn */
        }
    }

    private void save() {
        try {
            int count  = Math.max(1, Integer.parseInt(countInput.getValue()));  // С4: min 1
            int growth = Math.max(0, Integer.parseInt(growthPerWaveInput.getValue()));
            int chance = Math.min(100, Math.max(1, Integer.parseInt(spawnChanceInput.getValue())));
            int points = Math.max(0, Integer.parseInt(pointsPerKillInput.getValue()));

            editMob.setCount(count);
            editMob.setGrowthPerWave(growth);
            editMob.setSpawnChance(chance);
            editMob.setPointsPerKill(points);

            if (mobIndex != -1) waveConfig.getMobs().set(mobIndex, editMob);
            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException ignored) {}
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
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 12, GuiTheme.TEXT);

        // Рендер іконок спорядження
        renderEquipmentIcons(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderEquipmentIcons(MatrixStack g, int mouseX, int mouseY) {
        int cx = this.width / 2;
        // Розраховуємо y броні (виходячи з init структури)
        int armorRowY = 35 + 26 * 4 + 14 + 12 + 2; // приблизно
        int armorW = 70, armorGap = 4;
        int totalW = 4 * armorW + 3 * armorGap;
        int armorLeft = cx - totalW / 2;

        ItemStack[] slots = {editMob.getHelmet(), editMob.getChestplate(), editMob.getLeggings(), editMob.getBoots()};
        for (int i = 0; i < 4; i++) {
            if (!slots[i].isEmpty()) {
                int x = armorLeft + i * (armorW + armorGap) + (armorW - 16) / 2;
                com.wavedefense.gui.GuiCompat.renderItem(g, slots[i], x, armorRowY - 1);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= armorRowY - 1 && mouseY < armorRowY + 15) {
                    com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, slots[i], mouseX, mouseY);
                }
            }
        }

        // Зброя — іконки позиціонуються поряд з кнопками (cx-150 = початок mainHand, cx+5 = початок offHand)
        if (!editMob.getMainHand().isEmpty()) {
            int ix = cx - 150 + 122; // після кнопки 120px + 2px відступ
            com.wavedefense.gui.GuiCompat.renderItem(g, editMob.getMainHand(), ix, armorRowY + 48);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= armorRowY + 48 && mouseY < armorRowY + 64)
                com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, editMob.getMainHand(), mouseX, mouseY);
        }
        if (!editMob.getOffHand().isEmpty()) {
            int ix = cx + 5 + 122;
            com.wavedefense.gui.GuiCompat.renderItem(g, editMob.getOffHand(), ix, armorRowY + 48);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= armorRowY + 48 && mouseY < armorRowY + 64)
                com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, editMob.getOffHand(), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
