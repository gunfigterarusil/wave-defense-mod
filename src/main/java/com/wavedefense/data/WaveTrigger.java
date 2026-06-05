package com.wavedefense.data;

/**
 * Triggers for waves and location activation.
 * Extended set compared to LootSpawn.Trigger — includes player actions
 * (open chest, open door, hold resources, etc.).
 */
public enum WaveTrigger {

    // ── Base (waves) ─────────────────────────────────────────────────
    WAVE_COMPLETE       ("wavedefense.trigger.wave.wave_complete",       true,  true,
        "wavedefense.trigger.wave.wave_complete.tip"),
    MOBS_REMAINING_LOW  ("wavedefense.trigger.wave.mobs_remaining_low",  true,  false,
        "wavedefense.trigger.wave.mobs_remaining_low.tip"),
    TIMER_60            ("wavedefense.trigger.wave.timer_60",            true,  true,
        "wavedefense.trigger.wave.timer_60.tip"),
    TIMER_120           ("wavedefense.trigger.wave.timer_120",           true,  true,
        "wavedefense.trigger.wave.timer_120.tip"),
    TIMER_300           ("wavedefense.trigger.wave.timer_300",           true,  true,
        "wavedefense.trigger.wave.timer_300.tip"),
    PLAYER_JOIN         ("wavedefense.trigger.wave.player_join",         true,  true,
        "wavedefense.trigger.wave.player_join.tip"),
    PLAYER_DEATH        ("wavedefense.trigger.wave.player_death",        true,  true,
        "wavedefense.trigger.wave.player_death.tip"),
    WAVES_SURVIVED_5    ("wavedefense.trigger.wave.waves_survived_5",    true,  false,
        "wavedefense.trigger.wave.waves_survived_5.tip"),
    WAVES_SURVIVED_10   ("wavedefense.trigger.wave.waves_survived_10",   true,  false,
        "wavedefense.trigger.wave.waves_survived_10.tip"),
    WAVES_SURVIVED_20   ("wavedefense.trigger.wave.waves_survived_20",   true,  false,
        "wavedefense.trigger.wave.waves_survived_20.tip"),
    MOBS_KILLED_10      ("wavedefense.trigger.wave.mobs_killed_10",      true,  false,
        "wavedefense.trigger.wave.mobs_killed_10.tip"),
    MOBS_KILLED_50      ("wavedefense.trigger.wave.mobs_killed_50",      true,  false,
        "wavedefense.trigger.wave.mobs_killed_50.tip"),
    MOBS_KILLED_100     ("wavedefense.trigger.wave.mobs_killed_100",     true,  false,
        "wavedefense.trigger.wave.mobs_killed_100.tip"),

    // ── Player actions ───────────────────────────────────────────────
    PLAYER_OPEN_CHEST   ("wavedefense.trigger.wave.player_open_chest",   true,  true,
        "wavedefense.trigger.wave.player_open_chest.tip"),
    PLAYER_OPEN_DOOR    ("wavedefense.trigger.wave.player_open_door",    true,  true,
        "wavedefense.trigger.wave.player_open_door.tip"),
    PLAYER_FULL_INVENT  ("wavedefense.trigger.wave.player_full_invent",  true,  true,
        "wavedefense.trigger.wave.player_full_invent.tip"),
    PLAYER_HAS_DIAMOND  ("wavedefense.trigger.wave.player_has_diamond",  true,  true,
        "wavedefense.trigger.wave.player_has_diamond.tip"),
    PLAYER_HAS_IRON     ("wavedefense.trigger.wave.player_has_iron",     true,  true,
        "wavedefense.trigger.wave.player_has_iron.tip"),
    PLAYER_HAS_SWORD    ("wavedefense.trigger.wave.player_has_sword",    true,  true,
        "wavedefense.trigger.wave.player_has_sword.tip"),
    PLAYER_HAS_ITEM     ("wavedefense.trigger.wave.player_has_item",     true,  true,
        "wavedefense.trigger.wave.player_has_item.tip"),
    PLAYER_LOW_HEALTH   ("wavedefense.trigger.wave.player_low_health",   true,  true,
        "wavedefense.trigger.wave.player_low_health.tip"),
    PLAYER_ENTER_ZONE   ("wavedefense.trigger.wave.player_enter_zone",   true,  true,
        "wavedefense.trigger.wave.player_enter_zone.tip"),

    // ── PvP-specific ─────────────────────────────────────────────────
    ROUND_START         ("wavedefense.trigger.wave.round_start",         false,  true,
        "wavedefense.trigger.wave.round_start.tip"),
    ROUND_END           ("wavedefense.trigger.wave.round_end",           false,  true,
        "wavedefense.trigger.wave.round_end.tip"),
    BUY_PHASE           ("wavedefense.trigger.wave.buy_phase",           false,  true,
        "wavedefense.trigger.wave.buy_phase.tip"),
    TEAM_WIPE           ("wavedefense.trigger.wave.team_wipe",           false,  true,
        "wavedefense.trigger.wave.team_wipe.tip"),
    KILL_STREAK_3       ("wavedefense.trigger.wave.kill_streak_3",       false,  true,
        "wavedefense.trigger.wave.kill_streak_3.tip"),

    // ── Custom-value triggers ────────────────────────────────────────
    TIMER_CUSTOM        ("wavedefense.trigger.wave.timer_custom",        true,  false,
        "wavedefense.trigger.wave.timer_custom.tip"),
    MOBS_KILLED_N       ("wavedefense.trigger.wave.mobs_killed_n",       true,  false,
        "wavedefense.trigger.wave.mobs_killed_n.tip"),
    WAVES_SURVIVED_N    ("wavedefense.trigger.wave.waves_survived_n",    true,  false,
        "wavedefense.trigger.wave.waves_survived_n.tip"),

    // ── Shop ─────────────────────────────────────────────────────────
    SHOP_WAVE_START     ("wavedefense.trigger.wave.shop_wave_start",     true,  false,
        "wavedefense.trigger.wave.shop_wave_start.tip"),
    SHOP_WAVE_N         ("wavedefense.trigger.wave.shop_wave_n",         true,  false,
        "wavedefense.trigger.wave.shop_wave_n.tip"),
    SHOP_LOCATION_START ("wavedefense.trigger.wave.shop_location_start", true,  false,
        "wavedefense.trigger.wave.shop_location_start.tip"),
    SHOP_PLAYER_HAS_ITEM("wavedefense.trigger.wave.shop_player_has_item",true,  false,
        "wavedefense.trigger.wave.shop_player_has_item.tip");

    public final String  label;
    public final boolean pve;
    public final boolean pvp;
    public final String  tooltip;

    WaveTrigger(String label, boolean pve, boolean pvp, String tooltip) {
        this.label   = label;
        this.pve     = pve;
        this.pvp     = pvp;
        this.tooltip = tooltip;
    }
}
