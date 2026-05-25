package com.wavedefense.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G1 fix: Server-side per-player per-packet-type rate limiter.
 *
 * <p>Prevents DoS via C→S packet flood by silently dropping requests that arrive
 * faster than the configured cooldown for each packet type.
 *
 * <p>Usage in a packet handler:
 * <pre>{@code
 *   if (!PacketRateLimiter.allow(player.getUUID(), MyPacket.class, 3_000L)) return;
 * }</pre>
 *
 * <p>Call {@link #evictPlayer(UUID)} on player disconnect to free memory.
 */
public final class PacketRateLimiter {

    /** key = playerUUID + "#" + packetSimpleName, value = last-allowed timestamp (ms) */
    private static final ConcurrentHashMap<String, Long> lastAllowed = new ConcurrentHashMap<>();

    private PacketRateLimiter() {}

    /**
     * Returns {@code true} and records the current time when the player is allowed
     * to send this packet type; returns {@code false} if still within the cooldown
     * window — the caller should silently drop the packet.
     *
     * @param playerId    the sender's UUID
     * @param packetType  the packet class (used as a discriminator key)
     * @param cooldownMs  minimum interval between allowed packets, in milliseconds
     */
    public static boolean allow(UUID playerId, Class<?> packetType, long cooldownMs) {
        String key = playerId.toString() + '#' + packetType.getSimpleName();
        long now = System.currentTimeMillis();
        Long last = lastAllowed.get(key);
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        lastAllowed.put(key, now);
        return true;
    }

    /**
     * Removes all entries for the given player.
     * Should be called when the player disconnects so the map does not grow unboundedly.
     */
    public static void evictPlayer(UUID playerId) {
        String prefix = playerId.toString() + '#';
        lastAllowed.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
