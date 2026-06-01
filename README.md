# Wave Defense Mod - v0.2.53.2

Wave Defense is a PvE/PvP Forge mod for **Minecraft 1.20.1** and **Java 17**.
It lets server owners build configurable arena locations with mob waves, team PvP,
shops, loot events, portals, boundaries, HUD panels, and in-game admin editors.

---

## Status

Version `0.2.53.2` — PvP UX improvements, post-match scoreboard, optional Tacz gun mod
compatibility, and shop quantity fix. Hotfixes for monitor alert spam and Tacz gun discovery.

**v0.2.53.2 hotfix:**
- Fixed empty Tacz category counts in the shop picker — discovery now scans loaded
  `CreativeModeTab`s (the same source the regular item picker uses), so every gun
  the admin sees in creative — including datapack-added ones — appears in the shop.
- Bulk-add now uses the captured creative-tab stack as a template, preserving any
  default attachments or NBT the gun pack ships with.
- Categorisation tries Tacz's internal `GunData` first, then falls back to id-substring
  guessing (`glock_*` → pistol, `ak*` → rifle, `awp/kar98/mosin` → sniper, etc.).

**v0.2.53.1 hotfix:**
- `WaveDefenseMonitor` no longer floods operators with `[ALERT WARNING] Current TPS …`
  messages on every server boot. Chat broadcasts are now opt-in
  (`debug.monitorBroadcastAlerts`, default off — alerts still go to the server log).
- 60-second startup grace period suppresses all alerts while the server is still
  loading worlds.
- TPS thresholds relaxed: warning 18 → 12, critical 15 → 8 — only genuine sustained
  problems trigger now.
- Fixed duplicated `[ALERT WARNING]` in the chat broadcast text.

**v0.2.53 highlights:**
- **Shop quantity fix** — each of the 4 shop slots now has an `×N` count field
  (1–64). Previously every item was forced to count 1.
- **DM spawn modes** — Team / Random / Smart (smart picks the candidate furthest from
  any living enemy). Configurable per-location.
- **DM kill leaderboard HUD** — top-right panel showing your kills, leader, and target.
- **CtP speed multiplier** — more teammates on point = faster capture (cap 4×).
  Backward-compatible toggle.
- **CtP "Capture all points" win condition** — owning every point simultaneously
  wins the round immediately.
- **Post-match scoreboard** — new screen opens automatically for every player at match
  end with per-player kills / deaths / assists / points and per-team round wins.
- **Tacz (Timeless and Classics Zero) optional compatibility** — when Tacz is loaded:
  - shop item picker gains 9 extra sub-tabs (All / Pistols / Rifles / Shotguns / SMGs
    / Snipers / RPGs / MGs / Other)
  - a new "🔫 Tacz" bulk-add button in the shop editor: pick a category, enter a price,
    and every gun of that category becomes a new shop entry in one click
  - pure reflection — no compile-time dependency; works without Tacz too

**v0.2.52 fixes (14):**
- **CRITICAL** — Dedicated-server crash: `ConfigScreenHandler` / `WaveDefenseConfigScreen` (client-only classes) were imported directly by the `@Mod` class, causing `NoClassDefFoundError` on server startup. Isolated into `@OnlyIn(Dist.CLIENT)` inner class + `DistExecutor`.
- BackupSystem (`WaveDefenseBackupSystem`) was never started — `initialize()` / `startScheduledBackups()` added to `onServerStarting`.
- Auto-difficulty scaling broken — `recordWaveCompletion()` had no call site; wave metrics now recorded after every wave.
- Null-guard added for `locationManager` in `WaveManager.tickSession()` (NPE before server fully starts).
- `pvpPenaltyDeducted` set not cleared on PvP session end — could suppress death penalty in future sessions.
- `UUID.fromString()` in session NBT load now wrapped in `try/catch` — malformed UUID no longer crashes load.
- Dead-mob sweep extended to `triggerMobs` sets — trigger conditions blocked by mobs killed by non-player sources.
- `InfoPanelManager` reflection cached in a `static volatile` field — was re-reflecting every second per entity.
- CtP/KotH HUD overlay implemented in `PlayerHUD` — point ownership, capture progress bars, team scores, round timer.
- Orphan session now ended before location deletion in `DeleteLocationPacket`.
- Zone particles now use the player's actual dimension (Nether, End, custom) — previously hardcoded to Overworld.
- `WaveDefenseMonitor` no longer holds a stale `WaveContext` — replaced captured field with dynamic `waveCtx()` method.
- `SyncStatsPacket` now actually sent — `GameStats` pushed to client on mob kill and on location join/leave.
- Location triggers for `PLAYER_HAS_ITEM`, `PLAYER_LOW_HEALTH`, and inventory/item checks now evaluate correctly against nearby players instead of the (empty) in-session player list.

**v0.2.51 fixes (7):**
- Server tick wrapped in `try/catch` — one sub-manager exception can no longer crash the whole server.
- Leave Location keybind moved from `L` (conflicts with vanilla Advancements) to `G`.
- Location import now requires explicit two-click confirmation to prevent accidental overwrites.
- `LocationInfoScreen` wired up via a new `ℹ` button on every location row in the player menu.
- Surrender button in PvP menu now shows in red with "(з пенальті)" — visually distinct from "Exit PvP".
- Team-select screen now shows the minimum player count required to start the match.
- C→S packet rate limiter added (`PacketRateLimiter`) — protects against flood/DoS on 5 key packets.

**v0.2.50 fixes (12):** CtP/KotH/Leaderboard audit — capture radius cylinder fix, HUD bar denominator,
panel height, name truncation, podium colors, mouse-scroll location selector, blank-location guard,
particle-grid y-advance, inline empty-name error, dead code removal, no-capture-points warning.
Six translation keys added to all 8 language files.

Completed in this workspace:

- **Architecture**: Decomposed `WaveManager` (3 748 lines) into 11 focused sub-managers; `LocationSession` value object; `ListEditorScreen<T>` base class; `CoordinateInputField` compound widget; `LocationSerializer` / `NbtHelper` data layer.
- **PvE runtime**: Fixed `totalWaves` persistence, `victoryLingerTicks` countdown, dead-mob cleanup, `waveStartMobCount` inflation, `PlayerBackup` restore (armor/offhand/effects). Activated `waveEffect`, `pointsReward`, `completionCommand`, `firstWaveDelaySec`. Grace period (30 s) when last player leaves mid-wave.
- **PvP runtime**: Fixed premature `ENDED` state, `dmTeamKills` accumulation, BR simultaneous death draw, BR environment death tracking, rebalance during BUY phase, double-point award, PvP teammates HUD sync. PvP↔PvE mode switch now requires two-click confirmation.
- **Networking**: `SellItemPacket` now carries `shopPointIndex` and uses NBT-aware item matching; all server responses use `Component.translatable()`.
- **Data safety**: `LocationManager.saveToFile()` uses atomic `.tmp`→rename pattern; one serialization pass; `.bak` preserved. Restores from backup on corrupt primary file.
- **Spawn correctness**: Mob dimension fixed (no longer tied to player's current dimension); unloaded-chunk guard prevents stalled waves; dead-mob sweep covers all dimensions via `getAllLevels()`; portal double-spawn guard.
- **Mine and Slash compat** (`mmorpg` mod v6.1.0+, optional): per-location mob level, XP bonus %, and 5 elemental resistances configured in the PvE location editor. Pure reflection — zero compile-time dependency, zero overhead when MnS is absent.
- **i18n**: Full localization — keys per language, 8 language files (EN · UK · DE · FR · ES · PL · PT-BR · ZH-CN), zero hardcoded player-visible strings; missing `no_spawn_set` / `value_out_of_range` keys added to 6 lang files; 14 new MnS + grace keys added to all 8.
- **§ corruption fix**: All `?[color]` substitutions and `\\u00A7x` literal-escape auto-keys corrected across all 8 lang files and 3 Java GUI files.
- **GUI audit (52 screens)**: Scissor clipping, scroll behavior, hidden-widget click filtering, two-click delete confirmations (waves, rewards, PvP spawns), Cancel buttons, per-slot loot "from hand" buttons, stable effect-picker width, standardized button heights.
- **Layout fixes**: Import/Export header overlap, Completion Rewards item frame padding, tooltip black-box bug, LocationEditor footer overlap, mob spawn scroll Y offset, PvP mode unlock button.
- **Config screen**: In-game configuration via Mods menu → Wave Defense → Config (all `wavedefense-common.toml` settings, five tabs, saves on close).
- **UI design**: `GuiTheme` extended with 7 constants + 5 helpers; all 9 main screens and HUD use consistent theme (colors, cards, progress bars, section dividers, badges).
- **Loot triggers**: 11 of 20 `LootSpawn.Trigger` values now have runtime dispatch points — added `PLAYER_DEATH` (PvE), `TIMER_60`, `TIMER_120`, `TIMER_300` alongside the previous 7. `TIMER_CUSTOM` wave trigger also wired via `tickTimerCustomForLocation()`.
- **GuiTheme** applied uniformly to all ~25 GUI screens: `GuiTheme.renderBackground`, title color `GuiTheme.TEXT`, `g.flush()` before every `ScissorHelper.disable()` — eliminates deferred-text scissor bleed.

---

## Installation

1. Install Forge `1.20.1` (`47.2.0+` recommended).
2. Copy the built `wavedefense-0.2.53.2.jar` into the `mods/` folder.
3. **Optional**: install Mine and Slash (`mmorpg` mod, v6.1.0+) to unlock per-location mob level / XP / resistance settings.
4. Start the client or dedicated server.

---

## Key Bindings

| Key | Action |
| --- | --- |
| `V` | Open the main Wave Defense menu |
| `B` | Open the shop directly |
| `G` | Leave the current location |

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
- 30-second grace period when the last player leaves mid-wave — wave resumes if someone rejoins.

### Mine and Slash compatibility (optional)

When Mine and Slash (`mmorpg` mod, v6.1.0+) is installed, each PvE location gains extra
per-location settings in the Special tab of the location editor:

| Setting | Effect |
|---------|--------|
| **Mob Level** | Sets the MnS level of every mob spawned in this location |
| **XP Drop Bonus %** | Adds a PERCENT `bonus_exp` modifier to every spawned mob |
| **Fire Resist** | Adds a FLAT `fire_resist` modifier |
| **Water Resist** | Adds a FLAT `water_resist` modifier |
| **Lightning Resist** | Adds a FLAT `lightning_resist` modifier |
| **Chaos Resist** | Adds a FLAT `chaos_resist` modifier |
| **Physical Resist** | Adds a FLAT `physical_resist` modifier |

All values default to `0` (no override — MnS uses its own defaults).  
The section is hidden entirely when Mine and Slash is not installed.

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

### In-game (recommended)

Open the **Mods** menu → select **Wave Defense** → click **Config**.  
A five-tab GUI covers every setting without editing any file manually:

| Tab | Settings |
|-----|----------|
| **General** | HUD overlay, default wave time, UI tooltips, lobby timer, location game mode |
| **PvP** | Hide enemy nametags, default rounds, default buy time |
| **Mobs & Shop** | Mob equipment toggle, armor drop chance, shop categories, shop hotkey |
| **Limits** | Max mob types, waves, spawn points, shop items, loot spawns (1–9999) |
| **Debug** | Admin debug messages, server log verbosity |

Changes are written to disk immediately on **Save & Close**.

### File (manual)

Common config file:

```text
config/wavedefense-common.toml
```

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


## License

MIT.
