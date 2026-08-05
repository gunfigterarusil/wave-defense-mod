package com.wavedefense.network;

import com.wavedefense.data.LocationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the filename rules shared by every import/export packet.
 *
 * <p>These packets build paths out of strings the client supplied, so the guard is the
 * only thing between an op-level client and a write outside the export directory.
 */
class FilePathGuardTest {

    // ── Containment ─────────────────────────────────────────────────────────

    @Test
    void acceptsAFileDirectlyInsideTheDirectory(@TempDir Path dir) {
        File base = dir.toFile();
        assertTrue(FilePathGuard.isInside(base, new File(base, "wave1.nbt")));
        assertTrue(FilePathGuard.isInside(base, new File(base, "sub/wave1.nbt")),
            "nested paths are still inside");
    }

    @Test
    void rejectsParentDirectoryEscapes(@TempDir Path dir) {
        File base = dir.toFile();

        assertFalse(FilePathGuard.isInside(base, new File(base, "../escaped.nbt")));
        assertFalse(FilePathGuard.isInside(base, new File(base, "../../../../etc/passwd")));
        assertFalse(FilePathGuard.isInside(base, new File(base, "sub/../../escaped.nbt")));
    }

    @Test
    void rejectsASiblingDirectoryThatSharesANamePrefix(@TempDir Path dir) throws Exception {
        // The exact bypass this method exists to close: a plain
        // startsWith(canonicalDir) test passes ".../shops_evil" against ".../shops"
        // because the shorter path is a string prefix of the longer one.
        File shops = dir.resolve("shops").toFile();
        File evil  = dir.resolve("shops_evil").toFile();
        assertTrue(shops.mkdirs());
        assertTrue(evil.mkdirs());

        File target = new File(evil, "loot.nbt");

        assertFalse(FilePathGuard.isInside(shops, target),
            "shops_evil is a sibling of shops, not a child of it");
        assertTrue(FilePathGuard.isInside(evil, target));
    }

    @Test
    void theDirectoryItselfIsNotInsideItself(@TempDir Path dir) {
        File base = dir.toFile();
        assertFalse(FilePathGuard.isInside(base, base),
            "a write target must be a file within the directory, not the directory");
    }

    // ── Sanitising ──────────────────────────────────────────────────────────

    @Test
    void sanitizeStripsEverythingThatCouldFormAPath() {
        String cleaned = FilePathGuard.sanitizeFileName("/../../etc/passwd");

        assertEquals("_______etc_passwd", cleaned);
        // Restated as the property that actually matters, independent of the exact
        // replacement character: nothing path-forming survives.
        for (String dangerous : new String[] { "/", "\\", ".", ":" }) {
            assertFalse(cleaned.contains(dangerous), "sanitised name still contains " + dangerous);
            assertFalse(FilePathGuard.sanitizeFileName("../a/b\\c:d").contains(dangerous));
        }
    }

    @Test
    void sanitizeKeepsOrdinaryNamesReadable() {
        assertEquals("arena_01", FilePathGuard.sanitizeFileName("Arena_01"));
        assertEquals("my-arena", FilePathGuard.sanitizeFileName("my-arena"));
    }

    @Test
    void sanitizeNeverReturnsAnEmptyComponent() {
        // An empty result would produce a path ending in a bare separator.
        assertEquals("unnamed", FilePathGuard.sanitizeFileName(""));
        assertEquals("unnamed", FilePathGuard.sanitizeFileName(null));
        assertFalse(FilePathGuard.sanitizeFileName("...").isEmpty());
        assertFalse(FilePathGuard.sanitizeFileName("日本語").isEmpty());
    }

    // ── Location names ──────────────────────────────────────────────────────

    @Test
    void locationNamesRejectAnythingPathLike() {
        assertFalse(LocationManager.isValidName("../evil"));
        assertFalse(LocationManager.isValidName("a/b"));
        assertFalse(LocationManager.isValidName("a\\b"));
        assertFalse(LocationManager.isValidName("has space"));
        assertFalse(LocationManager.isValidName("dot.name"));
    }

    @Test
    void locationNamesRejectEmptyNullAndOverlong() {
        assertFalse(LocationManager.isValidName(null));
        assertFalse(LocationManager.isValidName(""));
        assertFalse(LocationManager.isValidName("a".repeat(LocationManager.MAX_NAME_LENGTH + 1)));
        assertTrue(LocationManager.isValidName("a".repeat(LocationManager.MAX_NAME_LENGTH)),
            "exactly at the limit is still allowed");
    }

    @Test
    void locationNamesAcceptOrdinaryNames() {
        assertTrue(LocationManager.isValidName("arena"));
        assertTrue(LocationManager.isValidName("Arena_01"));
        assertTrue(LocationManager.isValidName("my-arena-2"));
    }
}
