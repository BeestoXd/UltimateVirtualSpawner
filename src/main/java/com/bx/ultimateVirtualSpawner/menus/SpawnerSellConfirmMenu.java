package com.bx.ultimateVirtualSpawner.menus;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.managers.SpawnerManager;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import com.bx.ultimateVirtualSpawner.models.SpawnerTypeDefinition;
import com.bx.ultimateVirtualSpawner.utils.ColorUtils;
import com.bx.ultimateVirtualSpawner.utils.ItemUtils;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

public class SpawnerSellConfirmMenu extends BaseMenu {

    private final long spawnerId;
    private final int returnPage;

    public SpawnerSellConfirmMenu(UltimateVirtualSpawner plugin, long spawnerId, int returnPage) {
        super(plugin, "&8Confirm Sell", 27);
        this.spawnerId = spawnerId;
        this.returnPage = Math.max(1, returnPage);
    }

    @Override
    public void build(Player player) {
        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(spawnerId);
        if (instance == null) {
            inventory = Bukkit.createInventory(this, 27, ColorUtils.toComponent("&8Spawner Missing"));
            clear();
            fill(Material.GRAY_STAINED_GLASS_PANE);
            set(13, ItemUtils.createItem(Material.BARRIER, "&cSpawner Not Found"));
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getMenus();
        String titleStr = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.TITLE", "&8Confirm Sell");
        int menuSize = plugin.getSpawnerManager().normalizeSize(config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.SIZE", 27));
        inventory = Bukkit.createInventory(this, menuSize, ColorUtils.toComponent(titleStr));

        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        String mobLabel = instance.getMobTypeKey().replace('_', ' ').toUpperCase();
        long totalItems = instance.getTotalStoredItems();

        SpawnerManager.SpawnerSellPreview preview = plugin.getSpawnerManager().calculateLootSellPreview(player, instance);

        String formattedPrice = NumberUtils.format(preview.totalPayout());
        String formattedMultiplier = NumberUtils.format(preview.multiplier());
        String formattedItemCount = NumberUtils.format(preview.totalSellableItems() > 0 ? preview.totalSellableItems() : totalItems);

        int cancelSlot = config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.CANCEL-BUTTON.SLOT", 15);
        String cancelMatName = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.CANCEL-BUTTON.MATERIAL", "RED_STAINED_GLASS_PANE");
        Material cancelMat = Material.matchMaterial(cancelMatName);
        if (cancelMat == null) cancelMat = Material.RED_STAINED_GLASS_PANE;
        String cancelTitle = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.CANCEL-BUTTON.TITLE", "&cCancel");
        cancelTitle = replacePlaceholders(cancelTitle, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel);

        List<String> cancelLore = config.getStringList("SPAWNER-MENUS.SELL-CONFIRM-MENU.CANCEL-BUTTON.LORE");
        if (cancelLore.isEmpty()) cancelLore = List.of("&7Cancel transaction and return to storage.");
        List<String> replacedCancelLore = new ArrayList<>();
        for (String line : cancelLore) {
            replacedCancelLore.add(replacePlaceholders(line, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel));
        }
        set(cancelSlot, ItemUtils.createItem(cancelMat, cancelTitle, replacedCancelLore));

        int infoSlot = config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.INFO-ICON.SLOT", 13);
        String infoTitle = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.INFO-ICON.TITLE", "&e" + mobLabel + " LOOT SALE");
        infoTitle = replacePlaceholders(infoTitle, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel);

        List<String> infoLore = config.getStringList("SPAWNER-MENUS.SELL-CONFIRM-MENU.INFO-ICON.LORE");
        if (infoLore.isEmpty()) {
            infoLore = List.of(
                    "&7Stored Items: &f" + NumberUtils.format(totalItems),
                    "",
                    "&eConfirm selling all stored loot?"
            );
        }
        List<String> replacedInfoLore = new ArrayList<>();
        for (String line : infoLore) {
            replacedInfoLore.add(replacePlaceholders(line, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel));
        }

        String infoMatName = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.INFO-ICON.MATERIAL");
        if (infoMatName != null && !infoMatName.isBlank() && Material.matchMaterial(infoMatName) != null) {
            set(infoSlot, ItemUtils.createItem(Material.matchMaterial(infoMatName), infoTitle, replacedInfoLore));
        } else {
            SpawnerTypeDefinition def = plugin.getSpawnerManager().getTypeDefinition(instance.getMobTypeKey());
            String customTexture = def != null ? def.headTexture() : null;
            set(infoSlot, ItemUtils.createMobHead(instance.getMobTypeKey(), customTexture, infoTitle, replacedInfoLore));
        }

        int confirmSlot = config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.CONFIRM-BUTTON.SLOT", 11);
        String confirmMatName = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.CONFIRM-BUTTON.MATERIAL", "LIME_STAINED_GLASS_PANE");
        Material confirmMat = Material.matchMaterial(confirmMatName);
        if (confirmMat == null) confirmMat = Material.LIME_STAINED_GLASS_PANE;
        String confirmTitle = config.getString("SPAWNER-MENUS.SELL-CONFIRM-MENU.CONFIRM-BUTTON.TITLE", "&aConfirm Sell");
        confirmTitle = replacePlaceholders(confirmTitle, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel);

        List<String> confirmLore = config.getStringList("SPAWNER-MENUS.SELL-CONFIRM-MENU.CONFIRM-BUTTON.LORE");
        if (confirmLore.isEmpty()) {
            confirmLore = List.of(
                    "&fTotal Value: &a$" + formattedPrice,
                    "&7Category Multiplier: &e" + formattedMultiplier + "x",
                    "",
                    "&eClick to confirm sale!"
            );
        }
        List<String> replacedConfirmLore = new ArrayList<>();
        for (String line : confirmLore) {
            replacedConfirmLore.add(replacePlaceholders(line, formattedPrice, formattedMultiplier, formattedItemCount, mobLabel));
        }
        set(confirmSlot, ItemUtils.createItem(confirmMat, confirmTitle, replacedConfirmLore));
    }

    private String replacePlaceholders(String text, String price, String multiplier, String itemCount, String mobLabel) {
        if (text == null) return "";
        return text
                .replace("{price}", price)
                .replace("{multiplier}", multiplier)
                .replace("{item_count}", itemCount)
                .replace("{total_items}", itemCount)
                .replace("{mob}", mobLabel);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(spawnerId);
        if (instance == null) {
            player.closeInventory();
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getMenus();
        int cancelSlot = config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.CANCEL-BUTTON.SLOT", 15);
        int confirmSlot = config.getInt("SPAWNER-MENUS.SELL-CONFIRM-MENU.CONFIRM-BUTTON.SLOT", 11);

        if (slot == cancelSlot) {
            plugin.getSpawnerManager().playSellCancelSound(player);
            new SpawnerStorageMenu(plugin, spawnerId, returnPage).open(player);
            return;
        }

        if (slot == confirmSlot) {
            var sellResult = plugin.getSpawnerManager().sellAllLoot(player, instance);
            player.sendMessage(sellResult.message());
            new SpawnerStorageMenu(plugin, spawnerId, returnPage).open(player);
            return;
        }
    }
}
