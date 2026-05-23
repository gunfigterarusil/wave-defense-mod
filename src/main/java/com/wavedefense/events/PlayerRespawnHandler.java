package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.data.PvpSpawnPoint;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
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
@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID)
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // ── PvE death restore ──────────────────────────────────────────────
        PlayerBackup deathRestore = WaveDefenseMod.waveManager.consumePendingDeathRestore(player.getUUID());
        if (deathRestore != null) {
            deathRestore.restore(player);
            return;
        }

        // ── PvP ───────────────────────────────────────────────────────────
        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;
        Location location = data.getCurrentLocation();
        if (!location.isPvp()) return;

        // Перевіряємо чи гравець щойно помер у ACTIVE раунді
        boolean wasPvpDeath = WaveDefenseMod.waveManager.getPvpPendingRespawn()
            .contains(player.getUUID());
        WaveDefenseMod.waveManager.getPvpPendingRespawn()
            .remove(player.getUUID());

        com.wavedefense.data.PvpRoundState pvpState =
            WaveDefenseMod.waveManager.getPvpState(location.getName());

        if (pvpState == null ||
            pvpState.getPhase() == com.wavedefense.data.PvpRoundState.Phase.ENDED) {
            // Матч завершено — повертаємо backup (endPvpMatch сам викликає surrenderPlayer,
            // але на всяк випадок якщо гравець відроджується після ENDED)
            WaveDefenseMod.waveManager.surrenderPlayer(player);
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
                // Deathmatch: миттєве відродження на точці спавну команди (не spectator)
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                // Н2: health/food already set at line 81 above — removed duplicate call
                if (teamSpawn != null) {
                    WaveDefenseMod.waveManager.teleportToSpawnPoint(player, teamSpawn);
                }
                // Невразливість на 3 секунди після відродження (60 тіків)
                player.invulnerableTime = 60;
                // Ефект регенерації і вогнестійкості на 3 сек щоб уникнути миттєвої смерті
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 60, 1, false, false));
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.respawn_continue"), true);
            } else if (location.isBattleRoyale()) {
                // BR: теж миттєве відродження, але тільки якщо раунд ще не завершено (один гравець живий)
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                if (teamSpawn != null) {
                    WaveDefenseMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
                }
                // У BR гравець вибуває назавжди після смерті (respawn = виліт з гри)
                // recordDeath вже це обробив — тут просто переміщуємо spectator
                player.setGameMode(GameType.SPECTATOR);
            } else {
                // Standard: spectator на точці спавну
                player.setGameMode(GameType.SPECTATOR);
                if (teamSpawn != null) {
                    WaveDefenseMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
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
                WaveDefenseMod.waveManager.reapplyWaitEffects(player);
            } else {
                player.setGameMode(GameType.SPECTATOR);
            }
            if (teamSpawn != null) {
                WaveDefenseMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
            }
        } else {
            // Fallback: spectator на точці спавну
            player.setGameMode(GameType.SPECTATOR);
            if (teamSpawn != null) {
                WaveDefenseMod.waveManager.teleportToSafeSpawn(player, teamSpawn.getPos(), 0);
            }
        }
    }
}
