# Wave Defense Audit Log System

## Overview

The Wave Defense Audit Log system provides comprehensive logging and audit trail capabilities for the Wave Defense Minecraft mod. It offers detailed event tracking, tamper-evident logging, compliance reporting, and automated log management.

## Features

### Core Capabilities

- **Multi-Level Event Logging**: 9 audit categories with 4 severity levels
- **Tamper-Evident Logs**: SHA-256 hash chain for integrity verification
- **Automatic Log Rotation**: Size-based (100MB) and time-based (daily) rotation
- **Log Compression**: Automatic GZIP compression of rotated logs
- **Configurable Retention**: 90-day log retention, 1-year archive retention
- **Real-Time Buffering**: High-performance buffered writes with periodic flushing
- **Thread-Safe Operations**: Concurrent access support with proper synchronization

### Audit Categories

1. **PLAYER** - Player join/leave, deaths, kicks, bans, permissions
2. **WAVE** - Wave start/complete/fail, mob spawns/kills, boss events
3. **LOCATION** - Location create/delete/modify/activate/deactivate, teleports
4. **ITEM** - Item drops, pickups, crafts, enchants, trades, shop purchases
5. **PVP** - PvP kills, team kills, team create/disband/join/leave
6. **ECONOMY** - Points award/spend/transfer, reward claims
7. **ADMIN** - Command execution, config changes, plugin install/uninstall, backups
8. **SECURITY** - Login success/failure, cheat detection, exploit attempts, unauthorized access
9. **SYSTEM** - Server start/stop/crash, memory warnings, backup failures

### Severity Levels

- **INFO**: Normal operational events
- **WARNING**: Notable events requiring attention
- **ERROR**: Error conditions
- **CRITICAL**: Critical security or system events

## Architecture

### Components

#### 1. AuditEntry
Immutable audit log entry containing:
- Unique ID and timestamp
- Event type, category, and severity
- Actor and target information
- Location context
- Before/after state capture
- Cryptographic hash chain linkage
- Metadata map for extensibility

#### 2. AuditEventType
Enum defining all supported audit event types with:
- Event code
- Description
- Category assignment
- Default severity

#### 3. AuditQuery
Fluent query builder for filtering audit entries:
- Filter by category, severity, event type
- Filter by actor, target, location
- Time range filtering
- Combinable predicates

#### 4. Background Tasks
- **Buffer Flusher**: Periodic write of buffered entries (5s interval)
- **Rotation Checker**: Log rotation monitoring (1m interval)
- **Cleanup Task**: Removal of expired logs (1h interval)
- **Index Rebuilder**: Index reconstruction (24h interval)

## Usage

### Basic Logging

```java
WaveDefenseAuditLog auditLog = WaveDefenseAuditLog.getInstance();

// Simple event logging
auditLog.log(
    AuditEventType.PLAYER_JOIN,
    "PlayerName",
    playerUUID,
    "Player joined the server"
);

// Event with target
auditLog.log(
    AuditEventType.PVP_KILL,
    "KillerName",
    killerUUID,
    "VictimName",
    victimUUID,
    "PvP kill in arena"
);

// Event with location
auditLog.log(
    AuditEventType.WAVE_START,
    "System",
    null,
    "arena_north",
    "Wave 5 started in arena_north"
);
```

### Convenience Methods

```java
// Player events
auditLog.logPlayerJoin(player);
auditLog.logPlayerLeave(player);
auditLog.logPlayerDeath(player, "fell from height");

// Wave events
auditLog.logWaveStart("arena_north", 5);
auditLog.logWaveComplete("arena_north", 5, 120000);
auditLog.logMobSpawn("arena_north", "zombie");
auditLog.logMobKill("arena_north", "zombie", "PlayerName");

// Location events
auditLog.logLocationCreate("AdminName", adminUUID, "new_arena");
auditLog.logLocationDelete("AdminName", adminUUID, "old_arena");

// Item events
auditLog.logItemPurchase("PlayerName", playerUUID, "sword", 100);

// PvP events
auditLog.logPvpKill("Killer", killerUUID, "Victim", victimUUID);

// Configuration events
auditLog.logConfigChange("Admin", adminUUID, "max_waves", "10", "20");
auditLog.logCommandExecute("Admin", adminUUID, "/wavedefense reload");

// Security events
auditLog.logCheatDetected("PlayerName", playerUUID, "fly_hack");
auditLog.logLoginSuccess("PlayerName", playerUUID, "192.168.1.1");
auditLog.logLoginFailure("PlayerName", "192.168.1.1", "invalid_password");

// Economy events
auditLog.logPointsAward("Admin", adminUUID, "PlayerName", playerUUID, 500, "quest_reward");

// System events
auditLog.logServerStart();
auditLog.logServerStop();
auditLog.logBackupCreate("Admin", adminUUID, "backup_20240101");
auditLog.logBackupRestore("Admin", adminUUID, "backup_20240101");
```

### Advanced Logging

```java
// Custom event with metadata
Map<String, Object> metadata = new HashMap<>();
metadata.put("damage", 50);
metadata.put("weapon", "diamond_sword");
metadata.put("critical_hit", true);

auditLog.log(
    AuditEventType.MOB_KILL,
    "PlayerName",
    playerUUID,
    "zombie",
    zombieUUID,
    "arena_north",
    "Mob killed with critical hit",
    metadata,
    null,  // beforeState
    null   // afterState
);

// State change tracking
auditLog.log(
    AuditEventType.CONFIG_CHANGE,
    "Admin",
    adminUUID,
    null,
    null,
    null,
    "Configuration updated",
    null,
    "{max_waves: 10}",  // beforeState
    "{max_waves: 20}"   // afterState
);
```

### Querying Audit Logs

```java
// Get all critical events
List<AuditEntry> criticalEvents = auditLog.getCriticalEvents();

// Get security events
List<AuditEntry> securityEvents = auditLog.getSecurityEvents();

// Query by category
List<AuditEntry> playerEvents = auditLog.getEntriesByCategory(AuditCategory.PLAYER);

// Query by severity
List<AuditEntry> warnings = auditLog.getEntriesBySeverity(AuditSeverity.WARNING);

// Query by player
List<AuditEntry> playerActions = auditLog.getEntriesByPlayer(playerUUID);

// Query by time range
long startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours ago
long endTime = System.currentTimeMillis();
List<AuditEntry> recentEvents = auditLog.getEntriesByTimeRange(startTime, endTime);

// Query by location
List<AuditEntry> arenaEvents = auditLog.getEntriesByLocation("arena_north");

// Complex query using AuditQuery
List<AuditEntry> results = auditLog.query(
    new AuditQuery()
        .withCategory(AuditCategory.PLAYER)
        .withSeverity(AuditSeverity.CRITICAL)
        .withTimeRange(startTime, endTime)
);
```

### Compliance Reports

```java
// Generate compliance report
long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
String complianceReport = auditLog.generateComplianceReport(weekAgo, System.currentTimeMillis());
System.out.println(complianceReport);

// Generate security report
String securityReport = auditLog.generateSecurityReport(weekAgo, System.currentTimeMillis());
System.out.println(securityReport);
```

### Exporting Data

```java
List<AuditEntry> entries = auditLog.getCriticalEvents();

// Export to JSON
auditLog.exportToJson("critical_events.json", entries);

// Export to CSV
auditLog.exportToCsv("critical_events.csv", entries);

// Export to NBT
auditLog.exportToNbt("critical_events.nbt", entries);
```

### Integrity Verification

```java
// Verify log integrity
boolean isIntact = auditLog.verifyIntegrity();
if (!isIntact) {
    System.err.println("Audit log integrity check failed!");
}
```

### Shutdown

```java
// Graceful shutdown (call on server stop)
auditLog.shutdown();
```

## File Structure

```
wave-defense/
├── audit-logs/
│   ├── audit.log              # Current active log
│   ├── audit_20240101_120000.log.gz  # Rotated logs (compressed)
│   ├── audit_20240102_120000.log.gz
│   └── ...
├── audit-archives/
│   ├── audit_20230101_120000.log.gz  # Archived logs (>90 days)
│   └── ...
├── audit-index.json           # Search index (optional)
└── hash-chain.dat            # Cryptographic hash chain
```

## Configuration

Key configuration constants in `WaveDefenseAuditLog.java`:

```java
private static final long MAX_LOG_SIZE_BYTES = 100 * 1024 * 1024; // 100MB
private static final int MAX_LOG_AGE_DAYS = 90; // Retention period
private static final int MAX_ARCHIVE_AGE_DAYS = 365; // Archive period
private static final int LOG_BUFFER_SIZE = 1000; // Buffer capacity
private static final long FLUSH_INTERVAL_MS = 5000; // Flush interval
private static final long ROTATION_CHECK_INTERVAL_MS = 60000; // Rotation check
```

## Integration with Wave Defense

The audit log integrates with existing Wave Defense components:

### WaveDefenseMod Integration

```java
// Initialize in WaveDefenseMod constructor
WaveDefenseAuditLog.getInstance();

// Log server events
auditLog.logServerStart();
auditLog.logServerStop();
```

### Monitor Integration

```java
// Log performance alerts
if (memoryUsage > threshold) {
    auditLog.log(
        AuditEventType.MEMORY_CRITICAL,
        "System",
        null,
        "Memory usage critical: " + memoryUsage + "MB"
    );
}
```

### Command Integration

```java
// Log command execution
auditLog.logCommandExecute(playerName, playerUUID, command);
```

## Performance Considerations

- **Buffered Writes**: Events are buffered (1000 entries) and written in batches
- **Asynchronous Flushing**: Buffer flushed every 5 seconds or when full
- **Non-Blocking Operations**: Write operations use blocking queue with offer()
- **Background Tasks**: Heavy operations (compression, index rebuild) run asynchronously
- **Minimal Lock Contention**: Separate locks for write and rotation operations

## Security Features

### Tamper Detection

- Each log entry includes SHA-256 hash of its content
- Hash chain links each entry to the previous one
- `verifyIntegrity()` method validates entire chain
- Any modification breaks the hash chain

### Access Control

- Critical events broadcast to operators (permission level 2+)
- Security events logged with full context
- Unauthorized access attempts tracked

### Data Protection

- Logs stored in separate directory
- Automatic compression reduces tampering risk
- Archived logs retained for forensic analysis

## Compliance

The audit system supports compliance requirements:

- **GDPR**: Track personal data access and modifications
- **SOX**: Financial transaction logging
- **HIPAA**: Healthcare data access tracking (if applicable)
- **Server Policies**: Custom policy enforcement and tracking

## Troubleshooting

### High Memory Usage

- Reduce `LOG_BUFFER_SIZE` (default: 1000)
- Increase `FLUSH_INTERVAL_MS` (default: 5000)

### Disk Space Issues

- Reduce `MAX_LOG_AGE_DAYS` (default: 90)
- Reduce `MAX_LOG_SIZE_BYTES` (default: 100MB)
- Enable more aggressive compression

### Missing Events

- Check if buffer is full (increase `LOG_BUFFER_SIZE`)
- Verify disk write permissions
- Check for exceptions in server log

### Slow Performance

- Verify disk I/O performance
- Check for excessive logging (reduce event types)
- Increase flush interval
- Disable unnecessary audit categories

## Best Practices

1. **Regular Monitoring**: Review critical and security events daily
2. **Log Rotation**: Ensure sufficient disk space for rotated logs
3. **Backup**: Include audit logs in server backups
4. **Retention Policy**: Adjust based on compliance requirements
5. **Index Rebuilding**: Allow periodic index rebuilds for performance
6. **Integrity Checks**: Run `verifyIntegrity()` periodically
7. **Export**: Regular exports for long-term archival

## Future Enhancements

- Real-time event streaming to external systems
- Machine learning anomaly detection
- Automated alert escalation
- Web-based audit dashboard
- Advanced search and filtering UI
- Integration with SIEM systems
- Blockchain-based tamper proofing
- Multi-server audit aggregation

## License

Wave Defense Audit Log is part of the Wave Defense mod and is licensed under the same terms.

## Support

For issues or questions:
- Check server logs for error messages
- Verify file permissions
- Ensure sufficient disk space
- Review configuration settings
- Contact Wave Defense support team
