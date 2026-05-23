package com.wavedefense.backup;

import com.google.gson.*;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.data.*;
import com.wavedefense.wave.PlayerWaveData;
import com.wavedefense.wave.WaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Automated Backup and Recovery System for Wave Defense.
 * 
 * Features:
 * - Scheduled automatic backups (configurable interval)
 * - Full backups (complete system state)
 * - Incremental backups (only changed data since last backup)
 * - Recovery capabilities (restore from any backup point)
 * - Backup metadata management (compression, retention, listing)
 * - Integration with existing Wave Defense systems
 * 
 * Backup Structure:
 * backups/wavedefense/
 *   ├── full/                    # Full backup snapshots
 *   │   ├── 20240115_143022/
 *   │   │   ├── metadata.json
 *   │   │   ├── locations.dat
 *   │   │   ├── wavemanager.dat
 *   │   │   └── players/
 *   │   └── ...
 *   ├── incremental/             # Incremental changes
 *   │   ├── 20240115_150000/
 *   │   │   ├── metadata.json
 *   │   │   └── changes.dat
 *   │   └── ...
 *   └── backup_index.json        # Index of all backups
 */
public class WaveDefenseBackupSystem {

    // ──────────────────────────────────────────────────────────────────────
    //  Constants & Paths
    // ──────────────────────────────────────────────────────────────────────
    public static final Path BACKUP_ROOT = Paths.get("backups", "wavedefense");
    public static final Path FULL_BACKUP_DIR = BACKUP_ROOT.resolve("full");
    public static final Path INCREMENTAL_BACKUP_DIR = BACKUP_ROOT.resolve("incremental");
    public static final Path BACKUP_INDEX_FILE = BACKUP_ROOT.resolve("backup_index.json");
    public static final Path LAST_BACKUP_STATE_FILE = BACKUP_ROOT.resolve("last_state.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create();

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ──────────────────────────────────────────────────────────────────────
    //  Configuration (defaults)
    // ──────────────────────────────────────────────────────────────────────
    private static final long DEFAULT_BACKUP_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_MAX_FULL_BACKUPS = 24;
    private static final int DEFAULT_MAX_INCREMENTAL_BACKUPS = 168; // 1 week of hourly
    private static final boolean DEFAULT_COMPRESSION_ENABLED = true;
    private static final boolean DEFAULT_INCREMENTAL_ENABLED = true;

    // ──────────────────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────────────────
    private static WaveDefenseBackupSystem instance;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, byte[]> lastKnownState = new ConcurrentHashMap<>();
    private volatile boolean isBackupRunning = false;
    private volatile boolean isRestoreRunning = false;
    private volatile long lastBackupTime = 0;
    private volatile String lastBackupId = null;
    private volatile BackupType lastBackupType = BackupType.NONE;

    // ──────────────────────────────────────────────────────────────────────
    //  Enums
    // ──────────────────────────────────────────────────────────────────────
    public enum BackupType {
        FULL, INCREMENTAL, NONE
    }

    public enum BackupStatus {
        SUCCESS, FAILED, SKIPPED, IN_PROGRESS
    }

    public enum RestoreStatus {
        SUCCESS, FAILED, IN_PROGRESS, NOT_FOUND
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Data Classes
    // ──────────────────────────────────────────────────────────────────────
    public static class BackupMetadata {
        public String id;
        public BackupType type;
        public long timestamp;
        public String formattedTime;
        public long durationMs;
        public BackupStatus status;
        public String baseBackupId; // for incremental backups
        public long sizeBytes;
        public int locationCount;
        public int playerCount;
        public String checksum;
        public String serverVersion;
        public String waveDefenseVersion;
        public Map<String, Object> details = new HashMap<>();
    }

    public static class BackupIndex {
        public List<BackupMetadata> backups = new ArrayList<>();
        public String lastFullBackupId;
        public String lastBackupId;
        public long totalSizeBytes;
        public int totalBackups;
    }

    public static class BackupResult {
        public BackupStatus status;
        public String backupId;
        public BackupType type;
        public long durationMs;
        public long sizeBytes;
        public String message;
        public Path backupPath;
    }

    public static class RestoreResult {
        public RestoreStatus status;
        public String backupId;
        public long durationMs;
        public String message;
        public List<String> restoredLocations = new ArrayList<>();
        public List<String> restoredPlayers = new ArrayList<>();
    }

    public static class ChangeSet {
        public Set<String> addedLocations = new HashSet<>();
        public Set<String> modifiedLocations = new HashSet<>();
        public Set<String> removedLocations = new HashSet<>();
        public Set<UUID> modifiedPlayers = new HashSet<>();
        public Set<String> modifiedWaveStates = new HashSet<>();
        public long timestamp;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Singleton
    // ──────────────────────────────────────────────────────────────────────
    public static synchronized WaveDefenseBackupSystem getInstance() {
        if (instance == null) {
            instance = new WaveDefenseBackupSystem();
        }
        return instance;
    }

    private WaveDefenseBackupSystem() {
        // Private constructor for singleton
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Initialization & Shutdown
    // ──────────────────────────────────────────────────────────────────────
    public void initialize() {
        try {
            Files.createDirectories(FULL_BACKUP_DIR);
            Files.createDirectories(INCREMENTAL_BACKUP_DIR);
            Files.createDirectories(BACKUP_ROOT);
            loadLastKnownState();
            WaveDefenseMod.LOGGER.info("WaveDefense Backup System initialized");
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Failed to initialize backup system", e);
        }
    }

    public void startScheduledBackups() {
        long interval = getBackupIntervalMinutes();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                performScheduledBackup();
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("Scheduled backup failed", e);
            }
        }, interval, interval, TimeUnit.MINUTES);
        WaveDefenseMod.LOGGER.info("Scheduled backups started (interval: {} minutes)", interval);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        saveLastKnownState();
        WaveDefenseMod.LOGGER.info("Backup system shutdown complete");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Configuration Helpers
    // ──────────────────────────────────────────────────────────────────────
    private long getBackupIntervalMinutes() {
        // Could be read from config file in the future
        return DEFAULT_BACKUP_INTERVAL_MINUTES;
    }

    private int getMaxFullBackups() {
        return DEFAULT_MAX_FULL_BACKUPS;
    }

    private int getMaxIncrementalBackups() {
        return DEFAULT_MAX_INCREMENTAL_BACKUPS;
    }

    private boolean isCompressionEnabled() {
        return DEFAULT_COMPRESSION_ENABLED;
    }

    private boolean isIncrementalEnabled() {
        return DEFAULT_INCREMENTAL_ENABLED;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Main Backup Operations
    // ──────────────────────────────────────────────────────────────────────
    public BackupResult performFullBackup() {
        if (isBackupRunning) {
            return createResult(BackupStatus.SKIPPED, null, "Backup already in progress");
        }
        return performBackup(BackupType.FULL, null);
    }

    public BackupResult performIncrementalBackup() {
        if (isBackupRunning) {
            return createResult(BackupStatus.SKIPPED, null, "Backup already in progress");
        }
        if (!isIncrementalEnabled()) {
            return performFullBackup();
        }
        String baseId = getLastFullBackupId();
        if (baseId == null) {
            WaveDefenseMod.LOGGER.warn("No full backup found, performing full backup instead");
            return performFullBackup();
        }
        return performBackup(BackupType.INCREMENTAL, baseId);
    }

    public BackupResult performScheduledBackup() {
        if (lastBackupType == BackupType.NONE || System.currentTimeMillis() - lastBackupTime > TimeUnit.DAYS.toMillis(1)) {
            return performFullBackup();
        } else {
            return performIncrementalBackup();
        }
    }

    private BackupResult performBackup(BackupType type, String baseBackupId) {
        isBackupRunning = true;
        long startTime = System.currentTimeMillis();
        String backupId = TIMESTAMP_FORMAT.format(Instant.now());
        Path backupDir = (type == BackupType.FULL) ?
                FULL_BACKUP_DIR.resolve(backupId) :
                INCREMENTAL_BACKUP_DIR.resolve(backupId);

        try {
            Files.createDirectories(backupDir);
            MinecraftServer server = WaveDefenseMod.getServer();
            if (server == null) {
                return createResult(BackupStatus.FAILED, backupId, "Server not available");
            }

            BackupMetadata metadata = createMetadata(type, backupId, baseBackupId);

            if (type == BackupType.FULL) {
                performFullBackupInternal(server, backupDir, metadata);
            } else {
                performIncrementalBackupInternal(server, backupDir, metadata, baseBackupId);
            }

            // Save metadata
            Path metadataFile = backupDir.resolve("metadata.json");
            try (Writer writer = Files.newBufferedWriter(metadataFile)) {
                GSON.toJson(metadata, writer);
            }

            // Update index
            updateBackupIndex(metadata);

            // Update last known state
            updateLastKnownState(server);

            long duration = System.currentTimeMillis() - startTime;
            metadata.durationMs = duration;
            metadata.status = BackupStatus.SUCCESS;

            // Update volatile state
            lastBackupTime = System.currentTimeMillis();
            lastBackupId = backupId;
            lastBackupType = type;

            // Cleanup old backups
            cleanupOldBackups();

            WaveDefenseMod.LOGGER.info("{} backup completed: {} ({} ms, {} bytes)",
                    type, backupId, duration, metadata.sizeBytes);

            return new BackupResult() {{
                status = BackupStatus.SUCCESS;
                backupId = backupId;
                type = type;
                durationMs = duration;
                sizeBytes = metadata.sizeBytes;
                message = type + " backup completed successfully";
                backupPath = backupDir;
            }};

        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error(type + " backup failed", e);
            return createResult(BackupStatus.FAILED, backupId, e.getMessage());
        } finally {
            isBackupRunning = false;
        }
    }

    private void performFullBackupInternal(MinecraftServer server, Path backupDir, BackupMetadata metadata) throws IOException {
        // Backup locations
        LocationManager locationManager = WaveDefenseMod.locationManager;
        if (locationManager != null) {
            CompoundTag locationsTag = locationManager.save();
            Path locationsFile = backupDir.resolve("locations.dat");
            writeNbt(locationsTag, locationsFile);
            metadata.locationCount = locationManager.getAllLocations().size();
        }

        // Backup wave manager
        WaveManager waveManager = WaveDefenseMod.waveManager;
        if (waveManager != null) {
            CompoundTag waveTag = waveManager.save();
            Path waveFile = backupDir.resolve("wavemanager.dat");
            writeNbt(waveTag, waveFile);
        }

        // Backup player data
        Path playersDir = backupDir.resolve("players");
        Files.createDirectories(playersDir);
        int playerCount = 0;
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    Path playerFile = playersDir.resolve(player.getUUID() + ".dat");
                    CompoundTag playerTag = new CompoundTag();
                    player.saveWithoutId(playerTag);
                    writeNbt(playerTag, playerFile);
                    playerCount++;
                } catch (Exception e) {
                    WaveDefenseMod.LOGGER.warn("Failed to backup player data: " + player.getGameProfile().getName(), e);
                }
            }
        }
        metadata.playerCount = playerCount;

        // Calculate size
        metadata.sizeBytes = calculateDirectorySize(backupDir);
        metadata.checksum = calculateChecksum(backupDir);
    }

    private void performIncrementalBackupInternal(MinecraftServer server, Path backupDir,
                                                  BackupMetadata metadata, String baseBackupId) throws IOException {
        ChangeSet changes = detectChanges(server, baseBackupId);
        metadata.locationCount = changes.addedLocations.size() + changes.modifiedLocations.size();
        metadata.playerCount = changes.modifiedPlayers.size();

        // Save changeset
        Path changesFile = backupDir.resolve("changes.dat");
        try (Writer writer = Files.newBufferedWriter(changesFile)) {
            GSON.toJson(changes, writer);
        }

        // Backup changed locations
        Path locationsDir = backupDir.resolve("locations");
        Files.createDirectories(locationsDir);
        LocationManager locationManager = WaveDefenseMod.locationManager;
        if (locationManager != null) {
            for (String locName : changes.addedLocations) {
                Location loc = locationManager.getLocation(locName);
                if (loc != null) {
                    Path locFile = locationsDir.resolve(locName + ".dat");
                    NbtIo.writeCompressed(loc.save(), locFile.toFile());
                }
            }
            for (String locName : changes.modifiedLocations) {
                Location loc = locationManager.getLocation(locName);
                if (loc != null) {
                    Path locFile = locationsDir.resolve(locName + ".dat");
                    NbtIo.writeCompressed(loc.save(), locFile.toFile());
                }
            }
        }

        // Backup changed player data
        Path playersDir = backupDir.resolve("players");
        Files.createDirectories(playersDir);
        if (server.getPlayerList() != null) {
            for (UUID playerUuid : changes.modifiedPlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                if (player != null) {
                    Path playerFile = playersDir.resolve(playerUuid + ".dat");
                    CompoundTag playerTag = new CompoundTag();
                    player.saveWithoutId(playerTag);
                    NbtIo.writeCompressed(playerTag, playerFile.toFile());
                }
            }
        }

        // Backup wave state changes
        if (!changes.modifiedWaveStates.isEmpty()) {
            Path waveDir = backupDir.resolve("wave_states");
            Files.createDirectories(waveDir);
            WaveManager waveManager = WaveDefenseMod.waveManager;
            if (waveManager != null) {
                for (String locName : changes.modifiedWaveStates) {
                    CompoundTag waveTag = waveManager.saveLocationState(locName);
                    if (waveTag != null) {
                        Path waveFile = waveDir.resolve(locName + ".dat");
                        NbtIo.writeCompressed(waveTag, waveFile.toFile());
                    }
                }
            }
        }

        metadata.sizeBytes = calculateDirectorySize(backupDir);
        metadata.checksum = calculateChecksum(backupDir);
    }

    private BackupMetadata createMetadata(BackupType type, String backupId, String baseBackupId) {
        BackupMetadata metadata = new BackupMetadata();
        metadata.id = backupId;
        metadata.type = type;
        metadata.timestamp = System.currentTimeMillis();
        metadata.formattedTime = Instant.ofEpochMilli(metadata.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        metadata.status = BackupStatus.IN_PROGRESS;
        metadata.baseBackupId = baseBackupId;
        metadata.serverVersion = WaveDefenseMod.getServer().getServerVersion();
        metadata.waveDefenseVersion = "2.0.39";
        return metadata;
    }

    private ChangeSet detectChanges(MinecraftServer server, String baseBackupId) {
        ChangeSet changes = new ChangeSet();
        changes.timestamp = System.currentTimeMillis();

        // Detect location changes
        LocationManager locationManager = WaveDefenseMod.locationManager;
        if (locationManager != null) {
            for (Location loc : locationManager.getAllLocations()) {
                String locName = loc.getName();
                byte[] currentState = hashLocation(loc);
                byte[] lastState = lastKnownState.get("loc:" + locName);
                if (lastState == null) {
                    changes.addedLocations.add(locName);
                } else if (!Arrays.equals(lastState, currentState)) {
                    changes.modifiedLocations.add(locName);
                }
                lastKnownState.put("loc:" + locName, currentState);
            }
        }

        // Detect player changes
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                byte[] currentState = hashPlayer(player);
                byte[] lastState = lastKnownState.get("player:" + uuid);
                if (lastState == null || !Arrays.equals(lastState, currentState)) {
                    changes.modifiedPlayers.add(uuid);
                }
                lastKnownState.put("player:" + uuid, currentState);
            }
        }

        // Detect wave state changes
        WaveManager waveManager = WaveDefenseMod.waveManager;
        if (waveManager != null) {
            for (Location loc : locationManager.getAllLocations()) {
                String locName = loc.getName();
                byte[] currentState = hashWaveState(locName, waveManager);
                byte[] lastState = lastKnownState.get("wave:" + locName);
                if (lastState == null || !Arrays.equals(lastState, currentState)) {
                    changes.modifiedWaveStates.add(locName);
                }
                lastKnownState.put("wave:" + locName, currentState);
            }
        }

        return changes;
    }

    private byte[] hashLocation(Location loc) {
        try {
            CompoundTag tag = loc.save();
            return tag.toString().getBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] hashPlayer(ServerPlayer player) {
        try {
            CompoundTag tag = new CompoundTag();
            player.saveWithoutId(tag);
            return tag.toString().getBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private byte[] hashWaveState(String locName, WaveManager waveManager) {
        try {
            CompoundTag tag = waveManager.saveLocationState(locName);
            return tag != null ? tag.toString().getBytes() : new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Restore Operations
    // ──────────────────────────────────────────────────────────────────────
    public RestoreResult restoreFull(String backupId) {
        if (isRestoreRunning) {
            return createRestoreResult(RestoreStatus.FAILED, backupId, "Restore already in progress");
        }
        isRestoreRunning = true;
        long startTime = System.currentTimeMillis();

        try {
            Path backupDir = FULL_BACKUP_DIR.resolve(backupId);
            if (!Files.exists(backupDir)) {
                return createRestoreResult(RestoreStatus.NOT_FOUND, backupId, "Backup not found");
            }

            MinecraftServer server = WaveDefenseMod.getServer();
            if (server == null) {
                return createRestoreResult(RestoreStatus.FAILED, backupId, "Server not available");
            }

            // Restore locations
            Path locationsFile = backupDir.resolve("locations.dat");
            if (Files.exists(locationsFile)) {
                CompoundTag locationsTag = readNbt(locationsFile);
                WaveDefenseMod.locationManager = new LocationManager(server);
                WaveDefenseMod.locationManager.loadFromTag(locationsTag);
            }

            // Restore wave manager
            Path waveFile = backupDir.resolve("wavemanager.dat");
            if (Files.exists(waveFile)) {
                CompoundTag waveTag = readNbt(waveFile);
                WaveDefenseMod.waveManager.load(waveTag);
            }

            // Restore player data
            Path playersDir = backupDir.resolve("players");
            List<String> restoredPlayers = new ArrayList<>();
            if (Files.exists(playersDir) && server.getPlayerList() != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    Path playerFile = playersDir.resolve(player.getUUID() + ".dat");
                    if (Files.exists(playerFile)) {
                        CompoundTag playerTag = readNbt(playerFile);
                        player.load(playerTag);
                        restoredPlayers.add(player.getGameProfile().getName());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Update state
            updateLastKnownState(server);
            lastBackupId = backupId;
            lastBackupType = BackupType.FULL;

            WaveDefenseMod.LOGGER.info("Full restore completed from backup: {} ({} ms)", backupId, duration);

            RestoreResult result = createRestoreResult(RestoreStatus.SUCCESS, backupId, "Restore completed successfully");
            result.durationMs = duration;
            result.restoredPlayers = restoredPlayers;
            return result;

        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("Full restore failed", e);
            return createRestoreResult(RestoreStatus.FAILED, backupId, e.getMessage());
        } finally {
            isRestoreRunning = false;
        }
    }

    public RestoreResult restoreIncremental(String backupId) {
        if (isRestoreRunning) {
            return createRestoreResult(RestoreStatus.FAILED, backupId, "Restore already in progress");
        }
        isRestoreRunning = true;
        long startTime = System.currentTimeMillis();

        try {
            Path backupDir = INCREMENTAL_BACKUP_DIR.resolve(backupId);
            if (!Files.exists(backupDir)) {
                return createRestoreResult(RestoreStatus.NOT_FOUND, backupId, "Backup not found");
            }

            // Load changeset
            Path changesFile = backupDir.resolve("changes.dat");
            if (!Files.exists(changesFile)) {
                return createRestoreResult(RestoreStatus.FAILED, backupId, "Changeset not found");
            }

            ChangeSet changes;
            try (Reader reader = Files.newBufferedReader(changesFile)) {
                changes = GSON.fromJson(reader, ChangeSet.class);
            }

            MinecraftServer server = WaveDefenseMod.getServer();
            if (server == null) {
                return createRestoreResult(RestoreStatus.FAILED, backupId, "Server not available");
            }

            List<String> restoredLocations = new ArrayList<>();
            List<String> restoredPlayers = new ArrayList<>();

            // Restore changed locations
            Path locationsDir = backupDir.resolve("locations");
            if (Files.exists(locationsDir)) {
                LocationManager locationManager = WaveDefenseMod.locationManager;
                if (locationManager != null) {
                    for (String locName : changes.addedLocations) {
                        Path locFile = locationsDir.resolve(locName + ".dat");
                        if (Files.exists(locFile)) {
                            CompoundTag tag = NbtIo.readCompressed(locFile.toFile());
                            Location loc = Location.load(tag);
                            locationManager.addLocation(loc);
                            restoredLocations.add(locName);
                        }
                    }
                    for (String locName : changes.modifiedLocations) {
                        Path locFile = locationsDir.resolve(locName + ".dat");
                        if (Files.exists(locFile)) {
                            CompoundTag tag = NbtIo.readCompressed(locFile.toFile());
                            Location loc = Location.load(tag);
                            locationManager.updateLocation(loc);
                            restoredLocations.add(locName);
                        }
                    }
                }
            }

            // Restore changed player data
            Path playersDir = backupDir.resolve("players");
            if (Files.exists(playersDir) && server.getPlayerList() != null) {
                for (UUID playerUuid : changes.modifiedPlayers) {
                    Path playerFile = playersDir.resolve(playerUuid + ".dat");
                    if (Files.exists(playerFile)) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                        if (player != null) {
                            CompoundTag tag = NbtIo.readCompressed(playerFile.toFile());
                            player.load(tag);
                            restoredPlayers.add(player.getGameProfile().getName());
                        }
                    }
                }
            }

            // Restore wave states
            Path waveDir = backupDir.resolve("wave_states");
            if (Files.exists(waveDir)) {
                WaveManager waveManager = WaveDefenseMod.waveManager;
                if (waveManager != null) {
                    for (String locName : changes.modifiedWaveStates) {
                        Path waveFile = waveDir.resolve(locName + ".dat");
                        if (Files.exists(waveFile)) {
                            CompoundTag tag = NbtIo.readCompressed(waveFile.toFile());
                            waveManager.loadLocationState(locName, tag);
                        }
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Update state
            updateLastKnownState(server);

            WaveDefenseMod.LOGGER.info("Incremental restore completed from backup: {} ({} ms)", backupId, duration);

            RestoreResult result = createRestoreResult(RestoreStatus.SUCCESS, backupId, "Incremental restore completed successfully");
            result.durationMs = duration;
            result.restoredLocations = restoredLocations;
            result.restoredPlayers = restoredPlayers;
            return result;

        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("Incremental restore failed", e);
            return createRestoreResult(RestoreStatus.FAILED, backupId, e.getMessage());
        } finally {
            isRestoreRunning = false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Backup Index Management
    // ──────────────────────────────────────────────────────────────────────
    private void updateBackupIndex(BackupMetadata metadata) throws IOException {
        BackupIndex index = loadBackupIndex();
        index.backups.removeIf(b -> b.id.equals(metadata.id));
        index.backups.add(metadata);
        index.backups.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        if (metadata.type == BackupType.FULL) {
            index.lastFullBackupId = metadata.id;
        }
        index.lastBackupId = metadata.id;

        index.totalBackups = index.backups.size();
        index.totalSizeBytes = 0;
        for (BackupMetadata bm : index.backups) {
            index.totalSizeBytes += bm.sizeBytes;
        }

        try (Writer writer = Files.newBufferedWriter(BACKUP_INDEX_FILE)) {
            GSON.toJson(index, writer);
        }
    }

    public BackupIndex loadBackupIndex() throws IOException {
        BackupIndex index = new BackupIndex();
        if (Files.exists(BACKUP_INDEX_FILE)) {
            try (Reader reader = Files.newBufferedReader(BACKUP_INDEX_FILE)) {
                index = GSON.fromJson(reader, BackupIndex.class);
            }
        }
        if (index.backups == null) {
            index.backups = new ArrayList<>();
        }
        return index;
    }

    public List<BackupMetadata> listBackups() throws IOException {
        return loadBackupIndex().backups;
    }

    public List<BackupMetadata> listBackups(BackupType type) throws IOException {
        return loadBackupIndex().backups.stream()
                .filter(b -> b.type == type)
                .collect(Collectors.toList());
    }

    public Optional<BackupMetadata> getBackupInfo(String backupId) throws IOException {
        return loadBackupIndex().backups.stream()
                .filter(b -> b.id.equals(backupId))
                .findFirst();
    }

    private String getLastFullBackupId() {
        try {
            BackupIndex index = loadBackupIndex();
            return index.lastFullBackupId;
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.warn("Failed to load backup index", e);
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Cleanup
    // ──────────────────────────────────────────────────────────────────────
    private void cleanupOldBackups() {
        try {
            List<BackupMetadata> fullBackups = listBackups(BackupType.FULL);
            List<BackupMetadata> incrementalBackups = listBackups(BackupType.INCREMENTAL);

            int maxFull = getMaxFullBackups();
            int maxIncremental = getMaxIncrementalBackups();

            // Remove excess full backups (keep newest)
            if (fullBackups.size() > maxFull) {
                List<BackupMetadata> toRemove = fullBackups.stream()
                        .sorted(Comparator.comparingLong(b -> b.timestamp))
                        .limit(fullBackups.size() - maxFull)
                        .collect(Collectors.toList());
                for (BackupMetadata bm : toRemove) {
                    deleteBackup(bm);
                }
            }

            // Remove excess incremental backups (keep newest)
            if (incrementalBackups.size() > maxIncremental) {
                List<BackupMetadata> toRemove = incrementalBackups.stream()
                        .sorted(Comparator.comparingLong(b -> b.timestamp))
                        .limit(incrementalBackups.size() - maxIncremental)
                        .collect(Collectors.toList());
                for (BackupMetadata bm : toRemove) {
                    deleteBackup(bm);
                }
            }

        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Failed to cleanup old backups", e);
        }
    }

    private void deleteBackup(BackupMetadata metadata) {
        try {
            Path backupDir = (metadata.type == BackupType.FULL) ?
                    FULL_BACKUP_DIR.resolve(metadata.id) :
                    INCREMENTAL_BACKUP_DIR.resolve(metadata.id);
            if (Files.exists(backupDir)) {
                deleteDirectory(backupDir);
            }
            WaveDefenseMod.LOGGER.info("Deleted old backup: {} ({})", metadata.id, metadata.type);
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("Failed to delete backup: " + metadata.id, e);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            WaveDefenseMod.LOGGER.warn("Failed to delete: " + p, e);
                        }
                    });
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  State Persistence
    // ──────────────────────────────────────────────────────────────────────
    private void updateLastKnownState(MinecraftServer server) {
        lastKnownState.clear();
        LocationManager locationManager = WaveDefenseMod.locationManager;
        if (locationManager != null) {
            for (Location loc : locationManager.getAllLocations()) {
                lastKnownState.put("loc:" + loc.getName(), hashLocation(loc));
            }
        }
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                lastKnownState.put("player:" + player.getUUID(), hashPlayer(player));
            }
        }
        WaveManager waveManager = WaveDefenseMod.waveManager;
        if (waveManager != null && locationManager != null) {
            for (Location loc : locationManager.getAllLocations()) {
                lastKnownState.put("wave:" + loc.getName(), hashWaveState(loc.getName(), waveManager));
            }
        }
        saveLastKnownState();
    }

    private void saveLastKnownState() {
        try {
            JsonObject state = new JsonObject();
            state.addProperty("timestamp", System.currentTimeMillis());
            JsonObject hashes = new JsonObject();
            for (Map.Entry<String, byte[]> entry : lastKnownState.entrySet()) {
                hashes.addProperty(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
            }
            state.add("hashes", hashes);
            try (Writer writer = Files.newBufferedWriter(LAST_BACKUP_STATE_FILE)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.warn("Failed to save last known state", e);
        }
    }

    private void loadLastKnownState() {
        try {
            if (Files.exists(LAST_BACKUP_STATE_FILE)) {
                try (Reader reader = Files.newBufferedReader(LAST_BACKUP_STATE_FILE)) {
                    JsonObject state = GSON.fromJson(reader, JsonObject.class);
                    JsonObject hashes = state.getAsJsonObject("hashes");
                    for (Map.Entry<String, JsonElement> entry : hashes.entrySet()) {
                        lastKnownState.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue().getAsString()));
                    }
                }
            }
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.warn("Failed to load last known state", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  NBT I/O helpers — single point for compression logic
    //  (fixes: old code wrapped GZIPOutputStream around NbtIo.writeCompressed
    //   which itself uses GZIP → double compression → corrupt files)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes an NBT CompoundTag to a file.
     * When compression is enabled, NbtIo.writeCompressed handles GZIP internally.
     * When disabled, writes plain uncompressed NBT via DataOutputStream.
     */
    private void writeNbt(CompoundTag tag, Path file) throws IOException {
        if (isCompressionEnabled()) {
            // NbtIo.writeCompressed uses GZIP internally — do NOT wrap in GZIPOutputStream
            try (OutputStream os = Files.newOutputStream(file)) {
                NbtIo.writeCompressed(tag, os);
            }
        } else {
            try (java.io.DataOutputStream dos = new java.io.DataOutputStream(
                    Files.newOutputStream(file))) {
                NbtIo.write(tag, dos);
            }
        }
    }

    /**
     * Reads an NBT CompoundTag from a file.
     * Symmetric to writeNbt — uses same compression setting.
     */
    private CompoundTag readNbt(Path file) throws IOException {
        if (isCompressionEnabled()) {
            try (InputStream is = Files.newInputStream(file)) {
                return NbtIo.readCompressed(is);
            }
        } else {
            try (java.io.DataInputStream dis = new java.io.DataInputStream(
                    Files.newInputStream(file))) {
                return NbtIo.read(dis);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Utility Methods
    // ──────────────────────────────────────────────────────────────────────
    private long calculateDirectorySize(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private String calculateChecksum(Path dir) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(dir).sorted()) {
                paths.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        md.update(Files.readAllBytes(p));
                    } catch (IOException e) {
                        // Ignore
                    }
                });
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private BackupResult createResult(BackupStatus status, String backupId, String message) {
        BackupResult result = new BackupResult();
        result.status = status;
        result.backupId = backupId;
        result.message = message;
        return result;
    }

    private RestoreResult createRestoreResult(RestoreStatus status, String backupId, String message) {
        RestoreResult result = new RestoreResult();
        result.status = status;
        result.backupId = backupId;
        result.message = message;
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Public API for Commands
    // ──────────────────────────────────────────────────────────────────────
    public static BackupResult triggerFullBackup() {
        return getInstance().performFullBackup();
    }

    public static BackupResult triggerIncrementalBackup() {
        return getInstance().performIncrementalBackup();
    }

    public static RestoreResult triggerRestore(String backupId, boolean incremental) {
        if (incremental) {
            return getInstance().restoreIncremental(backupId);
        } else {
            return getInstance().restoreFull(backupId);
        }
    }

    public static String formatBackupInfo(BackupMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e═══════════════════════════════════════════════════════════════\n");
        sb.append("§e  备份信息: §6").append(metadata.id).append("\n");
        sb.append("§e═══════════════════════════════════════════════════════════════\n");
        sb.append("§7类型: §e").append(metadata.type).append("\n");
        sb.append("§7时间: §e").append(metadata.formattedTime).append("\n");
        sb.append("§7状态: §e").append(metadata.status).append("\n");
        sb.append("§7耗时: §e").append(metadata.durationMs).append(" ms\n");
        sb.append("§7大小: §e").append(String.format("%,d", metadata.sizeBytes)).append(" bytes\n");
        sb.append("§7位置数: §e").append(metadata.locationCount).append("\n");
        sb.append("§7玩家数: §e").append(metadata.playerCount).append("\n");
        if (metadata.baseBackupId != null) {
            sb.append("§7基础备份: §e").append(metadata.baseBackupId).append("\n");
        }
        sb.append("§7校验和: §e").append(metadata.checksum).append("\n");
        sb.append("§e═══════════════════════════════════════════════════════════════");
        return sb.toString();
    }

    public static String formatBackupList(List<BackupMetadata> backups) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e═══════════════════════════════════════════════════════════════\n");
        sb.append("§e  Wave Defense 备份列表 (共 ").append(backups.size()).append(" 个)\n");
        sb.append("§e═══════════════════════════════════════════════════════════════\n");
        for (int i = 0; i < Math.min(backups.size(), 20); i++) {
            BackupMetadata bm = backups.get(i);
            String typeColor = bm.type == BackupType.FULL ? "§a" : "§9";
            String statusColor = bm.status == BackupStatus.SUCCESS ? "§a" : "§c";
            sb.append(typeColor).append("  [").append(bm.type.toString().charAt(0)).append("] ");
            sb.append("§e").append(bm.id).append(" ");
            sb.append(statusColor).append(bm.status).append(" ");
            sb.append("§7").append(bm.formattedTime).append(" ");
            sb.append("§6").append(String.format("%,d", bm.sizeBytes)).append("B");
            if (bm.baseBackupId != null) {
                sb.append(" §7(基于: ").append(bm.baseBackupId).append(")");
            }
            sb.append("\n");
        }
        if (backups.size() > 20) {
            sb.append("§7  ... 还有 ").append(backups.size() - 20).append(" 个备份未显示\n");
        }
        sb.append("§e═══════════════════════════════════════════════════════════════");
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────────────────────────────
    public boolean isBackupRunning() {
        return isBackupRunning;
    }

    public boolean isRestoreRunning() {
        return isRestoreRunning;
    }

    public long getLastBackupTime() {
        return lastBackupTime;
    }

    public String getLastBackupId() {
        return lastBackupId;
    }

    public BackupType getLastBackupType() {
        return lastBackupType;
    }
}
