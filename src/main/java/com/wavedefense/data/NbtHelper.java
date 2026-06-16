package com.wavedefense.data;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    // ─── Atomic NBT file write ────────────────────────────────────────────

    // ─── Async/debounced save scheduling ──────────────────────────────────

    /** Single-thread executor used for all background NBT writes. Daemon so it
     *  doesn't block JVM shutdown — final save on stop happens synchronously. */
    private static final java.util.concurrent.ScheduledExecutorService SAVE_EXEC =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WaveDefense-Save");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

    /** Pending writes keyed by absolute file path. Latest snapshot wins; older
     *  scheduled writes are skipped when their snapshot is no longer current. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicReference<CompoundTag>>
        PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    /** Per-file scheduled-future for debounce; replaced on each new request. */
    private static final java.util.Map<String, java.util.concurrent.ScheduledFuture<?>>
        SCHEDULED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Default debounce window — collapses multiple saves within this window into one disk write. */
    public static final long DEFAULT_DEBOUNCE_MS = 1000L;

    /**
     * Schedule an atomic write off the server thread, debounced to collapse
     * burst-saves (e.g. admin clicking Save 5× / fast tab switches) into a
     * single disk hit. Safe to call from server thread on every state change.
     *
     * <p>The most recently-supplied {@code tag} wins; intermediate writes are
     * dropped (data is still consistent because each {@code tag} is a full
     * snapshot, not a delta).
     */
    public static void atomicWriteCompressedAsync(File dataFile, CompoundTag tag) {
        atomicWriteCompressedAsync(dataFile, tag, DEFAULT_DEBOUNCE_MS);
    }

    public static void atomicWriteCompressedAsync(File dataFile, CompoundTag tag, long debounceMs) {
        String key = dataFile.getAbsolutePath();
        PENDING.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicReference<>()).set(tag);

        // Cancel any pending scheduled flush; schedule a new one.
        java.util.concurrent.ScheduledFuture<?> existing = SCHEDULED.get(key);
        if (existing != null) existing.cancel(false);

        SCHEDULED.put(key, SAVE_EXEC.schedule(() -> {
            CompoundTag latest = PENDING.get(key).getAndSet(null);
            SCHEDULED.remove(key);
            if (latest == null) return; // someone already flushed
            atomicWriteCompressed(dataFile, latest);
        }, debounceMs, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /** Flush all pending debounced writes synchronously. Call on server stop. */
    public static void flushPendingWrites() {
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicReference<CompoundTag>> e : PENDING.entrySet()) {
            CompoundTag latest = e.getValue().getAndSet(null);
            if (latest != null) {
                atomicWriteCompressed(new File(e.getKey()), latest);
            }
        }
        // Cancel all scheduled — they would no-op anyway since PENDING is now empty
        for (java.util.concurrent.ScheduledFuture<?> sf : SCHEDULED.values()) sf.cancel(false);
        SCHEDULED.clear();
    }

    /**
     * Writes a {@link CompoundTag} atomically to {@code dataFile}.
     *
     * <p>Failure-safety contract:
     * <ol>
     *   <li>Serialize to {@code dataFile + ".tmp"} via {@link NbtIo#writeCompressed}.</li>
     *   <li>If the main file exists, move it to {@code dataFile + ".bak"} (REPLACE_EXISTING).</li>
     *   <li>Atomically move {@code .tmp} → main using {@code ATOMIC_MOVE} where supported,
     *       falling back to {@code REPLACE_EXISTING} on filesystems that don't support
     *       atomic rename (Windows on FAT, some network mounts).</li>
     *   <li>If the commit fails after backup, attempt to restore the backup so callers are
     *       never left with a missing data file.</li>
     * </ol>
     *
     * <p>This means: after any single crash, the reader can recover from either the
     * main file or the {@code .bak} — never both missing.
     *
     * @return {@code true} on successful commit, {@code false} if every fallback failed
     *         (data file may be missing — caller should fall back to in-memory state).
     */
    public static boolean atomicWriteCompressed(File dataFile, CompoundTag tag) {
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                WaveDefenseMod.LOGGER.error("[WaveDefense] Could not create parent dir for {}", dataFile);
                return false;
            }
            File tmp = new File(dataFile.getAbsolutePath() + ".tmp");
            File bak = new File(dataFile.getAbsolutePath() + ".bak");

            NbtIo.writeCompressed(tag, tmp);

            // Back up existing data file before clobbering it
            if (dataFile.exists()) {
                try {
                    Files.move(dataFile.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    WaveDefenseMod.LOGGER.warn("[WaveDefense] Could not back up {}: {}", dataFile.getName(), ex.getMessage());
                    // If we can't back up, do NOT risk losing the only good copy
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    return false;
                }
            }

            // Atomic commit (preferred); fall back to plain replace if FS doesn't support it
            Path tmpPath = tmp.toPath();
            Path dataPath = dataFile.toPath();
            try {
                Files.move(tmpPath, dataPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicEx) {
                Files.move(tmpPath, dataPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException commitEx) {
                WaveDefenseMod.LOGGER.error("[WaveDefense] Commit failed for {}: {} — attempting to restore backup",
                    dataFile.getName(), commitEx.getMessage());
                if (bak.exists()) {
                    try {
                        Files.move(bak.toPath(), dataPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException restoreEx) {
                        WaveDefenseMod.LOGGER.error("[WaveDefense] Backup restore also failed: {}", restoreEx.getMessage());
                    }
                }
                return false;
            }
            return true;
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Atomic write failed for {}: {}", dataFile, e.getMessage());
            return false;
        }
    }
}
