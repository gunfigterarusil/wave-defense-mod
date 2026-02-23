package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.gui.AdminMenuScreen;
import com.wavedefense.gui.PlayerHUD;
import com.wavedefense.gui.PlayerMenuScreen;
import com.wavedefense.gui.WaveActionsScreen;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class EventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WaveDefenseMod.waveManager.tick();
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().getPath().equals("player_list")) {
            PlayerHUD.render(event.getGuiGraphics(), event.getPartialTick(),
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Mob mob && !entity.level().isClientSide) {
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                if (mob.getPersistentData().contains("location")) {
                    WaveDefenseMod.waveManager.onMobKilled(player, mob);
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Якщо гравець зараз на локації (в хвилі) — відкриваємо WaveActionsScreen
        PlayerWaveData playerData = WaveDefenseMod.waveManager.getPlayerData(mc.player.getUUID());
        if (playerData != null && playerData.isInWave()) {
            mc.setScreen(new WaveActionsScreen());
            return;
        }

        // Гравець не на локації — стандартна поведінка
        if (mc.player.isCreative()) {
            mc.setScreen(new AdminMenuScreen());
        } else {
            mc.setScreen(new PlayerMenuScreen());
        }
    }
}
