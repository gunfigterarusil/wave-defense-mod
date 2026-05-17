# Wave Defense Mod - v0.2.43

Wave Defense is a PvE/PvP Forge mod for **Minecraft 1.20.1** and **Java 17**.
It lets server owners build configurable arena locations with mob waves, team PvP,
shops, loot events, portals, boundaries, HUD panels, and in-game admin editors.

The public release before this repair branch is **0.2.42**, so this workspace now
targets **0.2.43**.

---

## Status

Version `0.2.43` is a repair and UI-improvement branch.

Already repaired in this workspace:

- Restored important `WaveManager` delegations and sync methods that had become stubs.
- Repaired PvE player-death handling so real deaths are counted separately from surrender/logout.
- Repaired PvP team selection so an explicitly chosen team/spawn is not overwritten by auto-balance.
- Repaired player/team sync packets used by HUD teammate panels.
- Repaired shop item editor scrolling and hidden-widget click filtering.
- Repaired PvP rules editor scrolling so it uses real content height instead of a fixed limit.
- Added grouped dashboard-style PvE/PvP location tabs for waves, shop, loot, and shared settings.
- Added player shop tile/grid mode while keeping the classic list mode.
- Reworked PvP team selection into a scrollable team-card screen that preserves explicit team/spawn choice.
- Fixed all critical PvE data-loss bugs: `totalWaves` persistence, `victoryLingerTicks` countdown, dead-mob cleanup, `waveStartMobCount` inflation, `halfMobsTriggered` reset, and `PlayerBackup` restore completeness.
- Fixed all runtime features that were stored but never applied: `waveEffect`, `pointsReward`, `completionCommand`, `firstWaveDelaySec`.
- Fixed full PvP audit: `pvpPendingRespawn` clear, `dmTeamKills` clear, BR simultaneous death draw, BR environment death tracking, rebalance in BUY phase, double-point award on final round, premature `ENDED` state.
- Fixed shop sell path: `SellItemPacket` now passes `shopPointIndex` so sells from per-point shops use the correct item list.
- Fixed `PvpLocationEditorScreen` losing typed damage values on toggle rebuilds.
- Fixed `PvpTeamSelectScreen` hiding duplicate-named spawn points — all spawns now appear with `(N)` suffix when names clash.
- Fixed `StatsScreen` showing truncated UUIDs instead of player names.
- Replaced all hardcoded Ukrainian strings across 6 GUI screens with `Component.translatable()` keys; 35 new keys in all 8 language files (en/uk/de/fr/es/pl/pt_br/zh_cn).
- Verified code for correctness against Java 17 / Forge 1.20.1 API.

Still planned:

- Clean generated notes, old summaries, corrupted files, and root-level prototype files.
- Run full in-game testing for PvE waves and 2+ player PvP sessions.

---

## Installation

1. Install Forge `1.20.1` (`47.2.0+` recommended).
2. Copy the built `wavedefense-0.2.43.jar` into the `mods/` folder.
3. Start the client or dedicated server.

---

## Key Bindings

| Key | Action |
| --- | --- |
| `V` | Open the main Wave Defense menu |
| `B` | Open the shop directly |
| `L` | Leave the current location |

---

## Main Features

### PvE Wave Defense

- Configurable locations with player spawn, optional scatter radius, and mob spawn points.
- Configurable waves with mob type, count, equipment, effects, spawn chance, and scaling.
- Optional delay before the first wave and between later waves.
- Point system for kills, starting points, purchases, sales, rewards, and completion bonuses.
- Inventory backup and restore on entry/exit.
- Starting items and completion rewards.
- Global shop mode or point-based shop mode.
- Loot spawn points with event triggers.
- Optional auto-activation zone with countdown and particles.
- Optional location boundary with timer, damage, teleport-back, or instant surrender modes.
- Optional portal system for penalty waves.
- TextDisplay info panels for wave, timer, mob count, players, and points.

### PvP Modes

PvP locations use team spawn points and per-location PvP settings.

Supported sub-modes:

- **Standard**: team rounds with buy phase, countdown, active phase, win/loss points, and round count.
- **Deathmatch**: respawns during the match and victory by kill target.
- **Battle Royale**: random spawn assignment, shrinking border, border particles, and last-player-wins flow.

PvP settings include:

- team spawn points with optional spawn radius;
- minimum player count;
- friendly fire;
- team auto-balance;
- wait effects;
- starting points;
- kill points and death penalty;
- round start delay;
- round start/win/loss points;
- Deathmatch kill target;
- Battle Royale border radius, shrink interval, particles, and border damage.

---

## Admin Editors

The mod is designed to be configured in game through GUI screens:

- location editor;
- PvP location editor;
- wave list editor;
- wave mob editor;
- wave trigger editor;
- mob selection;
- starting items;
- completion rewards;
- shop editor;
- shop item editor;
- shop availability trigger editor;
- shop point editor;
- loot spawn editor;
- import/export screens;
- HUD editor.
- player PvP team selection screen.

Current UI direction for `0.2.43+`:

- fewer long unstructured forms;
- grouped sections for Basic, Spawns, Waves, Shop, Loot, Boundary, Portal, HUD, and PvP;
- consistent scroll behavior across all large screens;
- no clicks on hidden scrolled-out widgets;
- better player shop browsing with list and tile/grid modes;
- parity between useful PvE location settings and PvP location settings;
- clearer PvP team selection with scrollable cards for locations with many teams.

---

## Shop System

The shop supports:

- multiple item stacks per shop entry;
- buy and sell price;
- category;
- optional NBT matching for sell checks;
- availability triggers;
- global shop items;
- per-point shop items;
- import/export through NBT files.

Player-facing UI:

- tile/grid view with item icons, compact prices, category filtering, and clear buy/sell actions;
- classic list view remains available from the same shop screen.

---

## Trigger System

Triggers are used by waves, loot, shops, and location events.

Examples:

- wave events: `WAVE_START`, `WAVE_END`, `WAVE_N`, `WAVE_COMPLETE`;
- timers: `TIMER_60`, `TIMER_120`, `TIMER_300`, `TIMER_CUSTOM`;
- mob events: `MOB_KILL`, `MOBS_KILLED_N`, `MOBS_REMAINING_LOW`;
- player events: `PLAYER_JOIN`, `PLAYER_DEATH`, `PLAYER_ENTER_ZONE`, `PLAYER_HAS_ITEM`;
- PvP events: `ROUND_START`, `ROUND_END`, `BUY_PHASE`, `MATCH_START`, `MATCH_END`, `TEAM_WIPE`, `KILL_STREAK_3`;
- shop events: `SHOP_LOCATION_START`, `SHOP_WAVE_START`, `SHOP_WAVE_N`, `SHOP_PLAYER_HAS_ITEM`.

---

## Commands

| Command | Description |
| --- | --- |
| `/wavedefense` | Main command |
| `/wd` | Alias |
| `/wavedefense kick <players>` | Force-remove players from a location |
| `/wavedefense tp <location> <players>` | Teleport players to a location |
| `/wavedefense menu <player> [admin]` | Open a menu for a player |
| `/wavedefense reload` | Reload saved location data |

---

## Configuration

Common config file:

```text
config/wavedefense-common.toml
```

Important settings include:

- HUD enabled/disabled;
- shop hotkey;
- UI tooltips;
- default PvP rounds;
- default PvP buy time;
- mob equipment;
- equipment drop chance;
- debug logging.

---

## Architecture

Core runtime:

```text
WaveDefenseMod
  WaveManager
    WaveContext
    LocationSession
    SessionManager
    MobSpawnManager
    TriggerEvaluator
    PvpRoundManager
    BattleRoyaleManager
    BoundaryManager
    PortalManager
    ZoneActivationManager
    InfoPanelManager
```

Data layer:

```text
Location
LocationManager
LocationSerializer
NbtHelper
WaveConfig
WaveMob
WaveTrigger
ShopItem
ShopPoint
LootSpawn
PvpSpawnPoint
```

Networking:

```text
PacketHandler
network/packets/*
```

Client UI:

```text
gui/*
events/HudOverlay
events/KeyBindings
```

---

## Build

Use Java 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

Quick verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat compileJava test
```

---

## Cleanup Notes

The repository currently contains older implementation summaries, generated build/run
artifacts, root-level prototype files, and at least one corrupted Java file. Those should
be cleaned in a separate pass after gameplay and GUI behavior are stable.

Known cleanup candidates:

- root-level summary markdown files that are no longer useful;
- root-level prototype Java files outside `src/main/java`;
- `src/main/java/com/wavedefense/audit/WaveDefenseAuditLog.java.corrupted`;
- stale generated files in `build/` and `run/` if they were accidentally included in source control.

---

## License

MIT.
