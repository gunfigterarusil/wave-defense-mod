package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportWavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Вибір хвиль для експорту:
 * - Кнопка "Всі хвилі" → mode "all"
 * - Кнопка для кожної хвилі → mode "wave:N"
 */
public class WaveExportScreen extends Screen {

    private final Location location;
    private final Screen parent;
    private int scrollOffset = 0;

    private static final int BTN_H = 20;
    private static final int GAP   = 4;

    public WaveExportScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.wave.export_title", location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int cx = this.width / 2;
        int y  = 50;

        // ── Заголовок ─────────────────────────────────────────────────
        // "Зберегти всі хвилі" — один файл з усіма
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.wave.export_save_all", location.getWaves().size()),
            b -> {
                PacketHandler.sendToServer(new ExportWavePacket(location.getName(), "all"));
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(
                        Component.translatable("wavedefense.auto.надіслано_запит_на_збереження_вс_67d5986a"), true);
                minecraft.setScreen(parent);
            }
        ).bounds(cx - 130, y, 260, BTN_H).build());

        y += BTN_H + 10;

        // ── Горизонтальний розділювач ──────────────────────────────────
        y += 4;

        // ── Окремі хвилі ──────────────────────────────────────────────
        List<WaveConfig> waves = location.getWaves();
        int itemsPerPage = Math.max(3, (this.height - y - 35) / (BTN_H + GAP));

        for (int i = 0; i < itemsPerPage; i++) {
            int idx = i + scrollOffset;
            if (idx >= waves.size()) break;
            WaveConfig wc = waves.get(idx);
            final int finalIdx = idx;

            String label = I18n.get("wavedefense.wave.export_wave_line",
                idx + 1, wc.getMobs().size(), wc.getTimeBetweenWaves(),
                wc.isTriggerEnabled() ? " " + I18n.get("wavedefense.wave.export_wave_trigger") : "");

            this.addRenderableWidget(Button.builder(
                Component.literal(label),
                b -> {
                    PacketHandler.sendToServer(
                        new ExportWavePacket(location.getName(), "wave:" + finalIdx));
                    if (minecraft.player != null)
                        minecraft.player.displayClientMessage(
                            Component.translatable("wavedefense.wave.export_saving_wave", finalIdx + 1), true);
                    minecraft.setScreen(parent);
                }
            ).bounds(cx - 130, y + i * (BTN_H + GAP), 260, BTN_H).build());
        }

        // ── Скрол ─────────────────────────────────────────────────────
        if (waves.size() > itemsPerPage) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 133, 50 + BTN_H + 14, 18, BTN_H).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 133, this.height - 35, 18, BTN_H).build());
        }

        // ── Скасувати ─────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.скасувати_8b4c2025"),
            b -> minecraft.setScreen(parent)
        ).bounds(cx - 55, this.height - 28, 110, BTN_H).build());
    }

    @Override
    protected void rebuildWidgets() {
        init();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        List<WaveConfig> waves = location.getWaves();
        int itemsPerPage = Math.max(3, (this.height - 100) / (BTN_H + GAP));
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, GuiTheme.TEXT);
        g.drawCenteredString(this.font,
            I18n.get("wavedefense.wave.export_hint"),
            this.width / 2, 28, 0x888888);

        // Роздільна лінія між "всі" і окремими
        g.fill(this.width / 2 - 130, 50 + BTN_H + 6, this.width / 2 + 130, 50 + BTN_H + 7, 0x55FFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.wave.export_or_single"),
            this.width / 2, 50 + BTN_H + 8, 0x666666);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
