package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportWavePacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

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
        super(new TranslationTextComponent("wavedefense.wave.export_title", location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.buttons.clear(); this.children.clear();
        int cx = this.width / 2;
        int y  = 50;

        // ── Заголовок ─────────────────────────────────────────────────
        // "Зберегти всі хвилі" — один файл з усіма
        this.addButton(new Button(cx - 130, y, 260, BTN_H, new TranslationTextComponent("wavedefense.wave.export_save_all", location.getWaves().size()), b -> {
                PacketHandler.sendToServer(new ExportWavePacket(location.getName(), "all"));
                if (minecraft.player != null)
                    minecraft.player.displayClientMessage(
                        new TranslationTextComponent("wavedefense.auto.надіслано_запит_на_збереження_вс_67d5986a"), true);
                minecraft.setScreen(parent);
            }));

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

            this.addButton(new Button(cx - 130, y + i * (BTN_H + GAP), 260, BTN_H, new StringTextComponent(label), b -> {
                    PacketHandler.sendToServer(
                        new ExportWavePacket(location.getName(), "wave:" + finalIdx));
                    if (minecraft.player != null)
                        minecraft.player.displayClientMessage(
                            new TranslationTextComponent("wavedefense.wave.export_saving_wave", finalIdx + 1), true);
                    minecraft.setScreen(parent);
                }));
        }

        // ── Скрол ─────────────────────────────────────────────────────
        if (waves.size() > itemsPerPage) {
            this.addButton(new Button(cx + 133, 50 + BTN_H + 14, 18, BTN_H, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } }));
            this.addButton(new Button(cx + 133, this.height - 35, 18, BTN_H, new StringTextComponent("▼"), b -> { if (scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; init(); } }));
        }

        // ── Скасувати ─────────────────────────────────────────────────
        this.addButton(new Button(cx - 55, this.height - 28, 110, BTN_H, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), b -> minecraft.setScreen(parent)));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        List<WaveConfig> waves = location.getWaves();
        int itemsPerPage = Math.max(3, (this.height - 100) / (BTN_H + GAP));
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; init(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; init(); }
        return true;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 16, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font,
            I18n.get("wavedefense.wave.export_hint"),
            this.width / 2, 28, 0x888888);

        // Роздільна лінія між "всі" і окремими
        com.wavedefense.gui.GuiCompat.fill(g, this.width / 2 - 130, 50 + BTN_H + 6, this.width / 2 + 130, 50 + BTN_H + 7, 0x55FFFFFF);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.wave.export_or_single"),
            this.width / 2, 50 + BTN_H + 8, 0x666666);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
