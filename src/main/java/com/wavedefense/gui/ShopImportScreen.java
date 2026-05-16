package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportShopPacket;
import com.wavedefense.network.packets.RequestShopExportListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        super(Component.translatable("wavedefense.auto.імпорт_магазину_bb2c4eb5"));
        this.location = location;
        this.isPointMode = isPointMode;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Запитуємо список файлів при відкритті
        PacketHandler.sendToServer(new RequestShopExportListPacket());
        ClientShopExportManager.setOnUpdate(this::rebuildWidgets);
        buildWidgets();
    }

    private void buildWidgets() {
        this.clearWidgets();
        int cx = this.width / 2;
        List<String> files = ClientShopExportManager.getFiles();

        if (files.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.немає_збережених_файлів_спочатку_30f8d2d1"), b -> {}
            ).bounds(cx - 160, 50, 320, 14).build()).active = false;
        } else {
            int y = 44;
            for (int i = 0; i < Math.min(FILES_PER_PAGE, files.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= files.size()) break;
                final String fileName = files.get(idx);
                this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + fileName),
                    b -> showTargetSelect(fileName)
                ).bounds(cx - 150, y + i * 22, 300, 20).build());
            }
            if (files.size() > FILES_PER_PAGE) {
                this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
                ).bounds(cx + 156, 44, 18, 18).build());
                this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { int max = Math.max(0, files.size() - FILES_PER_PAGE);
                           if (scrollOffset < max) { scrollOffset++; rebuildWidgets(); } }
                ).bounds(cx + 156, this.height - 52, 18, 18).build());
            }
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.оновити_список_07c054ef"),
            b -> { PacketHandler.sendToServer(new RequestShopExportListPacket()); }
        ).bounds(cx - 80, this.height - 50, 160, 18).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.назад_f6dab074"), b -> minecraft.setScreen(parent)
        ).bounds(cx - 55, this.height - 28, 110, 20).build());
    }

    private void showTargetSelect(String fileName) {
        // Якщо глобальний режим — одразу імпортуємо
        if (!isPointMode) {
            PacketHandler.sendToServer(new ImportShopPacket(location.getName(), fileName, "global"));
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    Component.translatable("wavedefense.auto.імпортовано_глобальний_магазин_з_value_7439861b", fileName + "»"), true);
            minecraft.setScreen(parent);
            return;
        }
        // Точковий режим — вибрати ціль (нова або замінити існуючу)
        minecraft.setScreen(new ShopImportTargetScreen(location, fileName, parent));
    }

    @Override
    protected void rebuildWidgets() {
        buildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.shop.import_pick_file"), this.width / 2, 22, 0xAAAAAA);
        String modeLabel = isPointMode ? "§7Режим: §eточковий" : "§7Режим: §eглобальний";
        g.drawCenteredString(this.font, modeLabel, this.width / 2, 32, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
