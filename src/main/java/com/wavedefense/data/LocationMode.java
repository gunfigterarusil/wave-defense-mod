package com.wavedefense.data;

/**
 * Режим локації: PvE (захист від мобів) або PvP (гравці проти гравців).
 */
public enum LocationMode {
    PVE,
    PVP;

    public static LocationMode fromString(String s) {
        try { return valueOf(s); } catch (Exception e) { return PVE; }
    }
}
