package com.bx.ultimateVirtualSpawner.listeners;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.menus.BaseMenu;
import com.bx.ultimateVirtualSpawner.menus.SpawnerStorageMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class InventoryClickListener implements Listener {

    private final UltimateVirtualSpawner plugin;

    public InventoryClickListener(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof BaseMenu menu)) {
            return;
        }

        if (menu instanceof SpawnerStorageMenu storageMenu) {
            storageMenu.handleInventoryClick(event);
            return;
        }

        event.setCancelled(true);

        Inventory topInventory = event.getView().getTopInventory();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }
        if (event.getCurrentItem() == null) {
            return;
        }
        menu.handleClick(event.getSlot(), player, event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BaseMenu menu)) {
            return;
        }

        if (menu instanceof SpawnerStorageMenu storageMenu) {
            storageMenu.handleInventoryDrag(event);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof BaseMenu menu) {
            menu.onClose(player);
        }
    }
}
