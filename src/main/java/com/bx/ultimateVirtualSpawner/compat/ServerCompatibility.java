package com.bx.ultimateVirtualSpawner.compat;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServerCompatibility {

    public static final String DEFAULT_PAPER_MIN = "1.21.10";
    public static final String DEFAULT_PAPER_MAX = "26.2";
    public static final String DEFAULT_FOLIA_MIN = "1.21.11";
    public static final String DEFAULT_FOLIA_MAX = "26.2";

    private static final String RESOURCE = "compatibility.properties";

    public enum Failure {
        NONE,
        TOO_OLD,
        TOO_NEW,
        UNKNOWN_VERSION
    }

    public record Result(
            boolean compatible,
            Failure failure,
            ServerPlatform platform,
            MinecraftVersion detected,
            MinecraftVersion minimum,
            MinecraftVersion maximum,
            String rawServerVersion
    ) {
        public String rangeLabel() {
            return (minimum == null ? "?" : minimum.normalized())
                    + " - "
                    + (maximum == null ? "?" : maximum.normalized());
        }

        public String detectedLabel() {
            return detected == null ? (rawServerVersion == null ? "unknown" : rawServerVersion) : detected.normalized();
        }
    }

    private final ServerPlatform platform;
    private final MinecraftVersion paperMinimum;
    private final MinecraftVersion paperMaximum;
    private final MinecraftVersion foliaMinimum;
    private final MinecraftVersion foliaMaximum;

    private ServerCompatibility(
            ServerPlatform platform,
            MinecraftVersion paperMinimum,
            MinecraftVersion paperMaximum,
            MinecraftVersion foliaMinimum,
            MinecraftVersion foliaMaximum
    ) {
        this.platform = platform;
        this.paperMinimum = paperMinimum;
        this.paperMaximum = paperMaximum;
        this.foliaMinimum = foliaMinimum;
        this.foliaMaximum = foliaMaximum;
    }

    public static ServerCompatibility load(Plugin plugin) {
        Properties properties = readProperties(plugin);
        return new ServerCompatibility(
                ServerPlatform.detect(),
                versionOf(properties, "paper.min", DEFAULT_PAPER_MIN),
                versionOf(properties, "paper.max", DEFAULT_PAPER_MAX),
                versionOf(properties, "folia.min", DEFAULT_FOLIA_MIN),
                versionOf(properties, "folia.max", DEFAULT_FOLIA_MAX)
        );
    }

    static ServerCompatibility forRanges(
            ServerPlatform platform,
            String paperMinimum,
            String paperMaximum,
            String foliaMinimum,
            String foliaMaximum
    ) {
        return new ServerCompatibility(
                platform,
                MinecraftVersion.parse(paperMinimum),
                MinecraftVersion.parse(paperMaximum),
                MinecraftVersion.parse(foliaMinimum),
                MinecraftVersion.parse(foliaMaximum)
        );
    }

    public Result evaluate(ServerPlatform target, MinecraftVersion detected, boolean strict) {
        MinecraftVersion minimum = minimumFor(target);
        MinecraftVersion maximum = maximumFor(target);

        if (detected == null) {
            return new Result(!strict, strict ? Failure.UNKNOWN_VERSION : Failure.NONE,
                    target, null, minimum, maximum, null);
        }
        if (minimum != null && detected.compareTo(minimum) < 0) {
            return new Result(false, Failure.TOO_OLD, target, detected, minimum, maximum, detected.raw());
        }
        if (maximum != null && detected.compareTo(maximum) > 0) {
            return new Result(false, Failure.TOO_NEW, target, detected, minimum, maximum, detected.raw());
        }
        return new Result(true, Failure.NONE, target, detected, minimum, maximum, detected.raw());
    }

    public ServerPlatform platform() {
        return platform;
    }

    public MinecraftVersion minimumFor(ServerPlatform target) {
        return target.isFolia() ? foliaMinimum : paperMinimum;
    }

    public MinecraftVersion maximumFor(ServerPlatform target) {
        return target.isFolia() ? foliaMaximum : paperMaximum;
    }

    public Result check(boolean strict) {
        MinecraftVersion detected = MinecraftVersion.current();
        Result result = evaluate(platform, detected, strict);
        if (detected != null) {
            return result;
        }
        return new Result(result.compatible(), result.failure(), result.platform(), null,
                result.minimum(), result.maximum(), rawServerVersion());
    }

    public List<String> describeFailure(Result result, String pluginName, String pluginVersion) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("+--------------------------------------------------------------+");
        lines.add("|            " + pad(pluginName + " FAILED TO ENABLE", 50) + "|");
        lines.add("+--------------------------------------------------------------+");
        lines.add("| " + pad("This server is not supported by this plugin build.", 61) + "|");
        lines.add("| " + pad("", 61) + "|");
        lines.add("| " + pad("Plugin version   : " + pluginVersion, 61) + "|");
        lines.add("| " + pad("Server software  : " + result.platform().displayName(), 61) + "|");
        lines.add("| " + pad("Server version   : " + result.detectedLabel(), 61) + "|");
        lines.add("| " + pad("Supported range  : " + result.rangeLabel(), 61) + "|");
        lines.add("| " + pad("", 61) + "|");

        switch (result.failure()) {
            case TOO_OLD -> {
                lines.add("| " + pad("Reason: your Minecraft version is TOO OLD.", 61) + "|");
                lines.add("| " + pad("Update the server to at least "
                        + (result.minimum() == null ? "?" : result.minimum().normalized()) + ".", 61) + "|");
            }
            case TOO_NEW -> {
                lines.add("| " + pad("Reason: your Minecraft version is TOO NEW.", 61) + "|");
                lines.add("| " + pad("Wait for a plugin build that supports "
                        + result.detectedLabel() + ".", 61) + "|");
            }
            case UNKNOWN_VERSION -> {
                lines.add("| " + pad("Reason: the Minecraft version could not be detected.", 61) + "|");
                lines.add("| " + pad("Reported as: " + shorten(result.rawServerVersion(), 46), 61) + "|");
                lines.add("| " + pad("Set COMPATIBILITY.STRICT to false in config.yml to", 61) + "|");
                lines.add("| " + pad("start anyway (unsupported, at your own risk).", 61) + "|");
            }
            default -> lines.add("| " + pad("Reason: unsupported server.", 61) + "|");
        }

        lines.add("| " + pad("", 61) + "|");
        lines.add("| " + pad("Supported software:", 61) + "|");
        lines.add("| " + pad("  Paper / Spigot / Bukkit : "
                + rangeLabel(paperMinimum, paperMaximum), 61) + "|");
        lines.add("| " + pad("  Folia                   : "
                + rangeLabel(foliaMinimum, foliaMaximum), 61) + "|");
        lines.add("+--------------------------------------------------------------+");
        lines.add("");
        return lines;
    }

    public void logPlatformNotes(Logger logger) {
        if (!platform.isFolia() && ServerPlatform.foliaSchedulerPresent()) {
            logger.warning("[Compatibility] Folia classes detected on a non-Folia platform report; "
                    + "running in Folia-safe mode.");
        }
    }

    private static String rangeLabel(MinecraftVersion minimum, MinecraftVersion maximum) {
        return (minimum == null ? "?" : minimum.normalized()) + " - " + (maximum == null ? "?" : maximum.normalized());
    }

    private static String rawServerVersion() {
        try {
            return org.bukkit.Bukkit.getBukkitVersion() + " / " + org.bukkit.Bukkit.getVersion();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static Properties readProperties(Plugin plugin) {
        Properties properties = new Properties();
        try (InputStream stream = plugin.getResource(RESOURCE)) {
            if (stream != null) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[Compatibility] Failed to read " + RESOURCE + ", falling back to built-in ranges.", exception);
        }
        return properties;
    }

    private static MinecraftVersion versionOf(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        MinecraftVersion version = MinecraftVersion.parse(value);
        return version == null ? MinecraftVersion.parse(fallback) : version;
    }

    private static String pad(String text, int width) {
        String value = shorten(text, width);
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    private static String shorten(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() <= width ? value : value.substring(0, Math.max(0, width - 3)) + "...";
    }
}
