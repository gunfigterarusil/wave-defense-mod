package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import com.wavedefense.network.packets.TeleportPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PlayerMenuScreen extends Screen {

    private List<String> locationNames;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;

    public PlayerMenuScreen() {
        super(Component.literal("Wave Defense - Вибір локації"));
    }

    @Override
    protected void init() {
        super.init();
        PacketHandler.sendToServer(new RequestLocationDataPacket());

        this.locationNames = ClientLocationManager.getAllLocationNames();

        int centerX = this.width / 2;
        int startY = 40;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, locationNames.size()); i++) {
            int index = i + scrollOffset;
            if (index >= locationNames.size()) break;

            String name = locationNames.get(index);
            int yPos = startY + (i * 25);

            this.addRenderableWidget(Button.builder(
                    Component.literal(name),
                    button -> teleportToLocation(name)
            ).bounds(centerX - 100, yPos, 200, 20).build());
        }

        if (locationNames.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 105, startY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 105, startY + 180, 20, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void teleportToLocation(String name) {
        PacketHandler.sendToServer(new TeleportPacket(name));
        this.onClose();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < locationNames.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
