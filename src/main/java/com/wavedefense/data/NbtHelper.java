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

    // ─── Backup-aware read ────────────────────────────────────────────────

    /**
     * Outcome of a {@link #readWithBackup} call.
     *
     * <p>{@code tag == null} means nothing usable was found — either a fresh install
     * with no data yet, or both copies were unreadable. The two are distinguished in
     * the log, not in this result: callers treat both as "start empty".
     */
    public static final class LoadResult {
        /** Loaded root tag, or {@code null} when nothing could be read. */
        @Nullable public final CompoundTag tag;
        /** True when {@link #tag} came from the {@code .bak} copy rather than the primary. */
        public final boolean fromBackup;

        private LoadResult(@Nullable CompoundTag tag, boolean fromBackup) {
            this.tag = tag;
            this.fromBackup = fromBackup;
        }

        /** True when a tag was actually loaded. */
        public boolean isPresent() { return tag != null; }
    }

    private static final LoadResult NOTHING = new LoadResult(null, false);

    /**
     * Reads a file written by {@link #atomicWriteCompressed}, falling back to its
     * {@code .bak} sibling when the primary is missing or corrupt.
     *
     * <p>This is the read half of the atomic-write contract. The writer guarantees that
     * after any single crash at least one of the two copies is intact; without going
     * through this method a caller throws that guarantee away and silently starts from
     * an empty state with a perfectly good backup sitting next to the corrupt file.
     *
     * <p>When both copies are unreadable the primary is renamed to {@code .corrupted}
     * so the next save cannot overwrite evidence an admin might still want.
     *
     * @param dataFile the primary file
     * @param label    human-readable name for log messages, e.g. {@code "leaderboard data"}
     * @return never {@code null}; check {@link LoadResult#isPresent()}
     */
    public static LoadResult readWithBackup(File dataFile, String label) {
        File bak = new File(dataFile.getAbsolutePath() + ".bak");

        // Nothing on disk at all — a fresh install, not an error worth logging.
        if (!dataFile.exists() && !bak.exists()) return NOTHING;

        if (dataFile.exists()) {
            try {
                return new LoadResult(NbtIo.readCompressed(dataFile), false);
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("[WaveDefense] Primary {} file is corrupt: {}",
                    label, e.getMessage());
            }
        } else {
            WaveDefenseMod.LOGGER.warn("[WaveDefense] Primary {} file is missing", label);
        }

        if (bak.exists()) {
            try {
                CompoundTag tag = NbtIo.readCompressed(bak);
                WaveDefenseMod.LOGGER.warn("[WaveDefense] Restored {} from backup copy.", label);
                return new LoadResult(tag, true);
            } catch (Exception bakEx) {
                WaveDefenseMod.LOGGER.error("[WaveDefense] Backup {} file is also corrupt: {}",
                    label, bakEx.getMessage());
            }
        } else {
            WaveDefenseMod.LOGGER.error("[WaveDefense] No backup {} file to fall back to.", label);
        }

        quarantine(dataFile, label);
        return NOTHING;
    }

    /**
     * Moves an unreadable file aside so the next save does not overwrite it. Best-effort:
     * failing to quarantine must never stop the server from starting.
     */
    private static void quarantine(File dataFile, String label) {
        if (!dataFile.exists()) return;
        File corrupt = new File(dataFile.getAbsolutePath() + ".corrupted");
        if (dataFile.renameTo(corrupt)) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Moved unreadable {} aside to {}; starting empty.",
                label, corrupt.getName());
        } else {
            WaveDefenseMod.LOGGER.error("[WaveDefense] Could not quarantine unreadable {}; starting empty.",
                label);
        }
    }

    // ─── Atomic NBT file write ────────────────────────────────────────────

    // ─── Async/debounced save scheduling ──────────────────────────────────

    /**
     * Single-thread executor used for all background NBT writes.
     *
     * <p>Daemon, so it never blocks JVM shutdown. It is deliberately <b>never shut
     * down</b>: an integrated server can stop one world and open another inside the same
     * JVM, and a terminated executor would make every later async save throw
     * {@code RejectedExecutionException}. {@link #flushPendingWrites()} instead uses a
     * completion barrier, which gives the same "nothing is left in flight" guarantee
     * without that failure mode.
     */
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

    /**
     * Flush all pending debounced writes synchronously. Call on server stop.
     *
     * <p>Draining {@link #PENDING} alone is not enough: a scheduled write may already be
     * <em>running</em> on the save thread, in which case it has taken its snapshot and the
     * drain below sees nothing to do. Returning at that point would let the JVM exit and
     * kill the daemon thread mid-write. The barrier at the end closes that window.
     */
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

        // Completion barrier: the executor is single-threaded, so this no-op cannot start
        // until any write already in flight has finished. Waiting on it therefore waits
        // for that write. Submitted with zero delay, so it also jumps ahead of any task
        // still sitting out its debounce.
        try {
            SAVE_EXEC.submit(() -> { }).get(FLUSH_BARRIER_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            WaveDefenseMod.LOGGER.warn("[WaveDefense] Interrupted while waiting for pending saves to finish.");
        } catch (Exception e) {
            WaveDefenseMod.LOGGER.error("[WaveDefense] A background save did not finish in {}s: {}",
                FLUSH_BARRIER_TIMEOUT_SEC, e.toString());
        }
    }

    /** How long {@link #flushPendingWrites()} waits for an in-flight write before giving up. */
    private static final long FLUSH_BARRIER_TIMEOUT_SEC = 10L;

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
