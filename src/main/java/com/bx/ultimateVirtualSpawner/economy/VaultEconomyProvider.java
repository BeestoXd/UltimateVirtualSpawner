package com.bx.ultimateVirtualSpawner.economy;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class VaultEconomyProvider implements EconomyProvider {

    private final UltimateVirtualSpawner plugin;
    private volatile Economy economy;

    public VaultEconomyProvider(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        resolve();
    }

    private Economy resolve() {
        Economy cached = economy;
        if (cached != null) {
            return cached;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }

        try {
            RegisteredServiceProvider<Economy> registration =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (registration == null || registration.getProvider() == null) {
                return null;
            }
            economy = registration.getProvider();
            plugin.getLogger().info("[Economy] Hooked into " + economy.getName() + " via Vault.");
            return economy;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "[Economy] Failed to hook into Vault.", throwable);
            return null;
        }
    }

    @Override
    public String getName() {
        Economy resolved = resolve();
        return resolved == null ? "Vault (no provider)" : resolved.getName() + " via Vault";
    }

    @Override
    public boolean isAvailable() {
        return resolve() != null;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        Economy resolved = player == null ? null : resolve();
        if (resolved == null) {
            return 0D;
        }
        try {
            return resolved.getBalance(player);
        } catch (Throwable ignored) {
            return 0D;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Economy resolved = player == null ? null : resolve();
        if (resolved == null) {
            return false;
        }
        try {
            return resolved.has(player, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        Economy resolved = player == null ? null : resolve();
        if (resolved == null) {
            return TransactionResult.failed("no economy provider");
        }
        if (amount <= 0D) {
            return TransactionResult.failed("amount must be positive");
        }

        try {
            EconomyResponse response = resolved.depositPlayer(player, amount);
            if (response != null && response.transactionSuccess()) {
                return TransactionResult.ok(amount, response.balance);
            }
            return TransactionResult.failed(
                    response == null ? "no response" : String.valueOf(response.errorMessage));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "[Economy] Deposit failed for " + player.getName(), throwable);
            return TransactionResult.failed("deposit threw an exception");
        }
    }

    @Override
    public TransactionResult withdraw(OfflinePlayer player, double amount) {
        Economy resolved = player == null ? null : resolve();
        if (resolved == null) {
            return TransactionResult.failed("no economy provider");
        }
        if (amount <= 0D) {
            return TransactionResult.failed("amount must be positive");
        }

        try {
            EconomyResponse response = resolved.withdrawPlayer(player, amount);
            if (response != null && response.transactionSuccess()) {
                return TransactionResult.ok(amount, response.balance);
            }
            return TransactionResult.failed(
                    response == null ? "no response" : String.valueOf(response.errorMessage));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "[Economy] Withdraw failed for " + player.getName(), throwable);
            return TransactionResult.failed("withdraw threw an exception");
        }
    }

    @Override
    public TransactionResult setBalance(OfflinePlayer player, double amount) {
        double current = getBalance(player);
        double delta = amount - current;
        if (Math.abs(delta) < 1.0E-9) {
            return TransactionResult.ok(0D, current);
        }
        return delta > 0D ? deposit(player, delta) : withdraw(player, -delta);
    }

    @Override
    public boolean supportsAdministration() {
        return false;
    }
}
