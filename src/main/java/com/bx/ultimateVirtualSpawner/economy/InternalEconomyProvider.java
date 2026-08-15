package com.bx.ultimateVirtualSpawner.economy;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.managers.DatabaseManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InternalEconomyProvider implements EconomyProvider {

    private static final class Account {
        private final UUID uuid;
        private volatile String name;
        private volatile double balance;
        private volatile long updatedAt;

        private Account(UUID uuid, String name, double balance, long updatedAt) {
            this.uuid = uuid;
            this.name = name == null ? "" : name;
            this.balance = balance;
            this.updatedAt = updatedAt;
        }
    }

    private final UltimateVirtualSpawner plugin;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

    private double startingBalance;
    private int decimalPlaces;
    private boolean allowNegative;
    private double maximumBalance;

    public InternalEconomyProvider(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        reload();
        load();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        startingBalance = Math.max(0D, config.getDouble("ECONOMY.INTERNAL.STARTING_BALANCE", 0D));
        decimalPlaces = MoneyMath.clampDecimalPlaces(config.getInt("ECONOMY.INTERNAL.DECIMAL_PLACES", 2));
        allowNegative = config.getBoolean("ECONOMY.INTERNAL.ALLOW_NEGATIVE", false);
        maximumBalance = config.getDouble("ECONOMY.INTERNAL.MAXIMUM_BALANCE", 0D);
        if (maximumBalance <= 0D) {
            maximumBalance = Double.MAX_VALUE;
        }
    }

    private void load() {
        accounts.clear();
        for (DatabaseManager.BalanceRecord record : plugin.getDatabaseManager().loadAllBalances()) {
            accounts.put(record.playerUuid(),
                    new Account(record.playerUuid(), record.playerName(), record.balance(), record.updatedAt()));
        }
        plugin.getLogger().info("[Economy] Loaded " + accounts.size() + " internal balance(s).");
    }

    @Override
    public String getName() {
        return "Internal";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsAdministration() {
        return true;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public double getStartingBalance() {
        return startingBalance;
    }

    public boolean hasAccount(OfflinePlayer player) {
        return player != null && player.getUniqueId() != null && accounts.containsKey(player.getUniqueId());
    }

    public void createAccountIfMissing(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return;
        }
        account(player, true);
    }

    private Account account(OfflinePlayer player, boolean persistIfCreated) {
        UUID uuid = player.getUniqueId();
        boolean[] created = {false};
        Account account = accounts.computeIfAbsent(uuid, key -> {
            created[0] = true;
            return new Account(key, player.getName(), round(startingBalance), System.currentTimeMillis());
        });

        String currentName = player.getName();
        if (currentName != null && !currentName.equals(account.name)) {
            account.name = currentName;
            if (!created[0]) {
                persist(account);
            }
        }
        if (created[0] && persistIfCreated) {
            persist(account);
        }
        return account;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return 0D;
        }
        Account existing = accounts.get(player.getUniqueId());
        return existing == null ? round(startingBalance) : existing.balance;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= round(amount);
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        if (player == null || player.getUniqueId() == null) {
            return TransactionResult.failed("no player");
        }
        double rounded = round(amount);
        if (rounded <= 0D) {
            return TransactionResult.failed("amount must be positive");
        }

        Account account = account(player, false);
        synchronized (account) {
            double target = round(account.balance + rounded);
            if (target > maximumBalance) {
                target = maximumBalance;
            }
            double applied = round(target - account.balance);
            if (applied <= 0D) {
                return TransactionResult.failed("balance limit reached");
            }
            account.balance = target;
            account.updatedAt = System.currentTimeMillis();
            persist(account);
            return TransactionResult.ok(applied, target);
        }
    }

    @Override
    public TransactionResult withdraw(OfflinePlayer player, double amount) {
        if (player == null || player.getUniqueId() == null) {
            return TransactionResult.failed("no player");
        }
        double rounded = round(amount);
        if (rounded <= 0D) {
            return TransactionResult.failed("amount must be positive");
        }

        Account account = account(player, false);
        synchronized (account) {
            if (!allowNegative && account.balance < rounded) {
                return TransactionResult.failed("insufficient funds");
            }
            double target = round(account.balance - rounded);
            account.balance = target;
            account.updatedAt = System.currentTimeMillis();
            persist(account);
            return TransactionResult.ok(rounded, target);
        }
    }

    @Override
    public TransactionResult setBalance(OfflinePlayer player, double amount) {
        if (player == null || player.getUniqueId() == null) {
            return TransactionResult.failed("no player");
        }
        double target = round(amount);
        if (!allowNegative && target < 0D) {
            return TransactionResult.failed("amount must not be negative");
        }
        if (target > maximumBalance) {
            target = maximumBalance;
        }

        Account account = account(player, false);
        synchronized (account) {
            account.balance = target;
            account.updatedAt = System.currentTimeMillis();
            persist(account);
            return TransactionResult.ok(target, target);
        }
    }

    public TransactionResult transfer(OfflinePlayer from, OfflinePlayer to, double amount) {
        if (from == null || to == null || from.getUniqueId() == null || to.getUniqueId() == null) {
            return TransactionResult.failed("no player");
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return TransactionResult.failed("cannot pay yourself");
        }

        TransactionResult withdrawal = withdraw(from, amount);
        if (!withdrawal.success()) {
            return withdrawal;
        }

        TransactionResult depositResult = deposit(to, withdrawal.amount());
        if (!depositResult.success()) {
            deposit(from, withdrawal.amount());
            return TransactionResult.failed(depositResult.failureReason());
        }
        return TransactionResult.ok(withdrawal.amount(), withdrawal.newBalance());
    }

    public record BalanceEntry(UUID uuid, String name, double balance) {}

    public List<BalanceEntry> getTopBalances(int limit) {
        List<BalanceEntry> entries = new ArrayList<>(accounts.size());
        for (Account account : accounts.values()) {
            entries.add(new BalanceEntry(account.uuid, account.name, account.balance));
        }
        entries.sort(Comparator.comparingDouble(BalanceEntry::balance).reversed());
        return limit <= 0 || limit >= entries.size() ? entries : entries.subList(0, limit);
    }

    public int getAccountCount() {
        return accounts.size();
    }

    public double getTotalMoney() {
        double total = 0D;
        for (Account account : accounts.values()) {
            total += account.balance;
        }
        return total;
    }

    public boolean deleteAccount(UUID uuid) {
        if (uuid == null || accounts.remove(uuid) == null) {
            return false;
        }
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().deleteBalance(uuid));
        return true;
    }

    public UUID findAccountByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Account account : accounts.values()) {
            if (name.equalsIgnoreCase(account.name)) {
                return account.uuid;
            }
        }
        return null;
    }

    private void persist(Account account) {
        UUID uuid = account.uuid;
        String name = account.name;
        double balance = account.balance;
        long updatedAt = account.updatedAt;
        plugin.getDatabaseManager().executeAsync(
                () -> plugin.getDatabaseManager().saveBalance(uuid, name, balance, updatedAt));
    }

    @Override
    public void shutdown() {
        List<DatabaseManager.BalanceRecord> records = new ArrayList<>(accounts.size());
        for (Account account : accounts.values()) {
            records.add(new DatabaseManager.BalanceRecord(
                    account.uuid, account.name, account.balance, account.updatedAt));
        }
        plugin.getDatabaseManager().saveBalances(records);
    }

    public double round(double value) {
        return MoneyMath.round(value, decimalPlaces);
    }
}
