package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Перевірка кордону локації: частинки, наслідки (TIMER_SURRENDER / DAMAGE / TELEPORT_BACK / INSTANT_SURRENDER).
 */
public class BoundaryManager {

    private final WaveContext ctx;
    private int tickCounter = 0;

    public BoundaryManager(WaveContext ctx) { this.ctx = ctx; }

    public void tick(WaveManager wm) {
        if (WaveDefenseMod.getServer() == null) return;
        tickCounter++;

        // Частинки та шкода — раз на секунду
        boolean doSecond = (tickCounter % 20 == 0);
        if (doSecond) tickBorderParticles();

        for (Map.Entry<UUID, PlayerWaveData> e : ctx.playerData.entrySet()) {
            UUID uid = e.getKey();
            PlayerWaveData data = e.getValue();
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (!loc.isLocationBoundaryEnabled()) continue;

            BlockPos center = loc.getPlayerSpawn();
            if (center == null) continue;

            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(uid);
            if (player == null) continue;
            if (player.isSpectator()) continue;

            // Порівнюємо квадрати відстаней — уникаємо дорогого Math.sqrt()
            int radius = loc.getLocationBoundaryRadius();
            boolean outside = player.blockPosition().distSqr(center) > (double) radius * radius;

            if (outside) {
                applyConsequence(wm, player, uid, loc, doSecond);
            } else {
                // Повернувся — скидаємо таймер
                if (ctx.leaveCountdownTicks.containsKey(uid)) {
                    ctx.leaveCountdownTicks.remove(uid);
                    clearTitle(player);
                }
            }
        }
    }

    // ── Наслідки ────────────────────────────────────────────────────────

    private void applyConsequence(WaveManager wm, ServerPlayer player, UUID uid, Location loc, boolean doSecond) {
        switch (loc.getBoundaryConsequence()) {

            case TIMER_SURRENDER -> {
                int ticks = ctx.leaveCountdownTicks.getOrDefault(uid, 0);
                if (ticks <= 0) {
                    int secs = loc.getLocationLeaveTimerSec();
                    ctx.leaveCountdownTicks.put(uid, secs * 20);
                    sendBoundaryTitle(player,
                        Component.translatable("wavedefense.msg.boundary_return"),
                        Component.translatable("wavedefense.msg.boundary_timer", secs));
                } else {
                    int remaining = ticks - 1;
                    ctx.leaveCountdownTicks.put(uid, remaining);
                    if (remaining <= 0) {
                        // Час вийшов — здача (перевіряємо першою, щоб не показувати «0 сек»)
                        ctx.leaveCountdownTicks.remove(uid);
                        wm.surrenderPlayer(player);
                    } else if (remaining % 20 == 0) {
                        // Показуємо тільки якщо remaining > 0 — не буде «0 сек»
                        sendBoundaryTitle(player,
                            Component.translatable("wavedefense.msg.boundary_out_of_zone"),
                            Component.translatable("wavedefense.msg.boundary_timer", remaining / 20));
                        wm.syncPlayerData(player);
                    }
                }
            }

            case DAMAGE -> {
                // Шкода тільки раз на секунду
                if (doSecond) {
                    float dmg = loc.getBoundaryDamagePerSec();
                    if (dmg > 0) player.hurt(player.damageSources().magic(), dmg);
                    sendBoundaryTitle(player,
                        Component.translatable("wavedefense.msg.boundary_unsafe"),
                        Component.translatable("wavedefense.msg.boundary_damage", dmg));
                }
            }

            case TELEPORT_BACK -> {
                // Миттєва телепортація назад на точку спавну
                wm.teleportToSafeSpawn(player, loc.getPlayerSpawn(),
                    loc.getPlayerSpawnRadius());
                player.displayClientMessage(
                    Component.translatable("wavedefense.msg.teleported_back"), true);
                ctx.leaveCountdownTicks.remove(uid);
            }

            case INSTANT_SURRENDER -> {
                ctx.leaveCountdownTicks.remove(uid);
                wm.surrenderPlayer(player);
            }
        }
    }

    // ── Частинки кордону ────────────────────────────────────────────────

    private void tickBorderParticles() {
        if (WaveDefenseMod.getServer() == null) return;
        // Збираємо унікальні активні локації з кордоном і частинками
        Set<String> done = new HashSet<>();
        for (PlayerWaveData data : ctx.playerData.values()) {
            if (data.getCurrentLocation() == null) continue;
            Location loc = data.getCurrentLocation();
            if (!loc.isLocationBoundaryEnabled()) continue;
            if (!loc.isBoundaryParticlesEnabled()) continue;
            if (!done.add(loc.getName())) continue;
            if (loc.getPlayerSpawn() == null) continue;

            // Знаходимо world через першого гравця локації
            List<ServerPlayer> inLoc = WaveDefenseMod.getServer().getPlayerList()
                .getPlayers().stream()
                .filter(p -> {
                    PlayerWaveData d = ctx.playerData.get(p.getUUID());
                    return d != null && d.getCurrentLocation() != null
                        && d.getCurrentLocation().getName().equals(loc.getName());
                })
                .toList();
            if (inLoc.isEmpty()) continue;
            ServerLevel world = inLoc.get(0).serverLevel();
            spawnBorderRing(world, loc, inLoc);
        }
    }

    /**
     * Only the arc of the boundary near a player is drawn, and only for players who
     * asked to see it.
     *
     * <p>The old version drew the entire ring — one particle column every 2 blocks all
     * the way round — and broadcast each column to everyone nearby. At the default
     * radius of 50 that is ~157 columns × 3 rows = <b>471 particle packets every
     * second</b>, sent to bystanders outside the arena as well. That is what players
     * reported as a large FPS drop.
     *
     * <p>Now each player gets only the section of wall they could actually see, sent to
     * them alone, and anyone who turns the option off gets nothing at all.
     */
    private void spawnBorderRing(ServerLevel world, Location loc, List<ServerPlayer> inLoc) {
        ParticleOptions particle = ParticleHelper.resolveParticle(loc.getBoundaryParticleType(), ParticleTypes.SMOKE);
        int radius   = loc.getLocationBoundaryRadius();
        int height   = loc.getBoundaryParticleHeight();
        int count    = Math.max(1, loc.getBoundaryParticleCount());
        BlockPos ctr = loc.getPlayerSpawn();
        if (radius <= 0) return;

        double cx = ctr.getX() + 0.5;
        double cz = ctr.getZ() + 0.5;
        // Arc length between columns; larger spacing on bigger rings keeps the packet
        // count roughly constant instead of growing with the circumference.
        double angleStep = COLUMN_SPACING_BLOCKS / (double) radius;

        for (ServerPlayer p : inLoc) {
            if (!wantsBoundaryParticles(p)) continue;

            // Angle from arena centre towards the player: the wall behind them is not
            // worth a single packet.
            double centreAngle = Math.atan2(p.getZ() - cz, p.getX() - cx);
            int halfColumns = (int) Math.ceil(VISIBLE_ARC_RADIANS / 2.0 / angleStep);

            for (int i = -halfColumns; i <= halfColumns; i++) {
                double angle = centreAngle + i * angleStep;
                double px = cx + radius * Math.cos(angle);
                double pz = cz + radius * Math.sin(angle);
                // Skip columns too far to render anyway — the client culls them, so
                // sending them is pure waste.
                if (p.distanceToSqr(px, p.getY(), pz) > VISIBLE_DIST_SQR) continue;
                for (int dy = 0; dy < height; dy++) {
                    world.sendParticles(p, particle, false,
                        px, ctr.getY() + dy, pz, count, 0, 0, 0, 0.01);
                }
            }
        }
    }

    /** Spacing between particle columns along the boundary, in blocks. */
    private static final double COLUMN_SPACING_BLOCKS = 3.0;
    /** How much of the ring, in radians, is drawn around the direction the player faces out to. */
    private static final double VISIBLE_ARC_RADIANS = Math.PI / 2.0; // 90°
    /** Squared render cutoff — matches roughly the vanilla particle view distance. */
    private static final double VISIBLE_DIST_SQR = 48.0 * 48.0;

    /**
     * Whether this player wants to see boundary particles. Opt-out is per player and
     * lives in their synced settings, so someone on a weaker machine can turn the effect
     * off without the admin having to disable it for everyone.
     */
    private boolean wantsBoundaryParticles(ServerPlayer p) {
        PlayerWaveData d = ctx.playerData.get(p.getUUID());
        return d == null || d.isBoundaryParticlesVisible();
    }

    // ── Заголовки ────────────────────────────────────────────────────────

    private void sendBoundaryTitle(ServerPlayer p, String title, String subtitle) {
        sendBoundaryTitle(p,
            net.minecraft.network.chat.Component.literal(title),
            net.minecraft.network.chat.Component.literal(subtitle));
    }

    /** Component-overload: delivers translatable title/subtitle to the client in their own language. */
    private void sendBoundaryTitle(ServerPlayer p,
                                   net.minecraft.network.chat.Component title,
                                   net.minecraft.network.chat.Component subtitle) {
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 8));
    }

    private void clearTitle(ServerPlayer p) {
        p.connection.send(new ClientboundClearTitlesPacket(true));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану BoundaryManager. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tickCounter", tickCounter);
        return tag;
    }

    /** Відновлення стану BoundaryManager. */
    public void load(CompoundTag tag) {
        tickCounter = tag.getInt("tickCounter");
    }

    /**
     * Активація зони для заданих гравців (для тригерів).
     * Телепортує гравців до точки спавну локації.
     */
    public void activateZoneForPlayers(WaveManager wm, Location loc, java.util.Set<UUID> playerUuids) {
        BlockPos spawnPos = loc.getPlayerSpawn();
        if (spawnPos == null) return;
        
        for (UUID uuid : playerUuids) {
            ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                // Перевіряємо чи гравець вже на цій локації
                PlayerWaveData data = wm.getPlayerData(uuid);
                if (data == null || data.getCurrentLocation() == null 
                        || !data.getCurrentLocation().getName().equals(loc.getName())) {
                    wm.addPlayerToLocation(player, loc);
                }
            }
        }
    }
}
