package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportLocationPacket;
import com.wavedefense.network.packets.ImportLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import com.wavedefense.gui.ScissorHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Екран імпорту/експорту локацій через файли на сервері.
 * Експорт: зберігає локацію як locations/export/<name>.nbt
 * Імпорт: завантажує локацію з locations/export/<name>.nbt
 */
public class ImportExportScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget importNameInput;
    private String statusMsg = "";
    /** F6 fix: file that awaits confirmation before import (null = none pending). */
    private String pendingImportFile = null;

    // Список доступних .nbt файлів (отримується з сервера)
    private List<String> availableExports = new java.util.ArrayList<>();
    private int scrollOffset = 0;
    private static final int LIST_PER_PAGE = 6;

    public ImportExportScreen(Screen parent) {
        super(new TranslationTextComponent("wavedefense.auto.імпорт_експорт_локацій_ca7d533a"));
        this.parent = parent;
    }

    /** Викликається після отримання відповіді сервера */
    public void setAvailableExports(List<String> names) {
        this.availableExports = names;
        if (this.minecraft != null)
            this.minecraft.tell(() -> { if (this.minecraft.screen == this) init(); });
    }

    public void setStatus(String msg) {
        this.statusMsg = msg;
        if (this.minecraft != null)
            this.minecraft.tell(() -> { if (this.minecraft.screen == this) init(); });
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 30;

        // ── СЕКЦІЯ ЕКСПОРТУ ─────────────────────────────────────────────
        this.addButton(new Button(cx - 160, y, 320, 12, new TranslationTextComponent("wavedefense.auto.експорт_db6cfb09"), b -> {})).active = false;
        y += 16;

        // Список активних локацій для експорту
        List<String> allLocs = ClientLocationManager.getAllLocationNames();
        int lx = cx - 160;
        for (String locName : allLocs) {
            final String ln = locName;
            this.addButton(new Button(lx, y, 300, 18, new StringTextComponent("§a📤 " + locName), b -> {
                    PacketHandler.sendToServer(new ExportLocationPacket(ln));
                    statusMsg = String.format(I18n.get("wavedefense.export.status_exporting"), ln);
                    init();
                }));
            y += 22;
        }
        if (allLocs.isEmpty()) {
            this.addButton(new Button(lx, y, 200, 14, new TranslationTextComponent("wavedefense.auto.немає_локацій_0684a624"), b -> {})).active = false;
            y += 18;
        }
        y += 6;

        // ── СЕКЦІЯ ІМПОРТУ ─────────────────────────────────────────────
        this.addButton(new Button(cx - 160, y, 320, 12, new TranslationTextComponent("wavedefense.auto.імпорт_1edc3386"), b -> {})).active = false;
        y += 16;

        this.addButton(new Button(cx - 160, y, 200, 18, new TranslationTextComponent("wavedefense.auto.оновити_список_0f28150f"), b -> {
                PacketHandler.sendToServer(new ExportLocationPacket("__list__"));
                statusMsg = I18n.get("wavedefense.export.status_loading");
                init();
            }));
        y += 22;

        // ── F6 fix: confirmation banner if a file is pending import ────────
        if (pendingImportFile != null) {
            final String pf = pendingImportFile;
            // Warning row
            this.addButton(new Button(lx, y, 300, 14, new TranslationTextComponent("wavedefense.import.confirm_overwrite", pf), b -> {})).active = false;
            y += 18;
            // Confirm button
            this.addButton(new Button(lx, y, 145, 18, new TranslationTextComponent("wavedefense.button.confirm"), b -> {
                    PacketHandler.sendToServer(new ImportLocationPacket(pf));
                    statusMsg = net.minecraft.client.resources.I18n.get(
                        "wavedefense.import.status_importing", pf);
                    pendingImportFile = null;
                    init();
                }));
            this.addButton(new Button(lx + 155, y, 145, 18, new TranslationTextComponent("wavedefense.button.cancel"), b -> { pendingImportFile = null; init(); }));
            y += 24;
        }

        // Список файлів для імпорту
        int visibleFrom = scrollOffset;
        int visibleTo = Math.min(scrollOffset + LIST_PER_PAGE, availableExports.size());
        for (int i = visibleFrom; i < visibleTo; i++) {
            String fname = availableExports.get(i);
            final String fn = fname;
            this.addButton(new Button(lx, y, 300, 18, new StringTextComponent("§b📥 " + fname), b -> {
                    // F6 fix: require confirmation before import (prevents accidental overwrite)
                    pendingImportFile = fn;
                    init();
                }));
            y += 22;
        }
        if (availableExports.isEmpty()) {
            this.addButton(new Button(lx, y, 300, 14, new TranslationTextComponent("wavedefense.auto.немає_файлів_натисніть_оновити_77eada10"), b -> {})).active = false;
            y += 18;
        }
        if (availableExports.size() > LIST_PER_PAGE) {
            this.addButton(new Button(cx + 165, y - (visibleTo - visibleFrom) * 22, 18, 16, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); }}));
            this.addButton(new Button(cx + 165, y - 20, 18, 16, new StringTextComponent("▼"), b -> { if (scrollOffset + LIST_PER_PAGE < availableExports.size()) { scrollOffset++; init(); }}));
        }

        this.addButton(new Button(cx - 50, this.height - 28, 100, 20, new TranslationTextComponent("wavedefense.auto.назад_3fa51863"), b -> this.minecraft.setScreen(parent)));
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
    public void render(MatrixStack g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.import_export.title"), this.width / 2, 10, GuiTheme.TEXT);
        if (!statusMsg.isEmpty())
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, statusMsg, this.width / 2, this.height - 44, 0xFFFFFF);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, new TranslationTextComponent("wavedefense.import_export.path_hint"),
            this.width / 2 - 160, this.height - 12, 0x888888);

        // Scissor: прокручуваний контент між заголовком (24) і нижніми елементами (height-52)
        int listTop = 24, listBot = this.height - 52;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y + w.getHeight() > listTop && w.y < listBot) w.render(g, mouseX, mouseY, partial);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();
        // Static footer (Назад кнопка)
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if ((w.y < listTop || w.y >= listBot)) w.render(g, mouseX, mouseY, partial);
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
