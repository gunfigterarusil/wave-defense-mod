package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;

/**
 * Статистика гравця для PvP сесії: кіли, смерті, асисти.
 * Зберігається в PvpRoundState на час сесії.
 */
public class PvpPlayerStats {
    private String playerName = "";
    private String teamName   = "";
    private int kills         = 0;
    private int deaths        = 0;
    private int assists       = 0;

    public PvpPlayerStats() {
        // For loading from NBT
    }

    public PvpPlayerStats(String playerName, String teamName) {
        this.playerName = playerName;
        this.teamName   = teamName;
    }

    public String getPlayerName() { return playerName; }
    public String getTeamName()   { return teamName; }
    public void   setTeamName(String t) { this.teamName = t; }
    public int getKills()         { return kills; }
    public int getDeaths()        { return deaths; }
    public int getAssists()       { return assists; }

    public void addKill()   { kills++; }
    public void addDeath()  { deaths++; }
    public void addAssist() { assists++; }

    /** K/D ratio */
    public float getKD() {
        return deaths == 0 ? kills : (float) kills / deaths;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ──────────────────────────────────────────────────────────────────────

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("playerName", playerName);
        tag.putString("teamName", teamName);
        tag.putInt("kills", kills);
        tag.putInt("deaths", deaths);
        tag.putInt("assists", assists);
        return tag;
    }

    public void load(CompoundNBT tag) {
        playerName = tag.getString("playerName");
        teamName = tag.getString("teamName");
        kills = tag.getInt("kills");
        deaths = tag.getInt("deaths");
        assists = tag.getInt("assists");
    }
}
