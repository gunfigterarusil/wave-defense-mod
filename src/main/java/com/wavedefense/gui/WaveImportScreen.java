package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestWaveExportListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Список файлів у wave_export/ — вибрати один → відкрити WaveImportTargetScreen.
 */
public class WaveImportScreen extends Screen {

    final Location location;
    final Screen parent;
    private int scrollOffset = 0;

    private static final int BTN_H = 20;
    private static final int GAP   = 4;

    public WaveImportScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.wave.import_title", location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        // Запитуємо список файлів
        PacketHandler.sendToServer(new RequestWaveExportListPacket());
        buildWidgets();
    }

    private void buildWidgets() {
        this.clearWidgets();
        int cx = this.width / 2;
        List<String> files = ClientWaveExportManager.getFiles();

        int listStartY = 48;
        int itemsPerPage = Math.max(3, (this.height - listStartY - 35) / (BTN_H + GAP));

        if (files.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.файли_не_знайдено_у_wave_export_4006e193"),
                b -> {}
            ).bounds(cx - 130, listStartY, 260, BTN_H).build()).active = false;
        } else {
            int max = Math.max(0, files.size() - itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = i + scrollOffset;
                if (idx >= files.size()) break;
                final String fileName = files.get(idx);
                this.addRenderableWidget(Button.builder(
                    Component.literal("§b⬇ " + fileName),
                    b -> minecraft.setScreen(new WaveImportTargetScreen(location, fileName, this))
                ).bounds(cx - 130, listStartY + i * (BTN_H + GAP), 260, BTN_H).build());
            }

            // Скрол
            if (files.size() > itemsPerPage) {
                this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
                ).bounds(cx + 133, listStartY, 18, BTN_H).build());
                this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (scrollOffset < max) { scrollOffset++; rebuildWidgets(); } }
                ).bounds(cx + 133, this.height - 35, 18, BTN_H).build());
            }
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.скасувати_8b4c2025"),
            b -> { ClientWaveExportManager.clearOnUpdate(); minecraft.setScreen(parent); }
        ).bounds(cx - 55, this.height - 28, 110, BTN_H).build());

        // Якщо імпорт вже відбувся — повертаємось до WaveConfigScreen автоматично
        ClientWaveExportManager.setOnUpdate(() -> {
            // Оновлюємо список якщо отримали нові дані
            if (minecraft.screen == this) buildWidgets();
        });
    }

    @Override
    protected void rebuildWidgets() { buildWidgets(); }

    @Override
    public void onClose() {
        ClientWaveExportManager.clearOnUpdate();
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int itemsPerPage = Math.max(3, (this.height - 83) / (BTN_H + GAP));
        int max = Math.max(0, ClientWaveExportManager.getFiles().size() - itemsPerPage);
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
        else if (delta < 0 && scrollOffset < max) { scrollOffset++; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(this.font,
            net.minecraft.client.resources.language.I18n.get("wavedefense.wave.import_hint"),
            this.width / 2, 24, 0x888888);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
