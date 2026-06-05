package com.wavedefense.wave;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.data.Location;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.*;

/**
 * Перевірка кордону локації: частинки, наслідки (TIMER_SURRENDER / DAMAGE / TELEPORT_BACK / INSTANT_SURRENDER).
 */
public class BoundaryManager {

    private final WaveContext ctx;
    private int tickCounter = 0;

    public BoundaryManager(WaveContext ctx) { this.ctx = ctx; }

    public void tick(WaveManager wm) {
        if (WaveDefenceMod.getServer() == null) return;
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

            ServerPlayerEntity player = WaveDefenceMod.getServer().getPlayerList().getPlayer(uid);
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

    private void applyConsequence(WaveManager wm, ServerPlayerEntity player, UUID uid, Location loc, boolean doSecond) {
        // 1.16.5 port: classic switch (no -> expressions). Each case wrapped in
        // a block so local variables don't leak between cases.
        switch (loc.getBoundaryConsequence()) {

            case TIMER_SURRENDER: {
                int ticks = ctx.leaveCountdownTicks.getOrDefault(uid, 0);
                if (ticks <= 0) {
                    int secs = loc.getLocationLeaveTimerSec();
                    ctx.leaveCountdownTicks.put(uid, secs * 20);
                    sendBoundaryTitle(player,
                        new TranslationTextComponent("wavedefense.msg.boundary_return"),
                        new TranslationTextComponent("wavedefense.msg.boundary_timer", secs));
                } else {
                    int remaining = ticks - 1;
                    ctx.leaveCountdownTicks.put(uid, remaining);
                    if (remaining <= 0) {
                        ctx.leaveCountdownTicks.remove(uid);
                        wm.surrenderPlayer(player);
                    } else if (remaining % 20 == 0) {
                        sendBoundaryTitle(player,
                            new TranslationTextComponent("wavedefense.msg.boundary_out_of_zone"),
                            new TranslationTextComponent("wavedefense.msg.boundary_timer", remaining / 20));
                        wm.syncPlayerData(player);
                    }
                }
                break;
            }

            case DAMAGE: {
                if (doSecond) {
                    float dmg = loc.getBoundaryDamagePerSec();
                    // 1.16.5: DamageSource.MAGIC is the equivalent of damageSources().magic()
                    if (dmg > 0) player.hurt(net.minecraft.util.DamageSource.MAGIC, dmg);
                    sendBoundaryTitle(player,
                        new TranslationTextComponent("wavedefense.msg.boundary_unsafe"),
                        new TranslationTextComponent("wavedefense.msg.boundary_damage", dmg));
                }
                break;
            }

            case TELEPORT_BACK: {
                wm.teleportToSafeSpawn(player, loc.getPlayerSpawn(), loc.getPlayerSpawnRadius());
                player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.teleported_back"), true);
                ctx.leaveCountdownTicks.remove(uid);
                break;
            }

            case INSTANT_SURRENDER: {
                ctx.leaveCountdownTicks.remove(uid);
                wm.surrenderPlayer(player);
                break;
            }
        }
    }

    // ── Частинки кордону ────────────────────────────────────────────────

    private void tickBorderParticles() {
        if (WaveDefenceMod.getServer() == null) return;
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
            List<ServerPlayerEntity> inLoc = WaveDefenceMod.getServer().getPlayerList()
                .getPlayers().stream()
                .filter(p -> {
                    PlayerWaveData d = ctx.playerData.get(p.getUUID());
                    return d != null && d.getCurrentLocation() != null
                        && d.getCurrentLocation().getName().equals(loc.getName());
                })
                .collect(java.util.stream.Collectors.toList());
            if (inLoc.isEmpty()) continue;
            ServerWorld world = ((net.minecraft.world.server.ServerWorld) inLoc.get(0).level);
            spawnBorderRing(world, loc);
        }
    }

    private void spawnBorderRing(ServerWorld world, Location loc) {
        net.minecraft.particles.IParticleData particle = resolveParticle(loc.getBoundaryParticleType());
        int radius   = loc.getLocationBoundaryRadius();
        int height   = loc.getBoundaryParticleHeight();
        int count    = loc.getBoundaryParticleCount();
        BlockPos ctr = loc.getPlayerSpawn();

        int steps = Math.max(8, (int)(2 * Math.PI * radius / 2));
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double px = ctr.getX() + 0.5 + radius * Math.cos(angle);
            double pz = ctr.getZ() + 0.5 + radius * Math.sin(angle);
            for (int dy = 0; dy < height; dy++) {
                world.sendParticles(particle, px, ctr.getY() + dy, pz,
                    1, 0, 0, 0, 0.01);
            }
        }
    }

    // ── Заголовки ────────────────────────────────────────────────────────

    private void sendBoundaryTitle(ServerPlayerEntity p, String title, String subtitle) {
        sendBoundaryTitle(p,
            new net.minecraft.util.text.StringTextComponent(title),
            new net.minecraft.util.text.StringTextComponent(subtitle));
    }

    /** Component-overload: delivers translatable title/subtitle to the client in their own language. */
    private void sendBoundaryTitle(ServerPlayerEntity p,
                                   net.minecraft.util.text.ITextComponent title,
                                   net.minecraft.util.text.ITextComponent subtitle) {
        p.connection.send(new net.minecraft.network.play.server.STitlePacket(net.minecraft.network.play.server.STitlePacket.Type.TITLE, title));
        p.connection.send(new net.minecraft.network.play.server.STitlePacket(net.minecraft.network.play.server.STitlePacket.Type.SUBTITLE, subtitle));
        p.connection.send(new net.minecraft.network.play.server.STitlePacket(5, 30, 8));
    }

    private void clearTitle(ServerPlayerEntity p) {
        // 1.16.5: SPlayerListItemPacket has no CLEAR; use empty title to reset
        p.connection.send(new net.minecraft.network.play.server.STitlePacket(net.minecraft.network.play.server.STitlePacket.Type.CLEAR, null));
    }

    // ── Утиліти ──────────────────────────────────────────────────────────

    public static net.minecraft.particles.IParticleData resolveParticle(String id) {
        if (id == null || id.trim().isEmpty()) return ParticleTypes.SMOKE;
        try {
            net.minecraft.particles.ParticleType<?> type = net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(id));
            if (type instanceof net.minecraft.particles.BasicParticleType) { net.minecraft.particles.BasicParticleType spt = (net.minecraft.particles.BasicParticleType) type; return spt; }
        } catch (Exception ignored) {}
        switch (id) {
            case "minecraft:flame": return ParticleTypes.FLAME;
            case "minecraft:smoke": return ParticleTypes.SMOKE;
            case "minecraft:crit": return ParticleTypes.CRIT;
            case "minecraft:large_smoke": return ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal": return ParticleTypes.PORTAL;
            case "minecraft:enchant": return ParticleTypes.ENCHANT;
            case "minecraft:end_rod": return ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame": return ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:dripping_lava": return ParticleTypes.DRIPPING_LAVA;
            case "minecraft:dust": return new net.minecraft.particles.RedstoneParticleData(1f, 0f, 0f, 1.0f);
            default: return ParticleTypes.SMOKE;

        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану BoundaryManager. */
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("tickCounter", tickCounter);
        return tag;
    }

    /** Відновлення стану BoundaryManager. */
    public void load(CompoundNBT tag) {
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
            ServerPlayerEntity player = WaveDefenceMod.getServer().getPlayerList().getPlayer(uuid);
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
