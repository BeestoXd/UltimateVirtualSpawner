package com.bx.ultimateVirtualSpawner.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCompatibilityTest {

    private static ServerCompatibility gate(ServerPlatform platform) {
        return ServerCompatibility.forRanges(platform, "1.21.10", "26.2", "1.21.11", "26.2");
    }

    private static ServerCompatibility.Result evaluate(ServerPlatform platform, String version) {
        return gate(platform).evaluate(platform, MinecraftVersion.parse(version), true);
    }

    @ParameterizedTest
    @CsvSource({
            "PAPER,  1.21.10",
            "PAPER,  1.21.11",
            "PAPER,  26.2",
            "SPIGOT, 1.21.10",
            "BUKKIT, 1.21.10",
            "FOLIA,  1.21.11",
            "FOLIA,  26.2"
    })
    @DisplayName("supported combinations enable cleanly")
    void acceptsSupportedCombinations(ServerPlatform platform, String version) {
        ServerCompatibility.Result result = evaluate(platform, version);
        assertTrue(result.compatible(), platform + " " + version + " should be supported");
        assertEquals(ServerCompatibility.Failure.NONE, result.failure());
    }

    @ParameterizedTest
    @CsvSource({
            "PAPER,  1.21.9,  TOO_OLD",
            "PAPER,  1.20.6,  TOO_OLD",
            "PAPER,  1.8.8,   TOO_OLD",
            "PAPER,  26.3,    TOO_NEW",
            "PAPER,  27.1,    TOO_NEW",
            "SPIGOT, 1.21.9,  TOO_OLD",
            "BUKKIT, 26.3,    TOO_NEW",
            "FOLIA,  1.21.10, TOO_OLD",
            "FOLIA,  26.3,    TOO_NEW"
    })
    @DisplayName("unsupported combinations are rejected with the right reason")
    void rejectsUnsupportedCombinations(ServerPlatform platform, String version,
                                        ServerCompatibility.Failure expected) {
        ServerCompatibility.Result result = evaluate(platform, version);
        assertFalse(result.compatible(), platform + " " + version + " should be rejected");
        assertEquals(expected, result.failure());
    }

    @Test
    @DisplayName("Folia demands one patch more than Paper at the shared boundary")
    void foliaBoundaryDiffersFromPaper() {
        assertTrue(evaluate(ServerPlatform.PAPER, "1.21.10").compatible());
        assertFalse(evaluate(ServerPlatform.FOLIA, "1.21.10").compatible());
        assertEquals("1.21.10 - 26.2", evaluate(ServerPlatform.PAPER, "1.21.10").rangeLabel());
        assertEquals("1.21.11 - 26.2", evaluate(ServerPlatform.FOLIA, "1.21.10").rangeLabel());
    }

    @Test
    @DisplayName("an unparseable version is blocked in strict mode and allowed otherwise")
    void unknownVersionHonoursStrictFlag() {
        ServerCompatibility compatibility = gate(ServerPlatform.PAPER);

        ServerCompatibility.Result strict = compatibility.evaluate(ServerPlatform.PAPER, null, true);
        assertFalse(strict.compatible());
        assertEquals(ServerCompatibility.Failure.UNKNOWN_VERSION, strict.failure());

        ServerCompatibility.Result lenient = compatibility.evaluate(ServerPlatform.PAPER, null, false);
        assertTrue(lenient.compatible());
        assertEquals(ServerCompatibility.Failure.NONE, lenient.failure());
    }

    @Test
    @DisplayName("the failure banner is a well-formed box naming the reason and both ranges")
    void failureBannerIsWellFormed() {
        ServerCompatibility compatibility = gate(ServerPlatform.FOLIA);
        ServerCompatibility.Result result =
                compatibility.evaluate(ServerPlatform.FOLIA, MinecraftVersion.parse("1.21.10"), true);

        List<String> banner = compatibility.describeFailure(result, "UltimateVirtualSpawner", "1.0");
        String joined = String.join("\n", banner);

        assertTrue(joined.contains("FAILED TO ENABLE"), joined);
        assertTrue(joined.contains("TOO OLD"), joined);
        assertTrue(joined.contains("Folia"), joined);
        assertTrue(joined.contains("1.21.11 - 26.2"), joined);
        assertTrue(joined.contains("Paper / Spigot / Bukkit : 1.21.10 - 26.2"), joined);

        List<String> framed = banner.stream().filter(line -> line.startsWith("|") || line.startsWith("+")).toList();
        int width = framed.get(0).length();
        for (String line : framed) {
            assertEquals(width, line.length(), "ragged banner line: [" + line + "]");
            assertTrue(line.endsWith("|") || line.endsWith("+"), "unterminated banner line: [" + line + "]");
        }
    }

    @Test
    @DisplayName("a too-new server is told to wait for a newer plugin build")
    void tooNewBannerAsksForANewerBuild() {
        ServerCompatibility compatibility = gate(ServerPlatform.PAPER);
        ServerCompatibility.Result result =
                compatibility.evaluate(ServerPlatform.PAPER, MinecraftVersion.parse("27.1"), true);

        String joined = String.join("\n", compatibility.describeFailure(result, "UltimateVirtualSpawner", "1.0"));
        assertTrue(joined.contains("TOO NEW"), joined);
        assertTrue(joined.contains("27.1"), joined);
    }
}
