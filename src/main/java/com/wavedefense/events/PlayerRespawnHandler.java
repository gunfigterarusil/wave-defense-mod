package com.wavedefense.events;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Обробник респавну після смерті.
 *
 * PvP ACTIVE: гравець вмирає → бачить екран смерті → натискає Відродитись →
 *   тут переводимо у spectator і телепортуємо на точку спавну команди.
 *   Якщо раундів більше немає (ENDED) → відновлюємо backup.
 *
 * PvP WAITING/BUY/COUNTDOWN:
 *   Якщо pvpWaitEffect → накладаємо ефекти (slowness+blindness).
 *   Інакше → spectator.
 *
 * PvE: гравець загинув → виходить з локації (onPvePlayerDeath) →
 *   при respawn → відновлюємо backup (позиція + інвентар до входу).
 */
@Mod.EventBusSubscriber(modid = WaveDefenceMod.MODID)
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return; ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();

        // ── PvE death restore ──────────────────────────────────────────────
        PlayerBackup deathRestore = WaveDefenceMod.waveManager.consumePendingDeathRestore(player.getUUID());
        if (deathRestore != null) {
            deathRestore.restore(player);
            return;
        }

        // ── PvP ───────────────────────────────────────────────────────────
        PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;
        Location location = data.getCurrentLocation();
        if (!location.isPvp()) return;

        // Перевіряємо чи гравець щойно помер у ACTIVE раунді
        boolean wasPvpDeath = WaveDefenceMod.waveManager.getPvpPendingRespawn()
            .contains(player.getUUID());
        WaveDefenceMod.waveManager.getPvpPendingRespawn()
            .remove(player.getUUID());

        com.wavedefense.data.PvpRoundState pvpState =
            WaveDefenceMod.waveManager.getPvpState(location.getName());

        if (pvpState == null ||
            pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ENDED) {
            // Матч завершено — повертаємо backup (endPvpMatch сам викликає surrenderPlayer,
            // але на всяк випадок якщо гравець відроджується після ENDED)
            WaveDefenceMod.waveManager.surrenderPlayer(player);
            return;
        }

        boolean isActive = pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ACTIVE;
        boolean isWaiting = pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.WAITING
                         || pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.BUY
                         || pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.COUNTDOWN
                         || pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ROUND_END_DELAY;

        // Знаходимо точку спавну команди
        String team = location.getPlayerTeam(player.getUUID());
        PvpSpawnPoint teamSpawn = null;
        if (team != null) {
            for (PvpSpawnPoint sp : location.getPvpSpawnPoints()) {
                if (sp.getTeamName().equals(team)) { teamSpawn = sp; break; }
            }
        }

        if (wasPvpDeath && isActive) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            if (location.isDeathmatch()) {
                // B1: pick spawn point honouring dmSpawnMode (Team / Random / Smart).
                PvpSpawnPoint dmSpawn = WaveDefenceMod.waveManager.pvpMgr.pickDmSpawn(
                    location, player, teamSpawn);
                int delaySeconds = com.wavedefense.config.WaveDefenseConfig.PVP_RESPAWN_DELAY_SECONDS.get();
                if (delaySeconds > 0) {
                    // Затримка респавну: spectator + таймер
                    player.setGameMode(GameType.SPECTATOR);
                    if (dmSpawn != null) {
                        WaveDefenceMod.waveManager.teleportToSafeSpawn(player, dmSpawn.getPos(), 0);
                    }
                    WaveDefenceMod.waveManager.schedulePvpRespawn(player.getUUID(), dmSpawn, delaySeconds);
                    player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.respawn_in", delaySeconds), true);
                } else {
                    // Deathmatch: миттєве відродження на обраній точці (не spectator)
                    if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        player.setGameMode(GameType.SURVIVAL);
                    }
                    if (dmSpawn != null) {
                        WaveDefenceMod.waveManager.teleportToSpawnPoint(player, dmSpawn);
                    }
                    // Невразливість на 3 секунди після відродження (60 тіків)
                    player.invulnerableTime = 60;
                    // Ефект регенерації і вогнестійкості на 3 сек щоб уникнути миттєвої смерті
                    player.addEffect(new net.minecraft.potion.EffectInstance(
                        net.minecraft.potion.Effects.REGENERATION, 60, 1, false, false));
                    player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.respawn_continue"), true);
                }
            } else if (location.isBattleRoyale()) {
                // BR: player is eliminated permanently — move to spectator at their team spawn.
                // BR2 fix: removed the dead setGameMode(SURVIVAL) line that was immediately
                // overridden by setGameMode(SPECTATOR) two lines below.
                if (teamSpawn != null) {
                    WaveDefenceMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
                }
                // In BR a player is eliminated for good — stay as spectator
                player.setGameMode(GameType.SPECTATOR);
            } else {
                // Standard: spectator на точці спавну
                player.setGameMode(GameType.SPECTATOR);
                if (teamSpawn != null) {
                    WaveDefenceMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
                }
            }
        } else if (isWaiting) {
            // Очікування/купівля — режим залежить від налаштувань
            player.setHealth(player.getMaxHealth());
            if (location.isPvpWaitEffect()) {
                // Survival + ефекти нерухомості
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                WaveDefenceMod.waveManager.reapplyWaitEffects(player);
            } else {
                player.setGameMode(GameType.SPECTATOR);
            }
            if (teamSpawn != null) {
                WaveDefenceMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
            }
        } else {
            // Fallback: spectator на точці спавну
            player.setGameMode(GameType.SPECTATOR);
            if (teamSpawn != null) {
                WaveDefenceMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
            }
        }
    }
}
