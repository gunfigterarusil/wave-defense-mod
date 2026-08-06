package com.wavedefense.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the section-merge feature's core safety invariant: every NBT key a
 * {@link Location} can persist must be owned by exactly one {@link LocationSection}.
 *
 * <p>An unmapped key means a concurrent section-merge would silently drop an admin's
 * edit to it, so this fails the moment a persisted field is added without being placed
 * in a section.
 *
 * <p>The key list is <b>read out of {@code LocationSerializer}</b> rather than mirrored
 * by hand. An earlier version kept a hardcoded copy, which drifted the first time new
 * fields were added — the test then failed for its own staleness instead of for a real
 * problem, which is exactly the failure mode a guard must not have.
 */
class LocationSectionTest {

    private static final Path SERIALIZER =
        Paths.get("src/main/java/com/wavedefense/data/LocationSerializer.java");

    /**
     * Keys written by {@code LocationSerializer.save}, scraped from the source.
     *
     * <p>Covers both the direct {@code tag.putX("key", …)} calls and the
     * {@code NbtHelper.saveList(tag, "key", …)} helper used for list fields.
     */
    private static Set<String> persistedKeys() {
        String src;
        try {
            src = new String(Files.readAllBytes(SERIALIZER), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + SERIALIZER.toAbsolutePath()
                + " — the test must run with the module root as working directory", e);
        }

        Set<String> keys = new LinkedHashSet<>();
        Pattern[] writers = {
            Pattern.compile("tag\\.put\\w*\\(\\s*\"(\\w+)\""),
            Pattern.compile("NbtHelper\\.saveList\\(\\s*tag\\s*,\\s*\"(\\w+)\""),
        };
        for (Pattern p : writers) {
            Matcher m = p.matcher(src);
            while (m.find()) keys.add(m.group(1));
        }

        assertFalse(keys.isEmpty(),
            "no persisted keys parsed out of LocationSerializer — the scan would pass vacuously");
        return keys;
    }

    @Test
    void everyPersistedKeyIsMappedToExactlyOneSection() {
        for (String key : persistedKeys()) {
            LocationSection sec = LocationSection.sectionOf(key);
            assertNotNull(sec, "Key '" + key + "' is not assigned to any LocationSection — "
                + "a section-merge would silently drop edits to it.");
            int owners = 0;
            for (LocationSection s : LocationSection.values()) {
                if (s.owns(key)) owners++;
            }
            assertEquals(1, owners, "Key '" + key + "' is owned by " + owners
                + " sections (must be exactly 1).");
        }
    }

    @Test
    void noSectionDeclaresAKeyThatIsNeverPersisted() {
        Set<String> persisted = persistedKeys();
        List<String> stale = new ArrayList<>();
        for (LocationSection s : LocationSection.values()) {
            for (String key : s.keys()) {
                if (!persisted.contains(key)) stale.add(s + "." + key);
            }
        }
        assertTrue(stale.isEmpty(),
            "These sections declare keys that LocationSerializer never writes. Either the\n"
          + "field was removed and the mapping is stale, or the field exists but is not\n"
          + "being saved at all.\n  " + String.join("\n  ", stale));
    }

    @Test
    void runtimeKeysAreNeverEditorOwned() {
        for (LocationSection s : LocationSection.editorSections()) {
            for (String key : s.keys()) {
                assertFalse(LocationSection.isRuntime(key),
                    "Key '" + key + "' is in editor section " + s + " but also RUNTIME — "
                    + "the editor would be able to overwrite live match state.");
            }
        }
    }

    @Test
    void contentSizedListsAreAllRealPersistedKeys() {
        // These are excluded from location packets and carried separately; naming one
        // that is not actually persisted would silently exclude nothing.
        Set<String> persisted = persistedKeys();
        for (String key : LocationSection.contentSizedLists()) {
            assertTrue(persisted.contains(key),
                "'" + key + "' is declared content-sized but LocationSerializer never writes it");
        }
    }
}
