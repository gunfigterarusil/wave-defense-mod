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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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

    /** Обробка смерті мобів (PvE) та гравців (PvP) */
    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        // PvE: моб вбитий гравцем
        if (entity instanceof Mob mob) {
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                if (mob.getPersistentData().contains("location")) {
                    WaveDefenseMod.waveManager.onMobKilled(player, mob);
                }
            }
            return;
        }

        // PvP: гравець загинув
        if (entity instanceof ServerPlayer victim) {
            // Нараховуємо очки вбивці (якщо вбив інший гравець)
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                WaveDefenseMod.waveManager.onPlayerKilledPlayer(killer, victim);
            }
            // Штраф за смерть + респавн на точці команди
            WaveDefenseMod.waveManager.onPvpPlayerDeath(victim);
        }
    }

    /**
     * Блокування атак між союзниками в PvP (якщо friendly fire вимкнено).
     */
    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        if (!WaveDefenseMod.waveManager.canPvpAttack(attacker, target)) {
            event.setCanceled(true);
        }
    }

    /** Реєструємо удар для відслідковування асистів */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        WaveDefenseMod.waveManager.onPvpHit(attacker, victim);
    }

    @OnlyIn(Dist.CLIENT)
    public static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Спектатори (PvP між раундами) не можуть відкривати меню гри
        if (mc.player.isSpectator()) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§7Ви в режимі спостерігача. Зачекайте початку раунду."), true);
            return;
        }

        PlayerWaveData playerData = WaveDefenseMod.waveManager.getPlayerData(mc.player.getUUID());
        if (playerData != null && playerData.isInWave()) {
            mc.setScreen(new WaveActionsScreen());
            return;
        }

        if (mc.player.isCreative()) {
            mc.setScreen(new AdminMenuScreen());
        } else {
            mc.setScreen(new PlayerMenuScreen());
        }
    }
}
