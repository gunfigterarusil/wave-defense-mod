# Wave Defense Admin Commands System

## Overview

The `WaveDefenseAdminCommands` class provides an enhanced admin command system for the Wave Defense Minecraft mod with comprehensive validation, confirmation dialogs, audit logging, and safety features.

## File Location

`src/main/java/com/wavedefense/commands/WaveDefenseAdminCommands.java`

## Features

### 1. Permission System
- **5 Permission Levels** (0-4):
  - `PERM_NONE` (0): No access
  - `PERM_BASIC` (1): View-only commands
  - `PERM_MOD` (2): Standard admin operations
  - `PERM_ADMIN` (3): Elevated admin operations
  - `PERM_OWNER` (4): Full access including lockdown

- Permission checks are enforced on every command
- Automatic audit logging of permission denials
- Hover tooltips showing required vs. actual permission levels

### 2. Command Confirmation System
- **Destructive actions require confirmation**
- 30-second timeout for confirmations
- Visual confirmation dialog with clickable buttons
- Cooldown period (5s) between destructive actions
- Prevents accidental data loss

**Commands requiring confirmation:**
- Location lock/unlock
- Location wipe
- Player restore from backup
- Config reload
- Safety lockdown

### 3. Audit Logging
- **Comprehensive event tracking**
- All commands logged with timestamp, executor, type, and outcome
- Success and failure events tracked separately
- Player-specific log queries
- Console output for real-time monitoring
- Persistent in-memory storage

**Logged Events:**
- Teleport operations
- Player kicks
- Backup/restore operations
- Location modifications
- Configuration changes
- Permission denials
- Confirmation actions

### 4. Rate Limiting
- **30 commands per minute per player**
- Prevents command spam
- Automatic cleanup of old entries
- Clear feedback when rate-limited

### 5. Safety Features
- **Pre-teleport effect removal** (clears harmful potions)
- **Permission hierarchy enforcement** (can't kick higher-ranked players)
- **Location validation** (checks existence, spawn points, lock status)
- **Player state validation** (checks location, game mode)
- **Cooldown protection** (prevents rapid destructive actions)
- **Safety check command** (comprehensive server health report)
- **Lockdown mode** (emergency stop for all operations)

## Command Reference

### Main Command
```
/wavedefense-admin (or /wda)
```

### Teleportation

#### Teleport to Location
```
/wda tp <location> <players...>
```
- Teleports players to specified location
- Removes harmful effects before teleport
- Validates location exists and has spawn point

#### Teleport to Executor
```
/wda tp here <players...>
```
- Teleports players to command executor's position
- Cannot teleport self

#### Teleport to Spawn
```
/wda tp spawn <players...>
```
- Teleports players to world spawn
- Removes harmful effects

### Player Management

#### Kick Players
```
/wda kick <players...> [reason]
```
- Kicks players from server
- Optional reason parameter
- Cannot kick players with higher permissions

#### Heal Players
```
/wda player heal <players...>
```
- Restores full health and hunger
- Removes all effects
- Notifies players

#### Feed Players
```
/wda player feed <players...>
```
- Restores hunger to maximum

#### Change Game Mode
```
/wda player gamemode <players...> <mode>
```
- Modes: survival, creative, adventure, spectator
- Changes game mode for specified players

#### Clear Inventory
```
/wda player clear <players...>
```
- Clears player inventory
- Notifies affected players

### Backup & Restore

#### Create Backup
```
/wda backup create <player>
```
- Creates backup of player data
- Returns backup ID

#### Create All Backups
```
/wda backup create all
```
- Creates backups for all online players
- Requires ADMIN permission

#### List Backups
```
/wda backup list <player>
```
- Lists all backups for player
- Shows timestamps
- Provides restore buttons

#### Restore Backup
```
/wda restore <player> <backupId>
```
- Restores player from backup
- **Requires confirmation**
- Notifies player of restoration

### Location Management

#### Lock Location
```
/wda location lock <location>
```
- Prevents new players from entering
- Notifies players inside location
- **Requires confirmation**

#### Unlock Location
```
/wda location unlock <location>
```
- Allows players to enter location
- No confirmation required

#### Wipe Location
```
/wda location wipe <location>
```
- Removes all players from location
- Resets location progress
- **Requires confirmation**

#### Location Info
```
/wda location info <location>
```
- Shows location details
- Status, game mode, spawn point
- Wave configuration
- Player count

### Configuration

#### Set Config Value
```
/wda config set <key> <value>
```
- Supported keys:
  - `debug.admin_messages`
  - `debug.logging_enabled`
  - `location.entry_allowed`

#### Get Config Value
```
/wda config get <key>
```
- Retrieves current config value
- Supports all config keys

#### Reload Config
```
/wda config reload
```
- Reloads all configuration
- Reloads locations
- **Requires confirmation**

### Logging

#### Recent Logs
```
/wda log recent <count>
```
- Shows last N audit log entries
- Count: 1-100

#### Player Logs
```
/wda log player <player>
```
- Shows all audit events for player
- Filtered by executor

#### Clear Logs
```
/wda log clear
```
- Clears all audit logs
- **Requires confirmation**

### Safety

#### Safety Check
```
/wda safety check
```
- Comprehensive server health report
- Player count
- Location status
- Locked locations
- Rate-limited players
- Pending confirmations

#### Lockdown Mode
```
/wda safety lockdown <true|false>
```
- Emergency server lockdown
- Locks all locations
- Kicks non-admin players from locations
- **Requires OWNER permission**
- **Requires confirmation**

## Permission Requirements

| Command | Required Level | Description |
|---------|---------------|-------------|
| `tp`, `tp here`, `tp spawn` | MOD | Teleport operations |
| `kick` | MOD | Kick players |
| `player heal`, `player feed`, `player clear` | MOD | Player management |
| `player gamemode` | ADMIN | Change game mode |
| `backup create`, `backup list`, `restore` | MOD | Backup operations |
| `backup create all` | ADMIN | Backup all players |
| `location lock`, `location unlock`, `location wipe` | ADMIN | Location management |
| `location info` | BASIC | View location info |
| `config set`, `config reload` | ADMIN | Config modification |
| `config get` | BASIC | View config |
| `log recent`, `log player` | MOD | View logs |
| `log clear` | ADMIN | Clear logs |
| `safety check` | MOD | Safety report |
| `safety lockdown` | OWNER | Emergency lockdown |

## Audit Event Types

- `teleport` - Teleport operations
- `teleport_here` - Teleport to executor
- `teleport_spawn` - Teleport to spawn
- `kick` - Player kicks
- `player_heal` - Player healing
- `player_feed` - Player feeding
- `player_gamemode` - Game mode changes
- `player_clear` - Inventory clearing
- `backup_create` - Individual backup
- `backup_create_all` - Mass backup
- `restore` - Player restore
- `location_lock` - Location lock
- `location_unlock` - Location unlock
- `location_wipe` - Location wipe
- `config_set` - Config modification
- `config_reload` - Config reload
- `permission_denied` - Permission denial
- `confirmation` - Confirmation action
- `safety_lockdown` - Lockdown toggle

## Implementation Details

### Thread Safety
- `ConcurrentHashMap` for confirmation tracking
- `synchronizedList` for audit events
- Thread-safe command history

### Performance
- Lazy cleanup of old entries
- Efficient data structures
- Minimal overhead per command

### Extensibility
- Modular design
- Easy to add new commands
- Pluggable audit system
- Configurable limits

## Usage Examples

### Teleport Players
```
/wda tp arena @a
```

### Heal All Players
```
/wda player heal @a
```

### Create Backup Before Maintenance
```
/wda backup create all
```

### Lock Location for Event
```
/wda location lock arena
```
(Confirm in chat)

### Check Server Health
```
/wda safety check
```

### Emergency Lockdown
```
/wda safety lockdown true
```
(Confirm in chat)

## Integration

The admin commands integrate with:
- **WaveDefenseMod**: Location and wave managers
- **WaveDefenseConfig**: Configuration system
- **WaveGameRules**: Game rule management
- **PacketHandler**: Network communication
- **LocationManager**: Location persistence
- **PlayerBackup**: Backup/restore system

## Security Considerations

1. **Permission Validation**: Every command validates permissions
2. **Confirmation System**: Destructive actions require confirmation
3. **Rate Limiting**: Prevents command spam/abuse
4. **Audit Trail**: All actions logged for accountability
5. **Cooldown Protection**: Prevents rapid destructive actions
6. **Hierarchy Enforcement**: Cannot affect higher-ranked players
7. **Input Validation**: All parameters validated before execution

## Future Enhancements

- Persistent audit log storage
- Configurable rate limits
- Command aliases
- Permission groups
- Scheduled commands
- Rollback system
- Multi-server support
- Web dashboard integration

## License

Wave Defense Mod - Admin Commands System
