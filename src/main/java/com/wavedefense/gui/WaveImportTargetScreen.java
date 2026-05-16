package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportWavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Вибір способу вставки імпортованих хвиль:
 *  - Замінити всі хвилі
 *  - Додати в кінець
 *  - Замінити конкретну хвилю
 *  - Вставити після конкретної хвилі
 */
public class WaveImportTargetScreen extends Screen {

    private final Location location;
    private final String fileName;
    private final Screen parent;
    private int scrollOffset = 0;

    private static final int BTN_H = 20;
    private static final int GAP   = 4;

    public WaveImportTargetScreen(Location location, String fileName, Screen parent) {
        super(Component.literal("§b⬇ " + fileName + " → §e" + location.getName()));
        this.location = location;
        this.fileName = fileName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int cx = this.width / 2;
        int y  = 46;

        // ── Замінити всі хвилі ────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.замінити_всі_хвилі_132cdde4"),
            b -> sendImport("replace_all")
        ).bounds(cx - 130, y, 260, BTN_H).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.видалити_поточні_хвилі_і_вставит_58c1357b")));

        y += BTN_H + GAP + 4;

        // ── Додати в кінець ───────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.literal("§a➕ Додати в кінець (після хвилі " + location.getWaves().size() + ")"),
            b -> sendImport("append")
        ).bounds(cx - 130, y, 260, BTN_H).build());

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

            String info = String.format("Хвиля %d  §7(%d мобів, %d сек)", idx + 1, wc.getMobs().size(), wc.getTimeBetweenWaves());

            // Замінити цю хвилю
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.замінити_value_908f6b74", info),
                b -> sendImport("replace:" + finalIdx)
            ).bounds(cx - 130, listStartY + i * ((BTN_H + GAP) * 2 + 4), 260, BTN_H).build());

            // Вставити після цієї хвилі
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.після_value_e155ba08", info),
                b -> sendImport("insert_after:" + finalIdx)
            ).bounds(cx - 130, listStartY + i * ((BTN_H + GAP) * 2 + 4) + BTN_H + GAP, 260, BTN_H).build());
        }

        // ── Скрол ─────────────────────────────────────────────────────
        if (waves.size() > itemsPerPage) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 133, listStartY, 18, BTN_H).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> {
                    if (scrollOffset + itemsPerPage < waves.size()) { scrollOffset++; rebuildWidgets(); }
                }
            ).bounds(cx + 133, this.height - 35, 18, BTN_H).build());
        }

        // ── Скасувати ─────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.назад_3fa51863"),
            b -> minecraft.setScreen(parent)
        ).bounds(cx - 55, this.height - 28, 110, BTN_H).build());
    }

    private void sendImport(String insertMode) {
        PacketHandler.sendToServer(new ImportWavePacket(fileName, location.getName(), insertMode));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.auto.імпортую_хвилі_з_value_fc238cb7", fileName + "§b..."), true);
        // Повертаємось до WaveConfigScreen (пропускаємо WaveImportScreen)
        Screen target = (parent instanceof WaveImportScreen imp) ? imp.parent : parent;
        ClientWaveExportManager.clearOnUpdate();
        minecraft.setScreen(target);
    }

    @Override
    protected void rebuildWidgets() { init(); }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int itemsPerPage = Math.max(2, (this.height - 120) / ((BTN_H + GAP) * 2 + 4));
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < location.getWaves().size()) { scrollOffset++; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.wave.import_insert_mode"), this.width / 2, 22, 0xAAAAAA);

        // Роздільник між загальними і порядковими
        int divY = 46 + BTN_H + GAP + 4 + BTN_H + GAP + 2;
        g.fill(this.width / 2 - 130, divY + 8, this.width / 2 + 130, divY + 9, 0x44FFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.wave.import_or_relative"), this.width / 2, divY, 0x666666);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
