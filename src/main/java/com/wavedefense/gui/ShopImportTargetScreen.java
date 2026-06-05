package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ImportShopPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/** Вибір цільової точки при імпорті у точковий магазин. */
public class ShopImportTargetScreen extends Screen {

    private final Location location;
    private final String fileName;
    private final Screen parent;

    public ShopImportTargetScreen(Location location, String fileName, Screen parent) {
        super(new TranslationTextComponent("wavedefense.auto.вибір_цільової_точки_3ea40ab2"));
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
        this.addButton(new Button(cx - 130, y, 260, 20, new TranslationTextComponent("wavedefense.auto.додати_як_нову_точку_d1f0ba19"), b -> doImport("new")));

        y += 28;

        if (!location.getShopPoints().isEmpty()) {
            this.addButton(new Button(cx - 130, y, 260, 14, new TranslationTextComponent("wavedefense.auto.або_замінити_товари_існуючої_точ_2e9ed56b"), b -> {})).active = false;
            y += 18;

            for (ShopPoint sp : location.getShopPoints()) {
                final String pName = sp.getName();
                this.addButton(new Button(cx - 130, y, 260, 20, new StringTextComponent("§e↺ " + pName), b -> doImport("point:" + pName)));
                y += 24;
            }
        }

        this.addButton(new Button(cx - 55, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.auto.скасувати_8b4c2025"), b -> minecraft.setScreen(parent)));
    }

    private void doImport(String target) {
        PacketHandler.sendToServer(new ImportShopPacket(location.getName(), fileName, target));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.auto.імпорт_магазину_надіслано_51097785"), true);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.shop.import_file", fileName), this.width / 2, 24, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
