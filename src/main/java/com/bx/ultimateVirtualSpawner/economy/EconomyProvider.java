package com.bx.ultimateVirtualSpawner.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {

    record TransactionResult(boolean success, String failureReason, double amount, double newBalance) {

        public static TransactionResult ok(double amount, double newBalance) {
            return new TransactionResult(true, null, amount, newBalance);
        }

        public static TransactionResult failed(String reason) {
            return new TransactionResult(false, reason, 0D, 0D);
        }
    }

    String getName();

    boolean isAvailable();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    TransactionResult deposit(OfflinePlayer player, double amount);

    TransactionResult withdraw(OfflinePlayer player, double amount);

    TransactionResult setBalance(OfflinePlayer player, double amount);

    boolean supportsAdministration();

    default void shutdown() {
    }
}
