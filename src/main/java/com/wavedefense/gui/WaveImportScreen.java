package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestWaveExportListPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Список файлів у wave_export/ — вибрати один → відкрити WaveImportTargetScreen.
 */
public class WaveImportScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    final Location location;
    final Screen parent;
    private int scrollOffset = 0;

    private static final int BTN_H = 20;
    private static final int GAP   = 4;

    public WaveImportScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.wave.import_title", location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.buttons.clear(); this.children.clear();
        // Запитуємо список файлів
        PacketHandler.sendToServer(new RequestWaveExportListPacket());
        buildWidgets();
    }

    private void buildWidgets() {
        this.buttons.clear(); this.children.clear();
        int cx = this.width / 2;
        List<String> files = ClientWaveExportManager.getFiles();

        int listStartY = 48;
        int itemsPerPage = Math.max(3, (this.height - listStartY - 35) / (BTN_H + GAP));

        if (files.isEmpty()) {
            this.addButton(new Button(cx - 130, listStartY, 260, BTN_H, new TranslationTextComponent("wavedefense.auto.файли_не_знайдено_у_wave_export_4006e193"), b -> {})).active = false;
        } else {
            int max = Math.max(0, files.size() - itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = i + scrollOffset;
                if (idx >= files.size()) break;
                final String fileName = files.get(idx);
                this.addButton(new Button(cx - 130, listStartY + i * (BTN_H + GAP), 260, BTN_H, new StringTextComponent("§b⬇ " + fileName), b -> minecraft.setScreen(new WaveImportTargetScreen(location, fileName, this))));
            }

            // Скрол
            if (files.size() > itemsPerPage) {
                this.addButton(new Button(cx + 133, listStartY, 18, BTN_H, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; rebuild(); } }));
                this.addButton(new Button(cx + 133, this.height - 35, 18, BTN_H, new StringTextComponent("▼"), b -> { if (scrollOffset < max) { scrollOffset++; rebuild(); } }));
            }
        }

        this.addButton(new Button(cx - 55, this.height - 28, 110, BTN_H, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), b -> { ClientWaveExportManager.clearOnUpdate(); minecraft.setScreen(parent); }));

        // Якщо імпорт вже відбувся — повертаємось до WaveConfigScreen автоматично
        ClientWaveExportManager.setOnUpdate(() -> {
            // Оновлюємо список якщо отримали нові дані
            if (minecraft.screen == this) buildWidgets();
        });
    }

    @Override
    public void onClose() {
        ClientWaveExportManager.clearOnUpdate();
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int itemsPerPage = Math.max(3, (this.height - 83) / (BTN_H + GAP));
        int max = Math.max(0, ClientWaveExportManager.getFiles().size() - itemsPerPage);
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuild(); }
        else if (delta < 0 && scrollOffset < max) { scrollOffset++; rebuild(); }
        return true;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 12, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font,
            net.minecraft.client.resources.I18n.get("wavedefense.wave.import_hint"),
            this.width / 2, 24, 0x888888);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
