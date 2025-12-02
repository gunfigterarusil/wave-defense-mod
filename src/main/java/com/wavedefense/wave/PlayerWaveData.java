package com.wavedefense.wave;

import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PlayerWaveData {
    private Location currentLocation;
    private int currentWave;
    private long waveStartTime;
    private int timeUntilNextWave;
    private boolean isTimerActive;
    private List<ItemStack> originalInventory;
    private BlockPos originalPos;

    // Player settings
    private boolean showTimer = true;
    private boolean showNotifications = true;

    public boolean isInWave() {
        return currentLocation != null;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void setCurrentWave(int currentWave) {
        this.currentWave = currentWave;
    }

    public long getWaveStartTime() {
        return waveStartTime;
    }

    public void setWaveStartTime(long waveStartTime) {
        this.waveStartTime = waveStartTime;
    }

    public int getTimeUntilNextWave() {
        return timeUntilNextWave;
    }

    public void setTimeUntilNextWave(int timeUntilNextWave) {
        this.timeUntilNextWave = timeUntilNextWave;
    }

    public boolean isTimerActive() {
        return isTimerActive;
    }

    public void setTimerActive(boolean timerActive) {
        isTimerActive = timerActive;
    }

    public List<ItemStack> getOriginalInventory() {
        return originalInventory;
    }

    public void setOriginalInventory(List<ItemStack> originalInventory) {
        this.originalInventory = originalInventory;
    }

    public BlockPos getOriginalPos() {
        return originalPos;
    }

    public void setOriginalPos(BlockPos originalPos) {
        this.originalPos = originalPos;
    }

    public boolean isShowTimer() {
        return showTimer;
    }

    public void setShowTimer(boolean showTimer) {
        this.showTimer = showTimer;
    }

    public boolean isShowNotifications() {
        return showNotifications;
    }

    public void setShowNotifications(boolean showNotifications) {
        this.showNotifications = showNotifications;
    }
}
