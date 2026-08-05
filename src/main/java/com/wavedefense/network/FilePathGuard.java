package com.wavedefense.network;

import com.wavedefense.WaveDefenseMod;

import java.io.File;
import java.io.IOException;

/**
 * Filename handling for the import/export packets.
 *
 * <p>Every one of those packets builds a path out of a string the client supplied. They
 * used to each do it slightly differently — one had no containment check at all, two used
 * a canonical-prefix test that a sibling directory sharing a name prefix could slip past,
 * and one concatenated an unvalidated location name straight into a path. Centralising the
 * rules here means there is exactly one place to get right.
 */
public final class FilePathGuard {

    private FilePathGuard() {}

    /** Characters allowed in a generated filename; everything else becomes {@code '_'}. */
    private static final String UNSAFE_CHARS = "[^a-zA-Z0-9_\\-]";

    /**
     * Reduces a string to a safe filename component.
     *
     * <p>Deliberately a whitelist: separators, {@code ..}, control characters, unicode and
     * Windows-reserved punctuation all collapse to {@code '_'} without needing to be
     * enumerated. Lower-cased so exports do not collide on case-insensitive filesystems.
     *
     * @return a non-empty safe name; {@code "unnamed"} when the input reduces to nothing
     */
    public static String sanitizeFileName(String s) {
        if (s == null || s.isEmpty()) return "unnamed";
        String cleaned = s.replaceAll(UNSAFE_CHARS, "_").toLowerCase();
        // An all-underscore result is legal but useless; an empty one is impossible here,
        // yet guard anyway so callers never build a path ending in a bare separator.
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }

    /**
     * True when {@code child} really resolves inside {@code dir}.
     *
     * <p>Compares canonical paths <b>including a trailing separator</b>. Without that
     * separator {@code "/data/shops_evil/x"} passes a {@code startsWith("/data/shops")}
     * test, which is the bypass this method exists to close. Symlinks and {@code ..} are
     * handled by canonicalisation rather than by blacklisting.
     *
     * <p>Fails closed: if either path cannot be canonicalised, the answer is {@code false}.
     */
    public static boolean isInside(File dir, File child) {
        try {
            String parent = dir.getCanonicalPath();
            String target = child.getCanonicalPath();
            if (!parent.endsWith(File.separator)) parent = parent + File.separator;
            return target.startsWith(parent);
        } catch (IOException e) {
            WaveDefenseMod.LOGGER.warn("[WaveDefense] Could not canonicalise path for containment check: {}",
                e.getMessage());
            return false;
        }
    }

    /**
     * Containment check with the rejection already logged.
     *
     * @param who  player name, for the log line
     * @param what the client-supplied string, for the log line
     * @return true when the path is safe to use
     */
    public static boolean checkInside(File dir, File child, String who, String what) {
        if (isInside(dir, child)) return true;
        WaveDefenseMod.LOGGER.warn("[WaveDefense] Rejected path-traversal attempt by {}: {}", who, what);
        return false;
    }
}
