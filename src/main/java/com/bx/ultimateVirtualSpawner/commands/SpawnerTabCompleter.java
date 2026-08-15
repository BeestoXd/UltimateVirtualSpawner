package com.bx.ultimateVirtualSpawner.commands;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.managers.SpawnerManager;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpawnerTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("give", "info", "panel", "split", "types", "remove", "reload", "version");
    private static final List<String> AMOUNT_SUGGESTIONS = List.of("1", "16", "64", "128", "1000");

    private final UltimateVirtualSpawner plugin;

    public SpawnerTabCompleter(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, SpawnerManager.ADMIN_PERMISSION)) {
            return List.of();
        }

        return switch (args.length) {
            case 1 -> filter(SUBCOMMANDS, args[0]);
            case 2 -> {
                if (args[0].equalsIgnoreCase("give")) {
                    List<String> names = new ArrayList<>();
                    for (Player online : plugin.getServer().getOnlinePlayers()) {
                        names.add(online.getName());
                    }
                    yield filter(names, args[1]);
                }
                if (args[0].equalsIgnoreCase("split")) {
                    yield filter(AMOUNT_SUGGESTIONS, args[1]);
                }
                yield List.of();
            }
            case 3 -> args[0].equalsIgnoreCase("give")
                    ? filter(new ArrayList<>(plugin.getSpawnerManager().getTypeKeys()), args[2])
                    : List.of();
            case 4 -> args[0].equalsIgnoreCase("give")
                    ? filter(AMOUNT_SUGGESTIONS, args[3])
                    : List.of();
            default -> List.of();
        };
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
