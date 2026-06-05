package com.wavedefense.events;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.gui.ClientPvpStateManager;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клієнтські події:
 *  1) Анти-читинг хітбоксів на PvP
 *  2) Приховування нікнеймів ворожих гравців в PvP (RenderNameplateEvent)
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
import net.minecraftforge.client.event.RenderGameOverlayEvent;
@Mod.EventBusSubscriber(modid = WaveDefenceMod.MODID, value = Dist.CLIENT)
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

        EntityRendererManager erd = mc.getEntityRenderDispatcher();
        if (erd.shouldRenderHitBoxes()) {
            erd.setRenderHitBoxes(false);
            mc.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.msg.hitboxes_blocked"), true);
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
    public static void onRenderNameTag(RenderNameplateEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) return; PlayerEntity target = (PlayerEntity) event.getEntity();

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
            net.minecraft.util.text.ITextComponent greenName = new StringTextComponent("§a" + target.getName().getString());
            event.setContent(greenName);
            event.setResult(Event.Result.ALLOW);
        } else if (isActiveRound) {
            // Ворог під час активного раунду: ховаємо
            event.setResult(Event.Result.DENY);
        }
        // В BUY/WAITING: показуємо нормально (не втручаємось)
    }

    private static String getTargetTeam(PlayerEntity target) {
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
    public static void onRenderGuiOverlay(RenderGameOverlayEvent.Post event) {
        // 1.16.5: RenderGameOverlayEvent has getType()/getMatrixStack()/getPartialTicks()
        if (event.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HOTBAR) return;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        com.mojang.blaze3d.matrix.MatrixStack g = event.getMatrixStack();
        PlayerHUD.render(g, event.getPartialTicks(), w, h);
        // v0.2.62 — ready-check overlay (self-guards on phase != READY_CHECK)
        com.wavedefense.gui.PvpReadyHud.render(g, w, h);
        // v0.2.65 — admin debug HUD (self-guards on F4 toggle + op level)
        com.wavedefense.gui.AdminDebugHud.render(g, w, h);
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
                new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.ви_в_режимі_спостерігача_зачекай_24ec1d94"), true);
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