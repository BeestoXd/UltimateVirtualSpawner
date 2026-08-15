package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import com.bx.ultimateVirtualSpawner.models.SpawnerLootEntry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DatabaseManager {

    private final UltimateVirtualSpawner plugin;
    private final ExecutorService executor;

    private Connection connection;
    private boolean mySql;
    private boolean ready;

    public DatabaseManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "UltimateVirtualSpawner-DB");
            thread.setDaemon(true);
            return thread;
        });
        connect();
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isMySql() {
        return mySql;
    }

    private void connect() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String type = config.getString("DATABASE.TYPE", "SQLITE").trim().toUpperCase(Locale.US);
        mySql = type.equals("MYSQL") || type.equals("MARIADB");

        try {
            if (mySql) {
                String host = config.getString("DATABASE.MYSQL.HOST", "localhost");
                int port = config.getInt("DATABASE.MYSQL.PORT", 3306);
                String database = config.getString("DATABASE.MYSQL.DATABASE", "ultimatevirtualspawner");
                String user = config.getString("DATABASE.MYSQL.USERNAME", "root");
                String password = config.getString("DATABASE.MYSQL.PASSWORD", "");
                boolean useSsl = config.getBoolean("DATABASE.MYSQL.USE_SSL", false);

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + useSsl
                        + "&allowPublicKeyRetrieval=true"
                        + "&useUnicode=true&characterEncoding=UTF-8"
                        + "&autoReconnect=true";
                connection = DriverManager.getConnection(url, user, password);
            } else {
                File folder = plugin.getDataFolder();
                if (!folder.exists() && !folder.mkdirs()) {
                    plugin.getLogger().warning("[Database] Could not create the plugin data folder.");
                }
                String fileName = config.getString("DATABASE.SQLITE.FILE", "spawners.db");
                File file = new File(folder, fileName);
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            }

            createTables();
            ensureSpawnerColumns();
            ready = true;
            plugin.getLogger().info("[Database] Connected using " + (mySql ? "MySQL" : "SQLite") + ".");
        } catch (SQLException exception) {
            ready = false;
            plugin.getLogger().log(Level.SEVERE, "[Database] Failed to connect; spawner data will not persist.", exception);
        }
    }

    private void createTables() throws SQLException {
        execute(adaptSchemaSql(
                "CREATE TABLE IF NOT EXISTS uvs_spawners (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  world VARCHAR(96) NOT NULL," +
                "  x INTEGER NOT NULL," +
                "  y INTEGER NOT NULL," +
                "  z INTEGER NOT NULL," +
                "  owner_uuid VARCHAR(36) NOT NULL," +
                "  owner_name VARCHAR(32) NOT NULL," +
                "  mob_type VARCHAR(64) NOT NULL," +
                "  stack_amount BIGINT NOT NULL," +
                "  access_mode VARCHAR(24) NOT NULL," +
                "  last_processed_at BIGINT NOT NULL," +
                "  created_at BIGINT NOT NULL," +
                "  updated_at BIGINT NOT NULL," +
                "  disabled_loot_keys TEXT," +
                "  stored_xp DOUBLE DEFAULT 0," +
                "  UNIQUE(world, x, y, z)" +
                ")"
        ));
        execute(adaptSchemaSql(
                "CREATE TABLE IF NOT EXISTS uvs_spawner_loot (" +
                "  spawner_id BIGINT NOT NULL," +
                "  loot_key VARCHAR(64) NOT NULL," +
                "  material VARCHAR(96) NOT NULL," +
                "  amount BIGINT NOT NULL," +
                "  PRIMARY KEY (spawner_id, loot_key)" +
                ")"
        ));
        execute(adaptSchemaSql(
                "CREATE TABLE IF NOT EXISTS uvs_balances (" +
                "  player_uuid VARCHAR(36) NOT NULL," +
                "  player_name VARCHAR(32) NOT NULL," +
                "  balance DOUBLE NOT NULL," +
                "  updated_at BIGINT NOT NULL," +
                "  PRIMARY KEY (player_uuid)" +
                ")"
        ));
    }


    public record BalanceRecord(UUID playerUuid, String playerName, double balance, long updatedAt) {}

    public List<BalanceRecord> loadAllBalances() {
        List<BalanceRecord> balances = new ArrayList<>();
        if (!ready) {
            return balances;
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM uvs_balances")) {
            while (resultSet.next()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(resultSet.getString("player_uuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                balances.add(new BalanceRecord(
                        uuid,
                        resultSet.getString("player_name"),
                        resultSet.getDouble("balance"),
                        resultSet.getLong("updated_at")
                ));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to load balances", exception);
        }
        return balances;
    }

    public synchronized void saveBalance(UUID playerUuid, String playerName, double balance, long updatedAt) {
        if (!ready || playerUuid == null) {
            return;
        }

        String sql = mySql
                ? "INSERT INTO uvs_balances (player_uuid, player_name, balance, updated_at) VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), "
                        + "balance=VALUES(balance), updated_at=VALUES(updated_at)"
                : "REPLACE INTO uvs_balances (player_uuid, player_name, balance, updated_at) VALUES (?,?,?,?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setDouble(3, balance);
            statement.setLong(4, updatedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to save balance for " + playerUuid, exception);
        }
    }

    public synchronized void saveBalances(Collection<BalanceRecord> records) {
        if (!ready || records == null || records.isEmpty()) {
            return;
        }

        String sql = mySql
                ? "INSERT INTO uvs_balances (player_uuid, player_name, balance, updated_at) VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), "
                        + "balance=VALUES(balance), updated_at=VALUES(updated_at)"
                : "REPLACE INTO uvs_balances (player_uuid, player_name, balance, updated_at) VALUES (?,?,?,?)";

        boolean autoCommitDisabled = false;
        try {
            connection.setAutoCommit(false);
            autoCommitDisabled = true;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (BalanceRecord record : records) {
                    if (record == null || record.playerUuid() == null) {
                        continue;
                    }
                    statement.setString(1, record.playerUuid().toString());
                    statement.setString(2, record.playerName() == null ? "" : record.playerName());
                    statement.setDouble(3, record.balance());
                    statement.setLong(4, record.updatedAt());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            if (autoCommitDisabled) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Database] Failed to roll back balance batch", rollbackException);
                }
            }
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to save balances", exception);
        } finally {
            if (autoCommitDisabled) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public synchronized void deleteBalance(UUID playerUuid) {
        if (!ready || playerUuid == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM uvs_balances WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to delete balance for " + playerUuid, exception);
        }
    }

    private String adaptSchemaSql(String sql) {
        if (!mySql) {
            return sql.replace(" DOUBLE ", " REAL ");
        }
        return sql
                .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "BIGINT AUTO_INCREMENT PRIMARY KEY")
                .replace("UNIQUE(world, x, y, z)", "UNIQUE KEY uq_uvs_location (world, x, y, z)");
    }

    private void ensureSpawnerColumns() throws SQLException {
        ensureColumnExists("uvs_spawners", "disabled_loot_keys", "TEXT");
        ensureColumnExists("uvs_spawners", "stored_xp", mySql ? "DOUBLE DEFAULT 0" : "REAL DEFAULT 0");
    }

    private void ensureColumnExists(String table, String column, String definition) throws SQLException {
        if (hasColumn(table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        if (mySql) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                statement.setString(1, table);
                statement.setString(2, column);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public void executeAsync(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!ready || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "[Database] Async task failed", exception);
            }
        });
    }

    public List<SpawnerInstance> loadAllSpawners() {
        List<SpawnerInstance> spawners = new ArrayList<>();
        if (!ready) {
            return spawners;
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM uvs_spawners ORDER BY id ASC")) {
            while (resultSet.next()) {
                double storedXp = 0.0;
                try {
                    storedXp = resultSet.getDouble("stored_xp");
                } catch (SQLException ignored) {
                }

                UUID ownerUuid;
                try {
                    ownerUuid = UUID.fromString(resultSet.getString("owner_uuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                SpawnerInstance instance = new SpawnerInstance(
                        resultSet.getLong("id"),
                        resultSet.getString("world"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z"),
                        ownerUuid,
                        resultSet.getString("owner_name"),
                        resultSet.getString("mob_type"),
                        resultSet.getLong("stack_amount"),
                        SpawnerInstance.AccessMode.fromString(
                                resultSet.getString("access_mode"), SpawnerInstance.AccessMode.OWNER_ONLY),
                        resultSet.getLong("last_processed_at"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        storedXp
                );

                String disabledKeysRaw = resultSet.getString("disabled_loot_keys");
                if (disabledKeysRaw != null && !disabledKeysRaw.isBlank()) {
                    instance.setDisabledLootKeys(List.of(disabledKeysRaw.split(",")));
                }
                spawners.add(instance);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to load managed spawners", exception);
        }
        return spawners;
    }

    public Map<Long, List<SpawnerLootEntry>> loadAllSpawnerLoot() {
        Map<Long, List<SpawnerLootEntry>> lootBySpawnerId = new HashMap<>();
        if (!ready) {
            return lootBySpawnerId;
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM uvs_spawner_loot ORDER BY spawner_id ASC, loot_key ASC")) {
            while (resultSet.next()) {
                Material material = Material.matchMaterial(resultSet.getString("material"));
                if (material == null) {
                    continue;
                }
                lootBySpawnerId
                        .computeIfAbsent(resultSet.getLong("spawner_id"), ignored -> new ArrayList<>())
                        .add(new SpawnerLootEntry(
                                resultSet.getString("loot_key"),
                                material,
                                resultSet.getLong("amount")
                        ));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to load managed spawner loot", exception);
        }
        return lootBySpawnerId;
    }

    public synchronized long createSpawner(SpawnerInstance instance) {
        if (!ready || instance == null) {
            return -1L;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO uvs_spawners (world, x, y, z, owner_uuid, owner_name, mob_type, stack_amount, "
                        + "access_mode, last_processed_at, created_at, updated_at, disabled_loot_keys, stored_xp) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
        )) {
            bindSpawner(statement, instance, 1);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to create managed spawner", exception);
        }
        return -1L;
    }

    public synchronized void saveSpawner(SpawnerInstance instance) {
        if (!ready || instance == null) {
            return;
        }
        if (instance.getId() <= 0L) {
            long newId = createSpawner(instance);
            if (newId > 0L) {
                instance.setId(newId);
            }
            return;
        }

        String sql = mySql
                ? "INSERT INTO uvs_spawners (id, world, x, y, z, owner_uuid, owner_name, mob_type, stack_amount, "
                        + "access_mode, last_processed_at, created_at, updated_at, disabled_loot_keys, stored_xp) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), "
                        + "owner_uuid=VALUES(owner_uuid), owner_name=VALUES(owner_name), mob_type=VALUES(mob_type), "
                        + "stack_amount=VALUES(stack_amount), access_mode=VALUES(access_mode), "
                        + "last_processed_at=VALUES(last_processed_at), updated_at=VALUES(updated_at), "
                        + "disabled_loot_keys=VALUES(disabled_loot_keys), stored_xp=VALUES(stored_xp)"
                : "REPLACE INTO uvs_spawners (id, world, x, y, z, owner_uuid, owner_name, mob_type, stack_amount, "
                        + "access_mode, last_processed_at, created_at, updated_at, disabled_loot_keys, stored_xp) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instance.getId());
            bindSpawner(statement, instance, 2);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[Database] Failed to save managed spawner " + instance.getId(), exception);
        }
    }

    private void bindSpawner(PreparedStatement statement, SpawnerInstance instance, int offset) throws SQLException {
        statement.setString(offset, instance.getWorld());
        statement.setInt(offset + 1, instance.getX());
        statement.setInt(offset + 2, instance.getY());
        statement.setInt(offset + 3, instance.getZ());
        statement.setString(offset + 4, instance.getOwnerUuid().toString());
        statement.setString(offset + 5, instance.getOwnerNameSnapshot());
        statement.setString(offset + 6, instance.getMobTypeKey());
        statement.setLong(offset + 7, instance.getStackAmount());
        statement.setString(offset + 8, instance.getAccessMode().name());
        statement.setLong(offset + 9, instance.getLastProcessedAt());
        statement.setLong(offset + 10, instance.getCreatedAt());
        statement.setLong(offset + 11, instance.getUpdatedAt());
        statement.setString(offset + 12, String.join(",", instance.getDisabledLootKeys()));
        statement.setDouble(offset + 13, instance.getStoredXp());
    }

    public synchronized void replaceSpawnerLoot(long spawnerId, Collection<SpawnerLootEntry> lootEntries) {
        if (!ready || spawnerId <= 0L) {
            return;
        }

        boolean autoCommitDisabled = false;
        try {
            connection.setAutoCommit(false);
            autoCommitDisabled = true;

            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM uvs_spawner_loot WHERE spawner_id = ?")) {
                delete.setLong(1, spawnerId);
                delete.executeUpdate();
            }

            if (lootEntries != null && !lootEntries.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO uvs_spawner_loot (spawner_id, loot_key, material, amount) VALUES (?,?,?,?)")) {
                    for (SpawnerLootEntry entry : lootEntries) {
                        if (entry == null || entry.getAmount() <= 0L) {
                            continue;
                        }
                        insert.setLong(1, spawnerId);
                        insert.setString(2, entry.getKey());
                        insert.setString(3, entry.getMaterial().name());
                        insert.setLong(4, entry.getAmount());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }

            connection.commit();
        } catch (SQLException exception) {
            if (autoCommitDisabled) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Database] Failed to roll back spawner loot transaction", rollbackException);
                }
            }
            plugin.getLogger().log(Level.WARNING,
                    "[Database] Failed to replace managed spawner loot for spawner " + spawnerId, exception);
        } finally {
            if (autoCommitDisabled) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public synchronized void deleteSpawner(long spawnerId) {
        if (!ready || spawnerId <= 0L) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM uvs_spawners WHERE id = ?")) {
            statement.setLong(1, spawnerId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to delete managed spawner " + spawnerId, exception);
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM uvs_spawner_loot WHERE spawner_id = ?")) {
            statement.setLong(1, spawnerId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[Database] Failed to delete managed spawner loot " + spawnerId, exception);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to close the connection", exception);
            }
        }
        ready = false;
    }
}
