package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.TeleportPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Екран вибору команди (точки спавну) для PvP локації.
 * Гравець вибирає за якою точкою спавниться.
 */
public class PvpTeamSelectScreen extends Screen {

    private final Location location;
    private final Screen parent;

    public PvpTeamSelectScreen(Location location, Screen parent) {
        super(Component.literal("§c⚔ PvP — " + location.getName() + " §7| Обери команду"));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 45;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Оберіть точку спавну (команду):"), button -> {}
        ).bounds(cx - 150, y, 300, 16).build()).active = false;
        y += 22;

        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();

        if (spawns.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§cЖодних команд не налаштовано!"), button -> {}
            ).bounds(cx - 150, y, 300, 20).build()).active = false;
        } else {
            for (int i = 0; i < spawns.size(); i++) {
                PvpSpawnPoint sp = spawns.get(i);
                final int idx = i;
                this.addRenderableWidget(Button.builder(
                        Component.literal("⚑ " + sp.getTeamName()),
                        button -> joinTeam(idx)
                ).bounds(cx - 120, y + i * 28, 240, 22).build());
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 30, 100, 20).build());
    }

    private void joinTeam(int spawnIndex) {
        PacketHandler.sendToServer(new TeleportPacket(location.getName(), spawnIndex));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFF5555);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
