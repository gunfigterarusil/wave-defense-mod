package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportShopPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Вибір цільової точки при імпорті у точковий магазин. */
public class ShopImportTargetScreen extends Screen {

    private final Location location;
    private final String fileName;
    private final Screen parent;

    public ShopImportTargetScreen(Location location, String fileName, Screen parent) {
        super(Component.translatable("wavedefense.auto.вибір_цільової_точки_3ea40ab2"));
        this.location = location;
        this.fileName = fileName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 44;

        // Кнопка «Нова точка» — додає без прив'язки до існуючої
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.додати_як_нову_точку_d1f0ba19"),
            b -> doImport("new")
        ).bounds(cx - 130, y, 260, 20).build());

        y += 28;

        if (!location.getShopPoints().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.або_замінити_товари_існуючої_точ_2e9ed56b"), b -> {}
            ).bounds(cx - 130, y, 260, 14).build()).active = false;
            y += 18;

            for (ShopPoint sp : location.getShopPoints()) {
                final String pName = sp.getName();
                this.addRenderableWidget(Button.builder(
                    Component.literal("§e↺ " + pName),
                    b -> doImport("point:" + pName)
                ).bounds(cx - 130, y, 260, 20).build());
                y += 24;
            }
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.скасувати_8b4c2025"), b -> minecraft.setScreen(parent)
        ).bounds(cx - 55, this.height - 28, 110, 20).build());
    }

    private void doImport(String target) {
        PacketHandler.sendToServer(new ImportShopPacket(location.getName(), fileName, target));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.auto.імпорт_магазину_надіслано_51097785"), true);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.shop.import_file", fileName), this.width / 2, 24, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
