package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the read half of the atomic-write contract.
 *
 * <p>{@link NbtHelper#atomicWriteCompressed} promises that after any single crash at least
 * one of the primary file and its {@code .bak} is intact. That promise is only worth
 * anything if readers actually consult the backup — these tests pin that behaviour, plus
 * the shutdown barrier that stops an in-flight write from being lost.
 */
class NbtHelperBackupTest {

    private static CompoundTag sampleTag(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("marker", marker);
        tag.putInt("value", 42);
        return tag;
    }

    /** Overwrites a file with bytes that are definitely not valid compressed NBT. */
    private static void corrupt(File f) throws IOException {
        Files.write(f.toPath(), "this is not nbt".getBytes(StandardCharsets.UTF_8));
    }

    // ── Backup-aware read ───────────────────────────────────────────────────

    @Test
    void readsThePrimaryWhenItIsHealthy(@TempDir Path dir) {
        File data = dir.resolve("data.dat").toFile();
        assertTrue(NbtHelper.atomicWriteCompressed(data, sampleTag("primary")));

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");

        assertTrue(result.isPresent());
        assertFalse(result.fromBackup, "a healthy primary must not report a backup read");
        assertEquals("primary", result.tag.getString("marker"));
    }

    @Test
    void fallsBackToTheBackupWhenThePrimaryIsCorrupt(@TempDir Path dir) throws IOException {
        File data = dir.resolve("data.dat").toFile();
        // First write creates the primary; second write moves it aside as .bak.
        NbtHelper.atomicWriteCompressed(data, sampleTag("old"));
        NbtHelper.atomicWriteCompressed(data, sampleTag("new"));
        assertTrue(new File(data.getAbsolutePath() + ".bak").exists(), "second write should create .bak");

        corrupt(data);

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");

        assertTrue(result.isPresent(), "a readable .bak must be used rather than starting empty");
        assertTrue(result.fromBackup);
        assertEquals("old", result.tag.getString("marker"));
    }

    @Test
    void fallsBackToTheBackupWhenThePrimaryIsMissingEntirely(@TempDir Path dir) throws IOException {
        File data = dir.resolve("data.dat").toFile();
        NbtHelper.atomicWriteCompressed(data, sampleTag("old"));
        NbtHelper.atomicWriteCompressed(data, sampleTag("new"));

        assertTrue(data.delete());

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");

        assertTrue(result.isPresent());
        assertTrue(result.fromBackup);
    }

    @Test
    void freshInstallIsNotAnError(@TempDir Path dir) {
        File data = dir.resolve("never-written.dat").toFile();

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");

        assertFalse(result.isPresent());
        assertNull(result.tag);
        assertFalse(result.fromBackup);
        assertFalse(new File(data.getAbsolutePath() + ".corrupted").exists(),
            "nothing to quarantine when the file never existed");
    }

    @Test
    void bothCopiesCorruptQuarantinesThePrimary(@TempDir Path dir) throws IOException {
        File data = dir.resolve("data.dat").toFile();
        NbtHelper.atomicWriteCompressed(data, sampleTag("old"));
        NbtHelper.atomicWriteCompressed(data, sampleTag("new"));

        File bak = new File(data.getAbsolutePath() + ".bak");
        corrupt(data);
        corrupt(bak);

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");

        assertFalse(result.isPresent(), "nothing readable → start empty");
        assertFalse(data.exists(), "the unreadable primary must be moved aside");
        assertTrue(new File(data.getAbsolutePath() + ".corrupted").exists(),
            "so the next save cannot overwrite evidence an admin may want");
    }

    // ── Atomic write keeps a recoverable copy ───────────────────────────────

    @Test
    void everyWriteAfterTheFirstLeavesARecoverableBackup(@TempDir Path dir) {
        File data = dir.resolve("data.dat").toFile();

        NbtHelper.atomicWriteCompressed(data, sampleTag("v1"));
        assertFalse(new File(data.getAbsolutePath() + ".bak").exists(),
            "nothing to back up on the very first write");

        NbtHelper.atomicWriteCompressed(data, sampleTag("v2"));
        assertTrue(new File(data.getAbsolutePath() + ".bak").exists());
        assertFalse(new File(data.getAbsolutePath() + ".tmp").exists(),
            "the temp file must be consumed by the commit, not left behind");
    }

    // ── Shutdown barrier ────────────────────────────────────────────────────

    @Test
    void flushPendingWritesCommitsADebouncedWriteBeforeReturning(@TempDir Path dir) {
        File data = dir.resolve("debounced.dat").toFile();

        // Queue a write with the normal 1 s debounce — it is still only in memory here.
        NbtHelper.atomicWriteCompressedAsync(data, sampleTag("queued"));

        NbtHelper.flushPendingWrites();

        assertTrue(data.exists(), "flush must commit queued writes, not just cancel them");
        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");
        assertTrue(result.isPresent());
        assertEquals("queued", result.tag.getString("marker"));
    }

    @Test
    void flushIsSafeToCallWithNothingPending() {
        // The barrier must not blow up or hang when there is no work queued —
        // this runs on every server stop, including immediately after a clean save.
        assertDoesNotThrow(NbtHelper::flushPendingWrites);
        assertDoesNotThrow(NbtHelper::flushPendingWrites);
    }

    @Test
    void theSaveExecutorSurvivesAFlushSoALaterWorldCanStillSave(@TempDir Path dir) {
        // Regression guard: shutting the executor down on stop would break the
        // integrated server's "quit to title, open another world" flow.
        NbtHelper.flushPendingWrites();

        File data = dir.resolve("after-flush.dat").toFile();
        assertDoesNotThrow(() -> NbtHelper.atomicWriteCompressedAsync(data, sampleTag("after")));
        NbtHelper.flushPendingWrites();

        assertTrue(data.exists(), "async saves must still work after a previous flush");
    }

    @Test
    void latestSnapshotWinsWhenWritesAreCollapsed(@TempDir Path dir) {
        File data = dir.resolve("collapsed.dat").toFile();

        NbtHelper.atomicWriteCompressedAsync(data, sampleTag("first"));
        NbtHelper.atomicWriteCompressedAsync(data, sampleTag("second"));
        NbtHelper.atomicWriteCompressedAsync(data, sampleTag("third"));
        NbtHelper.flushPendingWrites();

        NbtHelper.LoadResult result = NbtHelper.readWithBackup(data, "test data");
        assertTrue(result.isPresent());
        assertEquals("third", result.tag.getString("marker"),
            "debouncing must keep the newest snapshot, not the oldest");
    }
}
