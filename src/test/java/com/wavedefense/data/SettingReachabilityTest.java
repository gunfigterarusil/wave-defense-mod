package com.wavedefense.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against settings that work in-game but cannot be changed by anyone.
 *
 * <p>Twice now a location setting has quietly become unreachable: deleting the legacy
 * editors in v0.3.0 took the mob-spawn-point editor and the starting-items screen with
 * them, and separately seven settings were left with no UI at all. In every case the
 * field kept serializing and the runtime kept honouring it, so nothing failed — the
 * feature simply could not be configured any more, and it took a player report to notice.
 *
 * <p>This test reads the sources directly rather than using reflection, because the
 * question is not "does the method exist" but "does any screen call it". Source scanning
 * is the only way to see that.
 */
class SettingReachabilityTest {

    private static final Path SRC = Paths.get("src/main/java/com/wavedefense");

    /** {@code public void setX(...)} declarations on Location. */
    private static final Pattern SETTER =
        Pattern.compile("public\\s+void\\s+(set\\w+)\\s*\\(");

    /**
     * Settings a player or the runtime owns rather than an admin, so no editor is
     * expected. Keep this list short and justified — it is the escape hatch, and every
     * entry is a promise that the value really is not admin-configurable.
     */
    private static final Set<String> NOT_ADMIN_EDITABLE = Set.of(
        // Runtime team assignment, written by PvpRoundManager during a match.
        "setPlayerTeam",
        // Identity, set at creation and via the rename flow, not an editor field.
        "setName"
    );

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + p, e);
        }
    }

    private static List<Path> javaFilesUnder(String... subdirs) {
        List<Path> out = new ArrayList<>();
        for (String sub : subdirs) {
            Path dir = sub.isEmpty() ? SRC : SRC.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            } catch (IOException e) {
                throw new AssertionError("Could not walk " + dir, e);
            }
        }
        return out;
    }

    /** Every mod source file, so a caller is never missed because of where it lives. */
    private static List<Path> allSources() {
        return javaFilesUnder("");
    }

    /** True when {@code name} appears as a call or a method reference anywhere in {@code sources}. */
    private static boolean isCalledIn(String name, List<String> sources) {
        // Both "loc.setFoo(" and "location::setFoo" count — the editor uses the second
        // form heavily via flushInt(box, location::setFoo).
        Pattern use = Pattern.compile("(?:\\." + Pattern.quote(name) + "\\s*\\()"
                                    + "|(?:::\\s*" + Pattern.quote(name) + "\\b)");
        for (String s : sources) {
            if (use.matcher(s).find()) return true;
        }
        return false;
    }

    @Test
    void everySettingHonouredAtRuntimeCanAlsoBeEdited() {
        assertTrue(Files.isDirectory(SRC),
            "test must run with the module root as working directory; looked for " + SRC.toAbsolutePath());

        String locationSrc = read(SRC.resolve("data/Location.java"));

        List<String> guiSources = new ArrayList<>();
        javaFilesUnder("gui").forEach(p -> guiSources.add(read(p)));
        List<String> runtimeSources = new ArrayList<>();
        javaFilesUnder("wave", "events").forEach(p -> runtimeSources.add(read(p)));

        assertFalse(guiSources.isEmpty(), "no GUI sources found — the scan would pass vacuously");
        assertFalse(runtimeSources.isEmpty(), "no runtime sources found — the scan would pass vacuously");

        // setter -> the getter name the runtime would call
        Map<String, String> unreachable = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();

        Matcher m = SETTER.matcher(locationSrc);
        while (m.find()) {
            String setter = m.group(1);
            if (!seen.add(setter)) continue;
            if (NOT_ADMIN_EDITABLE.contains(setter)) continue;

            String property = setter.substring("set".length());
            Pattern getter = Pattern.compile("\\.(?:get|is)" + Pattern.quote(property) + "\\s*\\(");

            boolean usedAtRuntime = runtimeSources.stream().anyMatch(s -> getter.matcher(s).find());
            if (!usedAtRuntime) continue;              // nothing reads it — not this test's problem

            if (!isCalledIn(setter, guiSources)) {
                unreachable.put(setter, "get/is" + property);
            }
        }

        assertTrue(unreachable.isEmpty(),
            "These Location settings are read by the runtime but no screen can change them.\n"
          + "Either add an editor control, or — if the value is genuinely not admin-owned —\n"
          + "add the setter to NOT_ADMIN_EDITABLE with a reason.\n  "
          + String.join("\n  ", unreachable.keySet()));
    }

    @Test
    void everyConfigOptionIsActuallyRead() {
        String configSrc = read(SRC.resolve("config/WaveDefenseConfig.java"));

        List<String> others = new ArrayList<>();
        javaFilesUnder("wave", "events", "gui", "data", "network", "commands", "monitor", "backup")
            .forEach(p -> others.add(read(p)));
        assertFalse(others.isEmpty(), "no sources found — the scan would pass vacuously");

        Pattern option = Pattern.compile("ForgeConfigSpec\\.\\w+Value\\s+(\\w+)");
        // The config screen reads every option to render it; that alone does not mean the
        // option does anything, which is exactly how 17 dead options went unnoticed.
        List<String> configScreenOnly = new ArrayList<>();
        List<String> sourcesExceptScreen = new ArrayList<>();
        for (Path p : javaFilesUnder("wave", "events", "gui", "data", "network", "commands", "monitor", "backup")) {
            if (p.getFileName().toString().equals("WaveDefenseConfigScreen.java")) continue;
            sourcesExceptScreen.add(read(p));
        }

        Set<String> seen = new LinkedHashSet<>();
        Matcher m = option.matcher(configSrc);
        while (m.find()) {
            String name = m.group(1);
            if (!seen.add(name)) continue;
            Pattern use = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            boolean used = sourcesExceptScreen.stream().anyMatch(s -> use.matcher(s).find());
            if (!used) configScreenOnly.add(name);
        }

        assertTrue(configScreenOnly.isEmpty(),
            "These config options are exposed to admins but nothing reads them, so toggling\n"
          + "them does nothing. Wire each one up or remove it.\n  "
          + String.join("\n  ", configScreenOnly));
    }

    @Test
    void everyScreenCanBeOpened() {
        List<Path> everything = allSources();
        List<String> unreachable = new ArrayList<>();

        for (Path p : javaFilesUnder("gui")) {
            String file = p.getFileName().toString();
            if (!file.endsWith("Screen.java")) continue;
            String cls = file.substring(0, file.length() - ".java".length());

            // Abstract bases are extended, not instantiated.
            if (read(p).contains("abstract class " + cls)) continue;

            // Allow a package prefix: the editor opens most screens fully qualified,
            // e.g. "new com.wavedefense.gui.CompletionRewardScreen(...)".
            Pattern ctor = Pattern.compile("new\\s+(?:[\\w.]+\\.)?" + Pattern.quote(cls) + "\\s*\\(");

            boolean opened = false;
            for (Path q : everything) {
                // A screen constructing itself (e.g. to refresh) does not make it reachable.
                if (q.equals(p)) continue;
                if (ctor.matcher(read(q)).find()) { opened = true; break; }
            }
            if (!opened) unreachable.add(cls);
        }

        assertTrue(unreachable.isEmpty(),
            "These screens exist but nothing opens them — either wire them up or delete them.\n  "
          + String.join("\n  ", unreachable));
    }
}
