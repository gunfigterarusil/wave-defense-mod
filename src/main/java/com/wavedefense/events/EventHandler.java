package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.gui.AdminMenuScreen;
import com.wavedefense.gui.PlayerHUD;
import com.wavedefense.gui.PlayerMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WaveDefenseMod.waveManager.tick();
        }
    }

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().getPath().equals("player_list")) {
            PlayerHUD.render(event.getGuiGraphics(), event.getPartialTick(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        }
    }

    public static void openMenu() {
        if (Minecraft.getInstance().player != null) {
            if (Minecraft.getInstance().player.isCreative()) {
                Minecraft.getInstance().setScreen(new AdminMenuScreen());
            } else {
                Minecraft.getInstance().setScreen(new PlayerMenuScreen());
            }
        }
    }
}
