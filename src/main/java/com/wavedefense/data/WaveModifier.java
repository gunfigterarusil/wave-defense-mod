package com.wavedefense.data;

import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * A twist rolled onto a whole wave, so no two runs of the same location play alike.
 *
 * <p>Most modifiers are just a potion effect applied to every mob in the wave — cheap,
 * predictable, and instantly readable by the player from the mob's particle colour.
 * Two ({@link #VOLATILE}, {@link #VENOMOUS}) need real behaviour and are implemented by
 * tagging the mob's persistent data; {@code EventHandler} reacts to those tags on death
 * and on hit respectively.
 *
 * <p>Effects are resolved through {@code ForgeRegistries} at spawn time rather than
 * captured as static fields, matching how per-mob effects are already handled in
 * {@code MobSpawnManager} and avoiding any class-init ordering risk during mod load.
 */
public enum WaveModifier {

    /** Everything sprints. Turns a slow shambling wave into a rush. */
    SWIFT       ("swift",        "minecraft:speed",             1),
    /** Halves incoming damage — punishes glass-cannon loadouts. */
    ARMORED     ("armored",      "minecraft:resistance",        1),
    /** Mobs heal, so chip damage stops working and you have to commit. */
    REGENERATING("regenerating", "minecraft:regeneration",      0),
    /** Mobs hit noticeably harder. */
    ENRAGED     ("enraged",      "minecraft:strength",          0),
    /** Extra health pool on top of whatever the difficulty preset already gave. */
    TOUGH       ("tough",        "minecraft:health_boost",      2),
    /** Invisible mobs — you fight by sound and by the armour they wear. */
    PHANTOM     ("phantom",      "minecraft:invisibility",      0),
    /** Explodes on death. Rewards spacing, punishes melee tunnel-vision. */
    VOLATILE    ("volatile",     null,                          0),
    /** Poisons whoever it hits. */
    VENOMOUS    ("venomous",     null,                          0);

    /** Persistent-data flag read by the death handler to trigger the explosion. */
    public static final String NBT_VOLATILE = "wd_volatile";
    /** Persistent-data flag read by the hurt handler to apply poison. */
    public static final String NBT_VENOMOUS = "wd_venomous";

    /** One hour of ticks — far longer than any wave, so effects never lapse mid-fight. */
    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 60;

    private final String key;
    private final String effectId;   // null for behaviour-only modifiers
    private final int    amplifier;

    WaveModifier(String key, String effectId, int amplifier) {
        this.key       = key;
        this.effectId  = effectId;
        this.amplifier = amplifier;
    }

    public String getKey() { return key; }

    /** Translation key for the name, e.g. {@code wavedefense.modifier.swift}. */
    public String getDisplayKey() { return "wavedefense.modifier." + key; }

    /** Translation key for the one-line explanation shown when the wave starts. */
    public String getDescriptionKey() { return "wavedefense.modifier." + key + ".desc"; }

    /**
     * Applies this modifier to a freshly spawned mob. Never throws — a modifier that
     * cannot be applied must not abort the spawn, or one bad registry lookup would
     * empty the whole wave.
     */
    public void applyTo(Mob mob) {
        if (effectId != null) {
            try {
                net.minecraft.world.effect.MobEffect effect =
                    net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(
                        new net.minecraft.resources.ResourceLocation(effectId));
                if (effect != null) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        effect, EFFECT_DURATION_TICKS, amplifier, false, false));
                }
            } catch (Exception e) {
                com.wavedefense.WaveDefenseMod.LOGGER.debug(
                    "[WaveDefense] Could not apply modifier effect '{}': {}", effectId, e.getMessage());
            }
        }
        switch (this) {
            case VOLATILE -> mob.getPersistentData().putBoolean(NBT_VOLATILE, true);
            case VENOMOUS -> mob.getPersistentData().putBoolean(NBT_VENOMOUS, true);
            default -> { /* effect-only modifier, nothing more to do */ }
        }
    }

    /** Lenient parse; returns {@code null} for unknown or empty input. */
    public static WaveModifier fromString(String s) {
        if (s == null || s.isEmpty()) return null;
        for (WaveModifier m : values()) {
            if (m.key.equalsIgnoreCase(s) || m.name().equalsIgnoreCase(s)) return m;
        }
        return null;
    }

    /**
     * Picks a random modifier from {@code pool}. An empty or entirely unrecognised pool
     * means "the admin enabled modifiers but named none", which is treated as "all of
     * them" — the useful reading, and it keeps the feature working out of the box.
     *
     * @return the rolled modifier, never {@code null}
     */
    public static WaveModifier roll(Collection<String> pool, Random rng) {
        List<WaveModifier> candidates = new ArrayList<>();
        if (pool != null) {
            for (String s : pool) {
                WaveModifier m = fromString(s);
                if (m != null && !candidates.contains(m)) candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            for (WaveModifier m : values()) candidates.add(m);
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }
}
