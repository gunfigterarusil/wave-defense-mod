package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;

public class ClientPlayerDataManager {
    private static PlayerWaveData playerData;

    /** Client ticks elapsed since the last server sync — drives local countdown. */
    private static int interpolationTicks = 0;

    public static void updateData(CompoundTag data) {
        if (playerData == null) {
            playerData = new PlayerWaveData();
        }
        playerData.loadClientData(data);
        // Server value is authoritative: restart the local interpolation window so
        // the client never drifts more than one second away from the real timer.
        interpolationTicks = 0;
        // Якщо гравець більше не на локації — скидаємо PvP-стан щоб HUD/меню не показували застарілий матч
        if (playerData.getCurrentLocation() == null) {
            ClientPvpStateManager.reset();
        }
    }

    /**
     * Counts the next-wave timer down locally, once per second, between the
     * server's 1 Hz syncs.
     *
     * <p>The server remains the source of truth ({@link #updateData} resets the
     * window on every packet); this only fills the gaps so the HUD keeps moving
     * if a sync packet is late, and never appears frozen.
     *
     * <p>Called from {@code ClientEventHandler.onClientTick}.
     */
    public static void tickClient() {
        if (playerData == null || !playerData.isTimerActive()) return;
        int secondsLeft = playerData.getTimeUntilNextWave();
        if (secondsLeft <= 0) return;

        if (++interpolationTicks >= 20) {
            interpolationTicks = 0;
            playerData.setTimeUntilNextWave(secondsLeft - 1);
        }
    }

    public static PlayerWaveData getPlayerData() {
        return playerData;
    }
}
