package com.wavedefense.gui;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.CreateLocationPacket;
import com.wavedefense.network.packets.DeleteLocationPacket;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import com.wavedefense.network.packets.TeleportPacket;
import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AdminMenuScreen extends Screen {

    private List<String> locationNames;
    private EditBox locationNameInput;
    private String errorMessage = "";
    private int scrollOffset = 0;
    private int itemsPerPage = 4; // Динамічне число рядків — перераховується в init()

    public AdminMenuScreen() {
        super(Component.translatable("wavedefense.title.admin_menu"));
    }

    @Override
    protected void init() {
        super.init();
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        this.locationNames = ClientLocationManager.getAllLocationNames();

        // Адаптивні розміри
        itemsPerPage = Math.max(4, (this.height - 130) / 25);
        int centerX = this.width / 2;
        int panelW  = Math.min(300, this.width - 60);
        int startY  = 50;

        // EditBox збережений між rebuildWidgets через поле
        locationNameInput = new EditBox(this.font, centerX - 100, startY, 200, 20, Component.literal("Location name"));
        locationNameInput.setMaxLength(32);
        // Дозволяємо лише латинські літери, цифри, підкреслення та дефіс
        locationNameInput.setFilter(s -> s.matches("[a-zA-Z0-9_\\-]*"));
        this.addRenderableWidget(locationNameInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Створити нову локацію"),
                button -> createNewLocation()
        ).bounds(centerX - panelW / 2, startY + 28, panelW, 20).build());

        int listStartY = startY + 65;
        for (int i = 0; i < Math.min(itemsPerPage, locationNames.size()); i++) {
            int index = i + scrollOffset;
            if (index >= locationNames.size()) break;

            String name = locationNames.get(index);
            int yPos = listStartY + (i * 25);

            final String finalName = name;
            final int finalIdx = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal(name),
                    button -> selectLocation(finalName)
            ).bounds(centerX - panelW / 2, yPos, panelW - 80, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"),
                    button -> editLocation(finalName)
            ).bounds(centerX + panelW / 2 - 75, yPos, 35, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> deleteLocation(finalName)
            ).bounds(centerX + panelW / 2 - 35, yPos, 35, 20).build());
        }

        if (locationNames.size() > itemsPerPage) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 105, listStartY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 105, listStartY + (itemsPerPage - 1) * 25, 20, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void createNewLocation() {
        String name = locationNameInput.getValue().trim();
        if (name.isEmpty()) {
            errorMessage = "§cНазва не може бути порожньою!";
            return;
        }
        if (ClientLocationManager.getLocation(name) != null) {
            errorMessage = "§cЛокація з такою назвою вже існує!";
            return;
        }
        errorMessage = "";
        PacketHandler.sendToServer(new CreateLocationPacket(name));
        locationNameInput.setValue("");
        // Запит даних і оновлення через невелику затримку
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        // Оновлення локального кешу та екрану
        net.minecraft.client.Minecraft.getInstance().tell(() -> {
            this.locationNames = ClientLocationManager.getAllLocationNames();
            this.rebuildWidgets();
        });
    }

    private void selectLocation(String name) {
        Location location = ClientLocationManager.getLocation(name);
        if (location != null) {
            editLocation(name);
        }
    }

    private void editLocation(String name) {
        Location location = ClientLocationManager.getLocation(name);
        if (location != null) {
            this.minecraft.setScreen(new LocationEditorScreen(location, this));
        }
    }

    private void deleteLocation(String name) {
        PacketHandler.sendToServer(new DeleteLocationPacket(name));
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        // Оновлення після видалення
        if (scrollOffset > 0 && scrollOffset >= locationNames.size() - 1) {
            scrollOffset = Math.max(0, locationNames.size() - 2);
        }
        net.minecraft.client.Minecraft.getInstance().tell(() -> {
            this.locationNames = ClientLocationManager.getAllLocationNames();
            this.rebuildWidgets();
        });
    }

    private void enterLocation(String name) {
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
        if (scrollOffset + itemsPerPage < locationNames.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scrollUp();
        else scrollDown();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        int centerX = this.width / 2;
        // Підпис до поля вводу
        graphics.drawString(this.font, "§7Назва (лише латиниця/цифри/_-):", centerX - 100, 38, 0xFFFFFF);

        // Повідомлення про помилку — внизу екрану щоб не накладатись на список
        if (!errorMessage.isEmpty()) {
            int errY = this.height - 50;
            int errW = this.font.width(errorMessage) + 14;
            graphics.fill(centerX - errW / 2, errY - 3, centerX + errW / 2, errY + this.font.lineHeight + 3, 0xDD000000);
            graphics.drawCenteredString(this.font, errorMessage, centerX, errY, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
