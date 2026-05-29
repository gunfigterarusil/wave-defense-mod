package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Info-panel settings for a location.
 * Two modes:
 *   1. spawnInfoPanel — TextDisplay above the player spawn point (general stats)
 *   2. mobSpawnPanel  — TextDisplay above each mob spawn point (wave timer)
 */
public class InfoPanelSettings {

    // ── Player panel (above spawn point) ─────────────────────────────────
    private boolean spawnPanelEnabled   = false;   // toggle
    private float   spawnPanelOffsetY   = 2.5f;    // height above the spawn block

    // What to display on the player panel
    private boolean showPlayerCount     = true;    // number of players in the location
    private boolean showWaveNumber      = true;    // current / total waves (e.g. "Wave 3/10")
    private boolean showWaveTimer       = true;    // countdown to next wave
    private boolean showMobsRemaining   = true;    // mobs still alive
    private boolean showSecretCount     = false;   // number of hidden (trigger) waves
    private boolean showShopSecrets     = false;   // number of items with an availability condition
    private boolean showPoints          = false;   // player's points
    private boolean showFirstWaveTimer  = true;    // countdown to the first wave after lobby ends
    private boolean showLobbyTimer      = true;    // countdown until the location starts (lobby zone panel)

    // ── Mob spawn panels ──────────────────────────────────────────────────
    private boolean mobSpawnPanelEnabled = false;  // toggle
    private float   mobSpawnOffsetY      = 2.5f;   // height above the mob spawn block

    // What to display above each mob spawn point
    private boolean mobShowWaveTimer     = true;   // countdown to next wave
    private boolean mobShowWaveNumber    = false;  // current wave number
    private boolean mobShowMobCount      = false;  // how many mobs spawn here

    // ── Text style ────────────────────────────────────────────────────────
    // Text colour in 0xRRGGBB format (no alpha channel)
    private int  textColor          = 0xFFFFFF;   // white
    private int  backgroundColor    = 0x7F000000; // semi-transparent black (0 = transparent)
    private boolean hasShadow       = true;
    private float   textScale       = 0.5f;        // text scale (0.1 – 2.0)

    // ═══════════════════════ Accessors ═══════════════════════════════════

    // Spawn panel
    public boolean isSpawnPanelEnabled()             { return spawnPanelEnabled; }
    public void    setSpawnPanelEnabled(boolean v)   { this.spawnPanelEnabled = v; }
    public float   getSpawnPanelOffsetY()            { return spawnPanelOffsetY; }
    public void    setSpawnPanelOffsetY(float v)     { this.spawnPanelOffsetY = Math.max(0.5f, Math.min(10f, v)); }

    public boolean isShowPlayerCount()               { return showPlayerCount; }
    public void    setShowPlayerCount(boolean v)     { this.showPlayerCount = v; }
    public boolean isShowWaveNumber()                { return showWaveNumber; }
    public void    setShowWaveNumber(boolean v)      { this.showWaveNumber = v; }
    public boolean isShowWaveTimer()                 { return showWaveTimer; }
    public void    setShowWaveTimer(boolean v)       { this.showWaveTimer = v; }
    public boolean isShowMobsRemaining()             { return showMobsRemaining; }
    public void    setShowMobsRemaining(boolean v)   { this.showMobsRemaining = v; }
    public boolean isShowSecretCount()               { return showSecretCount; }
    public void    setShowSecretCount(boolean v)     { this.showSecretCount = v; }
    public boolean isShowShopSecrets()               { return showShopSecrets; }
    public void    setShowShopSecrets(boolean v)     { this.showShopSecrets = v; }
    public boolean isShowPoints()                    { return showPoints; }
    public void    setShowPoints(boolean v)          { this.showPoints = v; }
    public boolean isShowFirstWaveTimer()            { return showFirstWaveTimer; }
    public void    setShowFirstWaveTimer(boolean v)  { this.showFirstWaveTimer = v; }
    public boolean isShowLobbyTimer()                { return showLobbyTimer; }
    public void    setShowLobbyTimer(boolean v)      { this.showLobbyTimer = v; }

    // Mob spawn panel
    public boolean isMobSpawnPanelEnabled()          { return mobSpawnPanelEnabled; }
    public void    setMobSpawnPanelEnabled(boolean v){ this.mobSpawnPanelEnabled = v; }
    public float   getMobSpawnOffsetY()              { return mobSpawnOffsetY; }
    public void    setMobSpawnOffsetY(float v)       { this.mobSpawnOffsetY = Math.max(0.5f, Math.min(10f, v)); }
    public boolean isMobShowWaveTimer()              { return mobShowWaveTimer; }
    public void    setMobShowWaveTimer(boolean v)    { this.mobShowWaveTimer = v; }
    public boolean isMobShowWaveNumber()             { return mobShowWaveNumber; }
    public void    setMobShowWaveNumber(boolean v)   { this.mobShowWaveNumber = v; }
    public boolean isMobShowMobCount()               { return mobShowMobCount; }
    public void    setMobShowMobCount(boolean v)     { this.mobShowMobCount = v; }

    // Style
    public int     getTextColor()                    { return textColor; }
    public void    setTextColor(int v)               { this.textColor = v; }
    public int     getBackgroundColor()              { return backgroundColor; }
    public void    setBackgroundColor(int v)         { this.backgroundColor = v; }
    public boolean isHasShadow()                     { return hasShadow; }
    public void    setHasShadow(boolean v)           { this.hasShadow = v; }
    public float   getTextScale()                    { return textScale; }
    public void    setTextScale(float v)             { this.textScale = Math.max(0.1f, Math.min(2f, v)); }

    // ═══════════════════════ NBT ══════════════════════════════════════════

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("spawnPanelEnabled",    spawnPanelEnabled);
        tag.putFloat  ("spawnPanelOffsetY",    spawnPanelOffsetY);
        tag.putBoolean("showPlayerCount",      showPlayerCount);
        tag.putBoolean("showWaveNumber",       showWaveNumber);
        tag.putBoolean("showWaveTimer",        showWaveTimer);
        tag.putBoolean("showMobsRemaining",    showMobsRemaining);
        tag.putBoolean("showSecretCount",      showSecretCount);
        tag.putBoolean("showShopSecrets",      showShopSecrets);
        tag.putBoolean("showPoints",           showPoints);
        tag.putBoolean("showFirstWaveTimer",  showFirstWaveTimer);
        tag.putBoolean("showLobbyTimer",       showLobbyTimer);

        tag.putBoolean("mobSpawnPanelEnabled", mobSpawnPanelEnabled);
        tag.putFloat  ("mobSpawnOffsetY",      mobSpawnOffsetY);
        tag.putBoolean("mobShowWaveTimer",     mobShowWaveTimer);
        tag.putBoolean("mobShowWaveNumber",    mobShowWaveNumber);
        tag.putBoolean("mobShowMobCount",      mobShowMobCount);

        tag.putInt    ("textColor",            textColor);
        tag.putInt    ("backgroundColor",      backgroundColor);
        tag.putBoolean("hasShadow",            hasShadow);
        tag.putFloat  ("textScale",            textScale);
        return tag;
    }

    public static InfoPanelSettings load(CompoundTag tag) {
        InfoPanelSettings s = new InfoPanelSettings();
        s.spawnPanelEnabled    = tag.contains("spawnPanelEnabled")    && tag.getBoolean("spawnPanelEnabled");
        s.spawnPanelOffsetY    = tag.contains("spawnPanelOffsetY")    ? tag.getFloat("spawnPanelOffsetY")    : 2.5f;
        s.showPlayerCount      = !tag.contains("showPlayerCount")     || tag.getBoolean("showPlayerCount");
        s.showWaveNumber       = !tag.contains("showWaveNumber")      || tag.getBoolean("showWaveNumber");
        s.showWaveTimer        = !tag.contains("showWaveTimer")       || tag.getBoolean("showWaveTimer");
        s.showMobsRemaining    = !tag.contains("showMobsRemaining")   || tag.getBoolean("showMobsRemaining");
        s.showSecretCount      = tag.contains("showSecretCount")      && tag.getBoolean("showSecretCount");
        s.showShopSecrets      = tag.contains("showShopSecrets")      && tag.getBoolean("showShopSecrets");
        s.showPoints           = tag.contains("showPoints")           && tag.getBoolean("showPoints");
        s.showFirstWaveTimer   = !tag.contains("showFirstWaveTimer")  || tag.getBoolean("showFirstWaveTimer");
        s.showLobbyTimer       = !tag.contains("showLobbyTimer")      || tag.getBoolean("showLobbyTimer");

        s.mobSpawnPanelEnabled = tag.contains("mobSpawnPanelEnabled") && tag.getBoolean("mobSpawnPanelEnabled");
        s.mobSpawnOffsetY      = tag.contains("mobSpawnOffsetY")      ? tag.getFloat("mobSpawnOffsetY")      : 2.5f;
        s.mobShowWaveTimer     = !tag.contains("mobShowWaveTimer")    || tag.getBoolean("mobShowWaveTimer");
        s.mobShowWaveNumber    = tag.contains("mobShowWaveNumber")    && tag.getBoolean("mobShowWaveNumber");
        s.mobShowMobCount      = tag.contains("mobShowMobCount")      && tag.getBoolean("mobShowMobCount");

        s.textColor            = tag.contains("textColor")            ? tag.getInt("textColor")              : 0xFFFFFF;
        s.backgroundColor      = tag.contains("backgroundColor")      ? tag.getInt("backgroundColor")        : 0x7F000000;
        s.hasShadow            = !tag.contains("hasShadow")           || tag.getBoolean("hasShadow");
        s.textScale            = tag.contains("textScale")            ? tag.getFloat("textScale")            : 0.5f;
        return s;
    }
}
