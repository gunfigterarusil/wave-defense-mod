package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.gui.ScissorHelper;
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

    public PlayerMenuScreen() {
        super(Component.translatable("wavedefense.title.player_menu"));
    }


    @Override
    protected void init() {
        super.init();
        // Завжди запитуємо свіжі дані з сервера при кожному init()
        // (включаючи rebuildWidgets після отримання SyncLocationDataPacket)
        PacketHandler.sendToServer(new RequestLocationDataPacket());
        // Гравці бачать тільки не-приховані локації; адміни (запущені через adminMode) — всі
        this.locationNames = ClientLocationManager.getAllLocationNames().stream()
            .filter(name -> {
                com.wavedefense.data.Location loc = ClientLocationManager.getLocation(name);
                return loc == null || !loc.isHiddenFromPlayers();
            })
            .collect(java.util.stream.Collectors.toList());

        // Адаптивні розміри
        int cx          = this.width / 2;
        int btnW        = Math.min(260, this.width - 80);
        int rowH        = this.height < 200 ? 20 : 25;
        int startY      = 40;
        int itemsPerPage = Math.max(4, (this.height - startY - 50) / rowH);

        for (int i = 0; i < Math.min(itemsPerPage, locationNames.size()); i++) {
            int index = i + scrollOffset;
            if (index >= locationNames.size()) break;

            String name = locationNames.get(index);
            int yPos = startY + (i * rowH);

            Location loc = ClientLocationManager.getLocation(name);
            boolean isPvp   = loc != null && loc.isPvp();
            String badge    = isPvp ? "§c[PvP] §f" : "§a[PvE] §f";
            boolean pvpReady = !isPvp || (loc != null && loc.getPvpSpawnPoints().size() >= 2);

            Button btn = Button.builder(
                    Component.literal(badge + name + (isPvp && !pvpReady ? " §c(не готово)" : "")),
                    button -> handleLocationClick(name, isPvp, loc)
            ).bounds(cx - btnW / 2, yPos, btnW, rowH - 2).build();
            btn.active = pvpReady;
            this.addRenderableWidget(btn);
        }

        if (locationNames.size() > itemsPerPage) {
            int sbX = cx + btnW / 2 + 4;
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(sbX, startY, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    button -> { if (scrollOffset + itemsPerPage < locationNames.size()) {
                        scrollOffset++; rebuildWidgets(); }
                    }
            ).bounds(sbX, startY + (itemsPerPage - 1) * rowH, 20, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"), button -> this.onClose()
        ).bounds(cx - 55, this.height - 28, 110, 20).build());
    }

    private void handleLocationClick(String name, boolean isPvp, Location loc) {
        if (isPvp && loc != null && loc.getPvpSpawnPoints().size() >= 2) {
            // PvP: відкриваємо вибір команди
            this.minecraft.setScreen(new PvpTeamSelectScreen(loc, this));
        } else {
            // PvE: телепортуємо напряму
            PacketHandler.sendToServer(new TeleportPacket(name));
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        // Scissor: список локацій між заголовком (36) і кнопкою "Закрити" (height-32)
        int listTop = 36, listBot = this.height - 32;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                w.render(g, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < listTop || w.getY() >= listBot))
                w.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
