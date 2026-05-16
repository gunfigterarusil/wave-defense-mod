package com.wavedefense.wave;

import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.data.WaveMob;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for wave progression logic in LocationSession.
 */
class LocationSessionTest {

    private WaveManager mockWaveManager;
    private Location mockLocation;
    private LocationSession session;

    @BeforeEach
    void setUp() {
        mockWaveManager = mock(WaveManager.class);
        mockLocation = mock(Location.class);

        // Setup location with 3 waves
        List<WaveConfig> waveConfigs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            WaveConfig waveConfig = mock(WaveConfig.class);
            when(waveConfig.getMobs()).thenReturn(new ArrayList<>());
            when(waveConfig.hasWaveSpawnPos()).thenReturn(false);
            waveConfigs.add(waveConfig);
        }

        when(mockLocation.getWaves()).thenReturn(waveConfigs);
        when(mockLocation.getTotalWaves()).thenReturn(10);
        when(mockLocation.getTimeBetweenWaves()).thenReturn(30);
        when(mockLocation.getName()).thenReturn("test_location");
        when(mockLocation.getMobSpawnRadius()).thenReturn(5);
        when(mockLocation.getPlayerSpawn()).thenReturn(new BlockPos(0, 64, 0));
        when(mockLocation.getMobSpawns()).thenReturn(new ArrayList<>());

        session = new LocationSession("test_location", mockLocation);
    }

    @Test
    void testInitialState() {
        assertEquals(1, session.currentWave);
        assertEquals(0, session.waveTimerTicks);
        assertEquals(0, session.startTimerMs);
        assertTrue(session.spawnedMobs.isEmpty());
    }

    @Test
    void testWaveProgressionAfterAllMobsKilled() {
        // Simulate that wave 1 was started and all mobs were killed
        session.spawnedMobs.clear(); // All mobs killed
        session.currentWave = 1;

        // Call tick - should set timer for next wave and increment currentWave
        session.tick(mockWaveManager, mockLocation);

        // Verify that timer was set and currentWave was incremented
        assertEquals(30 * 20, session.waveTimerTicks); // 30 seconds * 20 ticks/second
        assertEquals(2, session.currentWave); // Should have incremented to wave 2
    }

    @Test
    void testWaveProgressionWithZeroDelay() {
        when(mockLocation.getTimeBetweenWaves()).thenReturn(0);

        // Simulate that wave 1 was started and all mobs were killed
        session.spawnedMobs.clear(); // All mobs killed
        session.currentWave = 1;

        // Call tick - should start next wave immediately
        session.tick(mockWaveManager, mockLocation);

        // Verify that timer was not set and currentWave was incremented
        assertEquals(0, session.waveTimerTicks);
        assertEquals(2, session.currentWave); // Should have incremented to wave 2
    }

    @Test
    void testVictoryAfterAllWavesCompleted() {
        // Set currentWave to totalWaves + 1 (all waves completed)
        session.currentWave = 11; // totalWaves is 10
        session.spawnedMobs.clear(); // All mobs killed

        // Call tick - should trigger victory
        session.tick(mockWaveManager, mockLocation);

        // Verify that victory was triggered
        verify(mockWaveManager).triggerVictory("test_location");
    }

    @Test
    void testLobbyTimerExpiration() {
        // Set up lobby timer
        session.startTimerMs = System.currentTimeMillis() - 1000; // Expired 1 second ago
        session.currentWave = 1;

        // Call tick - should start first wave
        session.tick(mockWaveManager, mockLocation);

        // Verify that lobby timer was cleared and first wave was started
        assertEquals(0, session.startTimerMs);
        assertEquals(0, session.waveTimerTicks);
        // Note: startNextWave is called, which would spawn wave 1
    }

    @Test
    void testActiveTimerDoesNotProgressWave() {
        // Set up active wave timer
        session.waveTimerTicks = 100; // 5 seconds remaining
        session.currentWave = 1;

        // Call tick - should decrement timer but not progress wave
        session.tick(mockWaveManager, mockLocation);

        // Verify that timer was decremented but wave was not progressed
        assertEquals(99, session.waveTimerTicks);
        assertEquals(1, session.currentWave);
    }

    @Test
    void testActiveLobbyTimerDoesNotProgressWave() {
        // Set up active lobby timer (in the future)
        session.startTimerMs = System.currentTimeMillis() + 5000; // 5 seconds from now
        session.currentWave = 1;

        // Call tick - should not progress wave
        session.tick(mockWaveManager, mockLocation);

        // Verify that wave was not progressed
        assertEquals(1, session.currentWave);
    }
}
