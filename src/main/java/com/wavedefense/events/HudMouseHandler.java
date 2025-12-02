package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
public class HudMouseHandler {

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0) { // Left click
            if (HudOverlay.handleClick(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
            }
        }
    }
}