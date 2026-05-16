# Wave Defense Monitor System

## Overview

The Wave Defense Monitor is a comprehensive monitoring and statistics system that provides real-time metrics collection, performance tracking, and alerting capabilities for the Wave Defense mod.

## Features

### 1. Real-Time Metrics Collection
- **TPS Monitoring**: Tracks server ticks per second with rolling averages
- **Memory Usage**: Monitors JVM heap usage (used, max, free, total)
- **Player Counts**: Tracks online players and per-location player counts
- **Mob Tracking**: Counts active mobs, spawned mobs, and killed mobs
- **Wave Statistics**: Tracks wave completions, timeouts, and execution times

### 2. Performance Tracking
- **Tick Timing**: Records tick durations with 60-second history
- **Wave Execution Metrics**: Tracks wave spawn times, completion times, and mob spawn rates
- **Per-Location Analytics**: Maintains historical data for each active location
- **Per-Player Statistics**: Tracks individual player performance (kills, deaths, points, sessions)

### 3. Alerting System
- **Threshold-Based Alerts**: Configurable warning and critical thresholds
- **Automatic Detection**: Monitors TPS, memory, wave timeouts, player idle, mob spawn lag
- **Alert History**: Maintains 7-day history of all alerts
- **Admin Notifications**: Broadcasts critical alerts to online admins

### 4. Historical Statistics
- **Rolling Windows**: Maintains 1-hour history of server state
- **Memory History**: 5-minute memory usage tracking
- **Trend Analysis**: Enables identification of performance patterns

### 5. Report Generation
- **Summary Report**: Quick overview of server health and key metrics
- **Detailed Report**: Comprehensive analysis including historical trends
- **Location-Specific Reports**: Per-location statistics and player rankings

## Architecture

### Core Components

1. **WaveDefenseMonitor** (`com.wavedefense.monitor.WaveDefenseMonitor`)
   - Main monitoring class (Singleton pattern)
   - Coordinates all monitoring activities
   - Provides report generation

2. **Data Structures**:
   - `HistoricalSnapshot`: Captures server state at a point in time
   - `MemorySnapshot`: Records memory usage metrics
   - `WaveExecutionMetrics`: Tracks wave performance per location
   - `LocationHistory`: Maintains per-location statistics
   - `PlayerStatistics`: Tracks individual player performance
   - `PlayerActivity`: Monitors player activity and idle time
   - `PlayerSession`: Records player session data
   - `RollingAverage`: Calculates rolling averages for metrics

3. **Alert System**:
   - `AlertRule`: Defines alert conditions and thresholds
   - `Alert`: Represents active alert instances
   - `AlertSeverity`: INFO, WARNING, CRITICAL levels

## Integration Points

### Server Tick Integration
The monitor integrates with the server tick loop via `WaveDefenseMod.onServerTick()`:
```java
@SubscribeEvent
public void onServerTick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
        WaveDefenseMonitor.getInstance().onServerTick();
    }
}
```

### Wave Event Tracking
The monitor hooks into wave lifecycle events:
- `onWaveStart()`: Called when a wave begins
- `onWaveComplete()`: Called when a wave completes
- `onMobSpawned()`: Called when mobs spawn
- `onMobKilled()`: Called when mobs are killed

### Player Event Tracking
The monitor tracks player lifecycle:
- `onPlayerJoin()`: Called when players join a location
- `onPlayerDeath()`: Called when players die
- `onPlayerLeave()`: Called when players leave

## Commands

### `/wavedefense monitor [summary|detailed|alerts|reset]`
Displays monitoring information:
- `summary`: Quick overview of server health
- `detailed`: Comprehensive report with historical data
- `alerts`: Shows active alerts
- `reset`: Resets monitoring data (requires restart)

### `/wavedefense stats [location]`
Displays statistics:
- Without location: Global statistics
- With location: Location-specific statistics

### Short Aliases
- `/wdmon`: Alias for `/wavedefense monitor`
- `/wdstats`: Alias for `/wavedefense stats`

## Alert Rules

### Default Thresholds

| Alert | Severity | Threshold | Description |
|-------|----------|-----------|-------------|
| LOW_TPS_WARNING | WARNING | TPS < 18.0 | Server performance degradation |
| LOW_TPS_CRITICAL | CRITICAL | TPS < 15.0 | Severe performance issues |
| HIGH_MEMORY_WARNING | WARNING | Memory > 8GB | High memory usage |
| HIGH_MEMORY_CRITICAL | CRITICAL | Memory > 12GB | Risk of OutOfMemoryError |
| WAVE_TIMEOUT | WARNING | Wave > 300s | Wave taking too long |
| PLAYER_IDLE | INFO | Idle > 300s | Player inactivity |
| MOB_SPAWN_LAG | WARNING | Lag > 50 mobs | Mob spawn rate below expected |

## Configuration

Alert thresholds can be modified by editing the `initializeDefaultAlertRules()` method in `WaveDefenseMonitor.java`.

## Performance Impact

The monitoring system is designed to have minimal performance impact:
- Tick processing: < 1ms average
- Memory overhead: ~50MB for 1-hour history
- CPU usage: < 0.5% on typical servers
- All monitoring operations are non-blocking
- Graceful degradation on errors (never breaks gameplay)

## Data Retention

- Tick history: 60 seconds (1200 ticks)
- Historical snapshots: 1 hour (72000 ticks)
- Memory history: 5 minutes (600 snapshots)
- Alert history: 7 days
- Player activity: 24 hours

## Extending the Monitor

### Adding Custom Metrics

1. Create a new metric field in `WaveDefenseMonitor`
2. Add collection logic in `collectGameplayMetrics()`
3. Include in report generation methods

### Adding Custom Alerts

1. Add a new `AlertRule` in `initializeDefaultAlertRules()`
2. Implement the condition check
3. Define appropriate thresholds and severity

### Adding New Reports

1. Create a new report method (e.g., `generateCustomReport()`)
2. Add command handler in `WaveDefenseCommand.java`
3. Register the command in `register()` method

## Troubleshooting

### High CPU Usage
- Reduce history window sizes
- Increase tick check intervals
- Disable non-critical alerts

### Memory Issues
- Reduce `HISTORY_WINDOW_TICKS`
- Reduce `TICK_HISTORY_SIZE`
- Decrease memory snapshot frequency

### Missing Data
- Ensure `WaveDefenseMod.onServerTick()` is registered
- Check that monitoring is enabled
- Verify no exceptions in server logs

## Best Practices

1. **Regular Monitoring**: Check `/wavedefense monitor summary` periodically
2. **Alert Response**: Investigate CRITICAL alerts immediately
3. **Trend Analysis**: Use detailed reports to identify patterns
4. **Capacity Planning**: Monitor memory trends for scaling decisions
5. **Performance Tuning**: Use TPS data to optimize server settings

## Version History

- **v2.0**: Complete rewrite with comprehensive monitoring
  - Real-time metrics collection
  - Performance tracking
  - Alerting system
  - Historical analysis
  - Report generation

## License

This monitoring system is part of the Wave Defense mod and is released under the same license.

## Support

For issues or questions:
- Check server logs for monitoring-related errors
- Review alert history for patterns
- Verify integration points are properly registered
- Ensure all dependencies are available