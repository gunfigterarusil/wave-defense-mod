package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaveConfigScreen extends ScrollableScreen {
    private final Location location;
    private final Screen parent;
    private EditBox waveCountInput;
    private EditBox timeBetweenWavesInput;
    // Snapshot для скасування змін мобів
    private net.minecraft.nbt.CompoundTag waveSnapshot = null;
    private int snapshotWaveIndex = -1;

    private boolean showConfirmDialog = false;
    private int pendingWaveCount = 0;
    // Підтвердження видалення хвилі (G2)
    private int pendingDeleteWaveIndex = -1;
    // G8: Попередження про незбережені зміни при ESC
    private boolean isDirty = false;
    private boolean showUnsavedDialog = false;

    public WaveConfigScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.wave_config").append(": ").append(location.getName()));
        this.location = location;
        this.parent = parent;
    }

    // ─── ScrollableScreen API ──────────────────────────────────────────

    @Override protected int getClipTop() { return 100; }
    @Override protected int getClipBot() { return this.height - 34; }
    @Override protected int getListSize() { return location.getWaves().size(); }
    @Override protected int getItemsPerPage() { return Math.max(2, (this.height - 145) / 50); }

    // ─── init() ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = 45;

        if (showUnsavedDialog) {
            initUnsavedDialog(centerX);
            return;
        }

        if (showConfirmDialog) {
            initConfirmDialog(centerX);
            return;
        }

        // ── Header (static) ──────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.кількість_хвиль_9007135c"), button -> {}
        ).bounds(centerX - 150, startY, 115, 18).build()).active = false;

        waveCountInput = new EditBox(this.font, centerX - 30, startY, 55, 20, Component.translatable("wavedefense.auto.к_сть_7beea1a7"));
        waveCountInput.setValue(String.valueOf(location.getWaves().size()));
        waveCountInput.setMaxLength(4);
        addStatic(waveCountInput);

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.apply"),
                button -> applyWaveCount()
        ).bounds(centerX + 30, startY, 90, 20).build());

        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.час_між_хвилями_сек_6e1267b0"), button -> {}
        ).bounds(centerX - 150, startY + 27, 150, 18).build()).active = false;

        timeBetweenWavesInput = new EditBox(this.font, centerX + 5, startY + 27, 55, 20, Component.translatable("wavedefense.auto.секунди_9c50f9ee"));
        int currentTime = location.getWaves().isEmpty()
                ? location.getTimeBetweenWaves()
                : location.getWaves().get(0).getTimeBetweenWaves();
        timeBetweenWavesInput.setValue(String.valueOf(currentTime));
        timeBetweenWavesInput.setMaxLength(5);
        addStatic(timeBetweenWavesInput);

        addStatic(Button.builder(
                Component.translatable("wavedefense.label.apply_to_all"),
                button -> applyTimeToAll()
        ).bounds(centerX + 65, startY + 27, 75, 20).build());

        // ── Content (scrollable) ─────────────────────────────────────
        int listStartY = startY + 55;

        if (location.getWaves().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.auto.хвилі_не_налаштовані_встановіть_2b4d25fb"),
                    button -> {}
            ).bounds(centerX - 170, listStartY, 340, 20).build()).active = false;
        } else {
            int itemsPerPage = getItemsPerPage();
            int rowH = 46;

            // В4: clear pending-delete when only one wave remains so confirm button cannot fire
            if (location.getWaves().size() <= 1) pendingDeleteWaveIndex = -1;

            for (int i = 0; i < Math.min(itemsPerPage, location.getWaves().size()); i++) {
                int waveIndex = i + scrollOffset;
                if (waveIndex >= location.getWaves().size()) break;

                WaveConfig wave = location.getWaves().get(waveIndex);
                int yPos = listStartY + (i * rowH);

                this.addRenderableWidget(Button.builder(
                        Component.translatable("wavedefense.wave.label",
                                waveIndex + 1, wave.getMobs().size(), wave.getTimeBetweenWaves()),
                        button -> {}
                ).bounds(centerX - 150, yPos, 245, 18).build()).active = false;

                final int finalWaveIndex = waveIndex;
                boolean isPendingDel = (pendingDeleteWaveIndex == finalWaveIndex);
                Button deleteBtn = Button.builder(
                        isPendingDel
                            ? Component.translatable("wavedefense.button.confirm_delete")
                            : Component.literal("✕"),
                        button -> {
                            if (isPendingDel) {
                                pendingDeleteWaveIndex = -1;
                                deleteWave(finalWaveIndex);
                            } else {
                                pendingDeleteWaveIndex = finalWaveIndex;
                                rebuildWidgets();
                            }
                        }
                ).bounds(centerX + 100, yPos, 20, 18).build();
                deleteBtn.active = location.getWaves().size() > 1;
                this.addRenderableWidget(deleteBtn);

                this.addRenderableWidget(Button.builder(
                        Component.translatable("wavedefense.auto.моби_dd706633"),
                        button -> editWaveMobs(finalWaveIndex)
                ).bounds(centerX - 150, yPos + 22, 80, 20).build());

                this.addRenderableWidget(Button.builder(
                        Component.translatable("wavedefense.auto.нагороди_d8e86e2c"),
                        button -> editWaveRewards(finalWaveIndex)
                ).bounds(centerX - 65, yPos + 22, 88, 20).build());

                boolean hasTrigger = wave.isTriggerEnabled();
                String trigShort = hasTrigger ? wave.getTriggerType().label.substring(0, Math.min(7, wave.getTriggerType().label.length())) : "";
                String oneTimeSuffix = (hasTrigger && wave.isOneTimeOnly()) ? "§8¹" : "";
                String fromSuffix = (hasTrigger && wave.getActivateFromWave() > 0) ? "§8≥" + wave.getActivateFromWave() : "";
                String trigLbl = hasTrigger
                    ? "§d§l⚡§r §d" + trigShort + oneTimeSuffix + fromSuffix
                    : net.minecraft.client.resources.language.I18n.get("wavedefense.wave.no_trigger");
                this.addRenderableWidget(Button.builder(
                        Component.literal(trigLbl),
                        button -> openWaveTrigger(finalWaveIndex)
                ).bounds(centerX + 28, yPos + 22, 70, 20).build());

                boolean hasWSpawn = wave.hasWaveSpawnPos();
                this.addRenderableWidget(Button.builder(
                        Component.literal(hasWSpawn ? "§a§l📍" : "§7📍"),
                        button -> openWaveSpawnEditor(finalWaveIndex)
                ).bounds(centerX + 100, yPos + 22, 20, 20).build())
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    hasWSpawn
                        ? Component.translatable("wavedefense.wave.spawn_tooltip_active",
                            wave.getWaveSpawnPos().getX(), wave.getWaveSpawnPos().getY(), wave.getWaveSpawnPos().getZ())
                        : Component.translatable("wavedefense.wave.spawn_tooltip_hint")));

                this.addRenderableWidget(Button.builder(
                        Component.translatable("wavedefense.auto.сек_79350eb0"), button -> {}
                ).bounds(centerX + 122, yPos + 22, 28, 20).build()).active = false;
                EditBox timerBox = new EditBox(
                        this.font, centerX + 152, yPos + 23, 50, 16,
                        Component.translatable("wavedefense.auto.сек_0be9aa07"));
                timerBox.setMaxLength(5);
                timerBox.setValue(String.valueOf(wave.getTimeBetweenWaves()));
                timerBox.setResponder(s -> {
                    try {
                        int v = Integer.parseInt(s.trim());
                        if (v >= 1) {
                            location.getWaves().get(finalWaveIndex).setTimeBetweenWaves(v);
                            isDirty = true;
                        }
                    } catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(timerBox);
            }

            // Scroll buttons — must use addStatic() to avoid scissor clipping
            if (location.getWaves().size() > itemsPerPage) {
                addStatic(Button.builder(
                        Component.literal("▲"),
                        button -> { if (scrollOffset > 0) { scrollOffset--; pendingDeleteWaveIndex = -1; rebuildWidgets(); } }
                ).bounds(centerX + 155, listStartY, 22, 20).build());
                addStatic(Button.builder(
                        Component.literal("▼"),
                        button -> {
                            if (scrollOffset + getItemsPerPage() < location.getWaves().size()) { scrollOffset++; pendingDeleteWaveIndex = -1; rebuildWidgets(); }
                        }
                ).bounds(centerX + 155, listStartY + (itemsPerPage - 1) * 46 + 22, 22, 20).build());
            }
        }

        // ── Footer (static) ─────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.save_back"),
                button -> saveChanges()
        ).bounds(centerX - 160, this.height - 28, 110, 20).build());

        // G3: Кнопка "Назад без збереження"
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX - 46, this.height - 28, 80, 20).build());

        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.exp_648cf132"),
                button -> minecraft.setScreen(new WaveExportScreen(location, this))
        ).bounds(centerX + 38, this.height - 28, 50, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.зберегти_хвилі_у_файл_world_wave_6a05af92")));

        addStatic(Button.builder(
                Component.translatable("wavedefense.auto.imp_3d6db024"),
                button -> minecraft.setScreen(new WaveImportScreen(location, this))
        ).bounds(centerX + 92, this.height - 28, 50, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.завантажити_хвилі_з_файлу_world_9717fe73")));
    }

    // ─── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (showUnsavedDialog) {
            renderUnsavedDialog(g, mx, my, pt);
            return;
        }
        if (showConfirmDialog) {
            renderConfirmDialog(g, mx, my, pt);
            return;
        }
        super.render(g, mx, my, pt);
    }

    // G8: Діалог незбережених змін при ESC
    private void renderUnsavedDialog(GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        g.fill(0, 0, this.width, this.height, 0xAA000000);
        int cx = this.width / 2;
        int dy = this.height / 2 - 45;
        g.fill(cx - 155, dy - 5, cx + 155, dy + 95, GuiTheme.PANEL_DARK);
        GuiTheme.outline(g, cx - 156, dy - 6, cx + 156, dy + 96, GuiTheme.WARN);
        g.drawCenteredString(this.font,
            "§e§l⚠ " + net.minecraft.client.resources.language.I18n.get("wavedefense.msg.unsaved_changes"),
            cx, dy + 5, GuiTheme.TEXT);
        g.drawCenteredString(this.font,
            net.minecraft.client.resources.language.I18n.get("wavedefense.msg.unsaved_changes_hint"),
            cx, dy + 22, GuiTheme.TEXT_MUTED);
        super.render(g, mx, my, pt);
    }

    private void initUnsavedDialog(int cx) {
        int dy = this.height / 2 - 45;
        // Зберегти і вийти
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.save_back"),
            b -> saveChanges()
        ).bounds(cx - 150, dy + 45, 140, 20).build());
        // Вийти без збереження
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.exit_without_saving"),
            b -> { isDirty = false; showUnsavedDialog = false; this.minecraft.setScreen(parent); }
        ).bounds(cx - 5, dy + 45, 155, 20).build());
        // Залишитись
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.stay"),
            b -> { showUnsavedDialog = false; rebuildWidgets(); }
        ).bounds(cx - 75, dy + 70, 150, 20).build());
    }

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        // Title already rendered by GuiTheme.renderHeader in ScrollableScreen.render()
        g.drawString(this.font, Component.translatable("wavedefense.wave.time_per_wave_hint"),
                this.width / 2 - 150, 28, GuiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void renderContentExtra(GuiGraphics g, int mx, int my, float pt) {
        if (location.getWaves().isEmpty()) return;
        int listStartY = getClipTop();
        int itemsPerPage = getItemsPerPage();
        int rowH = 46;
        int cx = this.width / 2;

        for (int i = 0; i < Math.min(itemsPerPage, location.getWaves().size()); i++) {
            int waveIndex = i + scrollOffset;
            if (waveIndex >= location.getWaves().size()) break;
            int yPos = listStartY + i * rowH;
            boolean isPendingDel = (pendingDeleteWaveIndex == waveIndex);
            boolean hovered = my >= yPos && my < yPos + rowH - 2;

            // Card background for each row
            GuiTheme.card(g, cx - 156, yPos - 2, cx + 126, yPos + rowH - 2, hovered);
            // Left accent stripe — red if pending delete, otherwise ACCENT
            g.fill(cx - 156, yPos - 2, cx - 154, yPos + rowH - 2,
                   isPendingDel ? GuiTheme.DANGER : GuiTheme.ACCENT);
        }
    }

    private void renderConfirmDialog(GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        g.fill(0, 0, this.width, this.height, 0xAA000000);
        int cx = this.width / 2;
        int dy = this.height / 2 - 60;
        g.fill(cx - 155, dy - 5, cx + 155, dy + 130, GuiTheme.PANEL_DARK);
        GuiTheme.outline(g, cx - 156, dy - 6, cx + 156, dy + 131, GuiTheme.DANGER);
        // Рендер всіх віджетів діалогу без scissor
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w) w.render(g, mx, my, pt);
        }
    }

    // ─── Дії ───────────────────────────────────────────────────────────

    private void initConfirmDialog(int centerX) {
        int dialogY = this.height / 2 - 60;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.попередження_907dd752"), button -> {}
        ).bounds(centerX - 150, dialogY, 300, 25).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.wave.confirm_reduce", location.getWaves().size(), pendingWaveCount), button -> {}
        ).bounds(centerX - 150, dialogY + 30, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.всі_налаштування_зайвих_хвиль_бу_dafdbfa3"), button -> {}
        ).bounds(centerX - 150, dialogY + 55, 300, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.confirm"), button -> confirmWaveCountChange()
        ).bounds(centerX - 110, dialogY + 90, 100, 25).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"), button -> cancelWaveCountChange()
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
            isDirty = true;
            location.setTotalWaves(targetCount);
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
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

    private void confirmWaveCountChange() {
        while (location.getWaves().size() > pendingWaveCount) {
            location.getWaves().remove(location.getWaves().size() - 1);
        }
        location.setTotalWaves(pendingWaveCount);
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
            snapshotWaveIndex = idx;
            waveSnapshot = location.getWaves().get(idx).save();
            this.minecraft.setScreen(new WaveMobsEditorScreen(location, idx, this));
        }
    }

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
            isDirty = true;
            location.getWaves().remove(idx);
            location.setTotalWaves(location.getWaves().size());
            clampScroll();
            rebuildWidgets();
        }
    }

    public void autoSave() { saveChanges(); }

    private void saveChanges() {
        isDirty = false;
        showUnsavedDialog = false;
        // Always sync totalWaves to the actual number of configured waves
        location.setTotalWaves(location.getWaves().size());

        // Warn admin if any non-trigger wave has no mobs
        if (minecraft.player != null) {
            for (int i = 0; i < location.getWaves().size(); i++) {
                WaveConfig wc = location.getWaves().get(i);
                if (!wc.isTriggerEnabled() && wc.getMobs().isEmpty()) {
                    minecraft.player.displayClientMessage(
                        Component.translatable("wavedefense.msg.wave_has_no_mobs", i + 1), true);
                    break; // show only the first offending wave
                }
            }
        }

        try {
            int seconds = Integer.parseInt(timeBetweenWavesInput.getValue());
            location.setTimeBetweenWaves(seconds);
        } catch (NumberFormatException ignored) {}

        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.зміни_збережено_60feafc0"), true);
        }
        this.minecraft.setScreen(parent);
    }

    // G8: При ESC — показати попередження якщо є незбережені зміни
    @Override
    public void onClose() {
        if (isDirty && !showUnsavedDialog) {
            showUnsavedDialog = true;
            rebuildWidgets();
        } else {
            super.onClose();
        }
    }

    // BUG-D: скидати pendingDeleteWaveIndex при прокрутці мишею
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        pendingDeleteWaveIndex = -1;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }
}
