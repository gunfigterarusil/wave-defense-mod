package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-side renderer that emits particles along the 4 horizontal top edges
 * of a location's bbox so all players inside the location can SEE the boundary
 * (not just the admin who configured it via coordinates).
 *
 * <p>Activates only when the location has both a configured bbox and the
 * {@code bboxOutlineEnabled} toggle on. Particles spawn at low frequency
 * (every 20 server ticks = 1 sec) and only along edges the local player is
 * near (within a 64-block range) to keep network traffic bounded for large
 * boxes.
 */
public final class BboxRenderer {

    private BboxRenderer() {}

    /** Distance from player after which an edge is too far to bother rendering. */
    private static final double RENDER_RANGE = 64.0;
    /** Particle spacing along each edge (one particle every N blocks). */
    private static final int EDGE_SPACING = 3;

    /**
     * Called once per second from {@link WaveManager#onServerTick()}.
     * Walks every active session that has bbox-outline enabled and emits
     * particles along the top edges for nearby players to see.
     */
    public static void tick(WaveContext ctx) {
        if (WaveDefenseMod.getServer() == null) return;
        if (WaveDefenseMod.locationManager == null) return;

        for (LocationSession sess : ctx.sessions.values()) {
            Location loc = WaveDefenseMod.locationManager.getLocation(sess.locationName);
            if (loc == null) continue;
            if (!loc.hasBbox()) continue;
            if (!loc.isBboxOutlineEnabled()) continue;

            List<ServerPlayer> players = ctx.getPlayersInLocation(sess.locationName);
            if (players.isEmpty()) continue;

            ServerLevel world = players.get(0).serverLevel();
            BlockPos low  = loc.getBboxLowCorner();
            BlockPos high = loc.getBboxHighCorner();
            if (low == null || high == null) continue;

            // Y for the top outline — clamp to bbox high
            double y = high.getY() + 0.5;
            double xMin = low.getX() + 0.5;
            double xMax = high.getX() + 0.5;
            double zMin = low.getZ() + 0.5;
            double zMax = high.getZ() + 0.5;

            for (ServerPlayer p : players) {
                emitEdgeNear(p, world, xMin, xMax, zMin, y, true);  // top-Z = zMin edge (north)
                emitEdgeNear(p, world, xMin, xMax, zMax, y, true);  // top-Z = zMax edge (south)
                emitEdgeNear(p, world, zMin, zMax, xMin, y, false); // top-X = xMin edge (west)
                emitEdgeNear(p, world, zMin, zMax, xMax, y, false); // top-X = xMax edge (east)
            }
        }
    }

    /**
     * Emits particles every {@link #EDGE_SPACING} blocks along one straight edge,
     * skipping any particle whose distance from the player exceeds RENDER_RANGE.
     *
     * @param horizontal {@code true} if the edge runs along X (vary X, fix Z);
     *                   {@code false} if it runs along Z (vary Z, fix X)
     */
    private static void emitEdgeNear(ServerPlayer p, ServerLevel world,
                                      double aMin, double aMax, double bFixed,
                                      double y, boolean horizontal) {
        for (double a = aMin; a <= aMax; a += EDGE_SPACING) {
            double px = horizontal ? a : bFixed;
            double pz = horizontal ? bFixed : a;
            double dx = px - p.getX();
            double dy = y  - p.getY();
            double dz = pz - p.getZ();
            if (dx * dx + dy * dy + dz * dz > RENDER_RANGE * RENDER_RANGE) continue;
            // sendParticles to just this player (4th-arg single-player form would be cleaner
            // but ServerLevel.sendParticles broadcasts to a sphere — fine here, we'll let
            // engine de-dupe with the radius=8 limit).
            world.sendParticles(p, ParticleTypes.END_ROD,
                true, px, y, pz, 1, 0, 0, 0, 0.0);
        }
    }
}
