package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.common.util.Constants;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Typed NBT helpers — eliminates boilerplate in serialization.
 *
 * <p>1.16.5 port notes:
 * <ul>
 *   <li>{@code CompoundTag} → {@code CompoundNBT}</li>
 *   <li>{@code ListTag} → {@code ListNBT}</li>
 *   <li>{@code Tag.TAG_LIST}/{@code TAG_COMPOUND} → {@code Constants.NBT.TAG_LIST}/{@code TAG_COMPOUND}</li>
 *   <li>{@code BlockPos} import differs: {@code net.minecraft.util.math.BlockPos}</li>
 *   <li>Method signatures otherwise identical.</li>
 * </ul>
 */
public final class NbtHelper {
    private NbtHelper() {}

    // ─── Typed getters with defaults ──────────────────────────────────────

    public static int     getInt   (CompoundNBT tag, String key, int def)     { return tag.contains(key) ? tag.getInt(key)     : def; }
    public static float   getFloat (CompoundNBT tag, String key, float def)   { return tag.contains(key) ? tag.getFloat(key)   : def; }
    public static long    getLong  (CompoundNBT tag, String key, long def)    { return tag.contains(key) ? tag.getLong(key)    : def; }
    public static double  getDouble(CompoundNBT tag, String key, double def)  { return tag.contains(key) ? tag.getDouble(key)  : def; }
    public static boolean getBool  (CompoundNBT tag, String key, boolean def) { return tag.contains(key) ? tag.getBoolean(key) : def; }
    public static String  getString(CompoundNBT tag, String key, String def)  { return tag.contains(key) ? tag.getString(key)  : def; }

    // ─── BlockPos (packed long) ───────────────────────────────────────────

    public static void savePosLong(CompoundNBT tag, String key, @Nullable BlockPos pos) {
        if (pos != null) tag.putLong(key, pos.asLong());
    }

    @Nullable
    public static BlockPos loadPosLong(CompoundNBT tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    // ─── Enum (by name) ───────────────────────────────────────────────────

    public static <E extends Enum<E>> void saveEnum(CompoundNBT tag, String key, E value) {
        tag.putString(key, value.name());
    }

    public static <E extends Enum<E>> E loadEnum(CompoundNBT tag, String key,
                                                   Class<E> type, E def) {
        if (!tag.contains(key)) return def;
        try { return Enum.valueOf(type, tag.getString(key)); }
        catch (IllegalArgumentException e) { return def; }
    }

    // ─── List<T> ──────────────────────────────────────────────────────────

    public static <T> void saveList(CompoundNBT tag, String key,
                                     List<T> list,
                                     Function<T, CompoundNBT> serializer) {
        ListNBT lt = new ListNBT();
        for (T item : list) lt.add(serializer.apply(item));
        tag.put(key, lt);
    }

    /**
     * Loads a TAG_List of TAG_Compound entries.
     * Returns an empty (mutable) list if the key is absent.
     * Items for which the deserializer returns null are silently skipped.
     */
    public static <T> List<T> loadList(CompoundNBT tag, String key,
                                        Function<CompoundNBT, T> deserializer) {
        List<T> result = new ArrayList<>();
        if (!tag.contains(key, Constants.NBT.TAG_LIST)) return result;
        ListNBT lt = tag.getList(key, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < lt.size(); i++) {
            T item = deserializer.apply(lt.getCompound(i));
            if (item != null) result.add(item);
        }
        return result;
    }
}
