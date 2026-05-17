# Changelog

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
