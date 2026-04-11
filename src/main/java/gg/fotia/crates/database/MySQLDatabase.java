package gg.fotia.crates.database;

import gg.fotia.crates.FotiaCrates;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLDatabase implements Database {

    private final FotiaCrates plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private boolean initialConnectLogged = false;

    public MySQLDatabase(FotiaCrates plugin, String host, int port, String database, String username, String password) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(600000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            if (!initialConnectLogged) {
                plugin.getLogger().info("MySQL database connected!");
                initialConnectLogged = true;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL database connection closed.");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            connect();
        }
        return dataSource.getConnection();
    }

    @Override
    public void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 虚拟钥匙表 - 使用 key_id 存储钥匙ID
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_keys (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    key_id VARCHAR(64) NOT NULL,
                    amount INT DEFAULT 0,
                    UNIQUE KEY unique_player_key (uuid, key_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_history (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    reward_name VARCHAR(128) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pity_counter (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    count INT DEFAULT 0,
                    UNIQUE KEY unique_player_crate (uuid, crate_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS crate_locations (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    crate_id VARCHAR(64) NOT NULL,
                    yaw FLOAT DEFAULT 0,
                    UNIQUE KEY unique_location (world, x, y, z)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // 为旧表添加yaw列（如果不存在）
            try {
                stmt.executeUpdate("ALTER TABLE crate_locations ADD COLUMN yaw FLOAT DEFAULT 0");
            } catch (SQLException ignored) {
                // 列已存在，忽略错误
            }

            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }
}
