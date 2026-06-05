package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.CreateLocationPacket;
import com.wavedefense.network.packets.DeleteLocationPacket;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import com.wavedefense.data.Location;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class AdminMenuScreen extends ListEditorScreen<String> {

    private List<String> locationNames;
    private TextFieldWidget locationNameInput;
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
        super(new TranslationTextComponent("wavedefense.title.admin_menu"));
    }

    // ─── ListEditorScreen / ScrollableScreen API ───────────────────────────

    @Override protected List<String> getItems()    { return locationNames != null ? locationNames : java.util.Collections.emptyList(); }
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
        locationNameInput = new TextFieldWidget(this.font, cx - 100, startY, 200, 20,
            new TranslationTextComponent("wavedefense.label.location_name"));
        locationNameInput.setMaxLength(32);
        /* locationNameInput.setHint(...) omitted on 1.16.5 */
        locationNameInput.setFilter(s -> s.matches("[a-zA-Z0-9_\\-]*"));
        locationNameInput.setValue(pendingInputValue);
        addStatic(locationNameInput);

        addStatic(new Button(cx - panelW / 2, startY + 28, panelW, 20, new TranslationTextComponent("wavedefense.label.new_location"), button -> createNewLocation()));

        // ── Content (scrollable) ──────────────────────────────────────
        buildVisibleRows();

        int scrollX = cx + 105;
        addScrollButtons(scrollX, LIST_START_Y, LIST_START_Y + (Math.max(1, getItemsPerPage()) - 1) * ROW_H, 20, 20);

        // ── Footer (static) ───────────────────────────────────────────
        // Коли є очікуване видалення — показуємо Cancel на весь footer (замість Import/Close)
        if (pendingDeleteName != null) {
            addStatic(new Button(cx - panelW / 2, this.height - 30, panelW, 20, new TranslationTextComponent("wavedefense.button.cancel_delete"), button -> { pendingDeleteName = null; init(); }));
        } else {
            addStatic(new Button(cx - panelW / 2, this.height - 30, panelW / 2 - 4, 20, new TranslationTextComponent("wavedefense.button.import_export"), button -> this.minecraft.setScreen(new ImportExportScreen(this))));

            addStatic(new Button(cx + 4, this.height - 30, panelW / 2 - 4, 20, new TranslationTextComponent("wavedefense.button.close"), button -> this.onClose()));
        }
    }

    // ─── Row builder ───────────────────────────────────────────────────────

    @Override
    protected void buildRowWidgets(int cx, int y, String name, int index) {
        final String finalName = name;

        // Name button shrunk by 28px to make room for the ⎘ duplicate button.
        this.addButton(new Button(cx - panelW / 2, y, panelW - 138, 20, new StringTextComponent(name), button -> selectLocation(finalName)));

        // ⎘ Duplicate location (v0.2.63) — clones via NBT roundtrip with auto-numbered name
        this.addButton(new Button(cx + panelW / 2 - 133, y, 26, 20, new StringTextComponent("§b⎘"), button -> duplicateLocation(finalName)))
        /* setTooltip omitted on 1.16.5 */;

        // ✎ Default editor → new UniversalLocationEditor (Sprint 2, v0.2.58+)
        this.addButton(new Button(cx + panelW / 2 - 105, y, 28, 20, new StringTextComponent("§a✎"), button -> editLocation(finalName)))
        /* setTooltip omitted on 1.16.5 */;

        // 📜 Legacy editor — kept as fallback, to be removed in next major version
        this.addButton(new Button(cx + panelW / 2 - 75, y, 35, 20, new StringTextComponent("§e📜"), button -> editLocationLegacy(finalName)))
        /* setTooltip omitted on 1.16.5 */;

        boolean isPendingDel = name.equals(pendingDeleteName);
        this.addButton(new Button(cx + panelW / 2 - 35, y, 35, 20, isPendingDel
                    ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                    : new StringTextComponent("§c✕"), button -> {
                    if (isPendingDel) {
                        deleteLocation(finalName);
                        pendingDeleteName = null;
                    } else {
                        pendingDeleteName = finalName;
                        init();
                    }
                })
        )/* setTooltip omitted on 1.16.5 */;
    }

    // ─── Render hooks ──────────────────────────────────────────────────────

    @Override
    protected void renderHeader(MatrixStack g, int mx, int my, float pt) {
        int cx = this.width / 2;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, cx, 15, 0xFFFFFF);
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, I18n.get("wavedefense.label.name_hint"), cx - 100, 38, 0xFFFFFF);
    }

    @Override
    protected void renderOverlay(MatrixStack g, int mx, int my, float pt) {
        if (!errorMessage.isEmpty()) {
            int cx  = this.width / 2;
            int errY = this.height - 50;
            int errW = this.font.width(errorMessage) + 14;
            com.wavedefense.gui.GuiCompat.fill(g, cx - errW / 2, errY - 3, cx + errW / 2, errY + this.font.lineHeight + 3, 0xDD000000);
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, errorMessage, cx, errY, 0xFFFFFF);
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
            this.init();
        });
    }

    private void selectLocation(String name) {
        Location location = ClientLocationManager.getLocation(name);
        if (location != null) {
            editLocation(name);
        }
    }

    /** Default editor — Sprint 2 unified editor (PvE/PvP unified, 6 tabs). */
    private void editLocation(String name) {
        Location location = ClientLocationManager.getLocation(name);
        if (location != null) {
            this.minecraft.setScreen(
                new com.wavedefense.gui.universal.UniversalLocationEditor(location, this));
        }
    }

    /** Legacy fallback — routes to the old PvE / PvP editor depending on mode.
     *  Marked for removal in the next major release. */
    @SuppressWarnings("deprecation")
    private void editLocationLegacy(String name) {
        Location location = ClientLocationManager.getLocation(name);
        if (location == null) return;
        if (location.getMode() == com.wavedefense.data.LocationMode.PVP) {
            /* legacy editor not ported on 1.16.5 */ /* this.minecraft.setScreen(new PvpLocationEditorScreen(location, this)); */
        } else {
            /* legacy editor not ported on 1.16.5 */ /* this.minecraft.setScreen(new LocationEditorScreen(location, this)); */
        }
    }

    /** v0.2.63: client-side handler for ⎘ button. Picks a non-colliding name
     *  ({@code name_copy}, {@code name_copy2}, …) and sends DuplicateLocationPacket.
     *  Server side validates again before applying. */
    private void duplicateLocation(String sourceName) {
        // Find the first non-colliding "_copy<N>" suffix
        String targetBase = sourceName + "_copy";
        String target = targetBase;
        int n = 2;
        while (ClientLocationManager.getLocation(target) != null && n < 100) {
            target = targetBase + n;
            n++;
        }
        if (ClientLocationManager.getLocation(target) != null) {
            errorMessage = I18n.get("wavedefense.msg.duplicate_too_many");
            return;
        }
        errorMessage = "";
        PacketHandler.sendToServer(
            new com.wavedefense.network.packets.DuplicateLocationPacket(sourceName, target));
        PacketHandler.sendToServer(new com.wavedefense.network.packets.RequestLocationDataPacket());
        net.minecraft.client.Minecraft.getInstance().tell(() -> {
            this.locationNames = ClientLocationManager.getAllLocationNames();
            this.init();
        });
    }

    private void deleteLocation(String name) {
        pendingDeleteName = null;
        PacketHandler.sendToServer(new DeleteLocationPacket(name));
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        // After deletion the list shrinks by 1; clamp scrollOffset so the last item stays visible.
        if (scrollOffset > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        net.minecraft.client.Minecraft.getInstance().tell(() -> {
            this.locationNames = ClientLocationManager.getAllLocationNames();
            this.init();
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
