# Wave Defence — 1.16.5 backport status

**As of:** 2026-06-04
**Source:** Wave Defense 0.2.65 (Minecraft 1.20.1 / Forge 47.x / Java 17)
**Target:** Wave Defence (Minecraft 1.16.5 / Forge 36.x / Java 8)

## Headline numbers

| Metric | Value |
|---|---|
| Files mechanically ported | **154 / 155** planned (99.4%) |
| LOC mechanically ported   | **32 089 / 32 288** planned (99.4%) |
| LOC vs full 1.20.1 source | 32 089 / 38 549 = **83.2%** |

The 16.8% LOC gap vs full source is **intentionally excluded** scope
(see "Out of scope" below).

## What "ported" means here

This milestone is a **structural file-copy**: every in-scope `.java` file from
the 1.20.1 source tree has a counterpart in `1.16.5/Wave Defence/src/main/java/`
with mechanical API substitutions applied:

- Package: `com.wavedefense.*` (unchanged)
- modId: `wave_defence` (changed from `wavedefense`)
- NBT: `CompoundTag` → `CompoundNBT`, `ListTag` → `ListNBT`, etc.
- Text: `Component.literal/translatable` → `StringTextComponent` / `TranslationTextComponent`
- GUI: `GuiGraphics` → `MatrixStack` + `AbstractGui` statics
- Network: `net.minecraftforge.network.*` → `net.minecraftforge.fml.network.*`
- Java 14+ records → plain final classes with accessor methods
- Java 14+ pattern matching `instanceof X x` → classic cast
- Switch expressions → classic switch statements
- `Map.of` / `List.of` → `Collections.singletonMap` / `Arrays.asList`
- `var` → explicit types

## What "ported" does NOT mean

**The ported tree does not yet compile clean.** Expected remaining work:

1. **Method signature differences** — `mob.finalizeSpawn`, `world.getCurrentDifficultyAt`,
   `is.save(new CompoundNBT())` arg requirement, etc.
2. **Tooltip API** — 1.20.1 `widget.setTooltip(Tooltip.create(...))` has no
   1.16.5 equivalent; needs `Screen.renderTooltip(MatrixStack, …)` override per screen.
3. **Creative tab API** — `BuildCreativeModeTabContentsEvent` (1.20.1) vs
   `ItemGroup.getDisplayItems()` (1.16.5) — completely different.
4. **HUD event** — `RenderGuiOverlayEvent.Post` → `RenderGameOverlayEvent.Post`.
5. **KeyBindings registration** — `RegisterKeyMappingsEvent` →
   `ClientRegistry.registerKeyBinding`.
6. **EditBox API gaps** — `setBounds()` doesn't exist; handled at port time
   for `CoordinatePickerWidget`, but others may surface.
7. **Legacy editor references** — `AdminMenuScreen` and `UniversalLocationEditor`
   still reference `editLocationLegacy()` for the `📜 Legacy` button. Those call
   sites need to be commented out (5 lines) since `LocationEditorScreen.java` and
   `PvpLocationEditorScreen.java` are not ported.

These are the focus of subsequent phases (Phase 3/4/5 of the original port plan).

## Stubbed features (degraded on 1.16.5)

### `InfoPanelManager`

The InfoPanel feature renders floating text labels above PvE mob spawn points
and player areas using `Display.TextDisplay` entities. That entity type is a
**1.19.4+** feature with no equivalent in 1.16.5. The manager is **stubbed**
in this port: `tick()`, `removeInfoPanelEntities()`, `save()`, and `load()`
are all no-ops. A debug log line is emitted on first tick.

If the feature is needed on 1.16.5, a future revision can re-implement it
using invisible `ArmorStand` with custom name — single-line, no rich
formatting, ~150 LOC rewrite.

## Out of scope (intentionally not ported)

| Package / file | LOC | Reason |
|---|---|---|
| `compat/` (Tacz, MnS) | ~3 200 | Tacz/MnS don't exist or have radically different APIs for 1.16.5 |
| `backup/` | ~1 100 | Vanilla world-save covers basics; backup is niche |
| `monitor/` | ~1 400 | Telemetry feature is niche |
| `gui/AdminDebugHud.java` | ~250 | F4 debug HUD is brand-new polish (v0.2.65); add later |
| `gui/LocationEditorScreen.java` | 1 609 | Marked `@Deprecated(forRemoval)` since v0.2.56 |
| `gui/PvpLocationEditorScreen.java` | 1 417 | Marked `@Deprecated(forRemoval)` since v0.2.56 |
| `gui/TaczBulkAddScreen.java` | ~200 | Tacz compat |

## Three tiers of port quality

For honesty, the porting work has three distinct progress levels:

| Tier | Status | Meaning |
|---|---|---|
| File-copy % | **99.4%** | A `.java` file exists at the right path with mechanical API rewrites applied |
| Compile-ready % | **unknown — likely 5–30%** | The file actually compiles under Forge 36.x / Java 8 |
| Runtime-tested % | **0%** | Verified working in an actual 1.16.5 game session |

This document tracks the **file-copy** tier. Closing the compile-ready gap is
the next phase; runtime testing follows that.

## Next steps

1. `./gradlew compileJava` and triage compile errors in clusters
2. Pick off the systematic patterns (tooltip, creative tab, hud event)
3. Comment out `editLocationLegacy()` references (5 lines)
4. Hand-port remaining edge cases (method signatures, etc.)
5. First successful build → smoke test with `/wd list` and basic admin screen
