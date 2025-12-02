package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaveConfigScreen extends Screen {
    private final Location location;
    private final Screen parent;
    private EditBox waveCountInput;
    private EditBox timeBetweenWavesInput;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 5;
    private boolean showConfirmDialog = false;
    private int pendingWaveCount = 0;

    public WaveConfigScreen(Location location, Screen parent) {
        super(Component.literal("Налаштування хвиль"));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 50;

        if (showConfirmDialog) {
            initConfirmDialog(centerX);
            return;
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Кількість хвиль:"),
                button -> {}
        ).bounds(centerX - 150, startY, 120, 20).build()).active = false;

        waveCountInput = new EditBox(this.font, centerX - 25, startY, 60, 20,
                Component.literal("К-сть"));
        waveCountInput.setValue(String.valueOf(location.getWaves().size()));
        waveCountInput.setMaxLength(3);
        this.addRenderableWidget(waveCountInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Застосувати"),
                button -> applyWaveCount()
        ).bounds(centerX + 40, startY, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Час між хвилями (хв):"),
                button -> {}
        ).bounds(centerX - 150, startY + 30, 140, 20).build()).active = false;

        timeBetweenWavesInput = new EditBox(this.font, centerX - 5, startY + 30, 60, 20,
                Component.literal("Час"));
        if (!location.getWaves().isEmpty()) {
            timeBetweenWavesInput.setValue(String.valueOf(location.getWaves().get(0).getTimeBetweenWaves() / 60));
        } else {
            timeBetweenWavesInput.setValue("2");
        }
        timeBetweenWavesInput.setMaxLength(3);
        this.addRenderableWidget(timeBetweenWavesInput);

        if (location.getWaves().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Хвилі не налаштовані. Встановіть кількість вище."),
                    button -> {}
            ).bounds(centerX - 180, startY + 70, 360, 20).build()).active = false;
        } else {
            int listStartY = startY + 70;
            for (int i = 0; i < Math.min(ITEMS_PER_PAGE, location.getWaves().size()); i++) {
                int waveIndex = i + scrollOffset;
                if (waveIndex >= location.getWaves().size()) break;

                WaveConfig wave = location.getWaves().get(waveIndex);
                int yPos = listStartY + (i * 60);

                this.addRenderableWidget(Button.builder(
                        Component.literal("§6Хвиля " + (waveIndex + 1) + " §7(Мобів: " + wave.getMobs().size() + ")"),
                        button -> {}
                ).bounds(centerX - 150, yPos, 200, 20).build()).active = false;

                final int finalWaveIndex = waveIndex;
                this.addRenderableWidget(Button.builder(
                        Component.literal("✎ Моби"),
                        button -> editWaveMobs(finalWaveIndex)
                ).bounds(centerX - 150, yPos + 25, 90, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("🎁 Нагороди"),
                        button -> editWaveRewards(finalWaveIndex)
                ).bounds(centerX - 55, yPos + 25, 100, 20).build());

                Button deleteButton = Button.builder(
                        Component.literal("✕"),
                        button -> deleteWave(finalWaveIndex)
                ).bounds(centerX + 50, yPos, 20, 20).build();
                deleteButton.active = location.getWaves().size() > 1;
                this.addRenderableWidget(deleteButton);
            }

            if (location.getWaves().size() > ITEMS_PER_PAGE) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp()
                ).bounds(centerX + 80, listStartY, 25, 25).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown()
                ).bounds(centerX + 80, listStartY + 200, 25, 25).build());
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти і повернутися"),
                button -> saveChanges()
        ).bounds(centerX - 110, this.height - 30, 220, 20).build());
    }

    private void initConfirmDialog(int centerX) {
        int dialogY = this.height / 2 - 60;

        this.addRenderableWidget(Button.builder(
                Component.literal("§c⚠ ПОПЕРЕДЖЕННЯ"),
                button -> {}
        ).bounds(centerX - 150, dialogY, 300, 25).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Ви зменшуєте кількість хвиль з §e" + location.getWaves().size() + " §7до §e" + pendingWaveCount),
                button -> {}
        ).bounds(centerX - 150, dialogY + 30, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§cВСІ налаштування зайвих хвиль буде ВИДАЛЕНО!"),
                button -> {}
        ).bounds(centerX - 150, dialogY + 55, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§cЦю дію НЕ можна скасувати!"),
                button -> {}
        ).bounds(centerX - 150, dialogY + 80, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Підтвердити"),
                button -> confirmWaveCountChange()
        ).bounds(centerX - 110, dialogY + 110, 100, 25).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ Скасувати"),
                button -> cancelWaveCountChange()
        ).bounds(centerX + 10, dialogY + 110, 100, 25).build());
    }

    private void applyWaveCount() {
        try {
            int targetCount = Integer.parseInt(waveCountInput.getValue());
            if (targetCount < 1 || targetCount > 50) return;

            int currentCount = location.getWaves().size();

            if (targetCount < currentCount) {
                pendingWaveCount = targetCount;
                showConfirmDialog = true;
                this.rebuildWidgets();
                return;
            }

            if (targetCount > currentCount) {
                int minutes = 2;
                try {
                    minutes = Integer.parseInt(timeBetweenWavesInput.getValue());
                } catch (NumberFormatException ignored) {}

                for (int i = currentCount; i < targetCount; i++) {
                    WaveConfig newWave = new WaveConfig(i + 1, minutes * 60);
                    location.addWave(newWave);
                }
            }

            this.rebuildWidgets();
        } catch (NumberFormatException e) {
        }
    }

    private void confirmWaveCountChange() {
        while (location.getWaves().size() > pendingWaveCount) {
            location.getWaves().remove(location.getWaves().size() - 1);
        }

        waveCountInput.setValue(String.valueOf(pendingWaveCount));
        showConfirmDialog = false;
        this.rebuildWidgets();
    }

    private void cancelWaveCountChange() {
        showConfirmDialog = false;
        pendingWaveCount = 0;
        this.rebuildWidgets();
    }

    private void editWaveMobs(int waveIndex) {
        if (waveIndex >= 0 && waveIndex < location.getWaves().size()) {
            this.minecraft.setScreen(new WaveMobsEditorScreen(location, waveIndex, this));
        }
    }

    private void editWaveRewards(int waveIndex) {
        if (waveIndex >= 0 && waveIndex < location.getWaves().size()) {
            this.minecraft.setScreen(new RewardsConfigScreen(this, location.getWaves().get(waveIndex)));
        }
    }

    private void deleteWave(int waveIndex) {
        if (location.getWaves().size() > 1 && waveIndex >= 0 && waveIndex < location.getWaves().size()) {
            location.getWaves().remove(waveIndex);
            waveCountInput.setValue(String.valueOf(location.getWaves().size()));
            this.rebuildWidgets();
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < location.getWaves().size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    private void saveChanges() {
        try {
            int minutes = Integer.parseInt(timeBetweenWavesInput.getValue());
            if (minutes > 0) {
                int seconds = minutes * 60;

                for (WaveConfig wave : location.getWaves()) {
                    wave.setTimeBetweenWaves(seconds);
                }
            }
        } catch (NumberFormatException ignored) {}

        WaveDefenseMod.locationManager.save();

        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Зміни збережено!"),
                    true
            );
        }

        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        if (showConfirmDialog) {
            graphics.fill(0, 0, this.width, this.height, 0xAA000000);

            int centerX = this.width / 2;
            int dialogY = this.height / 2 - 60;

            graphics.fill(centerX - 155, dialogY - 5, centerX + 155, dialogY + 145, 0xFF1a1a1a);
            graphics.fill(centerX - 156, dialogY - 6, centerX + 156, dialogY - 5, 0xFFef4444);
            graphics.fill(centerX - 156, dialogY + 145, centerX + 156, dialogY + 146, 0xFFef4444);
            graphics.fill(centerX - 156, dialogY - 5, centerX - 155, dialogY + 145, 0xFFef4444);
            graphics.fill(centerX + 155, dialogY - 5, centerX + 156, dialogY + 145, 0xFFef4444);
        } else {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

            graphics.drawString(this.font,
                    "§7Налаштуйте кількість хвиль, потім редагуйте кожну",
                    this.width / 2 - 150, 30, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
