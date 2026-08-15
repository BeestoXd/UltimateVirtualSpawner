package com.bx.ultimateVirtualSpawner.listeners;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class SpawnerInteractListener implements Listener {

    private final UltimateVirtualSpawner plugin;

    public SpawnerInteractListener(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(block);
        if (instance == null) {
            instance = plugin.getSpawnerManager().restoreOrphanedSpawner(block);
        }
        if (instance == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        event.setCancelled(true);

        if (plugin.getSpawnerManager().isSpawnerItem(held)) {
            var result = plugin.getSpawnerManager().stackSpawner(player, block, held);
            player.sendMessage(result.message());
            if (result.success()) {
                int consumed = result.consumedAmount() > 0 ? result.consumedAmount() : (player.isSneaking() ? held.getAmount() : 1);
                plugin.getSpawnerManager().consumeHeldSpawnerItem(player, consumed);
                player.updateInventory();
            }
            return;
        }

        if (!plugin.getSpawnerManager().canOpen(player, instance)) {
            plugin.getMessageManager().send(player, "SPAWNER.NO_ACCESS");
            return;
        }

        plugin.getSpawnerManager().openMainMenu(player, instance);
        plugin.getAntiEspManager().updatePlayer(player);
    }
}
