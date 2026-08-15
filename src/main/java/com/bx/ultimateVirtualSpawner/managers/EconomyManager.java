package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.economy.EconomyProvider;
import com.bx.ultimateVirtualSpawner.economy.InternalEconomyProvider;
import com.bx.ultimateVirtualSpawner.economy.VaultEconomyProvider;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;

import java.util.Locale;
import java.util.logging.Level;

public class EconomyManager {

    public enum Mode {
        INTERNAL,
        VAULT,
        AUTO;

        static Mode fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return INTERNAL;
            }
            String normalized = raw.trim().toUpperCase(Locale.US);
            for (Mode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            return INTERNAL;
        }
    }

    public record DepositResult(boolean success, String message, double amount) {}

    private final UltimateVirtualSpawner plugin;

    private Mode mode;
    private InternalEconomyProvider internal;
    private VaultEconomyProvider vault;
    private boolean vaultBridgeRegistered;

    private String currencySymbol;
    private boolean compactFormat;
    private boolean symbolBeforeAmount;
    private String currencyNameSingular;
    private String currencyNamePlural;

    public EconomyManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        readSettings();
        setupProviders();
        logStartupState();
    }

    private void readSettings() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        mode = Mode.fromString(config.getString("ECONOMY.PROVIDER", "INTERNAL"));
        currencySymbol = config.getString("ECONOMY.CURRENCY_SYMBOL", "$");
        compactFormat = config.getBoolean("ECONOMY.COMPACT_FORMAT", true);
        symbolBeforeAmount = config.getBoolean("ECONOMY.SYMBOL_BEFORE_AMOUNT", true);
        currencyNameSingular = config.getString("ECONOMY.INTERNAL.CURRENCY_NAME_SINGULAR", "Dollar");
        currencyNamePlural = config.getString("ECONOMY.INTERNAL.CURRENCY_NAME_PLURAL", "Dollars");
    }

    private void setupProviders() {
        if (mode != Mode.VAULT) {
            internal = new InternalEconomyProvider(plugin);
        }
        if (mode != Mode.INTERNAL) {
            vault = new VaultEconomyProvider(plugin);
        }
        registerVaultBridgeIfWanted();
    }

    private void registerVaultBridgeIfWanted() {
        if (vaultBridgeRegistered || internal == null) {
            return;
        }
        if (!plugin.getConfigManager().getConfig().getBoolean("ECONOMY.INTERNAL.REGISTER_WITH_VAULT", true)) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        try {
            if (mode == Mode.AUTO && plugin.getServer().getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class) != null) {
                plugin.getLogger().info("[Economy] An external Vault economy is already registered; "
                        + "not publishing the internal one.");
                return;
            }

            net.milkbowl.vault.economy.Economy bridge =
                    new com.bx.ultimateVirtualSpawner.hooks.VaultEconomyBridge(plugin, internal);
            plugin.getServer().getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class, bridge, plugin, ServicePriority.Normal);
            vaultBridgeRegistered = true;
            plugin.getLogger().info("[Economy] Published the internal economy to Vault; "
                    + "other plugins now share these balances.");
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "[Economy] Failed to publish the internal economy to Vault.",
                    throwable);
        }
    }

    private void logStartupState() {
        switch (mode) {
            case INTERNAL -> plugin.getLogger().info("[Economy] Using the built-in economy "
                    + "(no external economy plugin required).");
            case VAULT -> {
                if (vault != null && vault.isAvailable()) {
                    plugin.getLogger().info("[Economy] Using " + vault.getName() + ".");
                } else {
                    plugin.getLogger().warning("[Economy] PROVIDER is VAULT but no economy provider is "
                            + "registered - selling is disabled. Set ECONOMY.PROVIDER to INTERNAL in "
                            + "config.yml to use the built-in economy instead.");
                }
            }
            case AUTO -> {
                if (vault != null && vault.isAvailable()) {
                    plugin.getLogger().info("[Economy] AUTO resolved to " + vault.getName() + ".");
                } else {
                    plugin.getLogger().info("[Economy] AUTO found no Vault economy; "
                            + "using the built-in economy.");
                }
            }
        }
    }

    public void reload() {
        Mode previousMode = mode;
        readSettings();

        if (mode != previousMode) {
            plugin.getLogger().warning("[Economy] ECONOMY.PROVIDER changed from " + previousMode + " to " + mode
                    + "; restart the server for it to take effect.");
            mode = previousMode;
        }
        if (internal != null) {
            internal.reload();
        }
    }

    public EconomyProvider getProvider() {
        if (mode == Mode.VAULT) {
            return vault;
        }
        if (mode == Mode.AUTO && vault != null && vault.isAvailable()) {
            return vault;
        }
        return internal;
    }

    public InternalEconomyProvider getInternal() {
        return internal;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isInternalActive() {
        return getProvider() == internal && internal != null;
    }

    public boolean isAvailable() {
        EconomyProvider provider = getProvider();
        return provider != null && provider.isAvailable();
    }

    public String getProviderName() {
        EconomyProvider provider = getProvider();
        return provider == null ? null : provider.getName();
    }


    public DepositResult deposit(OfflinePlayer player, double amount) {
        EconomyProvider provider = getProvider();
        if (provider == null) {
            return new DepositResult(false, "no economy provider", 0D);
        }
        EconomyProvider.TransactionResult result = provider.deposit(player, amount);
        return new DepositResult(result.success(),
                result.success() ? "ok" : result.failureReason(), result.amount());
    }

    public EconomyProvider.TransactionResult withdraw(OfflinePlayer player, double amount) {
        EconomyProvider provider = getProvider();
        return provider == null
                ? EconomyProvider.TransactionResult.failed("no economy provider")
                : provider.withdraw(player, amount);
    }

    public double getBalance(OfflinePlayer player) {
        EconomyProvider provider = getProvider();
        return provider == null ? 0D : provider.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        EconomyProvider provider = getProvider();
        return provider != null && provider.has(player, amount);
    }

    public void shutdown() {
        if (internal != null) {
            internal.shutdown();
        }
    }


    public String formatMoney(double amount) {
        return withSymbol(NumberUtils.format(amount));
    }

    public String formatMoneyCompact(double amount) {
        return withSymbol(compactFormat ? NumberUtils.formatNice(amount) : NumberUtils.format(amount));
    }

    private String withSymbol(String formatted) {
        return symbolBeforeAmount ? currencySymbol + formatted : formatted + currencySymbol;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public String getCurrencyNameSingular() {
        return currencyNameSingular;
    }

    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }
}
