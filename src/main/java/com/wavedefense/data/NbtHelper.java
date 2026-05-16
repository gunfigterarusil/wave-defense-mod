package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Typed NBT helpers — eliminates boilerplate in serialization.
 *
 * Scalar getters: return a default when the key is absent (never throw).
 * BlockPos:        stored as packed long (BlockPos.asLong / BlockPos.of).
 * Enum:            stored as the enum constant name; falls back to default on bad data.
 * List<T>:         reads/writes a TAG_List of TAG_Compound entries.
 */
public final class NbtHelper {
    private NbtHelper() {}

    // ─── Typed getters with defaults ──────────────────────────────────────

    public static int     getInt   (CompoundTag tag, String key, int def)     { return tag.contains(key) ? tag.getInt(key)     : def; }
    public static float   getFloat (CompoundTag tag, String key, float def)   { return tag.contains(key) ? tag.getFloat(key)   : def; }
    public static long    getLong  (CompoundTag tag, String key, long def)    { return tag.contains(key) ? tag.getLong(key)    : def; }
    public static double  getDouble(CompoundTag tag, String key, double def)  { return tag.contains(key) ? tag.getDouble(key)  : def; }
    public static boolean getBool  (CompoundTag tag, String key, boolean def) { return tag.contains(key) ? tag.getBoolean(key) : def; }
    public static String  getString(CompoundTag tag, String key, String def)  { return tag.contains(key) ? tag.getString(key)  : def; }

    // ─── BlockPos (packed long) ───────────────────────────────────────────

    public static void savePosLong(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) tag.putLong(key, pos.asLong());
    }

    public static @Nullable BlockPos loadPosLong(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    // ─── Enum (by name) ───────────────────────────────────────────────────

    public static <E extends Enum<E>> void saveEnum(CompoundTag tag, String key, E value) {
        tag.putString(key, value.name());
    }

    public static <E extends Enum<E>> E loadEnum(CompoundTag tag, String key,
                                                   Class<E> type, E def) {
        if (!tag.contains(key)) return def;
        try { return Enum.valueOf(type, tag.getString(key)); }
        catch (IllegalArgumentException e) { return def; }
    }

    // ─── List<T> ──────────────────────────────────────────────────────────

    public static <T> void saveList(CompoundTag tag, String key,
                                     List<T> list,
                                     Function<T, CompoundTag> serializer) {
        ListTag lt = new ListTag();
        for (T item : list) lt.add(serializer.apply(item));
        tag.put(key, lt);
    }

    /**
     * Loads a TAG_List of TAG_Compound entries.
     * Returns an empty (mutable) list if the key is absent.
     * Items for which the deserializer returns null are silently skipped.
     */
    public static <T> List<T> loadList(CompoundTag tag, String key,
                                        Function<CompoundTag, T> deserializer) {
        List<T> result = new ArrayList<>();
        if (!tag.contains(key, Tag.TAG_LIST)) return result;
        ListTag lt = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < lt.size(); i++) {
            T item = deserializer.apply(lt.getCompound(i));
            if (item != null) result.add(item);
        }
        return result;
    }
}
