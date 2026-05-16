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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import com.wavedefense.gui.TooltipHelper;

/**
 * Головне ігрове меню (клавіша E на локації).
 * PvP: Магазин | Статистика | Налаштування | Здатися
 * PvE: Магазин | Налаштування | Здатися | Інвентар
 */
public class WaveActionsScreen extends Screen {

    // Не кешуємо рядки в полях — обчислюємо в render() через I18n.get(),
    // щоб вони оновлювались при зміні мови без перевідкриття екрану.
    private final Map<Button, String> buttonTooltips = new HashMap<>();

    public WaveActionsScreen() {
        super(Component.translatable("wavedefense.title.wave_actions"));
    }

    @Override
    protected void init() {
        super.init();
        buttonTooltips.clear();

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
            Button surrenderBtn = Button.builder(
                    Component.translatable("wavedefense.button.surrender_penalty"),
                    b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
            ).bounds(cx - btnW / 2, startY, btnW, btnH).build();
            this.addRenderableWidget(surrenderBtn);
            buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.close_menu"),
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

    private void renderPvpMenu(int cx, int btnW, int btnH, int gap,
                               PlayerWaveData pd, Location location) {
        int startY = this.height / 2 - 70;
        int i = 0;

        // Магазин (PvP)
        boolean hasShop = location.isPointShopMode()
            ? !location.getShopPoints().isEmpty()
            : !location.getShopItems().isEmpty();
        Button shopBtn = Button.builder(
            Component.translatable("wavedefense.button.shop"),
            b -> openShopForLocation(location)
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        shopBtn.active = hasShop;
        this.addRenderableWidget(shopBtn);
        buttonTooltips.put(shopBtn, TooltipHelper.SHOP_OPEN);

        // Статистика
        Button statsBtn = Button.builder(
            Component.translatable("wavedefense.button.team_stats"),
            b -> minecraft.setScreen(new PvpScoreboardScreen())
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        this.addRenderableWidget(statsBtn);
        buttonTooltips.put(statsBtn, TooltipHelper.STATS);

        // Налаштування
        Button settingsBtn = Button.builder(
            Component.translatable("wavedefense.button.settings"),
            b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        this.addRenderableWidget(settingsBtn);
        buttonTooltips.put(settingsBtn, TooltipHelper.HUD_SETTINGS);

        // Вийти з PvP (без штрафу)
        Button exitBtn = Button.builder(
            Component.translatable("wavedefense.button.exit_pvp"),
            b -> { PacketHandler.sendToServer(new ExitPvpPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        this.addRenderableWidget(exitBtn);
        buttonTooltips.put(exitBtn, I18n.get("wavedefense.tooltip.exit_pvp"));

        // Здатися (з пенальті)
        Button surrenderBtn = Button.builder(
            Component.translatable("wavedefense.button.surrender"),
            b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH).build();
        this.addRenderableWidget(surrenderBtn);
        buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

        // Закрити
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.close"),
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
            Component.translatable("wavedefense.button.shop"),
            b -> { if (location != null) openShopForLocation(location); }
        ).bounds(cx - btnW / 2, startY, btnW, btnH).build();
        shopBtn.active = hasShop;
        this.addRenderableWidget(shopBtn);
        buttonTooltips.put(shopBtn, TooltipHelper.SHOP_OPEN);

        Button settingsBtn = Button.builder(
            Component.translatable("wavedefense.button.settings"),
            b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); }
        ).bounds(cx - btnW / 2, startY + btnH + gap, btnW, btnH).build();
        this.addRenderableWidget(settingsBtn);
        buttonTooltips.put(settingsBtn, TooltipHelper.HUD_SETTINGS);

        Button surrenderBtn = Button.builder(
            Component.translatable("wavedefense.button.surrender"),
            b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); }
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 2, btnW, btnH).build();
        this.addRenderableWidget(surrenderBtn);
        buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.inventory"),
            b -> minecraft.setScreen(new InventoryScreen(player))
        ).bounds(cx - btnW / 2, startY + (btnH + gap) * 3, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.close"),
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
            // Обчислюємо тут, а не в init(), щоб коректно оновлювалось при зміні мови
            String spectatorLabel = ClientPvpStateManager.getPhase().equals("WAITING")
                    ? I18n.get("wavedefense.pvp.waiting")
                    : I18n.get("wavedefense.pvp.spectator_dead");
            g.drawCenteredString(this.font, I18n.get("wavedefense.pvp.menu_title"), cx, topY + 8, 0xFF5555);
            g.drawCenteredString(this.font, spectatorLabel, cx, topY + 22, 0xAAAAAA);
            g.drawCenteredString(this.font, I18n.get("wavedefense.pvp.spectator_hint"), cx, topY + 34, 0x888888);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // Обчислюємо pvpStatusLine тут (render-фрейм), не в init() — оновлюється при зміні мови
        String phase   = ClientPvpStateManager.getPhase();
        int timerSec   = ClientPvpStateManager.getTimerSeconds();
        int round      = ClientPvpStateManager.getCurrentRound();
        int total      = ClientPvpStateManager.getTotalRounds();
        String pvpStatusLine;
        if ("BUY".equals(phase))        pvpStatusLine = I18n.get("wavedefense.pvp.buy_phase", timerSec, round, total);
        else if ("ACTIVE".equals(phase)) pvpStatusLine = I18n.get("wavedefense.pvp.round_active", round, total);
        else if ("WAITING".equals(phase)) pvpStatusLine = I18n.get("wavedefense.pvp.waiting");
        else if (!phase.isEmpty())       pvpStatusLine = I18n.get("wavedefense.pvp.match_ended");
        else                             pvpStatusLine = "";

        if (!pvpStatusLine.isEmpty()) {
            String loc = ClientPvpStateManager.getLocation();
            g.drawCenteredString(this.font, "§c§l⚔ PvP — §r§f" + loc, cx, topY, 0xFF5555);
            g.drawCenteredString(this.font, pvpStatusLine, cx, topY + 12, 0xFFFFFF);

            // Рахунок команд
            Map<String, Integer> wins = ClientPvpStateManager.getTeamWins();
            if (!wins.isEmpty()) {
                StringBuilder sb = new StringBuilder(I18n.get("wavedefense.pvp.score_prefix"));
                wins.forEach((t, w) -> sb.append("§e").append(t).append("§7:§a").append(w).append("  "));
                g.drawCenteredString(this.font, sb.toString().trim(), cx, topY + 24, 0xFFFFFF);
            }
        } else {
            g.drawCenteredString(this.font, I18n.get("wavedefense.menu.title_pve"), cx, topY + 8, 0xFFFFFF);
        }

        // Tooltips при наведенні на кнопки
        this.renderables.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.components.Button btn) {
                if (btn.isHoveredOrFocused() && btn.active) {
                    String tip = buttonTooltips.get(btn);
                    if (tip != null) {
                        com.wavedefense.gui.TooltipHelper.renderIfEnabled(g, this.font, tip, mouseX, mouseY);
                    }
                }
            }
        });

        super.render(g, mouseX, mouseY, partialTick);
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
                    Component.translatable("wavedefense.msg.shop_too_far"), true);
                this.onClose();
                return;
            }
            if (sp.getItems().isEmpty()) {
                minecraft.player.displayClientMessage(
                    Component.translatable("wavedefense.msg.shop_empty_nearby"), true);
                return;
            }
            minecraft.setScreen(new PlayerShopScreen(loc, sp));
        } else {
            minecraft.setScreen(new PlayerShopScreen(loc));
        }
    }

}
