package com.wavedefense.config;

import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * Керує дозволом входу на локації для звичайних гравців.
 *
 * В Forge 1.20.1 (47.x) RegisterGameRulesEvent не існує, а
 * GameRules.register() є @Internal API, тому ми реалізуємо
 * аналог через статичне поле + команду /wavedefense entry on|off.
 *
 * ВАЖЛИВО: перевірка hasPermissions(2) НЕ застосовується для обходу заборони,
 * бо creative-гравці на деяких серверах мають permission 2 але не є адмінами.
 * Адміни замість цього використовують /wavedefense tp для примусового входу.
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
     * Блокування поширюється на ВСІХ гравців включно з адмінами,
     * що дозволяє адміністратору повністю зупинити вхід перед подією.
     * Адміни можуть обійти через /wavedefense tp.
     */
    public static boolean isLocationEntryAllowed(ServerPlayerEntity player) {
        return locationEntryAllowed;
    }
}
