package com.wavedefense.monitor;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.*;
import com.wavedefense.wave.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive monitoring and statistics system for Wave Defense.
 * Provides real-time metrics collection, performance tracking, alerting capabilities,
 * and detailed analytics for server health and gameplay statistics.
 *
 * Features:
 * - Real-time metrics collection (TPS, memory, player counts, mob counts)
 * - Performance tracking (tick times, spawn times, wave completion times)
 * - Alerting system (threshold-based alerts for performance and gameplay issues)
 * - Historical statistics (rolling windows for trend analysis)
 * - Per-location and per-player analytics
 * - Exportable reports in multiple formats
 *
 * @author Wave Defense Team
 * @version 2.0
 */
public class WaveDefenseMonitor {

    // ============================================================
    //  SINGLETON INSTANCE
    // ============================================================
    private static WaveDefenseMonitor instance;

    public static synchronized WaveDefenseMonitor getInstance() {
        if (instance == null) {
            instance = new WaveDefenseMonitor();
        }
        return instance;
    }

    // ============================================================
    //  CORE MONITORING STATE
    // ============================================================
    // A4 fix: do NOT capture waveCtx at construction time — WaveManager may not
    // be initialised yet (singleton is created lazily on the first server tick).
    // Always resolve it through WaveDefenseMod.waveManager at call time.
    private final boolean monitoringEnabled;
    private volatile long monitorStartTime;
    private volatile long lastTickTime;
    private volatile int totalTicksProcessed;

    // ============================================================
    //  PERFORMANCE METRICS - TICK TIMING
    // ============================================================
    private static final int TICK_HISTORY_SIZE = 1200; // 60 seconds at 20 TPS
    private final Deque<Long> tickDurations = new ArrayDeque<>(TICK_HISTORY_SIZE);
    private final Deque<Long> waveManagerTickDurations = new ArrayDeque<>(TICK_HISTORY_SIZE);
    private final Deque<Long> pvpManagerTickDurations = new ArrayDeque<>(TICK_HISTORY_SIZE);
    private final Deque<Long> boundaryManagerTickDurations = new ArrayDeque<>(TICK_HISTORY_SIZE);

    // Rolling averages
    private final RollingAverage tickTimeAverage = new RollingAverage(100);
    private final RollingAverage waveTickAverage = new RollingAverage(100);

    // ============================================================
    //  GAMEPLAY METRICS - REAL-TIME COUNTERS
    // ============================================================
    private final AtomicLong totalMobsSpawned = new AtomicLong(0);
    private final AtomicLong totalMobsKilled = new AtomicLong(0);
    private final AtomicLong totalWavesCompleted = new AtomicLong(0);
    private final AtomicLong totalPlayerDeaths = new AtomicLong(0);
    private final AtomicLong totalPvpKills = new AtomicLong(0);
    private final AtomicLong totalItemsDropped = new AtomicLong(0);
    private final AtomicLong totalPointsAwarded = new AtomicLong(0);

    // Per-tick deltas
    private int mobsSpawnedThisTick = 0;
    private int mobsKilledThisTick = 0;
    private int wavesCompletedThisTick = 0;

    // ============================================================
    //  HISTORICAL DATA - ROLLING WINDOWS
    // ============================================================
    private static final int HISTORY_WINDOW_TICKS = 72000; // 1 hour at 20 TPS
    private final Deque<HistoricalSnapshot> historicalData = new ArrayDeque<>(HISTORY_WINDOW_TICKS);

    // Per-location historical data
    private final Map<String, LocationHistory> locationHistories = new ConcurrentHashMap<>();

    // Per-player statistics
    private final Map<UUID, PlayerStatistics> playerStatistics = new ConcurrentHashMap<>();

    // ============================================================
    //  ALERTING SYSTEM
    // ============================================================
    private final List<AlertRule> alertRules = new ArrayList<>();
    private final Deque<Alert> activeAlerts = new ArrayDeque<>();
    private final Deque<Alert> alertHistory = new ArrayDeque<>(1000);
    private volatile long lastAlertCheck = 0;
    private static final long ALERT_CHECK_INTERVAL_MS = 5000; // Check every 5 seconds

    // Default alert thresholds
    private static final double TPS_WARNING_THRESHOLD = 18.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;
    private static final long MEMORY_WARNING_THRESHOLD_MB = 8192; // 8GB
    private static final long MEMORY_CRITICAL_THRESHOLD_MB = 12288; // 12GB
    private static final int WAVE_TIMEOUT_THRESHOLD_SECONDS = 300; // 5 minutes
    private static final int PLAYER_IDLE_THRESHOLD_SECONDS = 300; // 5 minutes
    private static final int MOB_SPAWN_LAG_THRESHOLD = 50; // 50 mobs behind

    // ============================================================
    //  PERFORMANCE TRACKING - WAVE EXECUTION
    // ============================================================
    private final Map<String, WaveExecutionMetrics> waveExecutionMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> activeWaveStartTimes = new ConcurrentHashMap<>();

    // ============================================================
    //  MEMORY MONITORING
    // ============================================================
    private final Runtime runtime = Runtime.getRuntime();
    private final Deque<MemorySnapshot> memoryHistory = new ArrayDeque<>(600); // 5 minutes

    // ============================================================
    //  PLAYER ACTIVITY TRACKING
    // ============================================================
    private final Map<UUID, PlayerActivity> playerActivity = new ConcurrentHashMap<>();
    private final Deque<PlayerSession> playerSessions = new ArrayDeque<>();

    // ============================================================
    //  CONSTRUCTOR
    // ============================================================
    /** Returns the current WaveContext, or null if WaveManager is not yet initialised. */
    private static WaveContext waveCtx() {
        return WaveDefenseMod.waveManager != null ? WaveDefenseMod.waveManager.waveCtx : null;
    }

    private WaveDefenseMonitor() {
        this.monitoringEnabled = true;
        this.monitorStartTime = System.currentTimeMillis();
        this.lastTickTime = System.currentTimeMillis();
        this.totalTicksProcessed = 0;

        // Initialize default alert rules
        initializeDefaultAlertRules();

        WaveDefenseMod.LOGGER.info("WaveDefenseMonitor initialized - Monitoring enabled");
    }

    // ============================================================
    //  TICK PROCESSING - MAIN MONITORING LOOP
    // ============================================================
    /**
     * Called every server tick to collect metrics and check alerts.
     * This is the primary monitoring entry point.
     */
    public void onServerTick() {
        if (!monitoringEnabled) return;

        long tickStartTime = System.nanoTime();
        long currentTime = System.currentTimeMillis();
        totalTicksProcessed++;

        // Calculate tick duration
        long tickDuration = currentTime - lastTickTime;
        lastTickTime = currentTime;
        tickDurations.addLast((long) tickDuration);
        tickTimeAverage.add(tickDuration);

        // Trim history to size
        if (tickDurations.size() > TICK_HISTORY_SIZE) {
            tickDurations.removeFirst();
        }

        // Collect gameplay metrics
        collectGameplayMetrics();

        // Update player activity
        updatePlayerActivity();

        // Memory monitoring (every 20 ticks = 1 second)
        if (totalTicksProcessed % 20 == 0) {
            collectMemorySnapshot();
            cleanupOldData();
        }

        // Historical snapshot (every 100 ticks = 5 seconds)
        if (totalTicksProcessed % 100 == 0) {
            takeHistoricalSnapshot();
        }

        // Alert checking (every 5 seconds)
        if (currentTime - lastAlertCheck >= ALERT_CHECK_INTERVAL_MS) {
            checkAlerts();
            lastAlertCheck = currentTime;
        }

        // Calculate tick processing time
        long tickEndTime = System.nanoTime();
        long processingTime = (tickEndTime - tickStartTime) / 1_000_000; // Convert to ms
        if (processingTime > 50) { // Log if processing takes > 50ms
            WaveDefenseMod.LOGGER.warn("Monitor tick processing took {}ms", processingTime);
        }
    }

    // ============================================================
    //  METRICS COLLECTION
    // ============================================================
    private void collectGameplayMetrics() {
        if (waveCtx() == null) return;

        // Update per-location metrics
        for (LocationSession session : waveCtx().sessions.values()) {
            String locName = session.locationName;
            locationHistories.computeIfAbsent(locName, k -> new LocationHistory(locName))
                .update(session);
        }

        // Update wave execution metrics
        for (Map.Entry<String, Long> entry : activeWaveStartTimes.entrySet()) {
            String locName = entry.getKey();
            long startTime = entry.getValue();
            long duration = System.currentTimeMillis() - startTime;

            WaveExecutionMetrics metrics = waveExecutionMetrics.computeIfAbsent(
                locName, k -> new WaveExecutionMetrics(locName));
            metrics.updateActiveWaveDuration(duration);
        }
    }

    private void updatePlayerActivity() {
        if (WaveDefenseMod.getServer() == null) return;

        for (ServerPlayer player : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            PlayerActivity activity = playerActivity.computeIfAbsent(playerId, k -> new PlayerActivity(playerId));
            activity.update(player);
        }
    }

    private void collectMemorySnapshot() {
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);

        MemorySnapshot snapshot = new MemorySnapshot(
            System.currentTimeMillis(),
            usedMemory,
            maxMemory,
            freeMemory,
            runtime.totalMemory() / (1024 * 1024)
        );
        memoryHistory.addLast(snapshot);

        if (memoryHistory.size() > 600) {
            memoryHistory.removeFirst();
        }
    }

    // ============================================================
    //  HISTORICAL DATA MANAGEMENT
    // ============================================================
    private void takeHistoricalSnapshot() {
        HistoricalSnapshot snapshot = new HistoricalSnapshot(
            System.currentTimeMillis(),
            getOnlinePlayerCount(),
            getTotalActiveLocations(),
            getTotalActiveMobs(),
            getAverageTPS(),
            totalMobsKilled.get(),
            totalWavesCompleted.get(),
            totalPvpKills.get()
        );
        historicalData.addLast(snapshot);

        if (historicalData.size() > HISTORY_WINDOW_TICKS) {
            historicalData.removeFirst();
        }
    }

    private void cleanupOldData() {
        // Clean up old player activity (24 hours)
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        playerActivity.entrySet().removeIf(entry ->
            entry.getValue().getLastActivityTime() < cutoffTime);

        // Clean up old alerts (7 days)
        long alertCutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
        alertHistory.removeIf(alert -> alert.getTimestamp() < alertCutoff);
    }

    // ============================================================
    //  ALERT SYSTEM
    // ============================================================
    private void initializeDefaultAlertRules() {
        // TPS alerts — В6: use argsSupplier so values are current when the alert fires
        alertRules.add(new AlertRule(
            "LOW_TPS_WARNING",
            "TPS below warning threshold",
            () -> getAverageTPS() < TPS_WARNING_THRESHOLD,
            AlertSeverity.WARNING,
            "Current TPS: %.1f (threshold: %.1f)",
            () -> new Object[]{ getAverageTPS(), TPS_WARNING_THRESHOLD }
        ));

        alertRules.add(new AlertRule(
            "LOW_TPS_CRITICAL",
            "TPS below critical threshold",
            () -> getAverageTPS() < TPS_CRITICAL_THRESHOLD,
            AlertSeverity.CRITICAL,
            "Current TPS: %.1f (threshold: %.1f) - Server performance severely degraded!",
            () -> new Object[]{ getAverageTPS(), TPS_CRITICAL_THRESHOLD }
        ));

        // Memory alerts — В6: same dynamic-args pattern
        alertRules.add(new AlertRule(
            "HIGH_MEMORY_WARNING",
            "Memory usage above warning threshold",
            () -> getUsedMemoryMB() > MEMORY_WARNING_THRESHOLD_MB,
            AlertSeverity.WARNING,
            "Memory usage: %dMB / %dMB (threshold: %dMB)",
            () -> new Object[]{ getUsedMemoryMB(), getMaxMemoryMB(), MEMORY_WARNING_THRESHOLD_MB }
        ));

        alertRules.add(new AlertRule(
            "HIGH_MEMORY_CRITICAL",
            "Memory usage above critical threshold",
            () -> getUsedMemoryMB() > MEMORY_CRITICAL_THRESHOLD_MB,
            AlertSeverity.CRITICAL,
            "Memory usage: %dMB / %dMB (threshold: %dMB) - Risk of OutOfMemoryError!",
            () -> new Object[]{ getUsedMemoryMB(), getMaxMemoryMB(), MEMORY_CRITICAL_THRESHOLD_MB }
        ));

        // Wave timeout alerts
        alertRules.add(new AlertRule(
            "WAVE_TIMEOUT",
            "Wave taking too long to complete",
            this::checkWaveTimeouts,
            AlertSeverity.WARNING,
            "Wave has been active for more than %d seconds",
            WAVE_TIMEOUT_THRESHOLD_SECONDS
        ));

        // Player idle alerts
        alertRules.add(new AlertRule(
            "PLAYER_IDLE",
            "Player appears to be idle",
            this::checkPlayerIdle,
            AlertSeverity.INFO,
            "Player %s has been idle for more than %d seconds",
            "", PLAYER_IDLE_THRESHOLD_SECONDS
        ));

        // Mob spawn lag alerts
        alertRules.add(new AlertRule(
            "MOB_SPAWN_LAG",
            "Mob spawn rate below expected",
            this::checkMobSpawnLag,
            AlertSeverity.WARNING,
            "Mob spawn rate is %d%% below expected",
            0 // Will be calculated
        ));
    }

    private void checkAlerts() {
        for (AlertRule rule : alertRules) {
            if (rule.evaluate()) {
                Alert alert = new Alert(
                    rule.getId(),
                    rule.getDescription(),
                    rule.getSeverity(),
                    System.currentTimeMillis(),
                    String.format(rule.getMessage(), rule.getArgs())
                );

                // Check if this alert is already active
                boolean alreadyActive = activeAlerts.stream()
                    .anyMatch(a -> a.getRuleId().equals(rule.getId()));

                if (!alreadyActive) {
                    activeAlerts.addLast(alert);
                    alertHistory.addLast(alert);

                    // Log critical alerts
                    if (rule.getSeverity() == AlertSeverity.CRITICAL) {
                        WaveDefenseMod.LOGGER.error("CRITICAL ALERT: {}", alert.getMessage());
                    } else if (rule.getSeverity() == AlertSeverity.WARNING) {
                        WaveDefenseMod.LOGGER.warn("WARNING ALERT: {}", alert.getMessage());
                    }

                    // Broadcast to admins
                    broadcastAlert(alert);
                }
            }
        }

        // Clear resolved alerts
        activeAlerts.removeIf(alert -> {
            AlertRule rule = findAlertRule(alert.getRuleId());
            return rule != null && !rule.evaluate();
        });
    }

    private boolean checkWaveTimeouts() {
        if (waveCtx() == null) return false;

        for (LocationSession session : waveCtx().sessions.values()) {
            if (session.waveTimerTicks > 0) {
                long secondsRemaining = session.waveTimerTicks / 20;
                long waveDuration = (System.currentTimeMillis() - session.getWaveStartTime()) / 1000;
                if (waveDuration > WAVE_TIMEOUT_THRESHOLD_SECONDS) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPlayerIdle() {
        // Implementation would check player movement/activity
        return false;
    }

    private boolean checkMobSpawnLag() {
        if (waveCtx() == null) return false;

        for (LocationSession session : waveCtx().sessions.values()) {
            if (session.spawnedMobs.size() > MOB_SPAWN_LAG_THRESHOLD) {
                // Check if mobs have been pending for too long
                return true;
            }
        }
        return false;
    }

    private AlertRule findAlertRule(String ruleId) {
        return alertRules.stream()
            .filter(r -> r.getId().equals(ruleId))
            .findFirst()
            .orElse(null);
    }

    private void broadcastAlert(Alert alert) {
        if (WaveDefenseMod.getServer() == null) return;

        Component message = Component.translatable("wavedefense.auto.alert_s_s_942c0f5f", alert.getSeverity(), alert.getMessage()
        );

        for (ServerPlayer player : WaveDefenseMod.getServer().getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) { // Op level 2+
                player.displayClientMessage(message, false);
            }
        }
    }

    // ============================================================
    //  WAVE EXECUTION TRACKING
    // ============================================================
    public void onWaveStart(String locationName, int waveNumber) {
        String key = locationName + "_" + waveNumber;
        activeWaveStartTimes.put(key, System.currentTimeMillis());

        WaveExecutionMetrics metrics = waveExecutionMetrics.computeIfAbsent(
            locationName, k -> new WaveExecutionMetrics(locationName));
        metrics.incrementWaveCount();
        metrics.setCurrentWave(waveNumber);

        WaveDefenseMod.LOGGER.info("Wave started - Location: {}, Wave: {}", locationName, waveNumber);
    }

    public void onWaveComplete(String locationName, int waveNumber) {
        String key = locationName + "_" + waveNumber;
        Long startTime = activeWaveStartTimes.remove(key);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            WaveExecutionMetrics metrics = waveExecutionMetrics.get(locationName);
            if (metrics != null) {
                metrics.recordWaveDuration(duration);
            }

            totalWavesCompleted.incrementAndGet();
            wavesCompletedThisTick++;

            WaveDefenseMod.LOGGER.info("Wave completed - Location: {}, Wave: {}, Duration: {}ms",
                locationName, waveNumber, duration);
        }
    }

    public void onMobSpawned(String locationName) {
        totalMobsSpawned.incrementAndGet();
        mobsSpawnedThisTick++;

        WaveExecutionMetrics metrics = waveExecutionMetrics.get(locationName);
        if (metrics != null) {
            metrics.incrementMobsSpawned();
        }
    }

    public void onMobKilled(String locationName) {
        totalMobsKilled.incrementAndGet();
        mobsKilledThisTick++;

        WaveExecutionMetrics metrics = waveExecutionMetrics.get(locationName);
        if (metrics != null) {
            metrics.incrementMobsKilled();
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        totalPlayerDeaths.incrementAndGet();

        PlayerStatistics stats = playerStatistics.computeIfAbsent(
            player.getUUID(), k -> new PlayerStatistics(player.getUUID()));
        stats.incrementDeaths();

        WaveDefenseMod.LOGGER.info("Player death - Player: {}, Total deaths: {}",
            player.getName().getString(), totalPlayerDeaths.get());
    }

    public void onPvpKill(ServerPlayer killer, ServerPlayer victim) {
        totalPvpKills.incrementAndGet();

        PlayerStatistics killerStats = playerStatistics.computeIfAbsent(
            killer.getUUID(), k -> new PlayerStatistics(killer.getUUID()));
        killerStats.incrementPvpKills();

        PlayerStatistics victimStats = playerStatistics.computeIfAbsent(
            victim.getUUID(), k -> new PlayerStatistics(victim.getUUID()));
        victimStats.incrementPvpDeaths();

        WaveDefenseMod.LOGGER.info("PvP kill - Killer: {}, Victim: {}",
            killer.getName().getString(), victim.getName().getString());
    }

    public void onPointsAwarded(UUID playerId, int points) {
        totalPointsAwarded.addAndGet(points);

        PlayerStatistics stats = playerStatistics.computeIfAbsent(playerId, k -> new PlayerStatistics(playerId));
        stats.addPoints(points);
    }

    public void onItemDropped() {
        totalItemsDropped.incrementAndGet();
    }

    // ============================================================
    //  PLAYER SESSION TRACKING
    // ============================================================
    public void onPlayerJoin(ServerPlayer player) {
        PlayerSession session = new PlayerSession(player.getUUID(), player.getName().getString());
        playerSessions.addLast(session);

        PlayerStatistics stats = playerStatistics.computeIfAbsent(
            player.getUUID(), k -> new PlayerStatistics(player.getUUID()));
        stats.incrementSessionsPlayed();

        WaveDefenseMod.LOGGER.info("Player joined - Player: {}, Total sessions: {}",
            player.getName().getString(), stats.getSessionsPlayed());
    }

    public void onPlayerLeave(ServerPlayer player) {
        PlayerSession session = playerSessions.stream()
            .filter(s -> s.getPlayerId().equals(player.getUUID()) && s.getEndTime() == 0)
            .findFirst()
            .orElse(null);

        if (session != null) {
            session.end();
        }

        WaveDefenseMod.LOGGER.info("Player left - Player: {}", player.getName().getString());
    }

    // ============================================================
    //  METRICS ACCESSORS
    // ============================================================
    public double getAverageTPS() {
        if (tickDurations.isEmpty()) return 20.0;

        double avgTickTime = tickDurations.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(50.0);

        return Math.min(20.0, 1000.0 / Math.max(avgTickTime, 1.0));
    }

    public double getAverageWaveManagerTPS() {
        if (waveManagerTickDurations.isEmpty()) return 20.0;

        double avgTickTime = waveManagerTickDurations.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(50.0);

        return Math.min(20.0, 1000.0 / Math.max(avgTickTime, 1.0));
    }

    public long getUsedMemoryMB() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    public long getMaxMemoryMB() {
        return runtime.maxMemory() / (1024 * 1024);
    }

    public long getFreeMemoryMB() {
        return runtime.freeMemory() / (1024 * 1024);
    }

    public long getTotalMemoryMB() {
        return runtime.totalMemory() / (1024 * 1024);
    }

    public int getOnlinePlayerCount() {
        if (WaveDefenseMod.getServer() == null) return 0;
        return WaveDefenseMod.getServer().getPlayerList().getPlayerCount();
    }

    public int getTotalActiveLocations() {
        WaveContext ctx = waveCtx();
        return ctx != null ? ctx.sessions.size() : 0;
    }

    public int getTotalActiveMobs() {
        WaveContext ctx = waveCtx();
        if (ctx == null) return 0;
        return ctx.sessions.values().stream()
            .mapToInt(s -> s.spawnedMobs.size())
            .sum();
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - monitorStartTime) / 1000;
    }

    public long getTotalTicksProcessed() {
        return totalTicksProcessed;
    }

    public long getTotalMobsSpawned() {
        return totalMobsSpawned.get();
    }

    public long getTotalMobsKilled() {
        return totalMobsKilled.get();
    }

    public long getTotalWavesCompleted() {
        return totalWavesCompleted.get();
    }

    public long getTotalPlayerDeaths() {
        return totalPlayerDeaths.get();
    }

    public long getTotalPvpKills() {
        return totalPvpKills.get();
    }

    public long getTotalItemsDropped() {
        return totalItemsDropped.get();
    }

    public long getTotalPointsAwarded() {
        return totalPointsAwarded.get();
    }

    // ============================================================
    //  REPORT GENERATION
    // ============================================================
    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("  WAVE DEFENSE MONITOR - SUMMARY REPORT\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(String.format("  Generated: %s\n",
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(Instant.ofEpochMilli(System.currentTimeMillis())
                    .atZone(ZoneId.systemDefault()))));
        sb.append(String.format("  Uptime: %s\n", formatDuration(getUptimeSeconds())));
        sb.append("\n");

        // Server Performance
        sb.append("  ┌─ SERVER PERFORMANCE ──────────────────────────────────────┐\n");
        sb.append(String.format("  │  TPS:          %.1f (avg over %d ticks)\n",
            getAverageTPS(), tickDurations.size()));
        sb.append(String.format("  │  Memory:       %dMB / %dMB (%.1f%%)\n",
            getUsedMemoryMB(), getMaxMemoryMB(),
            (getUsedMemoryMB() * 100.0 / getMaxMemoryMB())));
        sb.append(String.format("  │  Online:       %d players\n", getOnlinePlayerCount()));
        sb.append(String.format("  │  Active Waves: %d locations\n", getTotalActiveLocations()));
        sb.append(String.format("  │  Active Mobs:  %d\n", getTotalActiveMobs()));
        sb.append("  └─────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");

        // Gameplay Statistics
        sb.append("  ┌─ GAMEPLAY STATISTICS ──────────────────────────────────────┐\n");
        sb.append(String.format("  │  Waves Completed:  %,d\n", getTotalWavesCompleted()));
        sb.append(String.format("  │  Mobs Spawned:     %,d\n", getTotalMobsSpawned()));
        sb.append(String.format("  │  Mobs Killed:      %,d\n", getTotalMobsKilled()));
        sb.append(String.format("  │  Player Deaths:    %,d\n", getTotalPlayerDeaths()));
        sb.append(String.format("  │  PvP Kills:        %,d\n", getTotalPvpKills()));
        sb.append(String.format("  │  Points Awarded:   %,d\n", getTotalPointsAwarded()));
        sb.append("  └─────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");

        // Active Alerts
        sb.append("  ┌─ ACTIVE ALERTS ─────────────────────────────────────────────┐\n");
        if (activeAlerts.isEmpty()) {
            sb.append("  │  No active alerts\n");
        } else {
            for (Alert alert : activeAlerts) {
                sb.append(String.format("  │  [%s] %s\n",
                    alert.getSeverity(), alert.getMessage()));
            }
        }
        sb.append("  └─────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");

        // Per-Location Status
        WaveContext _ctx = waveCtx();
        if (_ctx != null && !_ctx.sessions.isEmpty()) {
            sb.append("  ┌─ ACTIVE LOCATIONS ─────────────────────────────────────────┐\n");
            for (LocationSession session : _ctx.sessions.values()) {
                sb.append(String.format("  │  %s: Wave %d, %d mobs alive, %d players\n",
                    session.locationName,
                    session.currentWave,
                    session.spawnedMobs.size(),
                    getPlayersInLocation(session.locationName).size()));
            }
            sb.append("  └─────────────────────────────────────────────────────────────┘\n");
            sb.append("\n");
        }

        // Top Players
        if (!playerStatistics.isEmpty()) {
            sb.append("  ┌─ TOP PLAYERS (by points) ─────────────────────────────────┐\n");
            playerStatistics.values().stream()
                .sorted((a, b) -> Long.compare(b.getTotalPoints(), a.getTotalPoints()))
                .limit(5)
                .forEach(stats -> {
                    String playerName = getPlayerName(stats.getPlayerId());
                    sb.append(String.format("  │  %s: %,d points (%d kills, %d deaths)\n",
                        playerName, stats.getTotalPoints(),
                        stats.getMobsKilled(), stats.getDeaths()));
                });
            sb.append("  └─────────────────────────────────────────────────────────────┘\n");
            sb.append("\n");
        }

        sb.append("═══════════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    public String generateDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateSummaryReport());
        sb.append("\n");

        // Historical Trends
        sb.append("  ┌─ HISTORICAL TRENDS (Last Hour) ────────────────────────────┐\n");
        if (!historicalData.isEmpty()) {
            HistoricalSnapshot first = historicalData.peekFirst();
            HistoricalSnapshot last = historicalData.peekLast();

            if (first != null && last != null) {
                long timeSpan = (last.getTimestamp() - first.getTimestamp()) / 1000;
                sb.append(String.format("  │  Time Span: %s\n", formatDuration(timeSpan)));
                sb.append(String.format("  │  Waves Completed: %,d\n",
                    last.getTotalWavesCompleted() - first.getTotalWavesCompleted()));
                sb.append(String.format("  │  Mobs Killed: %,d\n",
                    last.getTotalMobsKilled() - first.getTotalMobsKilled()));
                sb.append(String.format("  │  Avg TPS: %.1f\n",
                    historicalData.stream()
                        .mapToDouble(HistoricalSnapshot::getAverageTPS)
                        .average()
                        .orElse(20.0)));
            }
        }
        sb.append("  └─────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");

        // Wave Execution Metrics
        if (!waveExecutionMetrics.isEmpty()) {
            sb.append("  ┌─ WAVE EXECUTION METRICS ───────────────────────────────────┐\n");
            for (WaveExecutionMetrics metrics : waveExecutionMetrics.values()) {
                sb.append(String.format("  │  %s:\n", metrics.getLocationName()));
                sb.append(String.format("  │    Waves: %,d\n", metrics.getWaveCount()));
                sb.append(String.format("  │    Avg Duration: %s\n",
                    formatDuration(metrics.getAverageWaveDuration() / 1000)));
                sb.append(String.format("  │    Mobs Spawned: %,d\n", metrics.getTotalMobsSpawned()));
                sb.append(String.format("  │    Mobs Killed: %,d\n", metrics.getTotalMobsKilled()));
            }
            sb.append("  └─────────────────────────────────────────────────────────────┘\n");
            sb.append("\n");
        }

        // Memory History
        sb.append("  ┌─ MEMORY HISTORY (Last 5 Minutes) ─────────────────────────┐\n");
        if (!memoryHistory.isEmpty()) {
            MemorySnapshot oldest = memoryHistory.peekFirst();
            MemorySnapshot newest = memoryHistory.peekLast();
            if (oldest != null && newest != null) {
                sb.append(String.format("  │  Start: %dMB used\n", oldest.getUsedMemoryMB()));
                sb.append(String.format("  │  End:   %dMB used\n", newest.getUsedMemoryMB()));
                sb.append(String.format("  │  Change: %+dMB\n",
                    newest.getUsedMemoryMB() - oldest.getUsedMemoryMB()));
            }
        }
        sb.append("  └─────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");

        // Alert History
        if (!alertHistory.isEmpty()) {
            sb.append("  ┌─ RECENT ALERTS (Last 24 Hours) ───────────────────────────┐\n");
            long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            alertHistory.stream()
                .filter(a -> a.getTimestamp() > cutoff)
                .forEach(alert -> {
                    sb.append(String.format("  │  [%s] %s: %s\n",
                        alert.getSeverity(),
                        DateTimeFormatter.ofPattern("HH:mm:ss")
                            .format(Instant.ofEpochMilli(alert.getTimestamp())
                                .atZone(ZoneId.systemDefault())),
                        alert.getMessage()));
                });
            sb.append("  └─────────────────────────────────────────────────────────────┘\n");
            sb.append("\n");
        }

        return sb.toString();
    }

    // ============================================================
    //  HELPER METHODS
    // ============================================================
    private List<ServerPlayer> getPlayersInLocation(String locationName) {
        if (WaveDefenseMod.getServer() == null) return Collections.emptyList();

        return WaveDefenseMod.getServer().getPlayerList().getPlayers().stream()
            .filter(p -> {
                PlayerWaveData data = WaveDefenseMod.waveManager.getPlayerData(p.getUUID());
                return data != null && data.getCurrentLocation() != null
                    && data.getCurrentLocation().getName().equals(locationName);
            })
            .collect(Collectors.toList());
    }

    public String getPlayerName(UUID playerId) {
        if (WaveDefenseMod.getServer() == null) return playerId.toString();

        ServerPlayer player = WaveDefenseMod.getServer().getPlayerList().getPlayer(playerId);
        return player != null ? player.getName().getString() : playerId.toString();
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        if (seconds < 86400) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
    }

    // ============================================================
    //  GETTERS
    // ============================================================
    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public long getMonitorStartTime() {
        return monitorStartTime;
    }

    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    public List<Alert> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    public Map<String, LocationHistory> getLocationHistories() {
        return new HashMap<>(locationHistories);
    }

    public Map<UUID, PlayerStatistics> getPlayerStatistics() {
        return new HashMap<>(playerStatistics);
    }

    public List<HistoricalSnapshot> getHistoricalData() {
        return new ArrayList<>(historicalData);
    }

    public List<MemorySnapshot> getMemoryHistory() {
        return new ArrayList<>(memoryHistory);
    }

    public Map<String, WaveExecutionMetrics> getWaveExecutionMetrics() {
        return new HashMap<>(waveExecutionMetrics);
    }

    public RollingAverage getTickTimeAverage() {
        return tickTimeAverage;
    }

    // ============================================================
    //  INNER CLASSES - DATA STRUCTURES
    // ============================================================

    /**
     * Represents a historical snapshot of server state.
     */
    public static class HistoricalSnapshot {
        private final long timestamp;
        private final int onlinePlayers;
        private final int activeLocations;
        private final int activeMobs;
        private final double averageTPS;
        private final long totalMobsKilled;
        private final long totalWavesCompleted;
        private final long totalPvpKills;

        public HistoricalSnapshot(long timestamp, int onlinePlayers, int activeLocations,
                                  int activeMobs, double averageTPS, long totalMobsKilled,
                                  long totalWavesCompleted, long totalPvpKills) {
            this.timestamp = timestamp;
            this.onlinePlayers = onlinePlayers;
            this.activeLocations = activeLocations;
            this.activeMobs = activeMobs;
            this.averageTPS = averageTPS;
            this.totalMobsKilled = totalMobsKilled;
            this.totalWavesCompleted = totalWavesCompleted;
            this.totalPvpKills = totalPvpKills;
        }

        public long getTimestamp() { return timestamp; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public int getActiveLocations() { return activeLocations; }
        public int getActiveMobs() { return activeMobs; }
        public double getAverageTPS() { return averageTPS; }
        public long getTotalMobsKilled() { return totalMobsKilled; }
        public long getTotalWavesCompleted() { return totalWavesCompleted; }
        public long getTotalPvpKills() { return totalPvpKills; }
    }

    /**
     * Represents a memory usage snapshot.
     */
    public static class MemorySnapshot {
        private final long timestamp;
        private final long usedMemoryMB;
        private final long maxMemoryMB;
        private final long freeMemoryMB;
        private final long totalMemoryMB;

        public MemorySnapshot(long timestamp, long usedMemoryMB, long maxMemoryMB,
                              long freeMemoryMB, long totalMemoryMB) {
            this.timestamp = timestamp;
            this.usedMemoryMB = usedMemoryMB;
            this.maxMemoryMB = maxMemoryMB;
            this.freeMemoryMB = freeMemoryMB;
            this.totalMemoryMB = totalMemoryMB;
        }

        public long getTimestamp() { return timestamp; }
        public long getUsedMemoryMB() { return usedMemoryMB; }
        public long getMaxMemoryMB() { return maxMemoryMB; }
        public long getFreeMemoryMB() { return freeMemoryMB; }
        public long getTotalMemoryMB() { return totalMemoryMB; }
    }

    /**
     * Tracks wave execution metrics for a specific location.
     */
    public static class WaveExecutionMetrics {
        private final String locationName;
        private long waveCount;
        private long totalWaveDuration;
        private long minWaveDuration = Long.MAX_VALUE;
        private long maxWaveDuration = 0;
        private long totalMobsSpawned;
        private long totalMobsKilled;
        private int currentWave;
        private long lastWaveStartTime;

        public WaveExecutionMetrics(String locationName) {
            this.locationName = locationName;
        }

        public synchronized void recordWaveDuration(long durationMs) {
            waveCount++;
            totalWaveDuration += durationMs;
            minWaveDuration = Math.min(minWaveDuration, durationMs);
            maxWaveDuration = Math.max(maxWaveDuration, durationMs);
        }

        public synchronized void updateActiveWaveDuration(long durationMs) {
            lastWaveStartTime = System.currentTimeMillis() - durationMs;
        }

        public synchronized void incrementWaveCount() {
            waveCount++;
            lastWaveStartTime = System.currentTimeMillis();
        }

        public synchronized void incrementMobsSpawned() {
            totalMobsSpawned++;
        }

        public synchronized void incrementMobsKilled() {
            totalMobsKilled++;
        }

        public synchronized void setCurrentWave(int wave) {
            this.currentWave = wave;
        }

        public String getLocationName() { return locationName; }
        public long getWaveCount() { return waveCount; }
        public long getTotalWaveDuration() { return totalWaveDuration; }
        public long getAverageWaveDuration() {
            return waveCount > 0 ? totalWaveDuration / waveCount : 0;
        }
        public long getMinWaveDuration() { return minWaveDuration; }
        public long getMaxWaveDuration() { return maxWaveDuration; }
        public long getTotalMobsSpawned() { return totalMobsSpawned; }
        public long getTotalMobsKilled() { return totalMobsKilled; }
        public int getCurrentWave() { return currentWave; }
    }

    /**
     * Tracks location-specific historical data.
     */
    public static class LocationHistory {
        private final String locationName;
        private long totalWavesCompleted;
        private long totalMobsSpawned;
        private long totalMobsKilled;
        private long totalPlayerDeaths;
        private long totalPointsAwarded;
        private final Map<UUID, Long> playerPoints = new ConcurrentHashMap<>();
        private long lastUpdateTime;

        public LocationHistory(String locationName) {
            this.locationName = locationName;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void update(LocationSession session) {
            this.totalWavesCompleted = session.currentWave - 1;
            this.totalMobsSpawned = session.spawnedMobs.size();
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public synchronized void addPlayerPoints(UUID playerId, long points) {
            playerPoints.merge(playerId, points, Long::sum);
            totalPointsAwarded += points;
        }

        public synchronized void incrementMobsKilled() {
            totalMobsKilled++;
        }

        public synchronized void incrementPlayerDeaths() {
            totalPlayerDeaths++;
        }

        public String getLocationName() { return locationName; }
        public long getTotalWavesCompleted() { return totalWavesCompleted; }
        public long getTotalMobsSpawned() { return totalMobsSpawned; }
        public long getTotalMobsKilled() { return totalMobsKilled; }
        public long getTotalPlayerDeaths() { return totalPlayerDeaths; }
        public long getTotalPointsAwarded() { return totalPointsAwarded; }
        public Map<UUID, Long> getPlayerPoints() { return new HashMap<>(playerPoints); }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }

    /**
     * Tracks individual player statistics.
     */
    public static class PlayerStatistics {
        private final UUID playerId;
        private long sessionsPlayed;
        private long mobsKilled;
        private long deaths;
        private long pvpKills;
        private long pvpDeaths;
        private long totalPoints;
        private long lastActivityTime;

        public PlayerStatistics(UUID playerId) {
            this.playerId = playerId;
            this.sessionsPlayed = 0;
            this.mobsKilled = 0;
            this.deaths = 0;
            this.pvpKills = 0;
            this.pvpDeaths = 0;
            this.totalPoints = 0;
            this.lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void incrementSessionsPlayed() {
            sessionsPlayed++;
        }

        public synchronized void incrementMobsKilled() {
            mobsKilled++;
            lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void incrementDeaths() {
            deaths++;
            lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void incrementPvpKills() {
            pvpKills++;
            lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void incrementPvpDeaths() {
            pvpDeaths++;
            lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void addPoints(long points) {
            totalPoints += points;
            lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void updateLastActivity() {
            lastActivityTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public long getSessionsPlayed() { return sessionsPlayed; }
        public long getMobsKilled() { return mobsKilled; }
        public long getDeaths() { return deaths; }
        public long getPvpKills() { return pvpKills; }
        public long getPvpDeaths() { return pvpDeaths; }
        public long getTotalPoints() { return totalPoints; }
        public long getLastActivityTime() { return lastActivityTime; }
    }

    /**
     * Tracks player activity and session data.
     */
    public static class PlayerActivity {
        private final UUID playerId;
        private long lastActivityTime;
        private int ticksSinceLastAction;
        private BlockPos lastPosition;
        private GameType lastGameMode;

        public PlayerActivity(UUID playerId) {
            this.playerId = playerId;
            this.lastActivityTime = System.currentTimeMillis();
            this.ticksSinceLastAction = 0;
        }

        public synchronized void update(ServerPlayer player) {
            long now = System.currentTimeMillis();
            if (lastPosition == null || !lastPosition.equals(player.blockPosition())) {
                ticksSinceLastAction = 0;
                lastPosition = player.blockPosition();
            } else {
                ticksSinceLastAction++;
            }
            lastGameMode = player.gameMode.getGameModeForPlayer();
            lastActivityTime = now;
        }

        public UUID getPlayerId() { return playerId; }
        public long getLastActivityTime() { return lastActivityTime; }
        public int getTicksSinceLastAction() { return ticksSinceLastAction; }
        public BlockPos getLastPosition() { return lastPosition; }
        public GameType getLastGameMode() { return lastGameMode; }
    }

    /**
     * Represents a player session.
     */
    public static class PlayerSession {
        private final UUID playerId;
        private final String playerName;
        private final long startTime;
        private long endTime;

        public PlayerSession(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
            this.endTime = 0;
        }

        public void end() {
            this.endTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDuration() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    /**
     * Rolling average calculator for performance metrics.
     */
    public static class RollingAverage {
        private final int size;
        private final Deque<Long> values;
        private long sum;

        public RollingAverage(int size) {
            this.size = size;
            this.values = new ArrayDeque<>(size);
            this.sum = 0;
        }

        public synchronized void add(long value) {
            values.addLast(value);
            sum += value;
            if (values.size() > size) {
                sum -= values.removeFirst();
            }
        }

        public synchronized double getAverage() {
            return values.isEmpty() ? 0 : (double) sum / values.size();
        }

        public synchronized void clear() {
            values.clear();
            sum = 0;
        }

        public int getSize() { return size; }
        public int getCount() { return values.size(); }
    }

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    /**
     * Alert rule definition.
     */
    public static class AlertRule {
        private final String id;
        private final String description;
        private final java.util.function.BooleanSupplier condition;
        private final AlertSeverity severity;
        private final String message;
        private final Object[] args;
        // В6: optional supplier so args are computed at alert-fire time, not at init
        private final java.util.function.Supplier<Object[]> argsSupplier;

        public AlertRule(String id, String description,
                        java.util.function.BooleanSupplier condition,
                        AlertSeverity severity, String message, Object... args) {
            this.id = id;
            this.description = description;
            this.condition = condition;
            this.severity = severity;
            this.message = message;
            this.args = args;
            this.argsSupplier = null;
        }

        /** Constructor that evaluates args dynamically at alert-fire time (В6). */
        public AlertRule(String id, String description,
                        java.util.function.BooleanSupplier condition,
                        AlertSeverity severity, String message,
                        java.util.function.Supplier<Object[]> argsSupplier) {
            this.id = id;
            this.description = description;
            this.condition = condition;
            this.severity = severity;
            this.message = message;
            this.args = null;
            this.argsSupplier = argsSupplier;
        }

        public boolean evaluate() {
            return condition.getAsBoolean();
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
        public AlertSeverity getSeverity() { return severity; }
        public String getMessage() { return message; }
        /** Returns args evaluated at call time if a supplier was provided. */
        public Object[] getArgs() { return argsSupplier != null ? argsSupplier.get() : args; }
    }

    /**
     * Active alert instance.
     */
    public static class Alert {
        private final String ruleId;
        private final String description;
        private final AlertSeverity severity;
        private final long timestamp;
        private final String message;

        public Alert(String ruleId, String description, AlertSeverity severity,
                    long timestamp, String message) {
            this.ruleId = ruleId;
            this.description = description;
            this.severity = severity;
            this.timestamp = timestamp;
            this.message = message;
        }

        public String getRuleId() { return ruleId; }
        public String getDescription() { return description; }
        public AlertSeverity getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
    }
}