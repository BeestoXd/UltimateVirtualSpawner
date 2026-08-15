package com.bx.ultimateVirtualSpawner.hooks;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.economy.EconomyProvider;
import com.bx.ultimateVirtualSpawner.economy.InternalEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

public class VaultEconomyBridge implements Economy {

    private static final String NOT_SUPPORTED = "Banks are not supported by UltimateVirtualSpawner.";

    private final UltimateVirtualSpawner plugin;
    private final InternalEconomyProvider economy;

    public VaultEconomyBridge(UltimateVirtualSpawner plugin, InternalEconomyProvider economy) {
        this.plugin = plugin;
        this.economy = economy;
    }


    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "UltimateVirtualSpawner";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return economy.getDecimalPlaces();
    }

    @Override
    public String format(double amount) {
        return plugin.getEconomyManager().formatMoney(amount);
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getEconomyManager().getCurrencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getEconomyManager().getCurrencyNameSingular();
    }


    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return economy.hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        economy.createAccountIfMissing(player);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return toResponse(economy.withdraw(player, amount), player);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return toResponse(economy.deposit(player, amount), player);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse toResponse(EconomyProvider.TransactionResult result, OfflinePlayer player) {
        if (result.success()) {
            return new EconomyResponse(result.amount(), result.newBalance(),
                    EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0D, economy.getBalance(player),
                EconomyResponse.ResponseType.FAILURE, result.failureReason());
    }


    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return hasAccount(offline(playerName));
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(offline(playerName));
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return getBalance(offline(playerName));
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(offline(playerName));
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return has(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(offline(playerName), amount);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(offline(playerName));
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(offline(playerName));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offline(String playerName) {
        return Bukkit.getOfflinePlayer(playerName);
    }


    private EconomyResponse noBanks() {
        return new EconomyResponse(0D, 0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, NOT_SUPPORTED);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
