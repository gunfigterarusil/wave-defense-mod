# Wave Defense v0.2.42 — Індекс моду

## Загальна інформація
- **Гра:** Minecraft 1.20.1 (Java Edition)
- **Платформа:** Forge 47.2.0+
- **Java:** 17
- **Mod ID:** `wavedefense`
- **Group:** `com.wavedefense`
- **Протокол:** v7
- **Ліцензія:** MIT
- **Збірка:** Gradle 8.8 + MinecraftForge Gradle 6.0.16+
- **Дані:** NBT формат (`data/wavedefense_locations.dat`)
- **Конфіг:** TOML (`config/wavedefense-common.toml`)

---

## Структура проєкту

```
src/main/java/com/wavedefense/
├── WaveDefenseMod.java          — Точка входу, ініціалізація
├── commands/                     — Серверні команди (1 клас)
├── config/                       — Конфігурація (2 класи)
├── data/                         — Моделі даних (19 класів)
├── events/                       — Обробники подій, HUD, клавіші (6 класів)
├── gui/                          — Екрани та клієнтські менеджери (50 класів)
├── network/                      — Пакети клієнт-сервер (33 класи)
└── wave/                         — Логіка хвиль та сесій (15 класів)

src/main/resources/
├── META-INF/mods.toml            — Маніфест Forge
├── pack.mcmeta                   — Ресурспак
└── assets/wavedefense/lang/
    ├── en_us.json                — English (224 ключів)
    ├── uk_ua.json                — Українська (224 ключів)
    ├── de_de.json                — Deutsch (224 ключів)
    ├── fr_fr.json                — Français (224 ключів)
    ├── es_es.json                — Español (224 ключів)
    ├── pl_pl.json                — Polski (224 ключів)
    ├── pt_br.json                — Português BR (224 ключів)
    └── zh_cn.json                — 简体中文 (224 ключів)
```

---

## Точка входу

| Файл | Опис |
|------|------|
| `WaveDefenseMod.java` | Реєстрація EventHandler, ClientEventHandler, LocationManager, WaveManager, PacketHandler. Ініціалізація серверної та клієнтської частини. |

---

## data/ — Моделі даних (19 класів)

### Локації та режими
| Файл | Опис |
|------|------|
| `Location.java` | Головна модель локації (~450 рядків): режим PvE/PvP, хвилі, магазин, лут, межі, портал, спавни, зона, команди. Поля package-private; `save()`/`load()` делегують до `LocationSerializer` |
| `LocationSerializer.java` | (**NEW v0.2.42**) Серіалізація/десеріалізація Location в NBT (~210 рядків). Статичні `save(Location)` / `load(CompoundTag)` |
| `NbtHelper.java` | (**NEW v0.2.42**) Утиліта NBT без instantiation: typed getters з default-ами, `BlockPos` через long, enum save/load, `saveList`/`loadList` |
| `LocationManager.java` | Завантаження/збереження локацій з NBT (`wavedefense_locations.dat`) |
| `LocationMode.java` | Enum: `PVE`, `PVP_STANDARD`, `PVP_DEATHMATCH`, `PVP_BATTLE_ROYALE` |

### Хвилі та моби
| Файл | Опис |
|------|------|
| `WaveConfig.java` | Конфігурація однієї хвилі: список мобів, таймер, тип тригера, cooldown, oneTimeOnly, spawnPos |
| `WaveMob.java` | Визначення моба у хвилі: тип (ResourceLocation), count, growthPerWave, spawnChance, pointsPerKill, спорядження (6 слотів), potion effects |
| `MobSpawnPoint.java` | Координати + радіус спавну мобів; `randomPos(Random)` для розсіювання |
| `WaveTrigger.java` | Enum тригерів подій (30+ типів): WAVE_*, TIMER_*, MOB_*, PLAYER_*, PvP_*, LOCATION_* |

### Магазин
| Файл | Опис |
|------|------|
| `ShopItem.java` | Товар: ціна (очки), список ItemStack (до 4), категорія, умови доступності (WaveTrigger) |
| `ShopPoint.java` | Точка магазину: BlockPos, назва, список товарів, радіус взаємодії |

### Лут
| Файл | Опис |
|------|------|
| `LootSpawn.java` | Точка луту: BlockPos, список ItemStack, shance%, тригери спрацювання |

### PvP
| Файл | Опис |
|------|------|
| `PvpRoundState.java` | Стан PvP-матчу: фаза (WAITING/BUY/COUNTDOWN/ACTIVE/ROUND_END_DELAY/ENDED), рахунок команд, таймер раунду, alive set |
| `PvpPlayerStats.java` | Статистика гравця: K/D/A, назва команди, ім'я |
| `PvpSpawnPoint.java` | Спавн-точка PvP команди: BlockPos, teamName, radius |

### Статистика та гравці
| Файл | Опис |
|------|------|
| `PlayerStats.java` | PvE статистика: kills, deaths, points, waves survived |
| `GameStats.java` | Загальна статистика локації: всього сесій, мобів вбито, хвиль пройдено |
| `PlayerBackup.java` | Бекап стану гравця: інвентар, HP, XP, позиція, gamemode — для відновлення при виході |
| `InfoPanelSettings.java` | Налаштування TextDisplay інфо-панелі над спавн-точкою |

---

## wave/ — Логіка хвиль (15 класів)

### Оркестратор
| Файл | Рядків | Опис |
|------|--------|------|
| `WaveManager.java` | ~1 660 | Головний оркестратор: `tick()`, делегування під-менеджерам, публічний API для Event handlers та пакетів |

### Стан та контекст
| Файл | Рядків | Опис |
|------|--------|------|
| `WaveContext.java` | ~115 | Per-player maps (`playerData`, `playerBackups`, `reEntryCooldowns`) + реєстр `sessions: Map<String, LocationSession>` |
| `LocationSession.java` | ~144 | Value object: весь стан однієї активної локації (хвиля, таймери, моби, portal, zone, PvP, info-panel). `dispose()` — атомарне очищення |
| `PlayerWaveData.java` | — | Дані хвилі для окремого гравця: currentWave, isInPvp, location, timerActive |

### Під-менеджери (витягнуті з WaveManager)
| Файл | Рядків | Опис |
|------|--------|------|
| `SessionManager.java` | ~230 | `addPlayer` (join + lobby timer), `surrender`, `triggerVictory`, `endSession` |
| `MobSpawnManager.java` | ~225 | `spawnWave`, `spawnAroundPos`, `applyMobEquipment`, `forceSetItemSlot`, `getRandomSpawnPoint` |
| `PortalManager.java` | ~390 | Спавн порталу, вхід, grace-period, penalty waves, частинки, `tick()` |
| `ZoneActivationManager.java` | ~250 | Зона авто-активації: countdown, collectPlayersInZone, частинки, `tick()` |
| `TriggerEvaluator.java` | ~585 | Оцінка тригерів: `tickWaveTriggers`, `checkWaveTriggerCondition`, cooldown по `LocationSession.triggerKey()`, `fireTriggerWave`, `fireLocationTrigger` |
| `InfoPanelManager.java` | ~405 | TextDisplay над спавн-точками: create, update (раз/сек), remove; ключі `"spawn"` / `"mob_N"` в `session.infoPanelEntityIds` |
| `PvpRoundManager.java` | ~720 | PvP state machine, авто-баланс команд, kill/death/surrender, Scoreboard команди для прихованн ніків |
| `BoundaryManager.java` | — | Перевірка меж: 4 режими (timer/damage/teleport/instant), border particles |
| `BattleRoyaleManager.java` | — | Стискання зони (1 блок/N сек), частинки кордону, урон за межами |

### Ключові поля LocationSession
```
currentWave, waveTimerTicks, startTimerMs, victoryLingerTicks
timer60, timer120, timer300, timerCustom
spawnedMobs (Set<UUID>), triggerMobs (Map<String,Set<UUID>>)
waveStartMobCount, mobsKilled, halfMobsTriggered
waveTriggerLastFired, waveTriggerWaveCounters, recentlyFiredTriggers
zoneCountdownTicker, zonePlayersInRange, zoneCountdownStartMs, zoneOpenUntilMs
portalPosition, portalPenaltyTimer, portalPenaltyMobs, portalEnteredPlayers
pvpState (PvpRoundState | null)
infoPanelEntityIds (Map<String,UUID>)
```

### triggerKey формат
`LocationSession.triggerKey(int wi)` → `"w" + wi`
Ключ зберігається в `session.waveTriggerLastFired` і `session.waveTriggerWaveCounters`.
**Без префіксу locName** — кожна сесія ізольована.

---

## events/ — Обробники подій (6 класів)

| Файл | Опис |
|------|------|
| `EventHandler.java` | Серверні події: `LivingDeathEvent` (моби → onMobKilled, гравці → onPvePlayerDeath/onPvpPlayerDeath), `PlayerLoggedOutEvent` (surrenderPlayer), `ServerTickEvent` (wm.tick), `LootingLevelEvent` |
| `ClientEventHandler.java` | Клієнтські події: реєстрація HudOverlay, `RenderNameTagEvent` (приховування ніків в PvP), `ScreenEvent` |
| `KeyBindings.java` | Клавіші: **V** (меню), **B** (магазин), **L** (вихід з локації без штрафу) |
| `HudOverlay.java` | Рендеринг HUD: таймер хвилі, очки, кількість мобів, PvP статус (фаза/раунд/рахунок), панель тімейтів |
| `HudMouseHandler.java` | Обробка кліків та скролу в HUD областях |
| `PlayerRespawnHandler.java` | Логіка смерті/респавну: відновлення інвентарю з бекапу, PvP-специфічний респавн |

### RenderNameTagEvent (ClientEventHandler) — логіка:
```
friendlyFire = true  → DENY всім (ніхто не бачить ніки)
та ж команда       → GREEN + ALLOW
інша команда + ACTIVE → DENY
BUY / WAITING фаза  → показати нормально
```

---

## gui/ — Екрани та клієнтські утиліти (50 класів)

### Головне меню
| Файл | Опис |
|------|------|
| `PlayerMenuScreen.java` | Меню гравця: список локацій, вхід, налаштування, статистика |
| `AdminMenuScreen.java` | Адмін-панель: управління локаціями, кнопки редагування/видалення |

### Редактор локацій
| Файл | Опис |
|------|------|
| `LocationEditorScreen.java` | Головний редактор (~1 371 рядків): вкладки Spec/PvE/PvP/Triggers/Loot |
| `LocationInfoScreen.java` | Інформаційний екран локації |
| `PvpLocationEditorScreen.java` | PvP налаштування: режими, команди, спавн-точки, Battle Royale/DM параметри |

### Редактор хвиль
| Файл | Опис |
|------|------|
| `WaveConfigScreen.java` | Список хвиль, таймер, кнопки add/edit/delete/export/import |
| `WaveActionsScreen.java` | Команди та ефекти при завершенні хвилі |
| `WaveMobsEditorScreen.java` | Список мобів у хвилі: add/edit/delete/reorder |
| `WaveMobEditScreen.java` | Редактор моба: тип, кількість, шанс, очки |
| `WaveMobSettingsScreen.java` | Детальні параметри: growthPerWave, spawnRadius, pointsPerKill |
| `MobEffectsEditorScreen.java` | Список potion effects для моба (format: `namespace:id:amplifier:duration`) |
| `WaveSpawnEditorScreen.java` | Точки спавну хвилі з координатами та радіусом |
| `WaveTriggerEditorScreen.java` | Тригер хвилі: тип, cooldown, oneTimeOnly, activateFromWave, andConditions |

### Магазин
| Файл | Опис |
|------|------|
| `ShopEditorScreen.java` | Редактор магазину (адмін): список товарів, режим (GLOBAL/POINT) |
| `ShopItemEditorScreen.java` | Редактор товару: ціна, до 4 ItemStack, категорія |
| `ShopPointEditorScreen.java` | Редактор точки магазину: назва, координати, список товарів |
| `ShopAvailabilityScreen.java` | Умови доступності товару (WaveTrigger-based) |
| `PlayerShopScreen.java` | Магазин для гравця: tabs по категоріях, купівля/продаж, перевірка очків |

### PvP екрани
| Файл | Опис |
|------|------|
| `PvpTeamSelectScreen.java` | Вибір команди: дедуплікація по `teamName` (LinkedHashMap), per-mode layout (BR/DM/Standard) |
| `PvpScoreboardScreen.java` | Таблиця рахунку PvP: K/D/A, команди, раунди |

### Імпорт/Експорт
| Файл | Опис |
|------|------|
| `ImportExportScreen.java` | Hub: вибір типу (location/shop/wave) та операції |
| `ShopImportScreen.java` | Імпорт магазину з `.nbt` файлу |
| `ShopImportTargetScreen.java` | Вибір цільової локації для імпорту |
| `ShopPointSelectExportScreen.java` | Вибір точки магазину для експорту |
| `WaveImportScreen.java` | Імпорт хвиль |
| `WaveImportTargetScreen.java` | Вибір цільової локації для імпорту хвиль |
| `WaveExportScreen.java` | Вибір хвиль для експорту |

### Нагороди та лут
| Файл | Опис |
|------|------|
| `LootSpawnEditorScreen.java` | Редактор точок луту: позиція, предмети, шанс, тригери |
| `RewardsConfigScreen.java` | Конфігурація нагород за завершення |
| `CompletionRewardScreen.java` | Список нагород: add/edit/delete |

### Налаштування та статистика
| Файл | Опис |
|------|------|
| `PlayerSettingsScreen.java` | Налаштування гравця: видимість HUD елементів, панель тімейтів |
| `StatsScreen.java` | Статистика: kills, deaths, waves, points (поточна сесія + всього) |
| `HudEditScreen.java` | Drag-and-drop редактор позицій HUD елементів; presets |
| `StartingItemsScreen.java` | Стартові предмети локації: add/remove ItemStack |

### Утиліти вибору
| Файл | Опис |
|------|------|
| `ItemSelectionScreen.java` | Пошук та вибір ItemStack з реєстру Minecraft |
| `MobTypeSelectionScreen.java` | Пошук та вибір типу моба (ResourceLocation) |
| `MobSelectionScreen.java` | Вибір конкретного моба з підказками |

### Клієнтські утиліти (не Screen-класи)
| Файл | Опис |
|------|------|
| `ScrollableScreen.java` | Базовий клас з scissor-рендерингом (3-pass: content → static header → static footer) |
| `ListEditorScreen.java` | (**NEW v0.2.42**) Абстрактний дженерик-клас для списків CRUD: `buildVisibleRows()`, `renderContentExtra()`, `getListSize()`, `addScrollButtons()`. Extends `ScrollableScreen` |
| `CoordinateInputField.java` | (**NEW v0.2.42**) Compound widget: 3 inactive label buttons + 3 EditBox для X/Y/Z координат. API: `setValue`, `getValue`, `isEmpty`, `setFromPlayer`, `addToScreen`, `getEndX` |
| `PlayerHUD.java` | Головний клас HUD: координує HudOverlay та HudLayout |
| `HudLayout.java` | Розташування HUD елементів: preset-и зберігають translation key |
| `ScissorHelper.java` | Утиліта обрізки: `begin(x,y,w,h)` / `end()` з стеком |
| `TooltipHelper.java` | Підказки: `Map<Button, String>` — незалежно від активної мови |
| `ClientLocationManager.java` | Кеш даних локацій на клієнті (оновлюється з `SyncLocationDataPacket`) |
| `ClientPlayerDataManager.java` | Поточні дані гравця: хвиля, очки, таймер, isInWave |
| `ClientPvpStateManager.java` | Стан PvP на клієнті: фаза, команда, статистика (для HUD і Scoreboard-екрану) |
| `ClientStatsManager.java` | Клієнтська статистика (K/D/A, waves) |
| `ClientTeammatesManager.java` | Дані тімейтів: UUID → name + HP для HUD-панелі |
| `ClientShopExportManager.java` | Менеджер UI-стану для Shop Export flow |
| `ClientWaveExportManager.java` | Менеджер UI-стану для Wave Export flow |

---

## network/ — Мережеві пакети (33 класи)

### Реєстрація
| Файл | Опис |
|------|------|
| `PacketHandler.java` | Реєстрація всіх 31 пакетів, `sendToServer()` / `sendToPlayer()` |

### Пакети локацій
| Файл | Напрямок | Опис |
|------|----------|------|
| `CreateLocationPacket.java` | C→S | Створення нової локації |
| `UpdateLocationPacket.java` | C→S | Збереження змін локації |
| `DeleteLocationPacket.java` | C→S | Видалення локації |
| `RequestLocationDataPacket.java` | C→S | Запит даних (відповідь — SyncLocationDataPacket) |
| `SyncLocationDataPacket.java` | S→C | Повна синхронізація даних локації |
| `ImportLocationPacket.java` | C→S | Імпорт локації з NBT |
| `ExportLocationPacket.java` | S→C | Відповідь з NBT-даними локації |

### Пакети гравців
| Файл | Напрямок | Опис |
|------|----------|------|
| `SyncPlayerDataPacket.java` | S→C | Хвиля, очки, таймер, isInWave, PvP-стан |
| `UpdatePlayerSettingsPacket.java` | C→S | Зміна налаштувань (HUD видимість) |
| `UpdatePointsPacket.java` | S→C | Оновлення балансу очок |
| `SyncTeammatesPacket.java` | S→C | UUID → name + HP тімейтів |
| `SyncStatsPacket.java` | S→C | K/D/A + waves статистика |
| `OpenMenuPacket.java` | S→C | Наказ відкрити екран (admin-triggered) |

### Пакети магазину
| Файл | Напрямок | Опис |
|------|----------|------|
| `SyncShopPacket.java` | S→C | Повний список товарів (при відкритті) |
| `PurchaseItemPacket.java` | C→S | Покупка: atomically captured shopPointRef + itemRef |
| `SellItemPacket.java` | C→S | Продаж предмету |
| `ImportShopPacket.java` | C→S | Імпорт магазину |
| `ExportShopPacket.java` | S→C | NBT-дані магазину |
| `RequestShopExportListPacket.java` | C→S | Запит списку файлів для імпорту |
| `ShopExportListPacket.java` | S→C | Список доступних .nbt файлів |

### Пакети хвиль
| Файл | Напрямок | Опис |
|------|----------|------|
| `ImportWavePacket.java` | C→S | Імпорт конфігурації хвилі |
| `ExportWavePacket.java` | S→C | NBT-дані конфігурації хвилі |
| `RequestWaveExportListPacket.java` | C→S | Запит списку хвиль для імпорту |
| `WaveExportListPacket.java` | S→C | Список доступних хвиль |
| `ExportListResponsePacket.java` | S→C | Загальна відповідь на запит списку |

### Пакети дій
| Файл | Напрямок | Опис |
|------|----------|------|
| `TeleportPacket.java` | C→S | Вхід гравця до локації (з вибором spawnIndex) |
| `AdminTeleportPacket.java` | C→S | Адмін-телепортація іншого гравця |
| `SurrenderPacket.java` | C→S | Здача / вихід з локації |
| `LeaveLocationPacket.java` | C→S | Вихід без штрафу (клавіша L) |

### Пакети PvP
| Файл | Напрямок | Опис |
|------|----------|------|
| `SyncPvpStatePacket.java` | S→C | Повна синхронізація PvP-стану (фаза, рахунок, гравці) |
| `ExitPvpPacket.java` | C→S | Запит виходу з PvP |

---

## commands/ — Команди (1 клас)

| Файл | Опис |
|------|------|
| `WaveDefenseCommand.java` | `/wavedefense` з підкомандами: `kick`, `tp`, `menu`, `reload`. Аліаси: `/wdm`, `/wdtp`, `/wdkick`, `/wdreload` |

---

## config/ — Конфігурація (2 класи)

| Файл | Опис |
|------|------|
| `WaveDefenseConfig.java` | TOML конфіг: всі налаштування моду (HUD, магазин, PvP, моби, debug, ліміти) |
| `WaveGameRules.java` | Кастомні game rules для світу |

### Ключові конфіг-опції
| Ключ | За замовчуванням | Опис |
|------|-----------------|------|
| `LOBBY_TIMER_SECONDS` | 30 | Очікування перед першою хвилею |
| `LOCATION_GAME_MODE` | `"survival"` | Gamemode в локаціях |
| `DEBUG_LOGGING_ENABLED` | `true` | Лог-вивід debug-повідомлень |
| `ADMIN_DEBUG_MESSAGES` | `true` | Чат debug-повідомлення для операторів |
| `MOBS_CAN_HAVE_EQUIPMENT` | `true` | Дозволити спорядження на мобах |
| `MOB_ARMOR_DROP_CHANCE` | `0.085` | Шанс дропу спорядження |
| `HIDE_ENEMY_NAMETAGS` | `true` | Приховувати ніки ворогів у PvP |

---

## Ігрові механіки

### PvE — Хвильовий захист

1. Гравець входить → `SessionManager.addPlayer` → `addPlayerToLocation`
2. Lobby timer: перший гравець запускає відлік (`sess.startTimerMs`)
3. Timer expire → `spawnWaveForLocation` → `MobSpawnManager.spawnWave`
4. Моби атакують гравців. Вбивство → `EventHandler.onMobKilled` → `onMobKilled(WaveManager)`
5. `onMobKilled` нараховує очки, перевіряє HALF_MOBS_DEAD, перевіряє `checkWaveComplete`
6. Всі моби вбиті → `onWaveComplete` → `TriggerEvaluator` → наступна хвиля або перемога
7. Перемога → `triggerVictory` → victory linger → `endSessionForLocation`

### PvP — State Machine

```
WAITING → (minPlayers reached) → BUY → (buyTime expired) → COUNTDOWN
→ (countdown expired) → ACTIVE → (round winner) → ROUND_END_DELAY
→ (totalRounds reached) → ENDED
```

Кожен перехід: `PvpRoundManager` → `LocationSession.pvpState`

### Тригери — формат cooldownKey
```java
LocationSession.triggerKey(waveIndex)  // "w0", "w1", ...
// зберігається в: session.waveTriggerLastFired.get("w0")
//                 session.waveTriggerWaveCounters.get("w0")
```

### Scoreboard команди PvP (v0.2.41+)
```
Назва команди: "wd_<locationName>_<teamName>"
Visibility: HIDE_FOR_OTHER_TEAMS
Lifecycle: assign on join → remove on leave/end → cleanupScoreboardTeams on session end
```

---

## Архітектура — зв'язки між класами

```
WaveDefenseMod
  ├── LocationManager       ← NBT завантаження/збереження Location
  ├── WaveManager           ← оркестратор
  │   ├── WaveContext       ← per-player maps + sessions registry
  │   ├── LocationSession   ← весь стан однієї активної локації
  │   ├── SessionManager    ← join/surrender/victory/endSession
  │   ├── MobSpawnManager   ← spawn + equipment
  │   ├── PortalManager     ← portal + penalty waves
  │   ├── ZoneActivationManager ← zone countdown + player collect
  │   ├── TriggerEvaluator  ← trigger eval + cooldown + fire
  │   ├── InfoPanelManager  ← TextDisplay lifecycle
  │   ├── PvpRoundManager   ← PvP state machine + Scoreboard teams
  │   ├── BoundaryManager   ← boundary 4-mode enforcement
  │   └── BattleRoyaleManager ← BR border shrink + damage
  ├── EventHandler          ← Forge events → WaveManager
  ├── PacketHandler         ← 31 пакет C↔S
  └── WaveDefenseCommand    ← /wavedefense dispatcher

ClientEventHandler
  ├── ClientLocationManager ← кеш локацій
  ├── ClientPlayerDataManager ← isInWave, currentWave, points
  ├── ClientPvpStateManager ← phase, team, scoreboard
  ├── ClientTeammatesManager ← teammate HP для HUD
  ├── HudOverlay / PlayerHUD ← рендеринг
  └── KeyBindings           ← V/B/L → відкриття екранів
```

---

## Швидкий пошук по задачах

| Задача | Де шукати |
|--------|-----------|
| Додати новий тип моба | `data/WaveMob.java`, `wave/MobSpawnManager.java` |
| Змінити логіку хвиль | `wave/WaveManager.java`, `wave/TriggerEvaluator.java`, `data/WaveConfig.java` |
| Додати новий тригер | `data/WaveTrigger.java`, `events/EventHandler.java`, `wave/TriggerEvaluator.java` |
| Змінити магазин | `data/ShopItem.java`, `gui/PlayerShopScreen.java`, `network/packets/PurchaseItemPacket.java` |
| Додати мережевий пакет | `network/PacketHandler.java`, `network/packets/` |
| Змінити HUD | `events/HudOverlay.java`, `gui/PlayerHUD.java`, `gui/HudLayout.java` |
| Новий PvP режим | `data/LocationMode.java`, `data/PvpRoundState.java`, `wave/PvpRoundManager.java` |
| Змінити межі | `wave/BoundaryManager.java`, `wave/BattleRoyaleManager.java` |
| Додати GUI екран | `gui/` + реєстрація в батьківському екрані або через `OpenMenuPacket` |
| Змінити команду | `commands/WaveDefenseCommand.java` |
| Додати конфіг опцію | `config/WaveDefenseConfig.java` |
| Змінити локалізацію | `resources/assets/wavedefense/lang/en_us.json` + усі 7 мов |
| Імпорт/Експорт | `gui/ImportExportScreen.java`, відповідні пакети |
| Збереження даних | `data/LocationManager.java`, `data/LocationSerializer.java` (NBT), `data/NbtHelper.java` (утиліта) |
| Стан сесії локації | `wave/LocationSession.java` (замінює ~30 Maps у WaveContext) |
| Portal логіка | `wave/PortalManager.java` |
| Zone activation | `wave/ZoneActivationManager.java` |
| Info panels | `wave/InfoPanelManager.java` |
| PvP раунди | `wave/PvpRoundManager.java` |
| Scoreboard / ніки | `wave/PvpRoundManager.java` (assignScoreboardTeam / cleanupScoreboardTeams) |
| Приховування ніків (client) | `events/ClientEventHandler.java` (RenderNameTagEvent) |
