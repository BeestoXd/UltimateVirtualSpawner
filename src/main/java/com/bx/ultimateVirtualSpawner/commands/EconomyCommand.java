package com.bx.ultimateVirtualSpawner.commands;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.economy.EconomyProvider;
import com.bx.ultimateVirtualSpawner.economy.InternalEconomyProvider;
import com.bx.ultimateVirtualSpawner.managers.EconomyManager;
import com.bx.ultimateVirtualSpawner.managers.MessageManager;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EconomyCommand implements CommandExecutor {

    public static final String PERMISSION_BALANCE = "ultimatevirtualspawner.command.balance";
    public static final String PERMISSION_BALANCE_OTHERS = "ultimatevirtualspawner.command.balance.others";
    public static final String PERMISSION_BALTOP = "ultimatevirtualspawner.command.baltop";
    public static final String PERMISSION_PAY = "ultimatevirtualspawner.command.pay";
    public static final String PERMISSION_ECO = "ultimatevirtualspawner.command.eco";

    private final UltimateVirtualSpawner plugin;

    public EconomyCommand(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.US)) {
            case "balance" -> handleBalance(sender, args);
            case "baltop" -> handleBaltop(sender, args);
            case "pay" -> handlePay(sender, args);
            case "eco" -> handleEco(sender, label, args);
            default -> false;
        };
    }

    private boolean requireInternal(CommandSender sender) {
        EconomyManager economy = plugin.getEconomyManager();
        if (economy.isInternalActive()) {
            return true;
        }
        plugin.getMessageManager().send(sender, "ECONOMY.EXTERNAL_PROVIDER",
                "provider", String.valueOf(economy.getProviderName()));
        return false;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, PERMISSION_BALANCE)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (!requireInternal(sender)) {
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "ECONOMY.BALANCE_USAGE_CONSOLE");
                return true;
            }
            messages.send(player, "ECONOMY.BALANCE_SELF",
                    "balance", plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(player)));
            return true;
        }

        if (!PermissionUtils.has(sender, PERMISSION_BALANCE_OTHERS)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            messages.send(sender, "ECONOMY.UNKNOWN_PLAYER", "player", args[0]);
            return true;
        }

        messages.send(sender, "ECONOMY.BALANCE_OTHER",
                "player", displayName(target, args[0]),
                "balance", plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(target)));
        return true;
    }

    private boolean handleBaltop(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, PERMISSION_BALTOP)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (!requireInternal(sender)) {
            return true;
        }

        InternalEconomyProvider internal = plugin.getEconomyManager().getInternal();
        int limit = plugin.getConfigManager().getConfig().getInt("ECONOMY.INTERNAL.BALTOP_SIZE", 10);
        if (args.length >= 1) {
            try {
                limit = Math.max(1, Math.min(100, Integer.parseInt(args[0].trim())));
            } catch (NumberFormatException ignored) {
            }
        }

        List<InternalEconomyProvider.BalanceEntry> top = internal.getTopBalances(limit);
        if (top.isEmpty()) {
            messages.send(sender, "ECONOMY.BALTOP_EMPTY");
            return true;
        }

        messages.send(sender, "ECONOMY.BALTOP_HEADER",
                "count", String.valueOf(top.size()),
                "total", plugin.getEconomyManager().formatMoneyCompact(internal.getTotalMoney()));
        int rank = 1;
        for (InternalEconomyProvider.BalanceEntry entry : top) {
            messages.send(sender, "ECONOMY.BALTOP_ENTRY",
                    "rank", String.valueOf(rank++),
                    "player", entry.name() == null || entry.name().isBlank() ? entry.uuid().toString() : entry.name(),
                    "balance", plugin.getEconomyManager().formatMoney(entry.balance()));
        }
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, PERMISSION_PAY)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "COMMAND.PLAYER_ONLY");
            return true;
        }
        if (!requireInternal(sender)) {
            return true;
        }
        if (!plugin.getConfigManager().getConfig().getBoolean("ECONOMY.INTERNAL.ALLOW_PAY", true)) {
            messages.send(player, "ECONOMY.PAY_DISABLED");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "ECONOMY.PAY_USAGE");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "COMMAND.PLAYER_NOT_FOUND", "player", args[0]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "ECONOMY.PAY_SELF");
            return true;
        }

        double amount = parseAmount(args[1]);
        if (Double.isNaN(amount)) {
            messages.send(player, "COMMAND.INVALID_AMOUNT");
            return true;
        }

        double minimum = plugin.getConfigManager().getConfig().getDouble("ECONOMY.INTERNAL.MINIMUM_PAY", 0.01D);
        if (amount < minimum) {
            messages.send(player, "ECONOMY.PAY_TOO_SMALL",
                    "minimum", plugin.getEconomyManager().formatMoney(minimum));
            return true;
        }

        InternalEconomyProvider internal = plugin.getEconomyManager().getInternal();
        EconomyProvider.TransactionResult result = internal.transfer(player, target, amount);
        if (!result.success()) {
            messages.send(player, "insufficient funds".equals(result.failureReason())
                    ? "ECONOMY.INSUFFICIENT_FUNDS"
                    : "ECONOMY.PAY_FAILED", "reason", String.valueOf(result.failureReason()));
            return true;
        }

        String formatted = plugin.getEconomyManager().formatMoney(result.amount());
        messages.send(player, "ECONOMY.PAY_SENT",
                "amount", formatted,
                "player", target.getName(),
                "balance", plugin.getEconomyManager().formatMoney(result.newBalance()));
        messages.send(target, "ECONOMY.PAY_RECEIVED",
                "amount", formatted,
                "player", player.getName(),
                "balance", plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(target)));
        return true;
    }

    private boolean handleEco(CommandSender sender, String label, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!PermissionUtils.has(sender, PERMISSION_ECO)) {
            messages.send(sender, "COMMAND.NO_PERMISSION");
            return true;
        }
        if (!requireInternal(sender)) {
            return true;
        }
        if (args.length < 2) {
            messages.sendList(sender, "ECONOMY.ECO_USAGE_LINES", "label", label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.US);
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            messages.send(sender, "ECONOMY.UNKNOWN_PLAYER", "player", args[1]);
            return true;
        }
        String targetName = displayName(target, args[1]);
        InternalEconomyProvider internal = plugin.getEconomyManager().getInternal();

        if (action.equals("reset")) {
            internal.setBalance(target, internal.getStartingBalance());
            messages.send(sender, "ECONOMY.ECO_RESET",
                    "player", targetName,
                    "balance", plugin.getEconomyManager().formatMoney(internal.getStartingBalance()));
            return true;
        }

        if (args.length < 3) {
            messages.sendList(sender, "ECONOMY.ECO_USAGE_LINES", "label", label);
            return true;
        }

        double amount = parseAmount(args[2]);
        if (Double.isNaN(amount)) {
            messages.send(sender, "COMMAND.INVALID_AMOUNT");
            return true;
        }

        EconomyProvider.TransactionResult result;
        String messageKey;
        switch (action) {
            case "give", "add" -> {
                result = internal.deposit(target, amount);
                messageKey = "ECONOMY.ECO_GIVE";
            }
            case "take", "remove" -> {
                result = internal.withdraw(target, amount);
                messageKey = "ECONOMY.ECO_TAKE";
            }
            case "set" -> {
                result = internal.setBalance(target, amount);
                messageKey = "ECONOMY.ECO_SET";
            }
            default -> {
                messages.sendList(sender, "ECONOMY.ECO_USAGE_LINES", "label", label);
                return true;
            }
        }

        if (!result.success()) {
            messages.send(sender, "ECONOMY.ECO_FAILED", "reason", String.valueOf(result.failureReason()));
            return true;
        }

        messages.send(sender, messageKey,
                "amount", plugin.getEconomyManager().formatMoney(result.amount()),
                "player", targetName,
                "balance", plugin.getEconomyManager().formatMoney(result.newBalance()));

        Player online = target.getPlayer();
        if (online != null && online.isOnline() && !online.equals(sender)) {
            messages.send(online, messageKey + "_NOTIFY",
                    "amount", plugin.getEconomyManager().formatMoney(result.amount()),
                    "balance", plugin.getEconomyManager().formatMoney(result.newBalance()));
        }
        return true;
    }

    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        InternalEconomyProvider internal = plugin.getEconomyManager().getInternal();
        if (internal != null) {
            UUID stored = internal.findAccountByName(name);
            if (stored != null) {
                return Bukkit.getOfflinePlayer(stored);
            }
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private String displayName(OfflinePlayer player, String fallback) {
        String name = player.getName();
        return name == null || name.isBlank() ? fallback : name;
    }

    private double parseAmount(String raw) {
        try {
            double parsed = NumberUtils.parse(raw);
            return parsed > 0D && Double.isFinite(parsed) ? parsed : Double.NaN;
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }
}
