package com.wavedefense.wave;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.player.PlayerEntity;
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
        Location location = WaveDefenceMod.locationManager.getLocation(locationName);
        if (location == null) return false;

        List<ServerPlayerEntity> players = ctx.getPlayersInLocation(locationName);
        if (players.isEmpty()) return false;

        if (WaveDefenceMod.getServer() == null) return false;
        // Spawn mobs in the same dimension as the players, not hardcoded Overworld.
        ServerWorld world = ((net.minecraft.world.server.ServerWorld) players.get(0).level);
        if (world == null) return false;
        LocationSession sess = ctx.getOrCreateSession(locationName, location);
        Set<UUID> spawnedMobs = sess.spawnedMobs;

        int playerCount = Math.max(1, players.size());
        boolean anySpawned = false;
        Random rng = new Random();

        // Snapshot size before spawning so portal/trigger mobs already in the set
        // don't inflate waveStartMobCount (which drives HALF_MOBS_DEAD trigger).
        int preSpawnSize = spawnedMobs.size();

        for (WaveMob waveMob : waveConfig.getMobs()) {
            EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            int baseCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            double difficulty = wm.getAutoScaler().getCurrentDifficulty();
            int mobCount = (int) (baseCount * playerCount * difficulty);
            if (mobCount < 1) mobCount = 1; // ensure at least 1 mob

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

                MobEntity mob = trySpawn(entityType, world, spawnPos, locationName, waveMob);
                if (mob != null) {
                    spawnedMobs.add(mob.getUUID());
                    anySpawned = true;
                }
            }
        }
        // Track only NEWLY spawned mobs for the HALF_MOBS_DEAD trigger.
        // Subtracting preSpawnSize excludes portal/trigger mobs that were already
        // in spawnedMobs before this wave started.
        sess.waveStartMobCount = Math.max(0, spawnedMobs.size() - preSpawnSize);
        sess.mobsKilled = 0;
        return anySpawned;
    }

    /** Spawn mobs around a specific position (portal/trigger waves). */
    public void spawnAroundPos(WaveConfig waveConfig, Location loc,
                                ServerWorld world, BlockPos center,
                                String locationName, int waveNumber) {
        LocationSession sess = ctx.getOrCreateSession(locationName, loc);
        Set<UUID> spawnedMobs = sess.spawnedMobs;
        Random rng = new Random();
        // Вибираємо точки спавну: якщо є конкретні MobSpawnPoints — розподіляємо моби по них
        java.util.List<com.wavedefense.data.MobSpawnPoint> spawnPoints = loc.getMobSpawns();

        for (WaveMob waveMob : waveConfig.getMobs()) {
            EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(waveMob.getMobType());
            if (entityType == null) continue;

            int mobCount = waveMob.getCount() + (waveMob.getGrowthPerWave() * (waveNumber - 1));
            for (int i = 0; i < mobCount; i++) {
                if (rng.nextInt(100) >= waveMob.getSpawnChance()) continue;
                BlockPos spawnPos;
                if (!spawnPoints.isEmpty()) {
                    // Використовуємо конкретну точку спавну з її власним радіусом
                    com.wavedefense.data.MobSpawnPoint sp = spawnPoints.get(rng.nextInt(spawnPoints.size()));
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
                MobEntity mob = trySpawn(entityType, world, spawnPos, locationName, waveMob);
                if (mob != null) spawnedMobs.add(mob.getUUID());
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private MobEntity trySpawn(EntityType<?> entityType, ServerWorld world,
                          BlockPos pos, String locationName, WaveMob waveMob) {
        if (!world.isLoaded(pos)) {
            WaveDefenceMod.LOGGER.debug("[WaveDefense] Skipping spawn at {} — chunk not loaded", pos);
            return null;
        }
        try {
            MobEntity mob = (MobEntity) entityType.create(world);
            if (mob == null) return null;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos),
                    SpawnReason.COMMAND, null, null);
            mob.goalSelector.addGoal(2,
                    new NearestAttackableTargetGoal<>(mob, PlayerEntity.class, true));
            mob.setPersistenceRequired();
            mob.getPersistentData().putString("location", locationName);
            mob.getPersistentData().putInt("points", waveMob.getPointsPerKill());
            applyMobEquipment(mob, waveMob);
            /* MnSCompat not ported */;
            world.addFreshEntity(mob);
            return mob;
        } catch (Exception e) {
            WaveDefenceMod.LOGGER.warn("[WaveDefense] trySpawn failed for '{}' at {} in '{}': {}",
                entityType.getDescriptionId(), pos, locationName, e.getMessage());
            return null;
        }
    }

    public BlockPos getRandomSpawnPoint(Location location) {
        java.util.List<com.wavedefense.data.MobSpawnPoint> spawns = location.getMobSpawns();
        if (spawns.isEmpty()) return location.getPlayerSpawn();
        com.wavedefense.data.MobSpawnPoint sp = spawns.get(new Random().nextInt(spawns.size()));
        return sp.randomPos(new Random());
    }

    // ── Equipment ─────────────────────────────────────────────────────

    public void applyMobEquipment(MobEntity mob, WaveMob waveMob) {
        if (!com.wavedefense.config.WaveDefenseConfig.MOBS_CAN_HAVE_EQUIPMENT.get()) return;
        float drop = (float) com.wavedefense.config.WaveDefenseConfig.MOB_ARMOR_DROP_CHANCE.get().floatValue();

        if (!waveMob.getHelmet().isEmpty())     forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.HEAD,     waveMob.getHelmet().copy(),     drop);
        if (!waveMob.getChestplate().isEmpty()) forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.CHEST,    waveMob.getChestplate().copy(), drop);
        if (!waveMob.getLeggings().isEmpty())   forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.LEGS,     waveMob.getLeggings().copy(),   drop);
        if (!waveMob.getBoots().isEmpty())      forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.FEET,     waveMob.getBoots().copy(),      drop);
        if (!waveMob.getMainHand().isEmpty())   forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.MAINHAND, waveMob.getMainHand().copy(),   drop);
        if (!waveMob.getOffHand().isEmpty())    forceSetItemSlot(mob, net.minecraft.inventory.EquipmentSlotType.OFFHAND,  waveMob.getOffHand().copy(),    drop);

        for (String effectStr : waveMob.getEffects()) {
            try {
                String[] parts = effectStr.split(":");
                if (parts.length == 4) {
                    net.minecraft.util.ResourceLocation id =
                        new net.minecraft.util.ResourceLocation(parts[0], parts[1]);
                    int amp = Integer.parseInt(parts[2]);
                    int dur = Integer.parseInt(parts[3]);
                    net.minecraft.potion.Effect effect =
                        net.minecraftforge.registries.ForgeRegistries.POTIONS.getValue(id);
                    if (effect != null)
                        mob.addEffect(new net.minecraft.potion.EffectInstance(effect, dur, amp));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Встановлює предмет у слот моба з NBT-fallback для GeckoLib/tacz мобів.
     */
    public void forceSetItemSlot(MobEntity mob, net.minecraft.inventory.EquipmentSlotType slot,
                                  net.minecraft.item.ItemStack item, float dropChance) {
        mob.setItemSlot(slot, item);
        mob.setDropChance(slot, dropChance);
        try {
            net.minecraft.nbt.CompoundNBT tag = new net.minecraft.nbt.CompoundNBT();
            mob.save(tag);
            net.minecraft.nbt.ListNBT armor = tag.contains("ArmorItems")
                ? tag.getList("ArmorItems", 10) : new net.minecraft.nbt.ListNBT();
            net.minecraft.nbt.ListNBT hand = tag.contains("HandItems")
                ? tag.getList("HandItems", 10) : new net.minecraft.nbt.ListNBT();
            while (armor.size() < 4) armor.add(new net.minecraft.nbt.CompoundNBT());
            while (hand.size() < 2)  hand.add(new net.minecraft.nbt.CompoundNBT());
            net.minecraft.nbt.CompoundNBT itemTag = item.save(new net.minecraft.nbt.CompoundNBT());
            switch (slot) {
                case MAINHAND: hand.set(0, itemTag); break;
                case OFFHAND:  hand.set(1, itemTag); break;
                case FEET:     armor.set(0, itemTag); break;
                case LEGS:     armor.set(1, itemTag); break;
                case CHEST:    armor.set(2, itemTag); break;
                case HEAD:     armor.set(3, itemTag); break;
            }
            tag.put("ArmorItems", armor);
            tag.put("HandItems",  hand);
            net.minecraft.nbt.ListNBT ad = new net.minecraft.nbt.ListNBT();
            net.minecraft.nbt.ListNBT hd = new net.minecraft.nbt.ListNBT();
            for (int i = 0; i < 4; i++) ad.add(net.minecraft.nbt.FloatNBT.valueOf(dropChance));
            for (int i = 0; i < 2; i++) hd.add(net.minecraft.nbt.FloatNBT.valueOf(dropChance));
            tag.put("ArmorDropChances", ad);
            tag.put("HandDropChances",  hd);
            mob.load(tag);
        } catch (Exception e) {
            com.wavedefense.WaveDefenceMod.LOGGER.warn(
                "[WaveDefense] NBT-fallback for mob equipment failed (slot={}, mob={}): {}",
                slot, mob.getType().getDescriptionId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану MobSpawnManager. */
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        // MobSpawnManager не має власних полів, які потребують збереження
        // Усі дані вже збережені через WaveContext/LocationSession
        return tag;
    }

    /** Відновлення стану MobSpawnManager. */
    public void load(CompoundNBT tag) {
        // Немає стану для відновлення
    }
}
