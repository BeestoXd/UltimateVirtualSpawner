package com.bx.ultimateVirtualSpawner.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {

    private static final MinecraftVersion PAPER_MIN = MinecraftVersion.parse("1.21.10");
    private static final MinecraftVersion FOLIA_MIN = MinecraftVersion.parse("1.21.11");
    private static final MinecraftVersion MAX = MinecraftVersion.parse("26.2");

    @ParameterizedTest
    @CsvSource({
            "1.21.10-R0.1-SNAPSHOT, 1.21.10",
            "1.21.11-R0.1-SNAPSHOT, 1.21.11",
            "26.2-R0.1-SNAPSHOT,    26.2",
            "1.21.10,               1.21.10",
            "1.21.11-pre2,          1.21.11",
            "26.2 (MC: 26.2),       26.2",
            "'1.21.10 (MC: 1.21.10)', 1.21.10",
            "git-Paper-196 (MC: 1.21.10), 1.21.10"
    })
    @DisplayName("parses the decorations servers append to their version string")
    void parsesServerVersionStrings(String input, String expected) {
        MinecraftVersion version = MinecraftVersion.parse(input);
        assertTrue(version != null, "expected " + input + " to parse");
        assertEquals(expected, version.normalized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-version", "vX.Y"})
    @DisplayName("returns null when there is no numeric version to recover")
    void rejectsUnparseableInput(String input) {
        assertNull(MinecraftVersion.parse(input));
    }

    @Test
    @DisplayName("null input is unparseable")
    void rejectsNull() {
        assertNull(MinecraftVersion.parse(null));
    }

    @Test
    @DisplayName("the calendar scheme sorts after the classic scheme")
    void ordersClassicBeforeCalendarScheme() {
        assertTrue(MinecraftVersion.parse("1.21.10").compareTo(MinecraftVersion.parse("1.21.11")) < 0);
        assertTrue(MinecraftVersion.parse("1.21.11").compareTo(MinecraftVersion.parse("26.1")) < 0);
        assertTrue(MinecraftVersion.parse("26.1").compareTo(MinecraftVersion.parse("26.2")) < 0);
        assertTrue(MinecraftVersion.parse("26.2").compareTo(MinecraftVersion.parse("26.10")) < 0);
    }

    @Test
    @DisplayName("missing trailing components count as zero")
    void treatsMissingComponentsAsZero() {
        assertEquals(0, MinecraftVersion.parse("1.21").compareTo(MinecraftVersion.parse("1.21.0")));
        assertTrue(MinecraftVersion.parse("1.21").compareTo(MinecraftVersion.parse("1.21.1")) < 0);
        assertEquals(0, MinecraftVersion.parse("26.2").compareTo(MinecraftVersion.parse("26.2.0")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21.10", "1.21.11", "1.21.99", "26.1", "26.2"})
    @DisplayName("Paper/Spigot/Bukkit accepts 1.21.10 through 26.2")
    void acceptsPaperRange(String raw) {
        assertTrue(MinecraftVersion.parse(raw).isWithin(PAPER_MIN, MAX), raw + " should be supported on Paper");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21.9", "1.21", "1.20.6", "1.8.8", "26.3", "27.1"})
    @DisplayName("Paper/Spigot/Bukkit rejects anything outside 1.21.10 - 26.2")
    void rejectsOutsidePaperRange(String raw) {
        assertFalse(MinecraftVersion.parse(raw).isWithin(PAPER_MIN, MAX), raw + " should be rejected on Paper");
    }

    @Test
    @DisplayName("Folia starts one patch later than Paper")
    void foliaRangeStartsAt1_21_11() {
        MinecraftVersion boundary = MinecraftVersion.parse("1.21.10");
        assertTrue(boundary.isWithin(PAPER_MIN, MAX), "1.21.10 is valid on Paper");
        assertFalse(boundary.isWithin(FOLIA_MIN, MAX), "1.21.10 is below the Folia minimum");
        assertTrue(MinecraftVersion.parse("1.21.11").isWithin(FOLIA_MIN, MAX));
        assertTrue(MinecraftVersion.parse("26.2").isWithin(FOLIA_MIN, MAX));
        assertFalse(MinecraftVersion.parse("26.3").isWithin(FOLIA_MIN, MAX));
    }

    @Test
    @DisplayName("boundaries are inclusive on both ends")
    void boundariesAreInclusive() {
        assertTrue(PAPER_MIN.isWithin(PAPER_MIN, MAX));
        assertTrue(MAX.isWithin(PAPER_MIN, MAX));
        assertTrue(FOLIA_MIN.isWithin(FOLIA_MIN, MAX));
    }

    @Test
    @DisplayName("equality and hashing ignore the raw decorations")
    void equalityIgnoresDecorations() {
        MinecraftVersion decorated = MinecraftVersion.parse("1.21.10-R0.1-SNAPSHOT");
        MinecraftVersion plain = MinecraftVersion.parse("1.21.10");
        assertEquals(plain, decorated);
        assertEquals(plain.hashCode(), decorated.hashCode());
        assertEquals("1.21.10-R0.1-SNAPSHOT", decorated.raw());
    }
}
