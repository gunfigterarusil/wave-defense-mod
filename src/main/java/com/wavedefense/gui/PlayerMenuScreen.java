package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import com.wavedefense.network.packets.TeleportPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerMenuScreen extends ListEditorScreen<String> {

    private List<String> locationNames;
    private int btnW;

    public PlayerMenuScreen() {
        super(Component.translatable("wavedefense.title.player_menu"));
    }

    // ─── ListEditorScreen / ScrollableScreen API ───────────────────────────

    @Override protected List<String> getItems()   { return locationNames != null ? locationNames : List.of(); }
    @Override protected int getRowHeight()        { return this.height < 200 ? 20 : 25; }
    @Override protected int getStartY()           { return 40; }
    @Override protected int getClipTop()          { return 36; }
    @Override protected int getClipBot()          { return this.height - 32; }
    @Override protected int getItemsPerPage()     { return Math.max(4, (this.height - 40 - 50) / getRowHeight()); }

    // ─── init() ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        this.locationNames = ClientLocationManager.getAllLocationNames().stream()
            .filter(name -> {
                Location loc = ClientLocationManager.getLocation(name);
                return loc == null || !loc.isHiddenFromPlayers();
            })
            .collect(Collectors.toList());

        super.init();

        int cx  = this.width / 2;
        btnW    = Math.min(260, this.width - 80);

        // ── Content (scrollable) ──────────────────────────────────────
        buildVisibleRows();

        int sbX = cx + btnW / 2 + 4;
        addScrollButtons(sbX, getStartY(),
                getStartY() + (Math.max(1, getItemsPerPage()) - 1) * getRowHeight(), 20, 20);

        // ── Footer (static) ───────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.close"), button -> this.onClose()
        ).bounds(cx - 55, this.height - 28, 110, 20).build());
    }

    // ─── Row builder ───────────────────────────────────────────────────────

    @Override
    protected void buildRowWidgets(int cx, int y, String name, int index) {
        Location loc    = ClientLocationManager.getLocation(name);
        boolean isPvp   = loc != null && loc.isPvp();
        String badge    = isPvp ? "§c[PvP] §f" : "§a[PvE] §f";
        boolean pvpReady = !isPvp || (loc != null && loc.getPvpSpawnPoints().size() >= 2);

        String label = badge + name + (isPvp && !pvpReady
                ? " §c(" + I18n.get("wavedefense.label.pvp_not_ready") + ")" : "");

        Button btn = Button.builder(
                Component.literal(label),
                button -> handleLocationClick(name, isPvp, loc)
        ).bounds(cx - btnW / 2, y, btnW, getRowHeight() - 2).build();
        btn.active = pvpReady;
        this.addRenderableWidget(btn);
    }

    // ─── Actions ───────────────────────────────────────────────────────────

    private void handleLocationClick(String name, boolean isPvp, Location loc) {
        if (isPvp && loc != null && loc.getPvpSpawnPoints().size() >= 2) {
            this.minecraft.setScreen(new PvpTeamSelectScreen(loc, this));
        } else {
            PacketHandler.sendToServer(new TeleportPacket(name));
            this.onClose();
        }
    }
}
