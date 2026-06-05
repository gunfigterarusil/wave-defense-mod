package com.wavedefense.network;

import java.util.UUID;

/**
 * 1.16.5 STUB — PacketRateLimiter not ported in this version.
 *
 * <p>Keeping only the public API surface that EventHandler references.
 * No actual rate limiting is performed.
 */
public class PacketRateLimiter {
    public static void evictPlayer(UUID id) { /* no-op */ }
    /** Stub: always allow (no rate limiting on 1.16.5). */
    public static boolean allow(UUID id, Class<?> packet, long minIntervalMs) { return true; }
}
