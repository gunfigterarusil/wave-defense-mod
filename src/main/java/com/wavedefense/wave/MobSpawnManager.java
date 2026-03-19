package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Відповідає за спавн мобів.
 * Методи perенесені з WaveManager: spawnWaveForLocation, spawnWaveAroundPos,
 * applyMobEquipment, forceSetItemSlot, getRandomSpawnPoint.
 */
public class MobSpawnManager {

    private final WaveContext ctx;

    public MobSpawnManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ── Spawn a full wave ─────────────────────────────────────────────

    public boolean spawnWave(String locationName, WaveConfig waveConfig,
                              int waveNumber, WaveManager wm) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null) return false;

        List<ServerPlayer> players = ctx.getPlayersInLocation(locationName);
        if (players.isEmpty()) return false;

        ServerLevel world = players.get(0).serverLevel();
        Set<UUID> spawnedMobs = ctx.spawnedMobsByLocation
                .computeIfAbsent(locationName, k -> new HashSet<>());

        int playerCount = Math.max(1, players.size());
        boolean anySpawned = false;
        Random rng = new Random();

        for (WaveMob waveMob : waveConfig.getMobs()) {
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            int baseCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            int mobCount  = baseCount * playerCount;

            for (int i = 0; i < mobCount; i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;

                BlockPos base = waveConfig.hasWaveSpawnPos()
                        ? waveConfig.getWaveSpawnPos()
                        : getRandomSpawnPoint(location);
                if (base == null) continue;

                // Per-mob radius overrides location radius if set
                int r = (waveMob.getSpawnRadius() > 0)
                    ? waveMob.getSpawnRadius()
                    : location.getMobSpawnRadius();
                BlockPos spawnPos;
                if (r > 0) {
                    int dx = rng.nextInt(r * 2 + 1) - r;
                    int dz = rng.nextInt(r * 2 + 1) - r;
                    spawnPos = base.offset(dx, 0, dz);
                } else {
                    spawnPos = base;
                }

                Mob mob = trySpawn(entityType, world, spawnPos, locationName, waveMob);
                if (mob != null) {
                    spawnedMobs.add(mob.getUUID());
                    anySpawned = true;
                }
            }
        }
        // Track mob count for HALF_MOBS_DEAD trigger
        ctx.waveStartMobCounts.put(locationName, spawnedMobs.size());
        ctx.locationMobsKilled.put(locationName, 0);
        return anySpawned;
    }

    /** Spawn mobs around a specific position (portal/trigger waves). */
    public void spawnAroundPos(WaveConfig waveConfig, Location loc,
                                ServerLevel world, BlockPos center,
                                String locationName, int waveNumber) {
        Set<UUID> spawnedMobs = ctx.spawnedMobsByLocation
                .computeIfAbsent(locationName, k -> new HashSet<>());
        Random rng = new Random();
        // Вибираємо точки спавну: якщо є конкретні MobSpawnPoints — розподіляємо моби по них
        var spawnPoints = loc.getMobSpawns();

        for (WaveMob waveMob : waveConfig.getMobs()) {
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            int mobCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            for (int i = 0; i < mobCount; i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                BlockPos spawnPos;
                if (!spawnPoints.isEmpty()) {
                    // Використовуємо конкретну точку спавну з її власним радіусом
                    var sp = spawnPoints.get(rng.nextInt(spawnPoints.size()));
                    int r = sp.getRadius() > 0 ? sp.getRadius() : loc.getMobSpawnRadius();
                    spawnPos = r > 0
                        ? sp.getPos().offset(rng.nextInt(r*2+1)-r, 0, rng.nextInt(r*2+1)-r)
                        : sp.getPos();
                } else {
                    // Fallback на center (точка спавну хвилі або локації)
                    int r = loc.getMobSpawnRadius();
                    spawnPos = r > 0
                        ? center.offset(rng.nextInt(r*2+1)-r, 0, rng.nextInt(r*2+1)-r)
                        : center;
                }
                Mob mob = trySpawn(entityType, world, spawnPos, locationName, waveMob);
                if (mob != null) spawnedMobs.add(mob.getUUID());
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private Mob trySpawn(EntityType<?> entityType, ServerLevel world,
                          BlockPos pos, String locationName, WaveMob waveMob) {
        try {
            Mob mob = (Mob) entityType.create(world);
            if (mob == null) return null;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos),
                    MobSpawnType.COMMAND, null, null);
            mob.goalSelector.addGoal(2,
                    new NearestAttackableTargetGoal<>(mob, Player.class, true));
            mob.setPersistenceRequired();
            mob.getPersistentData().putString("location", locationName);
            mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());
            applyMobEquipment(mob, waveMob);
            world.addFreshEntity(mob);
            return mob;
        } catch (Exception e) {
            return null;
        }
    }

    public BlockPos getRandomSpawnPoint(Location location) {
        var spawns = location.getMobSpawns();
        if (spawns.isEmpty()) return location.getPlayerSpawn();
        var sp = spawns.get(new Random().nextInt(spawns.size()));
        return sp.randomPos(new Random());
    }

    // ── Equipment ─────────────────────────────────────────────────────

    public void applyMobEquipment(Mob mob, WaveMob waveMob) {
        if (!com.wavedefense.config.WaveDefenseConfig.MOBS_CAN_HAVE_EQUIPMENT.get()) return;
        float drop = (float) com.wavedefense.config.WaveDefenseConfig.MOB_ARMOR_DROP_CHANCE.get().floatValue();

        if (!waveMob.getHelmet().isEmpty())     forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.HEAD,     waveMob.getHelmet().copy(),     drop);
        if (!waveMob.getChestplate().isEmpty()) forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.CHEST,    waveMob.getChestplate().copy(), drop);
        if (!waveMob.getLeggings().isEmpty())   forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.LEGS,     waveMob.getLeggings().copy(),   drop);
        if (!waveMob.getBoots().isEmpty())      forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.FEET,     waveMob.getBoots().copy(),      drop);
        if (!waveMob.getMainHand().isEmpty())   forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.MAINHAND, waveMob.getMainHand().copy(),   drop);
        if (!waveMob.getOffHand().isEmpty())    forceSetItemSlot(mob, net.minecraft.world.entity.EquipmentSlot.OFFHAND,  waveMob.getOffHand().copy(),    drop);

        for (String effectStr : waveMob.getEffects()) {
            try {
                String[] parts = effectStr.split(":");
                if (parts.length == 4) {
                    net.minecraft.resources.ResourceLocation id =
                        new net.minecraft.resources.ResourceLocation(parts[0], parts[1]);
                    int amp = Integer.parseInt(parts[2]);
                    int dur = Integer.parseInt(parts[3]);
                    net.minecraft.world.effect.MobEffect effect =
                        net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);
                    if (effect != null)
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, dur, amp));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Встановлює предмет у слот моба з NBT-fallback для GeckoLib/tacz мобів.
     */
    public void forceSetItemSlot(Mob mob, net.minecraft.world.entity.EquipmentSlot slot,
                                  net.minecraft.world.item.ItemStack item, float dropChance) {
        mob.setItemSlot(slot, item);
        mob.setDropChance(slot, dropChance);
        try {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            mob.save(tag);
            net.minecraft.nbt.ListTag armor = tag.contains("ArmorItems")
                ? tag.getList("ArmorItems", 10) : new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag hand = tag.contains("HandItems")
                ? tag.getList("HandItems", 10) : new net.minecraft.nbt.ListTag();
            while (armor.size() < 4) armor.add(new net.minecraft.nbt.CompoundTag());
            while (hand.size() < 2)  hand.add(new net.minecraft.nbt.CompoundTag());
            net.minecraft.nbt.CompoundTag itemTag = item.save(new net.minecraft.nbt.CompoundTag());
            switch (slot) {
                case MAINHAND -> hand.set(0, itemTag);
                case OFFHAND  -> hand.set(1, itemTag);
                case FEET     -> armor.set(0, itemTag);
                case LEGS     -> armor.set(1, itemTag);
                case CHEST    -> armor.set(2, itemTag);
                case HEAD     -> armor.set(3, itemTag);
            }
            tag.put("ArmorItems", armor);
            tag.put("HandItems",  hand);
            net.minecraft.nbt.ListTag ad = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag hd = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 4; i++) ad.add(net.minecraft.nbt.FloatTag.valueOf(dropChance));
            for (int i = 0; i < 2; i++) hd.add(net.minecraft.nbt.FloatTag.valueOf(dropChance));
            tag.put("ArmorDropChances", ad);
            tag.put("HandDropChances",  hd);
            mob.load(tag);
        } catch (Exception ignored) {}
    }
}
