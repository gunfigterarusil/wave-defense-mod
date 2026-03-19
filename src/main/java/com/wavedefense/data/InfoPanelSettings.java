package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Налаштування інфо-панелі локації.
 * Два режими:
 *   1. spawnInfoPanel — TextDisplay над точкою спавну гравців (загальна статистика)
 *   2. mobSpawnPanel  — TextDisplay над кожною точкою спавну мобів (таймер до хвилі)
 */
public class InfoPanelSettings {

    // ── Панель гравця (над точкою спавну) ────────────────────────────────
    private boolean spawnPanelEnabled   = false;   // вмикач
    private float   spawnPanelOffsetY   = 2.5f;    // висота над блоком спавну

    // Що відображати на панелі гравця
    private boolean showPlayerCount     = true;    // кількість гравців у локації
    private boolean showWaveNumber      = true;    // поточна / загальна хвиль (напр. «Хвиля 3/10»)
    private boolean showWaveTimer       = true;    // таймер до наступної хвилі
    private boolean showMobsRemaining   = true;    // кількість мобів що залишились
    private boolean showSecretCount     = false;   // кількість прихованих (тригерних) хвиль
    private boolean showShopSecrets     = false;   // кількість товарів із умовою доступності
    private boolean showPoints          = false;   // поінти гравця
    // Нові поля
    private boolean showFirstWaveTimer  = true;    // таймер до початку першої хвилі (після запуску)
    private boolean showLobbyTimer      = true;    // таймер до запуску локації (на панелі зони входу)

    // ── Панель точок спавну мобів ─────────────────────────────────────────
    private boolean mobSpawnPanelEnabled = false;  // вмикач
    private float   mobSpawnOffsetY      = 2.5f;   // висота над блоком спавну мобів

    // Що відображати над кожною точкою спавну мобів
    private boolean mobShowWaveTimer     = true;   // таймер до наступної хвилі
    private boolean mobShowWaveNumber    = false;  // поточна хвиля
    private boolean mobShowMobCount      = false;  // скільки мобів тут спавниться

    // ── Стиль тексту ──────────────────────────────────────────────────────
    // Колір тексту у форматі 0xRRGGBB (без альфа-каналу)
    private int  textColor          = 0xFFFFFF;   // білий
    private int  backgroundColor    = 0x7F000000; // напівпрозорий чорний (або 0 = прозорий)
    private boolean hasShadow       = true;
    private float   textScale       = 0.5f;        // масштаб тексту (0.1 – 2.0)

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
