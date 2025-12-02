package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

public class AdminMenuScreen extends Screen {

    private List<String> locationNames;
    private int selectedLocationIndex = -1;
    private EditBox locationNameInput;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;

    public AdminMenuScreen() {
        super(Component.literal("Wave Defense - Управління"));
    }

    @Override
    protected void init() {
        super.init();

        this.locationNames = WaveDefenseMod.locationManager.getAllLocations().stream()
                .map(Location::getName)
                .collect(Collectors.toList());

        int centerX = this.width / 2;
        int startY = 40;

        locationNameInput = new EditBox(this.font, centerX - 100, startY, 200, 20, Component.literal("Назва локації"));
        locationNameInput.setMaxLength(32);
        this.addRenderableWidget(locationNameInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Створити нову локацію"),
                button -> createNewLocation()
        ).bounds(centerX - 100, startY + 30, 200, 20).build());

        int listStartY = startY + 60;
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, locationNames.size()); i++) {
            int index = i + scrollOffset;
            if (index >= locationNames.size()) break;

            String name = locationNames.get(index);
            int yPos = listStartY + (i * 25);

            final int finalIndex = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal(name),
                    button -> selectLocation(finalIndex)
            ).bounds(centerX - 100, yPos, 120, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"),
                    button -> editLocation(name)
            ).bounds(centerX + 25, yPos, 35, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> deleteLocation(name)
            ).bounds(centerX + 65, yPos, 35, 20).build());
        }

        if (locationNames.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 105, listStartY, 20, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 105, listStartY + 180, 20, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void createNewLocation() {
        String name = locationNameInput.getValue().trim();
        if (name.isEmpty() || WaveDefenseMod.locationManager.locationExists(name)) {
            return;
        }
        WaveDefenseMod.locationManager.createLocation(name);
        locationNameInput.setValue("");
        this.rebuildWidgets();
    }

    private void selectLocation(int index) {
        selectedLocationIndex = index;
    }

    private void editLocation(String name) {
        Location location = WaveDefenseMod.locationManager.getLocation(name);
        if (location != null) {
            this.minecraft.setScreen(new LocationEditorScreen(location, this));
        }
    }

    private void deleteLocation(String name) {
        WaveDefenseMod.locationManager.removeLocation(name);
        this.rebuildWidgets();
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
