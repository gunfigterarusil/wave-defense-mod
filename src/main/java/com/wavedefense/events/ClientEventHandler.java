package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.gui.ClientPvpStateManager;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клієнтські події:
 *  1) Анти-читинг хітбоксів на PvP
 *  2) Приховування нікнеймів ворожих гравців в PvP (RenderNameTagEvent)
 *     - Союзники: зелений колір ніку
 *     - Вороги під час ACTIVE: ніки не відображаються
 *     - FriendlyFire ON: не відображаються взагалі
 */
import com.wavedefense.gui.AdminMenuScreen;
import com.wavedefense.gui.ClientCtpStateManager;
import com.wavedefense.gui.ClientLeaderboardCache;
import com.wavedefense.gui.PlayerHUD;
import com.wavedefense.gui.PlayerMenuScreen;
import com.wavedefense.gui.WaveActionsScreen;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.RequestLocationDataPacket;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    /** Tracks whether the initial location-data sync has been sent this session. */
    private static boolean initialSyncSent = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // Reset flag when player disconnects so the next login triggers a fresh sync.
        if (mc.player == null || mc.level == null) {
            initialSyncSent = false;
            // H-3 fix: clear client-side CtP/KotH and leaderboard caches on disconnect
            // so stale overlay data doesn't bleed into the next login session.
            ClientCtpStateManager.reset();
            ClientLeaderboardCache.reset();
            return;
        }

        // Proactively fetch location data right after login so menus are not empty on first open.
        if (!initialSyncSent && mc.getConnection() != null) {
            PacketHandler.sendToServer(new RequestLocationDataPacket());
            initialSyncSent = true;
        }

        // Anti-cheat hitbox suppression — applies to BOTH PvP and PvE locations.
        // Previously only PvP players were guarded; PvE players could leave hitboxes on
        // and see mob outlines through walls. Any active location (PvE wave or PvP match)
        // now disables hitbox rendering until they exit.
        PlayerWaveData data = ClientPlayerDataManager.getPlayerData();
        if (data == null) return;
        boolean inActiveLocation = data.isInPvp() || data.isInWave();
        if (!inActiveLocation) return;

        EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        if (erd.shouldRenderHitBoxes()) {
            erd.setRenderHitBoxes(false);
            mc.player.displayClientMessage(
                Component.translatable("wavedefense.msg.hitboxes_blocked"), true);
        }
    }

    /**
     * Обробник нікнеймів над головами гравців у PvP.
     *
     * Правила:
     *  - Якщо NOT PvP: показуємо нормально.
     *  - FriendlyFire ON: ховаємо всіх (не знаєш де хто).
     *  - Своя команда: зелений нік.
     *  - Ворог під час ACTIVE раунду: ховаємо нік.
     *  - Ворог під час BUY/WAITING: показуємо нормально.
     */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Перевіряємо чи ми в PvP
        PlayerWaveData myData = ClientPlayerDataManager.getPlayerData();
        if (myData == null || !myData.isInPvp()) return;

        String myTeam = ClientPvpStateManager.getMyTeam();
        String phase  = ClientPvpStateManager.getPhase();
        boolean isActiveRound = "ACTIVE".equals(phase);

        // Знаходимо команду target гравця
        String targetTeam = getTargetTeam(target);

        // Якщо target — це я сам, нічого не змінюємо
        if (target.getUUID().equals(mc.player.getUUID())) return;

        // Friendly fire — ховаємо всіх
        if (isFriendlyFireOn(myData)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        boolean sameTeam = myTeam != null && myTeam.equals(targetTeam);

        if (sameTeam) {
            // Союзник: зелений нік
            Component greenName = Component.literal("§a" + target.getName().getString());
            event.setContent(greenName);
            event.setResult(Event.Result.ALLOW);
        } else if (isActiveRound) {
            // Ворог під час активного раунду: ховаємо
            event.setResult(Event.Result.DENY);
        }
        // В BUY/WAITING: показуємо нормально (не втручаємось)
    }

    private static String getTargetTeam(Player target) {
        // Шукаємо команду гравця в ClientPvpStateManager
        for (ClientPvpStateManager.PlayerRow row : ClientPvpStateManager.getPlayers()) {
            if (row.name.equals(target.getName().getString())) {
                return row.team;
            }
        }
        return null;
    }

    private static boolean isFriendlyFireOn(PlayerWaveData data) {
        if (data.getCurrentLocation() == null) return false;
        return data.getCurrentLocation().isPvpFriendlyFire();
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // "hotbar" рендериться кожного кадру (не тільки при Tab)
        if (!event.getOverlay().id().getPath().equals("hotbar")) return;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        PlayerHUD.render(event.getGuiGraphics(), event.getPartialTick(), w, h);
        // v0.2.62 — ready-check overlay (self-guards on phase != READY_CHECK)
        com.wavedefense.gui.PvpReadyHud.render(event.getGuiGraphics(), w, h);
    }

    /**
     * Відкрити Wave Defense меню для поточного гравця (клієнтська сторона).
     * Викликається з KeyBindings.
     */
    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isSpectator()) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("wavedefense.auto.ви_в_режимі_спостерігача_зачекай_24ec1d94"), true);
            return;
        }
        // ── ВИПРАВЛЕНО: використовуємо клієнтський менеджер, не серверний waveManager ──
        PlayerWaveData playerData = ClientPlayerDataManager.getPlayerData();
        if (playerData != null && playerData.isInWave()) {
            mc.setScreen(new WaveActionsScreen());
            return;
        }
        // Адмін-меню — тільки для гравців з правами оператора (рівень 2+)
        // Раніше перевірявся лише creative-режим, що дозволяло non-op creative гравцям бачити адмін GUI
        if (mc.player.hasPermissions(2)) {
            mc.setScreen(new AdminMenuScreen());
        } else {
            mc.setScreen(new PlayerMenuScreen());
        }
    }

}