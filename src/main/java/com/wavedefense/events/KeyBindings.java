package com.wavedefense.events;

import com.mojang.blaze3d.platform.InputConstants;
import com.wavedefense.WaveDefenseMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.categories.wavedefense";
    public static KeyMapping openMenuKey;

    @Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventHandler {
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
            WaveDefenseMod.LOGGER.info("Key bindings registered");
        }
    }

    @Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (openMenuKey != null && event.phase == TickEvent.Phase.END) {
                while (openMenuKey.consumeClick()) {
                    EventHandler.openMenu();
                }
            }
        }
    }
}
