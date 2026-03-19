package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

public class WaveMobsEditorScreen extends Screen {
    private final Location location;
    private final int waveIndex;
    private final Screen parent;
    private final WaveConfig waveConfig;
    private int scrollOffset = 0;
    private EditBox mobCountInput;

    // Динамічна кількість рядків
    private int getItemsPerPage() {
        return Math.max(2, (this.height - 120) / 48);
    }

    public WaveMobsEditorScreen(Location location, int waveIndex, Screen parent) {
        super(Component.literal("Моби хвилі " + (waveIndex + 1)));
        this.location = location;
        this.waveIndex = waveIndex;
        this.parent = parent;
        this.waveConfig = location.getWaves().get(waveIndex);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int headerY = 30;

        // Підпис і поле кількості
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Кількість типів мобів:"), button -> {}
        ).bounds(centerX - 150, headerY, 140, 18).build()).active = false;

        mobCountInput = new EditBox(this.font, centerX - 5, headerY, 50, 20, Component.literal("К-сть"));
        mobCountInput.setValue(String.valueOf(waveConfig.getMobs().size()));
        mobCountInput.setMaxLength(2);
        this.addRenderableWidget(mobCountInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Застосувати"),
                button -> applyMobCount()
        ).bounds(centerX + 50, headerY, 90, 20).build());

        int startY = headerY + 26;
        int itemsPerPage = getItemsPerPage();
        int rowH = 46;

        if (waveConfig.getMobs().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Моби не додані. Встановіть кількість вище."),
                    button -> {}
            ).bounds(centerX - 150, startY, 300, 20).build()).active = false;
        } else {
            for (int i = 0; i < Math.min(itemsPerPage, waveConfig.getMobs().size()); i++) {
                int mobIndex = i + scrollOffset;
                if (mobIndex >= waveConfig.getMobs().size()) break;

                WaveMob mob = waveConfig.getMobs().get(mobIndex);
                int yPos = startY + (i * rowH);

                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mob.getMobType());
                String mobName = entityType != null ? entityType.getDescription().getString() : "???";

                // Рядок 1: назва + кнопки
                int nameWidth = Math.min(110, centerX - 30);
                this.addRenderableWidget(Button.builder(
                        Component.literal("§e#" + (mobIndex + 1) + " " + mobName),
                        button -> {}
                ).bounds(centerX - 150, yPos, nameWidth, 20).build()).active = false;

                final int finalMobIndex = mobIndex;
                this.addRenderableWidget(Button.builder(
                        Component.literal("Змінити"),
                        button -> selectMob(finalMobIndex)
                ).bounds(centerX - 150 + nameWidth + 4, yPos, 65, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("⚙ Налашт."),
                        button -> editMob(finalMobIndex)
                ).bounds(centerX - 150 + nameWidth + 73, yPos, 75, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("§c✕"),
                        button -> deleteMob(finalMobIndex)
                ).bounds(centerX - 150 + nameWidth + 152, yPos, 30, 20).build());

                // Рядок 2: статистика
                String info = String.format("§7К-сть: §f%d §7| Приріст: §f%d §7| Шанс: §f%d%% §7| Поінти: §f%d",
                        mob.getCount(), mob.getGrowthPerWave(), mob.getSpawnChance(), mob.getPointsPerKill());
                this.addRenderableWidget(Button.builder(
                        Component.literal(info), button -> {}
                ).bounds(centerX - 150, yPos + 22, 320, 18).build()).active = false;
            }

            // Скрол
            if (waveConfig.getMobs().size() > itemsPerPage) {
                int scrollX = centerX + 175;
                this.addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp()
                ).bounds(scrollX, startY, 22, 22).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown()
                ).bounds(scrollX, startY + (itemsPerPage - 1) * rowH, 22, 22).build());
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Готово"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 5, this.height - 28, 95, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ Без збереження"),
                button -> {
                    // Відновлюємо оригінальний стан хвилі (скидаємо зміни)
                    // Зберігаємо посилання на WaveConfigScreen і повертаємось без UpdateLocationPacket
                    if (parent instanceof WaveConfigScreen wcs) {
                        wcs.discardWaveChanges();
                    }
                    this.minecraft.setScreen(parent);
                }
        ).bounds(centerX - 105, this.height - 28, 105, 20).build());
    }

    private void applyMobCount() {
        try {
            int targetCount = Integer.parseInt(mobCountInput.getValue());
            if (targetCount < 0 || targetCount > 10) return;

            int currentCount = waveConfig.getMobs().size();

            if (targetCount > currentCount) {
                ResourceLocation zombieId = ResourceLocation.tryParse("minecraft:zombie");
                for (int i = currentCount; i < targetCount; i++) {
                    waveConfig.addMob(new WaveMob(zombieId, 5, 1, 100, 10));
                }
            } else {
                while (waveConfig.getMobs().size() > targetCount) {
                    waveConfig.getMobs().remove(waveConfig.getMobs().size() - 1);
                }
            }

            if (scrollOffset > 0 && scrollOffset >= waveConfig.getMobs().size()) {
                scrollOffset = Math.max(0, waveConfig.getMobs().size() - getItemsPerPage());
            }
            this.rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void selectMob(int mobIndex) {
        this.minecraft.setScreen(new MobSelectionScreen(this, waveConfig, mobIndex));
    }

    private void editMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            this.minecraft.setScreen(new WaveMobEditScreen(this, waveConfig, mobIndex));
        }
    }

    private void deleteMob(int mobIndex) {
        if (mobIndex >= 0 && mobIndex < waveConfig.getMobs().size()) {
            waveConfig.removeMob(mobIndex);
            mobCountInput.setValue(String.valueOf(waveConfig.getMobs().size()));
            if (scrollOffset > 0 && scrollOffset >= waveConfig.getMobs().size()) {
                scrollOffset = Math.max(0, waveConfig.getMobs().size() - getItemsPerPage());
            }
            this.rebuildWidgets();
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) { scrollOffset--; this.rebuildWidgets(); }
    }

    private void scrollDown() {
        if (scrollOffset + getItemsPerPage() < waveConfig.getMobs().size()) { scrollOffset++; this.rebuildWidgets(); }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scrollUp(); else scrollDown();
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);
        if (!waveConfig.getMobs().isEmpty()) {
            graphics.drawString(this.font, "§7Налаштуйте кожного моба або видаліть непотрібних",
                    cx - 150, 20, 0xFFFFFF);
        }
        int listTop = 56, listBot = this.height - 32;
        // Крок 1: scrolled content у scissor
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                w.render(graphics, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Крок 2: header поверх
        ScissorHelper.enable(0, 0, this.width, listTop);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() < listTop)
                w.render(graphics, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Крок 3: footer поверх
        ScissorHelper.enable(0, listBot, this.width, this.height - listBot);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= listBot)
                w.render(graphics, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
