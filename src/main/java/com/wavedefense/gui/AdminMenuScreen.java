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
    private String pendingInputValue = ""; // зберігаємо значення між rebuildWidgets
    private String errorMessage = "";
    private String pendingDeleteName = null; // ім'я локації очікує підтвердження видалення
    private int scrollOffset = 0;
    private int itemsPerPage = 4; // Динамічне число рядків — перераховується в init()
    private boolean firstOpen = true;

    public AdminMenuScreen() {
        super(Component.translatable("wavedefense.title.admin_menu"));
    }

    @Override
    protected void init() {
        super.init();
        // Зберігаємо поточне значення поля вводу перед очищенням (rebuildWidgets руйнує widgets)
        if (locationNameInput != null) {
            pendingInputValue = locationNameInput.getValue();
        }
        // Запит даних з сервера тільки при першому відкритті
        if (firstOpen) {
            PacketHandler.sendToServer(new RequestLocationDataPacket());
            firstOpen = false;
        }
        this.locationNames = ClientLocationManager.getAllLocationNames();

        // Адаптивні розміри
        itemsPerPage = Math.max(4, (this.height - 130) / 25);
        int centerX = this.width / 2;
        int panelW  = Math.min(300, this.width - 60);
        int startY  = 50;

        // Відновлюємо EditBox зі збереженим значенням (щоб не губити введений текст)
        locationNameInput = new EditBox(this.font, centerX - 100, startY, 200, 20, Component.literal("Назва локації"));
        locationNameInput.setMaxLength(32);
        locationNameInput.setHint(Component.literal("§8a-z 0-9 _ -"));
        // Дозволяємо лише латинські літери, цифри, підкреслення та дефіс
        locationNameInput.setFilter(s -> s.matches("[a-zA-Z0-9_\\-]*"));
        locationNameInput.setValue(pendingInputValue); // відновлюємо текст
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

            boolean isPendingDel = name.equals(pendingDeleteName);
            this.addRenderableWidget(Button.builder(
                    Component.literal(isPendingDel ? "§c§l✓ ТАК" : "§c✕"),
                    button -> {
                        if (isPendingDel) {
                            deleteLocation(finalName);
                            pendingDeleteName = null;
                        } else {
                            pendingDeleteName = finalName;
                            rebuildWidgets();
                        }
                    }
            ).bounds(centerX + panelW / 2 - 35, yPos, 35, 20).build()
            ).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(isPendingDel ? "§cПідтвердити видалення §e" + name + "§c?" : "§cВидалити локацію " + name)));
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
                Component.literal("📤 Імпорт/Експорт"),
                button -> this.minecraft.setScreen(new ImportExportScreen(this))
        ).bounds(centerX - panelW / 2, this.height - 30, panelW / 2 - 4, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX + 4, this.height - 30, panelW / 2 - 4, 20).build());

        // Кнопка скасування підтвердження видалення (якщо активна)
        if (pendingDeleteName != null) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Скасувати видалення"),
                    button -> { pendingDeleteName = null; rebuildWidgets(); }
            ).bounds(centerX - 80, this.height - 55, 160, 18).build());
        }
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
        pendingInputValue = ""; // скидаємо збережене значення
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter у полі вводу = швидке створення локації
        if (keyCode == 257 && locationNameInput != null && locationNameInput.isFocused()) {
            createNewLocation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return super.charTyped(c, modifiers);
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
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);
        graphics.drawString(this.font, "§7Назва (лише латиниця/цифри/_-):", centerX - 100, 38, 0xFFFFFF);

        // Повідомлення про помилку
        if (!errorMessage.isEmpty()) {
            int errY = this.height - 50;
            int errW = this.font.width(errorMessage) + 14;
            graphics.fill(centerX - errW / 2, errY - 3, centerX + errW / 2, errY + this.font.lineHeight + 3, 0xDD000000);
            graphics.drawCenteredString(this.font, errorMessage, centerX, errY, 0xFFFFFF);
        }

        // Scissor: список локацій між header (115) та footer (height-34)
        int listTop = 115, listBot = this.height - 34;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        super.render(graphics, mouseX, mouseY, partialTick);
        ScissorHelper.disable();
        // Re-render статичні елементи поза scissored зоною
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() < listTop || w.getY() >= listBot)
                    w.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
