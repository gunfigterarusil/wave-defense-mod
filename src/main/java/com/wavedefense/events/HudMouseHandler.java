package com.wavedefense.events;

import com.wavedefense.WaveDefenceMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WaveDefenceMod.MODID, value = Dist.CLIENT)
public class HudMouseHandler {

    @SubscribeEvent
    public static void onMouseClick(GuiScreenEvent.MouseClickedEvent.Pre event) {
        if (event.getButton() == 0) { // Left click
            if (HudOverlay.handleClick(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
            }
        }
    }
}