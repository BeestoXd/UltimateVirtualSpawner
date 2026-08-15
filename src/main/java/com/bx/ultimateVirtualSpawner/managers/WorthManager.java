package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WorthManager {

    public record WorthResult(boolean sellable, double unitWorth) {
        public static final WorthResult NOT_SELLABLE = new WorthResult(false, 0D);
    }

    private final UltimateVirtualSpawner plugin;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private final Map<String, Double> permissionMultipliers = new LinkedHashMap<>();

    private boolean sellEnabled;
    private double globalMultiplier;

    public WorthManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        permissionMultipliers.clear();

        FileConfiguration config = plugin.getConfigManager().getSpawners();
        sellEnabled = config.getBoolean("SELL.ENABLED", true);
        globalMultiplier = Math.max(0D, config.getDouble("SELL.MULTIPLIER", 1.0D));

        ConfigurationSection pricesSection = config.getConfigurationSection("SELL.PRICES");
        if (pricesSection != null) {
            for (String key : pricesSection.getKeys(false)) {
                Material material = Material.matchMaterial(key.trim().toUpperCase(Locale.US));
                if (material == null) {
                    plugin.getLogger().warning("[Worth] Unknown material in SELL.PRICES: " + key);
                    continue;
                }
                double price = Math.max(0D, pricesSection.getDouble(key, 0D));
                if (price > 0D) {
                    prices.put(material, price);
                }
            }
        }

        ConfigurationSection typesSection = config.getConfigurationSection("TYPES");
        if (typesSection != null) {
            for (String typeKey : typesSection.getKeys(false)) {
                ConfigurationSection dropsSection =
                        typesSection.getConfigurationSection(typeKey + ".DROPS");
                if (dropsSection == null) {
                    continue;
                }
                for (String dropKey : dropsSection.getKeys(false)) {
                    ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropKey);
                    if (dropSection == null || !dropSection.contains("SELL_PRICE")) {
                        continue;
                    }
                    Material material = Material.matchMaterial(
                            dropSection.getString("MATERIAL", dropKey).trim().toUpperCase(Locale.US));
                    if (material == null) {
                        continue;
                    }
                    double price = Math.max(0D, dropSection.getDouble("SELL_PRICE", 0D));
                    if (price > 0D) {
                        prices.put(material, price);
                    }
                }
            }
        }

        ConfigurationSection multiplierSection = config.getConfigurationSection("SELL.PERMISSION_MULTIPLIERS");
        if (multiplierSection != null) {
            for (String permission : multiplierSection.getKeys(false)) {
                double multiplier = multiplierSection.getDouble(permission, 1.0D);
                if (multiplier > 0D) {
                    permissionMultipliers.put(permission, multiplier);
                }
            }
        }
    }

    public boolean isSellEnabled() {
        return sellEnabled && plugin.getEconomyManager().isAvailable();
    }

    public WorthResult resolveWorth(Material material) {
        if (material == null) {
            return WorthResult.NOT_SELLABLE;
        }
        Double price = prices.get(material);
        if (price == null || price <= 0D) {
            return WorthResult.NOT_SELLABLE;
        }
        return new WorthResult(true, price);
    }

    public double getSellMultiplier(Player player) {
        double best = globalMultiplier;
        if (player == null || permissionMultipliers.isEmpty()) {
            return best;
        }

        double bonus = 1.0D;
        boolean matched = false;
        for (Map.Entry<String, Double> entry : permissionMultipliers.entrySet()) {
            if (PermissionUtils.has(player, entry.getKey()) && (!matched || entry.getValue() > bonus)) {
                bonus = entry.getValue();
                matched = true;
            }
        }
        return matched ? best * bonus : best;
    }

    public String prettifyMaterial(Material material) {
        if (material == null) {
            return "Unknown";
        }
        return prettify(material.name());
    }

    public static String prettify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }

        String[] tokens = raw.toLowerCase(Locale.US).replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return builder.toString();
    }
}
