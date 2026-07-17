package gg.fotia.crates.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.fotia.crates.FotiaCrates;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase implements Database {

    private final FotiaCrates plugin;
    private final String fileName;
    private HikariDataSource dataSource;
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

            HikariConfig config = new HikariConfig();
            config.setPoolName("FotiaCrates-SQLite");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10_000L);
            config.setMaxLifetime(0L);
            config.setConnectionInitSql("PRAGMA busy_timeout = 5000");
            dataSource = new HikariDataSource(config);

            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            if (!initialConnectLogged) {
                plugin.getLogger().info("SQLite database connected!");
                initialConnectLogged = true;
            }
            return true;
        } catch (Exception e) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            dataSource = null;
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            if (!connect()) {
                throw new SQLException("SQLite connection pool is unavailable");
            }
        }
        return dataSource.getConnection();
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
}
