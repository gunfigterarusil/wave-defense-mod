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
        WAVE_START     ("🌊 Початок хвилі",    true,  true),
        WAVE_END       ("✅ Кінець хвилі",      true,  true),
        TIMER_60       ("⏱ Кожні 60 сек",      true,  true),
        TIMER_120      ("⏱ Кожні 2 хв",        true,  true),
        TIMER_300      ("⏱ Кожні 5 хв",        true,  true),
        PLAYER_JOIN    ("👤 Гравець приєднався",true,  true),
        PLAYER_DEATH   ("💀 Смерть гравця",     true,  true),

        // ── PvE-специфічні ───────────────────────────────────────────
        MOB_KILL       ("⚔ Вбивство моба",     true,  false),
        HALF_MOBS_DEAD ("☠ Половина мобів загинула", true, false),
        LOCATION_START ("🚀 Старт локації",     true,  false),
        LOCATION_END   ("🏆 Завершення локації",true,  false),

        // ── PvP-специфічні ───────────────────────────────────────────
        ROUND_START    ("🔔 Початок раунду",   false,  true),
        ROUND_END      ("🏁 Кінець раунду",    false,  true),
        BUY_PHASE      ("🛒 Фаза покупок",     false,  true),
        TEAM_WIPE      ("💣 Команда вибита",   false,  true),
        KILL_STREAK_3  ("🔥 3 фраги підряд",   false,  true),
        MATCH_START    ("🎯 Старт матчу",      false,  true),
        MATCH_END      ("🏆 Кінець матчу",     false,  true);

        public final String label;
        public final boolean pve;  // доступний у PvE
        public final boolean pvp;  // доступний у PvP

        Trigger(String label, boolean pve, boolean pvp) {
            this.label = label;
            this.pve   = pve;
            this.pvp   = pvp;
        }
    }

    private BlockPos pos;
    private List<ItemStack> items;
    private int spawnChance; // 1–100
    private int count;
    private Set<Trigger> triggers; // один або кілька тригерів

    public LootSpawn(BlockPos pos, List<ItemStack> items, int spawnChance, int count) {
        this.pos         = pos;
        this.items       = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
        this.spawnChance = Math.max(1, Math.min(100, spawnChance));
        this.count       = Math.max(1, count);
        this.triggers    = new LinkedHashSet<>();
        this.triggers.add(Trigger.WAVE_START); // дефолт
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

        return ls;
    }
}
