# Changelog

## [0.2.53.5] - 2026-06-01 (hotfix)

### Fixed — Item picker and Tacz bulk-add showed empty lists

Tester reported the picker tab strip was empty (only the "All" virtual tab
visible, "0 items" in the footer) and the Tacz bulk-add screen showed `(0)`
in every category — even with Tacz installed and lots of guns available.

Root cause: in Forge 1.20.1, `CreativeModeTab.getDisplayItems()` returns an
empty collection until `BuildCreativeModeTabContentsEvent` has fired for
that tab. Our scan ran *before* the build event in this scenario, so it
saw nothing.

**New `gui/CreativeTabHelper.java`** — force-builds every loaded creative
tab using the current world's `FeatureFlagSet` and `RegistryAccess`, then
exposes a safe `getDisplayItems()` wrapper. Repeated calls are idempotent
and cheap.

- `ItemSelectionScreen.discoverCreativeTabs()` calls
  `CreativeTabHelper.forceBuildAllTabs()` first so the tab strip is
  populated on first open.
- `ItemSelectionScreen.buildAllStacks()` adds a `ForgeRegistries.ITEMS`
  fallback pass — every registered item that no tab exposed is still
  available in the "All" tab. The picker is now never empty.
- `TaczCompat.discoverGuns()` force-builds tabs before scanning, then
  refuses to cache an empty result so the next attempt can re-scan after
  Forge's build event fires.

After this fix the picker shows every mod's creative tabs verbatim and
Tacz bulk-add correctly lists `(N)` guns per category.

---

## [0.2.53.4] - 2026-06-01

### Changed — Item picker selection feedback + click-counted quantity

Tester reported that it was not clear whether an item had actually been picked
in the shop item editor, and that the only way to set quantity was through the
small per-slot `×N` field.

`ItemSelectionScreen` now provides explicit selection feedback and click-based
quantity:

- **Bright yellow 2-pixel outline** around the currently selected slot —
  visible at a glance.
- **Stack count overlay** drawn on the selected slot via vanilla's
  `renderItemDecorations` so the chosen quantity is shown in the same place
  it would appear in the inventory (corner of the icon).
- **Click counting** instead of click-to-confirm:
  - `LMB` on an item: +1 to its count (or selects it and sets count to 1 if
    a different item was selected before)
  - `RMB`: −1, drops to zero, deselects
  - `Shift+LMB`: +10
  - `Shift+RMB`: full reset
  - Clamp: 1–64
- **Confirm / Cancel** buttons replace the old "Close" — confirm sends the
  selected stack with the click-counted count back to the parent callback;
  cancel just closes without changing anything.
- Footer shows the click-hint (`LMB +1 · RMB -1 · Shift+LMB +10 · Shift+RMB
  reset`) and the live `× N` count so the admin never has to guess.
- Re-opening the picker for an existing slot pre-populates the counter from
  the slot's stack so successive edits are non-destructive.

`ShopItemEditorScreen` adopts the picker's chosen count into its per-slot
`×N` EditBox automatically, so the existing fine-tune-via-typing path still
works for values the click-counter can't reach quickly.

3 new lang keys × 8 langs: `wavedefense.item.click_hint`,
`wavedefense.item.selected_count`, `wavedefense.tab.all`.

---

## [0.2.53.3] - 2026-06-01

### Changed — Item picker now mirrors the creative-inventory tab layout

The shop / starting-items / loot item picker previously had a hard-coded list
of 6 categories (All / Weapon / Armor / Potion / Food / Other). With modded
content this categorisation was inconsistent — admins had to hunt for an item
when its modded category didn't match our enum.

`ItemSelectionScreen` now discovers tabs at runtime via
`CreativeModeTabRegistry.getSortedCreativeModeTabs()` — the same source the
vanilla creative inventory uses. Every loaded, non-empty creative tab becomes
a tab in our picker, with the **same label, same items, and same ordering**
as in creative.

- The first tab is a virtual "All" (aggregates every stack from every tab,
  deduplicated, sorted by name) — handy for free-form search.
- Modded tabs (Tacz gun categories, Mine and Slash gear tabs, datapack tabs)
  appear automatically without code changes.
- A `◄ / ►` strip lets the admin page through tabs when there are too many to
  fit on one row.
- Search remains scoped to the active tab.
- The old `Category` enum (`Weapon` / `Armor` / `Potion` / `Food` / `Other`)
  and the separate Tacz sub-tab row are removed — Tacz tabs now sit inside the
  main strip alongside everything else. The `wavedefense.item.category.*`
  lang keys are kept (unused) for compatibility with downstream code that
  might still reference them.

### Simplified — `TaczCompat` (after 0.2.53.2)

Stripped the reflection-into-`GunData` paths entirely — they were ineffective
and overcomplicated. Gun discovery now:

1. Walks creative tabs once, captures every Tacz-namespaced stack carrying a
   `GunId` NBT tag (template preserved for default attachments / pre-fill).
2. Buckets each gun into one of 8 fixed categories via substring matching on
   the gun id (`glock_*` → pistol, `ak*` → rifle, `m870` → shotgun, etc.).
3. Sorts results by category index then display name.

`TaczBulkAddScreen` still uses these 8 categories for bulk adds; the regular
item picker now shows Tacz guns through whichever creative tab the gunpack
author put them in (so Tacz's own sub-tab hierarchy is preserved verbatim).

---

## [0.2.53.2] - 2026-06-01 (hotfix)

### Fixed — Tacz gun discovery (shop showed "0 of every category")

The previous implementation tried to reach into Tacz's internal
`Map<ResourceLocation, GunData>` via reflection. On test servers — even with
many vanilla Tacz guns + datapack-added guns — this map either could not be
found at the expected class path or was empty by the time the editor was
opened, so every category tab showed `(0)` and bulk-add did nothing.

**Rewritten `TaczCompat.discoverGuns()`** to scan every loaded
`CreativeModeTab` (via `CreativeModeTabRegistry.getSortedCreativeModeTabs()`)
exactly the way `ItemSelectionScreen.buildAllStacks()` already does for the
general item picker. For each `ItemStack` in the `tacz` namespace that carries
a `GunId` NBT tag we:

1. Capture the stack itself as the entry's `template` (preserves any default
   attachments or pre-fill the gunpack ships).
2. Categorise via reflection into `GunData` **if available**, otherwise via
   substring matching on the gun id path (`glock_*`, `ak*`, `m870*`,
   `awp/kar98/mosin/*sniper*`, etc.).
3. Fall back to `other` for unrecognised ids — admin still gets them via the
   "Tacz All" or "Other" tabs.

`buildGunStack(gunId)` now returns the cached template `.copy()` first, only
constructing a bare container item with `GunId` NBT as a last resort.

Result: every gun the admin sees in their creative inventory — including
datapack-added ones — appears in the shop picker and is reachable via
bulk-add. Counts in the sub-tabs are accurate.

A one-line `[WD/Tacz] discovered N Tacz guns via creative tabs` info log
confirms how many guns were picked up after Tacz initialises.

---

## [0.2.53.1] - 2026-06-01 (hotfix)

### Fixed — Monitor alert spam on dev/test startup

`WaveDefenseMonitor` was broadcasting `[ALERT WARNING] Current TPS: 13.9 (threshold: 18.0)`
and `[ALERT CRITICAL] Current TPS: 13.9 (threshold: 15.0) — Server performance severely
degraded!` to every operator on every server boot, including dev workspaces where the
first 30–60 seconds of low TPS is normal load-time behaviour.

Three changes:

1. **Broadcast is now opt-in.** New config `debug.monitorBroadcastAlerts` (default
   `false`). Alerts are still written to the server log; only the chat broadcast is
   suppressed. Enable for live diagnostics on a production server.
2. **Startup grace period.** All alert evaluation is suppressed for the first 60 seconds
   of monitor uptime so world-load lag never triggers warnings.
3. **Thresholds relaxed.** `TPS_WARNING` 18 → 12, `TPS_CRITICAL` 15 → 8 — values that
   only fire under genuine sustained server-wide problems.

Also fixed the duplicated `[ALERT WARNING]` in the chat message — the translation key
`wavedefense.auto.alert_s_s_942c0f5f` had a literal `[ALERT %s]` prefix, but the first
substitution already carried the severity label. Reduced to `§c[%s] §f%s`.

---

## [0.2.53] - 2026-06-01

### Added — PvP UX gaps, Tacz compat, scoreboard, and shop quantity fix

**Part A — Shop item quantity fix**
`ShopItemEditorScreen` previously had no UI for `ItemStack.getCount()` — every shop item
was forced to count 1. Each of the 4 item slots now has an `×N` count `EditBox` (1–64);
values persist via a `pendingCounts[]` buffer across `rebuildWidgets()` and are applied
to each non-empty stack in `save()`.

**Part B — Five PvP UX improvements**

- **B1 — DM spawn modes** (`Location.DmSpawnMode`: `TEAM_SPAWN` / `RANDOM_SPAWN` /
  `SMART_SPAWN`). Admin cycles modes via a new button in the Deathmatch editor section.
  Smart spawn picks the candidate with the largest min-distance to any living enemy
  (target ≥10 blocks). New helper `PvpRoundManager.pickDmSpawn()` is shared by
  `PlayerRespawnHandler` (respawn) and `PvpRoundManager.startActiveRound()` (initial DM
  spawn) so behaviour is identical in both paths.

- **B2 — DM kill leaderboard HUD**: top-right panel in DM mode shows the player's own
  kill count, the current leader (name + kills), and the kill target. Uses the existing
  `ClientPvpStateManager.getPlayers()` data — no extra network traffic.

- **B3 — CtP capture speed multiplier**: when enabled (`Location.ctpSpeedMultiplier`),
  every additional teammate standing on a point increases capture rate proportionally
  (`progress += sign × min(teamCount, 4)`). Capped at 4× to keep mechanic meaningful in
  large lobbies. Off by default for backward-compat. Editor toggle visible only for CtP.

- **B4 — CtP "capture all points" win condition** (`Location.ctpCaptureAllWin`):
  when enabled, owning every capture point simultaneously instantly ends the round with
  the owning team as winner. Layered on top of the existing first-to-score / timer
  conditions. New broadcast message `wavedefense.msg.ctp_all_points_captured`.

- **B5 — Post-match scoreboard**: new `OpenPostMatchScoreboardPacket` (S→C) sent from
  `PvpRoundManager.endPvpMatch()` to every player in the location, carrying mode label,
  winning team, per-player stats (kills/deaths/assists/points) and per-team round wins.
  New `PostMatchScoreboardScreen` renders the table sorted by points→kills→deaths with
  the winning team's row highlighted. 10 new lang keys across all 8 lang files.

**Part C — Optional Timeless and Classics Zero (Tacz) integration**

- **`compat/TaczCompat.java`** — pure-reflection compatibility layer mirroring the
  proven `MineAndSlashCompat` pattern. Zero compile-time dependency on Tacz; every
  public method is a no-op when Tacz is absent.

- **Gun discovery** — reflects into one of several known Tacz class paths
  (`com.tacz.guns.resource.CommonAssetManager`,
  `com.tacz.guns.resource.manager.CommonGunPackLoader`, etc.) to find the
  `Map<ResourceLocation, GunData>` of currently loaded guns. Each gun's category
  (pistol/rifle/shotgun/SMG/sniper/RPG/MG) is read via `getType()`/`getCategory()`
  reflection with synonym normalisation. **Fallback**: when Tacz internals cannot be
  read, enumerates every item in the `tacz` namespace via `ForgeRegistries.ITEMS` as
  category `other` and logs a single warning.

- **Shop item picker** (`ItemSelectionScreen`) — when Tacz is loaded, a second filter
  row appears below the standard 6 categories: `Tacz All / Pistols / Rifles / Shotguns
  / SMGs / Snipers / RPGs / MGs / Other`. Selecting a Tacz tab replaces the regular
  item list with stacks built via `TaczCompat.buildGunStack(gunId)` (sets `GunId` NBT
  on the `tacz:modern_kinetic_gun` container item). Clicking the active tab again
  clears the Tacz filter.

- **Bulk-add** (`TaczBulkAddScreen` + new "🔫 Tacz" button in `ShopEditorScreen`
  global view) — admin enters a default price per category and clicks `Add Pistols (12)`
  → 12 new `ShopItem` entries are appended to the location's shop list, each containing
  one gun stack with the configured price and category `WEAPON`. After bulk-add, the
  packet is sent and the editor returns to the parent screen showing the new rows.

- **mods.toml** — Tacz declared as optional dependency
  (`mandatory=false`, `versionRange="[1.0.0,)"`).

### New files
- `src/main/java/com/wavedefense/compat/TaczCompat.java`
- `src/main/java/com/wavedefense/gui/PostMatchScoreboardScreen.java`
- `src/main/java/com/wavedefense/gui/TaczBulkAddScreen.java`
- `src/main/java/com/wavedefense/network/packets/OpenPostMatchScoreboardPacket.java`

### New translation keys (+46 per language × 8 langs = 368 keys total)
Categories: shop item count (1), DM spawn modes (4), DM HUD (3), CtP speed/capture-all
(5), scoreboard (10), Tacz tabs + bulk UI (16), plus messages for capture-all and
timeout draw.

---

## [0.2.52] - 2026-05-29

### Fixed — Server crash, gameplay correctness, and stats visibility (14 fixes)

**Dedicated-server crash — `Screen` class loaded by `@Mod` class (CRITICAL)**
`WaveDefenseMod` (a shared `@Mod` class loaded on both client and server) imported
`net.minecraftforge.client.ConfigScreenHandler` and `com.wavedefense.gui.WaveDefenseConfigScreen`
at the top level. `net.minecraftforge.client.*` is a client-only package — on a dedicated server
the classes do not exist, causing `NoClassDefFoundError` during mod initialization.
Fixed by moving all client-only setup into a private `@OnlyIn(Dist.CLIENT) static final class ClientSetup`
and routing the call through `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::register)`.
The `@OnlyIn` annotation is stripped by Forge's class transformer on the server; `DistExecutor`
ensures the lambda is never evaluated on the server JVM regardless.

**BackupSystem never started (A3)**
`WaveDefenseBackupSystem` had a complete ~700-line implementation but `initialize()` and
`startScheduledBackups()` were never called. Backup scheduler now starts in `onServerStarting()`
and `shutdown()` is called in `onServerStopping()`.

**Auto-difficulty scaling broken — `recordWaveCompletion()` never called (A2)**
`WaveAutoScaler.recordWaveCompletion()` existed but had no call site. The scaler accumulated
no data and always applied default difficulty. Fixed in `LocationSession.onWaveCompleted()`:
`WaveMetrics` is populated from `waveStartMobCount` / `mobsKilled` and passed to the scaler
after every wave.

**Null guard for `locationManager` in `WaveManager.tickSession()` (A7)**
`tickSession()` called `locationManager.getLocation()` without checking whether
`WaveDefenseMod.locationManager` was non-null (it is null before `onServerStarting` fires).
Added early return if `locationManager == null`.

**`pvpPenaltyDeducted` set not cleared on session end (E1)**
The dedup-set that prevents double death-penalty deductions in PvP was cleared in
`startActiveRound()` but not in `endPvpMatch()`. A UUID carried over from one session
could silently suppress the penalty in the next independent session. Fixed:
`pvpPenaltyDeducted.clear()` added before `ctx.removeSession()` in `endPvpMatch()`.

**`UUID.fromString()` without try-catch on NBT load (C2)**
`LocationSession.loadUuidSet()` called `UUID.fromString(string)` directly. A single
malformed UUID in persisted NBT (e.g. after a corrupt save) would throw
`IllegalArgumentException` and prevent the entire session from loading. Wrapped in
`try/catch`; malformed entries are skipped with a `LOGGER.warn`.

**Dead-mob sweep missed `triggerMobs` (A8 extension)**
The 40-tick periodic cleanup iterated `spawnedMobs` but not the per-wave `triggerMobs`
sets. Trigger-wave mobs killed by non-player causes (fire, fall, void) stayed in the
tracking set, preventing trigger conditions that check mob-count from ever clearing.
Extended the sweep to also clean `triggerMobs`.

**Reflection not cached in `InfoPanelManager` (D2)**
`setTextDisplayText()` and `setBillboardViaReflection()` used reflection on every call
(once per second per TextDisplay entity). The `EntityDataAccessor` field is now cached in
a `static volatile` field on first successful lookup; subsequent calls skip the reflection
entirely. Cache resets on exception so a future Forge/Minecraft update won't silently
cause NPEs.

**CtP/KotH HUD overlay missing (G7)**
`PlayerHUD` had no code path for Capture-the-Point or King-of-the-Hill game modes.
Added `renderCtpOverlay()` driven by `ClientCtpStateManager.isActive()`: a top-centre
panel showing each capture point's name, owner (team-coloured), and capture progress bar,
plus a score row with team scores, score-to-win, and round timer.
New key `wavedefense.hud.ctp_neutral` added to all 8 language files.

**Orphan session not ended when location is deleted (C1)**
`DeleteLocationPacket` called `locationManager.removeLocation()` directly. If an active
session existed for that location, it stayed in `waveCtx.sessions` forever — players could
not rejoin the (now non-existent) location, and the session leaked memory.
Fixed: `waveManager.endSessionForLocation()` is called before `removeLocation()`.

**Zone particles always spawned in the Overworld (A5)**
`ZoneActivationManager.spawnZoneParticlesForLocation()` used
`getServer().getLevel(Level.OVERWORLD)` unconditionally. Nether, End, and custom-dimension
arenas never showed their configured particles.
Fixed with a three-level fallback: (1) level of any player already in the session;
(2) level of any player within `radius + 32` blocks of the zone center; (3) Overworld.

**`WaveDefenseMonitor` held a stale `WaveContext` (A4)**
The singleton captured `WaveDefenseMod.waveManager.waveCtx` as a `final` field in its
constructor. If `getInstance()` were called before `WaveManager` was initialized, `waveCtx`
would be permanently `null`. Replaced the field with a static helper method `waveCtx()`
that resolves the context dynamically on each call.

**GameStats never sent to client (G4)**
`SyncStatsPacket` was registered and had a working client handler, but was never sent.
Players saw stale zeroes in `StatsScreen`. Fixed: new `WaveManager.syncPlayerStats(player)`
method sends the packet; it is called from `syncPlayerData()` (covers location join/leave)
and directly after `stats.incrementMobsKilled()` in `onMobKilled()`.

**Location triggers for `PLAYER_HAS_ITEM` / `PLAYER_LOW_HEALTH` etc. always returned `false` (G6)**
`TriggerEvaluator.tickLocationTriggers()` delegated to `checkWaveTriggerCondition()` for
item and health checks, which internally called `ctx.getPlayersInLocation(locName)`. But
the location is not yet active — nobody has joined — so that list is always empty.
Fixed: new private method `checkLocationTriggerForPlayers(trigger, nearbyPlayers, locName)`
checks the already-collected `nearbyPlayers` list directly, bypassing the session lookup.
Covers: `PLAYER_HAS_ITEM`, `PLAYER_LOW_HEALTH`, `PLAYER_HAS_DIAMOND`, `PLAYER_HAS_IRON`,
`PLAYER_HAS_SWORD`, `PLAYER_FULL_INVENT`.

---

## [0.2.51] - 2026-05-25

### Fixed — Server stability, UX clarity, and DoS hardening (7 fixes)

**Server-tick crash isolation (D3)**
`EventHandler.onServerTick()` called `waveManager.tick()` with no error boundary — a single
NPE anywhere in `BattleRoyaleManager`, `CapturePointManager`, `PvpRoundManager`, etc. would
propagate uncaught through Forge's tick loop and crash the entire server.
Wrapped the call in `try/catch(Exception)` + `LOGGER.error` so the offending tick is skipped
and the server continues running; the full stacktrace appears in the log for diagnosis.

**Keybinding conflict — Leave Location moved from L to G (K1)**
`L` is Minecraft 1.20.1's default Advancements hotkey.  With both bindings active, pressing
`L` simultaneously opened the Advancements screen and sent the leave-location C→S packet,
causing unexpected location exits mid-wave.  `leaveLocationKey` now defaults to `G` (not
bound in vanilla or any mainstream mod).  Existing players can re-bind in Controls settings.

**Import confirmation dialog — prevents accidental data loss (F6)**
Clicking a file name in `ImportExportScreen`'s import list sent `ImportLocationPacket`
immediately, with no indication that the named location's existing data would be overwritten.
A two-step banner now appears first:
`⚠ Перезаписати <name>? Дані будуть втрачені! | ✔ Підтвердити | ✕ Скасувати`
The packet fires only after explicit confirmation; Скасувати clears the banner.

**LocationInfoScreen wired up in player menu (A2/C1)**
`LocationInfoScreen.java` existed but was never opened from anywhere — effectively dead code.
Added a compact `§bℹ` button (22 px) to the right of every location row in `PlayerMenuScreen`.
Clicking it opens the read-only info panel showing wave count, mob types, shop availability,
and inventory mode.  The main join button shrinks by 25 px to make room; PvP locations with
no spawn points still show the inactive "not ready" badge as before.

**Surrender button visually distinct from Exit PvP (B4)**
In the active PvP action menu both "Вийти з PvP" (yellow `§e🚪`) and "Здатися" looked
identical in the prior build — same plain-text styling, no consequence hint.  The Surrender
button now uses the `surrender_penalty` lang key — `§c🏳 Здатися (з пенальті)` — rendered
in red with an explicit penalty note, making the difference immediately legible before clicking.

**Minimum player count shown in team-select screen (A3)**
`PvpTeamSelectScreen` displayed mode and kill-target rules but never told the player how many
participants are required to trigger match start.  A third info row now appears below the rules
line: `§8Мін. гравців: §7N` where N comes from `location.getPvpMinPlayers()`.
New key `wavedefense.pvp.team_select.min_players` added to all 8 language files.
Team-card grid start Y adjusted +18 px to accommodate the extra row.

**C→S packet rate limiter — DoS protection (G1)**
No server-side rate limiting existed for C→S packets.  A malicious client (or a macro) could
flood expensive server-side operations indefinitely via `TeleportPacket`, `SurrenderPacket`,
`ExitPvpPacket`, `LeaveLocationPacket`, and `RequestLeaderboardPacket`.
New `network/PacketRateLimiter.java` — a thread-safe per-player per-packet-type timestamp
map (`ConcurrentHashMap`) with configurable per-type cooldowns:

| Packet | Cooldown |
|--------|---------|
| `TeleportPacket` | 3 s |
| `SurrenderPacket` / `ExitPvpPacket` | 10 s |
| `LeaveLocationPacket` | 5 s |
| `RequestLeaderboardPacket` | 2 s |

Excess packets are silently dropped server-side.
`PacketRateLimiter.evictPlayer()` is called on `PlayerLoggedOutEvent` to keep map size bounded.

---

## [0.2.50] - 2026-05-25

### Fixed — CtP / KotH / Leaderboard audit pass (12 bugs + 6 lang keys)

**Capture radius — cylinder, not sphere (M-10)**
`CapturePointManager.isInRadius()` used 3D sphere distance (`dx²+dy²+dz²`), so a player
standing on a cliff one block above or below the point never contributed to capture progress.
Changed to horizontal-only 2D cylinder check (`dx²+dz²`), matching the expected tower-defence
feel where only X/Z distance matters.

**`captureTimeTicks` sent to client for accurate HUD progress bar (H-3)**
The client-side HUD progress bar divided capture progress by the hardcoded constant 200 ticks.
If an admin configured a non-default capture time (e.g. 5 s = 100 ticks), the bar always showed
50% at max progress instead of 100%.
Fix: `CapturePointManager.sendSync()` now builds a `Map<String, Integer> captureTimeTicks`
(pointId → `captureTimeSec × 20`) and includes it as the new 4th field of `SyncCtpStatePacket`.
`ClientCtpStateManager` caches the map; `HudOverlay` reads it as the denominator.
`SyncCtpStatePacket` updated: 8-arg constructor, `"capTicks"` NBT key, null-case decode
updated to 8 args. `PvpRoundManager` blank clear-packet updated to match (8 args).

**HUD panel height accounts for active progress bars (M-3)**
The CtP/KotH overlay panel height was calculated using only text rows, ignoring that each
actively-capturing point renders an extra 6-pixel progress bar below its text row.
Fixed: `panelH` now adds `activeProgressBars × 6` where `activeProgressBars` is the count of
points with non-zero capture progress.

**Long capture point names truncated in HUD (L-4)**
Point names longer than 12 characters overflowed the HUD overlay panel.
Fixed: names are clamped to 12 chars with `"…"` suffix before rendering.

**`LeaderboardScreen` podium colors corrected (L-2)**
Rank 1 was rendered in `§6` (dark gold/orange) and rank 3 in `§e` (bright yellow).
Fixed to: rank 1 = `§e` (bright gold), rank 2 = `§7` (silver), rank 3 = `§6` (bronze/dark gold).

**`LeaderboardScreen` player names truncated to 14 chars (M-6)**
Long player names overflowed the player column in the leaderboard table.
Fixed: `MAX_NAME_DISPLAY = 14` with `"…"` suffix.

**`LeaderboardScreen` location selector supports mouse scroll (L-3)**
The location button row had no `mouseScrolled()` override, so players with many locations
could only reach locations not fitting the first row by clicking the ▲/▼ arrows.
Fixed: `mouseScrolled()` increments/decrements `locScrollOffset` and calls `rebuildWidgets()`.

**`LeaderboardScreen` empty/blank location names filtered (H-7)**
`ClientLocationManager.getAllLocationNames()` can return null or blank-string entries for
locations not yet synced to the client. These produced empty tabs and triggered a null location
request to the server.
Fixed: `init()` filters out null/blank names; when the filtered list is empty the screen shows
the `wavedefense.leaderboard.no_locations` message instead of an empty button row. All location
request / tab-building paths guard on empty list.

**`CapturePointEditorScreen` particle grid y-advance corrected (M-9)**
The y-advance after the particle preset button grid used `(PARTICLE_IDS.length / 4 + 1) * 20 + 4`,
producing an extra 20-pixel gap for an 8-item × 4-per-row grid (exactly 2 rows; `+1` was wrong).
Fixed to `(PARTICLE_IDS.length / 4) * 20 + 4`.

**`CapturePointEditorScreen` shows inline error on empty name (L-1)**
Clicking Save with a blank point name silently discarded the save. Players had no feedback.
Fixed: `savePoint()` sets `saveError` string when the name is empty; `render()` draws it in
`§c` red above the Save button. Uses new key `wavedefense.capture_point.error_name_empty`.

**`PvpLocationEditorScreen` dead `mode == null` branch removed (L-9)**
`initModeAndRulesTab()` had `if (mode == STANDARD || mode == null)` — `getPvpMode()` never
returns null (enum field has a default), so the null branch was dead code. Removed.

**`PvpLocationEditorScreen` warns when no capture points configured (M-7)**
Switching to CtP or KotH mode with zero capture points defined would start a round with nothing
to capture. A non-interactive `§c⚠` warning button now appears in the Points tab when the
capture point list is empty, using key `wavedefense.pvp.warning.no_capture_points`.

**Six new translation keys added to all 8 language files (EN · UK · DE · FR · ES · PL · PT-BR · ZH-CN)**

| Key | Purpose |
|-----|---------|
| `wavedefense.capture_point.error_name_empty` | Inline error when saving a point with no name |
| `wavedefense.msg.pvp_draw` | Chat message when a PvP round ends in a draw |
| `wavedefense.msg.ctp_no_points` | Server warning when CtP/KotH round starts with 0 points |
| `wavedefense.msg.point_contested` | Chat message when a capture point becomes contested |
| `wavedefense.leaderboard.no_locations` | Placeholder text when leaderboard has no locations |
| `wavedefense.pvp.warning.no_capture_points` | Editor warning: no capture points defined |

---

## [0.2.49] - 2026-05-25

### Added — Capture the Point, King of the Hill, and Persistent Leaderboard

**New game modes:**
- **Capture the Point (CtP)** — teams compete to capture and hold multiple named points; score
  accumulates per point owned per second; first team to reach `scoreToWin` wins (or highest score
  at timer end in timer mode).
- **King of the Hill (KotH)** — same mechanic with a single contested hill point; configurable
  score-to-win or timer-based round.
- Both modes share configurable `scoreToWin`, `scorePerSec`, `roundDurationSec`, and a
  `firstToScore` toggle.
- Contested behaviour: capture progress freezes when both teams stand on a point simultaneously.

**Capture point management:**
- New `CapturePoint` data class: UUID id, display name, `BlockPos`, capture radius, capture
  time (sec), particle type + count.
- `CapturePointEditorScreen` — in-game editor (list + edit modes) for adding, removing, and
  configuring capture points per location.
- `PvpLocationEditorScreen` now shows a conditional "Points" tab (tab 6) when mode is CtP or KotH.
- `LocationSerializer` / `Location` updated to persist all CtP/KotH fields.

**Server-side game logic:**
- `CapturePointManager` — new sub-manager ticked from `WaveManager`; handles player detection,
  signed capture progress, point flipping, score ticks, win conditions, and particle spawning.
- `PvpRoundManager` — new `declareObjectiveWinner()` path; skips "one team alive" check for
  objective modes; records leaderboard entries on match end.
- `PvpRoundState` — new fields: `pointOwners`, `captureProgress`, `objectiveScore`,
  `roundDurationTicks`; new helpers `initCapturePoints`, `checkObjectiveWinner`, `getLeadingTeam`.

**Persistent Leaderboard:**
- `LeaderboardRecord` — stores player UUID, name, primary score, secondary score, duration, timestamp.
- `LeaderboardManager` — persists `<world>/data/wavedefense_leaderboards.dat`; per-location
  per-mode top-10 lists; atomic file write.
- `WaveDefenseMod` — initialises `LeaderboardManager` on server start, saves on server stop.
- `SessionManager` — records PvE leaderboard entry in `triggerVictory()` (waves + score + time).
- `PvpRoundManager.endPvpMatch()` — records DM/Standard/BR/CtP/KotH entries with kills and
  objective score.

**Networking (3 new packets, protocol → v8):**
- `SyncCtpStatePacket` (S→C) — sent every 20 ticks: point owners, display names, signed capture
  progress, team objective scores, scoreToWin, roundTicksLeft.
- `RequestLeaderboardPacket` (C→S) — client requests top-10 for a given location + mode key.
- `LeaderboardDataPacket` (S→C) — server responds with up to 10 `LeaderboardRecord` entries.

**Client UI:**
- `ClientCtpStateManager` — client-side mirror of active CtP/KotH state, updated by packet.
- `ClientLeaderboardCache` — stores the last leaderboard response for screen rendering.
- `HudOverlay` — new right-side CtP/KotH overlay: per-point ownership + capture progress bars,
  separator, team scores (n/scoreToWin), countdown timer in timer mode.
- `LeaderboardScreen` — full leaderboard UI: location selector, 6 mode tabs (PvE / Standard /
  DM / BR / Capture / KotH), paginated top-10 table with rank/player/score/secondary/time/date
  columns; accessible from `PlayerMenuScreen` when outside any active location.
- `PlayerMenuScreen` — added "🏆 Рейтинг" (Leaderboard) button, hidden when inside a location.

**Localisation:**
- ~35 new translation keys added to all 8 lang files (en_us, uk_ua, de_de, fr_fr, es_es, pl_pl,
  pt_br, zh_cn): mode names, editor labels, HUD strings, chat messages, leaderboard table headers.

---

## [0.2.48] - 2026-05-25

### Fixed — Verification-pass bugs (6 additional fixes)

- **HIGH** `ZoneActivationManager.tick()` accessed `WaveDefenseMod.locationManager` without a null-guard → NPE on every server tick during early startup before `LocationManager` is initialized. Fixed: added `if (WaveDefenseMod.locationManager == null) return;` after the existing `getServer()` guard.
- **HIGH** `PvpRoundManager.pvpPenaltyDeducted` was never cleared between rounds (`startActiveRound()` cleared `pvpKillStreaks` and `pvpPendingRespawn` but omitted `pvpPenaltyDeducted`) → a UUID left in the set from round N would silently suppress the death-penalty deduction for that player's first non-PvP-kill death in round N+1. Fixed: added `pvpPenaltyDeducted.clear()` in `startActiveRound()`.
- **MED** `PvpLocationEditorScreen.saveSharedSettings()` wrapped all 8 field parses in a single `try-catch` → one unparseable field silently discarded all remaining fields (boundary radius, leave timer, damage, portal timers, victory linger, re-entry cooldown). Fixed: individual try-catch per field, matching the pattern already applied to `saveAllRules()` in v0.2.47.
- **MED** `ShopEditorScreen`: point-deletion clamped `scrollOffsetPoints` to `size - 1` instead of `Math.max(0, size - POINTS_PER_PAGE)` → after deleting a point with more than one page, the scroll offset could exceed the last valid page, showing a blank list. Fixed.
- **LOW** `MineAndSlashCompat.initPhase1()` resolved `EntityData.get()` via reflection but never verified the method is actually static → `mGet.invoke(null, mob)` would throw a silent `IllegalArgumentException` if a future MnS version changes `get()` to an instance method. Fixed: added `Modifier.isStatic(mGet.getModifiers())` check that fails init with a clear log message.
- **COSMETIC** `MobEffectsEditorScreen.init()` had a stray double semicolon `super.init();;`. Removed.

---

## [0.2.47] - 2026-05-24

### Fixed — Critical runtime bugs (second audit pass)

**Wave runtime:**
- **HIGH** `WaveAutoScaler.load()` return value was discarded → difficulty scaling always reset to defaults after server reload. Fixed by adding `WaveAutoScaler.loadFrom(CompoundTag)` instance method that applies state in-place to the existing `final` field.
- **HIGH** `ZoneActivationManager` was never instantiated or ticked → auto-activate zone feature (countdown, particles, player entry) was silently non-functional. Fixed by adding `zoneMgr` field + `zoneMgr.tick(this)` in `WaveManager.onServerTick()`.
- **HIGH** `MobSpawnManager` spawned mobs in the Overworld regardless of the location's actual dimension → waves never appeared for Nether / End arenas. Fixed: use `players.get(0).serverLevel()`.
- **HIGH** `WaveManager.fireLootTriggerByName/WithValue` accessed `locationManager` without null-guard → NPE on every mob death during world reload. Fixed.
- **HIGH** `SessionManager.triggerVictory` called `getServer().getPlayerList()` without null-guard → potential NPE during server shutdown mid-game. Fixed.
- **MED** Grace-period cancel used `>= 0` instead of `> 0` → a player rejoining on the exact tick the grace expired could attempt to cancel an already-expiring session. Fixed.
- **MED** Trigger-mob kills (mobs not in `spawnedMobs`) were not counted in `mobsKilled` → `MOBS_KILLED_N` loot triggers fired late. Fixed.
- **MED** `TIMER_60/120/300` AND-conditions computed elapsed from `startTimerMs` which is `0L` before lobby start (always ≥ 60 immediately after lobby expires). Now uses session tick counters `timer60/120/300`. Fixed.
- **MED** `InfoPanelManager.tick()` called `getOrCreateSession()` for every location with panels → ghost sessions created for idle locations, corrupting timer counters for real joins. Fixed: only update panels when an active session exists.

**PvP / Network:**
- **HIGH** `PvpRoundManager.onPlayerKilledPlayer` AND `onPvpPlayerDeath` both deducted `pvpDeathPenalty` from victims → double point penalty in all PvP modes. Fixed via `pvpPenaltyDeducted` tracking set.
- **HIGH** `onPlayerLeave` called `endRound()` directly, bypassing the `ROUND_END_DELAY` phase → state machine skipped, broadcast delayed, `currentRound` could increment twice. Fixed: use `setPendingWinner + startRoundEndDelay(5)` path.
- **MED** Missing `getServer()` null-guard in `rebalancePvpTeams`. Fixed.
- **MED** Path traversal: `ImportLocationPacket` and `ImportShopPacket` accepted client-supplied filenames without canonical-path validation → potential server-side file read outside export directory. Fixed with `getCanonicalPath()` check.
- **MED** `ExportListResponsePacket` encoded names with unlimited `writeUtf` but decoded with `readUtf(64)` → `DecoderException` (client disconnect) on names > 64 chars. Fixed: both now use 256.
- **MED** `AdminTeleportPacket` used PvE join path (`addPlayerToLocation`) for PvP locations → bypassed team assignment. Fixed: check `location.isPvp()` and call `addPlayerToPvpLocation`.
- **MED** `RequestWaveExportListPacket` lacked permission check → any authenticated client could list server export filenames. Fixed: added `hasPermissions(2)`.

**Data layer:**
- **HIGH** `PlayerBackup.loadFromFile()`: `GameType.byName(String)` returns `null` for unrecognised values → NPE in `GameModeSnapshot` constructor, silently discarding the backup. Fixed: use `byName(str, SURVIVAL)` fallback.
- **HIGH** `PlayerBackup`: `BuiltInRegistries.MOB_EFFECT.getKey()` returns `null` for modded potion effects → NPE during backup creation, whole backup lost. Fixed: use `ForgeRegistries.MOB_EFFECTS` with BuiltIn as fallback.
- **MED** `WaveConfig` saved trigger fields only when `triggerEnabled=true` → disabling a trigger, saving, and re-enabling lost all configured type/cooldown/AND conditions. Fixed: always save trigger fields.

**GUI:**
- **HIGH** `MobEffectsEditorScreen.mouseScrolled()` had no upper-bound guard on left-panel scroll → `IndexOutOfBoundsException` possible after element deletion. Fixed.
- **HIGH** `PlayerShopScreen` scroll-down button computed `filteredIndices.size() - itemsPerPage` without clamping to 0 → negative `scrollOffset` → `IndexOutOfBoundsException`. Fixed.
- **HIGH** `PvpLocationEditorScreen.saveAllRules()` wrapped all 15 field parses in one `try-catch` → a single invalid input silently discarded all remaining fields. Fixed: individual try-catch per field.
- **MED** `LocationEditorScreen.switchTab()` didn't reset `specialScrollOffset` → Special tab re-opened at stale scroll position after mode change. Fixed.
- **MED** `LocationEditorScreen.switchTab()` didn't clear `pendingMode` → two-click PvP↔PvE confirmation guard survived tab switches, weakening its safety purpose. Fixed.
- **MED** `AdminMenuScreen.deleteLocation()` scroll clamp was off-by-one → after deleting the last visible item the list could appear empty. Fixed.
- **MED** `ShopEditorScreen` clamped `scrollOffsetGlobal` to `size - 1` instead of `size - ITEMS_PER_PAGE` → allowed scrolling to nearly-blank list. Fixed.
- **MED** `WaveMobSettingsScreen.save()` caught `NumberFormatException` silently, leaving the screen open with no feedback. Fixed: clamp each field to its current value as fallback.

**Low-priority cleanup:**
- `LocationSession.timerCustom` was saved/loaded but never written during gameplay (dead serialisation). Removed from save/load.

---

## [0.2.46] - 2026-05-24

### Added — Mine and Slash optional compatibility (mmorpg mod v6.1.0+)

Added a soft dependency on Mine and Slash (`mod ID: mmorpg`, v6.1.0+).
When the mod is present, a new section appears at the bottom of the **PvE location Special tab**
with per-location overrides for the MnS stat system:

| Field | MnS API call | Default |
|-------|-------------|---------|
| **Mob Level** | `EntityData.setLevel(int)` | 0 = MnS default |
| **XP Drop Bonus %** | `addExactStat(…, "bonus_exp", value, PERCENT)` | 0 = no bonus |
| **Fire Resist** | `addExactStat(…, "fire_resist", value, FLAT)` | 0 = no override |
| **Water Resist** | `addExactStat(…, "water_resist", value, FLAT)` | 0 = no override |
| **Lightning Resist** | `addExactStat(…, "lightning_resist", value, FLAT)` | 0 = no override |
| **Chaos Resist** | `addExactStat(…, "chaos_resist", value, FLAT)` | 0 = no override |
| **Physical Resist** | `addExactStat(…, "physical_resist", value, FLAT)` | 0 = no override |

**Implementation details:**
- Pure reflection — no compile-time dependency on MnS; the mod builds and runs correctly without MnS present.
- Two-phase initialization: Phase-1 resolves `EntityData` class methods once on first use; Phase-2 resolves `addExactStat` and `ModType` enum constants from the first concrete `CustomExactStats` instance encountered.
- Both phases are synchronized, idempotent, and wrapped in try/catch — any reflection failure logs a `WARN` and silently becomes a no-op.
- `hasAnyConfig()` fast-path: if all 7 MnS fields are 0, `applyToMob()` returns immediately with zero overhead.
- Applied in `MobSpawnManager.trySpawn()` after `applyMobEquipment()` and before `world.addFreshEntity()`.
- GUI section is shown **only** when MnS is detected **and** the location is in PvE mode (mob waves only exist in PvE).
- NBT saved sparsely (only non-zero fields written); fully backward-compatible with existing location files.
- `mods.toml` declares `mmorpg` as `mandatory=false`, `versionRange="[6.1.0,)"`.
- 11 new translation keys added to all 8 language files (see table below).

---

### Fixed — P1: Two missing translation keys in 6 language files

`wavedefense.msg.no_spawn_set` and `wavedefense.msg.value_out_of_range` existed in `en_us.json`
and `uk_ua.json` but were absent from `de_de`, `fr_fr`, `es_es`, `pl_pl`, `pt_br`, and `zh_cn`.
Players on those languages saw the raw translation key instead of text. Both keys added to all 6 files.

---

### Fixed — P1: `LocationManager.saveToFile()` — double serialization + non-atomic write

`save()` was called twice per save (once for the main file, once for the backup), performing
a full NBT graph traversal twice unnecessarily. Worse, a server crash between the two writes
could leave both files in an inconsistent state.

**New write pattern (atomic):**
1. Serialize NBT **once** → `data`.
2. Write `data` to `.tmp` file.
3. If the main file exists, rename it to `.bak`.
4. Rename `.tmp` → main file.

Either both files are consistent after the operation, or neither was written.

---

### Fixed — P1: PvP↔PvE mode switch without confirmation

Clicking the mode-toggle button in `LocationEditorScreen` previously switched the mode
immediately, risking accidental configuration corruption. Now requires a two-click confirmation:

- **First click**: button label changes to `"§e⚠ Підтвердити?"`, `pendingMode` is set.
- **Second click** on the same button: mode switch is applied and `pendingMode` is cleared.
- **Any other action**: `pendingMode` resets to `null` — the switch is cancelled.

---

### Fixed — P2: `WaveContext.broadcastToLocation` — preferred `Component` overload added

```java
// New preferred overload — message localises on the client side
public void broadcastToLocation(String locationName, Component component)

// Old overload kept for compatibility, now @Deprecated
@Deprecated
public void broadcastToLocation(String locationName, String message)
```

All existing call sites already use `Component.translatable()` — no call-site changes required.

---

### Fixed — Grace period when the last player leaves mid-wave

When the last player in a PvE session surrenders while a wave is active, the session now enters
a **30-second grace period** (`graceTicksRemaining = 600 ticks`) instead of immediately
despawning all mobs and closing the session.

- Countdown broadcasts every 10 s and every second in the final 5 s.
- If a player rejoins during grace, the timer is cancelled and the wave continues normally.
- New keys: `wavedefense.msg.grace_closing`, `wavedefense.msg.grace_cancelled`.

---

### Fixed — Dead mob sweep covers all dimensions

The periodic dead-mob cleanup in `LocationSession.tick()` previously searched only the Overworld
(`Level.OVERWORLD`). Mobs in the Nether, End, or custom dimensions were never cleaned up, causing
waves to stall if a mob somehow ended up in another dimension.

Fixed by iterating `server.getAllLevels()` instead of using a hardcoded dimension key.

---

### Fixed — `PortalManager` double-spawn guard

Portal penalty mobs could be spawned more than once per tick if `openPortal()` was invoked
before the previous spawn had been fully registered. Added a `portalSpawnPending` flag — set
before spawning, cleared after `addFreshEntity()`. Subsequent calls while a spawn is in flight
are ignored.

---

### Fixed — `SellItemPacket` NBT-aware item matching

`PlayerShopScreen.sell()` previously matched items with `ItemStack.isSameItem()`, which ignores
NBT data. Two items with the same base type but different enchantments or custom names could
be swapped. Now uses `matchesNbtForSale()`: same item type **and** matching NBT (or both having
no NBT). The sell button is enabled only when the player's held item passes this check.

---

### Dead code — `@Deprecated` annotations (P3)

`LocationSession.config` (`public final Location`) and `LocationSession.timerCustom` are now
`@Deprecated` with explanatory Javadoc. Neither field is read anywhere in runtime code:
- `config` is always `null` (passed as `null` in every constructor call).
- `timerCustom` is serialized/deserialized but never mutated during gameplay.

---

### New translation keys (all 8 language files)

| Key | Purpose |
|-----|---------|
| `wavedefense.msg.grace_closing` | Grace period countdown broadcast |
| `wavedefense.msg.grace_cancelled` | Grace cancelled when a player rejoins |
| `wavedefense.msg.not_enough_items` | Sell check: not enough matching items in inventory |
| `wavedefense.section.mine_and_slash` | MnS section header in location editor |
| `wavedefense.mas.loaded_hint` | Confirmation that MnS was detected at runtime |
| `wavedefense.mas.level` | Mob level field label |
| `wavedefense.mas.xp_bonus` | XP drop bonus % field label |
| `wavedefense.mas.resists_header` | Elemental resistances group header |
| `wavedefense.mas.fire_resist` | Fire resistance field label |
| `wavedefense.mas.water_resist` | Water resistance field label |
| `wavedefense.mas.lightning_resist` | Lightning resistance field label |
| `wavedefense.mas.chaos_resist` | Chaos resistance field label |
| `wavedefense.mas.physical_resist` | Physical resistance field label |
| `wavedefense.mas.hint` | MnS section bottom info hint |

---

## [0.2.45.1] - 2026-05-23

### Fixed — GuiTheme migration: all screens uniformly themed

The remaining 14 GUI screens still using `this.renderBackground()` (vanilla stone dirt panel)
were migrated to `GuiTheme.renderBackground()`. Main title colors changed from hardcoded
`0xFFFFFF` to `GuiTheme.TEXT` (`0xFFE7F5FF`). Every `ScissorHelper.disable()` call is now
preceded by `g.flush()` to prevent Minecraft's batched text renderer from bleeding deferred
glyphs across scissor boundaries.

Migrated screens:
`WaveSpawnEditorScreen`, `WaveMobEditScreen`, `RewardsConfigScreen`,
`PvpScoreboardScreen` (+flush), `StartingItemsScreen` (+flush),
`WaveImportScreen`, `WaveImportTargetScreen`, `WaveExportScreen`,
`ShopImportScreen`, `ShopImportTargetScreen`, `ShopPointSelectExportScreen`,
`WaveMobSettingsScreen`, `PvpTeamSelectScreen`, `LocationInfoScreen`.

Combined with the 11 screens migrated in 0.2.45, `GuiTheme.renderBackground` is now
used consistently across all ~25 screens in the mod.

---

### Fixed — Timer and death loot/wave triggers never fired

`LootSpawn.Trigger.TIMER_60 / TIMER_120 / TIMER_300` and `WaveTrigger.TIMER_60 / TIMER_120 / TIMER_300`
had no dispatch points — the timers in `LocationSession` were serialized but never incremented.

**Added in `LocationSession.tick()`** (fires once the lobby countdown ends, `startTimerMs == 0`):

| Threshold | Loot trigger dispatched | Wave trigger dispatched |
|-----------|------------------------|------------------------|
| 1 200 ticks (60 s) | `TIMER_60` | `TIMER_60` |
| 2 400 ticks (120 s) | `TIMER_120` | `TIMER_120` |
| 6 000 ticks (300 s) | `TIMER_300` | `TIMER_300` |

`TriggerEvaluator.tickTimerCustomForLocation()` was already implemented (per-wave-index
counters under `waveTriggerWaveCounters` with `"tc_N"` keys) but was never called.
Now called every tick from `LocationSession.tick()`.

`LootSpawn.Trigger.PLAYER_DEATH` was only dispatched on PvP death.
Added dispatch in `WaveManager.onPvePlayerDeath()` before `playerData.remove()`.

---

### Fixed — Trigger-wave mob off-by-one in `WaveManager.onMobKilled`

`onMobKilled` resolved the trigger wave config with `waveIndex > 0` and `get(waveIndex - 1)`,
which skipped index 0 (the most common case) and applied a 1-based offset to a 0-based list.
Corrected to `waveIndex >= 0 && waveIndex < waves.size()` and `get(waveIndex)`.
The erroneous `fireWaveTriggerForLocation` call (which starts a new location, not spawns mobs)
was replaced with a debug log to avoid an infinite mob-spawn loop.

---

### Removed — `docs/archive/`

Deleted 18 stale files (markdown summaries and prototype Java files) left by previous
agent sessions. Translation audit confirmed: all 151 `wavedefense.auto.*` keys in source
code have matching entries in every language file; 6 orphaned keys remain in the JSON files
(harmless).

---

## [0.2.45] - 2026-05-23

### Added — UI design system & screen theming

Extended `GuiTheme.java` with 7 new color constants (`STATUS_PVP`, `STATUS_PVE`,
`STATUS_WAITING`, `STATUS_ACTIVE`, `DANGER`, `WARN`, `SECTION_HEADER`) and 5 new
rendering helpers (`badge`, `sectionDivider`, `progressBar`, `renderSectionFrame`,
`statusLine`). `outline()` promoted from `private` to `public static`.

Applied the design system consistently across 9 screens / subsystems:

- **`WaveActionsScreen`** — `GuiTheme.renderBackground` / `renderHeader` / `renderContentFrame`; separator line between action buttons and exit; `GuiTheme.DANGER` / `STATUS_ACTIVE` / `TEXT_MUTED` replace hardcoded colors.
- **`StatsScreen`** — `GuiTheme.renderBackground` + `renderHeader`; `GuiTheme.card()` per player row; `ACCENT_ALT` for points, `TEXT_MUTED` for muted info.
- **`HudOverlay`** — `GuiTheme.panel()` for outer HUD block; `GuiTheme.progressBar()` for wave progress; mob-count color shifts from `ACCENT` → `WARN` → `DANGER` as mobs decrease.
- **`PlayerMenuScreen`** — removed duplicate `drawCenteredString` header call (title was drawn twice).
- **`ItemSelectionScreen`** — `GuiTheme.renderBackground` / `renderHeader`; `GuiTheme.scrollBar()` replaces manual fill; `PANEL_DARK` / `BORDER` for slot backgrounds; `GuiTheme.panel()` for preview panel; removed `§f`/`§7` format codes.
- **`ShopEditorScreen`** — `BORDER` / `PANEL_DARK` for item slots; `ACCENT` underline under active tab.
- **`WaveConfigScreen`** — `GuiTheme.card()` rows with left accent stripe; `TEXT_MUTED` for hints; `DANGER` tint for pending-delete row; dialog backgrounds use `PANEL_DARK` + `outline()` with `WARN`/`DANGER`.
- **`LocationEditorScreen` (Special tab)** — `GuiTheme.sectionDivider()` visual dividers between Boundary, Auto-Zone, and Portal sections.
- **Lang files (all 8)** — 3 new keys: `wavedefense.section.boundary`, `wavedefense.section.zone`, `wavedefense.section.portal`.

---

### Fixed — Loot trigger system: 7 of 20 triggers never fired

`fireLootTrigger` was called only for `PLAYER_JOIN` (in `SessionManager`) and
`KILL_STREAK_3` (in `PvpRoundManager`). The remaining triggers existed in the enum
and UI but had no dispatch points in runtime code.

**Dispatch points added:**

| Trigger | Where dispatched |
|---------|-----------------|
| `LOCATION_START` | `LocationSession.startNextWave()` when `currentWave == 1` |
| `WAVE_START` | `LocationSession.startNextWave()` on every successful spawn |
| `WAVE_N` | `LocationSession.startNextWave()` with value-match (`currentWave == N`) |
| `WAVE_END` | `LocationSession.onWaveCompleted()` |
| `LOCATION_END` | `SessionManager.triggerVictory()` before victory screen |
| `MOB_KILL` | `WaveManager.onMobKilled()` on every tracked kill |
| `MOBS_KILLED_N` | `WaveManager.onMobKilled()` with value-match (`mobsKilled == N`) |
| `HALF_MOBS_DEAD` | `WaveManager.onMobKilled()` when `halfMobsTriggered` set |

`fireLootTrigger` received a new overload `(Location, ServerLevel, Trigger, int requiredValue)` —
pass `requiredValue = -1` to skip the value check (backward-compatible), or `>= 0` to only
activate `LootSpawn` entries whose stored trigger value equals `requiredValue` exactly.
`fireLootTriggerByNameWithValue` wraps this for callers that have only a location name string.

---

### Fixed — Minor reliability issues

**`MobSpawnManager.forceSetItemSlot`** — the NBT-fallback block (for GeckoLib/tacz mobs)
was `catch (Exception ignored)`. Changed to `LOGGER.warn(slot, mob, message)` so admins
can diagnose why custom mob equipment isn't appearing.

**`Location.addWave`** — no capacity guard existed, unlike `addMob` and `addMobSpawn`
which already checked `MAX_MOB_TYPES` and `MAX_MOB_SPAWNS` respectively. Added
`if (waves.size() < WaveDefenseConfig.MAX_WAVES.get())` to match the pattern.

**`InfoPanelManager.updateOrCreateTextDisplayInSession`** — `world.addFreshEntity(td)`
was called without verifying the chunk was loaded. Added `if (!world.isLoaded(blockPos)) return`
guard so TextDisplay panels are not spawned into unloaded chunks.

---

## [0.2.44] - 2026-05-18

### Added — In-game configuration screen

Registered `WaveDefenseConfigScreen` as the mod's config screen via Forge's
`ConfigScreenHandler.ConfigScreenFactory`. The screen opens from the Mods menu
(select Wave Defense → Config button) and covers every setting in
`config/wavedefense-common.toml` without requiring players to edit the file manually.

Screen layout — five tabs:
- **General**: HUD overlay, default wave time, UI tooltips, lobby timer, location game mode (Survival / Adventure).
- **PvP**: hide enemy nametags, default round count, default buy time.
- **Mobs & Shop**: mob equipment toggle, armor drop chance, shop categories, shop hotkey (B).
- **Limits**: max mob types per wave, max waves, mob spawn points, PvP spawn points, shop items, loot spawns.
- **Debug**: admin debug messages, server log verbosity.

Values are applied and written to disk immediately on Save. Cancel discards all
unsaved changes. An info line on the Limits tab notes the 1–9999 valid range.

---

### Fixed — Critical button-layout bugs introduced in 0.2.43 seventh audit

Four regressions found by post-audit error analysis and corrected:

**`CompletionRewardScreen`: Delete button overlapped Edit button in pending-confirmation state.**
When `isPendingDelReward = true` the button width expanded to 50 px, but the position
formula `cx + 156 − delRewardW` placed the left edge at `cx + 106` — directly on top of
the ✎ Edit button at `cx + 105`. Fixed by using a fixed left anchor `cx + 131`
(immediately right of the edit button) so the expanded button grows rightward only.

**`ShopEditorScreen`: Cancel button overlapped Exp (export) button.**
Cancel spanned `cx − 5` to `cx + 105`; Exp started at `cx + 64` — a 41 px overlap.
Fixed by moving Exp to `cx + 110` and Imp to `cx + 156`, leaving a clean 5 px gap
after Cancel.

**`LootSpawnEditorScreen`: per-slot "←" button threw `IndexOutOfBoundsException`.**
`editItems.set(si, held.copy())` assumed `editItems` always had ≥ 4 entries, but the
list can be shorter when loading sparse loot data. Fixed with a guard:
`while (editItems.size() <= si) editItems.add(ItemStack.EMPTY)` before every `set()`.

**`WaveConfigScreen`: `pendingDeleteWaveIndex` not reset on scroll.**
After clicking ✕ on wave #3 (first-click pending state), scrolling moved the list but
kept wave #3 highlighted. A subsequent click could delete the wrong wave. Fixed by
clearing `pendingDeleteWaveIndex = -1` in both ▲/▼ button handlers and in an overridden
`mouseScrolled()`.

---

### Fixed — Seventh audit: § character corruption (all 8 language files + Java GUI)

`§` (U+00A7) was corrupted in two distinct ways across the codebase:

**Cause A — `?` substitution in lang file values** (saved in wrong encoding): corrected
in the shared block (lines 736–807 in every lang file) across all eight language files:
`en_us`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pl_pl`, `pt_br`, `zh_cn`.
Patterns fixed: `?6Wave %s`, `?d?l`, `?c?l PvP`, `?a▶`, `?7`, `?e`, `?8` etc.

**Cause B — `\\u00A7x` literal backslash-unicode in auto-keys**: six auto-generated keys
per language file (e.g. `wavedefense.auto.u00a76_u00a7l_*`) stored `"\\u00A76\\u00A7l"`
as their value. After JSON parsing this became the 12-character string `§6§l`
which Minecraft rendered literally (visible as `§6§lShop 1111` in image30).
Fixed to actual `§6§l` characters. Also fixed `\\u25B2` → `▲` and `\\u25BC` → `▼` in the
same auto-key batch.

**Java GUI files**: three screens had `?` where `§` was expected:
- `LocationEditorScreen.java` line 130: `"?a?l? "` / `"?7? "` → `"§a§l▶ "` / `"§7▶ "`
- `PvpLocationEditorScreen.java` line 94: same pattern
- `LootSpawnEditorScreen.java` line 383: `"?7"`, `"?8"`, garbled hint text → corrected Ukrainian

---

### Fixed — Seventh audit: layout and UX bugs (from tester's 43-screenshot report)

**`ImportExportScreen` (Bug 2.1):** "Refresh list" button used a `y − 14` hack that placed it
on the same row as the "— IMPORT —" header label, causing visual overlap. Moved to a
dedicated row with `y += 22` spacing.

**`CompletionRewardScreen` (Bug 2.2):** item slot frames started at `cx − 161` with zero
left padding, clipping through the card border. Shifted all item columns by +4 px
(`cx − 160 + j * 22` → `cx − 156 + j * 22`) in both `buildRowWidgets` and `renderContentExtra`.

**`CompletionRewardScreen` (Bug 2.3):** `renderTooltip` was called inside the item render loop
while ScissorHelper was active, producing a black rectangle instead of the tooltip.
Fixed by collecting `tooltipItem` in the loop and calling `renderTooltip` once after the
loop, after `ScissorHelper.disable()`.

**`LocationEditorScreen` (Bug 2.4 / 2.5 / N17):** scrollable content in Basic and Special tabs
overlapped the Save / Back / Close footer buttons. `CLIP_BOT` adjusted to
`CONTENT_BOT − 4` so the scissor region always ends above the footer.

**`LocationEditorScreen` (N3):** mob spawn point ▲/▼ scroll buttons were registered via
`addStatic()`, which placed them outside the scissor clip and left the list items at fixed
Y positions regardless of `mobSpawnScrollOffset`. Changed to `addRenderableWidget` so
buttons scroll with content and the ▼ position tracks `listY + maxVisible * 22`.

**`LocationEditorScreen` (N18):** switching to PvP mode from the location editor had no way
back. Added an explicit `"§a← PvE мобів/хвилі"` button that calls
`location.setMode(LocationMode.PVE)` and rebuilds the widget tree.

**`LootSpawnEditorScreen` (N14):** a single global "from hand" button filled only the first
empty slot. Replaced with four per-slot `"←"` buttons rendered below each slot icon,
each bound to its own slot index.

**`MobEffectsEditorScreen` (N6):** effect-picker button width was conditional on scrollbar
presence (`rightW − 16` or `rightW`), causing all buttons to jump in width when scrolling
crossed the visibility threshold. Fixed to always subtract 16 px.

**`WaveTriggerEditorScreen` (N8):** item-type label and hand button heights were 14 px while
surrounding rows used 18–20 px, making the section visually cramped. Standardised to
18 px height with `y += 20 / 22` spacing.

**`ShopEditorScreen` (N9):** no way to close the shop editor without saving. Added a
"Cancel" button (`cx − 5`, width 110) next to the existing "Save & Back" button.
"Save & Back" resized from 200 px → 150 px to make room.

**`WaveConfigScreen` (F1):** wave deletion was instant on a single click. Added two-click
confirmation: first click highlights the row and sets `pendingDeleteWaveIndex`; second
click on the same row executes the delete.

**`CompletionRewardScreen` (F2):** no way to leave the reward list without saving. Added
"Cancel" button that returns to `parent` without calling `saveAndBack()`.

**`CompletionRewardScreen` (F3):** reward deletion was instant. Added two-click confirmation
via `pendingDeleteRewardIndex` field (same pattern as wave deletion).

---

## [0.2.43] - 2026-05-17 (third pass)

### Fixed — third audit (server/client correctness, spawn dimension, null-safety)

**`MobSpawnManager`: mob spawn dimension tied to player's current dimension** — `spawnWave()` used `players.get(0).serverLevel()` to obtain the world for mob spawning. If any player was in the Nether or End at the time, all wave mobs were spawned in that dimension, not in Overworld where the location resides. The UUIDs were tracked in `spawnedMobs` but `world.getEntity(uuid)` in Overworld returned `null`, so mobs were never counted as killed and the wave would eventually stall. Fixed by replacing the player-level lookup with `WaveDefenseMod.getServer().getLevel(Level.OVERWORLD)` and guarding against a `null` return.

**`MobSpawnManager`: mob spawning in unloaded chunks stalled waves** — `trySpawn()` attempted `entityType.create(world)` and `world.addFreshEntity(mob)` without checking whether the target chunk was loaded. In an unloaded chunk the entity creation succeeded but the mob was never ticked, never died, and was never counted — effectively hanging the wave indefinitely. Fixed by adding `if (!world.isLoaded(pos)) return null;` at the top of `trySpawn()` before any entity allocation. A debug-level log line is emitted so admins can detect misconfigured spawn points.

**`WaveDefenseCommand`: NPE on `/wavedefense reload` and `/wavedefense tp` before init** — Both command executors accessed `WaveDefenseMod.locationManager` directly without a null-check. Issuing either command before `ServerStartingEvent` fires (e.g., in a command block that executes on first tick) would throw a `NullPointerException` and print a server-side stacktrace with no feedback to the caller. Fixed by returning early with a `sendFailure("[WaveDefense] Not yet initialized.")` message if `locationManager == null`.

**`WaveDefenseMod`: no explicit save on server shutdown** — Location data is written to disk on every change via `saveToFile()`, making data loss very unlikely. However, a hard crash or OS-level kill can skip the change-triggered write. Added a `ServerStoppingEvent` handler (`onServerStopping`) that calls `locationManager.saveToFile()` on clean shutdown, ensuring the final in-memory state is always flushed before the JVM exits.

#### Server/client separation audit result

All event handlers verified: Dist.CLIENT-only handlers are annotated with `@Mod.EventBusSubscriber(value = Dist.CLIENT)`, all S2C packet handlers use `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)`, all C2S handlers use `ctx.get().enqueueWork(...)`. No dedicated-server crashes or logical errors found.

---

## [0.2.43] - 2026-05-17 (second pass)

### Fixed — second audit (GUI data-loss, runtime, UX, security)

#### Critical — silent data loss in GUI

**`ShopItemEditorScreen`: buy/sell prices lost on category switch** — `buyPriceInput` and `sellPriceInput` had no `setResponder()`. Clicking a category button called `rebuildWidgets()`, destroying both fields and resetting them to the original value from the shop item list. The user's typed price was silently discarded. Fixed by adding class-level `pendingBuyPrice`/`pendingSellPrice` buffers, initializing them once in `init()`, and attaching `setResponder()` to both fields. `save()` now reads from the buffers, which are always current regardless of how many rebuilds occurred.

**`LocationEditorScreen`: all EditBox values lost on scroll** — None of the EditBox fields in this screen (starting points, boundary radius, portal timers, zone settings, info panel offsets — 13 fields total) used `setResponder()`. Scrolling called `rebuildWidgets()`, which re-read from `location` (not from the now-destroyed fields). Any value typed but not saved was silently dropped. Fixed by overriding `rebuildWidgets()` to call `parseAllInputsToLocation()` before `super.rebuildWidgets()`. This flushes all current field values to the model on every rebuild, so the fields are always re-populated correctly. The now-redundant explicit flush in `switchTab()` was removed.

#### High — runtime bugs

**`PortalManager`: penalty mobs remained in the world after portal closed** — `closePortalIfGraceExpired()` called `portalPenaltyMobs.clear()` (cleared the UUID set) but never called `entity.discard()` on the actual mob entities. Portal penalty mobs accumulated indefinitely on the server. Fixed by iterating the UUID set before clearing it and calling `world.getEntity(uuid).discard()` for each live entity. Also added a null guard to `spawnPortalParticles()` (`if (pos == null) return`).

**`SessionManager`: spawned mobs remained in the world when all players left** — When the last player surrendered, `ctx.removeSession()` was called immediately. The `spawnedMobs` UUID set was disposed (in-memory), but the actual mob entities stayed alive in the world. Same issue existed in the `endSession()` path (victory / end-of-session). Fixed by adding a `despawnSessionMobs(locationName)` helper that iterates `session.spawnedMobs`, discards each live entity, and logs the count. Called in both paths before `ctx.removeSession()`.

**`TriggerEvaluator`: `PLAYER_FULL_INVENT` did not check the offhand slot** — `getInventory().getFreeSlot() == -1` only covers the 27 main inventory slots. A player with a full main inventory but empty offhand would still trigger the condition. Fixed by also checking `!p.getInventory().offhand.get(0).isEmpty()` — both conditions must be true simultaneously.

**`BoundaryManager`: `Math.sqrt()` called every tick per player** — The distance check used `Math.sqrt(player.blockPosition().distSqr(center)) > radius`, which is mathematically correct but performs an expensive square-root operation for every player on every tick. With 10+ players this is 200+ `sqrt()` calls per second. Fixed by comparing squared distances directly: `player.blockPosition().distSqr(center) > (double) radius * radius`. Identical result, zero trigonometric cost.

#### Medium — UX and security

**`ClientEventHandler`: admin menu opened for any creative-mode player** — The V-key handler checked `mc.player.isCreative()` to decide whether to open `AdminMenuScreen`. Any player in creative mode (including non-ops on creative servers) saw the admin GUI. Server-side packets were protected by `hasPermissions(2)`, but the client GUI itself opened unconditionally for creative players. Fixed by replacing `isCreative()` with `hasPermissions(2)`.

**`StatsScreen` missing `isPauseScreen()` override** — `StatsScreen extends Screen` and did not override `isPauseScreen()`, so the default `true` was returned. In singleplayer, opening this screen paused the game, freezing wave timers. Fixed by adding `@Override public boolean isPauseScreen() { return false; }`. (All other GUI screens either extend `ScrollableScreen`, which already returns `false`, or had the override already in place.)

**HUD missing wave counter** — The HUD showed points and a "Next wave" timer but no indication of how many waves remain. Players had no way to track progress without opening a menu. Added a `"Wave X / Y"` line rendered in amber (`0xFFE0A020`) at the bottom-right (above the next-wave timer), reading `data.getCurrentWave()` and `currentLoc.getTotalWaves()`. Only shown in PvE mode. New key `wavedefense.hud.wave_counter` added to all 8 language files.

**`LocationManager`: no recovery when data file is corrupt** — If `wavedefense_locations.dat` was malformed, `NbtIo.readCompressed()` threw `IOException`, the catch block logged the error, and the server started with an empty location list (silently losing all data). Fixed in two parts: (1) `saveToFile()` now also writes a `.bak` copy after every successful save; (2) `load()` now attempts to restore from `.bak` on primary file failure, logs a detailed warning, and renames the corrupt file to `.corrupted` if neither source is readable — ensuring the server never silently starts with an empty list due to data corruption.

#### New lang key

`wavedefense.hud.wave_counter` — added to all 8 language files (en/uk/de/fr/es/pl/pt\_br/zh\_cn).

---

## [0.2.43] - 2026-05-17

### Fixed — GUI audit (full pass, all 52 screens)

#### High — broken shop sell path

**`SellItemPacket` ignored `shopPointIndex`** — the packet only carried `locationName` and `itemIndex`. Selling an item from a per-point shop always read from `location.getShopItems()` (global), so the wrong price could be applied or the item not found at all. Fixed: `SellItemPacket` now encodes/decodes a third `shopPointIndex` integer (−1 = global). `PlayerShopScreen.addSellButton()` resolves the point index by reference scan of `location.getShopPoints()` before building the packet. The server handler mirrors `PurchaseItemPacket`: if `shopPointIndex >= 0` it reads from the correct `ShopPoint.getItems()` list.

**`PvpLocationEditorScreen` lost typed values on toggle** — `brBorderDamageAmtInput` and `boundaryDamageInput` were recreated empty every time a toggle rebuilt the widget tree. Fixed by adding `setResponder()` to both EditBoxes so each keystroke immediately writes the value into `location`; `rebuildWidgets()` then correctly re-populates the field from `location` rather than a stale buffer.

#### Medium — UX and display

**`PvpTeamSelectScreen` hid duplicate-named spawn points** — `putIfAbsent(teamName, ...)` silently dropped every spawn point beyond the first when two points shared the same team name. Fixed by rewriting `buildTeamOptions()` to enumerate all `pvpSpawnPoints` in order. Points with a unique team name keep their display name as-is; points whose team name appears more than once get a `" (N)"` suffix. The translatable fallback key `wavedefense.pvp.team_select.team_fallback` is used for blank team names.

**`StatsScreen` showed truncated UUIDs instead of player names** — `entry.getKey().toString().substring(0, 8)` produced strings like `"550e8400"`. Fixed by querying `minecraft.getConnection().getPlayerInfo(uuid)` first; the full UUID string is used only as a last resort when the player info is unavailable.

#### Localization — replaced all hardcoded Ukrainian strings (full audit L1–L6)

**`LocationEditorScreen` boundary consequence labels** (`L1`) — The `String[]` array of four hardcoded Ukrainian consequence display names replaced with `Component.translatable()` via `I18n.get()` at render time. Four new keys: `wavedefense.boundary.consequence.timer_surrender / damage / teleport_back / instant_surrender`.

**`WaveConfigScreen` wave label and dialogs** (`L2`) — Five hardcoded Ukrainian format strings replaced: wave list row (`wavedefense.wave.label`), no-trigger indicator (`wavedefense.wave.no_trigger`), spawn tooltip active/hint (`wavedefense.wave.spawn_tooltip_active / spawn_tooltip_hint`), and wave-count reduction confirmation dialog (`wavedefense.wave.confirm_reduce`). Empty-wave validation warning added (`wavedefense.msg.wave_has_no_mobs`).

**`WaveMobEditScreen` field labels and armor/hand slots** (`L3`) — Ten hardcoded Ukrainian labels replaced: `wavedefense.label.mob_count_wave`, `mob_growth_per_wave`, `mob_spawn_chance`, `mob_points_per_kill`, `armor_slot.helmet / chest / legs / boots`, `mainhand_empty`, `offhand_empty`.

**`WaveMobsEditorScreen` mob info row** (`L4`) — `String.format("§7К-сть: …")` replaced with `Component.translatable("wavedefense.wave.mob_info", count, growth, chance, points)`.

**`LootSpawnEditorScreen` and `ShopPointEditorScreen` count/status labels** (`L5`) — `"X точок"` → `wavedefense.label.loot_spawn_count`; `"порожньо"` → `wavedefense.label.loot_empty`; shop point position status → `wavedefense.label.shop_point_pos_set / shop_point_pos_unset`.

**`RewardsConfigScreen` title and effect preview** (`L6`) — Screen title `Component.literal("Налаштування хвилі " + N)` replaced with `Component.translatable("wavedefense.title.wave_rewards", N)`. Effect preview string in `render()` replaced with `I18n.get("wavedefense.label.wave_effect_preview", effectId, level)`. All 4 wave-reward fields (`pointsReward`, `waveEffect`, `waveEffectAmplifier`, `completionCommand`) confirmed present and correctly persisted in `save()`.

#### New lang keys (all 8 language files — en/uk/de/fr/es/pl/pt\_br/zh\_cn)

35 new translatable keys added in this sprint:
`boundary.consequence.timer_surrender/damage/teleport_back/instant_surrender`,
`wave.label`, `wave.no_trigger`, `wave.spawn_tooltip_active/hint`, `wave.confirm_reduce`, `wave.mob_info`,
`msg.wave_has_no_mobs`,
`label.mob_count_wave/mob_growth_per_wave/mob_spawn_chance/mob_points_per_kill`,
`label.armor_slot.helmet/chest/legs/boots`,
`label.mainhand_empty/offhand_empty`,
`label.global_items_count/shop_points_count/loot_points_count/loot_spawn_count/loot_empty`,
`label.shop_point_pos_set/shop_point_pos_unset`,
`title.wave_rewards`,
`label.wave_effect_preview`.

---

## [0.2.43] - 2026-05-16

### Fixed — PvP system bugs (full audit)

#### Critical

**`PvpRoundState.recordTeamWin()` set `Phase.ENDED` prematurely** — the method wrote `phase = Phase.ENDED` before `endRound()` could decide whether to start a new buy phase or end the match. During that one-tick window the state machine was in `ENDED` with no handler, causing undefined behaviour. Fixed by removing `phase = Phase.ENDED` from `recordTeamWin()`. The only code that now writes `ENDED` is `endPvpMatch()`. `recordTeamWin()` is also null-safe (null team = draw, no entry recorded).

#### High

**Deathmatch: `dmTeamKills` not reset between rounds** — kill counts accumulated across all rounds. In a 3-round match the leading team could trigger `dmKillsToWin` on the very first tick of round 2 using kills carried over from round 1. Fixed by adding `dmTeamKills.clear()` to `PvpRoundState.startActiveRound()`.

**`rebalancePvpTeams()` skipped the BUY phase** — team rebalancing only ran in `WAITING` (pre-match lobby). If a player left during the buy phase between rounds, the teams stayed lopsided for all remaining rounds. Fixed by also allowing rebalance during `BUY`.

**Battle Royale: simultaneous death of two last players froze the match** — `checkBrWinner()` returned `null` when `aliveThisRound` was empty (both players gone at once). The state machine stayed in `ACTIVE` indefinitely. Fixed with three layered guards:
1. Watchdog in `tick()` ACTIVE phase: if BR and `aliveThisRound.isEmpty()` → declare draw, start `ROUND_END_DELAY`.
2. Early-return in `onPlayerKilledPlayer()` BR branch: if `alive == 0` after `recordDeath()` → declare draw immediately.
3. `ROUND_END_DELAY` handler: `pendingWinner == null` (draw) now always calls `endRound()` instead of silently skipping.

**Battle Royale: environment death did not update `aliveThisRound`** — deaths caused by void, fire, lava, or fall only triggered `onPvpPlayerDeath()`, which never called `state.recordDeath()`. The victim stayed in `aliveThisRound`, so `checkBrWinner()` never saw a winner even when only one real player remained. Fixed by calling `state.recordDeath(player, null)` at the start of the BR branch in `onPvpPlayerDeath()`.

#### Medium

**`pvpWinPoints` / `pvpLosePoints` distributed twice per match** — `endRound()` gave these points after every round, and `endPvpMatch()` gave them a second time for the same last round. Players on the winning team of the final round received double points. Fixed by removing the duplicate distribution block from `endPvpMatch()`.

#### Low / Localization

**Kill streak suffix `"ФРАГИ!"` was hardcoded Ukrainian** — replaced with `Component.translatable("wavedefense.msg.pvp_kill_streak", streak)`.

**`BoundaryManager` TIMER_SURRENDER and DAMAGE titles were hardcoded Ukrainian** — four hardcoded title/subtitle strings replaced with translatable keys:
- `wavedefense.msg.boundary_return`
- `wavedefense.msg.boundary_out_of_zone`
- `wavedefense.msg.boundary_timer`
- `wavedefense.msg.boundary_unsafe`
- `wavedefense.msg.boundary_damage`

**New lang keys** (all 8 language files — en/uk/de/fr/es/pl/pt\_br/zh\_cn):
`pvp_br_draw`, `pvp_kill_streak`, `boundary_return`, `boundary_out_of_zone`, `boundary_timer`, `boundary_unsafe`, `boundary_damage`.

---

### Fixed — PvE system bugs (deep audit, second pass)

#### New runtime features (settings existed in UI but were never applied)

**Wave effect (`waveEffect`) now applied to players** — `WaveConfig.waveEffect` and `waveEffectAmplifier` were stored and serialized but never read at runtime. `LocationSession.startNextWave()` now calls `applyWaveEffect()` (applies a 1-hour `MobEffectInstance` to all current players) and `removeWaveEffect()` (strips it from all players) at every wave boundary. New fields: `waveActive` (guards reward/command callbacks), `currentWaveEffectId` (tracks which effect to remove).

**Per-wave `pointsReward` now distributed** — `WaveConfig.pointsReward` was serialized and displayed in the GUI but never awarded. `LocationSession.onWaveCompleted()` now calls `location.addPoints()` for every player and syncs their HUD immediately after each wave ends.

**Per-wave `completionCommand` now executed** — `WaveConfig.completionCommand` supports `%location%`, `%wave%`, and `%players%` placeholders. The command is run via `MinecraftServer.getCommands().performPrefixedCommand()` in `onWaveCompleted()` with a `LOGGER.warn` on failure.

**`firstWaveDelaySec` now respected** — the field existed on `Location` but was never used. `LocationSession.tick()` now reads it when the lobby timer expires: if `firstWaveDelaySec > 0` and it is the first wave, `waveTimerTicks` is set to `firstWaveDelaySec * 20` before `startNextWave()` is called.

#### Lobby timer

**Lobby timer reset on every new player join** — each new player joining an active lobby unconditionally reset `startTimerMs` to `now + lobbyTime`. A busy server could delay the match indefinitely. Fixed: if a session's lobby timer is already running, the timer is left untouched; the new player receives a message with the **remaining** time via the new key `wavedefense.msg.player_joined_lobby_countdown`.

#### Mob tracking

**`waveStartMobCount` inflation by portal/trigger mobs** — `MobSpawnManager.spawnWave()` set `waveStartMobCount = spawnedMobs.size()` after the spawn loop. If portal or trigger-wave mobs were already present in `spawnedMobs`, they inflated the count, making `HALF_MOBS_DEAD` fire too late. Fixed by capturing `preSpawnSize = spawnedMobs.size()` before the loop and setting `waveStartMobCount = Math.max(0, spawnedMobs.size() - preSpawnSize)`.

#### New lang keys (all 8 language files)

`player_joined_lobby_countdown`.

---

### Fixed — PvE system bugs (deep audit, first pass)

**`WaveConfigScreen` did not persist `totalWaves`** — `applyWaveCount()` added/removed entries in `location.getWaves()` but never called `location.setTotalWaves()`. After saving, `totalWaves` stayed at the default (10), so `LocationSession.tick()` could never detect "all waves done" unless exactly 10 waves were configured. Fixed in `applyWaveCount()`, `confirmWaveCountChange()`, `deleteWave()`, and `saveChanges()`.

**`victoryLingerTicks` never counted down** — `SessionManager.triggerVictory()` set `sess.victoryLingerTicks = lingerTicks` with a comment "endSession will fire from tick()", but the countdown code was missing. Players were stuck on the victory screen permanently. Fixed by adding a countdown block at the very top of `LocationSession.tick()` that decrements the counter and calls `wm.endSessionForLocation()` when it reaches zero.

**Double `currentWave++` when wave spawn failed** — `tick()` incremented `currentWave` before calling `startNextWave()`, which incremented it again on spawn failure. Two waves were skipped per failed spawn. Fixed by removing the redundant increment from the failure path in `startNextWave()` and adding a `LOGGER.warn`.

**Dead mobs blocked wave completion** — mobs killed by void, fire, `/kill`, or fall damage bypassed `onMobKilled()` and stayed in `spawnedMobs` forever, keeping `spawnedMobs.isEmpty()` permanently false. Fixed by a periodic cleanup in `tick()` every 40 ticks: iterates `spawnedMobs`, removes entries whose entity is null or not alive, and adds them to `mobsKilled`.

**`PlayerBackup.restore()` did not restore armor, offhand, or potion effects** — `backup()` saved all three but `restore()` only applied `inventory[]`, `health`, `xp`, and `pos`. Players exited with empty armor slots and no effects. Fixed: `restore()` now copies each armor slot, the offhand slot, calls `removeAllEffects()`, and re-applies each saved `MobEffectInstance` by registry lookup.

**`MobSpawnManager.trySpawn()` silently swallowed exceptions** — any spawn error returned `null` with no log entry; the wave started empty and ended immediately. Fixed: the catch block now calls `LOGGER.warn` with entity ID, position, location name, and the exception message.

**`InfoPanelManager` showed `waves.size()` instead of `getTotalWaves()`** — when `totalWaves = 20` but only 3 wave configs existed, the panel displayed "Wave 5 / 3". Fixed by replacing the `stream().filter().count()` expression with `loc.getTotalWaves()`.

**`WaveConfigScreen` did not warn about empty waves** — a wave with no mobs spawned nothing and ended instantly. `saveChanges()` now iterates waves and shows an action-bar warning (key `wavedefense.msg.wave_has_no_mobs`) for the first non-trigger wave with an empty mob list.

**New lang keys (all 8 language files):** `wave_has_no_mobs`.

---

## [0.2.43] - 2026-04-25

### Architecture — WaveManager tick() extraction (Phase 6.1)

`WaveManager.tick()` was 155 lines. The four timer loops inside it have been extracted into private methods, reducing `tick()` to ~55 lines of clearly labelled delegation calls.

| New method | Responsibility |
|-----------|----------------|
| `tickStartTimers()` | Lobby countdown → spawn first wave (respects `firstDelay`) |
| `tickWaveTimers()` | Between-wave countdown → spawn next wave |
| `tickPerLocationTimers()` | 60 / 120 / 300 sec recurring triggers + `TIMER_CUSTOM` |
| `tickVictoryLingerTimers()` | Victory linger countdown → `endSession` on expiry |

`tick()` now reads as a flat list of delegation calls, each annotated with the subsystem it touches. No logic changes.

---

### Architecture — SessionManager properly wired (Phase 6.2)

`SessionManager` was created in Phase 1+2 as a placeholder but was **never called** — `WaveManager` retained its own complete implementations of all four lifecycle methods (`addPlayerToLocation`, `surrenderPlayer`, `triggerVictory`, `endSessionForLocation`), totalling ~324 lines of duplicated logic.

**Changes:**

- `SessionManager.java` rewritten with the complete, production-ready implementations migrated from `WaveManager`. All missing logic has been added: enforceGameMode on join, `spawnedMobs` check for session condition, title/subtitle packet for victory screen, oneTimeOnly wave reset, mob-kill stat flush, portal return position, completion-rewards-by-points, offline player handling, `clearTeammatesForAll`.
- `WaveManager.addPlayerToLocation`, `surrenderPlayer`, `triggerVictory`, `endSessionForLocation` → each reduced to a **1-line delegate** (`sessionMgr.*(…, this)`).
- Added package-private `WaveManager.invalidatePlayersCache()` so `SessionManager` can reset the per-tick players cache on join/leave.
- **Stale SessionManager bugs fixed** (were never live, but now fixed as the implementations go active):
  - Old `addPlayer`: missing `enforceGameMode`, `syncLocationDataToPlayer`, `syncTeammates`, incorrect first-player condition (missing `spawnedMobs.isEmpty()` check), hardcoded Ukrainian broadcast strings.
  - Old `surrender`: missing `removeWaitEffects`, `pvpMgr.getPvpPendingRespawn().remove`, `pvpMgr.onPlayerLeave/rebalancePvpTeams`, `clearTeammatesForPlayer`; incorrectly applied re-entry cooldown on surrender (belongs in `endSession`).
  - Old `triggerVictory`: missing completion-points reward, missing victory title packet, hardcoded Ukrainian broadcast string.
  - Old `endSession`: missing `isVictory` flag (always used victory exit), missing oneTimeOnly reset, mob kill stats, portal return pos, completion rewards, `clearTeammatesForAll`, incorrect ordering of `removeInfoPanelEntities`/`removeSession`.

**Net result:** WaveManager reduced by ~310 lines; all session lifecycle logic now lives exclusively in `SessionManager`.

---

### Architecture — MobSpawnManager wired + onWaveComplete extraction (Phase 6.3 + 6.4)

#### Phase 6.3 — MobSpawnManager properly wired

`MobSpawnManager` was created in Phase 1+2 with a complete `spawnWave()` implementation (per-mob spawn radius, proper fallback to `playerSpawn`, `trySpawn` helper, `waveStartMobCount` tracking) but was never called — `WaveManager.spawnWaveForLocation` retained its own inline ~50-line mob spawn loop.

**Changes:**
- `WaveManager.spawnWaveForLocation` mob spawn loop (50 lines) → 1-line delegate: `mobSpawnMgr.spawnWave(locationName, waveConfig, waveNumber, this)`.
- `WaveManager.applyMobEquipment` (~48 lines) → 1-line delegate to `mobSpawnMgr.applyMobEquipment`. Called by `PortalManager` and `TriggerEvaluator` via `wm.applyMobEquipment` — signature preserved.
- `WaveManager.forceSetItemSlot` (~44 lines) → removed (dead after delegation; `MobSpawnManager` has the complete NBT-fallback implementation, which applies `forceSetItemSlot` to ALL slots, vs. WaveManager's version that only used it for MAINHAND/OFFHAND).
- `WaveManager.getRandomSpawnPoint` → 1-line delegate to `mobSpawnMgr.getRandomSpawnPoint` (bug fix: MobSpawnManager version falls back to `playerSpawn` when no spawn points configured; WaveManager version returned `null`).
- 7 now-unused imports removed from WaveManager (`EntityType`, `Display`, `MobSpawnType`, `NearestAttackableTargetGoal`, `Player`, `ResourceLocation`, `PacketHandler`).

**Functional improvements vs. old inline loop:**
- Per-mob `spawnRadius` respected (MobSpawnManager uses `waveMob.getSpawnRadius()` with `location.getMobSpawnRadius()` fallback; old loop used hardcoded ±3 block scatter).
- Empty mob spawn fallback: `getRandomSpawnPoint` now falls back to `playerSpawn` instead of returning `null` (mobs no longer silently fail to spawn when location has no dedicated spawn points).
- `sess.mobsKilled = 0` reset on wave start (was missing from old loop).

#### Phase 6.4 — onWaveComplete reward extraction

`WaveManager.onWaveComplete` was 123 lines. The 46-line wave reward block (find non-trigger wave config index → award points to all players → execute wave completion command) was extracted into a private method:

```java
private void applyWaveReward(String locationName, int completedWave, Location location)
```

`onWaveComplete` is now ~75 lines. The reward logic is unchanged; the command string now uses a fluent `.replace()` chain for readability.

**Net result (all Phase 6 changes):** WaveManager reduced from **1 657 → 1 190 lines** (−467 lines, −28%).

---

## [0.2.42] - 2026-04-21

### Architecture — Data layer refactoring (Phase 3)

Extracted all NBT serialization logic out of `Location.java` into two new dedicated classes, reducing `Location.java` from **710 → ~450 lines**.

**`data/NbtHelper.java`** (NEW, ~80 lines) — pure utility, no instantiation:
- Typed getters with defaults: `getInt`, `getFloat`, `getLong`, `getDouble`, `getBool`, `getString`
- `BlockPos` via long encoding: `savePosLong(@Nullable BlockPos)` / `loadPosLong` → `@Nullable BlockPos` (avoids redundant compound tag nesting)
- Enum: `saveEnum<E>` / `loadEnum<E>(tag, key, Class<E>, E def)` with `IllegalArgumentException` fallback
- Lists: `saveList<T>(tag, key, List<T>, Function<T,CompoundTag>)` / `loadList<T>` with null-skip

**`data/LocationSerializer.java`** (NEW, ~210 lines):
- `public static CompoundTag save(Location loc)` — full NBT serialization using NbtHelper, replaces ~120 lines of boilerplate
- `public static Location load(CompoundTag tag)` — full deserialization, replaces ~145 lines of boilerplate
- Preserves backward-compatible `MobSpawnPoint` loading loop (old format detection) exactly as before
- Accesses Location fields directly via package-private visibility (same package)

**`data/Location.java`** (MODIFIED):
- All instance fields made package-private (removed `private` keyword) for `LocationSerializer` access
- `save()` body replaced with: `return LocationSerializer.save(this);`
- `load()` body replaced with: `return LocationSerializer.load(tag);`
- Removed unused imports: `ListTag`, `MobSpawnPoint` (no longer used directly)

---

### Architecture — GUI base class `ListEditorScreen<T>` (Phase 4)

New abstract class **`gui/ListEditorScreen.java`** (~98 lines) extends `ScrollableScreen`. It eliminates boilerplate shared across all list-based CRUD screens:

Abstract methods for subclasses: `getItems()`, `getRowHeight()`, `getStartY()`, `buildRowWidgets(cx, y, item, index)`
Optional override: `renderRow(...)` — default is no-op (most screens use widget-only rendering)
Provided automatically: `getListSize()`, `getItemsPerPage()`, `buildVisibleRows()`, `renderContentExtra()`

Migrated screens (before → after):

| Screen | Before | After | Saved |
|--------|--------|-------|-------|
| `WaveMobsEditorScreen` | 208 | ~168 | 40 |
| `AdminMenuScreen` | 229 | ~176 | 53 |
| `PlayerMenuScreen` | 111 | ~88 | 23 |
| `CompletionRewardScreen` | 332 | ~280 | 52 |

`AdminMenuScreen`: `panelW` instance field computed in `init()` before `buildVisibleRows()` — correct initialization order.
`PlayerMenuScreen`: `getRowHeight()` is dynamic (`height < 200 ? 20 : 25`) — evaluated at render time, not at construction.
`CompletionRewardScreen`: edit mode fully preserved — `render()` overrides to `renderEditMode()` when `editingItem=true`, `init()` returns early after `initEditMode()`.

---

### Architecture — `CoordinateInputField` component (Phase 4.1)

New compound widget **`gui/CoordinateInputField.java`** (~170 lines) bundles 3 inactive label buttons ("§7X:", "§7Y:", "§7Z:") and 3 `EditBox` inputs into a single reusable object.

**Constructor:** `(Font, startX, y, labelW, fieldW, height, stride)` where `stride` = pixel distance from one label-input pair start to the next (`labelW + fieldW + gap`). Convenience overload without stride uses zero gap.

**Public API:**
- `setValue(@Nullable BlockPos)` — fills or clears all three fields atomically
- `getValue()` → `@Nullable BlockPos` — parses with try/catch; `null` if all blank or any invalid
- `getValueOrDefault(BlockPos fallback)` — safe version
- `isEmpty()` — true only when all three fields are blank
- `setFromPlayer(@Nullable Player)` — fills from `player.blockPosition()`
- `addToScreen(Consumer<AbstractWidget>)` — registers all 6 inner widgets (labels + inputs)
- `addStaticToScreen(Consumer<AbstractWidget>)` — delegates to `addToScreen` (for static zones)
- `getEndX()` — right edge of the Z-input (`startX + stride*2 + labelW + fieldW`); use as `getEndX() + gap` to anchor a "📌" button

Migrated screens:

| Screen | Lines replaced | Key change |
|--------|---------------|------------|
| `WaveSpawnEditorScreen` | 22 → 4 | `save()` 12 lines → 1 line |
| `LocationEditorScreen` | 15 → 5 | `applySpawnCoords()` simplified |
| `PvpLocationEditorScreen` | 20 → 6 | Removed dead `sx/sy/sz` string variables |
| `ShopPointEditorScreen` | 10 → 3 | **Bug fix**: `save()` now calls `applyPosCoords()` — coordinates applied even without clicking "✓" |

Not migrated: `LootSpawnEditorScreen` — X label uses "§7📍 X:" (28 px) vs Y/Z labels (14 px); non-uniform `labelW` not supported by `CoordinateInputField`.

---

### i18n — Hardcoded Ukrainian strings replaced (Phase 5.2)

Full audit of all ~50 GUI screens. All player-facing and common admin screens now use `Component.translatable()` instead of `Component.literal("Ukrainian text")`. **17 new translation keys** added across all 8 language files (`en_us`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pl_pl`, `pt_br`, `zh_cn`).

**Player-facing screens fixed:**

| Screen | Fixed |
|--------|-------|
| `StatsScreen` | Title, Close button, all `render()` strings (`"Немає даних"`, `"Хвиль пройдено"`, `"Мобів вбито"`, per-player stats labels) |
| `PlayerShopScreen` | Constructor title `"Магазин: %s"`, Close button `"✕ Закрити"` |

**Admin screens fixed:**

| Screen | Fixed |
|--------|-------|
| `WaveSpawnEditorScreen` | Title `"📍 Спавн Хвилі N"` (with `%d`), Cancel button, render title → `this.title` |
| `StartingItemsScreen` | Title `"Стартове спорядження"`, Done button `"Готово"` |
| `ItemSelectionScreen` | Title `"Вибір предмета"`, `EditBox` hint `"Пошук..."`, Close button, render title → `this.title` |
| `MobSelectionScreen` | Both constructors title `"Вибір моба"`, `EditBox` hint, Back button `"← Назад"` |
| `MobTypeSelectionScreen` | Title `"Вибір типу моба"`, Select button `"Вибрати"`, Cancel button, `renderHeader()` mob count label |

**New translation keys:**

```
wavedefense.title.stats               wavedefense.title.starting_items
wavedefense.title.item_selection      wavedefense.title.mob_selection
wavedefense.title.mob_type_selection  wavedefense.title.wave_spawn
wavedefense.label.search              wavedefense.label.shop_title
wavedefense.button.select
wavedefense.stats.no_data             wavedefense.stats.waves_completed
wavedefense.stats.mobs_killed         wavedefense.stats.player
wavedefense.stats.player_mobs         wavedefense.stats.player_points
wavedefense.stats.mobs_available
```

Remaining Cyrillic `Component.literal()` calls are confined to inactive label buttons (`.active = false`) inside deep admin configuration screens (`MobEffectsEditorScreen`, `WaveTriggerEditorScreen`, `WaveConfigScreen`, etc.) — accessible only by server operators.

---

---

### Architecture — GUI structural cleanup (Phase 5.3 + 5.4)

#### LocationEditorScreen (Phase 5.3) — 1 347 → 1 244 lines

**Bug fixes — data loss on tab switch / rebuild:**

Previously, `flushCurrentTabInputs()` and `saveChanges()` were missing flush logic for several fields, causing their values to be silently discarded when the user switched tabs or the widget tree was rebuilt. Fixed by introducing a single unified private method `parseAllInputsToLocation()` that covers **all 18 EditBox / coordinate fields**:

| Previously missing from flush | Previously missing from save |
|-------------------------------|------------------------------|
| `particleCountInput` | `infoPanelOffsetYInput` |
| `particleSpeedInput` | `mobPanelOffsetYInput` |
| `particleIntervalInput` | `infoPanelTextScaleInput` |
| `infoPanelOffsetYInput` | *(also missed by flush)* |
| `mobPanelOffsetYInput` | |
| `infoPanelTextScaleInput` | |

**Structural cleanup:**

- `flushCurrentTabInputs()` → 1-line delegate to `parseAllInputsToLocation()`
- `saveChanges()` parsing block (60+ lines) → 1-line delegate
- `initSpecialTab()` head flush (35 lines of duplicated parsing) → 1-line delegate
- `initSpecialTab()` body (720 lines) split into **8 private section methods**, each returning updated `y`:

| New method | Content |
|-----------|---------|
| `initGameModeSection(lx, panelW, y)` | Enforce GameMode toggle |
| `initVictorySection(lx, panelW, y)` | Victory screen + linger timer |
| `initExitPointsSection(lx, panelW, y)` | Victory / surrender exit positions |
| `initParticlesSection(lx, panelW, y)` | Zone particle presets + 3 inputs |
| `initBoundarySection(lx, panelW, y)` | Boundary radius, consequence, optional timer/damage |
| `initZoneSection(lx, panelW, y)` | Auto-activation zone center, entry, 3 inputs |
| `initPortalSection(lx, panelW, y)` | Portal toggle, penalty wave, 3 inputs |
| `initInfoPanelsSection(lx, panelW, y)` | Spawn + mob info panels, 3 shared inputs |

`initSpecialTab()` is now a ~30-line orchestrator.

#### PvpLocationEditorScreen (Phase 5.4) — 687 → 676 lines

- Dead field `brBorderParticleCountInput` (declared but never initialized or read) removed
- `initModeAndRulesTab()` (258 lines) split into **4 private section methods**:

| New method | Content |
|-----------|---------|
| `initCommonRulesSection(cx, y)` | Min players, FF, gamemode, autobalance, points |
| `initStandardSection(cx, y)` | Rounds, buy time, delays, round points |
| `initDeathmatchSection(cx, y)` | DM kills-to-win, buy time |
| `initBattleRoyaleSection(cx, y)` | BR border radius, shrink interval, particles, damage |

`initModeAndRulesTab()` is now a ~35-line orchestrator.

---

## [0.2.41] - 2026-04-20

### Architecture — WaveManager refactoring (Phase 1 + 2)

`WaveManager.java` has been decomposed from a single 3 748-line file into 11 focused sub-managers. All per-location runtime state is now held in a single `LocationSession` value object instead of 30+ flat `Map<String, T>` fields in `WaveContext`. Session teardown calls `LocationSession.dispose()`, which clears every collection atomically — no orphaned entries, no memory leaks.

**New files added to `wave/`:**

| File | Lines | Responsibility |
|------|-------|----------------|
| `LocationSession.java` | 144 | Value object for all per-location state |
| `PortalManager.java` | ~390 | Portal lifecycle, penalty waves, particles |
| `ZoneActivationManager.java` | ~250 | Zone countdown, player collection, particles |
| `TriggerEvaluator.java` | ~585 | Event trigger evaluation, cooldowns, firing |
| `InfoPanelManager.java` | ~405 | TextDisplay entity create / update / remove |
| `PvpRoundManager.java` | ~720 | PvP state machine (WAITING→BUY→COUNTDOWN→ACTIVE→ENDED) |
| `SessionManager.java` | ~230 | Player join / surrender / victory / session end |
| `MobSpawnManager.java` | ~225 | Mob spawn, equipment, potion effects |

**`WaveContext.java`** reduced from 157 → 115 lines: only per-player maps remain (`playerData`, `playerBackups`, `pendingDeathRestores`, `reEntryCooldowns`, `leaveCountdownTicks`) plus the `sessions: Map<String, LocationSession>` registry.

**`WaveManager.java`** reduced from 3 748 → ~1 660 lines. Now acts as a thin orchestrator: delegates all subsystem work to the managers above, exposes a uniform public API (`addPlayerToLocation`, `tick`, `onMobKilled`, `surrenderPlayer`, `syncPlayerData`, …).

### Fixed — WAVE_COMPLETE trigger cooldown ignored (critical)

`WaveManager.onWaveComplete` built the trigger cooldown key as `locationName + "_w" + waveIndex`, but `TriggerEvaluator` reads/writes the key as `LocationSession.triggerKey(waveIndex)` → `"w" + waveIndex` (per-session, no location prefix). The two keys never matched, so every `WAVE_COMPLETE`-triggered wave fired on every wave completion regardless of the configured cooldown or `oneTimeOnly` flag.

```java
// Before (wrong — key never matched session storage):
String coolKey = locationName + "_w" + wi;

// After (correct):
String coolKey = LocationSession.triggerKey(wi);  // "w" + wi
```

### Fixed — PvP teammates HUD not updating on join

`WaveManager.addPlayerToPvpLocation` delegated to `pvpMgr` but never called `syncTeammates()`. The PvE join path (`addPlayerToLocation`, line 171) has always called it. PvP players now see the full team list immediately on join.

```java
public void addPlayerToPvpLocation(ServerPlayer player, Location location, int spawnIndex) {
    pvpMgr.addPlayerToPvpLocation(this, player, location, spawnIndex);
    syncTeammates(location.getName());  // ← added
}
```

### Fixed — Duplicate team buttons in PvpTeamSelectScreen

`renderTeamButtons` iterated over all `PvpSpawnPoint` objects, creating one button per spawn point rather than per unique team name. A team with two spawn points produced two identical buttons; clicking either sent the spawn point index, but the server's auto-balance could silently override it.

Fixed with `LinkedHashMap<String, Integer> teamToFirstIndex`: one button per unique team name, mapped to its first spawn index.

### Added — Server-side nameplate hiding via Minecraft Scoreboard (PvP)

Client-side `RenderNameTagEvent` suppression was already implemented. A resource pack or modified client could bypass it. `PvpRoundManager` now also manages Minecraft Scoreboard teams:

- **On join** (`addPlayerToPvpLocation`): player is added to a Scoreboard team named `wd_<location>_<teamName>` with `Team.Visibility.HIDE_FOR_OTHER_TEAMS`.
- **On leave** (`onPlayerLeave`, `endPvpMatch`): player is removed from their Scoreboard team.
- **On session end** (`endPvpMatch`, `clearLocation`): all `wd_<location>_*` Scoreboard teams are removed to keep the Scoreboard clean.

Three helper methods added to `PvpRoundManager`: `assignScoreboardTeam`, `removeFromScoreboardTeam`, `cleanupScoreboardTeams`.

### Fixed — Missing public utility methods in WaveManager

Sub-managers (`ZoneActivationManager`, `TriggerEvaluator`) called `wm.debugAdmin(...)`, `wm.debugLog(...)`, and `wm.broadcastToNearby(...)` which had been removed during the refactoring. Three public methods restored:

- **`debugAdmin(String)`** — sends a prefixed debug message to all online operators (only when `DEBUG_LOGGING_ENABLED = true`).
- **`debugLog(String)`** — writes to the server log when `DEBUG_LOGGING_ENABLED = true`.
- **`broadcastToNearby(BlockPos, Location, String)`** — sends a message to all players within `location.getAutoActivateRadius()` blocks of a position; falls back to `broadcastToLocation` if the radius is 0.

---

## [0.2.40] - 2026-04-18

### Added — Full i18n / Localization overhaul
The mod now fully integrates with Minecraft's translation system. Players with any interface language see all text in their own language — no hardcoded strings remain in player-facing output.

- **~53 Java files fixed** — replaced `Component.literal("text")` with `Component.translatable("key")` and `I18n.get()` throughout all GUI screens, game logic, network packets, and event handlers
- **224 translation keys** per language file, covering all UI screens, HUD strings, chat messages, tooltips, and admin menus
- **Added ~80 new translation keys** to `en_us.json` and `uk_ua.json`: PvP HUD strings, lobby/portal/boundary messages, PlayerSettings, HudEdit, HudLayout presets, AdminMenu, server-side game messages, wave/round/match events, completion rewards
- **6 new language files** (complete translation of all 224 keys):
  - 🇩🇪 `de_de.json` — Deutsch
  - 🇫🇷 `fr_fr.json` — Français
  - 🇪🇸 `es_es.json` — Español
  - 🇵🇱 `pl_pl.json` — Polski
  - 🇧🇷 `pt_br.json` — Português (Brasil)
  - 🇨🇳 `zh_cn.json` — 简体中文
- **`WaveManager.java`** — added `broadcastToLocation(String, Component)` overload; changed `endSessionForLocation` to accept a `Component` directly; all 30+ broadcast calls converted to `Component.translatable()`
- **`HudLayout.Preset`** — enum now stores a translation key instead of a raw display string; preset buttons rendered via `Component.translatable()`
- **`WaveActionsScreen`** — tooltip system refactored from text-matching to `Map<Button, String>` (works correctly regardless of active language)
- **`HudOverlay`** — PvP HUD status lines (`pvp.buy_phase`, `pvp.round_active`, `pvp.waiting`) converted from hardcoded Ukrainian to i18n keys
- **`PlayerSettingsScreen`**, **`HudEditScreen`**, **`AdminMenuScreen`**, **`CompletionRewardScreen`** — all labels, buttons, hints, and error messages converted to translatable keys
- **`KeyBindings.java`** — "leaving location" and "shop unavailable in spectator" messages converted
- All network packets (`PurchaseItemPacket`, `TeleportPacket`, `ImportLocationPacket`, `ExportLocationPacket`, `AdminTeleportPacket`) — server responses now use `Component.translatable()`, resolving client-side in the player's language

### Fixed — Scroll buttons in 8 admin screens (critical UI bug)
Scroll buttons (▲/▼) in 8 admin screens were registered via `addRenderableWidget()` and fell under the scissor clip — they became invisible and unclickable when a list appeared.

Fixed (`addRenderableWidget` → `addStatic`):
- `AdminMenuScreen` — location list
- `CompletionRewardScreen` — reward list
- `WaveConfigScreen` — wave list
- `PlayerMenuScreen` — location list for player
- `PlayerShopScreen` — item list
- `MobSelectionScreen` — mob list
- `WaveMobsEditorScreen` — wave mob list
- `LocationEditorScreen` — mob spawn point list

### Fixed — Memory leak on player disconnect
When a player disconnected while inside a location, `playerData`, `playerBackups`, and `reEntryCooldowns` were not cleaned up. Added a `PlayerLoggedOutEvent` handler in `EventHandler`: on logout, `surrenderPlayer()` is called, properly restoring inventory and clearing all internal maps.

### Fixed — reEntryCooldowns memory leak
Entries in `reEntryCooldowns` (UUID → timestamp) accumulated indefinitely. Added automatic removal of expired entries once per minute (every 1 200 ticks).

### Fixed — PurchaseItemPacket (TOCTOU + NPE)
- References to `ShopPoint`, `sourceList`, and `ShopItem` are now captured atomically
- Added null checks at every step (guards against NPE when an admin edits the shop concurrently)

### Fixed — ImportExportScreen rebuild race
`rebuildWidgets()` could run after the screen was already closed (packet arrives while the player has opened a different screen). Added a `minecraft.screen == this` guard before rebuild.

### Optimization — `getPlayersInLocation()` per-tick cache
The method was called 40+ times per tick, each time iterating over all `playerData` (O(n) per call). It now builds a shared `Map<String, List<ServerPlayer>>` once per tick; all subsequent calls are O(1) lookups. The cache is invalidated on player join/leave.

### Optimization — HudOverlay rendering
- Eliminated duplicate `mc.font.width()` calls (previously computed separately for each line)
- Block width is now calculated in a single pass

---

## [0.2.38] - 2026-03-16

### Fixed — Scissor clipping in scrollable screens
All screens with scrolling now use a **3-pass render**: scrolled content → static header → static footer. Scrolled content no longer overflows or overlaps top/bottom menu elements.

**Fixed screens:**
- **`LocationEditorScreen`** (⚙ Spec tab) — introduced `staticWidgets` set (IdentityHashMap-backed); `addStatic()` helper marks elements; render uses `staticWidgets.contains()` instead of Y-comparisons. PvE/PvP toggle (Y=25), tabs (Y=52), and bottom buttons marked static
- **`PvpLocationEditorScreen`** (🎮 Mode+Rules tab) — same approach: `staticWidgets`, `addStatic()`, 3-pass render. Tabs stay at top and Save/Back buttons stay at bottom while rules scroll
- **`WaveConfigScreen`** (wave list) — render rewritten: content through scissor, header and footer rendered on top via their own scissor zones
- **`WaveMobsEditorScreen`** (wave mob list) — same fix applied

### Fixed — PvP menu navigation
- Clicking the "PvP" button in `LocationEditorScreen` now opens `PvpLocationEditorScreen` directly, without an intermediate screen
- Tabs "⚙ Rules" and "🎮 Mode" merged into a single "🎮 Mode+Rules" tab: each sub-mode (Standard / Deathmatch / Battle Royale) shows its own settings inline

### Fixed — Compilation errors
- `PVP_ROUND_START_PTS` → `PVP_ROUND_POINTS` (correct name in TooltipHelper)
- `BR_SPAWN` → `BR_RANDOM_SPAWN` (correct name in TooltipHelper)
- Duplicate `killerTeam` declaration in `onPlayerKilledPlayer` — removed
- `ParticleTypes.BARRIER` does not exist in 1.20.1 — removed from `BoundaryManager`

---

## [0.2.37] - 2026-03-16

### Fixed — Scissor in LocationEditorScreen
- Spec tab: scrolled content no longer overlaps the PvE/PvP toggle (Y=25), header, or bottom buttons
- Introduced `staticWidgets` set and `addStatic()` helper for precise static-element marking

### Added
- **Auto-open PvP editor** — selecting PvP from `LocationEditorScreen` immediately opens `PvpLocationEditorScreen`
- **Merged PvP editor tabs** — "⚙ Rules" and "🎮 Mode" combined into one "🎮 Mode+Rules" tab with per-sub-mode settings

---

## [0.2.33] - 2026-03-15

### Fixed — Compilation errors (continued)
- **`BoundaryManager`** — missing `import com.wavedefense.wave.PlayerWaveData` added
- **`BattleRoyaleManager`** — missing `import WaveManager` and `import PlayerWaveData` added
- **`getPlayersInLocation`** in `WaveManager` — changed from `private` to `public` (required by `BattleRoyaleManager`)
- **`PvpPlayerStats` import** in `WaveManager` — added
- **`PLAYER_DEATH` loot trigger** in `onPvePlayerDeath` — now fires on PvE death as well

### Updated
- **TooltipHelper** — 30+ new tooltips for all new features

### Added — Location boundary settings
- 4 consequence modes: Timer → Surrender / Damage (HP/sec) / Teleport back / Instant surrender
- Border particles: type (registry ID), count, ring height
- 5 particle presets: smoke / flame / portal / snowflake / enchant

### Added — Mob scatter radius
`rN` button in spawn point list cycles 0→3→5→10→15→20 blocks

---

## [0.2.32] - 2026-03-15

### Fixed — Compilation errors
- **`tickInfoPanels`** — `getMobSpawns()` returns `List<MobSpawnPoint>`, not `List<BlockPos>`; fixed with `.getPos()` at point of use
- **`getRandomSpawnPoint`** — was returning `MobSpawnPoint` instead of `BlockPos`; added `.getPos()`
- **`DustParticleOptions`** in `BoundaryManager` — `Vector3f.ZERO.add(...)` replaced with `new Vector3f(1f, 0f, 0f)`
- Removed unused `DamageSource` import in `BattleRoyaleManager`

### Fixed — Logic
- **Info panels on mob spawn points** — now display correctly for all points after the `MobSpawnPoint` refactor
- **`PLAYER_DEATH` trigger** added to PvE death (previously only fired in PvP)

### Changed
- `BoundaryManager` — complete rewrite: supports all 4 modes, border particles, damage capped to once per second

---

## [0.2.31] - 2026-03-15

### Fixed — PvP loot triggers
- `MATCH_START` — fires at the start of round 1
- `MATCH_END` — fires when the match ends
- `TEAM_WIPE` — fires when a team is eliminated
- `KILL_STREAK_3` — fires every 3 consecutive kills

### Added
- **Kill streak system** — `pvpKillStreaks` tracks consecutive kills; resets on victim death and at round start
- **`PvpTeamSelectScreen`** — per-mode design: Battle Royale (single enter button) / Deathmatch (kill target + teams) / Standard (coloured team buttons)

---

## [0.2.30] - 2026-03-15

### Added — Deathmatch
- `PvpMode.DEATHMATCH` — unlimited respawns; victory by team kill count (`dmKillsToWin`, default 10)

### Added — Battle Royale
- `PvpMode.BATTLE_ROYALE` — random spawn, shrinking border, border particles, out-of-zone damage
- `BattleRoyaleManager` — border shrink logic, particle ring, damage per second
- 🎮 Mode tab in `PvpLocationEditorScreen` — select sub-mode + per-mode settings

---

## [0.2.29] - 2026-03-14

### Fixed
- Surrender did not exit location — `loadClientData` did not clear `currentLocation` on an empty packet
- Lobby timer not displayed — `locationStartTimers` did not send `syncPlayerData` to the client
- Players stuck after PvP victory — `endPvpMatch` did not sync clients

### Added
- PvP waiting effects — Slowness 127 + Blindness instead of spectator mode
- Round start countdown (COUNTDOWN phase)
- Round start points, win points, loss points
- Team auto-balance on player join/leave
- `/wavedefense kick` and `/wdkick`
- `enforceGameMode` per-location option
- "Discard" button in wave mob editor

### Changed
- First-wave delay moved: `timeBetweenWaves` of wave #1 now sets the post-lobby delay

---

## [0.2.28] - 2026-03-14

### Fixed — Critical multiplayer bugs
- Main surrender/death bug — `PlayerWaveData.loadClientData()` did not reset `currentLocation`
- Lobby timer not synced to the client
- Menu persisted after death

### Added
- `/wavedefense kick` command and `/wdkick` alias
- Protocol v7 — new packets for wave import/export

---

## [0.2.27] - 2026-03-13

### Added
- Teammates panel in HUD
- Keybind **L** — leave location without penalty
- Shop import/export (`ExportShopPacket`, `ImportShopPacket`)
- Wave import/export (`ExportWavePacket`, `ImportWavePacket`)
- Protocol v6

---

## [0.2.26] and earlier

Server crash fixes, PvP sync, HUD layout, shop system, wave manager, various UI fixes.
