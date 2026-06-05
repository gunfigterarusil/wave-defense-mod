package com.wavedefense.events;

import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.util.Constants;

import net.minecraft.client.util.InputMappings;
import com.wavedefense.WaveDefenceMod;
import com.wavedefense.gui.ClientLocationManager;
import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.gui.PlayerShopScreen;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = WaveDefenceMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {

    public static final String CATEGORY = "key.categories.wavedefense";

    /** V — відкрити головне меню гравця */
    public static KeyBinding openMenuKey;

    /** B — відкрити магазин напряму (без меню) */
    public static KeyBinding openShopKey;

    /** G — вийти з поточної локації (працює як «Здатися» без штрафу).
     *  K1 fix: changed from L (conflicts with vanilla Advancements) to G. */
    public static KeyBinding leaveLocationKey;

    /** R — toggle ready during PvP READY_CHECK phase (v0.2.62) */
    public static KeyBinding readyKey;

    /** F4 — toggle AdminDebugHud overlay (v0.2.65, op-level only) */
    public static KeyBinding debugHudKey;

    /** 1.16.5: called from {@link com.wavedefense.WaveDefenceMod#clientSetup} during
     *  FMLClientSetupEvent. 1.16.5 has no RegisterKeyMappingsEvent — keybindings are
     *  registered via {@link ClientRegistry#registerKeyBinding(KeyBinding)}. */
    public static void registerAll() {
        openMenuKey = new KeyBinding(
                "key.wavedefense.openmenu",
                KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        );
        ClientRegistry.registerKeyBinding(openMenuKey);

        openShopKey = new KeyBinding(
                "key.wavedefense.openshop",
                KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        );
        ClientRegistry.registerKeyBinding(openShopKey);

        leaveLocationKey = new KeyBinding(
                "key.wavedefense.leavelocation",
                KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        );
        ClientRegistry.registerKeyBinding(leaveLocationKey);

        readyKey = new KeyBinding(
                "key.wavedefense.ready",
                KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CATEGORY
        );
        ClientRegistry.registerKeyBinding(readyKey);

        debugHudKey = new KeyBinding(
                "key.wavedefense.debughud",
                KeyConflictContext.IN_GAME,
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_F4,
                CATEGORY
        );
        ClientRegistry.registerKeyBinding(debugHudKey);
    }

    @Mod.EventBusSubscriber(modid = WaveDefenceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // L — вийти з локації. Працює навіть якщо відкрито меню / екран.
            if (leaveLocationKey != null) {
                while (leaveLocationKey.consumeClick()) {
                    PlayerWaveData pd = ClientPlayerDataManager.getPlayerData();
                    if (pd != null && pd.isInWave()) {
                        com.wavedefense.network.PacketHandler.sendToServer(
                            new com.wavedefense.network.packets.LeaveLocationPacket());
                        mc.player.displayClientMessage(
                            new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.leaving_location"), true);
                        // Закриваємо поточний екран щоб не блокував
                        if (mc.screen != null) mc.setScreen(null);
                    }
                }
            }

            // v0.2.65: record tick for AdminDebugHud TPS probe
            com.wavedefense.gui.AdminDebugHud.TickRateProbe.recordTick();

            // F4 — toggle admin debug HUD overlay (v0.2.65)
            if (debugHudKey != null) {
                while (debugHudKey.consumeClick()) {
                    if (mc.player != null && mc.player.hasPermissions(2)) {
                        com.wavedefense.gui.AdminDebugHud.visible =
                            !com.wavedefense.gui.AdminDebugHud.visible;
                    }
                }
            }

            // R — toggle ready during PvP READY_CHECK phase (v0.2.62)
            if (readyKey != null) {
                while (readyKey.consumeClick()) {
                    String phase = com.wavedefense.gui.ClientPvpStateManager.getPhase();
                    if ("READY_CHECK".equals(phase)) {
                        boolean wasReady = com.wavedefense.gui.ClientPvpStateManager.isMeReady();
                        com.wavedefense.network.PacketHandler.sendToServer(
                            new com.wavedefense.network.packets.ReadyCheckPacket(!wasReady));
                        mc.player.displayClientMessage(
                            new net.minecraft.util.text.TranslationTextComponent(
                                wasReady ? "wavedefense.msg.ready_off" : "wavedefense.msg.ready_on"),
                            true);
                    }
                }
            }

            // Не обробляємо V/B якщо відкрито якесь меню
            if (mc.screen != null) return;

            // V — відкрити меню
            if (openMenuKey != null) {
                while (openMenuKey.consumeClick()) {
                    ClientEventHandler.openMenu();
                }
            }

            // B — відкрити магазин напряму (якщо гравець на локації і є магазин)
            if (openShopKey != null && com.wavedefense.config.WaveDefenseConfig.SHOP_HOTKEY_ENABLED.get()) {
                while (openShopKey.consumeClick()) {
                    openShopDirect(mc);
                }
            }
        }

        private static void openShopDirect(Minecraft mc) {
            // Спектатори не можуть відкривати магазин
            if (mc.player.isSpectator()) {
                mc.player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.shop_spectator"), true);
                return;
            }

            PlayerWaveData pd = ClientPlayerDataManager.getPlayerData();
            if (pd == null || pd.getCurrentLocation() == null) return;

            com.wavedefense.data.Location loc = ClientLocationManager.getLocation(pd.getCurrentLocation().getName());
            if (loc == null) loc = pd.getCurrentLocation();

            // Точковий режим: шукаємо найближчу точку магазину
            if (loc.isPointShopMode()) {
                double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
                com.wavedefense.data.ShopPoint sp = loc.findNearestShopPoint(px, py, pz);
                if (sp == null) {
                    mc.player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.shop_unavailable"), true);
                    return;
                }
                if (sp.getItems().isEmpty()) {
                    mc.player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.shop_empty"), true);
                    return;
                }
                mc.setScreen(new PlayerShopScreen(loc, sp));
                return;
            }

            // Звичайний (глобальний) режим
            if (loc.getShopItems().isEmpty()) {
                mc.player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.shop_empty"), true);
                return;
            }
            mc.setScreen(new PlayerShopScreen(loc));
        }
    }
}
