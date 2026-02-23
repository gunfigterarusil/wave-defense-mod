package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SurrenderPacket;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Екран дій гравця під час хвилі.
 * Відкривається замість звичайного інвентаря (клавіша E) коли гравець на локації.
 */
public class WaveActionsScreen extends Screen {

    private final Screen previousScreen; // для повернення

    public WaveActionsScreen() {
        super(Component.literal("Меню гравця"));
        this.previousScreen = null;
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;
        int startY = this.height / 2 - 60;
        int btnW = 200;
        int btnH = 24;
        int gap = 8;

        Player player = minecraft.player;
        if (player == null) return;

        PlayerWaveData playerData = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        Location location = playerData != null ? playerData.getCurrentLocation() : null;

        // Кнопка: Магазин
        boolean hasShop = location != null && !location.getShopItems().isEmpty();
        Button shopBtn = Button.builder(
                Component.literal("§6🛒 Відкрити магазин"),
                button -> {
                    if (location != null) {
                        minecraft.setScreen(new PlayerShopScreen(location));
                    }
                }
        ).bounds(cx - btnW / 2, startY, btnW, btnH).build();
        shopBtn.active = hasShop;
        this.addRenderableWidget(shopBtn);

        // Кнопка: Налаштування
        this.addRenderableWidget(Button.builder(
                Component.literal("⚙ Налаштування HUD"),
                button -> {
                    if (playerData != null) {
                        minecraft.setScreen(new PlayerSettingsScreen(playerData));
                    }
                }
        ).bounds(cx - btnW / 2, startY + btnH + gap, btnW, btnH).build());

        // Кнопка: Здатися
        this.addRenderableWidget(Button.builder(
                Component.literal("§c🏳 Здатися"),
                button -> {
                    PacketHandler.sendToServer(new SurrenderPacket());
                    this.onClose();
                }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 2, btnW, btnH).build());

        // Кнопка: Відкрити інвентар
        this.addRenderableWidget(Button.builder(
                Component.literal("§7📦 Інвентар"),
                button -> minecraft.setScreen(new InventoryScreen(player))
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 3, btnW, btnH).build());

        // Закрити
        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(cx - 50, startY + (btnH + gap) * 4 + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Напівпрозорий фон
        this.renderBackground(graphics);

        int cx = this.width / 2;
        int startY = this.height / 2 - 70;

        graphics.drawCenteredString(this.font, "§6§lWave Defense — Меню", cx, startY - 14, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
