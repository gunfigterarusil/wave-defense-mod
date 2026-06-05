package com.wavedefense.data;

import net.minecraft.util.text.TranslationTextComponent;

import com.google.gson.*;
import com.wavedefense.WaveDefenceMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Player backup system for Wave Defense.
 * Supports creating, saving, listing, and restoring player data backups.
 */
public class PlayerBackup {
    private static final Path BACKUP_DIR = Paths.get("backups", "wavedefense");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String id;
    private final UUID playerUuid;
    private final String playerName;
    private final long timestamp;
    private final BlockPos originalPosition;
    private final float health;
    private final int foodLevel;
    private final float saturation;
    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<ItemStack> armor = new ArrayList<>();
    private final List<ItemStack> offhand = new ArrayList<>();
    private final int experienceLevel;
    private final float experienceProgress;
    private final int totalExperience;
    private final GameModeSnapshot gameMode;
    private final List<EffectSnapshot> effects = new ArrayList<>();

    public PlayerBackup(ServerPlayerEntity player) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.playerUuid = player.getUUID();
        this.playerName = player.getGameProfile().getName();
        this.timestamp = System.currentTimeMillis();
        this.originalPosition = player.blockPosition();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodData().getFoodLevel();
        this.saturation = player.getFoodData().getSaturationLevel();
        this.experienceLevel = player.experienceLevel;
        this.experienceProgress = player.experienceProgress;
        this.totalExperience = player.totalExperience;
        this.gameMode = new GameModeSnapshot(player.gameMode.getGameModeForPlayer());

        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack stack = player.inventory.getItem(i).copy();
            if (!stack.isEmpty()) {
                inventory.add(stack);
            }
        }

        for (ItemStack stack : player.inventory.armor) {
            armor.add(stack.copy());
        }

        for (ItemStack stack : player.inventory.offhand) {
            offhand.add(stack.copy());
        }

        for (net.minecraft.potion.EffectInstance effect : player.getActiveEffects()) {
            effects.add(new EffectSnapshot(effect));
        }
    }

    private PlayerBackup(String id, UUID playerUuid, String playerName, long timestamp,
                        BlockPos pos, float health, int foodLevel, float saturation,
                        List<ItemStack> inventory, List<ItemStack> armor, List<ItemStack> offhand,
                        int expLevel, float expProgress, int totalExp,
                        GameModeSnapshot gameMode, List<EffectSnapshot> effects) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.timestamp = timestamp;
        this.originalPosition = pos;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.inventory.addAll(inventory);
        this.armor.addAll(armor);
        this.offhand.addAll(offhand);
        this.experienceLevel = expLevel;
        this.experienceProgress = expProgress;
        this.totalExperience = totalExp;
        this.gameMode = gameMode;
        this.effects.addAll(effects);
    }

    public static PlayerBackup create(ServerPlayerEntity player) {
        return new PlayerBackup(player);
    }

    public boolean save() {
        try {
            Files.createDirectories(BACKUP_DIR);
            Path file = BACKUP_DIR.resolve(playerUuid + "_" + id + ".json");

            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("playerUuid", playerUuid.toString());
            json.addProperty("playerName", playerName);
            json.addProperty("timestamp", timestamp);

            JsonObject pos = new JsonObject();
            pos.addProperty("x", originalPosition.getX());
            pos.addProperty("y", originalPosition.getY());
            pos.addProperty("z", originalPosition.getZ());
            json.add("position", pos);

            json.addProperty("health", health);
            json.addProperty("foodLevel", foodLevel);
            json.addProperty("saturation", saturation);
            json.addProperty("expLevel", experienceLevel);
            json.addProperty("expProgress", experienceProgress);
            json.addProperty("totalExp", totalExperience);

            JsonObject gm = new JsonObject();
            gm.addProperty("mode", gameMode.mode);
            json.add("gameMode", gm);

            json.add("inventory", serializeItemStacks(inventory));
            json.add("armor", serializeItemStacks(armor));
            json.add("offhand", serializeItemStacks(offhand));
            json.add("effects", serializeEffects(effects));

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(json, writer);
            }

            return true;
        } catch (IOException e) {
            WaveDefenceMod.LOGGER.error("Failed to save backup for player " + playerName, e);
            return false;
        }
    }

    public void restore(ServerPlayerEntity player) {
        player.teleportTo((net.minecraft.world.server.ServerWorld) player.getLevel(),
            originalPosition.getX() + 0.5, originalPosition.getY(), originalPosition.getZ() + 0.5,
            player.yRot, player.xRot);

        player.setHealth(health);
        player.getFoodData().setFoodLevel(foodLevel);
        player.getFoodData().setSaturation(saturation);

        // С7: set XP fields directly to avoid float-accumulation drift from giveExperienceLevels()
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0f;
        player.experienceLevel = experienceLevel;
        player.totalExperience = totalExperience;
        player.experienceProgress = experienceProgress;

        player.setGameMode(gameMode.toGameType());

        player.inventory.clearContent();
        for (int i = 0; i < inventory.size(); i++) {
            if (i < player.inventory.getContainerSize()) {
                player.inventory.setItem(i, inventory.get(i).copy());
            }
        }

        // Restore armor slots
        for (int i = 0; i < armor.size() && i < player.inventory.armor.size(); i++) {
            player.inventory.armor.set(i, armor.get(i).copy());
        }

        // Restore offhand slot
        if (!offhand.isEmpty()) {
            player.inventory.offhand.set(0, offhand.get(0).copy());
        }

        // Restore potion effects
        player.removeAllEffects();
        for (EffectSnapshot eff : effects) {
            try {
                net.minecraft.util.ResourceLocation effId =
                    new net.minecraft.util.ResourceLocation(eff.effect);
                net.minecraft.potion.Effect mobEffect =
                    net.minecraftforge.registries.ForgeRegistries.POTIONS.getValue(effId);
                if (mobEffect != null) {
                    player.addEffect(new net.minecraft.potion.EffectInstance(
                        mobEffect, eff.duration, eff.amplifier,
                        eff.ambient, eff.showParticles, eff.showIcon));
                }
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.warn("[WaveDefense] Failed to restore effect '{}': {}",
                    eff.effect, e.getMessage());
            }
        }

        player.sendMessage(new TranslationTextComponent("wavedefense.auto.your_data_was_restored_from_back_da57595c"), net.minecraft.util.Util.NIL_UUID);
    }

    public static List<PlayerBackup> list(UUID playerUuid) {
        try {
            if (!Files.exists(BACKUP_DIR)) {
                return java.util.Collections.emptyList();
            }

            String prefix = playerUuid + "_";
            try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(BACKUP_DIR)) {
                return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(PlayerBackup::loadFromFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(PlayerBackup::getTimestamp).reversed())
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            WaveDefenceMod.LOGGER.error("Failed to list backups for player " + playerUuid, e);
            return java.util.Collections.emptyList();
        }
    }

    public static Optional<PlayerBackup> load(UUID playerUuid, String backupId) {
        try {
            if (!Files.exists(BACKUP_DIR)) {
                return Optional.empty();
            }

            Path file = BACKUP_DIR.resolve(playerUuid + "_" + backupId + ".json");
            if (!Files.exists(file)) {
                return Optional.empty();
            }

            return Optional.ofNullable(loadFromFile(file));
        } catch (Exception e) {
            WaveDefenceMod.LOGGER.error("Failed to load backup " + backupId + " for player " + playerUuid, e);
            return Optional.empty();
        }
    }

    private static PlayerBackup loadFromFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            String id = json.get("id").getAsString();
            UUID playerUuid = UUID.fromString(json.get("playerUuid").getAsString());
            String playerName = json.get("playerName").getAsString();
            long timestamp = json.get("timestamp").getAsLong();

            JsonObject pos = json.getAsJsonObject("position");
            BlockPos position = new BlockPos(
                pos.get("x").getAsInt(),
                pos.get("y").getAsInt(),
                pos.get("z").getAsInt()
            );

            float health = json.get("health").getAsFloat();
            int foodLevel = json.get("foodLevel").getAsInt();
            float saturation = json.get("saturation").getAsFloat();

            List<ItemStack> inventory = deserializeItemStacks(json.getAsJsonArray("inventory"));
            List<ItemStack> armor = deserializeItemStacks(json.getAsJsonArray("armor"));
            List<ItemStack> offhand = deserializeItemStacks(json.getAsJsonArray("offhand"));

            int expLevel = json.get("expLevel").getAsInt();
            float expProgress = json.get("expProgress").getAsFloat();
            int totalExp = json.get("totalExp").getAsInt();

            JsonObject gm = json.getAsJsonObject("gameMode");
            // byName(String) can return null for unrecognised names — use fallback overload.
            GameModeSnapshot gameMode = new GameModeSnapshot(
                net.minecraft.world.GameType.byName(gm.get("mode").getAsString(),
                    net.minecraft.world.GameType.SURVIVAL)
            );

            List<EffectSnapshot> effects = deserializeEffects(json.getAsJsonArray("effects"));

            return new PlayerBackup(id, playerUuid, playerName, timestamp, position,
                health, foodLevel, saturation, inventory, armor, offhand,
                expLevel, expProgress, totalExp, gameMode, effects);
        } catch (Exception e) {
            WaveDefenceMod.LOGGER.error("Failed to parse backup file: " + file, e);
            return null;
        }
    }

    private static JsonArray serializeItemStacks(List<ItemStack> stacks) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                CompoundNBT tag = new CompoundNBT();
                stack.save(tag);
                array.add(tag.toString());
            }
        }
        return array;
    }

    private static List<ItemStack> deserializeItemStacks(JsonArray array) {
        if (array == null) return java.util.Collections.emptyList();
        List<ItemStack> stacks = new ArrayList<>();
        for (JsonElement elem : array) {
            try {
                CompoundNBT tag = JsonToNBT.parseTag(elem.getAsString());
                stacks.add(ItemStack.of(tag));
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.warn("Failed to deserialize item stack", e);
            }
        }
        return stacks;
    }

    private static JsonArray serializeEffects(List<EffectSnapshot> effects) {
        JsonArray array = new JsonArray();
        for (EffectSnapshot effect : effects) {
            array.add(effect.toJson());
        }
        return array;
    }

    private static List<EffectSnapshot> deserializeEffects(JsonArray array) {
        if (array == null) return java.util.Collections.emptyList();
        List<EffectSnapshot> effects = new ArrayList<>();
        for (JsonElement elem : array) {
            effects.add(EffectSnapshot.fromJson(elem.getAsJsonObject()));
        }
        return effects;
    }

    public String getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public long getTimestamp() { return timestamp; }

    private static class GameModeSnapshot {
        final String mode;

        GameModeSnapshot(net.minecraft.world.GameType mode) {
            this.mode = mode.getName();
        }

        net.minecraft.world.GameType toGameType() {
            return net.minecraft.world.GameType.byName(mode, net.minecraft.world.GameType.SURVIVAL);
        }
    }

    private static class EffectSnapshot {
        final String effect;
        final int duration;
        final int amplifier;
        final boolean ambient;
        final boolean showParticles;
        final boolean showIcon;

        EffectSnapshot(net.minecraft.potion.EffectInstance instance) {
            // ForgeRegistries covers both vanilla and modded effects.
            // BuiltInRegistries only covers vanilla — fall back to it just in case.
            ResourceLocation key = ForgeRegistries.POTIONS.getKey(instance.getEffect());
            if (key == null) {
                key = net.minecraftforge.registries.ForgeRegistries.POTIONS.getKey(instance.getEffect());
            }
            this.effect = key != null ? key.toString() : "minecraft:speed"; // safe fallback
            this.duration = instance.getDuration();
            this.amplifier = instance.getAmplifier();
            this.ambient = instance.isAmbient();
            this.showParticles = instance.isVisible();
            this.showIcon = instance.showIcon();
        }

        EffectSnapshot(String effect, int duration, int amplifier, boolean ambient, boolean showParticles, boolean showIcon) {
            this.effect = effect;
            this.duration = duration;
            this.amplifier = amplifier;
            this.ambient = ambient;
            this.showParticles = showParticles;
            this.showIcon = showIcon;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("effect", effect);
            json.addProperty("duration", duration);
            json.addProperty("amplifier", amplifier);
            json.addProperty("ambient", ambient);
            json.addProperty("showParticles", showParticles);
            json.addProperty("showIcon", showIcon);
            return json;
        }

        static EffectSnapshot fromJson(JsonObject json) {
            return new EffectSnapshot(
                json.get("effect").getAsString(),
                json.get("duration").getAsInt(),
                json.get("amplifier").getAsInt(),
                json.get("ambient").getAsBoolean(),
                json.get("showParticles").getAsBoolean(),
                json.get("showIcon").getAsBoolean()
            );
        }
    }
}
