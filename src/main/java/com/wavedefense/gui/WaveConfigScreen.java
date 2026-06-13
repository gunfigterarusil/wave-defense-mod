package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveTrigger;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class WaveConfigScreen extends ScrollableScreen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Location location;
    private final Screen parent;
    private TextFieldWidget waveCountInput;
    private TextFieldWidget timeBetweenWavesInput;
    // Snapshot для скасування змін мобів
    private net.minecraft.nbt.CompoundNBT waveSnapshot = null;
    private int snapshotWaveIndex = -1;

    private boolean showConfirmDialog = false;
    private int pendingWaveCount = 0;
    // Підтвердження видалення хвилі (G2)
    private int pendingDeleteWaveIndex = -1;
    // G8: Попередження про незбережені зміни при ESC
    private boolean isDirty = false;
    private boolean showUnsavedDialog = false;

    public WaveConfigScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.title.wave_config").append(": ").append(location.getName()));
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
        addStatic(new Button(centerX - 150, startY, 115, 18, new TranslationTextComponent("wavedefense.auto.кількість_хвиль_9007135c"), button -> {})).active = false;

        waveCountInput = new TextFieldWidget(this.font, centerX - 30, startY, 55, 20, new TranslationTextComponent("wavedefense.auto.к_сть_7beea1a7"));
        waveCountInput.setValue(String.valueOf(location.getWaves().size()));
        waveCountInput.setMaxLength(4);
        addStatic(waveCountInput);

        addStatic(new Button(centerX + 30, startY, 90, 20, new TranslationTextComponent("wavedefense.button.apply"), button -> applyWaveCount()));

        addStatic(new Button(centerX - 150, startY + 27, 150, 18, new TranslationTextComponent("wavedefense.auto.час_між_хвилями_сек_6e1267b0"), button -> {})).active = false;

        timeBetweenWavesInput = new TextFieldWidget(this.font, centerX + 5, startY + 27, 55, 20, new TranslationTextComponent("wavedefense.auto.секунди_9c50f9ee"));
        int currentTime = location.getWaves().isEmpty()
                ? location.getTimeBetweenWaves()
                : location.getWaves().get(0).getTimeBetweenWaves();
        timeBetweenWavesInput.setValue(String.valueOf(currentTime));
        timeBetweenWavesInput.setMaxLength(5);
        addStatic(timeBetweenWavesInput);

        addStatic(new Button(centerX + 65, startY + 27, 75, 20, new TranslationTextComponent("wavedefense.label.apply_to_all"), button -> applyTimeToAll()));

        // ── Content (scrollable) ─────────────────────────────────────
        int listStartY = startY + 55;

        if (location.getWaves().isEmpty()) {
            this.addButton(new Button(centerX - 170, listStartY, 340, 20, new TranslationTextComponent("wavedefense.auto.хвилі_не_налаштовані_встановіть_2b4d25fb"), button -> {})).active = false;
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

                this.addButton(new Button(centerX - 150, yPos, 245, 18, new TranslationTextComponent("wavedefense.wave.label",
                                waveIndex + 1, wave.getMobs().size(), wave.getTimeBetweenWaves()), button -> {})).active = false;

                final int finalWaveIndex = waveIndex;
                boolean isPendingDel = (pendingDeleteWaveIndex == finalWaveIndex);
                Button deleteBtn = new Button(centerX + 100, yPos, 20, 18, isPendingDel
                            ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                            : new StringTextComponent("✕"), button -> {
                            if (isPendingDel) {
                                pendingDeleteWaveIndex = -1;
                                deleteWave(finalWaveIndex);
                            } else {
                                pendingDeleteWaveIndex = finalWaveIndex;
                                init();
                            }
                        });
                deleteBtn.active = location.getWaves().size() > 1;
                this.addButton(deleteBtn);

                this.addButton(new Button(centerX - 150, yPos + 22, 80, 20, new TranslationTextComponent("wavedefense.auto.моби_dd706633"), button -> editWaveMobs(finalWaveIndex)));

                this.addButton(new Button(centerX - 65, yPos + 22, 88, 20, new TranslationTextComponent("wavedefense.auto.нагороди_d8e86e2c"), button -> editWaveRewards(finalWaveIndex)));

                boolean hasTrigger = wave.isTriggerEnabled();
                String trigShort = hasTrigger ? wave.getTriggerType().label.substring(0, Math.min(7, wave.getTriggerType().label.length())) : "";
                String oneTimeSuffix = (hasTrigger && wave.isOneTimeOnly()) ? "§8¹" : "";
                String fromSuffix = (hasTrigger && wave.getActivateFromWave() > 0) ? "§8≥" + wave.getActivateFromWave() : "";
                String trigLbl = hasTrigger
                    ? "§d§l⚡§r §d" + trigShort + oneTimeSuffix + fromSuffix
                    : net.minecraft.client.resources.I18n.get("wavedefense.wave.no_trigger");
                this.addButton(new Button(centerX + 28, yPos + 22, 70, 20, new StringTextComponent(trigLbl), button -> openWaveTrigger(finalWaveIndex)));

                boolean hasWSpawn = wave.hasWaveSpawnPos();
                this.addButton(new Button(centerX + 100, yPos + 22, 20, 20, new StringTextComponent(hasWSpawn ? "§a§l📍" : "§7📍"), button -> openWaveSpawnEditor(finalWaveIndex)))
                /* setTooltip omitted on 1.16.5 */;

                this.addButton(new Button(centerX + 122, yPos + 22, 28, 20, new TranslationTextComponent("wavedefense.auto.сек_79350eb0"), button -> {})).active = false;
                TextFieldWidget timerBox = new TextFieldWidget(
                        this.font, centerX + 152, yPos + 23, 50, 16,
                        new TranslationTextComponent("wavedefense.auto.сек_0be9aa07"));
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
                this.addButton(timerBox);
            }

            // Scroll buttons — must use addStatic() to avoid scissor clipping
            if (location.getWaves().size() > itemsPerPage) {
                addStatic(new Button(centerX + 155, listStartY, 22, 20, new StringTextComponent("▲"), button -> { if (scrollOffset > 0) { scrollOffset--; pendingDeleteWaveIndex = -1; init(); } }));
                addStatic(new Button(centerX + 155, listStartY + (itemsPerPage - 1) * 46 + 22, 22, 20, new StringTextComponent("▼"), button -> {
                            if (scrollOffset + getItemsPerPage() < location.getWaves().size()) { scrollOffset++; pendingDeleteWaveIndex = -1; init(); }
                        }));
            }
        }

        // ── Footer (static) ─────────────────────────────────────────
        addStatic(new Button(centerX - 160, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.button.save_back"), button -> saveChanges()));

        // G3: Кнопка "Назад без збереження"
        addStatic(new Button(centerX - 46, this.height - 28, 80, 20, new TranslationTextComponent("wavedefense.button.cancel"), button -> this.minecraft.setScreen(parent)));

        addStatic(new Button(centerX + 38, this.height - 28, 50, 20, new TranslationTextComponent("wavedefense.auto.exp_648cf132"), button -> minecraft.setScreen(new WaveExportScreen(location, this))))
        /* setTooltip omitted on 1.16.5 */;

        addStatic(new Button(centerX + 92, this.height - 28, 50, 20, new TranslationTextComponent("wavedefense.auto.imp_3d6db024"), button -> minecraft.setScreen(new WaveImportScreen(location, this))))
        /* setTooltip omitted on 1.16.5 */;
    }

    // ─── Render ────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack g, int mx, int my, float pt) {
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
    private void renderUnsavedDialog(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.fill(g, 0, 0, this.width, this.height, 0xAA000000);
        int cx = this.width / 2;
        int dy = this.height / 2 - 45;
        com.wavedefense.gui.GuiCompat.fill(g, cx - 155, dy - 5, cx + 155, dy + 95, GuiTheme.PANEL_DARK);
        GuiTheme.outline(g, cx - 156, dy - 6, cx + 156, dy + 96, GuiTheme.WARN);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font,
            "§e§l⚠ " + net.minecraft.client.resources.I18n.get("wavedefense.msg.unsaved_changes"),
            cx, dy + 5, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font,
            net.minecraft.client.resources.I18n.get("wavedefense.msg.unsaved_changes_hint"),
            cx, dy + 22, GuiTheme.TEXT_MUTED);
        super.render(g, mx, my, pt);
    }

    private void initUnsavedDialog(int cx) {
        int dy = this.height / 2 - 45;
        // Зберегти і вийти
        this.addButton(new Button(cx - 150, dy + 45, 140, 20, new TranslationTextComponent("wavedefense.button.save_back"), b -> saveChanges()));
        // Вийти без збереження
        this.addButton(new Button(cx - 5, dy + 45, 155, 20, new TranslationTextComponent("wavedefense.button.exit_without_saving"), b -> { isDirty = false; showUnsavedDialog = false; this.minecraft.setScreen(parent); }));
        // Залишитись
        this.addButton(new Button(cx - 75, dy + 70, 150, 20, new TranslationTextComponent("wavedefense.button.stay"), b -> { showUnsavedDialog = false; rebuild(); }));
    }

    @Override
    protected void renderHeader(MatrixStack g, int mx, int my, float pt) {
        // Title already rendered by GuiTheme.renderHeader in ScrollableScreen.render()
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, new TranslationTextComponent("wavedefense.wave.time_per_wave_hint"),
                this.width / 2 - 150, 28, GuiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void renderContentExtra(MatrixStack g, int mx, int my, float pt) {
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
            com.wavedefense.gui.GuiCompat.fill(g, cx - 156, yPos - 2, cx - 154, yPos + rowH - 2,
                   isPendingDel ? GuiTheme.DANGER : GuiTheme.ACCENT);
        }
    }

    private void renderConfirmDialog(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.fill(g, 0, 0, this.width, this.height, 0xAA000000);
        int cx = this.width / 2;
        int dy = this.height / 2 - 60;
        com.wavedefense.gui.GuiCompat.fill(g, cx - 155, dy - 5, cx + 155, dy + 130, GuiTheme.PANEL_DARK);
        GuiTheme.outline(g, cx - 156, dy - 6, cx + 156, dy + 131, GuiTheme.DANGER);
        // Рендер всіх віджетів діалогу без scissor
        for (Object r : this.buttons) {
            if (r instanceof Widget) { Widget w = (Widget) r; w.render(g, mx, my, pt); }
        }
    }

    // ─── Дії ───────────────────────────────────────────────────────────

    private void initConfirmDialog(int centerX) {
        int dialogY = this.height / 2 - 60;
        this.addButton(new Button(centerX - 150, dialogY, 300, 25, new TranslationTextComponent("wavedefense.auto.попередження_907dd752"), button -> {})).active = false;
        this.addButton(new Button(centerX - 150, dialogY + 30, 300, 20, new TranslationTextComponent("wavedefense.wave.confirm_reduce", location.getWaves().size(), pendingWaveCount), button -> {})).active = false;
        this.addButton(new Button(centerX - 150, dialogY + 55, 300, 20, new TranslationTextComponent("wavedefense.auto.всі_налаштування_зайвих_хвиль_бу_dafdbfa3"), button -> {})).active = false;
        this.addButton(new Button(centerX - 110, dialogY + 90, 100, 25, new TranslationTextComponent("wavedefense.button.confirm"), button -> confirmWaveCountChange()));
        this.addButton(new Button(centerX + 10, dialogY + 90, 100, 25, new TranslationTextComponent("wavedefense.button.cancel"), button -> cancelWaveCountChange()));
    }

    private void applyWaveCount() {
        try {
            int targetCount = Integer.parseInt(waveCountInput.getValue());
            if (targetCount < 1 || targetCount > 9999) return;
            int currentCount = location.getWaves().size();
            if (targetCount < currentCount) {
                pendingWaveCount = targetCount;
                showConfirmDialog = true;
                rebuild();
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
            rebuild();
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
            rebuild();
        } catch (NumberFormatException ignored) {}
    }

    private void confirmWaveCountChange() {
        while (location.getWaves().size() > pendingWaveCount) {
            location.getWaves().remove(location.getWaves().size() - 1);
        }
        location.setTotalWaves(pendingWaveCount);
        waveCountInput.setValue(String.valueOf(pendingWaveCount));
        showConfirmDialog = false;
        rebuild();
    }

    private void cancelWaveCountChange() {
        showConfirmDialog = false;
        pendingWaveCount = 0;
        rebuild();
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
            rebuild();
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
                        new TranslationTextComponent("wavedefense.msg.wave_has_no_mobs", i + 1), true);
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
            minecraft.player.displayClientMessage(new TranslationTextComponent("wavedefense.auto.зміни_збережено_60feafc0"), true);
        }
        this.minecraft.setScreen(parent);
    }

    // G8: При ESC — показати попередження якщо є незбережені зміни
    @Override
    public void onClose() {
        if (isDirty && !showUnsavedDialog) {
            showUnsavedDialog = true;
            rebuild();
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
