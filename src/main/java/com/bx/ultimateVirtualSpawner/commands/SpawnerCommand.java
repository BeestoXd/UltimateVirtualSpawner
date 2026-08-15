package com.bx.ultimateVirtualSpawner.commands;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.compat.ServerCompatibility;
import com.bx.ultimateVirtualSpawner.managers.MessageManager;
import com.bx.ultimateVirtualSpawner.managers.SpawnerManager;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import com.bx.ultimateVirtualSpawner.models.SpawnerTypeDefinition;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public class SpawnerCommand implements CommandExecutor {

    private final UltimateVirtualSpawner plugin;

    public SpawnerCommand(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager messages = plugin.getMessageManager();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                return sendUsage(sender, label);
            }
            if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
                messages.send(sender, "COMMAND.NO_PERMISSION");
                return true;
            }
            plugin.getSpawnerManager().openPanel(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.US)) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "panel" -> handlePanel(sender);
            case "info" -> handleInfo(sender);
            case "split" -> handleSplit(sender, args);
            case "types", "list" -> handleTypes(sender);
            case "version", "ver" -> handleVersion(sender);
            case "remove", "forcebreak" -> handleRemove(sender);
            default -> sendUsage(sender, label);
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "COMMAND.GIVE_USAGE");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "COMMAND.PLAYER_NOT_FOUND", "player", args[1]);
            return true;
        }

        long amount;
        try {
            amount = args.length >= 4 ? NumberUtils.parseLong(args[3]) : 1L;
        } catch (NumberFormatException exception) {
            messages.send(sender, "COMMAND.INVALID_AMOUNT");
            return true;
        }

        if (amount <= 0L) {
            messages.send(sender, "COMMAND.INVALID_AMOUNT");
            return true;
        }

        var result = plugin.getSpawnerManager().giveSpawner(target, args[2], amount);
        messages.sendRawColored(sender, result.message());
        if (result.success() && !sender.equals(target)) {
            messages.send(target, "SPAWNER.GIVE_RECEIVED",
                    "amount", NumberUtils.format(amount),
                    "type", plugin.getSpawnerManager().getPlainTypeDisplayName(args[2]));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }

        plugin.reloadEverything();
        plugin.getMessageManager().send(sender, "COMMAND.RELOADED");
        return true;
    }

    private boolean handlePanel(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "COMMAND.PLAYER_ONLY");
            return true;
        }
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }

        plugin.getSpawnerManager().openPanel(player);
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "COMMAND.PLAYER_ONLY");
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        SpawnerInstance instance = target == null ? null : plugin.getSpawnerManager().getSpawner(target);
        if (instance == null) {
            messages.send(player, "COMMAND.LOOK_AT_SPAWNER");
            return true;
        }

        messages.sendList(player, "COMMAND.INFO_LINES",
                "type", plugin.getSpawnerManager().getPlainTypeDisplayName(instance.getMobTypeKey()),
                "owner", instance.getOwnerNameSnapshot(),
                "stack", NumberUtils.format(instance.getStackAmount()),
                "stored", NumberUtils.format(instance.getTotalStoredItems()),
                "xp", String.format(Locale.US, "%.1f", instance.getStoredXp()),
                "access", instance.getAccessMode().name(),
                "world", instance.getWorld(),
                "x", String.valueOf(instance.getX()),
                "y", String.valueOf(instance.getY()),
                "z", String.valueOf(instance.getZ()));
        return true;
    }

    private boolean handleRemove(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "COMMAND.PLAYER_ONLY");
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        SpawnerInstance instance = target == null ? null : plugin.getSpawnerManager().getSpawner(target);
        if (instance == null) {
            messages.send(player, "COMMAND.LOOK_AT_SPAWNER");
            return true;
        }

        target.setType(Material.AIR, false);
        messages.sendRawColored(player, plugin.getSpawnerManager().removeSpawner(instance, false, player).message());
        return true;
    }

    private boolean handleTypes(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }

        messages.send(sender, "COMMAND.TYPES_HEADER",
                "count", String.valueOf(plugin.getSpawnerManager().getTypeDefinitions().size()));
        for (SpawnerTypeDefinition definition : plugin.getSpawnerManager().getTypeDefinitions()) {
            messages.send(sender, "COMMAND.TYPES_ENTRY",
                    "key", definition.key(),
                    "name", definition.displayName(),
                    "entity", definition.entityType().name(),
                    "drops", String.valueOf(definition.drops().size()));
        }
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();
        ServerCompatibility compatibility = plugin.getCompatibility();
        ServerCompatibility.Result result = compatibility.check(false);

        messages.sendList(sender, "COMMAND.VERSION_LINES",
                "version", plugin.getDescription().getVersion(),
                "platform", result.platform().displayName(),
                "server", result.detectedLabel(),
                "range", result.rangeLabel(),
                "scheduler", plugin.getSpigotScheduler().isFolia() ? "Folia (regionised)" : "Bukkit (global)",
                "spawners", String.valueOf(plugin.getSpawnerManager().getTotalSpawnerCount()),
                "economy", plugin.getEconomyManager().isAvailable()
                        ? plugin.getEconomyManager().getProviderName()
                                + " (mode: " + plugin.getEconomyManager().getMode() + ")"
                        : "unavailable (mode: " + plugin.getEconomyManager().getMode() + ")");
        return true;
    }

    private boolean handleSplit(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "COMMAND.PLAYER_ONLY");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.getSpawnerManager().isSpawnerItem(hand)) {
            messages.send(player, "COMMAND.SPLIT_HOLD_ITEM");
            return true;
        }

        long currentAmount = plugin.getSpawnerManager().getSpawnerItemAmount(hand);
        if (currentAmount <= 1L) {
            messages.send(player, "COMMAND.SPLIT_TOO_SMALL");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "COMMAND.SPLIT_USAGE");
            return true;
        }

        long splitAmount;
        try {
            splitAmount = NumberUtils.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "COMMAND.INVALID_AMOUNT");
            return true;
        }

        if (splitAmount <= 0L) {
            messages.send(player, "COMMAND.INVALID_AMOUNT");
            return true;
        }
        if (splitAmount >= currentAmount) {
            messages.send(player, "COMMAND.SPLIT_TOO_LARGE", "current", NumberUtils.format(currentAmount));
            return true;
        }

        String typeKey = plugin.getSpawnerManager().getSpawnerItemType(hand);
        ItemStack splitItem = plugin.getSpawnerManager().createSpawnerItem(typeKey, splitAmount);
        if (splitItem == null) {
            messages.send(player, "COMMAND.SPLIT_FAILED");
            return true;
        }

        long remainingAmount = currentAmount - splitAmount;
        plugin.getSpawnerManager().updateSpawnerItemAmount(hand, remainingAmount);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(splitItem);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        messages.send(player, "COMMAND.SPLIT_SUCCESS",
                "amount", NumberUtils.format(splitAmount),
                "remaining", NumberUtils.format(remainingAmount));
        return true;
    }

    private boolean sendUsage(CommandSender sender, String label) {
        plugin.getMessageManager().sendList(sender, "COMMAND.USAGE_LINES", "label", label);
        return true;
    }
}
