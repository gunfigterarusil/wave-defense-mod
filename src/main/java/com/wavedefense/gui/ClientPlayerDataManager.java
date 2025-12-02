package com.wavedefense.gui;

import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;

public class ClientPlayerDataManager {
    private static PlayerWaveData playerData;

    public static void updateData(CompoundTag data) {
        if (playerData == null) {
            playerData = new PlayerWaveData();
        }
        playerData.loadClientData(data);
    }

    public static PlayerWaveData getPlayerData() {
        return playerData;
    }
}
