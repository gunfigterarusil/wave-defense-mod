package com.wavedefense.gui;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SurrenderPacket;
import com.wavedefense.network.packets.ExitPvpPacket;
import com.wavedefense.wave.PlayerWaveData;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import com.wavedefense.gui.TooltipHelper;

/**
 * Головне ігрове меню (клавіша E на локації).
 * PvP: Магазин | Статистика | Налаштування | Здатися
 * PvE: Магазин | Налаштування | Здатися | Інвентар
 */
public class WaveActionsScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    // Не кешуємо рядки в полях — обчислюємо в render() через I18n.get(),
    // щоб вони оновлювались при зміні мови без перевідкриття екрану.
    private final Map<Button, String> buttonTooltips = new HashMap<>();
    private int storedBtnW = 200;
    private int separatorY = -1;
    private int contentTop = -1, contentBot = -1;

    public WaveActionsScreen() {
        super(new TranslationTextComponent("wavedefense.title.wave_actions"));
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
        this.storedBtnW = btnW;

        PlayerEntity player = minecraft.player;
        if (player == null) return;

        // ── Спектатор у PvP — кнопки "Вийти з PvP" і "Здатися" ────────────────
        if (player.isSpectator()) {
            int startY = this.height / 2 - 40;
            this.contentTop = startY - 8;
            this.contentBot = startY + btnH + gap + 4 + 24;
            this.separatorY = -1;
            Button surrenderBtn = new Button(cx - btnW / 2, startY, btnW, btnH, new TranslationTextComponent("wavedefense.button.surrender_penalty"), b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); });
            this.addButton(surrenderBtn);
            buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

            this.addButton(new Button(cx - 55, startY + btnH + gap + 4, 110, 20, new TranslationTextComponent("wavedefense.button.close_menu"), b -> this.onClose()));
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
        this.contentTop  = startY - 8;
        this.contentBot  = startY + (btnH + gap) * 5 + btnH + 6;
        this.separatorY  = startY + (btnH + gap) * 3 - gap / 2;
        int i = 0;

        // Магазин (PvP)
        boolean hasShop = location.isPointShopMode()
            ? !location.getShopPoints().isEmpty()
            : !location.getShopItems().isEmpty();
        Button shopBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH, new TranslationTextComponent("wavedefense.button.shop"), b -> openShopForLocation(location));
        shopBtn.active = hasShop;
        this.addButton(shopBtn);
        buttonTooltips.put(shopBtn, TooltipHelper.SHOP_OPEN);

        // Статистика
        Button statsBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH, new TranslationTextComponent("wavedefense.button.team_stats"), b -> minecraft.setScreen(new PvpScoreboardScreen()));
        this.addButton(statsBtn);
        buttonTooltips.put(statsBtn, TooltipHelper.STATS);

        // Налаштування
        Button settingsBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH, new TranslationTextComponent("wavedefense.button.settings"), b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); });
        this.addButton(settingsBtn);
        buttonTooltips.put(settingsBtn, TooltipHelper.HUD_SETTINGS);

        // Вийти з PvP (без штрафу)
        Button exitBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH, new TranslationTextComponent("wavedefense.button.exit_pvp"), b -> { PacketHandler.sendToServer(new ExitPvpPacket()); this.onClose(); });
        this.addButton(exitBtn);
        buttonTooltips.put(exitBtn, I18n.get("wavedefense.tooltip.exit_pvp"));

        // Здатися (з пенальті) — B4 fix: use surrender_penalty key (red + "(з пенальті)" label)
        // to visually distinguish from "Вийти з PvP" (no penalty, yellow).
        Button surrenderBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * i++, btnW, btnH, new TranslationTextComponent("wavedefense.button.surrender_penalty"), b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); });
        this.addButton(surrenderBtn);
        buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

        // Закрити
        this.addButton(new Button(cx - 50, startY + (btnH + gap) * i + 4, 100, 20, new TranslationTextComponent("wavedefense.button.close"), b -> this.onClose()));
    }

    private void renderPveMenu(int cx, int btnW, int btnH, int gap,
                               PlayerWaveData pd, Location location, PlayerEntity player) {
        int startY = this.height / 2 - 60;
        this.contentTop  = startY - 8;
        this.contentBot  = startY + (btnH + gap) * 4 + btnH + 6;
        this.separatorY  = startY + (btnH + gap) * 2 - gap / 2;

        boolean hasShop = location != null && (location.isPointShopMode()
            ? !location.getShopPoints().isEmpty()
            : !location.getShopItems().isEmpty());
        Button shopBtn = new Button(cx - btnW / 2, startY, btnW, btnH, new TranslationTextComponent("wavedefense.button.shop"), b -> { if (location != null) openShopForLocation(location); });
        shopBtn.active = hasShop;
        this.addButton(shopBtn);
        buttonTooltips.put(shopBtn, TooltipHelper.SHOP_OPEN);

        Button settingsBtn = new Button(cx - btnW / 2, startY + btnH + gap, btnW, btnH, new TranslationTextComponent("wavedefense.button.settings"), b -> { if (pd != null) minecraft.setScreen(new PlayerSettingsScreen(pd)); });
        this.addButton(settingsBtn);
        buttonTooltips.put(settingsBtn, TooltipHelper.HUD_SETTINGS);

        Button surrenderBtn = new Button(cx - btnW / 2, startY + (btnH + gap) * 2, btnW, btnH, new TranslationTextComponent("wavedefense.button.surrender"), b -> { PacketHandler.sendToServer(new SurrenderPacket()); this.onClose(); });
        this.addButton(surrenderBtn);
        buttonTooltips.put(surrenderBtn, TooltipHelper.SURRENDER);

        this.addButton(new Button(cx - btnW / 2, startY + (btnH + gap) * 3, btnW, btnH, new TranslationTextComponent("wavedefense.button.inventory"), b -> minecraft.setScreen(new InventoryScreen(player))));

        this.addButton(new Button(cx - 50, startY + (btnH + gap) * 4 + 10, 100, 20, new TranslationTextComponent("wavedefense.button.close"), b -> this.onClose()));
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx   = this.width / 2;
        int topY = this.height / 2 - 90;

        // Стилізований заголовок угорі екрану
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        // Рамка навколо блоку кнопок
        if (contentTop >= 0 && contentBot > contentTop) {
            GuiTheme.renderContentFrame(g,
                cx - storedBtnW / 2 - 6, contentTop,
                cx + storedBtnW / 2 + 6, contentBot);
        }

        // Роздільник між навігаційними та небезпечними кнопками
        if (separatorY > 0) {
            com.wavedefense.gui.GuiCompat.fill(g, cx - storedBtnW / 2, separatorY,
                   cx + storedBtnW / 2, separatorY + 1, GuiTheme.BORDER);
        }

        // Спектатор — простий оверлей
        if (minecraft.player != null && minecraft.player.isSpectator()) {
            String spectatorLabel = ClientPvpStateManager.getPhase().equals("WAITING")
                    ? I18n.get("wavedefense.pvp.waiting")
                    : I18n.get("wavedefense.pvp.spectator_dead");
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.pvp.menu_title"), cx, topY + 8, GuiTheme.STATUS_PVP);
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, spectatorLabel, cx, topY + 22, GuiTheme.TEXT_MUTED);
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.pvp.spectator_hint"), cx, topY + 34, GuiTheme.TEXT_MUTED);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // Обчислюємо pvpStatusLine тут (render-фрейм), не в init() — оновлюється при зміні мови
        String phase   = ClientPvpStateManager.getPhase();
        int timerSec   = ClientPvpStateManager.getTimerSeconds();
        int round      = ClientPvpStateManager.getCurrentRound();
        int total      = ClientPvpStateManager.getTotalRounds();
        String pvpStatusLine;
        if ("BUY".equals(phase))         pvpStatusLine = I18n.get("wavedefense.pvp.buy_phase", timerSec, round, total);
        else if ("ACTIVE".equals(phase)) pvpStatusLine = I18n.get("wavedefense.pvp.round_active", round, total);
        else if ("WAITING".equals(phase)) pvpStatusLine = I18n.get("wavedefense.pvp.waiting");
        else if (!phase.isEmpty())        pvpStatusLine = I18n.get("wavedefense.pvp.match_ended");
        else                              pvpStatusLine = "";

        if (!pvpStatusLine.isEmpty()) {
            String loc = ClientPvpStateManager.getLocation();
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§c§l⚔ PvP — §r§f" + loc, cx, topY, GuiTheme.TEXT);
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, pvpStatusLine, cx, topY + 12, GuiTheme.TEXT);

            // Рахунок команд
            Map<String, Integer> wins = ClientPvpStateManager.getTeamWins();
            if (!wins.isEmpty()) {
                StringBuilder sb = new StringBuilder(I18n.get("wavedefense.pvp.score_prefix"));
                wins.forEach((t, w) -> sb.append("§e").append(t).append("§7:§a").append(w).append("  "));
                com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, sb.toString().trim(), cx, topY + 24, GuiTheme.TEXT);
            }
        } else {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, I18n.get("wavedefense.menu.title_pve"), cx, topY + 8, GuiTheme.TEXT);
        }

        // Tooltips при наведенні на кнопки
        this.buttons.forEach(r -> {
            if (r instanceof net.minecraft.client.gui.widget.button.Button) { net.minecraft.client.gui.widget.button.Button btn = (net.minecraft.client.gui.widget.button.Button) r;
                if (btn.isHovered() && btn.active) {
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
                    new TranslationTextComponent("wavedefense.msg.shop_too_far"), true);
                this.onClose();
                return;
            }
            if (sp.getItems().isEmpty()) {
                minecraft.player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.shop_empty_nearby"), true);
                return;
            }
            minecraft.setScreen(new PlayerShopScreen(loc, sp));
        } else {
            minecraft.setScreen(new PlayerShopScreen(loc));
        }
    }

}
