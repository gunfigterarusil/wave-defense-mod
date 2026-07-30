package com.wavedefense.data;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every player's lifetime {@link PlayerProfile}.
 *
 * <p>Storage: {@code <world>/data/wavedefense_profiles.dat}, written through the same
 * atomic/debounced path as locations and leaderboards, so a crash mid-write cannot
 * corrupt the file and a burst of updates collapses into one disk hit.
 *
 * <p>Profiles are created lazily on first touch and never removed — a returning player
 * keeps their level even after months away.
 */
public class PlayerProfileManager {

    private static final int DATA_VERSION = 1;

    private final Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();
    private final File dataFile;

    public PlayerProfileManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/wavedefense_profiles.dat").toFile();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Returns the player's profile, creating an empty one on first use. */
    public synchronized PlayerProfile getOrCreate(UUID uuid, String playerName) {
        PlayerProfile p = profiles.get(uuid);
        if (p == null) {
            p = new PlayerProfile(uuid, playerName);
            profiles.put(uuid, p);
        } else {
            p.setPlayerName(playerName); // keep up with name changes
        }
        return p;
    }

    /** Returns the profile, or {@code null} if the player has never played. */
    public synchronized PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    /** All profiles, ranked by level then XP — backs the server-wide "top players" view. */
    public synchronized List<PlayerProfile> getRanked(int limit) {
        List<PlayerProfile> all = new ArrayList<>(profiles.values());
        all.sort((a, b) -> {
            int byXp = Integer.compare(b.getXp(), a.getXp());
            if (byXp != 0) return byXp;
            return Integer.compare(b.getBestWave(), a.getBestWave());
        });
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public synchronized int size() { return profiles.size(); }

    // ── Persistence ────────────────────────────────────────────────────────

    public synchronized void save() {
        NbtHelper.atomicWriteCompressedAsync(dataFile, serialize());
    }

    /** Synchronous save — used only on server stop. */
    public synchronized void saveSync() {
        NbtHelper.atomicWriteCompressed(dataFile, serialize());
    }

    public synchronized void load() {
        if (!dataFile.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile);
            int version = root.contains("__version__") ? root.getInt("__version__") : 0;
            if (version < DATA_VERSION) root = migrate(root, version);

            profiles.clear();
            ListTag list = root.getList("profiles", 10);
            for (int i = 0; i < list.size(); i++) {
                PlayerProfile p = PlayerProfile.load(list.getCompound(i));
                if (p != null) profiles.put(p.getUuid(), p);
            }
            WaveDefenseMod.LOGGER.info("[WaveDefense] Loaded {} player profile(s)", profiles.size());
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Could not load player profiles", e);
        }
    }

    private CompoundTag serialize() {
        CompoundTag root = new CompoundTag();
        root.putInt("__version__", DATA_VERSION);
        ListTag list = new ListTag();
        for (PlayerProfile p : profiles.values()) list.add(p.save());
        root.put("profiles", list);
        return root;
    }

    /** Migration seam for future profile schema changes. v0→v1 is a no-op. */
    private CompoundTag migrate(CompoundTag root, int fromVersion) {
        WaveDefenseMod.LOGGER.info("[WaveDefense] Migrating player profiles v{} → v{} (no-op).",
            fromVersion, DATA_VERSION);
        return root;
    }
}
