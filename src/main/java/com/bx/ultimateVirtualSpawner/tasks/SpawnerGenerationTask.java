package com.bx.ultimateVirtualSpawner.tasks;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;

public class SpawnerGenerationTask implements Runnable {

    private final UltimateVirtualSpawner plugin;

    private SpawnerGenerationTask(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    public static void start(UltimateVirtualSpawner plugin) {
        long configuredSeconds = plugin.getConfigManager().getSpawners()
                .getLong("SETTINGS.GENERATION_INTERVAL_SECONDS", 5L);
        long periodTicks = Math.max(20L, configuredSeconds * 20L);
        plugin.getSpigotScheduler().runGlobalTimer(new SpawnerGenerationTask(plugin), periodTicks, periodTicks);

        plugin.getSpigotScheduler().runGlobalTimer(() -> {
            if (plugin.getSpawnerManager() != null && plugin.getSpawnerManager().isEnabled()) {
                plugin.getSpawnerManager().refreshOpenStorageMenus();
            }
        }, 20L, 20L);
    }

    @Override
    public void run() {
        if (plugin.getSpawnerManager() != null && plugin.getSpawnerManager().isEnabled()) {
            plugin.getSpawnerManager().processGeneration();
        }
    }
}
