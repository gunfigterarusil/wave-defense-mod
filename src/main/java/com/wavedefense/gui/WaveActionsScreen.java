package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SurrenderPacket;
import com.wavedefense.network.packets.ExitPvpPacket;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import com.wavedefense.gui.TooltipHelper;

/**
 * Головне ігрове меню (клавіша E на локації).
 * PvP: Магазин | Статистика | Налаштування | Здатися
 * PvE: Магазин | Налаштування | Здатися | Інвентар
 */
public class WaveActionsScreen extends Screen {

    private String pvpStatusLine = "";

    public WaveActionsScreen() {
        super(Component.translatable("wavedefense.title.wave_actions"));
    }

    @Override
    protected void init() {
        super.init();

        // Адаптивна ширина кнопок під розширення монітора
        int cx   = this.width / 2;
        int btnW = Math.min(260, Math.max(160, this.width / 2));
        int btnH = this.height < 200 ? 20 : 24;
        int gap  = this.height < 200 ? 5 : 8;

        Player player = minecraft.player;
        if (player == null) return;

        // ── Спектатор у PvP — кнопки "Вийти з PvP" і "Здатися" ────────────────
        if (player.isSpectator()) {
            int startY = this.height / 2 - 40;
            g_spectatorLabel = ClientPvpStateManager.getPhase().equals("WAITING")
                    ? "§7Очікуємо гравців..."
                    : "§7Ви загинули — чекаємо наступного раунду";
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c🏳 Здатися (з пенальті)"),
                    b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
            ).bounds(cx - btnW / 2, startY, btnW, btnH).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("Закрити меню"),
                    b -> this.onClose()
            ).bounds(cx - 55, startY + btnH + gap + 4, 110, 20).build());
            return;
        }

        PlayerWaveData pd = com.wavedefense.gui.ClientPlayerDataManager.getPlayerData();
        Location location  = pd != null ? pd.getCurrentLocation() : null;
        boolean isPvp      = location != null && location.isPvp();

        if (isPvp) {
            renderPvpMenu(cx, btnW, btnH, gap, pd, location);
        } else {
            renderPveMenu(cx, btnW, btnH, gap, pd, location, player);
        }
    }

    private String g_spectatorLabel = "";

    private void renderPvpMenu(int cx, int btnW, int btnH, int gap,
                               PlayerWaveData pd, Location location) {
        String phase    = ClientPvpStateManager.getPhase();
        boolean isBuy   = "BUY".equals(phase);
        int timerSec    = ClientPvpStateManager.getTimerSeconds();
        int round       = ClientPvpStateManager.getCurrentRound();
        int total       = ClientPvpStateManager.getTotalRounds();

        if (isBuy)
            pvpStatusLine = String.format("§e⏱ Час покупок: §a%d сек | §7Раунд %d/%d", timerSec, round, total);
        else if ("ACTIVE".equals(phase))
            pvpStatusLine = String.format("§c⚔ Раунд %d/%d активний!", round, total);
        else if ("WAITING".equals(phase))
            pvpStatusLine = "§7Чекаємо гравців...";
        else
            pvpStatusLine = "§6Матч завершено";

        int startY = this.height / 2 - 70;
        int i = 0;

        // Магазин (PvP)
        boolean hasShop = location.isPointShopMode()
            ? !location.getShopPoints().isEmpty()
            : !location.getShopItems().isEmpty();
        Button shopBtn = Button.builder(
            Component.literal("§6🛒 Магазин"),
            b -> openShopForLocation(location)
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        shopBtn.active = hasShop;
        this.addRenderableWidget(shopBtn);

        // Статистика
        this.addRenderableWidget(Button.builder(
            Component.literal("§b📊 Статистика команд"),
            b -> minecraft.setScreen(new PvpScoreboardScreen())
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build());

        // Налаштування
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙ Налаштування HUD"),
            b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build());

        // Вийти з PvP (без штрафу)
        this.addRenderableWidget(Button.builder(
            Component.literal("§e🚪 Вийти з PvP"),
            b -> { PacketHandler.sendToServer(new ExitPvpPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build());

        // Здатися (з пенальті)
        this.addRenderableWidget(Button.builder(
            Component.literal("§c🏳 Здатися"),
            b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build());

        // Закрити
        this.addRenderableWidget(Button.builder(
            Component.literal("Закрити"),
            b -> this.onClose()
        ).bounds(cx - 50, startY + (btnH + gap) * i + 4, 100, 20).build());
    }

    private void renderPveMenu(int cx, int btnW, int btnH, int gap,
                               PlayerWaveData pd, Location location, Player player) {
        int startY = this.height / 2 - 60;

        boolean hasShop = location != null && (location.isPointShopMode()
            ? !location.getShopPoints().isEmpty()
            : !location.getShopItems().isEmpty());
        Button shopBtn = Button.builder(
            Component.literal("§6🛒 Відкрити магазин"),
            b -> { if (location != null) openShopForLocation(location); }
        ).bounds(cx - btnW / 2, startY, btnW, btnH).build();
        shopBtn.active = hasShop;
        this.addRenderableWidget(shopBtn);

        this.addRenderableWidget(Button.builder(
            Component.literal("⚙ Налаштування HUD"),
            b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); }
        ).bounds(cx - btnW / 2, startY + btnH + gap, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("§c🏳 Здатися"),
            b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 2, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("§7📦 Інвентар"),
            b -> minecraft.setScreen(new InventoryScreen(player))
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 3, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Закрити"),
            b -> this.onClose()
        ).bounds(cx - 50, startY + (btnH + gap) * 4 + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx   = this.width / 2;
        int topY = this.height / 2 - 90;

        // Спектатор — простий оверлей
        if (minecraft.player != null && minecraft.player.isSpectator()) {
            g.drawCenteredString(this.font, "§c§l⚔ Wave Defense — PvP Меню", cx, topY + 8, 0xFF5555);
            g.drawCenteredString(this.font, g_spectatorLabel, cx, topY + 22, 0xAAAAAA);
            g.drawCenteredString(this.font, "§7Вийти — повернутись без штрафу | Здатися — з пенальті", cx, topY + 34, 0x888888);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        if (!pvpStatusLine.isEmpty()) {
            String loc = ClientPvpStateManager.getLocation();
            g.drawCenteredString(this.font, "§c§l⚔ PvP — §r§f" + loc, cx, topY, 0xFF5555);
            g.drawCenteredString(this.font, pvpStatusLine, cx, topY + 12, 0xFFFFFF);

            // Рахунок команд
            Map<String, Integer> wins = ClientPvpStateManager.getTeamWins();
            if (!wins.isEmpty()) {
                StringBuilder sb = new StringBuilder("§7Рахунок: ");
                wins.forEach((t, w) -> sb.append("§e").append(t).append("§7:§a").append(w).append("  "));
                g.drawCenteredString(this.font, sb.toString().trim(), cx, topY + 24, 0xFFFFFF);
            }
        } else {
            g.drawCenteredString(this.font, "§6§lWave Defense — Меню", cx, topY + 8, 0xFFFFFF);
        }

        // Tooltips при наведенні на кнопки
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn) {
                if (btn.isHoveredOrFocused() && btn.active) {
                    String tip = getButtonTooltip(btn.getMessage().getString());
                    if (tip != null) {
                        com.wavedefense.gui.TooltipHelper.renderIfEnabled(g, this.font, tip, mouseX, mouseY);
                    }
                }
            }
        });

        super.render(g, mouseX, mouseY, partialTick);
    }

    private String getButtonTooltip(String label) {
        if (label.contains("Магазин"))    return TooltipHelper.SHOP_OPEN;
        if (label.contains("Статистик"))  return TooltipHelper.STATS;
        if (label.contains("Здатися"))    return TooltipHelper.SURRENDER;
        if (label.contains("Налаштув"))   return TooltipHelper.HUD_SETTINGS;
        if (label.contains("Вийти з PvP")) return "§7Покинути PvP локацію без штрафних очків";
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /**
     * Відкриває магазин з урахуванням режиму (GLOBAL або POINT).
     * У точковому режимі перевіряє відстань до найближчої точки.
     */
    private void openShopForLocation(com.wavedefense.data.Location loc) {
        if (loc.isPointShopMode()) {
            if (minecraft.player == null) return;
            double px = minecraft.player.getX(), py = minecraft.player.getY(), pz = minecraft.player.getZ();
            com.wavedefense.data.ShopPoint sp = loc.findNearestShopPoint(px, py, pz);
            if (sp == null) {
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c🛒 Підійдіть до точки магазину щоб відкрити його."), true);
                this.onClose();
                return;
            }
            if (sp.getItems().isEmpty()) {
                minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c🛒 Магазин поруч порожній!"), true);
                return;
            }
            minecraft.setScreen(new PlayerShopScreen(loc, sp));
        } else {
            minecraft.setScreen(new PlayerShopScreen(loc));
        }
    }

}