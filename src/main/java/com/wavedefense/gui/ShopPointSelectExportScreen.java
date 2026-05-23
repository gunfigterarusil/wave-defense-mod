package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportShopPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Вибір точки магазину для експорту. */
public class ShopPointSelectExportScreen extends Screen {

    private final Location location;
    private final Screen parent;

    public ShopPointSelectExportScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.auto.експорт_точки_магазину_e19a3bbd"));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 40;

        List<ShopPoint> points = location.getShopPoints();
        if (points.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.точок_немає_6ee9d45d"), b -> {}
            ).bounds(cx - 100, y, 200, 20).build()).active = false;
        } else {
            for (int pi = 0; pi < points.size(); pi++) {
                ShopPoint sp = points.get(pi);
                final String pName = sp.getName();
                final int rowY = y + pi * 26;
                this.addRenderableWidget(Button.builder(
                    Component.literal("§e⬆ " + pName + " §7(" + sp.getItems().size() + " " + net.minecraft.client.resources.language.I18n.get("wavedefense.item.count_suffix") + ")"),
                    b -> {
                        PacketHandler.sendToServer(new ExportShopPacket(location.getName(), "point:" + pName));
                        if (minecraft.player != null)
                            minecraft.player.displayClientMessage(
                                Component.translatable("wavedefense.auto.збережено_точку_value_b9ad0158", pName + "»"), true);
                        minecraft.setScreen(parent);
                    }
                ).bounds(cx - 130, rowY, 260, 20).build());
            }
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.назад_f6dab074"), b -> minecraft.setScreen(parent)
        ).bounds(cx - 55, this.height - 28, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("wavedefense.shop.export_pick_point"), this.width / 2, 26, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
