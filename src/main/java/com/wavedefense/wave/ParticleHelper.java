package com.wavedefense.wave;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared particle-id → {@link ParticleOptions} resolution.
 *
 * <p>Consolidates three near-identical {@code resolveParticle} copies that had
 * drifted apart across {@code BattleRoyaleManager}, {@code BoundaryManager} and
 * {@code CapturePointManager} — each had a slightly different fallback table and
 * default. This single source merges the union of all handled particles so every
 * border / boundary / capture-point renders the same set consistently.
 */
public final class ParticleHelper {

    private ParticleHelper() {}

    /**
     * Resolve a particle id string (e.g. {@code "minecraft:flame"}) to a
     * renderable {@link ParticleOptions}. Tries the vanilla/modded registry
     * first, then a curated fallback table, then the caller's default.
     *
     * @param id       particle id; blank/null → {@code fallback}
     * @param fallback returned when {@code id} is blank or unresolvable. Callers
     *                 pass their own context default (e.g. {@link ParticleTypes#FLAME}
     *                 for the BR border, {@link ParticleTypes#SMOKE} for zones).
     */
    public static ParticleOptions resolveParticle(String id, ParticleOptions fallback) {
        if (id == null || id.isBlank()) return fallback;
        try {
            var type = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (type instanceof ParticleOptions po) return po;
            if (type instanceof SimpleParticleType spt) return spt;
        } catch (Exception ignored) {}
        // Fallback table — only reached when the registry lookup fails (e.g. a
        // particle that needs options, like "dust"). Union of all three legacy copies.
        return switch (id) {
            case "minecraft:flame"           -> ParticleTypes.FLAME;
            case "minecraft:smoke"           -> ParticleTypes.SMOKE;
            case "minecraft:crit"            -> ParticleTypes.CRIT;
            case "minecraft:large_smoke"     -> ParticleTypes.LARGE_SMOKE;
            case "minecraft:portal"          -> ParticleTypes.PORTAL;
            case "minecraft:enchant"         -> ParticleTypes.ENCHANT;
            case "minecraft:end_rod"         -> ParticleTypes.END_ROD;
            case "minecraft:soul_fire_flame" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:snowflake"       -> ParticleTypes.SNOWFLAKE;
            case "minecraft:dripping_lava"   -> ParticleTypes.DRIPPING_LAVA;
            case "minecraft:witch"           -> ParticleTypes.WITCH;
            case "minecraft:happy_villager"  -> ParticleTypes.HAPPY_VILLAGER;
            case "minecraft:dust"            -> new net.minecraft.core.particles.DustParticleOptions(
                                                    new org.joml.Vector3f(1f, 0f, 0f), 1.0f);
            default                          -> fallback;
        };
    }
}
