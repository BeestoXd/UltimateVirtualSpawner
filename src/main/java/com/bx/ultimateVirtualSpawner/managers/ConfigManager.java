package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConfigManager {

    private static final String[] FILES = {
            "config.yml",
            "spawners.yml",
            "menus.yml",
            "messages.yml",
            "sounds.yml"
    };

    private final UltimateVirtualSpawner plugin;

    private FileConfiguration config;
    private FileConfiguration spawners;
    private FileConfiguration menus;
    private FileConfiguration messages;
    private FileConfiguration sounds;

    public ConfigManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        saveDefaults();
        reloadAll();
    }

    private void saveDefaults() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("[Config] Could not create the plugin data folder.");
        }
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) {
                plugin.saveResource(name, false);
            }
        }
    }

    public void reloadAll() {
        config = load("config.yml");
        spawners = load("spawners.yml");
        menus = load("menus.yml");
        messages = load("messages.yml");
        sounds = load("sounds.yml");
    }

    public void reloadSpawners() {
        spawners = load("spawners.yml");
    }

    public void reloadMenus() {
        menus = load("menus.yml");
    }

    public void reloadMessages() {
        messages = load("messages.yml");
    }

    public void reloadSounds() {
        sounds = load("sounds.yml");
    }

    private FileConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        FileConfiguration defaults = packagedDefaults(name);
        if (defaults != null) {
            loaded.setDefaults(defaults);
            loaded.options().copyDefaults(true);
            try {
                loaded.save(file);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[Config] Failed to write missing defaults into " + name, exception);
            }
        }
        return loaded;
    }

    private FileConfiguration packagedDefaults(String name) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[Config] Failed to read packaged defaults for " + name, exception);
            return null;
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getSpawners() {
        return spawners;
    }

    public FileConfiguration getMenus() {
        return menus;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getSounds() {
        return sounds;
    }
}
