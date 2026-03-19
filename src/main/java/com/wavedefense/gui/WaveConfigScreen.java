package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaveConfigScreen extends Screen {
    private final Location location;
    private final Screen parent;
    private EditBox waveCountInput;
    private EditBox timeBetweenWavesInput; // тепер у секундах
    private int scrollOffset = 0;
    // Snapshot для скасування змін мобів
    private net.minecraft.nbt.CompoundTag waveSnapshot = null;
    private int snapshotWaveIndex = -1;

    private int getItemsPerPage() {
        return Math.max(2, (this.height - 145) / 50);
    }

    private boolean showConfirmDialog = false;
    private int pendingWaveCount = 0;

    public WaveConfigScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.wave_config").append(": ").append(location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = 45;

        if (showConfirmDialog) {
            initConfirmDialog(centerX);
            return;
        }

        // Кількість хвиль
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Кількість хвиль:"), button -> {}
        ).bounds(centerX - 150, startY, 115, 18).build()).active = false;

        waveCountInput = new EditBox(this.font, centerX - 30, startY, 55, 20, Component.literal("К-сть"));
        waveCountInput.setValue(String.valueOf(location.getWaves().size()));
        waveCountInput.setMaxLength(4);
        this.addRenderableWidget(waveCountInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Застосувати"),
                button -> applyWaveCount()
        ).bounds(centerX + 30, startY, 90, 20).build());

        // Час між хвилями — у СЕКУНДАХ
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Час між хвилями (сек):"), button -> {}
        ).bounds(centerX - 150, startY + 27, 150, 18).build()).active = false;

        timeBetweenWavesInput = new EditBox(this.font, centerX + 5, startY + 27, 55, 20, Component.literal("Секунди"));
        // Беремо значення з першої хвилі або з location
        int currentTime = location.getWaves().isEmpty()
                ? location.getTimeBetweenWaves()
                : location.getWaves().get(0).getTimeBetweenWaves();
        timeBetweenWavesInput.setValue(String.valueOf(currentTime));
        timeBetweenWavesInput.setMaxLength(5);
        this.addRenderableWidget(timeBetweenWavesInput);

        // Кнопка «Застосувати час» до всіх хвиль
        this.addRenderableWidget(Button.builder(
                Component.literal("До всіх"),
                button -> applyTimeToAll()
        ).bounds(centerX + 65, startY + 27, 75, 20).build());

        int listStartY = startY + 55;

        if (location.getWaves().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Хвилі не налаштовані. Встановіть кількість вище."),
                    button -> {}
            ).bounds(centerX - 170, listStartY, 340, 20).build()).active = false;
        } else {
            int itemsPerPage = getItemsPerPage();
            int rowH = 46;

            for (int i = 0; i < Math.min(itemsPerPage, location.getWaves().size()); i++) {
                int waveIndex = i + scrollOffset;
                if (waveIndex >= location.getWaves().size()) break;

                WaveConfig wave = location.getWaves().get(waveIndex);
                int yPos = listStartY + (i * rowH);

                // Заголовок хвилі
                String waveLabel = String.format("§6Хвиля %d §7(Мобів: %d | Час: §e%d сек§7)",
                        waveIndex + 1, wave.getMobs().size(), wave.getTimeBetweenWaves());
                this.addRenderableWidget(Button.builder(
                        Component.literal(waveLabel), button -> {}
                ).bounds(centerX - 150, yPos, 245, 18).build()).active = false;

                final int finalWaveIndex = waveIndex;
                Button deleteBtn = Button.builder(
                        Component.literal("✕"), button -> deleteWave(finalWaveIndex)
                ).bounds(centerX + 100, yPos, 20, 18).build();
                deleteBtn.active = location.getWaves().size() > 1;
                this.addRenderableWidget(deleteBtn);

                // Кнопки редагування
                this.addRenderableWidget(Button.builder(
                        Component.literal("✎ Моби"),
                        button -> editWaveMobs(finalWaveIndex)
                ).bounds(centerX - 150, yPos + 22, 80, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.literal("🎁 Нагороди"),
                        button -> editWaveRewards(finalWaveIndex)
                ).bounds(centerX - 65, yPos + 22, 88, 20).build());

                // Тригер хвилі
                boolean hasTrigger = wave.isTriggerEnabled();
                String trigShort = hasTrigger ? wave.getTriggerType().label.substring(0, Math.min(7, wave.getTriggerType().label.length())) : "";
                String oneTimeSuffix = (hasTrigger && wave.isOneTimeOnly()) ? "§8¹" : "";
                String fromSuffix = (hasTrigger && wave.getActivateFromWave() > 0) ? "§8≥" + wave.getActivateFromWave() : "";
                String trigLbl = hasTrigger
                    ? "§d§l⚡§r §d" + trigShort + oneTimeSuffix + fromSuffix
                    : "§7⚡ Тригер";
                this.addRenderableWidget(Button.builder(
                        Component.literal(trigLbl),
                        button -> openWaveTrigger(finalWaveIndex)
                ).bounds(centerX + 28, yPos + 22, 70, 20).build());

                // Кнопка точки спавну хвилі
                boolean hasWSpawn = wave.hasWaveSpawnPos();
                this.addRenderableWidget(Button.builder(
                        Component.literal(hasWSpawn ? "§a§l📍" : "§7📍"),
                        button -> openWaveSpawnEditor(finalWaveIndex)
                ).bounds(centerX + 100, yPos + 22, 20, 20).build())
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(hasWSpawn
                        ? "§aСпавн хвилі: §eX" + wave.getWaveSpawnPos().getX() + " Y" + wave.getWaveSpawnPos().getY() + " Z" + wave.getWaveSpawnPos().getZ()
                        : "§7Особливий спавн мобів для цієї хвилі\n§8(за замовч. — точки спавну локації)")));

                // Поле вводу часу хвилі + label
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7сек:"), button -> {}
                ).bounds(centerX + 122, yPos + 22, 28, 20).build()).active = false;
                net.minecraft.client.gui.components.EditBox timerBox =
                    new net.minecraft.client.gui.components.EditBox(
                        this.font, centerX + 152, yPos + 23, 50, 16,
                        net.minecraft.network.chat.Component.literal("сек"));
                timerBox.setMaxLength(5);
                timerBox.setValue(String.valueOf(wave.getTimeBetweenWaves()));
                timerBox.setResponder(s -> {
                    try {
                        int v = Integer.parseInt(s.trim());
                        if (v >= 1) location.getWaves().get(finalWaveIndex).setTimeBetweenWaves(v);
                    } catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(timerBox);
            }

            // Скрол
            if (location.getWaves().size() > itemsPerPage) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("▲"), button -> scrollUp()
                ).bounds(centerX + 155, listStartY, 22, 20).build());
                this.addRenderableWidget(Button.builder(
                        Component.literal("▼"), button -> scrollDown()
                ).bounds(centerX + 155, listStartY + (itemsPerPage - 1) * rowH + 22, 22, 20).build());
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Зберегти і повернутися"),
                button -> saveChanges()
        ).bounds(centerX - 110, this.height - 28, 148, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§e⬆ Exp"),
                button -> minecraft.setScreen(new WaveExportScreen(location, this))
        ).bounds(centerX + 42, this.height - 28, 50, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7Зберегти хвилі у файл\n§8world/wavedefense/wave_export/")));

        this.addRenderableWidget(Button.builder(
                Component.literal("§b⬇ Imp"),
                button -> minecraft.setScreen(new WaveImportScreen(location, this))
        ).bounds(centerX + 96, this.height - 28, 50, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7Завантажити хвилі з файлу\n§8world/wavedefense/wave_export/")));
    }

    private void adjustWaveTime(int waveIndex, int delta) {
        if (waveIndex < 0 || waveIndex >= location.getWaves().size()) return;
        WaveConfig wave = location.getWaves().get(waveIndex);
        int newTime = Math.max(5, wave.getTimeBetweenWaves() + delta);
        wave.setTimeBetweenWaves(newTime);
        rebuildWidgets();
    }

    private void applyTimeToAll() {
        try {
            int seconds = Integer.parseInt(timeBetweenWavesInput.getValue());
            if (seconds < 1) return;
            for (WaveConfig wave : location.getWaves()) {
                wave.setTimeBetweenWaves(seconds);
            }
            location.setTimeBetweenWaves(seconds);
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void initConfirmDialog(int centerX) {
        int dialogY = this.height / 2 - 60;
        this.addRenderableWidget(Button.builder(
                Component.literal("§c⚠ ПОПЕРЕДЖЕННЯ"), button -> {}
        ).bounds(centerX - 150, dialogY, 300, 25).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Зменшення з §e" + location.getWaves().size() + " §7до §e" + pendingWaveCount), button -> {}
        ).bounds(centerX - 150, dialogY + 30, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("§cВСІ налаштування зайвих хвиль буде ВИДАЛЕНО!"), button -> {}
        ).bounds(centerX - 150, dialogY + 55, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Підтвердити"), button -> confirmWaveCountChange()
        ).bounds(centerX - 110, dialogY + 90, 100, 25).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ Скасувати"), button -> cancelWaveCountChange()
        ).bounds(centerX + 10, dialogY + 90, 100, 25).build());
    }

    private void applyWaveCount() {
        try {
            int targetCount = Integer.parseInt(waveCountInput.getValue());
            if (targetCount < 1 || targetCount > 9999) return;
            int currentCount = location.getWaves().size();
            if (targetCount < currentCount) {
                pendingWaveCount = targetCount;
                showConfirmDialog = true;
                rebuildWidgets();
                return;
            }
            if (targetCount > currentCount) {
                int seconds = 60;
                try { seconds = Integer.parseInt(timeBetweenWavesInput.getValue()); } catch (NumberFormatException ignored) {}
                for (int i = currentCount; i < targetCount; i++) {
                    location.addWave(new WaveConfig(i + 1, seconds));
                }
            }
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void confirmWaveCountChange() {
        while (location.getWaves().size() > pendingWaveCount) {
            location.getWaves().remove(location.getWaves().size() - 1);
        }
        waveCountInput.setValue(String.valueOf(pendingWaveCount));
        showConfirmDialog = false;
        rebuildWidgets();
    }

    private void cancelWaveCountChange() {
        showConfirmDialog = false;
        pendingWaveCount = 0;
        rebuildWidgets();
    }

    private void editWaveMobs(int idx) {
        if (idx >= 0 && idx < location.getWaves().size()) {
            // Робимо snapshot поточного стану хвилі
            snapshotWaveIndex = idx;
            waveSnapshot = location.getWaves().get(idx).save();
            this.minecraft.setScreen(new WaveMobsEditorScreen(location, idx, this));
        }
    }

    /** Відновлює хвилю зі snapshot (викликається при "Без збереження" з WaveMobsEditorScreen) */
    public void discardWaveChanges() {
        if (waveSnapshot != null && snapshotWaveIndex >= 0
                && snapshotWaveIndex < location.getWaves().size()) {
            location.getWaves().set(snapshotWaveIndex,
                com.wavedefense.data.WaveConfig.load(waveSnapshot));
        }
        waveSnapshot = null;
        snapshotWaveIndex = -1;
    }

    private void openWaveSpawnEditor(int idx) {
        if (idx < 0 || idx >= location.getWaves().size()) return;
        this.minecraft.setScreen(new WaveSpawnEditorScreen(this, location.getWaves().get(idx), idx));
    }

    private void editWaveRewards(int idx) {
        if (idx >= 0 && idx < location.getWaves().size()) {
            this.minecraft.setScreen(new RewardsConfigScreen(this, location.getWaves().get(idx)));
        }
    }

    private void openWaveTrigger(int idx) {
        if (idx >= 0 && idx < location.getWaves().size()) {
            boolean isPvp = location.isPvp();
            this.minecraft.setScreen(new WaveTriggerEditorScreen(
                this, location.getWaves().get(idx), idx, isPvp));
        }
    }

    private void deleteWave(int idx) {
        if (location.getWaves().size() > 1 && idx >= 0 && idx < location.getWaves().size()) {
            location.getWaves().remove(idx);
            if (scrollOffset > 0 && scrollOffset >= location.getWaves().size()) {
                scrollOffset = Math.max(0, location.getWaves().size() - getItemsPerPage());
            }
            rebuildWidgets();
        }
    }

    private void scrollUp() { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
    private void scrollDown() { if (scrollOffset + getItemsPerPage() < location.getWaves().size()) { scrollOffset++; rebuildWidgets(); } }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scrollUp(); else scrollDown(); return true;
    }

    /** Публічний метод — викликається дочірніми екранами (WaveSpawnEditorScreen тощо) */
    public void autoSave() { saveChanges(); }

    private void saveChanges() {
        // Зберігаємо глобальний час між хвилями
        try {
            int seconds = Integer.parseInt(timeBetweenWavesInput.getValue());
            location.setTimeBetweenWaves(seconds);
        } catch (NumberFormatException ignored) {}

        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§a✓ Зміни збережено!"), true);
        }
        this.minecraft.setScreen(parent);
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
        // Scissor: список хвиль між header (startY+55=100) і footer (height-34)
        // startY=45 + "кількість хвиль"(20) + "час між хвилями"(27) + відступ(8) = ~100
        int listTop = 100, listBot = this.height - 34;
        // Крок 1: scrolled content
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        if (showConfirmDialog) {
            graphics.fill(0, 0, this.width, this.height, 0xAA000000);
            int cx = this.width / 2;
            int dy = this.height / 2 - 60;
            graphics.fill(cx - 155, dy - 5, cx + 155, dy + 130, 0xFF1a1a1a);
            graphics.fill(cx - 156, dy - 6, cx + 156, dy - 5, 0xFFef4444);
            graphics.fill(cx - 156, dy + 130, cx + 156, dy + 131, 0xFFef4444);
            graphics.fill(cx - 156, dy - 5, cx - 155, dy + 130, 0xFFef4444);
            graphics.fill(cx + 155, dy - 5, cx + 156, dy + 130, 0xFFef4444);
            super.render(graphics, mouseX, mouseY, partialTick);
        } else {
            // Рендер тільки прокручених widgets (всередині scissor)
            for (var r : this.renderables) {
                if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                    w.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        ScissorHelper.disable();
        // Крок 2: статичний header поверх контенту
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.drawString(this.font, "§7Час між хвилями зберігається окремо для кожної хвилі",
                this.width / 2 - 150, 28, 0x888888);
        ScissorHelper.enable(0, 0, this.width, listTop);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() < listTop)
                w.render(graphics, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Крок 3: статичний footer поверх контенту
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
