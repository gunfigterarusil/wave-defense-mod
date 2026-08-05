package com.wavedefense.wave;

import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class PlayerWaveData {
    private UUID playerUUID;
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
    private boolean showTeammates = true;
    /**
     * Whether this player receives boundary particles. Opt-out exists because the effect
     * is the single heaviest client-side thing the mod draws, and players on weaker
     * machines should not have to leave the arena to get their frame rate back.
     */
    private boolean boundaryParticlesVisible = true;

    // Лічильник мобів/ворогів поточної хвилі (синхронізується з сервера)
    private int mobsRemaining = 0;
    // PvP режим
    private boolean isInPvp = false;
    // Таймер перемоги (секунди що залишились до виходу після перемоги), 0 = не активний
    private int victoryCountdownSec = 0;
    // Поінти гравця на поточній локації (синхронізуються з сервера)
    private int playerPoints = 0;

    public int getMobsRemaining() { return mobsRemaining; }
    public void setMobsRemaining(int mobsRemaining) { this.mobsRemaining = mobsRemaining; }
    public boolean isInPvp() { return isInPvp; }
    public void setInPvp(boolean inPvp) { this.isInPvp = inPvp; }
    public int  getVictoryCountdownSec() { return victoryCountdownSec; }
    public void setVictoryCountdownSec(int v) { this.victoryCountdownSec = Math.max(0, v); }
    public int  getPlayerPoints()  { return playerPoints; }
    public void setPlayerPoints(int p) { this.playerPoints = Math.max(0, p); }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

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

    public boolean isShowTeammates() { return showTeammates; }
    public void setShowTeammates(boolean v) { this.showTeammates = v; }

    public boolean isBoundaryParticlesVisible() { return boundaryParticlesVisible; }
    public void setBoundaryParticlesVisible(boolean v) { this.boundaryParticlesVisible = v; }

    public CompoundTag saveClientData() {
        CompoundTag tag = new CompoundTag();
        if (currentLocation != null) tag.put("location", currentLocation.save());
        tag.putInt("currentWave", currentWave);
        tag.putInt("timeUntilNextWave", timeUntilNextWave);
        tag.putBoolean("isTimerActive", isTimerActive);
        tag.putBoolean("showTimer", showTimer);
        tag.putBoolean("showTeammates", showTeammates);
        tag.putBoolean("boundaryParticlesVisible", boundaryParticlesVisible);
        tag.putInt("mobsRemaining", mobsRemaining);
        tag.putBoolean("isInPvp", isInPvp);
        tag.putInt("victoryCountdownSec", victoryCountdownSec);
        tag.putInt("playerPoints", playerPoints);
        return tag;
    }

    public void loadClientData(CompoundTag tag) {
        // ВАЖЛИВО: завжди скидаємо location на null якщо ключа нема —
        // це сигнал "гравець вийшов з локації" (порожній пакет від surrenderPlayer)
        if (tag.contains("location")) {
            this.currentLocation = Location.load(tag.getCompound("location"));
        } else {
            this.currentLocation = null;
        }
        this.currentWave = tag.getInt("currentWave");
        this.timeUntilNextWave = tag.getInt("timeUntilNextWave");
        this.isTimerActive = tag.getBoolean("isTimerActive");
        this.showTimer = !tag.contains("showTimer") || tag.getBoolean("showTimer");
        this.showTeammates = !tag.contains("showTeammates") || tag.getBoolean("showTeammates");
        // Absent key means an older save — default to visible, matching previous behaviour.
        this.boundaryParticlesVisible = !tag.contains("boundaryParticlesVisible")
            || tag.getBoolean("boundaryParticlesVisible");
        this.mobsRemaining = tag.contains("mobsRemaining") ? tag.getInt("mobsRemaining") : 0;
        this.isInPvp = tag.contains("isInPvp") && tag.getBoolean("isInPvp");
        this.victoryCountdownSec = tag.contains("victoryCountdownSec") ? tag.getInt("victoryCountdownSec") : 0;
        this.playerPoints = tag.contains("playerPoints") ? tag.getInt("playerPoints") : 0;
    }
}
