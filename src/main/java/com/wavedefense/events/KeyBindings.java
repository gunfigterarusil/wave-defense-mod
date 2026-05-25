package com.wavedefense.events;

import com.mojang.blaze3d.platform.InputConstants;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.gui.ClientLocationManager;
import com.wavedefense.gui.ClientPlayerDataManager;
import com.wavedefense.gui.PlayerShopScreen;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {

    public static final String CATEGORY = "key.categories.wavedefense";

    /** V — відкрити головне меню гравця */
    public static KeyMapping openMenuKey;

    /** B — відкрити магазин напряму (без меню) */
    public static KeyMapping openShopKey;

    /** G — вийти з поточної локації (працює як «Здатися» без штрафу).
     *  K1 fix: changed from L (conflicts with vanilla Advancements) to G. */
    public static KeyMapping leaveLocationKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping(
                "key.wavedefense.openmenu",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        );
        event.register(openMenuKey);

        openShopKey = new KeyMapping(
                "key.wavedefense.openshop",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        );
        event.register(openShopKey);

        leaveLocationKey = new KeyMapping(
                "key.wavedefense.leavelocation",
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,  // K1 fix: was GLFW_KEY_L (conflicts with vanilla Advancements)
                CATEGORY
        );
        event.register(leaveLocationKey);
    }

    @Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
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
                            net.minecraft.network.chat.Component.translatable("wavedefense.msg.leaving_location"), true);
                        // Закриваємо поточний екран щоб не блокував
                        if (mc.screen != null) mc.setScreen(null);
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
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.shop_spectator"), true);
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
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.shop_unavailable"), true);
                    return;
                }
                if (sp.getItems().isEmpty()) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.shop_empty"), true);
                    return;
                }
                mc.setScreen(new PlayerShopScreen(loc, sp));
                return;
            }

            // Звичайний (глобальний) режим
            if (loc.getShopItems().isEmpty()) {
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.shop_empty"), true);
                return;
            }
            mc.setScreen(new PlayerShopScreen(loc));
        }
    }
}
