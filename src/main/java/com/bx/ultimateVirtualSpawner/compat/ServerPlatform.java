package com.bx.ultimateVirtualSpawner.compat;

import java.util.Locale;

public enum ServerPlatform {

    FOLIA("Folia"),
    PAPER("Paper"),
    SPIGOT("Spigot"),
    BUKKIT("Bukkit");

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isFolia() {
        return this == FOLIA;
    }

    public boolean isPaperSpigotFamily() {
        return this != FOLIA;
    }

    public static ServerPlatform detect() {
        if (classPresent("io.papermc.paper.threadedregions.RegionizedServer")
                || classPresent("io.papermc.paper.threadedregions.scheduler.RegionScheduler")
                && classPresent("io.papermc.paper.threadedregions.ThreadedRegionizer")) {
            return FOLIA;
        }
        if (classPresent("com.destroystokyo.paper.PaperConfig")
                || classPresent("io.papermc.paper.configuration.Configuration")
                || classPresent("io.papermc.paper.threadedregions.scheduler.AsyncScheduler")) {
            return PAPER;
        }
        if (classPresent("org.spigotmc.SpigotConfig")) {
            return SPIGOT;
        }
        return BUKKIT;
    }

    public static boolean foliaSchedulerPresent() {
        return classPresent("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public static ServerPlatform fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return BUKKIT;
        }
        String normalized = raw.trim().toUpperCase(Locale.US);
        for (ServerPlatform platform : values()) {
            if (platform.name().equals(normalized)) {
                return platform;
            }
        }
        return BUKKIT;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
