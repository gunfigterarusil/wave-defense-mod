package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.wavedefense.gui.TooltipHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Редактор параметрів конкретного моба у хвилі.
 * Додано: вибір броні (4 слоти), зброя (mainHand/offHand), ефекти.
 * Підказки при наведенні (feature #1).
 */
public class WaveMobEditScreen extends Screen {
    private final Screen parentScreen;
    private final WaveConfig waveConfig;
    private final ResourceLocation mobType;
    private final int mobIndex;

    private EditBox countInput;
    private EditBox growthPerWaveInput;
    private EditBox spawnChanceInput;
    private EditBox pointsPerKillInput;

    // Поточний стан спорядження (зберігається між перебудовами)
    private WaveMob editMob;

    // Tooltip підказки
    private static final java.util.Map<String, String> TOOLTIPS = new java.util.HashMap<>();
    static {
        TOOLTIPS.put("count",   "Скільки мобів цього типу спавниться за хвилю");
        TOOLTIPS.put("growth",  "На скільки збільшується кількість мобів з кожною хвилею");
        TOOLTIPS.put("chance",  "Шанс (1-100%) що цей моб з'явиться у хвилі");
        TOOLTIPS.put("points",  "Кількість очок гравцю за вбивство цього моба");
        TOOLTIPS.put("armor",   "Видати мобам цього типу броню. Вибір через меню предметів");
        TOOLTIPS.put("weapon",  "Видати мобам основну зброю (права рука)");
        TOOLTIPS.put("effects", "Додати мобам ефекти (зілля). Формат: effectId:рівень:тіків");
    }

    public WaveMobEditScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.translatable("wavedefense.auto.редагування_моба_a6d30768"));
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

        var entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobType);
        String mobName = entityType != null ? entityType.getDescription().getString() : "???";

        // Назва моба
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.моб_value_07edda18", mobName), button -> {}
        ).bounds(cx - 150, startY - 20, 300, 18).build()).active = false;

        // Кількість мобів
        addLabeledField(cx, startY, net.minecraft.client.resources.language.I18n.get("wavedefense.label.mob_count_wave"), "count");
        countInput = new EditBox(this.font, cx + 45, startY, 80, 20, Component.translatable("wavedefense.auto.кількість_df256936"));
        countInput.setValue(String.valueOf(editMob.getCount()));
        this.addRenderableWidget(countInput);
        startY += 26;

        // Приріст
        addLabeledField(cx, startY, net.minecraft.client.resources.language.I18n.get("wavedefense.label.mob_growth_per_wave"), "growth");
        growthPerWaveInput = new EditBox(this.font, cx + 45, startY, 80, 20, Component.translatable("wavedefense.auto.приріст_5779238f"));
        growthPerWaveInput.setValue(String.valueOf(editMob.getGrowthPerWave()));
        this.addRenderableWidget(growthPerWaveInput);
        startY += 26;

        // Шанс появи
        addLabeledField(cx, startY, net.minecraft.client.resources.language.I18n.get("wavedefense.label.mob_spawn_chance"), "chance");
        spawnChanceInput = new EditBox(this.font, cx + 45, startY, 80, 20, Component.translatable("wavedefense.auto.шанс_7ce3c7bb"));
        spawnChanceInput.setValue(String.valueOf(editMob.getSpawnChance()));
        this.addRenderableWidget(spawnChanceInput);
        startY += 26;

        // Поінти за вбивство
        addLabeledField(cx, startY, net.minecraft.client.resources.language.I18n.get("wavedefense.label.mob_points_per_kill"), "points");
        pointsPerKillInput = new EditBox(this.font, cx + 45, startY, 80, 20, Component.translatable("wavedefense.auto.поінти_66d72273"));
        pointsPerKillInput.setValue(String.valueOf(editMob.getPointsPerKill()));
        this.addRenderableWidget(pointsPerKillInput);
        startY += 30;

        // ── БРОНЯ ──────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.броня_мобів_0998c14b"), b -> {}
        ).bounds(cx - 150, startY, 300, 12).build()).active = false;
        startY += 14;

        String[] armorLabels = {
            net.minecraft.client.resources.language.I18n.get("wavedefense.label.armor_slot.helmet"),
            net.minecraft.client.resources.language.I18n.get("wavedefense.label.armor_slot.chest"),
            net.minecraft.client.resources.language.I18n.get("wavedefense.label.armor_slot.legs"),
            net.minecraft.client.resources.language.I18n.get("wavedefense.label.armor_slot.boots")
        };
        ItemStack[] armorSlots = {editMob.getHelmet(), editMob.getChestplate(), editMob.getLeggings(), editMob.getBoots()};
        int armorW = 70, armorGap = 4;
        int totalArmorW = 4 * armorW + 3 * armorGap;
        int armorLeft = cx - totalArmorW / 2;

        for (int i = 0; i < 4; i++) {
            final int si = i;
            int x = armorLeft + i * (armorW + armorGap);
            final ItemStack curSlotItem = armorSlots[i];

            this.addRenderableWidget(Button.builder(
                    Component.literal("§8" + armorLabels[i]), b -> {}
            ).bounds(x, startY, armorW, 10).build()).active = false;

            // Label показує назву обраного предмета якщо є — виділення
            String slotLbl;
            if (curSlotItem.isEmpty()) {
                slotLbl = "§8[—]";
            } else {
                String nm = curSlotItem.getHoverName().getString();
                slotLbl = "§6§l▶ §r§a" + (nm.length() > 8 ? nm.substring(0, 7) + "…" : nm);
            }
            this.addRenderableWidget(Button.builder(
                    Component.literal(slotLbl),
                    b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        switch (si) {
                            case 0 -> editMob.setHelmet(stack);
                            case 1 -> editMob.setChestplate(stack);
                            case 2 -> editMob.setLeggings(stack);
                            case 3 -> editMob.setBoots(stack);
                        }
                        rebuildWidgets();
                    }, curSlotItem))
            ).bounds(x, startY + 12, armorW - 16, 16).build());
            // Кнопка очищення слоту броні
            var clearArmor = this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    b -> {
                        switch (si) {
                            case 0 -> editMob.setHelmet(ItemStack.EMPTY);
                            case 1 -> editMob.setChestplate(ItemStack.EMPTY);
                            case 2 -> editMob.setLeggings(ItemStack.EMPTY);
                            case 3 -> editMob.setBoots(ItemStack.EMPTY);
                        }
                        rebuildWidgets();
                    }
            ).bounds(x + armorW - 14, startY + 12, 14, 16).build());
            clearArmor.active = !curSlotItem.isEmpty();
        }
        startY += 32;

        // ── ЗБРОЯ ─────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.зброя_мобів_06ba2742"), b -> {}
        ).bounds(cx - 150, startY, 300, 12).build()).active = false;
        startY += 14;

        {
            final ItemStack curMain = editMob.getMainHand();
            String mainNm = curMain.isEmpty() ? "" : curMain.getHoverName().getString();
            String mainLabel = curMain.isEmpty()
                ? net.minecraft.client.resources.language.I18n.get("wavedefense.label.mainhand_empty")
                : "§6§l▶ §r§a" + (mainNm.length() > 9 ? mainNm.substring(0, 8) + "…" : mainNm);
            this.addRenderableWidget(Button.builder(
                    Component.literal(mainLabel),
                    b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editMob.setMainHand(stack); rebuildWidgets();
                    }, curMain))
            ).bounds(cx - 150, startY, 120, 20).build());
            // Кнопка очищення основної руки
            var clearMain = this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    b -> { editMob.setMainHand(ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(cx - 26, startY, 20, 20).build());
            clearMain.active = !curMain.isEmpty();
        }
        {
            final ItemStack curOff = editMob.getOffHand();
            String offNm = curOff.isEmpty() ? "" : curOff.getHoverName().getString();
            String offLabel = curOff.isEmpty()
                ? net.minecraft.client.resources.language.I18n.get("wavedefense.label.offhand_empty")
                : "§6§l▶ §r§a" + (offNm.length() > 9 ? offNm.substring(0, 8) + "…" : offNm);
            this.addRenderableWidget(Button.builder(
                    Component.literal(offLabel),
                    b -> minecraft.setScreen(new ItemSelectionScreen(this, stack -> {
                        editMob.setOffHand(stack); rebuildWidgets();
                    }, curOff))
            ).bounds(cx + 5, startY, 120, 20).build());
            // Кнопка очищення лівої руки
            var clearOff = this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"),
                    b -> { editMob.setOffHand(ItemStack.EMPTY); rebuildWidgets(); }
            ).bounds(cx + 129, startY, 20, 20).build());
            clearOff.active = !curOff.isEmpty();
        }
        startY += 26;

        // ── ЕФЕКТИ ────────────────────────────────────────────────────────
        int effectCount = editMob.getEffects().size();
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.ефекти_value_2868f0cb", effectCount + ")"),
                b -> minecraft.setScreen(new MobEffectsEditorScreen(this, editMob))
        ).bounds(cx - 150, startY, 300, 20).build());

        // Кнопки збереження
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.зберегти_b7c070cf"), b -> save()
        ).bounds(cx - 105, this.height - 32, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.скасувати_8b4c2025"), b -> this.minecraft.setScreen(parentScreen)
        ).bounds(cx + 5, this.height - 32, 100, 20).build());
    }

    private void addLabeledField(int cx, int y, String text, String tooltipKey) {
        this.addRenderableWidget(Button.builder(
                Component.literal(text), b -> {}
        ).bounds(cx - 150, y, 190, 18).build()).active = false;
    }

    private void save() {
        try {
            int count  = Integer.parseInt(countInput.getValue());
            int growth = Integer.parseInt(growthPerWaveInput.getValue());
            int chance = Math.min(100, Math.max(1, Integer.parseInt(spawnChanceInput.getValue())));
            int points = Integer.parseInt(pointsPerKillInput.getValue());

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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Рендер іконок спорядження
        renderEquipmentIcons(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderEquipmentIcons(GuiGraphics g, int mouseX, int mouseY) {
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
                g.renderItem(slots[i], x, armorRowY - 1);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= armorRowY - 1 && mouseY < armorRowY + 15) {
                    g.renderTooltip(this.font, slots[i], mouseX, mouseY);
                }
            }
        }

        // Зброя — іконки позиціонуються поряд з кнопками (cx-150 = початок mainHand, cx+5 = початок offHand)
        if (!editMob.getMainHand().isEmpty()) {
            int ix = cx - 150 + 122; // після кнопки 120px + 2px відступ
            g.renderItem(editMob.getMainHand(), ix, armorRowY + 48);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= armorRowY + 48 && mouseY < armorRowY + 64)
                g.renderTooltip(this.font, editMob.getMainHand(), mouseX, mouseY);
        }
        if (!editMob.getOffHand().isEmpty()) {
            int ix = cx + 5 + 122;
            g.renderItem(editMob.getOffHand(), ix, armorRowY + 48);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= armorRowY + 48 && mouseY < armorRowY + 64)
                g.renderTooltip(this.font, editMob.getOffHand(), mouseX, mouseY);
        }
    }

    private String getMobTip(String label) {
        if (label.contains("Шолом") || label.contains("Нагрудник") || label.contains("Поножі") || label.contains("Чоботи"))
            return TooltipHelper.MOB_ARMOR;
        if (label.contains("зброю") || label.contains("MainHand") || label.contains("зброя"))
            return TooltipHelper.MOB_WEAPON;
        if (label.contains("Ефект") || label.contains("ефект"))
            return TooltipHelper.MOB_EFFECT;
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
