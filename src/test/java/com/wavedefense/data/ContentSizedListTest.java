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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the invariant behind the "payload may not be larger than 32767 bytes" family of
 * bugs.
 *
 * <p>Several location lists hold modded items whose NBT can be enormous, so they are kept
 * out of whole-location packets and shipped on their own chunked channel. That split has
 * a sharp edge: the sender omitting a list and the handler treating its absence as a
 * deletion are two halves of one contract. Break the pairing and the list is silently
 * erased — which is exactly what happened to shop points, and nearly happened again to
 * per-team kits and PvP spawn points.
 *
 * <p>These tests fail the build when a list is declared content-sized without anything to
 * carry it, or when a heavyweight list is left riding inside a location payload.
 */
class ContentSizedListTest {

    private static final Path SRC = Paths.get("src/main/java/com/wavedefense");

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + p, e);
        }
    }

    private static List<Path> allSources() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SRC)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        } catch (IOException e) {
            throw new AssertionError("Could not walk " + SRC, e);
        }
        return out;
    }

    /** The keys declared content-sized, read from LocationSection's own list. */
    private static Set<String> declaredContentSized() {
        String src = read(SRC.resolve("data/LocationSection.java"));
        int start = src.indexOf("CONTENT_SIZED");
        assertTrue(start > 0, "LocationSection.CONTENT_SIZED not found — was it renamed?");
        int end = src.indexOf(");", start);
        assertTrue(end > start, "malformed CONTENT_SIZED declaration");

        Set<String> keys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"(\\w+)\"").matcher(src.substring(start, end));
        while (m.find()) keys.add(m.group(1));
        assertFalse(keys.isEmpty(), "CONTENT_SIZED parsed as empty — the scan would pass vacuously");
        return keys;
    }

    @Test
    void everyContentSizedListHasSomethingThatSendsIt() {
        Set<String> declared = declaredContentSized();

        // Collect the list keys any screen actually transmits. Two shapes count:
        //   1. a literal key at the call site — sendList(name, "lootSpawns", list)
        //   2. a declared array of keys iterated into sendList, which is how a screen
        //      that owns several lists sends them
        Set<String> carried = new LinkedHashSet<>();
        Pattern literalKey = Pattern.compile("sendList\\(\\s*[^,]+,\\s*\"(\\w+)\"");
        Pattern keyArray   = Pattern.compile("String\\[\\]\\s+\\w*LISTS\\w*\\s*=\\s*\\{([^}]*)\\}");
        Pattern quoted     = Pattern.compile("\"(\\w+)\"");
        boolean shopHasDedicatedPacket = false;

        for (Path p : allSources()) {
            String body = read(p);
            String file = p.getFileName().toString();

            Matcher m = literalKey.matcher(body);
            while (m.find()) carried.add(m.group(1));

            if (body.contains("sendList(")) {
                Matcher arr = keyArray.matcher(body);
                while (arr.find()) {
                    Matcher q = quoted.matcher(arr.group(1));
                    while (q.find()) carried.add(q.group(1));
                }
            }

            // The shop predates the generic channel and keeps its own packets.
            if (!file.equals("ShopItemOpPacket.java") && body.contains("ShopItemOpPacket.")) {
                shopHasDedicatedPacket = true;
            }
        }
        if (shopHasDedicatedPacket) carried.add("shopItems");

        List<String> orphaned = new ArrayList<>();
        for (String key : declared) {
            if (!carried.contains(key)) orphaned.add(key);
        }

        assertTrue(orphaned.isEmpty(),
            "These lists are excluded from location packets but nothing sends them, so any\n"
          + "edit to them is silently discarded. Give each one a transport (see\n"
          + "ReplaceLocationListPacket.sendList) or drop it from CONTENT_SIZED.\n  "
          + String.join("\n  ", orphaned));
    }

    @Test
    void senderAndHandlerBothSkipContentSizedLists() {
        // Both halves of the merge contract must consult the same predicate. If only one
        // does, an omitted list reads as a deletion on the other side.
        String handler = read(SRC.resolve("network/packets/MergeLocationPacket.java"));
        String sender  = read(SRC.resolve("gui/universal/UniversalLocationEditor.java"));

        assertTrue(handler.contains("isContentSizedList"),
            "MergeLocationPacket must skip content-sized keys, or their absence deletes them");
        assertTrue(sender.contains("isContentSizedList"),
            "The editor must omit content-sized keys, or one big shop blows the payload limit");
    }

    @Test
    void wholeLocationPacketCarriesNoContentSizedList() {
        String src = read(SRC.resolve("network/packets/UpdateLocationPacket.java"));
        assertTrue(src.contains("LocationSection.contentSizedLists()"),
            "UpdateLocationPacket must strip content-sized lists from its payload and\n"
          + "preserve the server's copies, otherwise one large shop breaks every save");
    }

    @Test
    void locationBroadcastDoesNotShipShops() {
        // This runs on every login and every change; including shops handed every player
        // the server's entire item catalogue.
        String src = read(SRC.resolve("wave/WaveManager.java"));
        assertTrue(src.contains("locationListWithoutShops"),
            "syncLocationDataToPlayer must strip shops from the broadcast");
    }
}
