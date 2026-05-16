package com.wavedefense.backup;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.LocationManager;
import com.wavedefense.wave.WaveManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WaveDefenseBackupSystem.
 */
class WaveDefenseBackupSystemTest {

    private WaveDefenseBackupSystem backupSystem;
    private MinecraftServer mockServer;
    private LocationManager mockLocationManager;
    private WaveManager mockWaveManager;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        // Mock static WaveDefenseMod.getServer()
        mockServer = mock(MinecraftServer.class);
        mockLocationManager = mock(LocationManager.class);
        mockWaveManager = mock(WaveManager.class);

        // Initialize the backup system
        backupSystem = WaveDefenseBackupSystem.getInstance();
        
        // Override the backup root to use temp directory
        // Note: In a real test, you'd want to use reflection or modify the system
        // to use a test-specific directory
        
        System.out.println("Backup system initialized for testing");
    }

    @Test
    void testSingletonInstance() {
        WaveDefenseBackupSystem instance1 = WaveDefenseBackupSystem.getInstance();
        WaveDefenseBackupSystem instance2 = WaveDefenseBackupSystem.getInstance();
        
        assertSame(instance1, instance2, "Should return the same instance");
    }

    @Test
    void testInitialization() {
        // Test that initialization doesn't throw exceptions
        assertDoesNotThrow(() -> backupSystem.initialize());
    }

    @Test
    void testFullBackupResult() {
        WaveDefenseBackupSystem.BackupResult result = backupSystem.performFullBackup();
        
        assertNotNull(result, "Backup result should not be null");
        assertNotNull(result.status, "Backup status should not be null");
        assertNotNull(result.message, "Backup message should not be null");
        
        // Since server is not available, it should return FAILED or SKIPPED
        assertTrue(
            result.status == WaveDefenseBackupSystem.BackupStatus.FAILED || result.status == WaveDefenseBackupSystem.BackupStatus.SKIPPED,
            "Backup should be FAILED or SKIPPED when server is not available"
        );
    }

    @Test
    void testIncrementalBackupResult() {
        WaveDefenseBackupSystem.BackupResult result = backupSystem.performIncrementalBackup();
        
        assertNotNull(result, "Backup result should not be null");
        assertNotNull(result.status, "Backup status should not be null");
        assertNotNull(result.message, "Backup message should not be null");
    }

    @Test
    void testBackupMetadata() {
        // Create a test metadata object
        WaveDefenseBackupSystem.BackupMetadata metadata = new WaveDefenseBackupSystem.BackupMetadata();
        metadata.id = "test-backup-001";
        metadata.type = WaveDefenseBackupSystem.BackupType.FULL;
        metadata.timestamp = System.currentTimeMillis();
        metadata.formattedTime = "2024-01-15T14:30:22";
        metadata.durationMs = 1500;
        metadata.status = WaveDefenseBackupSystem.BackupStatus.SUCCESS;
        metadata.sizeBytes = 1024 * 1024; // 1 MB
        metadata.locationCount = 5;
        metadata.playerCount = 10;
        metadata.checksum = "abc123def456";
        metadata.serverVersion = "1.20.1";
        metadata.waveDefenseVersion = "2.0.39";

        // Verify the metadata fields
        assertEquals("test-backup-001", metadata.id);
        assertEquals(WaveDefenseBackupSystem.BackupType.FULL, metadata.type);
        assertEquals(WaveDefenseBackupSystem.BackupStatus.SUCCESS, metadata.status);
        assertEquals(1024 * 1024, metadata.sizeBytes);
        assertEquals(5, metadata.locationCount);
        assertEquals(10, metadata.playerCount);
    }

    @Test
    void testBackupIndex() {
        WaveDefenseBackupSystem.BackupIndex index = new WaveDefenseBackupSystem.BackupIndex();
        
        assertNotNull(index.backups, "Backups list should not be null");
        assertTrue(index.backups.isEmpty(), "New index should have no backups");
        
        // Add a backup
        WaveDefenseBackupSystem.BackupMetadata metadata = new WaveDefenseBackupSystem.BackupMetadata();
        metadata.id = "backup-001";
        metadata.type = WaveDefenseBackupSystem.BackupType.FULL;
        metadata.timestamp = System.currentTimeMillis();
        metadata.sizeBytes = 1000;
        
        index.backups.add(metadata);
        
        assertEquals(1, index.backups.size());
        assertEquals("backup-001", index.backups.get(0).id);
    }

    @Test
    void testChangeSet() {
        WaveDefenseBackupSystem.ChangeSet changeSet = new WaveDefenseBackupSystem.ChangeSet();
        
        assertNotNull(changeSet.addedLocations, "Added locations set should not be null");
        assertNotNull(changeSet.modifiedLocations, "Modified locations set should not be null");
        assertNotNull(changeSet.removedLocations, "Removed locations set should not be null");
        assertNotNull(changeSet.modifiedPlayers, "Modified players set should not be null");
        assertNotNull(changeSet.modifiedWaveStates, "Modified wave states set should not be null");
        
        // Test adding changes
        changeSet.addedLocations.add("location1");
        changeSet.modifiedLocations.add("location2");
        changeSet.modifiedPlayers.add(UUID.randomUUID());
        
        assertEquals(1, changeSet.addedLocations.size());
        assertEquals(1, changeSet.modifiedLocations.size());
        assertEquals(1, changeSet.modifiedPlayers.size());
    }

    @Test
    void testBackupResult() {
        WaveDefenseBackupSystem.BackupResult result = new WaveDefenseBackupSystem.BackupResult();
        
        result.status = WaveDefenseBackupSystem.BackupStatus.SUCCESS;
        result.backupId = "test-backup";
        result.type = WaveDefenseBackupSystem.BackupType.FULL;
        result.durationMs = 1000;
        result.sizeBytes = 5000;
        result.message = "Backup completed successfully";
        
        assertEquals(WaveDefenseBackupSystem.BackupStatus.SUCCESS, result.status);
        assertEquals("test-backup", result.backupId);
        assertEquals(WaveDefenseBackupSystem.BackupType.FULL, result.type);
        assertEquals(1000, result.durationMs);
        assertEquals(5000, result.sizeBytes);
        assertEquals("Backup completed successfully", result.message);
    }

    @Test
    void testRestoreResult() {
        WaveDefenseBackupSystem.RestoreResult result = new WaveDefenseBackupSystem.RestoreResult();
        
        result.status = WaveDefenseBackupSystem.RestoreStatus.SUCCESS;
        result.backupId = "test-backup";
        result.durationMs = 2000;
        result.message = "Restore completed successfully";
        result.restoredLocations.add("location1");
        result.restoredPlayers.add("player1");
        
        assertEquals(WaveDefenseBackupSystem.RestoreStatus.SUCCESS, result.status);
        assertEquals("test-backup", result.backupId);
        assertEquals(2000, result.durationMs);
        assertEquals("Restore completed successfully", result.message);
        assertEquals(1, result.restoredLocations.size());
        assertEquals(1, result.restoredPlayers.size());
    }
}
