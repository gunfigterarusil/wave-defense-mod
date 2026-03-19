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
        super(Component.literal(
            switch (location.getPvpMode()) {
                case BATTLE_ROYALE -> "§c🏆 Королівська Битва — " + location.getName();
                case DEATHMATCH    -> "§e⚡ Deathmatch — " + location.getName();
                default            -> "§c⚔ PvP — " + location.getName() + " §7| Обери команду";
            }));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 45;

        com.wavedefense.data.Location.PvpMode mode = location.getPvpMode();

        // ── Battle Royale: гравець не обирає — одна кнопка "Увійти" ────
        if (mode == com.wavedefense.data.Location.PvpMode.BATTLE_ROYALE) {
            this.addRenderableWidget(Button.builder(
                Component.literal("§c§l🏆 КОРОЛІВСЬКА БИТВА"), b -> {}
            ).bounds(cx - 150, y, 300, 18).build()).active = false;
            y += 24;
            this.addRenderableWidget(Button.builder(
                Component.literal("§8Вас телепортує на випадкову позицію"), b -> {}
            ).bounds(cx - 150, y, 300, 14).build()).active = false;
            y += 20;
            this.addRenderableWidget(Button.builder(
                Component.literal("§a▶ Увійти в гру"),
                b -> joinTeam(0) // сервер сам призначить випадкову точку
            ).bounds(cx - 80, y, 160, 24).build());

        } else if (mode == com.wavedefense.data.Location.PvpMode.DEATHMATCH) {
            // ── Deathmatch: вибір команди є, але показуємо режим ──────────
            this.addRenderableWidget(Button.builder(
                Component.literal("§e§l⚡ DEATHMATCH"), b -> {}
            ).bounds(cx - 150, y, 300, 18).build()).active = false;
            y += 24;
            this.addRenderableWidget(Button.builder(
                Component.literal("§8Перемагає команда що першою набере §e"
                    + location.getDmKillsToWin() + "§8 вбивств"), b -> {}
            ).bounds(cx - 150, y, 300, 14).build()).active = false;
            y += 20;
            renderTeamButtons(cx, y);

        } else {
            // ── Standard: звичайний вибір команди ─────────────────────────
            this.addRenderableWidget(Button.builder(
                Component.literal("§7Оберіть команду:"), button -> {}
            ).bounds(cx - 150, y, 300, 16).build()).active = false;
            y += 22;
            renderTeamButtons(cx, y);
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 50, this.height - 30, 100, 20).build());
    }

    private void renderTeamButtons(int cx, int y) {
        List<PvpSpawnPoint> spawns = location.getPvpSpawnPoints();
        if (spawns.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§cЖодних команд не налаштовано!"), b -> {}
            ).bounds(cx - 150, y, 300, 20).build()).active = false;
        } else {
            for (int i = 0; i < spawns.size(); i++) {
                PvpSpawnPoint sp = spawns.get(i);
                final int idx = i;
                // Колір кнопки залежить від назви команди
                String color = switch (sp.getTeamName().toLowerCase()) {
                    case "red",   "червоні", "червона" -> "§c";
                    case "blue",  "сині",    "синя"    -> "§9";
                    case "green", "зелені",  "зелена"  -> "§a";
                    case "yellow","жовті",   "жовта"   -> "§e";
                    default -> "§f";
                };
                this.addRenderableWidget(Button.builder(
                        Component.literal(color + "⚑ " + sp.getTeamName()),
                        b -> joinTeam(idx)
                ).bounds(cx - 120, y + i * 28, 240, 22).build());
            }
        }
    }

    private void joinTeam(int spawnIndex) {
        PacketHandler.sendToServer(new TeleportPacket(location.getName(), spawnIndex));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int titleColor = switch (location.getPvpMode()) {
            case BATTLE_ROYALE -> 0xFF4444;
            case DEATHMATCH    -> 0xFFAA00;
            default            -> 0xFF5555;
        };
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, titleColor);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
