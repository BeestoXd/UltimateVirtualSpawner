package com.bx.ultimateVirtualSpawner.listeners;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class SpawnerChunkListener implements Listener {

    private final UltimateVirtualSpawner plugin;

    public SpawnerChunkListener(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }

        Chunk chunk = event.getChunk();
        if (plugin.getSpawnerManager().getSpawnersInChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())
                .isEmpty()) {
            return;
        }

        plugin.getSpigotScheduler().runRegion(chunk.getWorld(), chunk.getX(), chunk.getZ(),
                () -> plugin.getSpawnerManager().syncSpawnersInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }
}
