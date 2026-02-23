package com.wavedefense.gui;

import com.wavedefense.data.GameStats;
import net.minecraft.nbt.CompoundTag;

public class ClientStatsManager {
    private static GameStats currentStats;

    public static void updateStats(CompoundTag data) {
        currentStats = GameStats.load(data);
    }

    public static GameStats getStats() {
        return currentStats;
    }
}
