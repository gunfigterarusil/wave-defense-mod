package com.wavedefense.events;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Обробник респавну після смерті.
 * У PvP — гравець відроджується в спектаторі (чекає наступного раунду).
 * Це спрацьовує ПІСЛЯ onPvpPlayerDeath в EventHandler,
 * тому gamemode вже SPECTATOR — просто підтверджуємо.
 */
@Mod.EventBusSubscriber(modid = WaveDefenseMod.MODID)
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(player.getUUID());
        if (data == null || data.getCurrentLocation() == null) return;

        // PvP: підтверджуємо спектатор, тіло залишається де померло (Minecraft за замовчуванням)
        if (data.isInPvp() && data.getCurrentLocation().isPvp()) {
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR) {
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            }
        }
    }
}
