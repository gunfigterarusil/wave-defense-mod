package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.SyncCtpStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Handles Capture the Point (CtP) and King of the Hill (KotH) game logic.
 *
 * Called every server tick from {@link WaveManager#onServerTick()}.
 *
 * Responsibilities:
 * - Detect players standing inside capture-point radius
 * - Advance / freeze / reverse capture progress
 * - Award objective score to owning teams every second
 * - Check win conditions and declare a winner via {@link PvpRoundManager}
 * - Spawn visual particles around each point
 * - Send {@link SyncCtpStatePacket} to players every second
 */
public class CapturePointManager {

    private final WaveContext ctx;

    public CapturePointManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Main tick
    // ════════════════════════════════════════════════════════════════════

    public void tick(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;
        if (WaveDefenseMod.locationManager == null) return;

        for (Map.Entry<String, LocationSession> entry : ctx.sessions.entrySet()) {
            String locName   = entry.getKey();
            LocationSession  sess    = entry.getValue();
            PvpRoundState    state   = sess.pvpState;
            if (state == null) continue;
            if (state.getPhase() != PvpRoundState.Phase.ACTIVE) continue;

            Location location = WaveDefenseMod.locationManager.getLocation(locName);
            if (location == null) continue;
            if (!location.isObjectiveMode()) continue;

            List<CapturePoint> points = location.getCapturePoints();
            if (points.isEmpty()) continue;

            List<ServerPlayer> players = wm.getPlayersInLocation(locName);
            ServerLevel world = players.isEmpty()
                ? WaveDefenseMod.getServer().overworld()
                : players.get(0).serverLevel();

            // ── Per-point logic ──────────────────────────────────────────
            for (CapturePoint cp : points) {
                tickPoint(wm, location, state, cp, players);
            }

            // ── B4: capture-all-points win condition (CtP only) ──────────
            if (location.isCtpMode() && location.isCtpCaptureAllWin()) {
                String allOwner = null;
                boolean allSame = true;
                for (CapturePoint cp : points) {
                    String owner = state.getPointOwner(cp.getId());
                    if (owner == null) { allSame = false; break; }
                    if (allOwner == null) allOwner = owner;
                    else if (!allOwner.equals(owner)) { allSame = false; break; }
                }
                if (allSame && allOwner != null) {
                    wm.broadcastToLocation(location.getName(),
                        Component.translatable("wavedefense.msg.ctp_all_points_captured", allOwner));
                    wm.pvpMgr.declareObjectiveWinner(wm, location, state, allOwner, false);
                    continue;
                }
            }

            // ── KotH Hold-Timer mode (overrides score-based win conditions) ─
            boolean kothHold = location.isKothMode() && location.isKothHoldMode();

            if (kothHold) {
                // Every tick: increment hold timer for the team currently owning the hill
                for (CapturePoint cp : points) {
                    String owner = state.getPointOwner(cp.getId());
                    if (owner != null) {
                        state.addKothHoldTicks(owner, 1);
                    }
                }

                // Sync every second + check win condition
                if (wm.waveCtx.tickCounter % 20 == 0) {
                    int target = location.getKothHoldDurationSec() * 20;
                    String winner = null;
                    for (Map.Entry<String, Integer> e : state.getKothHoldTicks().entrySet()) {
                        if (e.getValue() >= target) { winner = e.getKey(); break; }
                    }
                    if (winner != null) {
                        wm.pvpMgr.declareObjectiveWinner(wm, location, state, winner, false);
                        continue;
                    }
                    sendSync(wm, location, state);
                }
            } else {
                // ── Score modes (CtP + KotH score-based) ─────────────────
                // ── Award score every second (20 ticks) ───────────────────
                if (wm.waveCtx.tickCounter % 20 == 0) {
                    for (CapturePoint cp : points) {
                        String owner = state.getPointOwner(cp.getId());
                        if (owner != null) {
                            state.addObjectiveScore(owner, location.getObjectiveScorePerSec());
                        }
                    }

                    // ── Win condition: first-to-score ─────────────────────
                    if (location.isObjectiveFirstToScore()) {
                        String winner = state.checkObjectiveWinner(location.getObjectiveScoreToWin());
                        if (winner != null) {
                            wm.pvpMgr.declareObjectiveWinner(wm, location, state, winner, false);
                            continue;
                        }
                    }

                    // ── Sync state to clients every second ────────────────
                    sendSync(wm, location, state);
                }

                // ── Timer countdown (timer mode) ─────────────────────────
                if (!location.isObjectiveFirstToScore()) {
                    int remaining = state.getRoundDurationTicks();
                    if (remaining > 0) {
                        state.setRoundDurationTicks(remaining - 1);
                        if (remaining - 1 <= 0) {
                            String winner = state.getLeadingTeam();
                            wm.pvpMgr.declareObjectiveWinner(wm, location, state, winner, true);
                            continue;
                        }
                    }
                }
            }

            // ── Spawn particles every 10 ticks ────────────────────────────
            if (wm.waveCtx.tickCounter % 10 == 0) {
                for (CapturePoint cp : points) {
                    spawnPointParticles(world, cp.getPos(), cp.getCaptureRadius(),
                        cp.getParticleType(), cp.getParticleCount());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Per-point tick
    // ════════════════════════════════════════════════════════════════════

    private void tickPoint(WaveManager wm, Location location, PvpRoundState state,
                           CapturePoint cp, List<ServerPlayer> players) {
        // Count players per team inside the capture sphere
        Map<String, Integer> teamCounts = new LinkedHashMap<>();
        for (ServerPlayer p : players) {
            String team = location.getPlayerTeam(p.getUUID());
            if (team == null) continue;
            if (isInRadius(p, cp.getPos(), cp.getCaptureRadius())) {
                teamCounts.merge(team, 1, Integer::sum);
            }
        }

        // Determine who is standing on the point
        long teamsPresent = teamCounts.values().stream().filter(c -> c > 0).count();

        if (teamsPresent == 0) return;  // nobody on point — no change

        if (teamsPresent > 1) {
            // Contested — freeze progress (do nothing)
            return;
        }

        // Exactly one team on the point
        String capturingTeam = teamCounts.entrySet().stream()
            .filter(e -> e.getValue() > 0).findFirst().map(Map.Entry::getKey).orElse(null);
        if (capturingTeam == null) return;

        String currentOwner = state.getPointOwner(cp.getId());
        if (capturingTeam.equals(currentOwner)) return;  // already owned by this team, no action needed

        int capTicks = cp.getCaptureTimeSec() * 20;
        int prog = state.getCaptureProgress().getOrDefault(cp.getId(), 0);

        // C-2 fix: use per-point direction tracking instead of alphabetical team order.
        // When progress is at 0 (neutral), the first team to arrive "claims" positive direction.
        // Other teams always push in the negative direction (neutralising the positive team's progress).
        // Supports any number of teams without sign-ambiguity.
        String directionTeam = state.getPointCapturingTeam().get(cp.getId());

        if (prog == 0 || directionTeam == null) {
            // Neutral point (or stale direction) — this team claims positive direction
            state.getPointCapturingTeam().put(cp.getId(), capturingTeam);
            directionTeam = capturingTeam;
        }

        int sign = capturingTeam.equals(directionTeam) ? 1 : -1;

        // B3: optional speed multiplier — capture progresses faster with more teammates on point.
        // Capped at 4× to keep the mechanic meaningful in larger lobbies.
        int speed = 1;
        if (location.isCtpMode() && location.isCtpSpeedMultiplier()) {
            Integer count = teamCounts.get(capturingTeam);
            if (count != null) speed = Math.max(1, Math.min(4, count));
        }
        // Advance progress (or neutralise opposing team's progress)
        prog += sign * speed;

        if (prog == 0) {
            // Point neutralised — clear direction so next team to arrive claims it
            state.getPointCapturingTeam().remove(cp.getId());
            state.getCaptureProgress().put(cp.getId(), 0);
        } else if (Math.abs(prog) >= capTicks) {
            // Point captured!
            String oldOwner = state.getPointOwners().get(cp.getId());
            state.getPointOwners().put(cp.getId(), capturingTeam);
            state.getCaptureProgress().put(cp.getId(), 0);
            state.getPointCapturingTeam().remove(cp.getId());
            // KotH hold-timer mode: if "reset on loss" is enabled, zero the previous owner's timer.
            if (location.isKothMode() && location.isKothHoldMode() && location.isKothResetOnLoss()
                    && oldOwner != null && !oldOwner.equals(capturingTeam)) {
                state.resetKothHoldTicks(oldOwner);
            }
            wm.broadcastToLocation(location.getName(),
                Component.translatable("wavedefense.msg.point_captured", capturingTeam, cp.getName()));
        } else {
            state.getCaptureProgress().put(cp.getId(), prog);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private static boolean isInRadius(ServerPlayer player, BlockPos center, int radius) {
        if (center == null) return false;
        // M-10: 2D cylinder check (horizontal distance only) so players on different Y levels
        // (e.g. standing on a slope or being pushed up) still count as on the point.
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    private void sendSync(WaveManager wm, Location location, PvpRoundState state) {
        boolean firstToScore = location.isObjectiveFirstToScore();
        int scoreToWin       = firstToScore ? location.getObjectiveScoreToWin() : 0;
        int roundTicksLeft   = firstToScore ? 0 : state.getRoundDurationTicks();

        // Build pointId → display name map so the client can show friendly names
        // H-3: Also build pointId → captureTimeTicks so the progress bar fills correctly
        LinkedHashMap<String, String> pointNames = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> captureTimeTicks = new LinkedHashMap<>();
        for (CapturePoint cp : location.getCapturePoints()) {
            pointNames.put(cp.getId(), cp.getName());
            captureTimeTicks.put(cp.getId(), cp.getCaptureTimeSec() * 20);
        }

        SyncCtpStatePacket pkt = new SyncCtpStatePacket(
            location.getName(),
            new LinkedHashMap<>(state.getPointOwners()),
            pointNames,
            captureTimeTicks,
            new LinkedHashMap<>(state.getCaptureProgress()),
            new LinkedHashMap<>(state.getObjectiveScore()),
            scoreToWin,
            roundTicksLeft
        );
        for (ServerPlayer p : wm.getPlayersInLocation(location.getName())) {
            PacketHandler.sendToPlayer(p, pkt);
        }
    }

    private void spawnPointParticles(ServerLevel world, BlockPos center, int radius,
                                     String particleId, int count) {
        if (center == null || world == null) return;
        ParticleOptions particle = resolveParticle(particleId);
        // Spawn a small cluster around the point center (not a full ring — keep it lightweight)
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 1.0;
        double cz = center.getZ() + 0.5;
        double r   = Math.max(1, radius);
        int steps  = Math.max(4, (int)(2 * Math.PI * r / 4));
        int toSpawn = Math.min(count, steps);
        for (int i = 0; i < toSpawn; i++) {
            double angle = 2 * Math.PI * i / toSpawn;
            double px = cx + r * Math.cos(angle);
            double pz = cz + r * Math.sin(angle);
            world.sendParticles(particle, px, cy, pz, 1, 0, 0.1, 0, 0.01);
        }
    }

    static ParticleOptions resolveParticle(String id) {
        if (id == null || id.isBlank()) return ParticleTypes.SMOKE;
        try {
            var type = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (type instanceof net.minecraft.core.particles.SimpleParticleType spt) return spt;
            if (type instanceof ParticleOptions po) return po;
        } catch (Exception ignored) {}
        return switch (id) {
            case "minecraft:flame"          -> ParticleTypes.FLAME;
            case "minecraft:smoke"          -> ParticleTypes.SMOKE;
            case "minecraft:crit"           -> ParticleTypes.CRIT;
            case "minecraft:large_smoke"    -> ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal"         -> ParticleTypes.PORTAL;
            case "minecraft:enchant"        -> ParticleTypes.ENCHANT;
            case "minecraft:end_rod"        -> ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame"-> ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:snowflake"      -> ParticleTypes.SNOWFLAKE;
            case "minecraft:witch"          -> ParticleTypes.WITCH;
            case "minecraft:happy_villager" -> ParticleTypes.HAPPY_VILLAGER;
            default                         -> ParticleTypes.SMOKE;
        };
    }
}
