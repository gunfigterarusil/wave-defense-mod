package com.wavedefense.gui;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.CreateLocationPacket;
import com.wavedefense.network.packets.DeleteLocationPacket;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AdminMenuScreen extends ListEditorScreen<String> {

    private List<String> locationNames;
    private EditBox locationNameInput;
    private String pendingInputValue = "";
    private String errorMessage = "";
    private String pendingDeleteName = null;
    private boolean firstOpen = true;

    // Layout constants
    private static final int LIST_START_Y = 115; // = getClipTop()
    private static final int ROW_H        = 25;

    // panelW depends on screen width; computed in init()
    private int panelW;

    public AdminMenuScreen() {
        super(Component.translatable("wavedefense.title.admin_menu"));
    }

    // ─── ListEditorScreen / ScrollableScreen API ───────────────────────────

    @Override protected List<String> getItems()    { return locationNames != null ? locationNames : List.of(); }
    @Override protected int getRowHeight()         { return ROW_H; }
    @Override protected int getStartY()            { return LIST_START_Y; }
    @Override protected int getClipTop()           { return LIST_START_Y; }
    @Override protected int getClipBot()           { return this.height - 34; }
    @Override protected int getItemsPerPage()      { return Math.max(4, (this.height - 130) / ROW_H); }

    // ─── init() ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Preserve text-field value across rebuilds
        if (locationNameInput != null) {
            pendingInputValue = locationNameInput.getValue();
        }
        if (firstOpen) {
            PacketHandler.sendToServer(new RequestLocationDataPacket());
            firstOpen = false;
        }
        this.locationNames = ClientLocationManager.getAllLocationNames();

        super.init(); // clears staticWidgets, clamps scroll

        int cx   = this.width / 2;
        panelW   = Math.min(300, this.width - 60);
        int startY = 50;

        // ── Header (static) ──────────────────────────────────────────
        locationNameInput = new EditBox(this.font, cx - 100, startY, 200, 20,
            Component.translatable("wavedefense.label.location_name"));
        locationNameInput.setMaxLength(32);
        locationNameInput.setHint(Component.translatable("wavedefense.auto.a_z_0_9_aa25a6d8"));
        locationNameInput.setFilter(s -> s.matches("[a-zA-Z0-9_\\-]*"));
        locationNameInput.setValue(pendingInputValue);
        addStatic(locationNameInput);

        addStatic(Button.builder(
                Component.translatable("wavedefense.label.new_location"),
                button -> createNewLocation()
        ).bounds(cx - panelW / 2, startY + 28, panelW, 20).build());

        // ── Content (scrollable) ──────────────────────────────────────
        buildVisibleRows();

        int scrollX = cx + 105;
        addScrollButtons(scrollX, LIST_START_Y, LIST_START_Y + (Math.max(1, getItemsPerPage()) - 1) * ROW_H, 20, 20);

        // Cancel-delete button (in scrollable zone)
        if (pendingDeleteName != null) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.cancel_delete"),
                    button -> { pendingDeleteName = null; rebuildWidgets(); }
            ).bounds(cx - 80, this.height - 55, 160, 18).build());
        }

        // ── Footer (static) ───────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.import_export"),
                button -> this.minecraft.setScreen(new ImportExportScreen(this))
        ).bounds(cx - panelW / 2, this.height - 30, panelW / 2 - 4, 20).build());

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.close"),
                button -> this.onClose()
        ).bounds(cx + 4, this.height - 30, panelW / 2 - 4, 20).build());
    }

    // ─── Row builder ───────────────────────────────────────────────────────

    @Override
    protected void buildRowWidgets(int cx, int y, String name, int index) {
        final String finalName = name;

        this.addRenderableWidget(Button.builder(
                Component.literal(name),
                button -> selectLocation(finalName)
        ).bounds(cx - panelW / 2, y, panelW - 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("✎"),
                button -> editLocation(finalName)
        ).bounds(cx + panelW / 2 - 75, y, 35, 20).build());

        boolean isPendingDel = name.equals(pendingDeleteName);
        this.addRenderableWidget(Button.builder(
                isPendingDel
                    ? Component.translatable("wavedefense.button.confirm_delete")
                    : Component.literal("§c✕"),
                button -> {
                    if (isPendingDel) {
                        deleteLocation(finalName);
                        pendingDeleteName = null;
                    } else {
                        pendingDeleteName = finalName;
                        rebuildWidgets();
                    }
                }
        ).bounds(cx + panelW / 2 - 35, y, 35, 20).build()
        ).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal(isPendingDel
                ? I18n.get("wavedefense.tooltip.confirm_delete", name)
                : I18n.get("wavedefense.tooltip.delete_location", name))));
    }

    // ─── Render hooks ──────────────────────────────────────────────────────

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 15, 0xFFFFFF);
        g.drawString(this.font, I18n.get("wavedefense.label.name_hint"), cx - 100, 38, 0xFFFFFF);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mx, int my, float pt) {
        if (!errorMessage.isEmpty()) {
            int cx  = this.width / 2;
            int errY = this.height - 50;
            int errW = this.font.width(errorMessage) + 14;
            g.fill(cx - errW / 2, errY - 3, cx + errW / 2, errY + this.font.lineHeight + 3, 0xDD000000);
            g.drawCenteredString(this.font, errorMessage, cx, errY, 0xFFFFFF);
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────

    private void createNewLocation() {
        String name = locationNameInput.getValue().trim();
        if (name.isEmpty()) {
            errorMessage = I18n.get("wavedefense.msg.name_empty");
            return;
        }
        if (ClientLocationManager.getLocation(name) != null) {
            errorMessage = I18n.get("wavedefense.msg.name_exists");
            return;
        }
        errorMessage = "";
        PacketHandler.sendToServer(new CreateLocationPacket(name));
        locationNameInput.setValue("");
        pendingInputValue = "";
        PacketHandler.sendToServer(new RequestLocationDataPacket());
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
        pendingDeleteName = null;
        PacketHandler.sendToServer(new DeleteLocationPacket(name));
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        if (scrollOffset > 0 && scrollOffset >= locationNames.size() - 1) {
            scrollOffset = Math.max(0, locationNames.size() - 2);
        }
        net.minecraft.client.Minecraft.getInstance().tell(() -> {
            this.locationNames = ClientLocationManager.getAllLocationNames();
            this.rebuildWidgets();
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
}
