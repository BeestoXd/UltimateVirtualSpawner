package com.bx.ultimateVirtualSpawner;

import com.bx.ultimateVirtualSpawner.commands.EconomyCommand;
import com.bx.ultimateVirtualSpawner.commands.EconomyTabCompleter;
import com.bx.ultimateVirtualSpawner.commands.SpawnerCommand;
import com.bx.ultimateVirtualSpawner.commands.SpawnerTabCompleter;
import com.bx.ultimateVirtualSpawner.compat.ServerCompatibility;
import com.bx.ultimateVirtualSpawner.hooks.SpawnerPlaceholderExpansion;
import com.bx.ultimateVirtualSpawner.listeners.InventoryClickListener;
import com.bx.ultimateVirtualSpawner.listeners.SpawnerBlockListener;
import com.bx.ultimateVirtualSpawner.listeners.SpawnerChunkListener;
import com.bx.ultimateVirtualSpawner.listeners.SpawnerInteractListener;
import com.bx.ultimateVirtualSpawner.listeners.SpawnerVisibilityListener;
import com.bx.ultimateVirtualSpawner.managers.AntiEspManager;
import com.bx.ultimateVirtualSpawner.managers.ConfigManager;
import com.bx.ultimateVirtualSpawner.managers.DatabaseManager;
import com.bx.ultimateVirtualSpawner.managers.EconomyManager;
import com.bx.ultimateVirtualSpawner.managers.MessageManager;
import com.bx.ultimateVirtualSpawner.managers.SpawnerManager;
import com.bx.ultimateVirtualSpawner.managers.WorthManager;
import com.bx.ultimateVirtualSpawner.tasks.SpawnerGenerationTask;
import com.bx.ultimateVirtualSpawner.utils.ColorUtils;
import com.bx.ultimateVirtualSpawner.utils.SpigotScheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class UltimateVirtualSpawner extends JavaPlugin {

    private ServerCompatibility compatibility;
    private SpigotScheduler spigotScheduler;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private WorthManager worthManager;
    private SpawnerManager spawnerManager;
    private AntiEspManager antiEspManager;

    private boolean fullyEnabled;

    @Override
    public void onEnable() {
        compatibility = ServerCompatibility.load(this);

        try {
            configManager = new ConfigManager(this);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load configuration files.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean strict = configManager.getConfig().getBoolean("COMPATIBILITY.STRICT", true);
        boolean gateEnabled = configManager.getConfig().getBoolean("COMPATIBILITY.ENABLED", true);
        ServerCompatibility.Result result = compatibility.check(strict);

        if (!result.compatible()) {
            if (gateEnabled) {
                for (String line : compatibility.describeFailure(result, getName(), getDescription().getVersion())) {
                    getLogger().severe(line);
                }
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().warning("[Compatibility] Server " + result.detectedLabel() + " is outside the supported range "
                    + result.rangeLabel() + ", but COMPATIBILITY.ENABLED is false. Running unsupported.");
        }

        getLogger().info("[Compatibility] " + result.platform().displayName() + " "
                + result.detectedLabel() + " (supported: " + result.rangeLabel() + ")");
        compatibility.logPlatformNotes(getLogger());

        try {
            ColorUtils.init();
            spigotScheduler = new SpigotScheduler(this);
            messageManager = new MessageManager(this);
            databaseManager = new DatabaseManager(this);
            if (!databaseManager.isReady()) {
                getLogger().severe("The database is unavailable, so no spawner data could be read.");
                getLogger().severe("Refusing to start: running now would treat every managed spawner");
                getLogger().severe("as an ordinary vanilla spawner. Fix the database error logged");
                getLogger().severe("above and restart - the stored spawner data itself is untouched.");
                databaseManager.shutdown();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            economyManager = new EconomyManager(this);
            worthManager = new WorthManager(this);
            spawnerManager = new SpawnerManager(this);
            antiEspManager = new AntiEspManager(this);

            registerListeners();
            registerCommands();
            registerPlaceholders();
            SpawnerGenerationTask.start(this);

            fullyEnabled = true;
            getLogger().info(getName() + " v" + getDescription().getVersion() + " enabled on "
                    + result.platform().displayName() + (spigotScheduler.isFolia() ? " (region scheduler)" : "") + ".");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to enable " + getName() + ".", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (!fullyEnabled) {
            return;
        }

        try {
            if (antiEspManager != null) {
                antiEspManager.shutdown();
            }
            if (spawnerManager != null) {
                spawnerManager.shutdown();
            }
            if (economyManager != null) {
                economyManager.shutdown();
            }
            if (databaseManager != null) {
                databaseManager.shutdown();
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Error while shutting down " + getName() + ".", exception);
        }

        getLogger().info(getName() + " disabled.");
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new SpawnerBlockListener(this), this);
        pluginManager.registerEvents(new SpawnerChunkListener(this), this);
        pluginManager.registerEvents(new SpawnerInteractListener(this), this);
        pluginManager.registerEvents(new SpawnerVisibilityListener(this), this);
        pluginManager.registerEvents(new InventoryClickListener(this), this);
    }

    private void registerCommands() {
        PluginCommand spawner = getCommand("spawner");
        if (spawner == null) {
            getLogger().warning("The /spawner command is missing from plugin.yml.");
        } else {
            spawner.setExecutor(new SpawnerCommand(this));
            spawner.setTabCompleter(new SpawnerTabCompleter(this));
        }

        EconomyCommand economyExecutor = new EconomyCommand(this);
        EconomyTabCompleter economyCompleter = new EconomyTabCompleter(this);
        for (String name : new String[]{"balance", "baltop", "pay", "eco"}) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().warning("The /" + name + " command is missing from plugin.yml.");
                continue;
            }
            command.setExecutor(economyExecutor);
            command.setTabCompleter(economyCompleter);
        }
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new SpawnerPlaceholderExpansion(this).register();
            getLogger().info("[Hooks] Registered PlaceholderAPI expansion 'uvs'.");
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "[Hooks] Failed to register the PlaceholderAPI expansion.", throwable);
        }
    }

    public void reloadEverything() {
        configManager.reloadAll();
        messageManager.reload();
        economyManager.reload();
        worthManager.reload();
        spawnerManager.reload();
        antiEspManager.reload();
        antiEspManager.refreshAllPlayers();
    }

    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }

    public ServerCompatibility getCompatibility() {
        return compatibility;
    }

    public SpigotScheduler getSpigotScheduler() {
        return spigotScheduler;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public WorthManager getWorthManager() {
        return worthManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public AntiEspManager getAntiEspManager() {
        return antiEspManager;
    }
}
