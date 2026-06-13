package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.ExportShopPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/** Вибір точки магазину для експорту. */
public class ShopPointSelectExportScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private final Location location;
    private final Screen parent;

    public ShopPointSelectExportScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.auto.експорт_точки_магазину_e19a3bbd"));
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
            this.addButton(new Button(cx - 100, y, 200, 20, new TranslationTextComponent("wavedefense.auto.точок_немає_6ee9d45d"), b -> {})).active = false;
        } else {
            for (int pi = 0; pi < points.size(); pi++) {
                ShopPoint sp = points.get(pi);
                final String pName = sp.getName();
                final int rowY = y + pi * 26;
                this.addButton(new Button(cx - 130, rowY, 260, 20, new StringTextComponent("§e⬆ " + pName + " §7(" + sp.getItems().size() + " " + net.minecraft.client.resources.I18n.get("wavedefense.item.count_suffix") + ")"), b -> {
                        PacketHandler.sendToServer(new ExportShopPacket(location.getName(), "point:" + pName));
                        if (minecraft.player != null)
                            minecraft.player.displayClientMessage(
                                new TranslationTextComponent("wavedefense.auto.збережено_точку_value_b9ad0158", pName + "»"), true);
                        minecraft.setScreen(parent);
                    }));
            }
        }

        this.addButton(new Button(cx - 55, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.auto.назад_f6dab074"), b -> minecraft.setScreen(parent)));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 14, GuiTheme.TEXT);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, new TranslationTextComponent("wavedefense.shop.export_pick_point"), this.width / 2, 26, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
