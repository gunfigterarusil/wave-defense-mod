package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Точка появи луту на карті.
 * Зберігає: позицію, предмети (до 4), шанс, кількість та набір тригерів.
 */
public class LootSpawn {

    /**
     * Тригери що вказують КОЛИ спавниться лут.
     * Можна встановити кілька тригерів одночасно.
     */
    public enum Trigger {
        // ── Загальні ─────────────────────────────────────────────────
        WAVE_START     ("🌊 Початок хвилі",        true,  true,  false, "Лут спавниться на початку кожної хвилі"),
        WAVE_END       ("✅ Кінець хвилі",          true,  true,  false, "Спавниться коли хвиля повністю завершена"),
        TIMER_60       ("⏱ Кожні 60 сек",          true,  true,  false, "Спавниться раз на 60 секунд поки локація активна"),
        TIMER_120      ("⏱ Кожні 2 хв",            true,  true,  false, "Спавниться раз на 2 хвилини"),
        TIMER_300      ("⏱ Кожні 5 хв",            true,  true,  false, "Спавниться раз на 5 хвилин"),
        PLAYER_JOIN    ("👤 Гравець приєднався",    true,  true,  false, "Кожного разу коли гравець входить на локацію"),
        PLAYER_DEATH   ("💀 Смерть гравця",         true,  true,  false, "Кожного разу коли гравець гине на локації"),

        // ── PvE-специфічні ───────────────────────────────────────────
        MOB_KILL       ("⚔ Вбивство моба",         true,  false, false, "Спавниться при кожному вбивстві моба на локації"),
        HALF_MOBS_DEAD ("☠ Половина мобів загинула",true, false, false, "Коли загинуло ≥50% мобів поточної хвилі"),
        LOCATION_START ("🚀 Старт локації",         true,  false, false, "Один раз при активації локації"),
        LOCATION_END   ("🏆 Завершення локації",    true,  false, false, "Коли локацію пройдено (всі хвилі завершені)"),
        WAVE_N         ("🌊 Хвиля N",               true,  false, true,  "Спавниться тільки на початку конкретної хвилі (N задається окремо)"),
        MOBS_KILLED_N  ("⚔ Вбито N мобів",         true,  false, true,  "Коли на локації вбито ≥N мобів за сесію (N задається окремо)"),

        // ── PvP-специфічні ───────────────────────────────────────────
        ROUND_START    ("🔔 Початок раунду",        false, true,  false, "На початку кожного PvP раунду"),
        ROUND_END      ("🏁 Кінець раунду",         false, true,  false, "Коли раунд завершується"),
        BUY_PHASE      ("🛒 Фаза покупок",          false, true,  false, "На початку BUY фази між раундами"),
        TEAM_WIPE      ("💣 Команда вибита",        false, true,  false, "Коли всю команду знищено в раунді"),
        KILL_STREAK_3  ("🔥 3 фраги підряд",        false, true,  false, "Коли гравець набирає 3 вбивства поспіль"),
        MATCH_START    ("🎯 Старт матчу",           false, true,  false, "Один раз на початку матчу"),
        MATCH_END      ("🏆 Кінець матчу",          false, true,  false, "Коли матч завершується");

        public final String  label;
        public final boolean pve;        // доступний у PvE
        public final boolean pvp;        // доступний у PvP
        public final boolean needsValue; // потребує числового налаштування (N)
        public final String  tooltip;

        Trigger(String label, boolean pve, boolean pvp, boolean needsValue, String tooltip) {
            this.label      = label;
            this.pve        = pve;
            this.pvp        = pvp;
            this.needsValue = needsValue;
            this.tooltip    = tooltip;
        }
    }

    private BlockPos pos;
    private List<ItemStack> items;
    private int spawnChance; // 1–100
    private int count;
    private Set<Trigger> triggers; // один або кілька тригерів
    // Per-trigger custom values: наприклад WAVE_N=3, MOBS_KILLED_N=50
    private java.util.Map<Trigger, Integer> triggerValues = new java.util.EnumMap<>(Trigger.class);

    public LootSpawn(BlockPos pos, List<ItemStack> items, int spawnChance, int count) {
        this.pos         = pos;
        this.items       = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
        this.spawnChance = Math.max(1, Math.min(100, spawnChance));
        this.count       = Math.max(1, count);
        this.triggers     = new LinkedHashSet<>();
        this.triggers.add(Trigger.WAVE_START); // дефолт
        this.triggerValues = new java.util.EnumMap<>(Trigger.class);
    }

    // ── Getters / Setters ────────────────────────────────────────────
    public BlockPos getPos()             { return pos; }
    public void setPos(BlockPos pos)     { this.pos = pos; }

    public List<ItemStack> getItems()    { return items; }
    public void setItems(List<ItemStack> items) {
        this.items = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
    }

    public int getSpawnChance()          { return spawnChance; }
    public void setSpawnChance(int v)    { this.spawnChance = Math.max(1, Math.min(100, v)); }

    public int getCount()                { return count; }
    public void setCount(int v)          { this.count = Math.max(1, v); }

    public Set<Trigger> getTriggers()    { return triggers; }
    public void setTriggers(Set<Trigger> t) { this.triggers = t; }
    public void addTrigger(Trigger t)    { triggers.add(t); }
    public void removeTrigger(Trigger t) { triggers.remove(t); }
    public boolean hasTrigger(Trigger t) { return triggers.contains(t); }

    public int  getTriggerValue(Trigger t)          { return triggerValues.getOrDefault(t, 1); }
    public void setTriggerValue(Trigger t, int val) {
        if (val >= 1) triggerValues.put(t, val); else triggerValues.remove(t);
    }

    /** Зручний ярлик — є хоч один з перелічених тригерів */
    public boolean matchesAny(Trigger... check) {
        for (Trigger t : check) if (triggers.contains(t)) return true;
        return false;
    }

    // ── NBT ──────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("count", count);

        ListTag itemsList = new ListTag();
        for (ItemStack item : items) {
            if (!item.isEmpty()) itemsList.add(item.save(new CompoundTag()));
        }
        tag.put("items", itemsList);

        // Зберігаємо тригери як рядок через кому
        tag.putString("triggers", triggers.stream()
                .map(Trigger::name)
                .collect(Collectors.joining(",")));
        // Per-trigger values: "WAVE_N=3,MOBS_KILLED_N=50"
        if (!triggerValues.isEmpty()) {
            String tvStr = triggerValues.entrySet().stream()
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(","));
            tag.putString("triggerValues", tvStr);
        }
        return tag;
    }

    public static LootSpawn load(CompoundTag tag) {
        BlockPos pos       = BlockPos.of(tag.getLong("pos"));
        int spawnChance    = tag.getInt("spawnChance");
        int count          = tag.contains("count") ? tag.getInt("count") : 1;

        List<ItemStack> items = new ArrayList<>();
        ListTag itemsList = tag.getList("items", 10);
        for (int i = 0; i < itemsList.size(); i++) {
            items.add(ItemStack.of(itemsList.getCompound(i)));
        }

        LootSpawn ls = new LootSpawn(pos, items, spawnChance, count);
        ls.triggers.clear();

        // Завантажуємо тригери
        if (tag.contains("triggers")) {
            String raw = tag.getString("triggers");
            if (!raw.isEmpty()) {
                for (String s : raw.split(",")) {
                    try { ls.triggers.add(Trigger.valueOf(s.trim())); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        }
        // Дефолт якщо порожньо
        if (ls.triggers.isEmpty()) ls.triggers.add(Trigger.WAVE_START);

        // Per-trigger values
        if (tag.contains("triggerValues")) {
            String tvRaw = tag.getString("triggerValues");
            for (String part : tvRaw.split(",")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    try {
                        Trigger t = Trigger.valueOf(kv[0].trim());
                        int     v = Integer.parseInt(kv[1].trim());
                        ls.triggerValues.put(t, v);
                    } catch (Exception ignored) {}
                }
            }
        }

        return ls;
    }
}
