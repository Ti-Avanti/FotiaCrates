package gg.fotia.crates.database;

import gg.fotia.crates.FotiaCrates;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase implements Database {

    private final FotiaCrates plugin;
    private Connection connection;
    private final String fileName;
    private boolean initialConnectLogged = false;

    public SQLiteDatabase(FotiaCrates plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public boolean connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, fileName);
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
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
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    @Override
    public void createTables() {
        try (Statement stmt = getConnection().createStatement()) {
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
                    UNIQUE(world, x, y, z)
                )
            """);

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_uuid ON crate_history(uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_timestamp ON crate_history(timestamp)");

            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }
}
