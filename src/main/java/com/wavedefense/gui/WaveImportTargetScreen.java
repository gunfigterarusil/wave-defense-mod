package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportWavePacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Вибір способу вставки імпортованих хвиль:
 *  - Замінити всі хвилі
 *  - Додати в кінець
 *  - Замінити конкретну хвилю
 *  - Вставити після конкретної хвилі
 */
public class WaveImportTargetScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private final Location location;
    private final String fileName;
    private final Screen parent;
    private int scrollOffset = 0;

    private static final int BTN_H = 20;
    private static final int GAP   = 4;

    public WaveImportTargetScreen(Location location, String fileName, Screen parent) {
        super(new TranslationTextComponent("wavedefense.wave.import_target_title", fileName, location.getName()));
        this.location = location;
        this.fileName = fileName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.buttons.clear(); this.children.clear();
        int cx = this.width / 2;
        int y  = 46;

        // ── Замінити всі хвилі ────────────────────────────────────────
        this.addButton(new Button(cx - 130, y, 260, BTN_H, new TranslationTextComponent("wavedefense.auto.замінити_всі_хвилі_132cdde4"), b -> sendImport("replace_all")))
        /* setTooltip omitted on 1.16.5 */;

        y += BTN_H + GAP + 4;

        // ── Додати в кінець ───────────────────────────────────────────
        this.addButton(new Button(cx - 130, y, 260, BTN_H, new TranslationTextComponent("wavedefense.wave.import_append", location.getWaves().size()), b -> sendImport("append")));

        y += BTN_H + 12;

        // ── Роздільник ────────────────────────────────────────────────
        final int divY = y;

        y += 10;

        // ── По кожній хвилі ───────────────────────────────────────────
        List<WaveConfig> waves = location.getWaves();
        int listStartY = y;
        int itemsPerPage = Math.max(2, (this.height - listStartY - 35) / ((BTN_H + GAP) * 2 + 6));

        for (int i = 0; i < itemsPerPage; i++) {
            int idx = i + scrollOffset;
            if (idx >= waves.size()) break;
            WaveConfig wc = waves.get(idx);
            final int finalIdx = idx;

            String info = net.minecraft.client.resources.I18n.get(
                "wavedefense.wave.export_wave_line", idx + 1, wc.getMobs().size(), wc.getTimeBetweenWaves(), "");

            // Замінити цю хвилю
            this.addButton(new Button(cx - 130, listStartY + i * ((BTN_H + GAP) * 2 + 4), 260, BTN_H, new TranslationTextComponent("wavedefense.auto.замінити_value_908f6b74", info), b -> sendImport("replace:" + finalIdx)));

            // Вставити після цієї хвилі
            this.addButton(new Button(cx - 130, listStartY + i * ((BTN_H + GAP) * 2 + 4) + BTN_H + GAP, 260, BTN_H, new TranslationTextComponent("wavedefense.auto.після_value_e155ba08", info), b -> sendImport("insert_after:" + finalIdx)));
        }

        // ── Скрол ─────────────────────────────────────────────────────
        if (waves.size() > itemsPerPage) {
            this.addButton(new Button(cx + 133, listStartY, 18, BTN_H, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } }));
            this.addButton(new Button(cx + 133, this.height - 35, 18, BTN_H, new StringTextComponent("▼"), b -> {
                    if (scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; init(); }
                }));
        }

        // ── Скасувати ─────────────────────────────────────────────────
        this.addButton(new Button(cx - 55, this.height - 28, 110, BTN_H, new TranslationTextComponent("wavedefense.auto.назад_3fa51863"), b -> minecraft.setScreen(parent)));
    }

    private void sendImport(String insertMode) {
        PacketHandler.sendToServer(new ImportWavePacket(fileName, location.getName(), insertMode));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.auto.імпортую_хвилі_з_value_fc238cb7", fileName + "§b..."), true);
        // Повертаємось до WaveConfigScreen (пропускаємо WaveImportScreen)
        Screen target = (parent instanceof WaveImportScreen) ? ((WaveImportScreen) parent).parent : parent;
        ClientWaveExportManager.clearOnUpdate();
        minecraft.setScreen(target);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int itemsPerPage = Math.max(2, (this.height - 120) / ((BTN_H + GAP) * 2 + 4));
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuild(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < location.getWaves().size()) { scrollOffset++; rebuild(); }
        return true;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.wave.import_insert_mode"), this.width / 2, 22, 0xAAAAAA);

        // Роздільник між загальними і порядковими
        int divY = 46 + BTN_H + GAP + 4 + BTN_H + GAP + 2;
        com.wavedefense.gui.GuiCompat.fill(g, this.width / 2 - 130, divY + 8, this.width / 2 + 130, divY + 9, 0x44FFFFFF);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.wave.import_or_relative"), this.width / 2, divY, 0x666666);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
