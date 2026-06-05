package com.wavedefense.data;

/**
 * Location mode: PvE (mob defence) or PvP (players vs players).
 */
public enum LocationMode {
    PVE,
    PVP;

    public static LocationMode fromString(String s) {
        try { return valueOf(s); } catch (Exception e) { return PVE; }
    }
}
