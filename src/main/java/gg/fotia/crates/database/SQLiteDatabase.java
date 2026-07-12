package gg.fotia.crates.database;

import gg.fotia.crates.FotiaCrates;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase implements Database {

    private final FotiaCrates plugin;
    private final String fileName;
    private boolean initialConnectLogged = false;

    public SQLiteDatabase(FotiaCrates plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public boolean connect() {
        try (Connection ignored = openConnection()) {
            if (!initialConnectLogged) {
                plugin.getLogger().info("SQLite database connected!");
                initialConnectLogged = true;
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        // SQLite connections are operation-scoped and closed by their callers.
    }

    @Override
    public Connection getConnection() throws SQLException {
        return openConnection();
    }

    @Override
    public void createTables() {
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            // 虚拟钥匙表 - 使用 key_id 存储钥匙ID
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_keys (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    key_id VARCHAR(64) NOT NULL,
                    amount INTEGER DEFAULT 0,
                    UNIQUE(uuid, key_id)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    reward_name VARCHAR(128) NOT NULL,
                    timestamp BIGINT NOT NULL
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pity_counter (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    count INTEGER DEFAULT 0,
                    UNIQUE(uuid, crate_id)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    yaw REAL DEFAULT 0,
                    UNIQUE(world, x, y, z)
                )
            """);

            // 为旧表添加yaw列（如果不存在）
            try {
                stmt.executeUpdate("ALTER TABLE crate_locations ADD COLUMN yaw REAL DEFAULT 0");
            } catch (SQLException ignored) {
                // 列已存在，忽略错误
            }

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_uuid ON crate_history(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON crate_history(timestamp)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_uuid_timestamp_id ON crate_history(uuid, timestamp DESC, id DESC)");

            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, fileName);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            // Allow the asynchronous writer to wait briefly for another SQLite operation instead of failing immediately.
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }
}
