package com.bx.ultimateVirtualSpawner.hooks;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class SpawnerPlaceholderExpansion extends PlaceholderExpansion {

    private final UltimateVirtualSpawner plugin;

    public SpawnerPlaceholderExpansion(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "uvs";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.US);

        if (key.equals("total")) {
            return String.valueOf(plugin.getSpawnerManager().getTotalSpawnerCount());
        }
        if (key.equals("types")) {
            return String.valueOf(plugin.getSpawnerManager().getTypeDefinitions().size());
        }
        if (offlinePlayer == null) {
            return null;
        }

        switch (key) {
            case "balance" -> {
                return String.format(Locale.US, "%.2f", plugin.getEconomyManager().getBalance(offlinePlayer));
            }
            case "balance_formatted" -> {
                return plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(offlinePlayer));
            }
            case "balance_short" -> {
                return plugin.getEconomyManager()
                        .formatMoneyCompact(plugin.getEconomyManager().getBalance(offlinePlayer));
            }
            default -> {
            }
        }

        List<SpawnerInstance> owned = plugin.getSpawnerManager().getSpawnersOwnedBy(offlinePlayer.getUniqueId());
        switch (key) {
            case "owned" -> {
                return String.valueOf(owned.size());
            }
            case "owned_stack" -> {
                long total = 0L;
                for (SpawnerInstance instance : owned) {
                    total += instance.getStackAmount();
                }
                return NumberUtils.format(total);
            }
            case "owned_stored" -> {
                long total = 0L;
                for (SpawnerInstance instance : owned) {
                    total += instance.getTotalStoredItems();
                }
                return NumberUtils.format(total);
            }
            case "owned_xp" -> {
                double total = 0D;
                for (SpawnerInstance instance : owned) {
                    total += instance.getStoredXp();
                }
                return String.format(Locale.US, "%.1f", total);
            }
            default -> {
            }
        }

        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) {
            return null;
        }

        if (key.equals("world")) {
            return String.valueOf(plugin.getSpawnerManager().getSpawnersInWorld(player.getWorld().getName()).size());
        }

        if (key.startsWith("looking_")) {
            var block = player.getTargetBlockExact(6);
            SpawnerInstance instance = block == null ? null : plugin.getSpawnerManager().getSpawner(block);
            if (instance == null) {
                return "";
            }
            return switch (key.substring("looking_".length())) {
                case "type" -> plugin.getSpawnerManager().getPlainTypeDisplayName(instance.getMobTypeKey());
                case "owner" -> instance.getOwnerNameSnapshot();
                case "stack" -> NumberUtils.format(instance.getStackAmount());
                case "stored" -> NumberUtils.format(instance.getTotalStoredItems());
                case "xp" -> String.format(Locale.US, "%.1f", instance.getStoredXp());
                case "access" -> instance.getAccessMode().name();
                default -> "";
            };
        }

        return null;
    }
}
