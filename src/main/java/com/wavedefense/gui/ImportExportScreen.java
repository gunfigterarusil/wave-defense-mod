package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportLocationPacket;
import com.wavedefense.network.packets.ImportLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.wavedefense.gui.ScissorHelper;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Екран імпорту/експорту локацій через файли на сервері.
 * Експорт: зберігає локацію як locations/export/<name>.nbt
 * Імпорт: завантажує локацію з locations/export/<name>.nbt
 */
public class ImportExportScreen extends Screen {
    private final Screen parent;
    private EditBox importNameInput;
    private String statusMsg = "";

    // Список доступних .nbt файлів (отримується з сервера)
    private List<String> availableExports = new java.util.ArrayList<>();
    private int scrollOffset = 0;
    private static final int LIST_PER_PAGE = 6;

    public ImportExportScreen(Screen parent) {
        super(Component.translatable("wavedefense.auto.імпорт_експорт_локацій_ca7d533a"));
        this.parent = parent;
    }

    /** Викликається після отримання відповіді сервера */
    public void setAvailableExports(List<String> names) {
        this.availableExports = names;
        if (this.minecraft != null)
            this.minecraft.tell(() -> { if (this.minecraft.screen == this) rebuildWidgets(); });
    }

    public void setStatus(String msg) {
        this.statusMsg = msg;
        if (this.minecraft != null)
            this.minecraft.tell(() -> { if (this.minecraft.screen == this) rebuildWidgets(); });
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 30;

        // ── СЕКЦІЯ ЕКСПОРТУ ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.експорт_db6cfb09"), b -> {}
        ).bounds(cx - 160, y, 320, 12).build()).active = false;
        y += 16;

        // Список активних локацій для експорту
        List<String> allLocs = ClientLocationManager.getAllLocationNames();
        int lx = cx - 160;
        for (String locName : allLocs) {
            final String ln = locName;
            this.addRenderableWidget(Button.builder(
                Component.literal("§a📤 " + locName),
                b -> {
                    PacketHandler.sendToServer(new ExportLocationPacket(ln));
                    statusMsg = "§7Експортую §e" + ln + "§7...";
                    rebuildWidgets();
                }
            ).bounds(lx, y, 300, 18).build());
            y += 22;
        }
        if (allLocs.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.немає_локацій_0684a624"), b -> {}
            ).bounds(lx, y, 200, 14).build()).active = false;
            y += 18;
        }
        y += 6;

        // ── СЕКЦІЯ ІМПОРТУ ─────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.імпорт_1edc3386"), b -> {}
        ).bounds(cx - 160, y, 320, 12).build()).active = false;
        y += 16;

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.оновити_список_0f28150f"), b -> {
                PacketHandler.sendToServer(new ExportLocationPacket("__list__"));
                statusMsg = "§7Завантажую список...";
                rebuildWidgets();
            }
        ).bounds(cx - 160, y, 200, 18).build());
        y += 22;

        // Список файлів для імпорту
        int visibleFrom = scrollOffset;
        int visibleTo = Math.min(scrollOffset + LIST_PER_PAGE, availableExports.size());
        for (int i = visibleFrom; i < visibleTo; i++) {
            String fname = availableExports.get(i);
            final String fn = fname;
            this.addRenderableWidget(Button.builder(
                Component.literal("§b📥 " + fname),
                b -> {
                    PacketHandler.sendToServer(new ImportLocationPacket(fn));
                    statusMsg = "§7Імпортую §e" + fn + "§7...";
                    rebuildWidgets();
                }
            ).bounds(lx, y, 300, 18).build());
            y += 22;
        }
        if (availableExports.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.немає_файлів_натисніть_оновити_77eada10"), b -> {}
            ).bounds(lx, y, 300, 14).build()).active = false;
            y += 18;
        }
        if (availableExports.size() > LIST_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }}
            ).bounds(cx + 165, y - (visibleTo - visibleFrom) * 22, 18, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (scrollOffset + LIST_PER_PAGE < availableExports.size()) { scrollOffset++; rebuildWidgets(); }}
            ).bounds(cx + 165, y - 20, 18, 16).build());
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.назад_3fa51863"), b -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 28, 100, 20).build());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.import_export.title"), this.width / 2, 10, GuiTheme.TEXT);
        if (!statusMsg.isEmpty())
            g.drawCenteredString(this.font, statusMsg, this.width / 2, this.height - 44, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("wavedefense.import_export.path_hint"),
            this.width / 2 - 160, this.height - 12, 0x888888);

        // Scissor: прокручуваний контент між заголовком (24) і нижніми елементами (height-52)
        int listTop = 24, listBot = this.height - 52;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                w.render(g, mouseX, mouseY, partial);
        }
        g.flush();
        ScissorHelper.disable();
        // Static footer (Назад кнопка)
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < listTop || w.getY() >= listBot))
                w.render(g, mouseX, mouseY, partial);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
