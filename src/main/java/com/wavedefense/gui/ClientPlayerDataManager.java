package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundNBT;

public class ClientPlayerDataManager {
    private static PlayerWaveData playerData;

    public static void updateData(CompoundNBT data) {
        if (playerData == null) {
            playerData = new PlayerWaveData();
        }
        playerData.loadClientData(data);
        // Якщо гравець більше не на локації — скидаємо PvP-стан щоб HUD/меню не показували застарілий матч
        if (playerData.getCurrentLocation() == null) {
            ClientPvpStateManager.reset();
        }
    }

    public static PlayerWaveData getPlayerData() {
        return playerData;
    }
}
