package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID, value = Dist.CLIENT)
public class HudMouseHandler {

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() == 0 && event.getAction() == 1) { // Left click pressed
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) { // Only when no GUI is open
                double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                
                if (HudOverlay.handleClick(mouseX, mouseY)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
