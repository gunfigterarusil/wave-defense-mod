package com.wavedefense.gui;

import com.wavedefense.data.GameStats;
import net.minecraft.nbt.CompoundNBT;

public class ClientStatsManager {
    private static GameStats currentStats;

    public static void updateStats(CompoundNBT data) {
        currentStats = GameStats.load(data);
    }

    public static GameStats getStats() {
        return currentStats;
    }
}
