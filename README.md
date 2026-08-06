# Wave Defense Mod - v0.4.0

Wave Defense is a PvE/PvP Forge mod for **Minecraft 1.20.1** and **Java 17**.
It lets server owners build configurable arena locations with mob waves, team PvP,
shops, loot events, portals, boundaries, HUD panels, and in-game admin editors.

> A frozen, work-in-progress **1.16.5 / Forge 36.x / Java 8 backport** lives
> under `1.16.5/` — see its `PORT_STATUS.md`. The main, fully-supported version
> is this 1.20.1 build.

---

## Status

Version `0.4.0` — **gameplay depth, restored editors, and a large-shop overhaul.**
Four opt-in systems give an arena a reason to be replayed; around them sits a long
tail of correctness work — settings that had silently become uneditable are back,
arenas no longer leak their mobs, one bad location can no longer take down the server
tick, and the editing protocol was reworked so a shop holding thousands of modded
weapons stops breaking every save on that location.

Existing worlds load and play identically until an admin opts in — every new setting
takes a default that reproduces the old behaviour.

> **Upgrading:** the network protocol moved to version 9, so **client and server must
> be updated together** — a mismatched client is refused rather than left to misread
> the stream. The `maxShopItems` default rises from 100 to 5000 and is now enforced on
> the bulk-import path that used to bypass it; **existing config files keep their stored
> value**, so raise it by hand if yours still says 100.

**v0.4.0 highlights:**

- **Endless mode** — waves never run out; the score becomes how far you got.
  Ranked on its own leaderboard, recorded on death because endless never reaches
  a victory. Mob stats grow linearly per loop, deliberately not compounding.
- **Wave modifiers** — every Nth wave rolls one of eight twists (Swift, Armored,
  Regenerating, Enraged, Tough, Phantom, Volatile, Venomous), announced in chat.
  Volatile explosions never damage terrain — arenas are hand-built.
- **Difficulty presets** — Easy / Normal / Hard / Nightmare scale mob health,
  damage, count **and point rewards**, each with its own leaderboard.
- **Lifetime player profiles** — waves, best wave, kills, matches, playtime and a
  level that persists across runs, locations and restarts.

**Fixed — mobs wandered off instead of hunting.** The targeting goal was on the
wrong selector (`goalSelector` instead of `targetSelector`), and `FOLLOW_RANGE`
was never raised from the vanilla 16 blocks, so on a large arena mobs literally
could not perceive anyone. Spiders made it obvious.

**Fixed — dead arenas leaked their mobs forever.** Wave mobs are spawned
`persistenceRequired`, and the teardown path taken when the last player dies
dropped the tracking set without removing the entities. Every run that ended in
death stranded its whole live wave.

**Fixed — boundary particles cost far more than they were worth.** The entire ring
was drawn and broadcast to everyone nearby: ~471 particle packets per second at
the default radius. Each player now gets only the arc they face, sent to them
alone — and can turn mod particles off entirely in their own settings.

**Restored — settings that had become unreachable.** Deleting the legacy editors in
v0.3.0 took their UI with them: mob spawn points, scatter radius, starting kit,
completion points, first-wave delay, keep-loot-on-exit, player spawn radius,
starting points, and both location-trigger fields. All are editable again, and a
new test fails the build if it ever happens again.

**Fixed — a large shop broke every editor on that location.** Reported as
`Payload may not be larger than 32767 bytes` when bulk-adding 3000+ TACZ guns, but the
upload was only the visible half: any location whose shop had grown that large could no
longer be edited at all — renaming the arena, changing one price, editing a wave. Every
save shipped the whole location, several megabytes, through a 32 KB packet.

Seven location lists hold modded items and can each exceed that limit on their own, so
none of them travels inside a location payload any more. `LocationSection` is the single
source of truth for which those are; a generic chunked channel carries them, splitting by
encoded size rather than element count because a rifle with attachments and a plain sword
are orders of magnitude apart. The bulk upload now sends one item per packet, paced across
client ticks, and only the last packet triggers the save and broadcast.

**Fixed — 17 of 31 config options did nothing.** `enableHUD` never gated the HUD, three
`max*` caps were not enforced, `pvpHideEnemyNametags` was ignored because hiding was
hard-coded, and the `default*` values were never applied to new locations. All are wired
to what their description promises, and a test now fails the build if an option is exposed
that nothing reads.

**v0.3.0 (previous) — HUD fixes + cleanup:**
- **Fixed: the next-wave timer now actually counts down.** It was only ever set
  when you joined a location and never refreshed, so the HUD froze at the join
  value (players saw `0:30` linger forever). The server now pushes live wave +
  countdown once per second, and the client interpolates between syncs for a
  smooth read.
- **Fixed: the wave counter now advances** in PvE — same root cause.
- **HUD status panel** — points / wave / timer are now one themed panel with a
  backdrop, a draining progress bar, and urgency colours (amber under 10 s, red
  under 5 s) instead of three bare white lines.
- **Concurrent multi-admin editing** — section-level merge saves, so two admins
  editing different parts of the same arena no longer overwrite each other.
- **Legacy editors removed** — `LocationEditorScreen` + `PvpLocationEditorScreen`
  (deprecated since v0.2.56) deleted; the `📜 Legacy` admin button is gone.
  The unified 6-tab editor is now the only location editor.
- **`TriggerEvaluator` refactored** — the 130-line trigger-condition switch was
  split into named, behaviour-identical helpers; nested inventory lambdas and
  duplicated value-lookup blocks consolidated.

**v0.2.66 (previous) — hardening & performance:**
- O(1) location lookup (`LinkedHashMap` index), atomic + async/debounced saves,
  rate limits on 7 packets, path-traversal guard, critical-path logging,
  data-version migration hook.

See [CHANGELOG.md](CHANGELOG.md) for the full history (0.2.54 → 0.4.0).

**v0.2.54.1 hotfix:**
- Tacz bulk-add — captures every NBT-distinct gun variant from creative tabs
  (e.g. AK-47 + AK-47-with-scope + AK-47-with-suppressor become 3 separate
  shop items). Was collapsing variants into 1 default per gunId.
- Minimap player facing arrow now points in the correct direction (was rotated
  by 90° due to a triple sign-inversion bug).

**v0.2.54 changes:**
- **Critical** — Adding 100+ Tacz guns at once no longer crashes the client.
  Replaced the giant `UpdateLocationPacket` with chunked `BulkAddShopItemsPacket`
  (25 items per network packet).
- **New** — Per-location bbox (2 corner points) configured in the Special tab.
- **New** — Tactical PvP minimap (96×96 px, bottom-left HUD) when bbox is set
  and `minimapEnabled = true`. Shows teammates + your facing direction, no
  enemy positions revealed.
- Teammate positions now in the periodic SyncTeammates packet for live dots.

**v0.2.53.7 changes:**
- Hitbox renderer (F3+B) now suppressed in PvE too, not only PvP.
- Teammate HUD HP bars now refresh once per second (was only on death / join / leave).
- BR border damage no longer applies during the initial wait phase.
- Admin shop editor gets a Tiles / List toggle — visual parity with PlayerShopScreen.

**v0.2.53.6 hotfix:**
- `checkRoundWinner()` no longer declares a winner when only one team is in the match —
  prevents round-ends-instantly bug when both players land in the same team.
- Empty spawn-point list or out-of-range index on PvP join no longer crashes — clean
  rejection with a player-facing error message.
- Yellow `⚠` warning broadcast at round start when only one team is represented, so
  admins immediately spot configuration issues.

**v0.2.53.5 hotfix:**
- New `CreativeTabHelper` force-builds every creative tab before scanning, so the
  item picker is never empty and Tacz `(0)` category counts are gone.
- "All" tab has a `ForgeRegistries.ITEMS` fallback — every registered item is
  guaranteed to show up even if no creative tab claims it.

**v0.2.53.4 changes:**
- Yellow outline + count overlay show the currently selected item in the picker.
- `LMB +1 · RMB -1 · Shift+LMB +10 · Shift+RMB reset` — click counting replaces
  click-to-confirm; explicit Confirm / Cancel buttons.
- Picker count flows back into the slot's `×N` EditBox automatically.

**v0.2.53.3 changes:**
- Item picker (shop / starting items / loot) now discovers tabs dynamically via
  `CreativeModeTabRegistry` — same labels, same items, same ordering as the creative
  inventory. Modded tabs (Tacz, Mine and Slash, datapacks) appear automatically.
- Virtual "All" tab aggregates every stack from every tab for free-form search.
- `◄ / ►` paging when the tab strip overflows.
- Simplified `TaczCompat`: removed brittle GunData reflection paths; bulk-add still
  works through our 8 fixed categories (pistol / rifle / shotgun / smg / sniper / rpg
  / mg / other).

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
2. Copy the built `wavedefense-0.2.55.jar` into the `mods/` folder.
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
