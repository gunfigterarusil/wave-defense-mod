package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.WaveTrigger;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Серверні події — без жодних клієнтських імпортів.
 * Клієнтські події (HUD, меню) — в ClientEventHandler.
 */
public class EventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // D3 fix: wrap tick in try/catch so a single sub-manager exception
            // cannot crash the entire server — log and skip the offending tick.
            try {
                WaveDefenseMod.waveManager.tick();
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("[WaveDefense] Uncaught exception in server tick — skipping this tick", e);
            }
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

        // PvE/PvP: гравець загинув
        if (entity instanceof ServerPlayer victim) {
            PlayerWaveData deathData = WaveDefenseMod.waveManager.getPlayerData(victim.getUUID());
            if (deathData != null && deathData.getCurrentLocation() != null) {
                if (deathData.getCurrentLocation().isPvp()) {
                    // PvP: гравець не губить предмети — прибираємо при вході, повертаємо backup при виході
                    // Підміняємо gamerule keepInventory тимчасово
                    net.minecraft.world.level.GameRules rules = victim.level().getGameRules();
                    boolean wasKeep = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
                    if (!wasKeep) rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
                        .set(true, victim.server);
                    // PvP: передаємо кілеру якщо є, потім PvP death handler
                    try {
                        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                            WaveDefenseMod.waveManager.onPlayerKilledPlayer(killer, victim);
                        } else {
                            WaveDefenseMod.waveManager.onPvpPlayerDeath(victim);
                        }
                    } finally {
                        // Відновлюємо gamerule завжди, навіть при виключенні
                        if (!wasKeep) rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
                            .set(false, victim.server);
                    }
                } else {
                    // PvE: гравець помирає → виходить з локації
                    // Викликаємо ДО fireWaveTrigger щоб playerData був ще доступний
                    WaveDefenseMod.waveManager.onPvePlayerDeath(victim);
                }
            }
            // Тригери після обробки смерті (playerData вже може бути null — це нормально)
            WaveDefenseMod.waveManager.fireWaveTriggerForPlayer(victim, WaveTrigger.PLAYER_DEATH);
            WaveDefenseMod.waveManager.fireLocationTrigger(victim, WaveTrigger.PLAYER_DEATH);
        }
    }

    /** Блокування атак між союзниками в PvP (якщо friendly fire вимкнено). */
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

    @SubscribeEvent
    public void onPlayerOpenContainer(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WaveDefenseMod.waveManager.fireWaveTriggerForPlayer(player, WaveTrigger.PLAYER_OPEN_CHEST);
    }

    @SubscribeEvent
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        net.minecraft.world.level.block.state.BlockState state =
            event.getEntity().level().getBlockState(event.getHitVec().getBlockPos());
        boolean isContainer = state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.TrappedChestBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.DispenserBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.HopperBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.DropperBlock;
        boolean isDoor = state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
        if (isContainer) {
            WaveDefenseMod.waveManager.fireWaveTriggerForPlayer(player, WaveTrigger.PLAYER_OPEN_CHEST);
        }
        if (isDoor) {
            WaveDefenseMod.waveManager.fireWaveTriggerForPlayer(player, WaveTrigger.PLAYER_OPEN_DOOR);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // If the player was in a location, surrender cleanly to restore inventory and free session slot.
        // Without this, playerData/playerBackups/reEntryCooldowns leak forever after disconnect.
        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (data != null && data.getCurrentLocation() != null) {
            WaveDefenseMod.waveManager.surrenderPlayer(player);
        }
        // G1 fix: free rate-limiter entries for this player to avoid unbounded map growth
        com.wavedefense.network.PacketRateLimiter.evictPlayer(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WaveDefenseMod.waveManager.fireLocationTrigger(player, WaveTrigger.PLAYER_JOIN);
        // Надсилаємо дані локацій новому гравцю одразу при вході
        // (без цього ClientLocationManager порожній і жоден інтерфейс не працює)
        WaveDefenseMod.waveManager.syncLocationDataToPlayer(player);
        // Якщо гравець вже був на локації (наприклад, переконнект) — відновлюємо
        WaveDefenseMod.waveManager.syncPlayerData(player);
    }

    /**
     * Блокує перехід в Creative mode якщо гравець знаходиться на локації.
     * Дозволяє лише Spectator (для PvP смерті) та Survival/Adventure.
     */
    @SubscribeEvent
    public void onGameModeChange(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getNewGameMode() != GameType.CREATIVE) return;

        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;

        // Гравець на локації — блокуємо Creative
        event.setResult(Event.Result.DENY);
        player.displayClientMessage(
            Component.translatable("wavedefense.msg.creative_blocked",
                data.getCurrentLocation().getName()), true);
    }

    /**
     * Кожен тік перевіряє чи гравець на локації в Creative — якщо так, переводить у потрібний режим.
     * Це обробляє випадок коли гравець вже був в Creative перед входом на локацію.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) return;

        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;

        // Гравець в Creative на локації → примусово переводимо в налаштований режим
        GameType target = com.wavedefense.config.WaveDefenseConfig.getLocationGameType();
        player.setGameMode(target);
        player.displayClientMessage(
            Component.translatable("wavedefense.msg.gamemode_changed", target.getName()), true);
    }
}
