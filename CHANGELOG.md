# Changelog

## [0.2.38] - 2026-03-16

### Виправлено — scissor у прокручуваних меню
Всі екрани що мають прокрутку (scroll) тепер правильно ховають прокручений контент
за статичними елементами (заголовок, таби, нижні кнопки) при скролінгу:

- **`LocationEditorScreen`** (⚙ Спец) — введено `staticWidgets` set (`IdentityHashMap`-backed);
  хелпер `addStatic()` позначає елементи; render використовує `staticWidgets.contains()` замість Y-порівнянь
- **`PvpLocationEditorScreen`** (🎮 Режим+Правила) — аналогічний `staticWidgets` підхід;
  таби (Y=25) і нижні кнопки позначені статичними; при скролі правил вони не перекриваються
- **`WaveConfigScreen`** (список хвиль) — render переписано на 3-pass:
  (1) контент через scissor, (2) header поверх, (3) footer поверх
- **`WaveMobsEditorScreen`** (список мобів) — аналогічний 3-pass render

### Виправлено — меню налаштування PvP
- При натисканні кнопки "PvP" у `LocationEditorScreen` — одразу відкривається
  `PvpLocationEditorScreen` без проміжного екрану з кнопкою
- Вкладки "⚙ Правила" та "🎮 Режим" об'єднані в єдину "🎮 Режим+Правила":
  кожен підрежим (Стандарт / Deathmatch / Королівська Битва) показує власні налаштування

## [0.2.32] - 2026-03-15

### Виправлено (помилки компіляції)
- **`tickInfoPanels`** — `List<BlockPos> mobSpawns = loc.getMobSpawns()` спричиняло помилку типу, оскільки `getMobSpawns()` повертає `List<MobSpawnPoint>`. Виправлено на `List<MobSpawnPoint>` з `.getPos()` при передачі
- **`getRandomSpawnPoint`** — повертав `MobSpawnPoint` замість `BlockPos`. Додано `.getPos()`
- **`DustParticleOptions`** у `BoundaryManager` — `Vector3f.ZERO.add(...)` замінено на `new Vector3f(1f,0f,0f)`
- Видалено невикористаний імпорт `DamageSource` у `BattleRoyaleManager`

### Виправлено (логіка)
- **Info-панелі на точках спавну мобів** — тепер правильно відображаються для всіх точок після рефактору `MobSpawnPoint`
- **Тригер `PLAYER_DEATH`** додано до PvE-смерті (раніше спрацьовував тільки у PvP)

### Додано
- **Налаштування кордону локації** — повний UI у "Спец. налаштування":
  - 4 режими наслідків: Таймер→здача / Постійна шкода (HP/сек) / Телепорт назад / Миттєва здача
  - Частинки кордону: тип (registry id), кількість, висота кільця
  - 5 пресетів частинок: smoke / flame / portal / snowflake / enchant
- **Радіус розкиду мобів** — кнопка `rN` у списку точок спавну циклічно перемикає 0→3→5→10→15→20

### Змінено
- `BoundaryManager` — повна переробка: підтримка всіх 4 режимів, частинки кордону, шкода обмежена до 1 рази/сек
- README оновлено до v0.2.32

---

## [0.2.31] - 2026-03-15

### Виправлено (PvP лут-тригери)
- **`MATCH_START`** — тепер стріляє при старті раунду №1
- **`MATCH_END`** — при завершенні матчу
- **`TEAM_WIPE`** — при знищенні команди (кінець раунду)
- **`KILL_STREAK_3`** — кожні 3 вбивства поспіль (+ повідомлення в чат)
- **`PLAYER_DEATH`** — при кожній смерті гравця у PvP

### Додано
- **Kill streak система** — `pvpKillStreaks` відстежує вбивства поспіль; скидається при смерті жертви і на початку раунду
- **`PvpTeamSelectScreen`** — новий дизайн під кожен режим:
  - BR: одна кнопка "Увійти в гру" з поясненням про випадковий спавн
  - Deathmatch: показує ціль (вбивств), потім вибір команди з кольоровими кнопками
  - Standard: кольорові кнопки команд за назвою (червоні/сині/зелені/жовті)

---

## [0.2.30] - 2026-03-15

### Додано — Deathmatch
- **`PvpMode.DEATHMATCH`** — підрежим без вибування; гравці після смерті миттєво відроджуються
- Перемога по вбивствах команди (`dmKillsToWin`, за замовч. 10)
- `PvpRoundState.recordDmKill()`, `checkDmWinner()`, `getDmTeamKills()`

### Додано — Battle Royale
- **`PvpMode.BATTLE_ROYALE`** — підрежим з кордоном що звужується
- При вході — випадкова точка спавну (TeleportPacket ігнорує вибір гравця)
- `BattleRoyaleManager` — звуження, частинки по периметру, шкода поза кордоном
- Переможець — останній живий гравець (`checkBrWinner()`)

### Додано — UI
- Вкладка **🎮 Режим** у `PvpLocationEditorScreen` — вибір STANDARD/DEATHMATCH/BATTLE_ROYALE + налаштування кожного
- BR налаштування: радіус, інтервал звуження, тип частинок, шкода

---

## [0.2.29] - 2026-03-14

### Виправлено
- **Здача не виходила з локації** — `loadClientData` не очищала `currentLocation` при порожньому пакеті
- **Меню залишалось після смерті** — наслідок того ж бага
- **Таймер лоббі не відображався** — `locationStartTimers` не надсилав `syncPlayerData` клієнту
- **Після перемоги у PvP гравці застрягали** — `endPvpMatch` не синхронізував клієнтів
- **Баг `particleSpeed` скидається до 0.02** — `setValue` тепер перед `setResponder`

### Додано
- **Ефекти очікування PvP** — Slowness 127 + Blindness замість spectator
- **Таймер початку раунду (COUNTDOWN)** — нова фаза між BUY і ACTIVE
- **Поінти на початок раунду**, поінти за перемогу/поразку у раунді
- **Автобаланс команд** при вході/виході (`pvpTeamAutoBalance`)
- **`/wavedefense kick`** та `/wdkick` — примусовий вихід гравця
- **`enforceGameMode`** — per-location опція (не глобальна)
- Кнопка **«Без збереження»** у редакторі мобів хвилі

### Змінено
- **Затримка першої хвилі** перенесена: `timeBetweenWaves` хвилі №1 = затримка після лоббі
- **«Вийти з PvP»** прибрано з меню гравця

---

## [0.2.28] - 2026-03-14

### Виправлено (критичні баги мультиплеєра)
- **Головний баг здачі/смерті** — `PlayerWaveData.loadClientData()` не обнуляла `currentLocation` при порожньому пакеті. Результат: `isInWave()` завжди `true`, HUD/меню не зникали
- **Таймер лоббі** — не синхронізувався з клієнтом (не надсилався `syncPlayerData`)
- **Меню після смерті** — наслідок першого бага

### Додано
- Команда **`/wavedefense kick`** та аліас `/wdkick`
- **Протокол v7** — нові пакети для wave import/export

---

## [0.2.27] - 2026-03-13

### Додано
- **Тімейт-панель** у HUD (клавіша показу/приховання у налаштуваннях)
- **Keybind L** — вийти з локації без штрафу
- **Імпорт/Експорт магазину** (ExportShopPacket, ImportShopPacket)
- **Імпорт/Експорт хвиль** (ExportWavePacket, ImportWavePacket)
- Протокол v6

---

## [0.2.26] і раніше

Виправлення крашів сервера, PvP sync, HUD layout, система магазину, хвильовий менеджер, різні UI виправлення.

---

## [0.2.33] - 2026-03-15

### Виправлено — помилки компіляції (продовження)
- **`BoundaryManager`** — відсутній `import com.wavedefense.wave.PlayerWaveData` додано
- **`BattleRoyaleManager`** — відсутні `import WaveManager` і `import PlayerWaveData` додано
- **`getPlayersInLocation`** у `WaveManager` — змінено з `private` на `public` (потрібно `BattleRoyaleManager`)
- **`PvpPlayerStats` import** у `WaveManager` — додано
- **`PLAYER_DEATH` loot trigger** у `onPvePlayerDeath` — тепер стріляє і при PvE смерті

### Оновлено
- **README.md** — повністю переписаний під v0.2.32+
- **CHANGELOG.md** — деталізована хронологія v0.2.26 → v0.2.33
- **TooltipHelper** — 30+ нових підказок для всіх нових функцій:
  - Кордон: BOUNDARY_ENABLED, BOUNDARY_TIMER, BOUNDARY_DAMAGE, BOUNDARY_TELEPORT, BOUNDARY_INSTANT, BOUNDARY_PARTICLES
  - PvP: PVP_WAIT_EFFECT, PVP_AUTO_BALANCE, PVP_ROUND_DELAY, PVP_WIN_POINTS, PVP_LOSE_POINTS, PVP_ROUND_START_PTS, PVP_SPAWN_RADIUS
  - Deathmatch: DM_KILLS_TO_WIN, DM_RESPAWN
  - BR: BR_SPAWN, BR_BORDER_RADIUS, BR_SHRINK, BR_PARTICLE, BR_DAMAGE
  - Загальні: ENFORCE_GAMEMODE, WAVE_TIMER_BOX, MOB_SPAWN_RADIUS, WAVE_EXPORT, WAVE_IMPORT
- **`PvpLocationEditorScreen.getTip()`** — розширено підказками для всіх нових полів

---

## [0.2.38] - 2026-03-16

### Виправлено — Scissor/перекриття UI при скролі
Всі екрани зі скролінгом тепер використовують **3-pass render**: прокручений контент → статичний header → статичний footer. Скролений вміст більше не виходить за межі та не перекриває верхні/нижні елементи меню.

**Виправлені екрани:**
- **`LocationEditorScreen`** (вкладка ⚙ Спец) — запроваджено `staticWidgets` (IdentityHashMap-based set) для точного визначення статичних елементів. PvE/PvP toggle (Y=25), таби (Y=52) і нижні кнопки помічені через `addStatic()`. Більше не залежить від Y-порівнянь що могли помилятись
- **`PvpLocationEditorScreen`** (вкладка 🎮 Режим+Правила) — той самий підхід: `staticWidgets`, `addStatic()`, 3-pass render. При скролі правил таби залишаються зверху, кнопки Зберегти/Назад — знизу
- **`WaveConfigScreen`** (список хвиль) — render виправлено: контент через scissor, header і footer рендеряться поверх через власні scissor-зони
- **`WaveMobsEditorScreen`** (список мобів хвилі) — аналогічне виправлення

### Виправлено — Компіляція
- `PVP_ROUND_START_PTS` → `PVP_ROUND_POINTS` (правильна назва в TooltipHelper)
- `BR_SPAWN` → `BR_RANDOM_SPAWN` (правильна назва в TooltipHelper)
- `killerTeam` повторне оголошення в `onPlayerKilledPlayer` — видалено дублікат
- `ParticleTypes.BARRIER` не існує в 1.20.1 — видалено з BoundaryManager

---

## [0.2.37] - 2026-03-16

### Виправлено — Scissor у LocationEditorScreen
- Вкладка ⚙ Спец: при скролі контент більше не перекриває PvE/PvP toggle (Y=25), заголовок і нижні кнопки
- Запроваджено `staticWidgets` set та `addStatic()` хелпер — точне маркування статичних елементів

### Нове
- **Авто-перехід до PvP редактора** — при виборі PvP з `LocationEditorScreen` відразу відкривається `PvpLocationEditorScreen`
- **Вкладки PvP редактора об'єднані** — "⚙ Правила" і "🎮 Режим" → одна вкладка "🎮 Режим+Правила" з налаштуваннями для кожного підрежиму
