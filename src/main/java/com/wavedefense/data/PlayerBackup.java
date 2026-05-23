package com.wavedefense.data;

import com.google.gson.*;
import com.wavedefense.WaveDefenseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

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

    public PlayerBackup(ServerPlayer player) {
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

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i).copy();
            if (!stack.isEmpty()) {
                inventory.add(stack);
            }
        }

        for (ItemStack stack : player.getInventory().armor) {
            armor.add(stack.copy());
        }

        for (ItemStack stack : player.getInventory().offhand) {
            offhand.add(stack.copy());
        }

        for (var effect : player.getActiveEffects()) {
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

    public static PlayerBackup create(ServerPlayer player) {
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
            WaveDefenseMod.LOGGER.error("Failed to save backup for player " + playerName, e);
            return false;
        }
    }

    public void restore(ServerPlayer player) {
        player.teleportTo(player.serverLevel(),
            originalPosition.getX() + 0.5, originalPosition.getY(), originalPosition.getZ() + 0.5,
            player.getYRot(), player.getXRot());

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

        player.getInventory().clearContent();
        for (int i = 0; i < inventory.size(); i++) {
            if (i < player.getInventory().getContainerSize()) {
                player.getInventory().setItem(i, inventory.get(i).copy());
            }
        }

        // Restore armor slots
        for (int i = 0; i < armor.size() && i < player.getInventory().armor.size(); i++) {
            player.getInventory().armor.set(i, armor.get(i).copy());
        }

        // Restore offhand slot
        if (!offhand.isEmpty()) {
            player.getInventory().offhand.set(0, offhand.get(0).copy());
        }

        // Restore potion effects
        player.removeAllEffects();
        for (EffectSnapshot eff : effects) {
            try {
                net.minecraft.resources.ResourceLocation effId =
                    new net.minecraft.resources.ResourceLocation(eff.effect);
                net.minecraft.world.effect.MobEffect mobEffect =
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(effId);
                if (mobEffect != null) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        mobEffect, eff.duration, eff.amplifier,
                        eff.ambient, eff.showParticles, eff.showIcon));
                }
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Failed to restore effect '{}': {}",
                    eff.effect, e.getMessage());
            }
        }

        player.sendSystemMessage(Component.translatable("wavedefense.auto.your_data_was_restored_from_back_da57595c"));
    }

    public static List<PlayerBackup> list(UUID playerUuid) {
        try {
            if (!Files.exists(BACKUP_DIR)) {
                return List.of();
            }

            String prefix = playerUuid + "_";
            try (var stream = Files.list(BACKUP_DIR)) {
                return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(PlayerBackup::loadFromFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(PlayerBackup::getTimestamp).reversed())
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Failed to list backups for player " + playerUuid, e);
            return List.of();
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
            WaveDefenseMod.LOGGER.error("Failed to load backup " + backupId + " for player " + playerUuid, e);
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
            GameModeSnapshot gameMode = new GameModeSnapshot(
                net.minecraft.world.level.GameType.byName(gm.get("mode").getAsString())
            );

            List<EffectSnapshot> effects = deserializeEffects(json.getAsJsonArray("effects"));

            return new PlayerBackup(id, playerUuid, playerName, timestamp, position,
                health, foodLevel, saturation, inventory, armor, offhand,
                expLevel, expProgress, totalExp, gameMode, effects);
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("Failed to parse backup file: " + file, e);
            return null;
        }
    }

    private static JsonArray serializeItemStacks(List<ItemStack> stacks) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                stack.save(tag);
                array.add(tag.toString());
            }
        }
        return array;
    }

    private static List<ItemStack> deserializeItemStacks(JsonArray array) {
        if (array == null) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        for (JsonElement elem : array) {
            try {
                CompoundTag tag = TagParser.parseTag(elem.getAsString());
                stacks.add(ItemStack.of(tag));
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.warn("Failed to deserialize item stack", e);
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
        if (array == null) return List.of();
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

        GameModeSnapshot(net.minecraft.world.level.GameType mode) {
            this.mode = mode.getName();
        }

        net.minecraft.world.level.GameType toGameType() {
            return net.minecraft.world.level.GameType.byName(mode);
        }
    }

    private static class EffectSnapshot {
        final String effect;
        final int duration;
        final int amplifier;
        final boolean ambient;
        final boolean showParticles;
        final boolean showIcon;

        EffectSnapshot(net.minecraft.world.effect.MobEffectInstance instance) {
            this.effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).toString();
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
