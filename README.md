# Wave Defence — Minecraft 1.16.5 backport

Port of the **Wave Defense** mod (originally for MC 1.20.1) to
**Minecraft 1.16.5 / Forge 36.x / Java 8**.

| Item | Value |
|---|---|
| Mod ID | `wave_defence` |
| Source version base | 1.20.1 — v0.2.65 |
| Backport version | 0.0.1 (pre-alpha) |
| Java | 8 |
| Forge | 36.x |
| Mappings | official 1.16.5 |

## Status

🚧 **Pre-alpha — not yet runtime-tested.**

See [`PORT_STATUS.md`](PORT_STATUS.md) for the honest three-tier breakdown
(file-copy / compile-ready / runtime-tested).

| Tier | Progress |
|---|---|
| File-copy | 99.4% (32 089 / 32 288 planned LOC) |
| Compile-ready | in progress |
| Runtime-tested | 0% |

## What's included

All in-scope packages: `data/`, `network/`, `wave/`, `events/`, `commands/`,
`config/`, `gui/` (64 screens). 8 language files copied verbatim from the
1.20.1 source (lang keys still use the `wavedefense.*` prefix even though the
modId is `wave_defence` — keys and assets path are independent in Minecraft).

## What's excluded

- `compat/` (Tacz, MnS) — those mods don't exist or have a very different API on 1.16.5
- `backup/` — niche, vanilla world-save covers basics
- `monitor/` — telemetry, niche
- `gui/AdminDebugHud.java` — brand-new polish (v0.2.65), add later
- `gui/LocationEditorScreen.java` + `PvpLocationEditorScreen.java` — already
  marked `@Deprecated(forRemoval)` in the 1.20.1 source since v0.2.56;
  superseded by `UniversalLocationEditor`
- `gui/TaczBulkAddScreen.java` — Tacz compat

## Build

```bash
cd "1.16.5/Wave Defence"
./gradlew build
```

The resulting jar will land in `build/libs/wave_defence-*.jar`.

## Differences from 1.20.1

API translations applied at port time:

| 1.20.1 | 1.16.5 |
|---|---|
| `CompoundTag` / `ListTag` | `CompoundNBT` / `ListNBT` |
| `Component.literal/translatable` | `StringTextComponent` / `TranslationTextComponent` |
| `GuiGraphics` | `MatrixStack` + `AbstractGui` statics |
| `ServerPlayer` | `ServerPlayerEntity` |
| `Player` | `PlayerEntity` |
| `Mob` | `MobEntity` |
| `KeyMapping` | `KeyBinding` |
| `net.minecraftforge.network.*` | `net.minecraftforge.fml.network.*` |
| records | plain final classes with accessor methods |
| switch expressions | classic switch + break/return |
| `instanceof T t` patterns | classic `instanceof` + cast |

## License

Same as the 1.20.1 parent project.
