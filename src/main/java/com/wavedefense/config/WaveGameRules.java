package com.wavedefense.config;

import net.minecraft.server.level.ServerPlayer;

/**
 * Керує дозволом входу на локації для звичайних гравців.
 *
 * В Forge 1.20.1 (47.x) RegisterGameRulesEvent не існує, а
 * GameRules.register() є @Internal API, тому ми реалізуємо
 * аналог через статичне поле + команду /wavedefense entry on|off.
 *
 * Адміни (permission >= 2) ігнорують це налаштування.
 */
public class WaveGameRules {

    private static boolean locationEntryAllowed = true;

    public static boolean isLocationEntryAllowed() {
        return locationEntryAllowed;
    }

    public static void setLocationEntryAllowed(boolean allowed) {
        locationEntryAllowed = allowed;
    }

    /**
     * Перевіряє чи може гравець увійти на локацію.
     * Адміни (permission >= 2) — завжди так.
     */
    public static boolean isLocationEntryAllowed(ServerPlayer player) {
        if (player.hasPermissions(2)) return true;
        return locationEntryAllowed;
    }
}
