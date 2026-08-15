package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.utils.ColorUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MessageManager {

    private final UltimateVirtualSpawner plugin;

    private String prefix;
    private boolean usePrefix;

    public MessageManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        prefix = messages.getString("PREFIX", "&8[&dSpawners&8] &r");
        usePrefix = messages.getBoolean("USE_PREFIX", true);
    }

    private FileConfiguration messages() {
        return plugin.getConfigManager().getMessages();
    }

    public String raw(String key, Object... replacements) {
        String value = messages().getString(key);
        if (value == null) {
            return "<missing: " + key + ">";
        }
        return applyReplacements(value, replacements);
    }

    public String get(String key, Object... replacements) {
        String value = messages().getString(key);
        if (value == null) {
            return ColorUtils.toComponent("&c<missing message: " + key + ">");
        }
        if (value.isBlank()) {
            return "";
        }
        return ColorUtils.toComponent(prefixed(applyReplacements(value, replacements)));
    }

    public String getPlain(String key, Object... replacements) {
        String value = messages().getString(key);
        if (value == null) {
            return ColorUtils.toComponent("&c<missing message: " + key + ">");
        }
        return ColorUtils.toComponent(applyReplacements(value, replacements));
    }

    public List<String> getList(String key, Object... replacements) {
        List<String> lines = messages().getStringList(key);
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(ColorUtils.toComponent(applyReplacements(line, replacements)));
        }
        return result;
    }

    public void send(CommandSender sender, String key, Object... replacements) {
        if (sender == null) {
            return;
        }
        String message = get(key, replacements);
        if (message.isEmpty()) {
            return;
        }
        sender.sendMessage(message);
    }

    public void sendRawColored(CommandSender sender, String coloredMessage) {
        if (sender == null || coloredMessage == null || coloredMessage.isEmpty()) {
            return;
        }
        sender.sendMessage(coloredMessage);
    }

    public void sendList(CommandSender sender, String key, Object... replacements) {
        if (sender == null) {
            return;
        }
        for (String line : getList(key, replacements)) {
            sender.sendMessage(line);
        }
    }

    public void send(Player player, String key, Object... replacements) {
        send((CommandSender) player, key, replacements);
    }

    public String getPrefix() {
        return ColorUtils.toComponent(prefix);
    }

    private String prefixed(String message) {
        return usePrefix ? prefix + message : message;
    }

    private String applyReplacements(String message, Object... replacements) {
        if (replacements == null || replacements.length == 0) {
            return message;
        }

        String result = message;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            Object rawKey = replacements[index];
            Object rawValue = replacements[index + 1];
            if (rawKey == null) {
                continue;
            }
            String placeholder = rawKey.toString();
            if (!placeholder.startsWith("{")) {
                placeholder = "{" + placeholder + "}";
            }
            result = result.replace(placeholder, rawValue == null ? "" : rawValue.toString());
        }
        return result;
    }
}
