package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportShopPacket;
import com.wavedefense.network.packets.RequestShopExportListPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Екран імпорту магазину з .nbt файлу.
 * Показує список доступних файлів (отриманих від сервера).
 * Дозволяє вибрати ціль: global або конкретну точку.
 */
public class ShopImportScreen extends Screen {

    private final Location location;
    private final boolean isPointMode;
    private final Screen parent;

    private int scrollOffset = 0;
    private static final int FILES_PER_PAGE = 6;

    public ShopImportScreen(Location location, boolean isPointMode, Screen parent) {
        super(new TranslationTextComponent("wavedefense.auto.імпорт_магазину_bb2c4eb5"));
        this.location = location;
        this.isPointMode = isPointMode;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Запитуємо список файлів при відкритті
        PacketHandler.sendToServer(new RequestShopExportListPacket());
        ClientShopExportManager.setOnUpdate(() -> this.init());
        buildWidgets();
    }

    private void buildWidgets() {
        this.buttons.clear(); this.children.clear();
        int cx = this.width / 2;
        List<String> files = ClientShopExportManager.getFiles();

        if (files.isEmpty()) {
            this.addButton(new Button(cx - 160, 50, 320, 14, new TranslationTextComponent("wavedefense.auto.немає_збережених_файлів_спочатку_30f8d2d1"), b -> {})).active = false;
        } else {
            int y = 44;
            for (int i = 0; i < Math.min(FILES_PER_PAGE, files.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= files.size()) break;
                final String fileName = files.get(idx);
                this.addButton(new Button(cx - 150, y + i * 22, 300, 20, new StringTextComponent("§e" + fileName), b -> showTargetSelect(fileName)));
            }
            if (files.size() > FILES_PER_PAGE) {
                this.addButton(new Button(cx + 156, 44, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } }));
                this.addButton(new Button(cx + 156, this.height - 52, 18, 18, new StringTextComponent("▼"), b -> { int max = Math.max(0, files.size() - FILES_PER_PAGE);
                           if (scrollOffset < max) { scrollOffset++; init(); } }));
            }
        }

        this.addButton(new Button(cx - 80, this.height - 50, 160, 18, new TranslationTextComponent("wavedefense.auto.оновити_список_07c054ef"), b -> { PacketHandler.sendToServer(new RequestShopExportListPacket()); }));

        this.addButton(new Button(cx - 55, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.auto.назад_f6dab074"), b -> minecraft.setScreen(parent)));
    }

    private void showTargetSelect(String fileName) {
        // Якщо глобальний режим — одразу імпортуємо
        if (!isPointMode) {
            PacketHandler.sendToServer(new ImportShopPacket(location.getName(), fileName, "global"));
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.auto.імпортовано_глобальний_магазин_з_value_7439861b", fileName + "»"), true);
            minecraft.setScreen(parent);
            return;
        }
        // Точковий режим — вибрати ціль (нова або замінити існуючу)
        minecraft.setScreen(new ShopImportTargetScreen(location, fileName, parent));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.shop.import_pick_file"), this.width / 2, 22, 0xAAAAAA);
        String modeLabel = isPointMode ? I18n.get("wavedefense.shop.import_mode_point") : I18n.get("wavedefense.shop.import_mode_global");
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, modeLabel, this.width / 2, 32, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
