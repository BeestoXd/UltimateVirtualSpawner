package com.bx.ultimateVirtualSpawner.commands;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EconomyTabCompleter implements TabCompleter {

    private static final List<String> ECO_ACTIONS = List.of("give", "take", "set", "reset");
    private static final List<String> AMOUNTS = List.of("100", "1000", "10K", "1M");

    private final UltimateVirtualSpawner plugin;

    public EconomyTabCompleter(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.US);

        return switch (name) {
            case "balance" -> args.length == 1
                    && PermissionUtils.has(sender, EconomyCommand.PERMISSION_BALANCE_OTHERS)
                    ? filter(onlineNames(), args[0])
                    : List.of();
            case "pay" -> {
                if (!PermissionUtils.has(sender, EconomyCommand.PERMISSION_PAY)) {
                    yield List.of();
                }
                yield switch (args.length) {
                    case 1 -> filter(onlineNames(), args[0]);
                    case 2 -> filter(AMOUNTS, args[1]);
                    default -> List.of();
                };
            }
            case "eco" -> {
                if (!PermissionUtils.has(sender, EconomyCommand.PERMISSION_ECO)) {
                    yield List.of();
                }
                yield switch (args.length) {
                    case 1 -> filter(ECO_ACTIONS, args[0]);
                    case 2 -> filter(onlineNames(), args[1]);
                    case 3 -> args[0].equalsIgnoreCase("reset") ? List.of() : filter(AMOUNTS, args[2]);
                    default -> List.of();
                };
            }
            default -> List.of();
        };
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.US);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.US).startsWith(normalized)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
