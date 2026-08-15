package com.bx.ultimateVirtualSpawner.compat;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    private final String raw;
    private final int[] components;

    private MinecraftVersion(String raw, int[] components) {
        this.raw = raw;
        this.components = components;
    }

    public static MinecraftVersion parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String cleaned = input.trim();

        int mcMarker = cleaned.toUpperCase(Locale.US).indexOf("(MC:");
        if (mcMarker >= 0) {
            int close = cleaned.indexOf(')', mcMarker);
            cleaned = close > mcMarker
                    ? cleaned.substring(mcMarker + 4, close).trim()
                    : cleaned.substring(mcMarker + 4).trim();
        }

        int dash = cleaned.indexOf('-');
        if (dash > 0) {
            cleaned = cleaned.substring(0, dash);
        }

        int space = cleaned.indexOf(' ');
        if (space > 0) {
            cleaned = cleaned.substring(0, space);
        }

        List<Integer> parsed = new ArrayList<>(4);
        for (String part : cleaned.split("\\.")) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                break;
            }
            try {
                parsed.add(Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
                break;
            }
        }

        if (parsed.isEmpty()) {
            return null;
        }

        int[] components = new int[parsed.size()];
        for (int index = 0; index < parsed.size(); index++) {
            components[index] = parsed.get(index);
        }
        return new MinecraftVersion(input.trim(), components);
    }

    public static MinecraftVersion current() {
        MinecraftVersion version = parse(safe(Bukkit::getBukkitVersion));
        if (version != null) {
            return version;
        }
        return parse(safe(Bukkit::getVersion));
    }

    private static String safe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String raw() {
        return raw;
    }

    public String normalized() {
        StringBuilder builder = new StringBuilder();
        for (int component : components) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(component);
        }
        return builder.toString();
    }

    public boolean isAtLeast(MinecraftVersion other) {
        return other == null || compareTo(other) >= 0;
    }

    public boolean isAtMost(MinecraftVersion other) {
        return other == null || compareTo(other) <= 0;
    }

    public boolean isWithin(MinecraftVersion minimum, MinecraftVersion maximum) {
        return isAtLeast(minimum) && isAtMost(maximum);
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int length = Math.max(components.length, other.components.length);
        for (int index = 0; index < length; index++) {
            int mine = index < components.length ? components[index] : 0;
            int theirs = index < other.components.length ? other.components[index] : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MinecraftVersion version)) {
            return false;
        }
        return compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalized());
    }

    @Override
    public String toString() {
        return normalized();
    }
}
