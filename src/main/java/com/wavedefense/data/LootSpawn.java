package com.wavedefense.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A loot spawn point on the map.
 * Stores: position, items (up to 4), chance, count, and a set of triggers.
 */
public class LootSpawn {

    /**
     * Triggers that control WHEN loot spawns.
     * Multiple triggers can be active simultaneously.
     */
    public enum Trigger {
        // ── General ───────────────────────────────────────────────────
        WAVE_START     ("wavedefense.trigger.loot.wave_start",     true,  true,  false, "wavedefense.trigger.loot.wave_start.tip"),
        WAVE_END       ("wavedefense.trigger.loot.wave_end",       true,  true,  false, "wavedefense.trigger.loot.wave_end.tip"),
        TIMER_60       ("wavedefense.trigger.loot.timer_60",       true,  true,  false, "wavedefense.trigger.loot.timer_60.tip"),
        TIMER_120      ("wavedefense.trigger.loot.timer_120",      true,  true,  false, "wavedefense.trigger.loot.timer_120.tip"),
        TIMER_300      ("wavedefense.trigger.loot.timer_300",      true,  true,  false, "wavedefense.trigger.loot.timer_300.tip"),
        PLAYER_JOIN    ("wavedefense.trigger.loot.player_join",    true,  true,  false, "wavedefense.trigger.loot.player_join.tip"),
        PLAYER_DEATH   ("wavedefense.trigger.loot.player_death",   true,  true,  false, "wavedefense.trigger.loot.player_death.tip"),

        // ── PvE-specific ──────────────────────────────────────────────
        MOB_KILL       ("wavedefense.trigger.loot.mob_kill",       true,  false, false, "wavedefense.trigger.loot.mob_kill.tip"),
        HALF_MOBS_DEAD ("wavedefense.trigger.loot.half_mobs_dead", true,  false, false, "wavedefense.trigger.loot.half_mobs_dead.tip"),
        LOCATION_START ("wavedefense.trigger.loot.location_start", true,  false, false, "wavedefense.trigger.loot.location_start.tip"),
        LOCATION_END   ("wavedefense.trigger.loot.location_end",   true,  false, false, "wavedefense.trigger.loot.location_end.tip"),
        WAVE_N         ("wavedefense.trigger.loot.wave_n",         true,  false, true,  "wavedefense.trigger.loot.wave_n.tip"),
        MOBS_KILLED_N  ("wavedefense.trigger.loot.mobs_killed_n",  true,  false, true,  "wavedefense.trigger.loot.mobs_killed_n.tip"),

        // ── PvP-specific ──────────────────────────────────────────────
        ROUND_START    ("wavedefense.trigger.loot.round_start",    false, true,  false, "wavedefense.trigger.loot.round_start.tip"),
        ROUND_END      ("wavedefense.trigger.loot.round_end",      false, true,  false, "wavedefense.trigger.loot.round_end.tip"),
        BUY_PHASE      ("wavedefense.trigger.loot.buy_phase",      false, true,  false, "wavedefense.trigger.loot.buy_phase.tip"),
        TEAM_WIPE      ("wavedefense.trigger.loot.team_wipe",      false, true,  false, "wavedefense.trigger.loot.team_wipe.tip"),
        KILL_STREAK_3  ("wavedefense.trigger.loot.kill_streak_3",  false, true,  false, "wavedefense.trigger.loot.kill_streak_3.tip"),
        MATCH_START    ("wavedefense.trigger.loot.match_start",    false, true,  false, "wavedefense.trigger.loot.match_start.tip"),
        MATCH_END      ("wavedefense.trigger.loot.match_end",      false, true,  false, "wavedefense.trigger.loot.match_end.tip");

        public final String  label;
        public final boolean pve;        // available in PvE
        public final boolean pvp;        // available in PvP
        public final boolean needsValue; // requires a numeric setting (N)
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
    private Set<Trigger> triggers; // one or more triggers
    // Per-trigger custom values: e.g. WAVE_N=3, MOBS_KILLED_N=50
    private java.util.Map<Trigger, Integer> triggerValues = new java.util.EnumMap<>(Trigger.class);

    public LootSpawn(BlockPos pos, List<ItemStack> items, int spawnChance, int count) {
        this.pos         = pos;
        this.items       = items.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
        this.spawnChance = Math.max(1, Math.min(100, spawnChance));
        this.count       = Math.max(1, count);
        this.triggers     = new LinkedHashSet<>();
        this.triggers.add(Trigger.WAVE_START); // default
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
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putLong("pos", pos.asLong());
        tag.putInt("spawnChance", spawnChance);
        tag.putInt("count", count);

        ListNBT itemsList = new ListNBT();
        for (ItemStack item : items) {
            if (!item.isEmpty()) itemsList.add(item.save(new CompoundNBT()));
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

    public static LootSpawn load(CompoundNBT tag) {
        BlockPos pos       = BlockPos.of(tag.getLong("pos"));
        int spawnChance    = tag.getInt("spawnChance");
        int count          = tag.contains("count") ? tag.getInt("count") : 1;

        List<ItemStack> items = new ArrayList<>();
        ListNBT itemsList = tag.getList("items", 10);
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
