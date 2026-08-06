# Changelog
## [0.4.0] - 2026-08-06 — gameplay depth, restored editors, and a large-shop overhaul

The headline is four opt-in systems that give an arena a reason to be replayed. Around
them sits a long tail of correctness work: settings that had silently become uneditable
are back, arenas no longer leak their mobs, one bad location can no longer take down the
server tick, and the whole editing protocol was reworked so a shop with thousands of
modded weapons stops breaking every save on that location.

Existing worlds load and play identically until an admin opts in — every new setting
takes a default that reproduces the previous behaviour. Compile targets are unchanged:
Forge 47.2.0, Minecraft 1.20.1, Java 17.

**Upgrading:** the network protocol moved to version 9, so client and server must be
updated together. If your config still carries `maxShopItems = 100` from an older
install, raise it — the cap is now enforced on bulk imports too.

### Added — endless mode

- A PvE location can now be marked **endless**: waves never run out, there is no
  victory, and the score becomes *how far did you get*.
- The wave list already cycled internally (`(currentWave - 1) % waves.size()`),
  so this needed no new spawn logic — only the two victory gates now defer to
  the flag.
- Mob health and damage scale by **+N% per completed loop** through the wave
  list, configurable (default 10%). Scaling is **linear, not compounding**:
  compounding turns loop 5 into an unplayable wall, which reads as a bug rather
  than a challenge. Mob *counts* already grow through each wave's existing
  `growthPerWave`, so they are deliberately left out of the loop multiplier.
- Endless runs get their own leaderboard (`PvE_ENDLESS`) ranked by wave reached
  rather than points — the two are not comparable numbers.
- **The record is written on death, not on victory.** Endless never reaches
  `triggerVictory`, so without this an endless location would silently never
  produce a leaderboard entry at all. Non-endless deaths are still unranked:
  dying on wave 3 of 10 is not a result worth listing beside a finished run.

### Added — wave modifiers

- Every *N*-th wave (default 3) rolls a random twist, announced in chat with a
  one-line explanation of what it does. The waves in between run clean, which is
  what makes a modifier feel like an event rather than ambient noise — and gives
  players a wave to recover on.
- Eight modifiers: **Swift**, **Armored**, **Regenerating**, **Enraged**,
  **Tough**, **Phantom** (invisible), **Volatile** (explodes on death), and
  **Venomous** (poisons on hit).
- Admins can restrict the pool per location; selecting none means all are
  eligible, and the editor label says so rather than leaving eight empty boxes
  looking like a misconfiguration.
- **Volatile explosions never damage terrain** (`ExplosionInteraction.NONE`).
  Arenas are hand-built, and a modifier that quietly demolishes the map over a
  few runs would be worse than no modifier at all. The blast still hurts players,
  and it fires on *any* death — an environment kill is just as dangerous to be
  standing next to.

### Added — difficulty presets

- Easy / Normal / Hard / Nightmare, set per location, scaling mob health,
  damage, count, and point rewards.
- **Rewards scale with difficulty**, or nobody would ever pick anything above
  Normal.
- Each tier ranks on its own leaderboard so a Nightmare clear never has to
  compete with an Easy one. Normal keeps the unsuffixed key, so existing records
  stay exactly where they are.
- This composes with, rather than replaces, the existing `WaveAutoScaler`: the
  preset sets the baseline that the adaptive scaler then nudges around, so
  Nightmare stays harder than Easy even after the scaler settles.

### Added — lifetime player progression

- A new per-player profile persists across runs, locations, and restarts:
  waves survived, best wave, kills, points, matches played/won, deaths, playtime,
  and XP.
- Level curve is quadratic (`1 + floor(sqrt(xp / 100))`) — early levels arrive
  fast enough to notice, later ones stay meaningful.
- Stored in `world/data/wavedefense_profiles.dat` through the same atomic,
  debounced write path as locations and leaderboards, so a crash mid-write
  cannot corrupt it.
- Profiles are never pruned: a player returning after months keeps their level.

### Fixed — features that had quietly become unreachable

Removing the two legacy location editors in v0.3.0 took their UI with them. The data
kept serializing and the runtime kept honouring it, so nothing broke loudly — the
settings simply could not be changed any more. A player report about missing mob
spawn points led to finding the rest.

- **Mob spawn points** and the **default scatter radius** are editable again, in a new
  Gameplay section: add at your own position, add by coordinates, edit, delete. An
  empty list now explains the consequence — every mob arrives in one spot — instead of
  saying nothing.
- **Starting kit** (`StartingItemsScreen`) had no opener at all despite the items still
  being issued on join. Reachable again from the Economy tab.
- Seven settings were live in-game but had no control anywhere: completion **points**
  reward (the rewards screen only ever edited items), first-wave delay, keep-loot-on-exit,
  player spawn scatter radius, starting points, and **both** location-trigger fields —
  meaning `TriggerEvaluator` polled every tick for a feature that could never be switched on.
- **Personal stats** (`StatsScreen`) had a complete GUI and a working sync packet but
  nothing opened it. Now in the player menu, and it returns there on close.
- Deleted `MobTypeSelectionScreen` and `WaveMobSettingsScreen` — duplicates of the
  screens actually in use, referenced by nothing.

### Fixed — mobs wandered off instead of hunting

- The targeting goal was registered on `goalSelector`, but vanilla runs target
  acquisition on a separate `targetSelector`; on the movement selector it competed with
  the mob's own strolling goals rather than driving them.
- `FOLLOW_RANGE` was never set, leaving the vanilla 16 blocks. On an arena tens of
  blocks across a mob simply could not perceive anyone. Now derived from the arena
  diameter, capped at 128 so a large boundary cannot turn every mob into a long-range
  tracker. Spiders made this obvious because climbing carries them away quickly.
- Line of sight is no longer required, so stepping behind cover does not drop aggro,
  and idle mobs are re-pointed at the nearest player every 2 s.

### Fixed — wave size had no ceiling

- Mob count is a product of four independent factors — `count + growthPerWave ×
  (wave-1)`, player count, the adaptive scaler (up to 5×) and the difficulty preset
  (up to 1.5×) — and **none of them was bounded**. A modest arena (count 3, growth 1,
  two players, Normal) already reaches 104 mobs per entry by wave 50, twice the mod's
  own lag threshold, and a wave may hold up to 20 entries.
- Endless mode guarantees those wave numbers are reached, so this was not a
  theoretical edge case: any endless arena eventually becomes unplayable.
- Capped at 120 mobs per wave entry, with a one-off warning per wave naming the
  location, the mob and the requested figure, so an arena that stopped scaling is
  discoverable instead of mysterious. This is a stability backstop, not a balance
  knob — difficulty is meant to come from tougher mobs, not from more entities than
  the server can tick.

### Fixed — endless silently disabled five configured settings

Endless never reaches `triggerVictory`, so completion rewards, completion points,
the victory exit, the victory screen and the `LOCATION_END` loot trigger could all be
configured but would never fire. The editor now says so directly under the endless
toggle, listing exactly what stops applying and pointing at per-wave points instead.

### Fixed — a stored round count of 0 ended PvP matches after one round

`pvpTotalRounds` was documented as "0 = infinite", but `isAllRoundsDone()` is
`currentRound >= totalRounds`, so 0 was true immediately. The setter clamped to 1,
yet deserialization writes the field directly and bypassed it — a legacy save holding
0 lost every match after the first round. Now clamped on load, and the misleading
comment is gone.

### Fixed — dead arenas left their mobs in the world forever

- Wave mobs are spawned with `setPersistenceRequired()`, so they never despawn on
  their own. `WaveContext.removeSession` calls `dispose()`, which clears the tracking
  set — **it does not remove the entities**. Every other teardown path calls
  `despawnSessionMobs` first; the path taken when the *last* player in a PvE arena
  dies did not.
- So every run that ended in death stranded its entire live wave: persistent hostile
  mobs, untracked because the set had just been cleared, accumulating in loaded chunks
  run after run. The PvP teardown had the same gap for trigger and portal mobs.
- Both paths now despawn before the session is dropped, and the method carries a
  javadoc stating the ordering requirement so the next teardown path does not repeat it.

### Fixed — one bad arena could take down the server tick

- `onServerTick` called seven subsystem ticks and every session tick with no exception
  handling, and the Forge event handler above it had none either. A malformed particle
  id, a null spawn point or one corrupt wave propagated straight out and killed the
  tick. The later blocks in the same method were already guarded, so the file was
  inconsistent with itself.
- Each subsystem now runs inside `safeTick`, which contains the failure to that
  subsystem and logs it — at most once per 10 s per subsystem, because a fault that
  reproduces every tick would otherwise write 20 stack traces a second and bury the
  actual cause.

### Fixed — the crash-safe write contract was only half-implemented

- `atomicWriteCompressed` writes through `.tmp → .bak → ATOMIC_MOVE` and documents
  the guarantee that *"after any single crash, the reader can recover from either
  the main file or the `.bak` — never both missing."* Only **one of four readers**
  actually honoured it. `LeaderboardManager`, `PlayerProfileManager` and
  `WaveManager.loadRuntimeState` caught the read failure and silently started from
  an empty state — discarding a perfectly good backup sitting next to the corrupt
  file. A single bad shutdown wiped the leaderboard.
- The read half now lives next to the write half as `NbtHelper.readWithBackup`,
  and all four readers go through it. When both copies are unreadable the primary
  is renamed to `.corrupted` so the next save cannot destroy evidence.
- **An in-flight save could still be lost on shutdown.** `flushPendingWrites()`
  drained the queue but did not wait for a write that was *already running*: that
  task had taken its snapshot, so the drain found nothing, returned, and the JVM
  killed the daemon save thread mid-write. Combined with the above, that was
  unrecoverable. Flush now ends with a completion barrier — the save executor is
  single-threaded, so a no-op submitted at that point cannot start until the
  running write has finished.
- The executor is deliberately **not** shut down: an integrated server can quit to
  title and open another world in the same JVM, and a terminated executor would
  make every later save throw. The barrier gives the same guarantee without that.

### Fixed — monitor state grew without bound

- `playerSessions` was only ever appended to, and `onPlayerLeave` streamed the whole
  deque to find the open session: a leak that also made every logout progressively
  slower. Now capped, and scanned newest-first, where the match almost always is.
- `playerActivity` kept the last position and game mode of every player who had ever
  connected — data that is stale the moment they log out. Dropped on leave.
- `playerStatistics` retained one object per unique visitor forever. Capped, evicting
  least-recently-active first. Lifetime numbers that genuinely need to persist live in
  `PlayerProfile`; this map only backs the live monitor report.

### Fixed — bulk-adding a large TACZ pack killed the connection

Reported: adding 3000+ guns disconnected the client with
`DecoderException: Payload may not be larger than 32767 bytes`. That was the visible
symptom of three separate faults, any one of which loses items:

- **Packet too large.** Items were batched 25 per packet. A single gun carries enough
  NBT that 25 of them exceed the 32767-byte serverbound payload limit outright.
- **Most packets were silently discarded.** The handler rate-limited itself to one
  packet per 500 ms, but the sender fired all ~120 batches in a single frame — so all
  but the first were dropped without a word. Items were going missing *before* the
  size error ever appeared.
- **Quadratic work and a response that also overflowed.** Every batch triggered a full
  save, a location broadcast and a shop re-sync carrying the entire location NBT —
  which grows with each batch. By the end the server was re-serializing thousands of
  guns per packet and sending it back.

Now one item per packet, paced across client ticks by `ClientShopUploadQueue`
(~160 items/second, so a 3000-gun pack finishes in well under a minute) with a
progress readout. Only the final packet carries a `last` flag, and only that one
triggers the save, broadcast and re-sync. The per-packet rate limit is gone — pacing
is the client's job now, and op permission plus the shop-size cap are the real guards.
An upload in flight is abandoned if the player disconnects.

`maxShopItems` is raised from 100 to 5000 and is now enforced on the bulk path too,
which previously wrote the list directly and bypassed it. **Existing config files keep
their stored value** — if yours still says 100, raise it or the upload will stop there
and tell you so.

### Fixed — a large shop broke every editor, not just bulk-add

Fixing the TACZ upload exposed that the same limit was hit by four other paths. Once a
location held a few thousand guns, **editing anything on it stopped working**: renaming
the arena, changing one price, editing a wave. Each of those sent the whole location —
several megabytes — through a 32767-byte serverbound packet.

The shop and the wave list are the only parts of a location whose size follows its
content rather than a fixed schema, so they no longer travel inside a location payload:

- **`UpdateLocationPacket` excludes `shopItems`, `shopPoints` and `waves`.** When a
  preserved list is absent the handler keeps the server's copy, so a partial payload can
  never wipe one. Stripping is done in the constructor, so no caller can reintroduce it
  by accident.
- **`MergeLocationPacket` now sends only the sections it marked dirty.** It always knew
  which ones the server would apply — it just shipped the rest anyway. Keys the server
  cannot classify are still included, matching the handler's own rule exactly: dropping
  one would have been read as a deletion.
- **New `ShopItemOpPacket`** — add / update / remove a single entry, in the global shop
  or in a named shop point. `ShopItemEditorScreen` uses it instead of resending the
  location.
- **Waves move to a chunked replace** as well, since `WaveConfigScreen` batches its
  edits and saves once — per-wave operations would have had no natural caller there.
- **The location broadcast no longer carries shops at all.** It runs on every login and
  every change, so every player was being handed the server's entire weapon catalogue
  repeatedly. Shops now arrive on demand via the new `RequestShopDataPacket`, and the
  client cache keeps any shop it already holds when a stripped list arrives.

`ShopEditorScreen`'s old clear-send-restore dance is gone with it — it briefly emptied
the client's own list and would have lost it outright had anything thrown in between.

Protocol version bumped to 9: packet ids are assigned by registration order, so an old
client must be refused rather than left to misread the stream.

### Fixed — the same limit applied to loot, rewards and kits

Splitting the shop out revealed that it was never only about shops. Seven location
lists hold modded items, and a modded item's NBT can be enormous: loot spawns (fifty
chest-fulls), completion reward tables, per-team starting kits, PvP spawn points. Any
one of them can exceed the payload limit on its own.

Fixing them individually turned out to be the wrong instinct — excluding shops from
`UpdateLocationPacket` **silently broke shop-point editing**, because that list was
missed and its absence read as a deletion. So the handling is now generic:

- **`LocationSection.isContentSizedList`** is the single source of truth for which keys
  are content-sized. `UpdateLocationPacket`, `MergeLocationPacket` and the editor all
  consult it, so sender and handler cannot drift apart.
- **New `ReplaceLocationListPacket`** replaces any one of those lists, addressed by its
  NBT key. Adding a list later means registering a key, not writing another packet.
- **Chunking is by encoded size, not element count.** Element weight varies by orders of
  magnitude — a plain sword against a rifle with attachments — so "N per packet" is
  either wasteful or unsafe depending on content. It replaces the wave-specific packet
  written moments earlier, which would have been a fourth near-identical transport.
- `MergeLocationPacket` skips these keys on both sides. Without the handler half, the
  sender omitting a list would have wiped shops, waves and loot the admin never touched.

**New `ContentSizedListTest`** fails the build when a list is declared content-sized with
nothing to send it, when only one half of the merge contract skips it, or when the
broadcast starts shipping shops again. Writing it immediately caught two more lists —
per-team kits and PvP spawn points — that had been excluded without a transport.

### Fixed — regressions from the packet split itself

A pass over everything this release touched found that splitting the big lists out had
broken two save paths and left one transport orphaned:

- **`ShopEditorScreen` lost edits on small shops.** It only used the chunked path past a
  50-item threshold; below that it relied on `UpdateLocationPacket`, which now strips
  shops. Adding or deleting a single item in a modest shop — and every shop-point
  change — was silently discarded. It now always sends both lists on their own channel,
  regardless of size.
- **`ReplaceShopItemsPacket` had no sender left** once the generic transport took over.
  Removed rather than left registered, so the packet table matches what actually runs.
- Three imports left dangling by the rewrites are gone.

### Changed — the client no longer trusts its own optimistic edits

Shop screens mutate their local copy first so the UI responds instantly, but the server
can legitimately disagree: an add may hit `maxShopItems`, and an index-based update can
race another admin. After a bulk upload finishes, and after a single-item edit, the
client now re-requests the shop and adopts what was actually stored. Previously the
editor could keep showing entries the server never saved.

### Fixed — boundary particles cost far more than they were worth

- The whole ring was drawn — a particle column every 2 blocks all the way round — and
  every column was broadcast to everyone within 32 blocks, including players not in the
  arena. At the default radius of 50 that is ~471 particle packets per second.
- Each player now receives only the 90° arc they are facing out towards, at 3-block
  spacing, sent to them individually: roughly 20 columns instead of 157, and nothing at
  all for bystanders.
- `boundaryParticleCount` was read into a variable and then ignored — the call passed a
  hard-coded `1`, so the admin's slider did nothing. It works now.
- Players can turn mod particles off entirely in their own settings. This covers both
  the boundary ring and the bbox outline, and is the fix for the reported frame drops.

### Performance

- `BattleRoyaleManager` allocated an empty `HashMap` 20 times a second whenever no
  Battle Royale was running — which is almost always.
- `PortalManager` built a stream pipeline and a capturing lambda per portal-enabled
  location per tick to answer a boolean.
- `TriggerEvaluator` walked the entire server player list *inside* its per-location
  loop, and built an empty `ArrayList` for every location with nobody nearby.

### Fixed — 17 of 31 config options did nothing

Eight were read nowhere; nine only by the config screen that displays them, so the
toggle moved but nothing changed. All are now wired to what their description promises:

- `enableHUD` never gated the HUD. It does now.
- `maxPlayerSpawns`, `maxShopItems`, `maxLootSpawns` were not enforced, though
  `maxWaves` and `maxMobSpawns` were — the intent was there, the rest was missed.
- `pvpHideEnemyNametags` was ignored: hiding was hard-coded at team creation, so the
  option did nothing *and* flipping it later had no effect either.
- `pvpMax*` safety caps now actually clamp the values an admin can type.
- `defaultWaveTime`, `defaultRounds`, `defaultBuyTime`, `zoneActivationRadius` and
  `zoneActivationCountdown` are applied when a location is created.
- `zoneActivationParticles` gates the activation-zone effect.
- `shopCategoriesEnabled` hides the category filter row; the active filter resets to
  ALL when it is switched off, so nothing stays hidden.

### Fixed — filename handling in import/export

All six import/export packets built paths from client-supplied strings, and each did
it differently. Consolidated into `network/FilePathGuard`:

- `ImportWavePacket` had **no containment check at all** — the only one of the family
  without one.
- `ImportLocationPacket` and `ImportShopPacket` compared canonical paths without a
  trailing separator, so a sibling directory sharing a name prefix (`shops_evil`
  against `shops`) passed the check.
- `ExportShopPacket` concatenated an unsanitised location name straight into a path.
- Location names were validated by `DuplicateLocationPacket` but **not** by
  `CreateLocationPacket` — and that unvalidated name is what fed the export filename.
  The rule now lives once, in `LocationManager.isValidName`, and both packets use it.
  Invalid names are rejected with a translated message rather than silently ignored.

All of these required op level, so this is hardening and consistency work rather than
a closed hole.

### Added — a guard so this cannot happen again silently

`SettingReachabilityTest` reads the sources and fails the build when:

1. a `Location` setting the runtime reads has no screen that can change it,
2. a config option is exposed to admins but nothing reads it,
3. a screen exists that nothing opens.

Source scanning rather than reflection, because the question is not "does the method
exist" but "does anything call it". Deleting a screen now breaks the build instead of
quietly removing a feature.

### Added — tests

- `NbtHelperBackupTest` — 9 tests: backup fallback for a corrupt and for a missing
  primary, fresh install treated as normal, quarantine when both copies are bad,
  and the flush barrier actually committing a debounced write before returning.
- `FilePathGuardTest` — 10 tests including the prefix-sibling bypass and the
  location-name rules.
- `ProgressionTest` — 18 tests covering difficulty parsing and monotonicity,
  endless loop arithmetic and its linear-not-compounding guarantee, modifier
  interval and pool rolling, the level curve, and profile NBT round-tripping.

### Notes

- **61 new translation keys** across all 8 supported locales (1504 each, parity checked).
- **Test suite grew from 13 to 82** across nine classes, four of which are structural
  guards that fail the build rather than describing behaviour.
- **No data-format change.** Every new setting takes a default that reproduces the
  previous behaviour, so existing worlds load and play identically until an admin
  opts in. Compile targets are unchanged: Forge 47.2.0, Minecraft 1.20.1, Java 17.
- **Network protocol 8 → 9.** Packet ids are assigned by registration order and this
  release adds, removes and reorders several, so client and server must be updated
  together. A mismatched client is refused rather than left to misread the stream.
- **Config:** `maxShopItems` default raised 100 → 5000, and it is now enforced on the
  bulk-import path that previously bypassed it. Existing config files keep their stored
  value — raise it by hand if yours still says 100.
- Removed: `MobTypeSelectionScreen` and `WaveMobSettingsScreen` (duplicates of the
  screens actually in use), and the `ReplaceShopItemsPacket` / per-wave transports that
  the generic chunked list channel superseded.
- Minor version bump rather than a patch: four new gameplay systems, several restored
  features and a protocol change is well past a bug-fix release.

## [0.3.0] - 2026-06-19 — HUD fixes, cleanup + concurrent multi-admin editing

A maintenance release that fixes two long-standing HUD bugs reported by
players, pays down tech debt, and lets multiple admins edit locations at the
same time safely. No data-format changes — existing saves load unchanged.

### Fixed — HUD showed stale data (player-reported)

- **The next-wave countdown never counted down.** `PlayerWaveData.timeUntilNextWave`
  was only ever written when a player *joined* a location, and `syncPlayerData`
  ran solely on discrete events (purchase, death, join) — there was no periodic
  sync. The HUD therefore displayed whatever number was captured at join time
  forever, which players saw as the timer "freezing" and the wave-start `0:30`
  lingering on screen.
- **The wave counter never advanced** for the same reason: `setCurrentWave` was
  only called on join (and by `PvpRoundManager` for PvP), never when a PvE
  location moved to the next wave. The HUD stayed on the wave you arrived at.
- Both are fixed by a new `WaveManager.refreshHudState()`, run once per second,
  which derives the live wave number and countdown from the authoritative
  `LocationSession` (`startTimerMs` for the lobby phase, `waveTimerTicks`
  between waves) and syncs each PvE player — **only when a rendered value
  actually changed**, so an idle lobby sends nothing. PvP locations are skipped
  because `PvpRoundManager` already owns and syncs that state.
- **Smooth countdown:** `ClientPlayerDataManager.tickClient()` now decrements the
  timer locally between the server's 1 Hz syncs (each incoming packet resets the
  interpolation window, so the client can never drift more than a second). The
  timer keeps moving even if a sync packet is late.

### Fixed — a crash no longer eats player inventories

- When a player entered an arena their real inventory was stashed in a
  `PlayerBackup` that lived **only in memory**. A clean `/stop` was survivable
  (every player fires a logout event, which surrenders them and hands the gear
  back), but a crash or `kill -9` destroyed those backups along with the
  process — the player logged back in with an arena loadout and no way to
  recover their belongings.
- Live match state is now persisted to `world/data/wavedefense_runtime.dat`:
  on a clean stop, and autosaved every 30 s while any session is running, so a
  crash costs at most half a minute.
- On login, `recoverCrashedPlayer` restores any pending backup — inventory,
  armour, position, health, XP and game mode — and tells the player what
  happened.
- **Sessions are deliberately not resumed** after a restart: the mobs a wave
  spawned are gone and the world has moved on, so a half-finished wave could
  never complete. Abandoned sessions are discarded (and logged) while the
  inventory backups are kept.

### Added — onboarding hints

- Joining a location now prints a short intro: the objective for that specific
  mode (waves to survive / kills to win / last one standing / capture the
  points / rounds to win) and the mod's hotkeys — **V** menu, **B** shop,
  **G** leave, plus **R** to ready up in PvP.
- Previously these keybinds were undiscoverable outside the vanilla controls
  menu, so new players had no idea the mod had a UI at all.
- Toggle with `showJoinHints` in the config for servers that explain it
  themselves. Translated into all 8 supported languages.

### Added — tests & CI

- `PvpRoundStateTest` — 13 tests covering the PvP state machine: phase
  transitions, timer clamping, ready-check bookkeeping, Deathmatch and
  objective win conditions, KotH hold timers, the Battle Royale
  last-survivor/draw split, and NBT round-tripping. This is the highest-risk
  class in the mod and had no coverage.
- `.github/workflows/build.yml` — builds and tests on every push and PR
  (JDK 17, cached ForgeGradle/Minecraft artifacts, JUnit report surfaced in the
  checks tab, jar uploaded as an artifact).

### Changed — HUD status panel

- The three free-floating white `drawString` lines in the bottom-right corner
  (points / wave / timer) are now one grouped panel with a `GuiTheme` backdrop
  and border, matching the editor screens. This fixes readability against bright
  terrain and the "UI needs refining" feedback.
- The countdown gained a **progress bar** that drains as the timer runs out, plus
  urgency colouring — white → amber under 10 s → red under 5 s.
- The panel sits clear of the hotbar and auto-sizes to its widest row.

### Added — concurrent multi-admin location editing

- **Section-level merge saves.** The location editor no longer replaces the
  whole location on Save (which silently clobbered a second admin's edits —
  classic lost-update). It now diffs the working copy against the snapshot it
  opened and sends only the **changed sections** (`MergeLocationPacket`). The
  server merges those keys onto the *current live* location, so two admins
  editing different aspects of the same arena (e.g. one tunes waves while the
  other edits the shop) both keep their work.
- New `data/LocationSection` partitions all 113 persisted NBT keys into the six
  editor sections (General / Gameplay / Area / Economy / Visual / Compat) plus
  a `RUNTIME` group (play-lock, lifetime stats, live points/teams). RUNTIME
  keys are **never** written by an editor save — fixing a latent bug where
  saving the editor mid-match reset live points/stats to the editor's snapshot.
- `LocationSectionTest` enforces the safety invariant: every persisted key maps
  to exactly one section, so a new field can't be added without being placed
  (an unmapped key would silently drop on merge — the test fails loudly).
- Editing **different** locations concurrently already worked; this makes the
  **same** location safe too (different sections merge; same section is
  last-write-wins, acceptable).

### Changed — build / tests / perf

- Enabled JUnit 5 (`useJUnitPlatform()`) in `build.gradle` — the existing
  Mockito/Jupiter tests (and the new section-coverage test) were silently never
  run by Gradle before.
- **Per-tick allocation removed.** The five server managers that scan every
  location each tick (Portal, Zone, InfoPanel, Trigger ×2) now use a new
  non-copying `LocationManager.getAllLocationsView()` (an unmodifiable view of
  the live map) instead of `getAllLocations()`, which since v0.2.66 allocated a
  fresh `ArrayList` copy on every call — ~60 short-lived lists/sec at idle.
  One-off / backup callers keep the defensive-copy variant.

### Removed

- **Legacy location editors deleted** — `LocationEditorScreen` (1609 LOC) and
  `PvpLocationEditorScreen` (1417 LOC), both `@Deprecated(forRemoval)` since
  v0.2.56, are gone. The unified `UniversalLocationEditor` (6 tabs, PvE+PvP)
  has been the default since v0.2.58 and covers every workflow the old screens
  did. **−3026 LOC** of duplicated editor code.
- The `📜 Legacy` button and `editLocationLegacy()` routing were removed from
  the admin menu; the location-row layout reclaimed the freed 35 px for the
  name button.
- Dropped the now-unused `wavedefense.tooltip.edit_location_legacy` lang key
  from all 8 locales (1433 → 1432 keys, still Δ+0 parity).

### Changed

- **`TriggerEvaluator` god-method refactored.** `checkWaveTriggerCondition`
  (130-line switch with nested inventory lambdas, cx/LOC 0.36) was split into
  named, behaviour-identical helpers: `hasDiamondGear`, `hasIronGear`,
  `hasSword`, `anyPlayerHasConfiguredItem`, and `customTriggerValue` (the last
  also de-duplicates the MOBS_KILLED_N / WAVES_SURVIVED_N value-lookup blocks).
  The switch is now a clean dispatcher.
- **Particle resolution de-duplicated.** Three copies of `resolveParticle`
  (in `BattleRoyaleManager`, `BoundaryManager`, `CapturePointManager`) had
  drifted apart with inconsistent fallback tables. Consolidated into a single
  `wave/ParticleHelper.resolveParticle(id, fallback)` that merges the union of
  all handled particles, so border / boundary / capture-point visuals now
  resolve the same set consistently. Each caller passes its own default
  (FLAME for the BR border, SMOKE for zones). Removed the now-orphaned
  `BuiltInRegistries` / `ResourceLocation` imports from the three managers.

### Deferred (intentionally not in this release)

- **Lang-key pruning** — ~360 keys appear unused to a static scan, but many are
  built dynamically (`key + suffix`) and pruning risks runtime
  missing-translation breakage. Skipped; orphan keys are ~50 KB and harmless.
- Unit tests + CI, and the `SavedData` persistence migration, remain on the
  roadmap for a later release.

## [0.2.66] - 2026-06-19 — Hardening, performance & robustness pass

A senior-modder / server-ops audit drove a round of correctness, performance,
and durability fixes. No new gameplay features — this is a stability release
focused on production-server safety. No data-format changes (existing
`wavedefense_locations.dat` / leaderboard files load unchanged).

### Performance

- **O(1) location lookup.** `LocationManager` now stores locations in a
  `LinkedHashMap<String, Location>` (insertion-ordered) instead of an
  `ArrayList` scanned with `stream().filter()`. `getLocation()` was called
  from 73 sites, several on per-tick hot paths — now constant-time.
- **`getAllLocations()` returns a defensive copy** in creation order, closing
  the mutable-internal-list encapsulation leak (all callers are read-only).

### Durability

- **Atomic file writes** (`NbtHelper.atomicWriteCompressed`): serialize to
  `.tmp`, move current file to `.bak`, then commit `.tmp` → main via
  `ATOMIC_MOVE` (falls back to `REPLACE_EXISTING` on filesystems without
  atomic rename). After any crash, either the main file or `.bak` is always
  valid — corruption can no longer wipe all locations.
- **Async + debounced saves** (`NbtHelper.atomicWriteCompressedAsync`):
  disk I/O moves off the server thread onto a daemon executor, and bursts of
  saves (admin spam-clicking Save, fast tab switches, round-end leaderboard
  writes) collapse into a single write per 1 s window. Server stop flushes
  synchronously so nothing is lost on shutdown. The serialization snapshot
  is taken eagerly on the server thread, so there is no read-during-write race.
- **Per-entry load resilience:** a single malformed location entry is now
  skipped + logged instead of aborting the whole file load.
- **Data-version migration hook** scaffolded in `LocationManager` and
  `LeaderboardManager` (`migrate(tag, fromVersion)`) — v0→v1 is a no-op, but
  the seam exists for future schema changes.

### Security

- **Rate limits** added to seven previously-unprotected packets:
  `UpdateLocationPacket` (500 ms), `ReplaceShopItemsPacket` (100 ms/chunk),
  `BulkAddShopItemsPacket` (500 ms), `ImportLocationPacket` (2 s),
  `RequestLocationDataPacket` (1 s), `UpdatePlayerSettingsPacket` (500 ms),
  `ReadyCheckPacket` (250 ms) — closes a packet-flood DoS surface.
- **Path-traversal guard** added to `ExportLocationPacket` (canonical-path
  check); `ExportWavePacket` was already safe via filename sanitization.

### Observability

- Silent `catch {}` blocks on critical paths (scoreboard team assign/remove/
  cleanup, mob-effect parsing) now log at `debug`, so failures leave a trace
  instead of vanishing.
- `volatile` added to the four static manager singletons in `WaveDefenseMod`
  (read from the async save thread).

### Notes

- Deferred to a later release (per scope decision): `TriggerEvaluator`
  god-method refactor, removal of the two `@Deprecated` legacy editors
  (~3026 LOC), and pruning of ~362 unused lang keys (dynamic-key
  false-positive risk).

## [0.2.65] - 2026-06-03 — Team colors/display names + admin debug HUD + inspection commands

PvP teams gain real customization (named, colored), admins get an F4 debug
overlay, and three new /wda commands for inspection and targeted teleport.

### Added — Team colors + display names (Phase A)

`data/PvpSpawnPoint`:
- `colorName` — ChatFormatting enum name as string (e.g. "RED", "BLUE", "GREEN").
  Empty = legacy hash-from-team-name auto-pick.
- `customDisplayName` — shown in HUD/scoreboard/minimap legend. Empty = use
  `teamName` (internal id).
- `resolveChatColor()` — falls back to 8-palette hash if no explicit colour set.
- `getDisplayName()` — convenience: returns custom name or team name.
- NBT save/load: only persists fields when non-empty (compact).

`gui/universal/UniversalLocationEditor` — spawn-edit form gains:
- **Display name** EditBox (under team name)
- **Color** cycle button — cycles through 9 states (auto + 8 ChatFormatting
  colours), preview shows §<color>● next to current value
- Session-level `spawnEditingColor` state — cleared when form opens/closes/cancels

Spawn list rows now show `§<color>● §f<DisplayName>§8(teamName) §7XYZ` instead
of plain `§e<teamName> §7XYZ`.

`MinimapPreviewWidget` honors explicit `colorName` first, falls back to hash
palette otherwise.

### Added — Admin Debug HUD (Phase B)

New file `gui/AdminDebugHud.java` (~110 lines) — F3-style left-aligned overlay
with semi-transparent backdrop. Toggled by **F4** keybind.

Lines:
- §e§lWaveDefense [DEBUG] F4 to hide
- Tick: 20.0/s (target 20) — TPS estimated client-side via 20-sample frame ring
- Last PvP sync: N ms ago — freshness indicator (green &lt;1s, yellow &lt;5s, red older)
- This loc: phase + ready count + timer
- My team: name + coords
- Heap NN/MMMB, FPS NN

Self-guards: returns immediately if not visible, no player, GUI hidden, or
local player lacks op-level 2.

`gui/ClientPvpStateManager` adds `lastUpdateMs` field + `getLastUpdateAgoMs()`.

`KeyBindings` adds `debugHudKey` bound to GLFW_KEY_F4. Tick handler:
- Records frame for TickRateProbe every client tick
- Consumes F4 click (op-level gate) → toggles `AdminDebugHud.visible`

### Added — 3 new /wda commands (Phase C)

```
/wda players-in <location>     # list players currently inside, with team
/wda who-ready <location>      # list ready-set during READY_CHECK
/wda tp-to-spawn <player> <location> <team>   # rebalance targeted player
```

`PvpRoundManager.debugDumpReadySet(loc)` — multi-line string with player names
and timer remaining. Returns sensible message when phase != READY_CHECK.

Audit-logged via existing `AuditEvent.success()` factory.

### Translations

4 new keys × 8 langs = **32 strings**: F4 keybind label + 3 spawn-form labels
(display name, color, color_auto placeholder).

### Files changed

**Created (1):**
- `gui/AdminDebugHud.java`

**Modified (6):**
- `data/PvpSpawnPoint.java` — colorName + customDisplayName + helpers + NBT
- `gui/universal/UniversalLocationEditor.java` — display-name EditBox + color
  cycle + spawnEditingColor state + reset on form close + spawn-list row
  format + safeCf helper
- `gui/widgets/MinimapPreviewWidget.java` — honor explicit colorName
- `gui/ClientPvpStateManager.java` — lastUpdateMs + getLastUpdateAgoMs
- `events/KeyBindings.java` — debugHudKey + F4 handler + TickRateProbe.recordTick
- `events/ClientEventHandler.java` — call AdminDebugHud.render in HUD overlay
- `commands/WaveDefenseAdminCommands.java` — 3 new subcommands wired into register
- `wave/PvpRoundManager.java` — debugDumpReadySet
- 8 × lang files (+4 keys each)
- `gradle.properties`, `README.md` — version 0.2.65

### Smoke test
1. `./gradlew build` clean.
2. Edit a PvP spawn → set Display name="Crimson Squad", cycle Color to RED →
   Save. Spawn list shows §c● §fCrimson Squad§8(internal_name) ...
3. Press F4 → admin debug HUD shows TPS, sync time, phase, team, heap.
   Press F4 again to hide. Non-op players see nothing.
4. `/wda players-in test-arena` lists everyone in that location with their team.
5. `/wda who-ready test-arena` during READY_CHECK lists who pressed R.
6. `/wda tp-to-spawn Alice test-arena Red` teleports Alice to the Red team
   spawn point.

### Deferred to v0.2.66+
- Scoreboard / nameplate using customDisplayName + colorName (currently still
  uses teamName for the WD_TEAM_PREFIX team)
- ChatFormatting picker UI (currently text-cycle button — could be a grid)
- AdminDebugHud auto-pinned packet-rate counter

---

## [0.2.64] - 2026-06-03 — Chunked shop save + reset-defaults + per-team starting items

Three quality features that together remove a real crash class and unlock a
much-requested PvP customization.

### Added — Chunked shop save (Phase A)

New `network/packets/ReplaceShopItemsPacket.java`:
- Client→server with `(locationName, chunkIndex, totalChunks, items[])`
- Server-side per-(player, location) accumulator keyed by chunk index
- When all chunks arrive: replaces `location.getShopItems()` (replace, not append),
  persists via `locationManager.save()`, broadcasts via `broadcastLocationData()`
- Stale buffers cleaned up after 30s

`ShopEditorScreen.saveChanges()` now branches on item count:
- ≤ 50 items: single `UpdateLocationPacket` (existing path)
- \> 50 items: sends metadata-only `UpdateLocationPacket` (with shopItems
  temporarily emptied) + N `ReplaceShopItemsPacket` chunks of 25 items each
- Action-bar feedback: *"§6Shop save sent in 5 chunks (127 items)"*

This removes the v0.2.63 "large-shop warning" and replaces it with a real fix.
Shops with 200+ items now save reliably under Forge's 32 KB channel limit.

### Added — Reset-to-defaults per section (Phase B)

New helper `sectionWithReset(x, y, w, langKey, Runnable)` on
`UniversalLocationEditor` — adds a small §a↩ button on the right of the section
header with tooltip *"Reset this section to default values"*.

Applied to 3 sections:
- **InfoPanels** (Visual tab) → resets all 9 flags + offsetY + textScale +
  hasShadow + mobSpawnPanelEnabled to documented defaults
- **MnS** (Compat tab) → resets all 7 override values to 0
- **Portal** (Area tab) → resets all 5 portal fields to defaults (disabled,
  60s open-after, 30s penalty, 300s respawn, -1 penalty wave, true disappear)

Future sections gain this for free by switching their `section(...)` call to
`sectionWithReset(...)`.

### Added — Per-team starting items (Phase C)

`data/PvpSpawnPoint`:
- New `List<ItemStack> startingItems` field with NBT save/load
- Empty list = no override (fall back to location-global items)
- Non-empty list = ADDITIVE on top of location items (admin can mix)

`wave/PvpRoundManager.addPlayerToPvpLocation`:
- After spawn-point selection, if `!keepInventory` and spawn's `startingItems`
  is non-empty, copies each item into player inventory in addition to the
  location-global ones already added

`gui/StartingItemsScreen`:
- New constructor `(parentScreen, List<ItemStack>, titleSuffix)` for arbitrary
  item lists (not tied to Location)
- Legacy `(parentScreen, Location)` constructor delegates to the new one
- All internal `location.getStartingItems()` calls replaced with `items` field

`gui/universal/UniversalLocationEditor` (spawn-edit form):
- New "Edit team starting items (N) ▶" button below the coord picker (when
  editing an existing spawn). Opens StartingItemsScreen against the spawn's
  items list with team name as title suffix
- For newly-being-added spawns: §7"Save the spawn first, then re-open to add
  team items" hint instead (can't open StartingItemsScreen against a list
  that's not in `location.getPvpSpawnPoints()` yet)

### Translations
4 new keys × 8 langs = **32 strings**.

### Files changed

**Created (1):**
- `network/packets/ReplaceShopItemsPacket.java`

**Modified (5):**
- `network/PacketHandler.java` — register ReplaceShopItemsPacket
- `data/PvpSpawnPoint.java` — startingItems field + NBT save/load
- `wave/PvpRoundManager.java` — apply per-team starting items on PvP join
- `gui/StartingItemsScreen.java` — refactor to take List directly, legacy
  Location constructor delegates
- `gui/universal/UniversalLocationEditor.java` — sectionWithReset helper +
  3 reset sites + "Edit team items" button in spawn-edit form
- `gui/ShopEditorScreen.java` — chunked save path with CHUNK_THRESHOLD=50,
  CHUNK_SIZE=25; replaces v0.2.63 large-shop warning
- 8 × lang files (+4 keys each)
- `gradle.properties`, `README.md` — version 0.2.64

### Smoke test
1. `./gradlew build` clean.
2. Bulk-add 100 Tacz guns via TaczBulkAddScreen → already works (v0.2.55).
   Now ALSO open ShopEditor → Save → no crash, action-bar shows chunked count.
3. Open editor InfoPanels section → click §a↩ → all flags reset to sane defaults.
4. PvP location → edit spawn point → save → re-open → click "Edit team starting
   items (0)" → opens StartingItemsScreen titled with team name → hold sword,
   add → save → spawn at this team in-game → sword is in inventory.

### Deferred to v0.2.65
- Custom team colors + display names (PvpSpawnPoint.color field + dedicated UI)
- F3-style admin debug HUD
- /wda commands: `players-in`, `who-ready`, `tp-to-spawn`

---

## [0.2.63] - 2026-06-03 — UX polish: tooltips, warnings, duplicate, shop hint

Quality-of-life polish across the editor and admin menu, requested as the next
"якість і зручність" pass after v0.2.62.

### Added — Tooltips on cryptic EditBox fields (Phase A)

New `labelledIntRowT` variant of `labelledIntRow` that auto-discovers a tooltip
by convention: if a lang key `{labelKey}.tooltip` exists, both the label and
the EditBox get a hover-tooltip with the long explanation. Callers don't change
— tooltips appear as soon as the translation key is added.

16 tooltip keys added across the most-confused fields:
- PvP: `round_delay`, `round_time_limit`, `match_time_limit`, `dm_spawn_mode`,
  `br_shrink_interval`, `br_initial_wait`, `br_final_radius`,
  `score_per_sec`, `round_duration`, `koth.hold_duration`, `ready_timeout`
- Area: `portal_open_after`, `portal_penalty_timer`
- Behaviour: `leave_timer`, `victory_linger`, `re_entry_cooldown`

Future tooltips can be added by appending `.tooltip` suffix lang keys —
no code change required.

### Added — Missing-config warning bar (Phase B)

`UniversalLocationEditor.renderConfigWarnings()` runs each render frame and
draws a yellow ⚠ line below the title for critical missing setup:

- §e⚠ No bounding box set
- §e⚠ PvP needs at least 2 spawn points (current: N)
- §e⚠ Objective mode needs capture points
- §e⚠ PvE has no waves configured
- §e⚠ PvE player spawn not set

Multiple warnings join with §8| separator. Subtle dark backdrop so it stays
legible over the GuiTheme header. Disappears once the location is sufficiently
configured.

### Added — Duplicate location workflow (Phase C)

New `network/packets/DuplicateLocationPacket.java`:
- Client→server with `(sourceName, targetName)`
- Server validates: perm ≥2, source exists, target doesn't collide, name regex OK
- Clones via `Location.load(src.save())` NBT roundtrip — every persisted field
  copied (waves, shop, spawns, capture points, MnS overrides, etc.)
- New name set via `setName()`, added to LocationManager, broadcast to clients

New `⎘` button (cyan) in each AdminMenuScreen row (between the name and `✎`):
- `duplicateLocation(sourceName)` picks first non-colliding `_copy`, `_copy2`, …
  suffix (up to 100) and sends the packet
- Tooltip: "Duplicate %s (clone all settings under a new name)"
- Row layout adjusted: name button width reduced by 28px to fit the new icon

### Added — ShopEditor large-shop warning (Phase D)

`ShopEditorScreen.saveChanges()` now displays an action-bar warning if
`shopItems.size() > 50`:
*"§e⚠ Shop has 127 items — large saves may take a moment"*

Full chunked-save protocol (replacing one big `UpdateLocationPacket` with
multiple `ReplaceShopItemsPacket` deltas) deferred to v0.2.64.

### Translations

24 new keys × 8 langs = **192 strings**:
- 16 tooltip keys
- 5 warning labels
- 1 duplicate tooltip + 1 duplicate-too-many error
- 1 shop large-shop warning

### Files changed

**Created (1):**
- `network/packets/DuplicateLocationPacket.java`

**Modified (5):**
- `network/PacketHandler.java` — register DuplicateLocationPacket
- `gui/universal/UniversalLocationEditor.java` — labelledIntRowT helper +
  renderConfigWarnings + render-frame call
- `gui/AdminMenuScreen.java` — `⎘` button + `duplicateLocation` method
- `gui/ShopEditorScreen.java` — large-shop warning in saveChanges()
- 8 × lang files (+24 keys each)
- `gradle.properties`, `README.md` — version 0.2.63

### Smoke test
1. `./gradlew build` clean.
2. Hover any "BUY time", "round time limit", or "BR shrink interval" field →
   long-form explanation appears.
3. Create new PvP location → top of editor shows §e⚠ for no bbox + few spawns;
   add spawns and bbox → warnings clear.
4. Click `⎘` next to a location → new location appears named `name_copy`,
   identical settings. Click again → `name_copy2`.
5. Save a 100-item shop → action-bar warning appears before success message.

### Deferred to v0.2.64
- Chunked ShopEditor save (real replacement of UpdateLocationPacket for large shops)
- Reset-to-defaults per-section
- Per-team starting items
- Custom team colors / display names
- F3-style admin debug HUD

---

## [0.2.62] - 2026-06-03 — Ready-check UI completion + graphical minimap preview

Closes the two v0.2.61 deferrals — players can now press **R** to ready up,
and a top-centre HUD overlay shows progress. The Area-tab minimap preview also
gets a real graphical rectangle with team-coloured spawn dots (text fallback
on narrow screens).

### Added — Hotkey **R** + `ReadyCheckPacket`

New file `network/packets/ReadyCheckPacket.java`:
- Client→server toggle, payload = single `boolean ready`
- Server reads sender UUID from `ctx.getSender()` (never trusts payload identity)
- Routes to existing `PvpRoundManager.markPlayerReady` / new `unmarkPlayerReady`
- Guard: silently no-op if player not in PvP location with phase=READY_CHECK
- Registered in `PacketHandler.register()` between existing C2S packets

New `KeyBindings.readyKey` bound to `GLFW.GLFW_KEY_R`, `KeyConflictContext.IN_GAME`:
- `ClientEvents.onClientTick` consumes click only when phase=READY_CHECK
- Toggles ready state via `ReadyCheckPacket(!isMeReady)`
- Action-bar feedback message "§a✓ You are ready" / "§7You are no longer ready"

### Added — `PvpReadyHud` overlay

New file `gui/PvpReadyHud.java` (~120 lines):
- Top-centre semi-transparent box with §606060 border
- Line 1: title (changes between "Ready up — press R" and "✓ Ready — press R to cancel")
  + " — X/Y ready" count
- Line 2: per-team rows like "§fRed: §a● §7○ §a●  §8| §fBlue: §a● §7○"
- Line 3: "§7Auto-start in §eXs§7" timer (or "Waiting for everyone…" if timeout=0)
- Self-guards: returns immediately if phase ≠ READY_CHECK or `hideGui`
- Called from `ClientEventHandler.onRenderGuiOverlay` after `PlayerHUD.render`

### Added — Server-side ready sync

`SyncPvpStatePacket.build` gains overload accepting `Set<String> readyPlayerNames`.
NBT writes `readyPlayers` ListTag of names (only when set is non-empty — saves
bytes during non-READY_CHECK syncs). The 8-arg overload kept for backwards
compat with any other caller; new flow uses the 9-arg version.

`PvpRoundManager.broadcastPvpSync` collects ready UUIDs → names via
`PvpPlayerStats.getPlayerName()` and passes to packet builder.

`ClientPvpStateManager`:
- New `Set<String> readyNames` field
- `getReadyNames()` accessor
- `isMeReady()` — looks up local player's name via `mc.player.getGameProfile().getName()`
- Cleared in `reset()`

### Added — `MinimapPreviewWidget` (graphical Area-tab preview)

New file `gui/widgets/MinimapPreviewWidget.java` (~95 lines):
- Extends `AbstractWidget`, takes `(x, y, size, location)`
- Renders top-down 2D map:
  - Dark fill + light border (a rectangle)
  - 3×3 coloured dot per PvpSpawnPoint, position scaled to bbox X/Z extent
  - Team colour from 8-palette hash (red/blue/green/yellow/purple/cyan/orange/pink)
  - Centre label = "WxL" block dimensions
- Editor Area tab uses widget when `colW >= 360` (80px square on the right),
  falls back to single-line text summary on narrow screens

### Added — `unmarkPlayerReady` server API

`PvpRoundManager.unmarkPlayerReady(WaveManager, String, UUID)` — mirror of
`markPlayerReady`, removes UUID from ready set and broadcasts new sync.
Used by `ReadyCheckPacket` when player presses R twice.

### Translations

8 new keys × 8 langs = **64 strings**. Covers keybind display name, 3 HUD
overlay lines (4 with the "ready vs not-ready" title variant), and 2 action-bar
messages for ready/un-ready feedback.

### Files changed

**Created (3):**
- `network/packets/ReadyCheckPacket.java`
- `gui/PvpReadyHud.java`
- `gui/widgets/MinimapPreviewWidget.java`

**Modified (7):**
- `network/PacketHandler.java` — register ReadyCheckPacket
- `network/packets/SyncPvpStatePacket.java` — 9-arg build overload + readyPlayers NBT
- `events/KeyBindings.java` — readyKey + onClientTick handler
- `events/ClientEventHandler.java` — call PvpReadyHud.render from HUD overlay
- `gui/ClientPvpStateManager.java` — readyNames + isMeReady + reset cleanup
- `wave/PvpRoundManager.java` — unmarkPlayerReady, broadcastPvpSync passes readyNames
- `gui/universal/UniversalLocationEditor.java` — MinimapPreviewWidget integration
  (wide screens) + text fallback (narrow)
- 8 × lang files (+8 keys each)
- `gradle.properties`, `README.md` — version 0.2.62

### Promises closed
- ✅ Hotkey R + ReadyCheckPacket (v0.2.61 deferred)
- ✅ PvpReadyHud overlay (v0.2.61 deferred)
- ✅ Graphical minimap preview (v0.2.61 deferred)

### Smoke test (user host)
1. `./gradlew build` clean.
2. Open PvP test location, join with 2 players → READY_CHECK starts, top-centre
   HUD overlay appears. Each player presses R → state advances immediately.
3. Open editor Area tab on a location with bbox + 4 spawns → see rectangular
   preview with 4 coloured dots in approximate top-down positions.
4. Resize editor window narrow (< 360px content) → preview falls back to text
   summary (same as v0.2.61).
5. Lang switch UA↔EN → all new strings translated.

---

## [0.2.61] - 2026-06-02 — Ready-check phase + admin commands + config caps

Replaces the implicit "WAITING → start" with an explicit **READY_CHECK** phase
where players spawn at their team points but are frozen until everyone presses
ready (or the timeout fires). Adds match-control admin commands and config caps.

### Added — Ready-check phase (Phase A)

New `PvpRoundState.Phase.READY_CHECK` between WAITING and BUY/ACTIVE:
- When `minPlayers` are present, state transitions to READY_CHECK with a
  configurable timeout (per-location, default 60s, config-capped at 600s).
- Players freeze using the existing PvpWaitEffect (slowness + blindness) until
  either:
  - **All in-location players press ready** → fast advance to BUY/ACTIVE
  - **Timeout expires** with ≥minPlayers ready → force-start (AFK out-of-luck)
  - **Timeout expires** with <minPlayers ready → fall back to WAITING
- BR mode adds a late-join lock: once past READY_CHECK, new joiners are
  rejected with §c"Battle Royale match already in progress" message. DM, Std,
  CtP, KotH still allow late joins.
- New public API in `PvpRoundManager`:
  - `markPlayerReady(WaveManager, String, UUID)` — for future ReadyCheckPacket
  - `skipReadyCheck(WaveManager, String)` — admin force-skip
  - `forceEndPvpLocation(WaveManager, String)` — admin stop/restart
  - `debugDumpPvpState(String)` — multi-line state for `/wda debug state`

**Note**: actual ready-press UI (hotkey R + HUD overlay) is deferred to v0.2.62.
For v0.2.61 timeout auto-advances. Admins can `/wda match skip-readycheck`.

New persistent field on Location: `pvpReadyCheckTimeoutSec` (default 60),
serialized via LocationSerializer NBT roundtrip.

### Added — Minimap preview (Phase B, MVP)

Area tab now shows a text summary of the minimap when bbox is set:
*"Minimap preview: 64×64 (h=20) ● ×4 §a✓ HUD ON"* — dimensions, spawn count,
HUD on/off state. Real graphical preview deferred to v0.2.62.

### Added — Config knobs (Phase C)

`config/wavedefense-common.toml`:
- `pvpMaxReadyCheckTimeoutSec` — hard cap for per-location ready-check timeouts (600s default)
- `pvpMaxKillPoints` / `pvpMaxDeathPenalty` / `pvpMaxWinPoints` / `pvpMaxLosePoints`
  — caps against accidental absurd input (100000 default, max 9999999)

### Added — Admin commands (Phase D)

```
/wda match skip-readycheck <location>     # admin force-skip ready phase
/wda match stop <location>                # end ongoing match
/wda match restart <location>             # stop + clean (players rejoin)
/wda debug state <location>               # PvP state dump
/wda debug reload <location>              # reload location from disk
/wda reset leaderboard                    # clear all records (PERM_ADMIN)
```

Match commands require `PERM_MOD`. Reset requires `PERM_ADMIN` (destructive).
All commands audit-logged via existing `AuditLogger`.

New helper: `LeaderboardManager.clearAll()` — wipes records + persists.

### Translations
5 new keys × 8 langs = **40 strings**. Covers ready-check broadcast messages,
BR lock rejection, editor ready-timeout label, minimap preview label.

### Files changed
- `data/PvpRoundState.java` — new READY_CHECK phase + readyPlayers Set + helpers
- `data/Location.java` — new `pvpReadyCheckTimeoutSec` field + accessors
- `data/LocationSerializer.java` — serialize new field
- `data/LeaderboardManager.java` — new `clearAll()` method
- `wave/PvpRoundManager.java` — BR late-join lock, READY_CHECK tick handler,
  WAITING→READY_CHECK transition, public ready-check API, force-end + debug-dump
- `commands/WaveDefenseAdminCommands.java` — match/debug/reset subcommands
- `config/WaveDefenseConfig.java` — 5 new PvP caps
- `gui/universal/UniversalLocationEditor.java` — ready-timeout EditBox + minimap preview line
- 8 × lang files (+5 keys each)
- `gradle.properties`, `README.md` — version 0.2.61

### Deferred to v0.2.62
- Hotkey **R** ready-press + `ReadyCheckPacket`
- `PvpReadyHud` overlay showing "X/Y ready" with team dots
- Graphical minimap preview (vs text-summary)
- Per-team starting items, custom team colors

---

## [0.2.60] - 2026-06-02 — Final pre-3.0 inline pass + spawn-context clarity

Closes all 12 audit-identified blockers (B1-B12) so that v0.3.0 can safely
delete `LocationEditorScreen.java` and `PvpLocationEditorScreen.java`.

### Added — Spawn-context hint
PvP mode in General tab now shows a §8gray hint§r under the Player Spawn
section: *"Used as fallback only. PvP players spawn at team points configured
in §eGameplay → Team spawn points§r."* Removes the long-standing confusion
between Player Spawn (PvE) and Team Spawn Points (PvP).

### Added — 12 inline-edit blockers

| ID | Field | Tab | Type |
|----|-------|-----|------|
| B1 | `victoryScreenEnabled` | General | toggle |
| B2 | `shopMode` GLOBAL/POINT | Economy | cycle |
| B3 | `portalEnabled` (master) | Area | toggle (gates B4-B5) |
| B4 | `portalPenaltyWave` | Area | ± control pair |
| B5 | `portalDisappearsOnComplete` | Area | toggle |
| B6 | `boundaryParticlesEnabled` | Area | toggle |
| B7 | `boundaryDamagePerSec` | Area | EditBox (only when consequence=DAMAGE) |
| B8 | `zoneParticle{Type,Count,Speed,Interval}` | Area | 4 EditBoxes (new "Zone particles" section) |
| B9 | `zoneActivationTimeSec`, `zoneOpenAfterStartSec` | Area | 2 EditBoxes (gated by auto-zone on) |
| B10 | `zoneUsesCustomCenter` + `zoneCenter` | Area | toggle + CoordinatePicker |
| B11 | InfoPanel `textScale` + `hasShadow` | Visual | EditBox + toggle |
| B12 | InfoPanel `mobSpawnPanelEnabled` | Visual | toggle |

All EditBoxes flushed via existing `flushEditBoxes()` mechanism. New refs added
to `resetEditBoxRefs()` to avoid cross-tab pollution.

### Translations
21 new `editor2.*` keys × 8 langs = **168 strings**. Total `editor2.*` key
count: 135 per lang. Coverage audit: all 135 source-referenced keys present in
all 8 lang files.

### Status: ready for v0.3.0 legacy removal

Every `location.set*` / `infoPanel.set*` call from both legacy editors now has
an inline equivalent in `UniversalLocationEditor.java`. After smoke-test
verification on user host:
- v0.3.0 will delete both legacy editor classes (~3026 LOC)
- Remove `editLocationLegacy()` + `📜` button from `AdminMenuScreen`
- Remove `wavedefense.tooltip.edit_location_legacy` lang key × 8 langs

### Files changed
- `gui/universal/UniversalLocationEditor.java` — B1-B12 inline + spawn-context hint
- 8 × lang files (+21 keys each)
- `gradle.properties`, `README.md` — version 0.2.60

---

## [0.2.59] - 2026-06-02 — Complete inline migration (legacy-removal prep)

Closes the remaining ~40% of fields that still required `📜 Legacy` round-trips
after v0.2.58. Every routine workflow is now reachable from the unified editor.
This is the **last release before legacy editor removal** in v0.3.0.

### Added — Phase A: PvP per-sub-mode inline rules

The PvP Gameplay tab no longer shows a read-only summary that forces admins to
deep-link. All per-sub-mode fields are now editable inline via a new
`initPvpSubModeRules(int, int, int, PvpMode)` dispatch method:

| Sub-mode | Inline fields |
|----------|---------------|
| **STANDARD** | total rounds, BUY time, round start delay, round-start/win/lose points, round time limit |
| **DEATHMATCH** | kills to win, match time limit, DM spawn mode cycle (TEAM/RANDOM/SMART) |
| **BATTLE_ROYALE** | border radius, shrink interval, shrink amount, initial wait, final radius, particle ID + count, damage toggle + amount |
| **CAPTURE_THE_POINT** | score-to-win, score-per-sec, win-mode toggle, round duration, speed multiplier, capture-all-win |
| **KING_OF_THE_HILL** | score-to-win, score-per-sec, win-mode toggle, round duration, hold mode + duration + reset-on-loss |

### Added — Phase B: PvE Area heavy fields inline

- **Boundary particles**: ID (EditBox), count, height — previously read-only
- **Portal timers**: open-after-start, penalty timer, respawn timer
- **Auto-activate zone**: enable toggle + radius EditBox + entry-position
  `CoordinatePickerWidget`
- **Behaviour timers (General tab)**: leave timer, victory linger, re-entry
  cooldown — three EditBoxes in a new "Timers" section

### Added — Phase C: Standalone-screen openers

- **Capture points editor** — button in PvP Gameplay tab when sub-mode is
  CTP or KOTH, opens existing `CapturePointEditorScreen`. Warning chip shows
  point count and ⚠ when zero.
- **Starting items editor** — button in PvE Economy tab, opens existing
  `StartingItemsScreen` with current count display.

### Added — Phase D: PvP team-spawn inline list

Replaces the "Spawn points: N" stat in the legacy editor. New section in PvP
Gameplay tab:
- Paginated list (5 visible at once, ▲▼ scroll buttons)
- Each row: team name + coords + radius display, ✎ edit, ✕ delete
- **+ Add** opens an inline form with team name EditBox +
  `CoordinatePickerWidget` (`withRadius=true` for scatter radius) + Save/Cancel
- Live ⚠ warning when no spawns configured (PvP requires ≥2 teams)

### Refactor — `flushEditBoxes()` + `flushInt()` helper

The flush method grew to ~35 fields. Compressed the repetitive
`if (box != null) try { setter.accept(Integer.parseInt(box.getValue().trim())); }
catch …` pattern into a one-line `flushInt(box, setter)` call via
`IntConsumer`. CtP/KotH objective fields route via `isCtp` branch (same setter
shape for both modes). All 35 fields covered; `resetEditBoxRefs()` nulls them
all at start of `init()`.

### Translations

44 new `editor2.*` keys × 8 languages = **352 strings**. Total `editor2.*`
key count is now 124 per language. Lang coverage audit: all 110 source-referenced
keys present in all 8 lang files.

### Files changed

- `gui/universal/UniversalLocationEditor.java` — Phase A (`initPvpSubModeRules`
  + per-mode field blocks), Phase B (Area heavy + General timers), Phase C
  (openers), Phase D (spawn list + edit form + `saveSpawnForm`), 25+ new
  EditBox refs, `labelledIntRow` and `flushInt` helpers
- 8 × lang files — +44 keys each
- `gradle.properties`, `README.md` — 0.2.59

### Pre-test quick wins (still 0.2.59)

Four small fixes added before user smoke-test:

1. **QW1 — Spawn-edit form leak fixed.** Switching tab while editing a PvP spawn
   used to leave `spawnEditing=true`; returning to Gameplay tab resurrected an
   empty form. Tab-button handler now resets `spawnEditing` / `spawnEditingIndex`.
2. **QW2 — `saveSpawnForm` smart fallback.** Editing an existing spawn but not
   touching coords now keeps the original position (was: silently overwrote with
   player position). Creating a new spawn still falls back to player position
   when picker is empty.
3. **QW3 — PvP economy points fully inline.** Kill points, death penalty,
   round-win/lose/start points moved from read-only summary + legacy deep-link
   to live EditBoxes (Standard mode shows all 5; DM shows just kill/death since
   it has no rounds). The last "open legacy ▶" hint in PvP Economy removed.
5 new EditBox refs + `flushEditBoxes` entries.
4. **QW4 — Boundary consequence cycle inline.** Added a 4-state cycle button
   to Area tab boundary section (TIMER_SURRENDER / DAMAGE / TELEPORT_BACK /
   INSTANT_SURRENDER). 5 new lang keys × 8 languages.

After QW3+QW4, the only legacy-only workflow remaining is the InfoPanel
deep-config link in Visual tab (kept for one release as a safety net — all 9
flags are already inline; the link only opens for users who want the floating
panel offset edit). v0.3.0 will remove legacy entirely.

### Status: legacy editor removal scheduled for v0.3.0

After this release, the only remaining legacy-specific workflow is the
InfoPanel deep-config link in Visual tab (kept as safety net). All other
PvE/PvP editor functionality is now reachable inline. v0.3.0 will delete
`LocationEditorScreen.java` and `PvpLocationEditorScreen.java`, and remove the
`📜 Legacy` button from `AdminMenuScreen`.

---

## [0.2.58] - 2026-06-02 — Editor bugfix + inline-edit completion

Closes the two critical correctness bugs uncovered in the 0.2.57 audit and
moves the most-touched legacy-only fields into the new editor inline.

### Fixed — Critical

- **`CoordinatePickerWidget` no longer zeros sibling coords** while the admin
  is mid-typing. Root cause: `setResponder(s -> fire())` parsed empty fields
  as `0`, then a sibling toggle's `rebuildWidgets()` rebuilt the picker from
  that fabricated state, silently clobbering the user's typed Y/Z. Fix:
  `getValue()` returns a non-null `BlockPos` only when **all 3** fields parse
  cleanly. Partial-typing states are silently swallowed (no `onChange` fire).
  A new `Result.cleared` sentinel distinguishes "user emptied all 3 fields
  (clear value)" from "user is mid-typing (do nothing)".

- **Cancel now actually cancels.** Previously, every mutation went straight
  to the `Location` instance in `ClientLocationManager`. Clicking Cancel just
  closed the screen — local mutations persisted until the next server sync.
  Fix: the editor constructor now deep-copies the location via NBT roundtrip
  (`Location.load(original.save())`); all tab callbacks mutate the working
  copy; Save commits via `ClientLocationManager.updateSingleLocation()` +
  `UpdateLocationPacket`; Cancel discards the working copy.

### Added — Mode-switch warning

General tab now shows a yellow ⚠ hint under the PvE/PvP buttons:
*"Switching mode hides current-mode data; press Cancel to discard."* Made safe
by the Cancel deep-copy fix above — toggling mode without Save is now
reversible.

### Added — Inline editable fields (no more legacy-bouncing for routine work)

| Tab | Field | Notes |
|-----|-------|-------|
| Area    | Boundary radius (EditBox) | clamps to [1, 9999] via setter |
| Visual  | All 8 InfoPanel granular flags | 2×4 grid of toggles |
| Visual  | Spawn panel offset Y (EditBox) | clamps to [0.5, 10.0] via setter |
| Gameplay PvP | Min players (EditBox) + Friendly fire + Auto-balance + Wait-effect toggles | always editable, applies to every PvP sub-mode |
| Compat  | MnS Level + XP bonus + 5 resistance values | 7 small EditBoxes in a 2-col grid; only shown when Mine and Slash is loaded |

All EditBox values are flushed to the working Location via a new
`flushEditBoxes()` method called from both `save()` and the overridden
`rebuildWidgets()` — typed-but-unsaved values now survive sibling toggle clicks
(same pattern as `PvpLocationEditorScreen.saveAllRules()`).

### Translations

22 new `editor2.*` keys × 8 languages = **176 strings**. Covers mode-switch
warning, all visual flags, gameplay toggles, MnS labels.

### Tech debt swept

3 stale persistent tasks were re-inspected and found **invalid**:
1. "Hardcode UA at `WaveTriggerEditorScreen.java:260`" — that line is an emoji
   ternary (`"⏱" : "⚔" : "🏆"`), no Ukrainian.
2. "Dead field `LocationSession.timerCustom`" — `grep` finds no such field
   anywhere in `src/main/java/com/wavedefense`.
3. "Dead fallback in `LocationEditorScreen.getLocTip()`" — that method is
   actively called at line 694 and contains live tooltip routing.

Real tech-debt fixed this release: stale comment in `AdminMenuScreen.java`
line 116 updated from "Sprint 1+" → "Sprint 2, v0.2.58+".

### Files changed

- `gui/widgets/CoordinatePickerWidget.java` — `getValue()` rewrite + `fire()` gate + `Result.cleared` sentinel
- `gui/universal/UniversalLocationEditor.java` — deep-copy in constructor, `rebuildWidgets` override, `flushEditBoxes`, mode-switch warning, B1-B4 inline edits, `toggleInfoPanelFlag` + `masEditRow` helpers, `resetEditBoxRefs` cleanup
- `gui/AdminMenuScreen.java` — comment update
- 8 × lang files (+22 keys each)
- `gradle.properties`, `README.md` — version 0.2.58

---

## [0.2.57] - 2026-06-02 — Editor widget integration + responsive layout (Sprint 2 finish)

Finishes Sprint 2's plan phases 6 and 8 that were still pending after 0.2.56:

### Changed — `CoordinatePickerWidget` everywhere in the new editor

All five coordinate fields in `UniversalLocationEditor` now use the reusable
`gui/widgets/CoordinatePickerWidget`:

| Tab | Field | Before | After |
|-----|-------|--------|-------|
| General | Player spawn      | "📌 Here" only | `[X][Y][Z] [📌] [🗑]` |
| General | Victory exit       | "📌 Here" + ✕  | `[X][Y][Z] [📌] [🗑]` |
| General | Surrender exit     | "📌 Here" + ✕  | `[X][Y][Z] [📌] [🗑]` |
| Area    | BBox corner 1      | "📌 Here" + ✕  | `[X][Y][Z] [📌] [🗑]` |
| Area    | BBox corner 2      | "📌 Here" + ✕  | `[X][Y][Z] [📌] [🗑]` |

Honours the user requirement *"всюди де координати потрібно аби було не лише
поточна позиція а і вручну вписать координати"* — every coord field now accepts
manual numeric entry alongside the position-pin shortcut. `withRadius=false` for
these fields since neither spawn, exits, nor bbox corners have a scatter radius
in the data model (the radius input remains available for the widget when used
elsewhere — e.g. mob spawn points, future shop points).

`ItemPickerWidget` and `ListTileView` were not integrated in this pass — the
new editor delegates all item-list and paginated-list editing to existing
purpose-built screens (`ShopEditorScreen`, `LootSpawnEditorScreen`, etc.). The
widgets remain in the foundation for future migrations.

### Changed — Responsive layout

All hard-coded `cx ± 200` widths replaced with formulas based on `this.width`:

```
PAD = 16; MAX_CONTENT_W = 520;
colW  = Math.min(MAX_CONTENT_W, this.width - 2 * PAD);
leftX = cx - colW / 2;
```

Save/Cancel buttons scale with `Math.min(120, (this.width - 80) / 2)` so they
fit on 640×480 displays at GUI scale 3 without overlapping the screen edges.

### Files changed

- `gui/universal/UniversalLocationEditor.java` — 5 coord clusters → widget, +
  responsive width fields (`PAD`, `MAX_CONTENT_W`, `colW`, `leftX`) computed in
  `init()`; tab methods read from instance fields.
- `gradle.properties`, `README.md` — version 0.2.57.

---

## [0.2.56] - 2026-06-02 — Unified Location Editor (Sprint 2)

### Added — `UniversalLocationEditor` is now the default location editor

Sprint 2 completes the Plan B unified-editor rebuild started in 0.2.55. A single
6-tab screen now covers **both PvE and PvP** locations:

| Tab | Contents |
|-----|----------|
| **🏷 General**  | name, mode toggle, player spawn, victory/surrender exits, `enforceGameMode`, `keepInventory`, hidden-from-menu |
| **⚔ Gameplay** | PvE: waves/mobs/trigger summary + buttons to `WaveConfigScreen` / `CompletionRewardScreen`. PvP: sub-mode selector (Standard / DM / BR / CtP / KotH) + per-mode rules summary + deep-link to legacy detailed rules |
| **🗺 Area**     | bbox corners with 📌/✕, minimap toggle, in-world outline toggle, boundary tracking + radius |
| **💰 Economy** | shop summary + opener, loot summary + opener, completion rewards opener (PvE), points summary (PvP/PvE) |
| **🎨 Visual**  | info panels (now available for **PvP too** — previously PvE-only), zone particles, BR border particles, HUD note |
| **📦 Compat**  | Import/Export opener, Mine and Slash overrides summary (when loaded), Tacz bulk-add opener (when loaded) |

### Changed — Admin menu button layout

- `✎` (green) — opens the new `UniversalLocationEditor` (was: old editor)
- `📜` (yellow) — opens the legacy editor (`LocationEditorScreen` for PvE,
  `PvpLocationEditorScreen` for PvP). Kept as a fallback for any workflow that
  hasn't been verified in the new editor yet.

### Deprecated

- `gui/LocationEditorScreen.java` — `@Deprecated(forRemoval=true, since="0.2.56")`
- `gui/PvpLocationEditorScreen.java` — `@Deprecated(forRemoval=true, since="0.2.56")`

Both classes will be removed in the next major version (0.3.x). For now they
remain reachable through the `📜` button and through the new editor's
"open legacy for advanced fields" deep-links inside Gameplay, Economy, Visual,
and Compat tabs.

### Translations

- **58 new `editor2.*` keys × 8 languages = 464 strings added** (en_us, uk_ua,
  de_de, fr_fr, es_es, pl_pl, pt_br, zh_cn). Covers all 6 tabs, all section
  headers, all status labels, mode names, and the `tooltip.edit_location_legacy`
  button hint.

### Architectural notes

- The new editor renders header + tab bar (static) + scrolled content + footer.
  Scissor clipping (`ScissorHelper`) keeps content from bleeding into header/footer.
- Static widgets (tabs, Save/Cancel) bypass scissor — always clickable, always visible.
- Content is rebuilt on tab switch (`rebuildWidgets()` in tab-button handlers).
- Deep-link to legacy editor for fields whose EditBox layouts are too complex
  to duplicate inline (PvP rules per sub-mode, boundary particle ID, MnS resists).
  This keeps the new editor maintainable while honouring the user's "повний
  функціонал доступний" rule.

---

## [0.2.55] - 2026-06-02 — UI redesign foundation (Sprint 1)

### Added — Reusable widgets for upcoming admin-UI refactor

The user requested a thorough overhaul of admin screens so every place that asks
for an item/coordinate/list looks and works the same. Instead of rewriting all
27 screens at once (high risk), this sprint introduces three **reusable widgets**
that future migrations will drop in:

#### `gui/widgets/ItemPickerWidget.java`
Composite for picking a single `ItemStack`. Includes:
- 16×16 icon preview with vanilla decoration overlay
- "Select item" button that opens the creative-tab `ItemSelectionScreen`
- "✋" hand button — copies the player's main-hand item
- "×" clear button
- "N" EditBox for stack count (1–64)

Drop into any screen with one line of `addToScreen(this::addRenderableWidget)`.
Replaces ~50 lines of duplicated boilerplate per item slot.

#### `gui/widgets/CoordinatePickerWidget.java`
Unified coordinate input combining all idioms used across the mod:
- 3× EditBox for manual X/Y/Z entry
- "📌" button — sets fields from the player's current block position
- Optional scatter-radius EditBox (`withRadius = true`)
- "🗑" clear button
- Returns a `Result(BlockPos pos, int radius)` tuple

Replaces the older `CoordinateInputField` (kept for backward-compat).

#### `gui/widgets/ListTileView.java`
Generic list/tile toggle for paginated lists. Caller provides:
- a `Renderer<T>` strategy with `renderRow()` and `renderTile()` methods
- the list of entries
- the area rectangle

Widget handles: mode toggle button, page calculation, scroll offset clamp.

### Roadmap for Sprint 2

Sprint 1 (this release) — foundation only, no screen migrations yet.
Sprint 2 will migrate one screen per release using these widgets so each change
is auditable in isolation. Migration priority is up to the user — most-painful
screens first.

### New lang keys (× 8 langs)
7 keys for picker/coord widget tooltips.

---

## [0.2.54.4] - 2026-06-02

### Added — In-world bbox outline (visible to ALL players, not just admin)

Previously the location bbox existed only as coordinates in the admin editor —
players had no way to see where the edges actually are in 3D. New
`BboxRenderer` emits `END_ROD` particles along the 4 horizontal top edges
of the bbox every second, but only for the segments within 64 blocks of
each player (cheap on large boxes).

New toggle in both PvE and PvP editors:
- `§a✓ In-world outline ON — particles show bbox edges to all players`
- `§7○ In-world outline OFF (admin-only via coords)`

Independent of the minimap toggle — admin can have one without the other.

---

## [0.2.54.3] - 2026-06-02

### Deep PvP audit — 2 real bugs, 5 false positives ruled out

Ran a thorough trace of the PvP state machine and player lifecycle. Most
flagged issues turned out to be either by-design behaviour (BR respawn as
spectator) or false alarms from misread code paths (the recurring "double
recordDeath" claim is wrong because EventHandler.onEntityDeath is `if/else`,
not sequential).

### Fixed — Mid-round join makes the joining player "invisible" to round-end (HIGH)

`PvpRoundManager.addPlayerToPvpLocation()` registered new players in `stats`
and assigned a team, but did NOT add them to `aliveThisRound`. Since
`PvpRoundState.checkRoundWinner()` only iterates `aliveThisRound`, a player
who joined mid-Standard-round was effectively a ghost to the win predicate:

- A and B fight. A dies. B remains, on Team Blue.
- C joins (Team Red, same as A) — registered but NOT in aliveThisRound.
- `checkRoundWinner()` sees only B (Blue) alive → declares Blue winner.
- C is alive on the field but the round ended without him counting.

Fix: when a player joins while phase is ACTIVE (and the mode is not BR —
BR's respawn-as-spectator is by design), add their UUID to `aliveThisRound`
so they're a real participant from the moment they spawn.

### Fixed — `rebalancePvpTeams` left scoreboard team out of sync (HIGH)

When auto-balance moved a player from the bigger team to the smaller, three
pieces of state were updated:
- `location.setPlayerTeam(toMove, smallTeam)`
- `stats[toMove].teamName = smallTeam`
- teleport to new spawn

But the **Minecraft scoreboard team** assignment (used by `HIDE_FOR_OTHER_TEAMS`
nametag visibility) was never refreshed. Visible symptoms:
- Old teammates still saw the player's nametag in green ally colour.
- New teammates saw the player as an enemy with hidden nametag (since they
  appeared on a different scoreboard team).

Fix: call `removeFromScoreboardTeam(sp)` + `assignScoreboardTeam(sp, locName,
smallTeam)` after the team change in `rebalancePvpTeams()`.

### Verified — not bugs

| Claim | Verdict |
|---|---|
| BR initial-wait ticker overshoots 0 | False — `seconds * 20` is always a multiple of 20 |
| Double `recordDeath` in BR/Standard | False — `EventHandler.onEntityDeath` is if/else, not sequential |
| Penalty leak on disconnect mid-combat | False — late kill events early-return when player is no longer in the location |
| `rebalancePvpTeams` target team has no spawn | False — team names come from spawn points, can't exist without one |
| Loot triggers cross-contaminate PvE/PvP | False — triggers are per-location, namespaces don't conflict |

---

## [0.2.54.2] - 2026-06-02

### Added — Bbox + minimap settings reachable from PvP editor

Previously the per-location bbox and tactical-minimap toggle lived only in the
PvE LocationEditor's Special tab. PvP-specific locations had no way to configure
them — admins had to switch the location to PvE, configure the bbox, switch
back. Added `initBboxMinimapSection` to `PvpLocationEditorScreen.initRulesTab`
so the same UI (Corner 1 / Corner 2 / Set Here / Clear / Minimap on-off / hint)
appears for every PvP mode (Standard / DM / BR / CtP / KotH).

### Improved — Tactical minimap adapts to screen resolution

`MinimapRenderer` previously rendered a fixed 96×96 px square — too small on
4K monitors with low GUI scale, almost full screen on 720p with large GUI scale.
Replaced with `min(width, height) / 6` clamped to `[72, 160] px` — the minimap
stays at a reasonable proportion of the viewport on every common resolution
and GUI-scale combination.

### Fixed — 188 Ukrainian translations missing (UI was half English)

The UA client showed "half English, half Ukrainian" because 188 lang keys
in `uk_ua.json` still held their English values from when the keys were
first added but were never re-translated. Hand-translated all of them across
admin feedback messages (auto.*), editor tabs, shop UI, PvP team-select,
imports/exports, and section headers.

After this pass `uk_ua.json` has 9 remaining English-looking values —
all proper nouns (Mine and Slash, Tacz, Battle Royale) or coordinate
format strings (`§7#%d: §fX:%d Y:%d Z:%d`) that don't translate.

### Added — Diagnostic logging for PvP match end

`endRound()` and `endPvpMatch()` now log a clear reason to the server log
when a round or match ends:
- `[WD/PvP] endRound @ '<loc>' — round N/M, winner=<team|(draw)>, teamWins={…}`
- `[WD/PvP] endPvpMatch @ '<loc>' — reason: <DM kill target reached |
  all rounds played (N/M)> — teamWins={…}`

Also added a warning when an admin creates a Standard-mode location with
`totalRounds = 1` (an easy footgun — the match ends after a single round,
so a draw kicks everyone out immediately). The log line tells the admin
to set `totalRounds >= 3` for typical play.

These diagnostics help debug the "we just joined PvP, BUY phase ended,
suddenly we're out of the location" complaints by surfacing the actual
exit reason instead of leaving admins guessing.

---

## [0.2.54.1] - 2026-06-02 (hotfix)

### Fixed — Tacz bulk-add: capture all gun variants, not just defaults

`TaczCompat.discoverGuns()` deduplicated by `gunId` alone, which collapsed
every NBT variant the gunpack author placed in the creative tab (a gun with
default attachments vs. the same gun pre-fit with a scope, suppressor, paint
job, etc.) into a single "default" entry. Bulk-add therefore lost the
pre-built loadouts and the admin saw only one item per gun.

Dedupe key changed from `gunId` to a full stable-NBT key (item id +
NBT compound minus the cosmetic `display`/`Damage`/`RepairCost` fields),
mirroring the dedupe `ItemSelectionScreen` uses for the general picker.
Every visually-distinct stack from the Tacz creative tabs now becomes a
separate `TaczGunEntry` and a separate shop item.

### Fixed — Tactical minimap facing arrow direction

`MinimapRenderer.render()` applied three sign inversions to the yaw vector
when computing the facing-arrow tip, leaving the arrow rotated by 90°.
Replaced with the correct Minecraft → screen transform:
`dx = -sin(yaw)`, `dz = cos(yaw)`, then map straight to screen `+x` / `+y`.

### Audit
Ran a thorough audit of 0.2.54 changes. Apart from the two fixes above:
- Server-side stat double-count claims were false positives —
  `EventHandler.onEntityDeath` is `if/else` (PvP-kill **or** env-death,
  never both).
- `BulkAddShopItemsPacket` handler / chunking math verified correct.
- BBox NBT backward-compat: old saves missing `bboxMin`/`bboxMax` keys
  load as `null` and cleanly disable the minimap.
- `SyncTeammatesPacket.PlayerEntry` overload pair is unambiguous —
  no positional-arg confusion at any call site.

---

## [0.2.54] - 2026-06-02

### Fixed — Tacz bulk-add crash with 107+ guns (CRITICAL)

`TaczBulkAddScreen.addCategory()` previously appended every gun to the location's
`shopItems` list locally and then sent **one** `UpdateLocationPacket` containing
the entire serialised location. With 100+ Tacz guns (vanilla + datapacks),
the NBT payload exceeds Forge's safe channel size and the client disconnects
on send (or the server fails to decode and drops the player).

**New packet** `BulkAddShopItemsPacket` (C→S) carries only:
- the target location name
- a batch of `ShopItem` entries (CompoundTag list)

Server appends, saves once, and broadcasts a single `SyncShopPacket` to players
currently inside the location. Permission check requires level 2+.

**Chunking**: `TaczBulkAddScreen` now splits the gun list into batches of 25
items per packet (~6-8 KB each). Even a 500-gun datapack now adds in 20 small
packets instead of one ~250 KB monster, eliminating the crash and giving
realistic progress on slow connections.

### Added — Optional location bbox + tactical PvP minimap

`Location` gains 3 optional fields:
- `bboxMin` / `bboxMax` — two corner `BlockPos` defining an axis-aligned cube
- `minimapEnabled` — toggle for the tactical HUD

A new section "🗺 Location BBox & Minimap" appears at the top of the
LocationEditor Special tab with:
- "Here" buttons for each corner (uses player's current position)
- "✕" clear buttons
- A minimap on/off toggle (disabled until both corners are set)

When `minimapEnabled` is true AND both corners are set AND the player is in a
PvP match, a new `MinimapRenderer` draws a top-down tactical map in the
bottom-left of the HUD (96×96 px):

- BBox region scaled to fit, with 3×3 grid lines
- Dots for every teammate (data from existing `ClientTeammatesManager`)
- Local player marker (bright green) + facing-direction indicator
- Dead teammates shown in grey
- **Enemies are NOT shown** by design — no wallhack

### Teammate position sync

`SyncTeammatesPacket.PlayerEntry` extended with `(double x, y, z, float yaw)`.
`WaveManager.syncTeammates()` populates positions; `ClientTeammatesManager`
stores them for the minimap. Existing per-second sync (added in 0.2.53.7)
keeps dots moving in real time.

### New files
- `network/packets/BulkAddShopItemsPacket.java`
- `gui/MinimapRenderer.java`

### New translation keys (× 8 langs)
`section.bbox`, `bbox.corner1_unset` / `corner2_unset`, `bbox.set_here`,
`bbox.minimap_on` / `minimap_off`, `bbox.hint` (7 keys total).

---

## [0.2.53.7] - 2026-06-02

### Fixed — Anti-cheat hitbox disable now covers PvE too

`ClientEventHandler.onClientTick()` previously only disabled the client-side
hitbox renderer when `data.isInPvp()` was true. PvE players could keep
F3+B turned on and see mob outlines through walls. Now the check is
`isInPvp() || isInWave()` — any active Wave Defense session forces hitbox
rendering off.

### Fixed — Teammate HUD HP bars not updating in real time

`syncTeammates()` was called only at join / leave / death. While a teammate
took damage between deaths the HUD bar stayed full. Added a 20-tick periodic
sync in `WaveManager.onServerTick()` so each active location pushes a fresh
`SyncTeammatesPacket` to every player once per second.

### Fixed — BR border damage during initial wait phase

`BattleRoyaleManager.tick()` was applying border damage to players outside
the (still-static) radius even during the configured "initial wait" phase —
i.e. before the border started shrinking. Damage now applies only after the
`initialWaitTicker` reaches zero, giving players the intended grace period
to spread out.

### Added — Tile mode in admin shop editor

`ShopEditorScreen` (global shop view) gains a `tileMode` toggle next to the
"Add item" / "Tacz bulk" buttons, mirroring the `PlayerShopScreen` UX.
Tile mode shows shop items as 112×84 cards with the primary icon, name,
buy/sell prices, availability trigger badge, plus Edit / Delete buttons
inside each tile. List mode (default) is unchanged.

Reuses the existing `wavedefense.shop.view_tiles` / `wavedefense.shop.view_list`
translation keys.

---

## [0.2.53.6] - 2026-06-01 (hotfix)

### Fixed — PvP Standard round ending instantly after BUY phase

Two players who picked the same team (either through spawn-point misconfiguration
or by both clicking the same team in the selector) would see the match end the
moment the BUY phase finished. `PvpRoundState.checkRoundWinner()` returns the
team-name once every alive player belongs to a single team — which is the right
answer *during a round in progress*, but is wrong *at round start* when there's
nobody on the other team yet.

`checkRoundWinner()` now additionally verifies that **at least 2 distinct teams
have ever been registered in the match** before declaring a winner. If only one
team is represented (degenerate setup or opponents not yet joined), it returns
`null` instead.

Side effects:
- BR/DM/CtP/KotH untouched — they use their own win predicates (`checkBrWinner`,
  `checkDmWinner`, `CapturePointManager`).
- Standard matches with `pvpRoundTimeLimitSec > 0` will still end on timeout if
  opponents never join (handled by the existing time-limit logic).
- `startActiveRound()` now broadcasts a yellow `⚠` warning if the round starts
  with only one team represented, plus logs to the server log so the admin
  immediately understands the configuration issue.

### Fixed — IndexOutOfBoundsException on PvP join with stale spawnIndex

`PvpRoundManager.addPlayerToPvpLocation()` did `location.getPvpSpawnPoints().get(spawnIndex)`
with no bounds check. A stale `TeleportPacket` (admin deleted a spawn point
between team selection and join) or a malicious client could throw IOOBE,
leaving the player half-joined (`playerBackups` populated, no session).

Now:
- Empty spawn-points list → join rejected with `wavedefense.msg.pvp_no_spawn_points`
  message, backup cleared.
- Out-of-range index → clamped to 0 with a server log warning.

### New lang keys (× 8 languages)
`wavedefense.msg.pvp_no_spawn_points`, `wavedefense.msg.pvp_insufficient_teams`

---

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
