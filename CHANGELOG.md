# Wave Defence (1.16.5 port) — CHANGELOG

## [0.0.1] — Unreleased — Initial backport

### Added

- **Foundation port** of Wave Defense 0.2.65 (originally MC 1.20.1) to
  MC 1.16.5 / Forge 36.x / Java 8.
- **154 / 155 planned files** (99.4%) ported via mechanical translator
  (`port_to_1165.py`) from the 1.20.1 source tree.
- **32 089 / 32 288 planned LOC** (99.4%) carried over.
- Full package coverage:
  - `data/` — 22 classes (Location, NbtHelper, LocationSerializer,
    LocationManager, LeaderboardManager, ShopItem/Point, PvpSpawnPoint,
    PvpRoundState, WaveConfig/Mob/Trigger, etc.)
  - `network/` — 39 packets (PacketHandler + all client/server messages)
  - `wave/` — 17 files (WaveManager, PvpRoundManager with READY_CHECK phase,
    BrManager, PortalManager, ZoneActivationManager, MobSpawnManager,
    SessionManager, InfoPanelManager, etc.)
  - `events/` — 6 files (PlayerEventHandler, ClientEventHandler,
    PlayerRespawnHandler, KeyBindings, etc.)
  - `commands/` — full `WaveDefenseAdminCommands` (1578 LOC) + basic
    `WaveDefenseCommand`
  - `config/` — `WaveDefenseConfig` + `WaveGameRules`
  - `gui/` — 64 screens (UniversalLocationEditor, ShopEditor, WaveConfig,
    PlayerHUD, MinimapRenderer, PvpReadyHud, etc.)
- 8 language files copied verbatim from 1.20.1
  (`en_us`, `uk_ua`, `de_de`, `fr_fr`, `es_es`, `pl_pl`, `pt_br`, `zh_cn`).

### Translated automatically

- Java 14+ records → plain final classes with `name()`/`uuid()`/etc. accessors.
- `instanceof T t` pattern variable → classic `instanceof` + explicit cast.
- `switch (x) -> {...}` expressions → classic `switch (x) { case ...: return; }`.
- `Map.of(...)` / `List.of(...)` → `Collections.singletonMap(...)` / `Arrays.asList(...)`.
- `var` → explicit type.
- Text API: `Component.literal/translatable` → `StringTextComponent` /
  `TranslationTextComponent`.
- NBT API: `CompoundTag` → `CompoundNBT`, `ListTag` → `ListNBT`, etc.
- Forge network: `net.minecraftforge.network.*` → `net.minecraftforge.fml.network.*`.
- Entity: `ServerPlayer`/`Player`/`Mob` → `ServerPlayerEntity`/`PlayerEntity`/`MobEntity`.
- Forge constants: `net.minecraft.nbt.Tag` → `net.minecraftforge.common.util.Constants`.

### Intentionally excluded

- `compat/` (Tacz, MnS) — incompatible APIs on 1.16.5.
- `backup/` — niche feature; vanilla world-save covers basics.
- `monitor/` — telemetry, niche.
- `gui/AdminDebugHud.java` — v0.2.65 polish, add post-Phase-3.
- `gui/LocationEditorScreen.java` + `gui/PvpLocationEditorScreen.java` — already
  `@Deprecated(forRemoval)` in 1.20.1 source; superseded by
  `UniversalLocationEditor`.
- `gui/TaczBulkAddScreen.java` — Tacz compat.

### Known limitations

- **Not runtime-tested.** Compile-ready % is still in progress; see
  `PORT_STATUS.md` for the three-tier breakdown.
- Tooltip API in screens still uses 1.20.1 `widget.setTooltip(Tooltip.create(...))`
  pattern in some places — needs `Screen.renderTooltip()` override per screen.
- Creative tab API (`BuildCreativeModeTabContentsEvent`) — 1.16.5 uses
  `ItemGroup.getDisplayItems()`; needs hand-port in `CreativeTabHelper`.
- HUD overlay event still references `RenderGuiOverlayEvent` — needs
  `RenderGameOverlayEvent.Post` in 1.16.5.
- KeyBinding registration uses `RegisterKeyMappingsEvent` — needs
  `ClientRegistry.registerKeyBinding(...)` in 1.16.5.
- `editLocationLegacy()` references in `AdminMenuScreen` /
  `UniversalLocationEditor` need to be commented out (5 lines), since the two
  legacy editor classes are not ported.

### Build

```bash
cd "1.16.5/Wave Defence"
./gradlew build
```
