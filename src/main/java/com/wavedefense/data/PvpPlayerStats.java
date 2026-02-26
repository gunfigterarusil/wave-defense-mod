package com.wavedefense.data;

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

    public PvpPlayerStats(String playerName, String teamName) {
        this.playerName = playerName;
        this.teamName   = teamName;
    }

    public String getPlayerName() { return playerName; }
    public String getTeamName()   { return teamName; }
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
}
